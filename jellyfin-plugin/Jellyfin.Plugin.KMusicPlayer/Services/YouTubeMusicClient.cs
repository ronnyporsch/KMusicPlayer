using System;
using System.Collections.Generic;
using System.Globalization;
using System.Linq;
using System.Net.Http;
using System.Net.Mime;
using System.Text;
using System.Text.Json.Nodes;
using System.Text.RegularExpressions;
using System.Threading;
using System.Threading.Tasks;
using Jellyfin.Plugin.KMusicPlayer.Api;
using MediaBrowser.Common.Net;

namespace Jellyfin.Plugin.KMusicPlayer.Services;

public record YouTubeAlbumTrack(string? VideoId, string Title, int? TrackNumber);

public record YouTubeAlbum(
    string Title,
    /// <summary>Artist credit as displayed, e.g. "Daft Punk, Pharrell Williams &amp; Nile Rodgers".</summary>
    string Artist,
    IReadOnlyList<YouTubeArtistDto> Artists,
    /// <summary>As displayed by YouTube Music, e.g. "Album", "Single" or "EP".</summary>
    string? ReleaseType,
    int? Year,
    int? TrackCount,
    IReadOnlyList<YouTubeAlbumTrack> Tracks);

/// <summary>
/// Minimal client for the YouTube Music web (InnerTube) API: searching songs and looking up their albums (for tagging).
/// </summary>
public partial class YouTubeMusicClient
{
    private const string BaseUrl = "https://music.youtube.com";
    private const string ClientVersion = "1.20260101.01.00";
    // Restricts search results to the "Songs" shelf
    private const string SongsFilterParams = "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D";
    private static readonly HashSet<string> JoinPhrases =
        [",", "&", "and", "x", "feat.", "feat", "ft.", "ft", "featuring", "with", "vs.", "vs"];

    private readonly IHttpClientFactory _httpClientFactory;

    public YouTubeMusicClient(IHttpClientFactory httpClientFactory)
    {
        _httpClientFactory = httpClientFactory;
    }

    public static string WatchUrl(string videoId) => BaseUrl + "/watch?v=" + videoId;

    /// <summary>YouTube thumbnail URLs encode their size (e.g. <c>=w60-h60-l90-rj</c>); request a specific one instead.</summary>
    public static string ResizeThumbnail(string url, int size) =>
        ThumbnailSizeRegex().Replace(url, $"=w{size}-h{size}");

    public async Task<List<YouTubeSongDto>> SearchSongsAsync(string query, CancellationToken cancellationToken)
    {
        var root = await PostAsync("search", new JsonObject { ["query"] = query, ["params"] = SongsFilterParams }, cancellationToken)
            .ConfigureAwait(false);
        var sections = Path(root, "contents", "tabbedSearchResultsRenderer", "tabs").Items().FirstOrDefault()
            .Pipe(tab => Path(tab, "tabRenderer", "content", "sectionListRenderer", "contents"))
            .Items();
        return sections
            .SelectMany(section => Path(section, "musicShelfRenderer", "contents").Items())
            .Select(item => Path(item, "musicResponsiveListItemRenderer"))
            .Select(ParseSong)
            .OfType<YouTubeSongDto>()
            .DistinctBy(song => song.VideoId)
            .ToList();
    }

    /// <summary>Looks up an album by its browse id (see <see cref="YouTubeSongDto.AlbumId"/>), or returns null if unavailable.</summary>
    public async Task<YouTubeAlbum?> GetAlbumAsync(string albumId, CancellationToken cancellationToken)
    {
        var root = await PostAsync("browse", new JsonObject { ["browseId"] = albumId }, cancellationToken).ConfigureAwait(false);
        return ParseAlbum(root);
    }

