using System;
using System.Collections.Generic;
using System.IO;
using System.Net.Http;
using System.Net.Mime;
using System.Security.Claims;
using System.Threading;
using System.Threading.Tasks;
using Jellyfin.Plugin.KMusicPlayer.Services;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;
using Microsoft.Extensions.Logging;

namespace Jellyfin.Plugin.KMusicPlayer.Api;

/// <summary>
/// Endpoints used by KMusicPlayer clients: the server searches YouTube Music for them, downloads the tracks
/// they pick into the library and streams tracks for playback.
/// </summary>
[ApiController]
[Authorize]
[Route("KMusicPlayer")]
[Produces(MediaTypeNames.Application.Json)]
public class KMusicPlayerController : ControllerBase
{
    private readonly YtDlpService _ytDlp;
    private readonly YouTubeMusicClient _youTubeMusic;
    private readonly DownloadManager _downloads;
    private readonly ILogger<KMusicPlayerController> _logger;

    public KMusicPlayerController(
        YtDlpService ytDlp,
        YouTubeMusicClient youTubeMusic,
        DownloadManager downloads,
        ILogger<KMusicPlayerController> logger)
    {
        _logger = logger;
        _ytDlp = ytDlp;
        _youTubeMusic = youTubeMusic;
        _downloads = downloads;
    }

    /// <summary>Tells clients the plugin is installed and whether the user may download.</summary>
    [HttpGet("Info")]
    public async Task<ActionResult<PluginInfoDto>> GetInfo(CancellationToken cancellationToken)
    {
        return new PluginInfoDto
        {
            Version = Plugin.Instance!.Version.ToString(),
            YtDlpAvailable = YtDlpService.IsAvailable,
            YtDlpVersion = await _ytDlp.GetVersionAsync(cancellationToken).ConfigureAwait(false),
            CanDownload = MayDownload(),
            DownloadDirectory = _downloads.GetDownloadDirectory(),
            Warning = _ytDlp.CookiesWarning,
        };
    }

    /// <summary>Searches YouTube Music for songs.</summary>
    [HttpGet("YouTube/Search")]
    public async Task<ActionResult<List<YouTubeSongDto>>> Search([FromQuery] string query, CancellationToken cancellationToken)
    {
        if (string.IsNullOrWhiteSpace(query))
        {
            return new List<YouTubeSongDto>();
        }

        try
        {
            return await _youTubeMusic.SearchSongsAsync(query, cancellationToken).ConfigureAwait(false);
        }
        catch (HttpRequestException e)
        {
            return Problem(e.Message, statusCode: StatusCodes.Status502BadGateway);
        }
    }

    /// <summary>Queues a download of a YouTube Music track (a result of <see cref="Search"/>) into the music library.</summary>
    [HttpPost("Downloads")]
    [ProducesResponseType(StatusCodes.Status202Accepted)]
    public ActionResult<DownloadJobDto> RequestDownload([FromBody] YouTubeSongDto song)
    {
        if (!MayDownload())
        {
            return Problem("Only administrators may download into the library (see the KMusicPlayer plugin settings)", statusCode: StatusCodes.Status403Forbidden);
        }

        try
        {
            var job = _downloads.Enqueue(song, UserId);
            return AcceptedAtAction(nameof(GetDownload), new { id = job.Id }, job);
        }
        catch (ArgumentException e)
        {
            return Problem(e.Message, statusCode: StatusCodes.Status400BadRequest);
        }
        catch (InvalidOperationException e)
        {
            return Problem(e.Message, statusCode: StatusCodes.Status503ServiceUnavailable);
        }
    }

    [HttpGet("Downloads")]
    public ActionResult<IReadOnlyList<DownloadJobDto>> GetDownloads() => Ok(_downloads.GetJobs(UserId));

    [HttpGet("Downloads/{id}")]
    public ActionResult<DownloadJobDto> GetDownload([FromRoute] Guid id) =>
        _downloads.GetJob(id, UserId) is { } job ? job : NotFound();

    /// <summary>Codec and bitrate of the stream a download would get (YouTube's search results don't say).</summary>
    [HttpGet("YouTube/{videoId}/StreamInfo")]
    [ProducesResponseType(StatusCodes.Status200OK)]
    [ProducesResponseType(StatusCodes.Status204NoContent)]
    public async Task<ActionResult<AudioStreamInfo>> GetStreamInfo([FromRoute] string videoId, CancellationToken cancellationToken)
    {
        if (!YtDlpService.IsValidVideoId(videoId))
        {
            return BadRequest();
        }

        try
        {
            var info = await _ytDlp.GetAudioStreamInfoAsync(videoId, cancellationToken).ConfigureAwait(false);
            return info != null ? info : NoContent();
        }
        catch (OperationCanceledException) when (cancellationToken.IsCancellationRequested)
        {
            // The client lost interest, e.g. the search results scrolled out of view
            return NoContent();
        }
        catch (IOException e)
        {
            // Only informational, clients just don't show codec and bitrate
            _logger.LogWarning("No stream info for {VideoId}: {Message}", videoId, e.Message);
            return NoContent();
        }
    }

    /// <summary>
    /// Audio of a YouTube Music track for clients that can't run yt-dlp themselves. <paramref name="format"/>
    /// "mp3" converts it for players without Opus/AAC support; otherwise the original stream is served.
    /// </summary>
    [HttpGet("YouTube/{videoId}/Audio")]
    public async Task<ActionResult> GetAudio([FromRoute] string videoId, [FromQuery] string? format, CancellationToken cancellationToken)
    {
        if (!YtDlpService.IsValidVideoId(videoId))
        {
            return BadRequest();
        }

        try
        {
            var path = await _ytDlp.GetPlaybackFileAsync(videoId, format == "mp3", cancellationToken).ConfigureAwait(false);
            return PhysicalFile(path, ContentTypeOf(path), enableRangeProcessing: true);
        }
        catch (OperationCanceledException) when (cancellationToken.IsCancellationRequested)
        {
            // The client skipped the track before it was ready
            return new EmptyResult();
        }
    }

    private Guid UserId =>
        Guid.TryParse(User.FindFirstValue("Jellyfin-UserId"), out var id) ? id : Guid.Empty;

    private bool MayDownload() =>
        User.IsInRole("Administrator") || Plugin.Instance!.Configuration.AllowNonAdminDownloads;

    private static string ContentTypeOf(string path) => Path.GetExtension(path).ToLowerInvariant() switch
    {
        ".opus" or ".ogg" or ".oga" => "audio/ogg",
        ".m4a" or ".mp4" => "audio/mp4",
        ".webm" => "audio/webm",
        ".mp3" => "audio/mpeg",
        _ => "application/octet-stream",
    };
}
