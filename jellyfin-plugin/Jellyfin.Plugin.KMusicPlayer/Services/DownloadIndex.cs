using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text.Json;
using System.Text.RegularExpressions;
using System.Threading;
using System.Threading.Tasks;
using MediaBrowser.Common.Configuration;
using Microsoft.Extensions.Logging;

namespace Jellyfin.Plugin.KMusicPlayer.Services;

/// <summary>
/// Knows which YouTube Music tracks are in the download folder. Downloads carry their watch URL in the comment tag,
/// which Jellyfin doesn't store, so the index reads the tags itself: once for all files, then only for new or changed
/// ones. It is saved, so restarts don't read every file again.
/// </summary>
public partial class DownloadIndex
{
    private static readonly TimeSpan RescanInterval = TimeSpan.FromMinutes(10);
    private static readonly HashSet<string> AudioExtensions =
        [".opus", ".ogg", ".oga", ".m4a", ".mp4", ".webm", ".mp3", ".flac"];

    private readonly ILogger<DownloadIndex> _logger;
    private readonly string _indexPath;
    private readonly object _lock = new();
    private Dictionary<string, Entry>? _entries;
    private Dictionary<string, string> _pathsByVideoId = [];
    private DateTime _lastScan = DateTime.MinValue;
    private Task? _scan;

    public DownloadIndex(ILogger<DownloadIndex> logger, IApplicationPaths applicationPaths)
    {
        _logger = logger;
        _indexPath = Path.Combine(applicationPaths.DataPath, "kmusicplayer", "download-index.json");
    }

    /// <summary>
    /// Returns those of <paramref name="videoIds"/> whose tracks are in <paramref name="directory"/>. Rescans it in
    /// the background if the last scan is a while ago, so the very first lookups may miss earlier downloads.
    /// </summary>
    public IReadOnlyList<string> FindDownloaded(IEnumerable<string> videoIds, string? directory)
    {
        if (directory != null)
        {
            RescanIfStale(directory);
        }

        lock (_lock)
        {
            Load();
            return videoIds.Distinct()
                .Where(id => _pathsByVideoId.TryGetValue(id, out var path) && File.Exists(path))
                .ToList();
        }
    }

    /// <summary>Records a finished download.</summary>
    public void Add(string videoId, string path)
    {
        lock (_lock)
        {
            Load();
            _entries![path] = new Entry(videoId, File.GetLastWriteTimeUtc(path).Ticks);
            _pathsByVideoId[videoId] = path;
            Save();
        }
    }

    public void RescanIfStale(string directory)
    {
        lock (_lock)
        {
            if (_scan is { IsCompleted: false } || DateTime.UtcNow - _lastScan < RescanInterval)
            {
                return;
            }

            _lastScan = DateTime.UtcNow;
            _scan = Task.Run(() => Scan(directory));
        }
    }

    private void Scan(string directory)
    {
        try
        {
            if (!Directory.Exists(directory))
            {
                return;
            }

            Dictionary<string, Entry> known;
            lock (_lock)
            {
                Load();
                known = new Dictionary<string, Entry>(_entries!);
            }

            var found = new Dictionary<string, Entry>();
            var read = 0;
            var files = Directory.EnumerateFiles(directory, "*", new EnumerationOptions { RecurseSubdirectories = true, IgnoreInaccessible = true })
                .Where(path => AudioExtensions.Contains(Path.GetExtension(path).ToLowerInvariant()));
            foreach (var path in files)
            {
                var modified = File.GetLastWriteTimeUtc(path).Ticks;
                if (known.TryGetValue(path, out var entry) && entry.Modified == modified)
                {
                    found[path] = entry;
                    continue;
                }

                found[path] = new Entry(ReadVideoId(path), modified);
                read++;
            }

            lock (_lock)
            {
                // Keep downloads added while scanning
                foreach (var (path, entry) in _entries!.Where(e => !found.ContainsKey(e.Key) && File.Exists(e.Key)))
                {
                    found[path] = entry;
                }

                _entries = found;
                Reindex();
                Save();
            }

            if (read > 0)
            {
                _logger.LogInformation("Read the tags of {Count} files in {Directory}, {Tracks} YouTube Music tracks known", read, directory, _pathsByVideoId.Count);
            }
        }
        catch (Exception e)
        {
            _logger.LogWarning(e, "Scanning {Directory} for YouTube Music downloads failed", directory);
        }
    }

    private string? ReadVideoId(string path)
    {
        try
        {
            using var file = TagLib.File.Create(path, TagLib.ReadStyle.None);
            var comment = file.Tag.Comment;
            return comment != null && WatchUrlRegex().Match(comment) is { Success: true } match ? match.Groups[1].Value : null;
        }
        catch (Exception e)
        {
            _logger.LogDebug(e, "Could not read the tags of {Path}", path);
            return null;
        }
    }

    // Callers hold _lock
    private void Load()
    {
        if (_entries != null)
        {
            return;
        }

        try
        {
            _entries = File.Exists(_indexPath)
                ? JsonSerializer.Deserialize<Dictionary<string, Entry>>(File.ReadAllText(_indexPath)) ?? []
                : [];
        }
        catch (Exception e) when (e is IOException or JsonException)
        {
            _logger.LogWarning(e, "Could not read {Path}, rebuilding it", _indexPath);
            _entries = [];
        }

        Reindex();
    }

    private void Reindex()
    {
        _pathsByVideoId = _entries!
            .Where(entry => entry.Value.VideoId != null)
            .GroupBy(entry => entry.Value.VideoId!)
            .ToDictionary(group => group.Key, group => group.First().Key);
    }

    private void Save()
    {
        try
        {
            Directory.CreateDirectory(Path.GetDirectoryName(_indexPath)!);
            var partial = _indexPath + ".part";
            File.WriteAllText(partial, JsonSerializer.Serialize(_entries));
            File.Move(partial, _indexPath, true);
        }
        catch (IOException e)
        {
            _logger.LogWarning(e, "Could not save {Path}", _indexPath);
        }
    }

    [GeneratedRegex(@"youtube\.com/watch\?(?:.*&)?v=([\w-]{11})")]
    private static partial Regex WatchUrlRegex();

    /// <summary>A file of the download folder; <see cref="VideoId"/> is null for files that aren't YouTube Music downloads.</summary>
    private sealed record Entry(string? VideoId, long Modified);
}