    private async Task<JsonNode?> PostAsync(string endpoint, JsonObject parameters, CancellationToken cancellationToken)
    {
        parameters["context"] = new JsonObject
        {
            ["client"] = new JsonObject { ["clientName"] = "WEB_REMIX", ["clientVersion"] = ClientVersion, ["hl"] = "en" },
        };
        using var request = new HttpRequestMessage(HttpMethod.Post, $"{BaseUrl}/youtubei/v1/{endpoint}?prettyPrint=false")
        {
            Content = new StringContent(parameters.ToJsonString(), Encoding.UTF8, MediaTypeNames.Application.Json),
        };
        request.Headers.TryAddWithoutValidation("Origin", BaseUrl);
        request.Headers.TryAddWithoutValidation("Referer", BaseUrl + "/");
        var client = _httpClientFactory.CreateClient(NamedClient.Default);
        using var response = await client.SendAsync(request, cancellationToken).ConfigureAwait(false);
        if (!response.IsSuccessStatusCode)
        {
            throw new HttpRequestException($"YouTube Music {endpoint} failed: {(int)response.StatusCode}");
        }

        return JsonNode.Parse(await response.Content.ReadAsStringAsync(cancellationToken).ConfigureAwait(false));
    }

    private static YouTubeSongDto? ParseSong(JsonNode? item)
    {
        var columns = Path(item, "flexColumns").Items();
        List<JsonNode?> Runs(int column) =>
            Path(columns.ElementAtOrDefault(column), "musicResponsiveListItemFlexColumnRenderer", "text", "runs").Items();

        var titleRun = Runs(0).FirstOrDefault();
        var title = String(titleRun, "text");
        var videoId = String(Path(titleRun, "navigationEndpoint", "watchEndpoint"), "videoId")
            ?? String(Path(item, "playlistItemData"), "videoId");
        if (title == null || videoId == null)
        {
            return null;
        }

        // Second column: "<artist runs> • <album> • <duration>"
        var details = Runs(1);
        var artistRuns = details.TakeWhile(run => String(run, "text")?.Trim() != "•").ToList();
        var albumRun = details.FirstOrDefault(run => PageType(run) == "MUSIC_PAGE_TYPE_ALBUM");
        var duration = details.Select(run => String(run, "text")?.Trim())
            .LastOrDefault(text => text != null && DurationRegex().IsMatch(text))
            ?.Split(':').Aggregate(0, (acc, part) => (acc * 60) + int.Parse(part, CultureInfo.InvariantCulture)) ?? 0;

        return new YouTubeSongDto
        {
            VideoId = videoId,
            Title = title,
            Artist = string.Concat(artistRuns.Select(run => String(run, "text"))).Trim(),
            Artists = ParseArtists(artistRuns),
            Album = String(albumRun, "text") ?? string.Empty,
            AlbumId = BrowseId(albumRun) ?? string.Empty,
            ThumbnailUrl = String(
                Path(item, "thumbnail", "musicThumbnailRenderer", "thumbnail", "thumbnails").Items().LastOrDefault(),
                "url"),
            Duration = duration,
        };
    }

