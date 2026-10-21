package de.ronnyporsch.open_rgb.kmusicplayer.data.youtube

import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.Artist
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.Song
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.YOUTUBE_SONG_ID_PREFIX
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*

/**
 * Minimal client for the YouTube Music web (InnerTube) API — only used for searching songs
 * and looking up their albums (for tagging downloads).
 * Playback and downloads are handled by the platform (see [de.ronnyporsch.open_rgb.kmusicplayer.platform.YouTubeDownloader]).
 */
class YouTubeMusicApi {

    private val client = HttpClient()

    suspend fun searchSongs(query: String): List<Song> = parseSongs(
        post("search") {
            put("query", query)
            put("params", SONGS_FILTER_PARAMS)
        },
    )

    /** Looks up an album by its browse id (see [Song.albumId] of search results), or returns null if unavailable. */
    suspend fun getAlbum(albumId: String): YouTubeAlbum? = parseAlbum(post("browse") { put("browseId", albumId) })

    private suspend fun post(endpoint: String, params: JsonObjectBuilder.() -> Unit): JsonElement {
        val body = buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", "WEB_REMIX")
                    put("clientVersion", CLIENT_VERSION)
                    put("hl", "en")
                }
            }
            params()
        }
        val response = client.post("$BASE_URL/youtubei/v1/$endpoint?prettyPrint=false") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Origin, BASE_URL)
            header(HttpHeaders.Referrer, "$BASE_URL/")
            setBody(body.toString())
        }
        if (!response.status.isSuccess()) error("YouTube Music $endpoint failed: ${response.status}")
        return Json.parseToJsonElement(response.bodyAsText())
    }

    private fun parseSongs(root: JsonElement): List<Song> {
        val sections = root.path("contents", "tabbedSearchResultsRenderer", "tabs")?.jsonArray
            ?.firstOrNull()?.path("tabRenderer", "content", "sectionListRenderer", "contents")?.jsonArray
            ?: return emptyList()
        return sections
            .flatMap { it.path("musicShelfRenderer", "contents")?.jsonArray ?: emptyList() }
            .mapNotNull { it.path("musicResponsiveListItemRenderer")?.let(::parseSong) }
            .distinctBy { it.id }
    }

    private fun parseSong(item: JsonElement): Song? {
        val columns = item.path("flexColumns")?.jsonArray ?: return null
        fun runs(column: Int) = columns.getOrNull(column)
            ?.path("musicResponsiveListItemFlexColumnRenderer", "text", "runs")?.jsonArray ?: emptyList()

        val titleRun = runs(0).firstOrNull() ?: return null
        val title = titleRun.string("text") ?: return null
        val videoId = titleRun.path("navigationEndpoint", "watchEndpoint")?.string("videoId")
            ?: item.path("playlistItemData")?.string("videoId")
            ?: return null

        // Second column: "<artist runs> • <album> • <duration>"
        val details = runs(1)
        val groups = mutableListOf(mutableListOf<JsonElement>())
        for (run in details) {
            if (run.string("text")?.trim() == "•") groups += mutableListOf<JsonElement>() else groups.last() += run
        }
        val artistRuns = groups.firstOrNull().orEmpty()
        val artist = artistRuns.joinToString("") { it.string("text").orEmpty() }.trim()
        val albumRun = details.firstOrNull { it.pageType() == "MUSIC_PAGE_TYPE_ALBUM" }
        val duration = details.mapNotNull { it.string("text")?.trim() }
            .lastOrNull { DURATION_REGEX.matches(it) }
            ?.split(":")?.fold(0) { acc, part -> acc * 60 + part.toInt() } ?: 0

        val thumbnail = item.path("thumbnail", "musicThumbnailRenderer", "thumbnail", "thumbnails")?.jsonArray
            ?.lastOrNull()?.string("url")

        return Song(
            id = YOUTUBE_SONG_ID_PREFIX + videoId,
            title = title,
            artist = artist,
            artists = parseArtists(artistRuns),
            album = albumRun?.string("text").orEmpty(),
            albumId = albumRun?.browseId().orEmpty(),
            coverArtId = thumbnail,
            duration = duration,
        )
    }

    private fun parseAlbum(root: JsonElement): YouTubeAlbum? {
        val tabs = root.path("contents", "twoColumnBrowseResultsRenderer") ?: return null
        val header = tabs.path("tabs")?.jsonArray?.firstOrNull()
            ?.path("tabRenderer", "content", "sectionListRenderer", "contents")?.jsonArray?.firstOrNull()
            ?.path("musicResponsiveHeaderRenderer") ?: return null
        fun runs(key: String) = header.path(key, "runs")?.jsonArray ?: emptyList()

        val title = runs("title").joinToString("") { it.string("text").orEmpty() }.trim().ifBlank { return null }
        val artistRuns = runs("straplineTextOne")
        // Subtitle: "Album • 2013"
        val subtitle = runs("subtitle").mapNotNull { it.string("text")?.trim() }.filter { it.isNotEmpty() && it != "•" }

        val tracks = tabs.path("secondaryContents", "sectionListRenderer", "contents")?.jsonArray.orEmpty()
            .flatMap { it.path("musicShelfRenderer", "contents")?.jsonArray ?: emptyList() }
            .mapNotNull { item ->
                val renderer = item.path("musicResponsiveListItemRenderer") ?: return@mapNotNull null
                val trackTitle = renderer.path("flexColumns")?.jsonArray?.firstOrNull()
                    ?.path("musicResponsiveListItemFlexColumnRenderer", "text", "runs")?.jsonArray
                    ?.joinToString("") { it.string("text").orEmpty() }?.trim()
                    ?: return@mapNotNull null
                YouTubeAlbumTrack(
                    videoId = renderer.path("playlistItemData")?.string("videoId"),
                    title = trackTitle,
                    trackNumber = renderer.path("index", "runs")?.jsonArray?.firstOrNull()?.string("text")?.toIntOrNull(),
                )
            }
        // "13 songs • 1 hour, 14 minutes"; long track lists are paginated, so prefer this count over tracks.size
        val songCount = runs("secondSubtitle").firstNotNullOfOrNull { SONG_COUNT_REGEX.find(it.string("text").orEmpty()) }
            ?.groupValues?.get(1)?.toIntOrNull()

        return YouTubeAlbum(
            title = title,
            artist = artistRuns.joinToString("") { it.string("text").orEmpty() }.trim(),
            artists = parseArtists(artistRuns),
            releaseType = subtitle.firstOrNull { !YEAR_REGEX.matches(it) },
            year = subtitle.firstOrNull { YEAR_REGEX.matches(it) }?.toInt(),
            trackCount = songCount ?: tracks.size.takeIf { it > 0 },
            tracks = tracks,
        )
    }

    /** Splits artist runs like "A", ", ", "B", " & ", "C" into the individual artists. */
    private fun parseArtists(runs: List<JsonElement>): List<Artist> = runs.mapNotNull { run ->
        val name = run.string("text")?.trim().orEmpty()
        when {
            run.pageType() == "MUSIC_PAGE_TYPE_ARTIST" -> Artist(id = run.browseId().orEmpty(), name = name)
            name.isEmpty() || name.lowercase() in JOIN_PHRASES -> null
            else -> Artist(id = "", name = name)
        }
    }.distinctBy { it.name }

    private fun JsonElement.path(vararg keys: String): JsonElement? =
        keys.fold(this as JsonElement?) { element, key -> (element as? JsonObject)?.get(key) }

    private fun JsonElement.string(key: String): String? =
        ((this as? JsonObject)?.get(key) as? JsonPrimitive)?.contentOrNull

    private fun JsonElement.browseId(): String? = path("navigationEndpoint", "browseEndpoint")?.string("browseId")

    private fun JsonElement.pageType(): String? = path(
        "navigationEndpoint", "browseEndpoint", "browseEndpointContextSupportedConfigs",
        "browseEndpointContextMusicConfig",
    )?.string("pageType")

    companion object {
        private const val BASE_URL = "https://music.youtube.com"
        private const val CLIENT_VERSION = "1.20260101.01.00"
        // Restricts search results to the "Songs" shelf
        private const val SONGS_FILTER_PARAMS = "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D"
        private val DURATION_REGEX = Regex("""\d+(:\d{2})+""")
        private val YEAR_REGEX = Regex("""\d{4}""")
        private val SONG_COUNT_REGEX = Regex("""(\d+) songs?""")
        private val JOIN_PHRASES = setOf(",", "&", "and", "x", "feat.", "feat", "ft.", "ft", "featuring", "with", "vs.", "vs")

        /** YouTube thumbnail URLs encode their size (e.g. `=w60-h60-l90-rj`); request a specific one instead. */
        fun resizeThumbnail(url: String, size: Int): String =
            url.replace(Regex("""=w\d+-h\d+"""), "=w$size-h$size")
    }
}

data class YouTubeAlbum(
    val title: String,
    /** Artist credit as displayed, e.g. "Daft Punk, Pharrell Williams & Nile Rodgers". */
    val artist: String,
    val artists: List<Artist>,
    /** As displayed by YouTube Music, e.g. "Album", "Single" or "EP". */
    val releaseType: String?,
    val year: Int?,
    val trackCount: Int?,
    val tracks: List<YouTubeAlbumTrack>,
)

data class YouTubeAlbumTrack(val videoId: String?, val title: String, val trackNumber: Int?)
