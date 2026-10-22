using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.Diagnostics;
using System.Globalization;
using System.IO;
using System.Linq;
using System.Runtime.InteropServices;
using System.Text.RegularExpressions;
using System.Threading;
using System.Threading.Tasks;
using MediaBrowser.Common.Configuration;
using MediaBrowser.Controller.MediaEncoding;
using Microsoft.Extensions.Logging;

namespace Jellyfin.Plugin.KMusicPlayer.Services;

/// <summary>Codec display name (e.g. "Opus") and bitrate in kbps of the audio stream that gets downloaded.</summary>
public record AudioStreamInfo(string? Codec, int? BitRate);

/// <summary>
/// Runs yt-dlp: the configured one, else the standalone release shipped with the plugin (Linux x64/arm64), else the
/// one on the PATH. Lets it solve YouTube's JavaScript challenges with the Deno shipped with the plugin (else a Deno
/// on the PATH); without a JavaScript runtime signed-in requests get no formats. Uses ffmpeg and ffprobe from the PATH if installed, otherwise (e.g. in the official Docker
/// image) Jellyfin's own ffmpeg.
/// </summary>
public partial class YtDlpService
{
    /// <summary>
    /// Picks the best codec/bitrate combination: YouTube's quality tier first (so e.g. a 256 kbps AAC
    /// stream beats 128 kbps Opus), then channels, then the more efficient codec, then bitrate.
    /// Same selection as the KMusicPlayer desktop client.
    /// </summary>
    internal static readonly string[] BestAudio = ["-f", "bestaudio", "-S", "quality,channels,acodec,abr"];

    private static readonly TimeSpan PlaybackCacheLifetime = TimeSpan.FromHours(12);
    private static readonly TimeSpan SelfUpdateInterval = TimeSpan.FromDays(1);

    private readonly ILogger<YtDlpService> _logger;
    private readonly IMediaEncoder _mediaEncoder;
    private readonly string _playbackCacheDir;
    private readonly string _denoCacheDir;
    // Each lookup spawns a yt-dlp process; don't start one per search result at once
    private readonly SemaphoreSlim _streamInfoLookups = new(4, 4);
    private readonly ConcurrentDictionary<string, AudioStreamInfo> _streamInfoCache = new();
    private readonly ConcurrentDictionary<string, SemaphoreSlim> _playbackLocks = new();
    private readonly object _cookiesCopyLock = new();
    private string? _lastCookiesKey;
    private string? _cookiesCopySource;
    private DateTime _cookiesCopyModified;
    private DateTime _lastSelfUpdate = DateTime.MinValue;

    public YtDlpService(
        ILogger<YtDlpService> logger,
        IMediaEncoder mediaEncoder,
        IApplicationPaths applicationPaths)
    {
        _logger = logger;
        _mediaEncoder = mediaEncoder;
        _playbackCacheDir = Path.Combine(applicationPaths.CachePath, "kmusicplayer");
        _denoCacheDir = Path.Combine(applicationPaths.CachePath, "kmusicplayer-deno");
    }

    /// <summary>Gets a warning to show to users, e.g. when the configured cookies couldn't be read.</summary>
    public string? CookiesWarning { get; private set; }

    private static Configuration.PluginConfiguration Config =>
        Plugin.Instance?.Configuration ?? new Configuration.PluginConfiguration();

    public static bool IsValidVideoId(string videoId) => VideoIdRegex().IsMatch(videoId);

    /// <summary>Gets a value indicating whether yt-dlp is available.</summary>
    public static bool IsAvailable => FindExecutable() != null;

    /// <summary>Gets the yt-dlp release shipped with the plugin for this platform, or null if there is none.</summary>
    private static string? BundledPath
    {
        get
        {
            var asset = (OperatingSystem.IsLinux() ? RuntimeInformation.OSArchitecture : (Architecture?)null) switch
            {
                Architecture.X64 => "yt-dlp_linux",
                Architecture.Arm64 => "yt-dlp_linux_aarch64",
                _ => null,
            };
            var pluginDir = Path.GetDirectoryName(Plugin.Instance?.AssemblyFilePath);
            return asset == null || pluginDir == null ? null : Path.Combine(pluginDir, "yt-dlp", asset);
        }
    }