    private static YouTubeAlbum? ParseAlbum(JsonNode? root)
    {
        var tabs = Path(root, "contents", "twoColumnBrowseResultsRenderer");
        var header = Path(Path(tabs, "tabs").Items().FirstOrDefault(), "tabRenderer", "content", "sectionListRenderer", "contents")
            .Items().FirstOrDefault()
            .Pipe(section => Path(section, "musicResponsiveHeaderRenderer"));
        if (header == null)
        {
            return null;
        }

        List<JsonNode?> Runs(string key) => Path(header, key, "runs").Items();
        string Join(IEnumerable<JsonNode?> runs) => string.Concat(runs.Select(run => String(run, "text"))).Trim();

        var title = Join(Runs("title"));
        if (title.Length == 0)
        {
            return null;
        }

        var artistRuns = Runs("straplineTextOne");
        // Subtitle: "Album • 2013"
        var subtitle = Runs("subtitle").Select(run => String(run, "text")?.Trim())
            .OfType<string>().Where(text => text.Length > 0 && text != "•").ToList();

        var tracks = Path(tabs, "secondaryContents", "sectionListRenderer", "contents").Items()
            .SelectMany(section => Path(section, "musicShelfRenderer", "contents").Items())
            .Select(item => Path(item, "musicResponsiveListItemRenderer"))
            .Where(renderer => renderer != null)
            .Select(renderer => new YouTubeAlbumTrack(
                String(Path(renderer, "playlistItemData"), "videoId"),
                Join(Path(Path(renderer, "flexColumns").Items().FirstOrDefault(), "musicResponsiveListItemFlexColumnRenderer", "text", "runs").Items()),
                int.TryParse(String(Path(renderer, "index", "runs").Items().FirstOrDefault(), "text"), out var number) ? number : null))
            .Where(track => track.Title.Length > 0)
            .ToList();

        // "13 songs • 1 hour, 14 minutes"; long track lists are paginated, so prefer this count over tracks.Count
        var songCount = Runs("secondSubtitle")
            .Select(run => SongCountRegex().Match(String(run, "text") ?? string.Empty))
            .FirstOrDefault(match => match.Success)?.Groups[1].Value;
        var year = subtitle.FirstOrDefault(text => YearRegex().IsMatch(text));

        return new YouTubeAlbum(
            title,
            Join(artistRuns),
            ParseArtists(artistRuns),
            subtitle.FirstOrDefault(text => !YearRegex().IsMatch(text)),
            year != null ? int.Parse(year, CultureInfo.InvariantCulture) : null,
            songCount != null ? int.Parse(songCount, CultureInfo.InvariantCulture) : (tracks.Count > 0 ? tracks.Count : null),
            tracks);
    }

    /// <summary>Splits artist runs like "A", ", ", "B", " &amp; ", "C" into the individual artists.</summary>
    private static List<YouTubeArtistDto> ParseArtists(IEnumerable<JsonNode?> runs) => runs
        .Select(run =>
        {
            var name = String(run, "text")?.Trim() ?? string.Empty;
            if (PageType(run) == "MUSIC_PAGE_TYPE_ARTIST")
            {
                return new YouTubeArtistDto { Id = BrowseId(run) ?? string.Empty, Name = name };
            }

            return name.Length == 0 || JoinPhrases.Contains(name.ToLowerInvariant()) ? null : new YouTubeArtistDto { Name = name };
        })
        .OfType<YouTubeArtistDto>()
        .DistinctBy(artist => artist.Name)
        .ToList();

    private static JsonNode? Path(JsonNode? node, params string[] keys) =>
        keys.Aggregate(node, (current, key) => current is JsonObject obj ? obj[key] : null);

    private static string? String(JsonNode? node, string key) =>
        node is JsonObject obj && obj[key] is JsonValue value && value.TryGetValue<string>(out var text) ? text : null;

    private static string? BrowseId(JsonNode? run) => String(Path(run, "navigationEndpoint", "browseEndpoint"), "browseId");

    private static string? PageType(JsonNode? run) => String(
        Path(run, "navigationEndpoint", "browseEndpoint", "browseEndpointContextSupportedConfigs", "browseEndpointContextMusicConfig"),
        "pageType");

    [GeneratedRegex(@"=w\d+-h\d+")]
    private static partial Regex ThumbnailSizeRegex();

    [GeneratedRegex(@"^\d+(:\d{2})+$")]
    private static partial Regex DurationRegex();

    [GeneratedRegex(@"^\d{4}$")]
    private static partial Regex YearRegex();

    [GeneratedRegex(@"(\d+) songs?")]
    private static partial Regex SongCountRegex();
}

internal static class JsonNodeExtensions
{
    public static List<JsonNode?> Items(this JsonNode? node) => node is JsonArray array ? array.ToList() : [];

    public static TResult Pipe<T, TResult>(this T value, Func<T, TResult> transform) => transform(value);
}
