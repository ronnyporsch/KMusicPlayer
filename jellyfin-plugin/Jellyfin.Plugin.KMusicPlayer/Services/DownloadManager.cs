using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Net.Http;
using System.Text.RegularExpressions;
using System.Threading;
using System.Threading.Tasks;
using Jellyfin.Plugin.KMusicPlayer.Api;
using MediaBrowser.Common.Net;
using MediaBrowser.Controller.Library;
using MediaBrowser.Model.Tasks;
using Microsoft.Extensions.Logging;

namespace Jellyfin.Plugin.KMusicPlayer.Services;

/// <summary>
/// Queues downloads requested by clients, writes tagged files into the music library and triggers a library scan.
/// </summary>
public partial class DownloadManager
{
    private const int MaxRetainedJobs = 200;
    private static readonly TimeSpan ImportTimeout = TimeSpan.FromMinutes(3);
    private static readonly TimeSpan AlbumTracksLifetime = TimeSpan.FromDays(1);
    private static readonly string[] CoverHostSuffixes = [".googleusercontent.com", ".ytimg.com", ".ggpht.com"];

    private readonly ILogger<DownloadManager> _logger;
    private readonly YtDlpService _ytDlp;
    private readonly YouTubeMusicClient _youTubeMusic;
    private readonly ILibraryManager _libraryManager;
    private readonly ITaskManager _taskManager;
    private readonly IHttpClientFactory _httpClientFactory;
    private readonly DownloadIndex _index;
    private readonly ConcurrentDictionary<Guid, Job> _jobs = new();
    private readonly SemaphoreSlim _slots;
    /// <summary>Video ids of album tracks, by album id; albums rarely change, so look each up at most daily.</summary>
    private readonly ConcurrentDictionary<string, (DateTime Fetched, IReadOnlyList<string> VideoIds)> _albumTracks = new();
    private readonly SemaphoreSlim _albumLookups = new(4, 4);

    public DownloadManager(
        ILogger<DownloadManager> logger,
        YtDlpService ytDlp,
        YouTubeMusicClient youTubeMusic,
        ILibraryManager libraryManager,
        ITaskManager taskManager,
        IHttpClientFactory httpClientFactory,
        DownloadIndex index)
    {
        _logger = logger;
        _ytDlp = ytDlp;
        _youTubeMusic = youTubeMusic;
        _libraryManager = libraryManager;
        _taskManager = taskManager;
        _httpClientFactory = httpClientFactory;
        _index = index;
        var slots = Math.Clamp(Plugin.Instance?.Configuration.MaxConcurrentDownloads ?? 2, 1, 8);
        _slots = new SemaphoreSlim(slots, slots);
    }

    /// <summary>Returns the folder downloads go to: the configured one or the first folder of the first music library.</summary>
    public string? GetDownloadDirectory()
    {
        var configured = Plugin.Instance?.Configuration.DownloadDirectory.Trim();
        if (!string.IsNullOrEmpty(configured))
        {
            return configured;
        }

        return _libraryManager.GetVirtualFolders()
            .Where(folder => folder.CollectionType?.ToString() == "music")
            .SelectMany(folder => folder.Locations)
            .FirstOrDefault();
    }

    public DownloadJobDto Enqueue(YouTubeSongDto song, Guid userId)
    {
        if (!YtDlpService.IsValidVideoId(song.VideoId))
        {
            throw new ArgumentException("Invalid YouTube video id");
        }

        if (string.IsNullOrWhiteSpace(song.Title))
        {
            throw new ArgumentException("A title is required");
        }

        var targetDir = GetDownloadDirectory()
            ?? throw new InvalidOperationException("No download folder configured and no music library found");

        // Requesting the same track again while it's being downloaded just returns the running job
        var running = _jobs.Values.FirstOrDefault(job =>
            job.UserId == userId && job.Dto.VideoId == song.VideoId && job.Dto.State is DownloadState.Queued or DownloadState.Downloading);
        if (running != null)
        {
            return running.Dto;
        }

        var dto = new DownloadJobDto
        {
            Id = Guid.NewGuid(),
            VideoId = song.VideoId,
            Title = song.Title,
            State = DownloadState.Queued,
            CreatedAt = DateTime.UtcNow,
        };
        var newJob = new Job(dto, userId);
        _jobs[dto.Id] = newJob;
        PruneJobs();
        _ = Task.Run(() => ProcessAsync(newJob, song, targetDir));
        return dto;
    }

    /// <summary>Returns those of <paramref name="videoIds"/> that are already in the download folder.</summary>
    public IReadOnlyList<string> FindDownloaded(IEnumerable<string> videoIds) =>
        _index.FindDownloaded(videoIds, GetDownloadDirectory());

