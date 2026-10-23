package de.ronnyporsch.open_rgb.kmusicplayer.data.jellyfin

import de.ronnyporsch.open_rgb.kmusicplayer.getPlatform
import de.ronnyporsch.open_rgb.kmusicplayer.platform.createHttpClient
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

data class JellyfinSession(
    val serverUrl: String,
    val userId: String,
    val accessToken: String,
    val deviceId: String,
)

class JellyfinApiException(val status: HttpStatusCode, message: String) : Exception(message)

/** Client for the Jellyfin 12 REST API (and the endpoints of the KMusicPlayer Jellyfin plugin). */
class JellyfinApiService {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    private val client = createHttpClient {
        install(ContentNegotiation) {
            json(json)
        }
        expectSuccess = false
    }

    var session: JellyfinSession? = null
        private set

    val isConfigured: Boolean get() = session != null

    private fun requireSession() = session ?: throw IllegalStateException("Server not configured")

    private fun baseUrl() = requireSession().serverUrl

    val userId: String get() = requireSession().userId

    private fun authorizationHeader(deviceId: String, token: String?): String {
        // Quotes and commas would break the header format
        val device = getPlatform().name.replace(Regex("""[",]"""), "")
        return buildString {
            append("""MediaBrowser Client="KMusicPlayer", Device="$device", DeviceId="$deviceId", Version="$CLIENT_VERSION"""")
            if (token != null) append(""", Token="$token"""")
        }
    }

    private fun HttpRequestBuilder.authorize() {
        val s = requireSession()
        header(HttpHeaders.Authorization, authorizationHeader(s.deviceId, s.accessToken))
    }

    suspend fun authenticate(serverUrl: String, username: String, password: String, deviceId: String): JellyfinSession {
        val url = serverUrl.trimEnd('/')
        val response = client.post("$url/Users/AuthenticateByName") {
            header(HttpHeaders.Authorization, authorizationHeader(deviceId, null))
            contentType(ContentType.Application.Json)
            setBody(AuthenticateByNameRequest(username, password))
        }
        if (response.status == HttpStatusCode.Unauthorized) throw JellyfinApiException(response.status, "Wrong username or password")
        response.ensureSuccess()
        val result: AuthenticationResultDto = response.body()
        return JellyfinSession(url, result.user.id, result.accessToken, deviceId).also { session = it }
    }

    suspend fun ping(): Boolean = try {
        client.get("${baseUrl()}/System/Info") { authorize() }.status.isSuccess()
    } catch (e: Exception) {
        false
    }

    suspend fun getItems(params: Map<String, Any?>): ItemsResultDto = get("Items", params + ("userId" to userId))

    suspend fun getItem(itemId: String): BaseItemDto = get("Items/$itemId", mapOf("userId" to userId))

    suspend fun getAlbumArtists(params: Map<String, Any?> = emptyMap()): ItemsResultDto =
        get("Artists/AlbumArtists", params + ("userId" to userId))

    suspend fun getArtists(params: Map<String, Any?>): ItemsResultDto = get("Artists", params + ("userId" to userId))

    suspend fun getPlaylistItems(playlistId: String): ItemsResultDto =
        get("Playlists/$playlistId/Items", mapOf("userId" to userId))

    suspend fun createPlaylist(name: String, songIds: List<String>): PlaylistCreationResultDto {
        val response = client.post("${baseUrl()}/Playlists") {
            authorize()
            contentType(ContentType.Application.Json)
            setBody(CreatePlaylistDto(name = name, ids = songIds, userId = userId))
        }
        response.ensureSuccess()
        return response.body()
    }

    suspend fun addToPlaylist(playlistId: String, songIds: List<String>) {
        client.post("${baseUrl()}/Playlists/$playlistId/Items") {
            authorize()
            parameter("ids", songIds.joinToString(","))
            parameter("userId", userId)
        }.ensureSuccess()
    }

    suspend fun removeFromPlaylist(playlistId: String, entryIds: List<String>) {
        client.delete("${baseUrl()}/Playlists/$playlistId/Items") {
            authorize()
            parameter("entryIds", entryIds.joinToString(","))
        }.ensureSuccess()
    }

    /** Marks an item (song, album, artist) as favorite of the user or unmarks it. */
    suspend fun setFavorite(itemId: String, favorite: Boolean) {
        val url = "${baseUrl()}/UserFavoriteItems/$itemId"
        val response = if (favorite) {
            client.post(url) { authorize(); parameter("userId", userId) }
        } else {
            client.delete(url) { authorize(); parameter("userId", userId) }
        }
        response.ensureSuccess()
    }

    /** Scans all libraries. Requires an administrator. */
    suspend fun refreshLibrary(): Boolean =
        client.post("${baseUrl()}/Library/Refresh") { authorize() }.status.isSuccess()

    /** Lyrics stored on the server (embedded, sidecar file or downloaded before); null if there are none. */
    suspend fun getLyrics(itemId: String): LyricDto? {
        val response = client.get("${baseUrl()}/Audio/$itemId/Lyrics") { authorize() }
        if (response.status == HttpStatusCode.NotFound) return null
        response.ensureSuccess()
        return response.body()
    }

    /**
     * Asks the server's lyrics providers (e.g. the LrcLib plugin) for lyrics of the item.
     * Requires the lyric management permission.
     */
    suspend fun searchRemoteLyrics(itemId: String): List<RemoteLyricInfoDto> = get("Audio/$itemId/RemoteSearch/Lyrics", emptyMap())

    /** Saves the [searchRemoteLyrics] result [lyricId] for the item, so [getLyrics] returns it from now on. */
    suspend fun downloadRemoteLyrics(itemId: String, lyricId: String): LyricDto {
        val response = client.post("${baseUrl()}/Audio/$itemId/RemoteSearch/Lyrics/${lyricId.encodeURLPathPart()}") { authorize() }
        response.ensureSuccess()
        return response.body()
    }

    /**
     * Stream URL that plays the original file if the platform can decode its container ([containers])
     * and an MP3 transcode otherwise. Carries the token as query parameter since players can't set headers.
     */
    fun getStreamUrl(itemId: String, containers: List<String>): String {
        val s = requireSession()
        return URLBuilder("${s.serverUrl}/Audio/$itemId/universal").apply {
            parameters.append("UserId", s.userId)
            parameters.append("DeviceId", s.deviceId)
            parameters.append("Container", containers.joinToString(","))
            parameters.append("TranscodingContainer", "mp3")
            parameters.append("TranscodingProtocol", "http")
            parameters.append("AudioCodec", "mp3")
            parameters.append("MaxStreamingBitrate", "999999999")
            parameters.append("ApiKey", s.accessToken)
        }.buildString()
    }

    /** The original file, without transcoding. Unlike /Items/{id}/Download, this needs no download permission. */
    fun getOriginalAudioUrl(itemId: String): String {
        val s = requireSession()
        return URLBuilder("${s.serverUrl}/Audio/$itemId/stream").apply {
            parameters.append("static", "true")
            parameters.append("ApiKey", s.accessToken)
        }.buildString()
    }

    fun getImageUrl(itemId: String, size: Int?, imageType: String = "Primary"): String {
        val s = requireSession()
        return URLBuilder("${s.serverUrl}/Items/$itemId/Images/$imageType").apply {
            if (size != null) {
                parameters.append("fillWidth", size.toString())
                parameters.append("fillHeight", size.toString())
            }
            parameters.append("quality", "90")
        }.buildString()
    }

    // KMusicPlayer plugin

    /** Returns null if the KMusicPlayer plugin isn't installed on the server. */
    suspend fun getPluginInfo(): PluginInfoDto? {
        val response = client.get("${baseUrl()}/KMusicPlayer/Info") { authorize() }
        if (!response.status.isSuccess()) return null
        // Jellyfin answers unknown routes of its web client with HTML
        if (response.contentType()?.match(ContentType.Application.Json) != true) return null
        return response.body()
    }

    /** [includeVideos] needs plugin 1.8.0.0, see [PluginInfoDto.videoAvailable]. */
    suspend fun searchYouTube(query: String, includeVideos: Boolean): List<YouTubeSongDto> =
        get("KMusicPlayer/YouTube/Search", mapOf("query" to query, "includeVideos" to includeVideos.takeIf { it }))

    suspend fun searchYouTubeArtists(query: String): List<YouTubeArtistSummaryDto> =
        get("KMusicPlayer/YouTube/Artists/Search", mapOf("query" to query))

    suspend fun getYouTubeArtist(channelId: String): YouTubeArtistPageDto = get("KMusicPlayer/YouTube/Artists/$channelId", emptyMap())

    suspend fun getYouTubeAlbum(albumId: String): YouTubeAlbumDto = get("KMusicPlayer/YouTube/Albums/$albumId", emptyMap())

    /** Queues a download of [song], a result of [searchYouTube]. */
    suspend fun requestDownload(song: YouTubeSongDto): DownloadJobDto {
        val response = client.post("${baseUrl()}/KMusicPlayer/Downloads") {
            authorize()
            contentType(ContentType.Application.Json)
            setBody(song)
        }
        response.ensureSuccess()
        return response.body()
    }

    suspend fun getDownload(jobId: String): DownloadJobDto = get("KMusicPlayer/Downloads/$jobId", emptyMap())

    /**
     * Those of [videoIds] whose tracks are already in the library. Empty with plugins that can't tell; without
     * codec and bitrate with plugins before 1.7.3.1.
     */
    suspend fun getDownloadedYouTube(videoIds: List<String>): List<DownloadedTrackDto> {
        val response = client.post("${baseUrl()}/KMusicPlayer/YouTube/Downloaded/Details") {
            authorize()
            contentType(ContentType.Application.Json)
            setBody(videoIds)
        }
        if (response.status != HttpStatusCode.NotFound) {
            response.ensureSuccess()
            return response.body()
        }
        val legacy = client.post("${baseUrl()}/KMusicPlayer/YouTube/Downloaded") {
            authorize()
            contentType(ContentType.Application.Json)
            setBody(videoIds)
        }
        if (legacy.status == HttpStatusCode.NotFound) return emptyList()
        legacy.ensureSuccess()
        return legacy.body<List<String>>().map { DownloadedTrackDto(it) }
    }

    /** Those of [albumIds] whose tracks are all in the library. Empty with plugins that can't tell. */
    suspend fun getDownloadedYouTubeAlbums(albumIds: List<String>): List<String> {
        val response = client.post("${baseUrl()}/KMusicPlayer/YouTube/Albums/Downloaded") {
            authorize()
            contentType(ContentType.Application.Json)
            setBody(albumIds)
        }
        if (response.status == HttpStatusCode.NotFound) return emptyList()
        response.ensureSuccess()
        return response.body()
    }

    /** The user's recent downloads. */
    suspend fun getDownloads(): List<DownloadJobDto> = get("KMusicPlayer/Downloads", emptyMap())

    suspend fun getYouTubeStreamInfo(videoId: String): AudioStreamInfoDto? {
        val response = client.get("${baseUrl()}/KMusicPlayer/YouTube/$videoId/StreamInfo") { authorize() }
        if (response.status == HttpStatusCode.NoContent) return null
        response.ensureSuccess()
        return response.body()
    }

    /** Audio of a YouTube Music track, served by the plugin. [mp3] converts it for players without Opus/AAC support. */
    fun getYouTubeAudioUrl(videoId: String, mp3: Boolean): String {
        val s = requireSession()
        return URLBuilder("${s.serverUrl}/KMusicPlayer/YouTube/$videoId/Audio").apply {
            if (mp3) parameters.append("format", "mp3")
            parameters.append("ApiKey", s.accessToken)
        }.buildString()
    }

    /** A YouTube video with picture and sound (MP4), in the highest resolution up to [maxHeight]. */
    fun getYouTubeVideoUrl(videoId: String, maxHeight: Int): String {
        val s = requireSession()
        return URLBuilder("${s.serverUrl}/KMusicPlayer/YouTube/$videoId/Video").apply {
            parameters.append("maxHeight", maxHeight.toString())
            parameters.append("ApiKey", s.accessToken)
        }.buildString()
    }

    /** Codecs and bitrates of the streams of [getYouTubeVideoUrl]; null if unknown or with plugins before 1.8.1.0. */
    suspend fun getYouTubeVideoInfo(videoId: String, maxHeight: Int): VideoStreamInfoDto? {
        val response = client.get("${baseUrl()}/KMusicPlayer/YouTube/$videoId/VideoInfo") {
            authorize()
            parameter("maxHeight", maxHeight)
        }
        if (response.status == HttpStatusCode.NoContent || response.status == HttpStatusCode.NotFound) return null
        response.ensureSuccess()
        return response.body()
    }

    /** Heights (e.g. 1080) the video is available in, highest first. */
    suspend fun getYouTubeVideoHeights(videoId: String): List<Int> = get("KMusicPlayer/YouTube/$videoId/VideoHeights", emptyMap())

    private suspend inline fun <reified T> get(path: String, params: Map<String, Any?>): T {
        val response = client.get("${baseUrl()}/$path") {
            authorize()
            params.forEach { (key, value) -> if (value != null) parameter(key, value) }
        }
        response.ensureSuccess()
        return response.body()
    }

    private suspend fun HttpResponse.ensureSuccess() {
        if (status.isSuccess()) return
        // ASP.NET problem details carry a "detail" message
        val detail = runCatching { Regex(""""detail"\s*:\s*"([^"]*)"""").find(bodyAsText())?.groupValues?.get(1) }.getOrNull()
        throw JellyfinApiException(status, detail ?: "Jellyfin request failed: $status")
    }

    companion object {
        const val CLIENT_VERSION = "1.0.0"
    }
}