    /// <summary>Gets the Deno release shipped with the plugin for this platform, or null if there is none.</summary>
    private static string? BundledDenoPath
    {
        get
        {
            var arch = (OperatingSystem.IsLinux() ? RuntimeInformation.OSArchitecture : (Architecture?)null) switch
            {
                Architecture.X64 => "x86_64",
                Architecture.Arm64 => "aarch64",
                _ => null,
            };
            var pluginDir = Path.GetDirectoryName(Plugin.Instance?.AssemblyFilePath);
            return arch == null || pluginDir == null ? null : Path.Combine(pluginDir, "deno", "deno_" + arch);
        }
    }

    /// <summary>Returns the yt-dlp executable, or null if there is none.</summary>
    public static string? FindExecutable()
    {
        var configured = Config.YtDlpPath.Trim();
        if (configured.Length > 0)
        {
            return File.Exists(configured) ? configured : null;
        }

        var bundled = BundledPath;
        if (bundled != null && File.Exists(bundled) && EnsureExecutable(bundled))
        {
            return bundled;
        }

        return FindOnPath(OperatingSystem.IsWindows() ? "yt-dlp.exe" : "yt-dlp");
    }

    /// <summary>Zip extraction may drop the executable bit of the bundled release. Returns false if it can't be set.</summary>
    private static bool EnsureExecutable(string path)
    {
        const UnixFileMode Execute = UnixFileMode.UserExecute | UnixFileMode.GroupExecute | UnixFileMode.OtherExecute;
        if (OperatingSystem.IsWindows())
        {
            return true;
        }

        try
        {
            var mode = File.GetUnixFileMode(path);
            if ((mode & UnixFileMode.UserExecute) == 0)
            {
                File.SetUnixFileMode(path, mode | Execute);
            }

            return true;
        }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException)
        {
            return false;
        }
    }

    public async Task<string?> GetVersionAsync(CancellationToken cancellationToken)
    {
        var executable = FindExecutable();
        if (executable == null)
        {
            return null;
        }

        var result = await RunProcessAsync(executable, ["--version"], cancellationToken).ConfigureAwait(false);
        return result.ExitCode == 0 ? result.Output.Trim() : null;
    }

    /// <summary>Returns codec and bitrate of the audio stream <see cref="BestAudio"/> selects, or null if unknown.</summary>
    public async Task<AudioStreamInfo?> GetAudioStreamInfoAsync(string videoId, CancellationToken cancellationToken)
    {
        RequireValidId(videoId);
        InvalidateCachesIfCookiesChanged();
        if (_streamInfoCache.TryGetValue(videoId, out var cached))
        {
            return cached;
        }

        await _streamInfoLookups.WaitAsync(cancellationToken).ConfigureAwait(false);
        try
        {
            var output = await RunAsync(
                ["--no-playlist", "--quiet", "--no-warnings", "--skip-download", .. BestAudio, "--print", "%(acodec)s %(abr)s", YouTubeMusicClient.WatchUrl(videoId)],
                cancellationToken).ConfigureAwait(false);
            var parts = output.Split('\n', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries)
                .FirstOrDefault()?.Split(' ');
            if (parts is not { Length: 2 })
            {
                return null;
            }

            int? bitRate = double.TryParse(parts[1], NumberStyles.Float, CultureInfo.InvariantCulture, out var abr)
                ? (int)Math.Round(abr)
                : null;
            var info = new AudioStreamInfo(CodecName(parts[0]), bitRate);
            if (info.Codec == null && info.BitRate == null)
            {
                return null;
            }

            _streamInfoCache[videoId] = info;
            return info;
        }
        finally
        {
            _streamInfoLookups.Release();
        }
    }

    /// <summary>
    /// Downloads the best audio stream of <paramref name="videoId"/> into <paramref name="directory"/> as
    /// <c>audio.&lt;ext&gt;</c>, remuxed into an audio container without re-encoding (e.g. webm -> opus),
    /// or converted when <paramref name="audioFormat"/> is given. Returns the file.
    /// </summary>
    public async Task<string> DownloadAudioAsync(string videoId, string directory, string? audioFormat, CancellationToken cancellationToken)
    {
        RequireValidId(videoId);
        List<string> args = ["--no-playlist", "--quiet", "--no-warnings", .. BestAudio, "-x"];
        if (audioFormat != null)
        {
            args.AddRange(["--audio-format", audioFormat, "--audio-quality", "0"]);
        }

        args.AddRange(["-o", Path.Combine(directory, "audio.%(ext)s"), YouTubeMusicClient.WatchUrl(videoId)]);
        await RunAsync(args, cancellationToken).ConfigureAwait(false);
        return Directory.EnumerateFiles(directory, "audio.*").SingleOrDefault()
            ?? throw new IOException("yt-dlp did not produce an audio file");
    }

    /// <summary>
    /// Returns a cached audio file of <paramref name="videoId"/> for playback (in its original codec, or as MP3 for
    /// clients that can't decode Opus/AAC), downloading it first if necessary.
    /// </summary>
    public async Task<string> GetPlaybackFileAsync(string videoId, bool mp3, CancellationToken cancellationToken)
    {
        RequireValidId(videoId);
        var key = videoId + (mp3 ? ".mp3" : string.Empty);
        var semaphore = _playbackLocks.GetOrAdd(key, _ => new SemaphoreSlim(1, 1));
        await semaphore.WaitAsync(cancellationToken).ConfigureAwait(false);
        try
        {
            var dir = Path.Combine(_playbackCacheDir, key);
            var existing = Directory.Exists(dir) ? Directory.EnumerateFiles(dir, "audio.*").FirstOrDefault() : null;
            if (existing != null && !existing.EndsWith(".part", StringComparison.Ordinal))
            {
                Directory.SetLastWriteTimeUtc(dir, DateTime.UtcNow);
                return existing;
            }

            CleanUpPlaybackCache();
            if (Directory.Exists(dir))
            {
                Directory.Delete(dir, true);
            }

            Directory.CreateDirectory(dir);
            try
            {
                return await DownloadAudioAsync(videoId, dir, mp3 ? "mp3" : null, cancellationToken).ConfigureAwait(false);
            }
            catch
            {
                Directory.Delete(dir, true);
                throw;
            }
        }
        finally
        {
            semaphore.Release();
        }
    }

    /// <summary>Runs yt-dlp with <paramref name="args"/>, signed in via the configured cookies if possible.</summary>
    public async Task<string> RunAsync(IReadOnlyList<string> args, CancellationToken cancellationToken)
    {
        var executable = FindExecutable() ?? throw new InvalidOperationException(
            Config.YtDlpPath.Trim().Length > 0 ? $"yt-dlp not found at {Config.YtDlpPath}" : "yt-dlp is not installed on the server");
        if (executable == BundledPath)
        {
            SelfUpdateInBackground(executable);
        }

        args = [.. FfmpegArguments(), .. JsRuntimeArguments(), .. args];
        var (cookieArgs, cookiesSource, cookiesProblem) = CookiesArguments();
        if (cookiesProblem != null)
        {
            CookiesWarning = cookiesProblem;
            _logger.LogWarning("{Warning}", CookiesWarning);
        }
        else if (cookieArgs != null)
        {
            var signedIn = await RunProcessAsync(executable, [.. cookieArgs, .. args], cancellationToken)
                .ConfigureAwait(false);
            if (signedIn.ExitCode == 0)
            {
                CookiesWarning = null;
                return signedIn.Output;
            }

            if (IsCookieError(signedIn.Error))
            {
                // Don't fail every request because e.g. the browser profile isn't mounted into the container
                CookiesWarning = $"Could not read {cookiesSource}, downloading without signing in: {LastLine(signedIn.Error, signedIn.ExitCode)}";
            }
            else if (signedIn.Error.Contains("Requested format is not available", StringComparison.Ordinal))
            {
                // Signed in, yt-dlp uses YouTube clients whose formats need a JavaScript runtime
                CookiesWarning = "YouTube offered no usable formats when signed in (yt-dlp needs a JavaScript runtime such as Deno for that), downloading without signing in";
            }
            else
            {
                throw new IOException("yt-dlp failed: " + LastLine(signedIn.Error, signedIn.ExitCode));
            }

            _logger.LogWarning("{Warning}", CookiesWarning);
        }

        var result = await RunProcessAsync(executable, args, cancellationToken).ConfigureAwait(false);
        if (result.ExitCode != 0)
        {
            throw new IOException("yt-dlp failed: " + LastLine(result.Error, result.ExitCode));
        }

        return result.Output;
    }

    /// <summary>Points yt-dlp at the bundled Deno; a Deno on the PATH it finds by itself.</summary>
    private static string[] JsRuntimeArguments()
    {
        var deno = BundledDenoPath;
        return deno != null && File.Exists(deno) && EnsureExecutable(deno) ? ["--js-runtimes", "deno:" + deno] : [];
    }

    /// <summary>Points yt-dlp at Jellyfin's ffmpeg (and the ffprobe next to it) unless both are on the PATH.</summary>
    private string[] FfmpegArguments()
    {
        string Exe(string name) => OperatingSystem.IsWindows() ? name + ".exe" : name;
        var ffmpeg = _mediaEncoder.EncoderPath;
        if ((FindOnPath(Exe("ffmpeg")) != null && FindOnPath(Exe("ffprobe")) != null) || string.IsNullOrEmpty(ffmpeg))
        {
            return [];
        }

        return ["--ffmpeg-location", Path.GetDirectoryName(ffmpeg) ?? ffmpeg];
    }

    /// <summary>
    /// Returns the yt-dlp arguments for the configured cookies (the cookies file, else the browser) and a description
    /// of where they come from, or a problem to warn about if the cookies file can't be used.
    /// </summary>
    private (string[]? Args, string? Source, string? Problem) CookiesArguments()
    {
        var file = Config.CookiesFile.Trim();
        if (file.Length > 0)
        {
            if (!File.Exists(file))
            {
                return (null, null, $"Cookies file {file} not found, downloading without signing in");
            }

            try
            {
                return (["--cookies", WritableCookiesCopy(file)], "cookies file " + file, null);
            }
            catch (Exception e) when (e is IOException or UnauthorizedAccessException)
            {
                return (null, null, $"Could not read cookies file {file}, downloading without signing in: {e.Message}");
            }
        }

        var browser = Config.CookiesFromBrowser.Trim();
        if (browser.Length == 0)
        {
            return (null, null, null);
        }

        var profile = Config.BrowserProfile.Trim();
        return (["--cookies-from-browser", profile.Length > 0 ? browser + ":" + profile : browser], browser + " cookies", null);
    }

    /// <summary>
    /// yt-dlp saves the cookie jar back to its <c>--cookies</c> file (and fails on a read-only mount), so it gets a copy
    /// in the cache, refreshed whenever the original changes.
    /// </summary>
    private string WritableCookiesCopy(string file)
    {
        var copy = Path.Combine(_playbackCacheDir, "cookies.txt");
        lock (_cookiesCopyLock)
        {
            var modified = File.GetLastWriteTimeUtc(file);
            if (file != _cookiesCopySource || modified != _cookiesCopyModified || !File.Exists(copy))
            {
                Directory.CreateDirectory(_playbackCacheDir);
                File.Copy(file, copy + ".tmp", true);
                File.Move(copy + ".tmp", copy, true);
                _cookiesCopySource = file;
                _cookiesCopyModified = modified;
            }
        }

        return copy;
    }

    /// <summary>Identifies the configured cookies, to notice when they change.</summary>
    private static string CookiesKey()
    {
        var file = Config.CookiesFile.Trim();
        if (file.Length > 0)
        {
            return "file:" + file + (File.Exists(file) ? ":" + File.GetLastWriteTimeUtc(file).Ticks.ToString(CultureInfo.InvariantCulture) : string.Empty);
        }

        return "browser:" + Config.CookiesFromBrowser.Trim() + ":" + Config.BrowserProfile.Trim();
    }

    private static bool IsCookieError(string error) =>
        error.Contains("cookies database", StringComparison.OrdinalIgnoreCase)
        || error.Contains("cookie database", StringComparison.OrdinalIgnoreCase)
        || error.Contains("cookies from browser", StringComparison.OrdinalIgnoreCase)
        || error.Contains("unsupported browser", StringComparison.OrdinalIgnoreCase)
        || error.Contains("failed to decrypt", StringComparison.OrdinalIgnoreCase)
        || error.Contains("netscape format", StringComparison.OrdinalIgnoreCase)
        || error.Contains("cookies file", StringComparison.OrdinalIgnoreCase);

    private void InvalidateCachesIfCookiesChanged()
    {
        var key = CookiesKey();
        if (key != _lastCookiesKey)
        {
            // Signed in, other (e.g. Premium) streams may be available
            _lastCookiesKey = key;
            _streamInfoCache.Clear();
        }
    }

    private void SelfUpdateInBackground(string executable)
    {
        if (DateTime.UtcNow - _lastSelfUpdate < SelfUpdateInterval)
        {
            return;
        }

        _lastSelfUpdate = DateTime.UtcNow;
        // YouTube changes often break older yt-dlp versions; only the standalone release we ship can update itself
        _ = Task.Run(async () =>
        {
            try
            {
                var result = await RunProcessAsync(executable, ["-U"], CancellationToken.None).ConfigureAwait(false);
                _logger.LogInformation("yt-dlp self-update: {Output}", LastLine(result.Output, result.ExitCode));
            }
            catch (Exception e)
            {
                _logger.LogWarning(e, "yt-dlp self-update failed");
            }
        });
    }

    private void CleanUpPlaybackCache()
    {
        if (!Directory.Exists(_playbackCacheDir))
        {
            return;
        }

        foreach (var dir in Directory.EnumerateDirectories(_playbackCacheDir))
        {
            try
            {
                if (DateTime.UtcNow - Directory.GetLastWriteTimeUtc(dir) > PlaybackCacheLifetime)
                {
                    Directory.Delete(dir, true);
                }
            }
            catch (IOException e)
            {
                _logger.LogDebug(e, "Could not delete {Dir}", dir);
            }
        }
    }

    private async Task<(int ExitCode, string Output, string Error)> RunProcessAsync(
        string executable,
        IEnumerable<string> args,
        CancellationToken cancellationToken)
    {
        var startInfo = new ProcessStartInfo(executable)
        {
            RedirectStandardOutput = true,
            RedirectStandardError = true,
            UseShellExecute = false,
            CreateNoWindow = true,
        };
        // Deno's cache would default to the home folder, which may not be writable for the user Jellyfin runs as
        startInfo.Environment["DENO_DIR"] = _denoCacheDir;
        foreach (var arg in args)
        {
            startInfo.ArgumentList.Add(arg);
        }

        using var process = Process.Start(startInfo) ?? throw new IOException("Could not start " + executable);
        try
        {
            var output = process.StandardOutput.ReadToEndAsync(cancellationToken);
            var error = process.StandardError.ReadToEndAsync(cancellationToken);
            await process.WaitForExitAsync(cancellationToken).ConfigureAwait(false);
            return (process.ExitCode, await output.ConfigureAwait(false), await error.ConfigureAwait(false));
        }
        finally
        {
            if (!process.HasExited)
            {
                process.Kill(true);
            }
        }
    }

    private static string LastLine(string output, int exitCode) =>
        output.Split('\n', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries).LastOrDefault()
        ?? "exit code " + exitCode.ToString(CultureInfo.InvariantCulture);

    /// <summary>Maps yt-dlp codec ids (e.g. <c>mp4a.40.2</c>, <c>opus</c>) to display names.</summary>
    private static string? CodecName(string acodec) => acodec switch
    {
        "none" or "NA" => null,
        _ when acodec.StartsWith("mp4a", StringComparison.Ordinal) => "AAC",
        "opus" => "Opus",
        "vorbis" => "Vorbis",
        _ when acodec.StartsWith("mp3", StringComparison.Ordinal) => "MP3",
        _ when acodec.StartsWith("ac-3", StringComparison.Ordinal) => "AC-3",
        _ when acodec.StartsWith("ec-3", StringComparison.Ordinal) => "E-AC-3",
        _ => acodec.ToUpperInvariant(),
    };

    private static string? FindOnPath(string executable) =>
        (Environment.GetEnvironmentVariable("PATH") ?? string.Empty)
            .Split(Path.PathSeparator, StringSplitOptions.RemoveEmptyEntries)
            .Select(dir => Path.Combine(dir, executable))
            .FirstOrDefault(File.Exists);

    private static void RequireValidId(string videoId)
    {
        if (!IsValidVideoId(videoId))
        {
            throw new ArgumentException("Invalid YouTube video id: " + videoId, nameof(videoId));
        }
    }

    [GeneratedRegex("^[A-Za-z0-9_-]{1,64}$")]
    private static partial Regex VideoIdRegex();
}