    /// <summary>Returns those of <paramref name="albumIds"/> whose tracks are all in the download folder.</summary>
    public async Task<IReadOnlyList<string>> FindDownloadedAlbumsAsync(IEnumerable<string> albumIds, CancellationToken cancellationToken)
    {
        var albums = await Task.WhenAll(albumIds.Distinct().Select(async albumId =>
            (Id: albumId, VideoIds: await GetAlbumTracksAsync(albumId, cancellationToken).ConfigureAwait(false)))).ConfigureAwait(false);
        var downloaded = FindDownloaded(albums.SelectMany(album => album.VideoIds)).ToHashSet();
        return albums
            .Where(album => album.VideoIds.Count > 0 && album.VideoIds.All(downloaded.Contains))
            .Select(album => album.Id)
            .ToList();
    }

    private async Task<IReadOnlyList<string>> GetAlbumTracksAsync(string albumId, CancellationToken cancellationToken)
    {
        if (_albumTracks.TryGetValue(albumId, out var cached) && DateTime.UtcNow - cached.Fetched < AlbumTracksLifetime)
        {
            return cached.VideoIds;
        }

        // Don't hit YouTube Music with dozens of requests at once when a client opens an artist
        await _albumLookups.WaitAsync(cancellationToken).ConfigureAwait(false);
        try
        {
            var album = await GetAlbumAsync(albumId, cancellationToken).ConfigureAwait(false);
            if (album == null)
            {
                return [];
            }

            // Unavailable tracks can't be downloaded, so they don't count
            var videoIds = album.Tracks.Select(track => track.VideoId).OfType<string>().ToList();
            _albumTracks[albumId] = (DateTime.UtcNow, videoIds);
            return videoIds;
        }
        finally
        {
            _albumLookups.Release();
        }
    }

    public DownloadJobDto? GetJob(Guid id, Guid userId) =>
        _jobs.TryGetValue(id, out var job) && job.UserId == userId ? job.Dto : null;

    public IReadOnlyList<DownloadJobDto> GetJobs(Guid userId) =>
        _jobs.Values.Where(job => job.UserId == userId).Select(job => job.Dto).OrderByDescending(dto => dto.CreatedAt).ToList();

    private async Task ProcessAsync(Job job, YouTubeSongDto song, string targetDir)
    {
        try
        {
            await _slots.WaitAsync().ConfigureAwait(false);
            string target;
            try
            {
                job.Dto.State = DownloadState.Downloading;
                target = await DownloadAsync(song, targetDir, CancellationToken.None).ConfigureAwait(false);
            }
            finally
            {
                _slots.Release();
            }

            job.Dto.Path = target;
            _index.Add(song.VideoId, target);
            job.Dto.State = DownloadState.Importing;
            job.Dto.Message = "Saved to " + target + ", scanning library";
            _logger.LogInformation("Downloaded {VideoId} to {Path}", song.VideoId, target);

            var itemId = await ImportAsync(target).ConfigureAwait(false);
            job.Dto.ItemId = itemId?.ToString("N");
            job.Dto.Message = itemId != null
                ? "Saved to " + target + " and added to the library"
                : "Saved to " + target + ", it will appear once the library scan finishes";
            if (_ytDlp.CookiesWarning != null)
            {
                job.Dto.Message += ". " + _ytDlp.CookiesWarning;
            }

            job.Dto.State = DownloadState.Completed;
        }
        catch (Exception e)
        {
            _logger.LogError(e, "Downloading {VideoId} failed", song.VideoId);
            job.Dto.Message = e.Message;
            job.Dto.State = DownloadState.Failed;
        }
    }

    /// <summary>
    /// Downloads the track in its original codec (no re-encoding), tags it (with details of its album if it can
    /// be looked up) and moves it to
    /// <c>targetDir/Album Artist/YYYY - Album/NN Title.ext</c> (see <see cref="GetRelativePath"/>). Returns the file path.
    /// </summary>
    private async Task<string> DownloadAsync(YouTubeSongDto song, string targetDir, CancellationToken cancellationToken)
    {
        var workDir = Directory.CreateTempSubdirectory("kmusicplayer-dl").FullName;
        try
        {
            var audio = await _ytDlp.DownloadAudioAsync(song.VideoId, workDir, null, cancellationToken).ConfigureAwait(false);
            var album = await GetAlbumAsync(song.AlbumId, cancellationToken).ConfigureAwait(false);
            var tags = TrackTagsBuilder.Build(song, album);
            // The YouTube Music search thumbnail is the square album cover; the video thumbnail often isn't
            var cover = await DownloadCoverAsync(
                song.ThumbnailUrl != null ? YouTubeMusicClient.ResizeThumbnail(song.ThumbnailUrl, 544) : null,
                cancellationToken).ConfigureAwait(false);

            AudioTagger.Write(audio, tags, cover);

            var target = Path.Combine(targetDir, GetRelativePath(tags) + Path.GetExtension(audio).ToLowerInvariant());
            Directory.CreateDirectory(Path.GetDirectoryName(target)!);
            // Copy under a non-audio extension first so the library scan never picks up a half-written file
            var partial = target + ".part";
            File.Copy(audio, partial, true);
            File.Move(partial, target, true);
            return target;
        }
        finally
        {
            try
            {
                Directory.Delete(workDir, true);
            }
            catch (IOException e)
            {
                _logger.LogDebug(e, "Could not delete {Dir}", workDir);
            }
        }
    }

    /// <summary>
    /// Returns the path of the file without extension, following the Picard naming script
    /// <code>
    /// $if2($getmulti(%albumartists%,0),$getmulti(%artists%,0),%artist%)/
    /// $if(%album%,$if(%date%,$left(%date%,4) - ,)%album%/,)
    /// $if($gt(%totaldiscs%,1),CD %discnumber%/,)
    /// $if($and(%album%,%tracknumber%),$num(%tracknumber%,2) ,)
    /// %title%
    /// </code>
    /// The disc folder never applies: YouTube Music albums have no discs.
    /// </summary>
    private static string GetRelativePath(TrackTags tags)
    {
        var artist = new[] { tags.AlbumArtists.FirstOrDefault(), tags.Artists.FirstOrDefault(), tags.Artist }
            .FirstOrDefault(name => !string.IsNullOrWhiteSpace(name)) ?? "Unknown Artist";
        var parts = new List<string> { artist };

        var hasAlbum = !string.IsNullOrWhiteSpace(tags.Album);
        if (hasAlbum)
        {
            parts.Add(tags.Year != null ? $"{tags.Year.Value:0000} - {tags.Album}" : tags.Album!);
        }

        var trackPrefix = hasAlbum && tags.TrackNumber != null
            ? tags.TrackNumber.Value.ToString("00", System.Globalization.CultureInfo.InvariantCulture) + " "
            : string.Empty;
        parts.Add(trackPrefix + tags.Title);

        return Path.Combine(parts.Select(SanitizeFileName).ToArray());
    }

    private async Task<YouTubeAlbum?> GetAlbumAsync(string albumId, CancellationToken cancellationToken)
    {
        if (string.IsNullOrWhiteSpace(albumId))
        {
            return null;
        }

        try
        {
            return await _youTubeMusic.GetAlbumAsync(albumId, cancellationToken).ConfigureAwait(false);
        }
        catch (Exception e) when (e is HttpRequestException or System.Text.Json.JsonException)
        {
            // Tag what the search result provides
            _logger.LogWarning(e, "Could not look up album {AlbumId}", albumId);
            return null;
        }
    }

    private async Task<byte[]?> DownloadCoverAsync(string? url, CancellationToken cancellationToken)
    {
        // Only fetch from YouTube's image hosts: the URL is part of the client's request
        if (!Uri.TryCreate(url, UriKind.Absolute, out var uri)
            || uri.Scheme != Uri.UriSchemeHttps
            || !CoverHostSuffixes.Any(suffix => uri.Host.EndsWith(suffix, StringComparison.OrdinalIgnoreCase)))
        {
            return null;
        }

        try
        {
            var client = _httpClientFactory.CreateClient(NamedClient.Default);
            return await client.GetByteArrayAsync(uri, cancellationToken).ConfigureAwait(false);
        }
        catch (HttpRequestException e)
        {
            _logger.LogWarning(e, "Could not download cover {Url}", url);
            return null;
        }
    }

    /// <summary>Queues a library scan and waits (bounded) for the track to show up. Returns its item id if it did.</summary>
    private async Task<Guid?> ImportAsync(string path)
    {
        var scan = _taskManager.ScheduledTasks.FirstOrDefault(task => task.ScheduledTask.Key == "RefreshLibrary");
        if (scan != null)
        {
            // Runs again after the current scan if one is in progress, which may have passed the folder already
            _taskManager.QueueScheduledTask(scan.ScheduledTask, new TaskOptions());
        }
        else
        {
            _ = _libraryManager.ValidateMediaLibrary(new Progress<double>(), CancellationToken.None);
        }

        var deadline = DateTime.UtcNow + ImportTimeout;
        while (DateTime.UtcNow < deadline)
        {
            await Task.Delay(TimeSpan.FromSeconds(2)).ConfigureAwait(false);
            var item = _libraryManager.FindByPath(path, false);
            if (item != null)
            {
                return item.Id;
            }
        }

        return null;
    }

    private void PruneJobs()
    {
        foreach (var old in _jobs.Values
                     .Where(job => job.Dto.State is DownloadState.Completed or DownloadState.Failed)
                     .OrderByDescending(job => job.Dto.CreatedAt)
                     .Skip(MaxRetainedJobs))
        {
            _jobs.TryRemove(old.Dto.Id, out _);
        }
    }

    private static string SanitizeFileName(string name)
    {
        var sanitized = InvalidFileNameChars().Replace(name, "_").Trim().Trim('.');
        if (sanitized.Length > 120)
        {
            sanitized = sanitized[..120];
        }

        return sanitized.Length == 0 ? "Unknown" : sanitized;
    }

    [GeneratedRegex(@"[\\/:*?""<>|\p{Cc}]")]
    private static partial Regex InvalidFileNameChars();

    private sealed record Job(DownloadJobDto Dto, Guid UserId);
}
