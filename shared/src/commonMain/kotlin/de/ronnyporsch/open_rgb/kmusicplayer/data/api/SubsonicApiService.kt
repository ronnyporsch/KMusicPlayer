package de.ronnyporsch.open_rgb.kmusicplayer.data.api

import de.ronnyporsch.open_rgb.kmusicplayer.data.dto.SubsonicError
import de.ronnyporsch.open_rgb.kmusicplayer.data.dto.SubsonicResponse
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.ServerConfig
import de.ronnyporsch.open_rgb.kmusicplayer.platform.createHttpClient
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

class SubsonicApiService {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val client = createHttpClient {
        install(ContentNegotiation) {
            json(json)
        }
    }

    private var config: ServerConfig? = null

    fun configure(serverConfig: ServerConfig) {
        config = serverConfig
    }

    val isConfigured: Boolean get() = config != null

    private fun baseUrl(): String {
        val cfg = config ?: throw IllegalStateException("Server not configured")
        return cfg.serverUrl.trimEnd('/')
    }

    private fun HttpRequestBuilder.addAuthParams() {
        val cfg = config ?: throw IllegalStateException("Server not configured")
        parameter("u", cfg.username)
        parameter("t", cfg.token)
        parameter("s", cfg.salt)
        parameter("v", "1.16.1")
        parameter("c", "KMusicPlayer")
        parameter("f", "json")
    }

    suspend fun ping(): Boolean {
        return try {
            val response: SubsonicResponse = client.get("${baseUrl()}/rest/ping.view") {
                addAuthParams()
            }.body()
            response.subsonicResponse.status == "ok"
        } catch (e: Exception) {
            false
        }
    }

    /** Returns the server's error if it rejects the configured credentials. Throws if it can't be reached. */
    suspend fun checkCredentials(): SubsonicError? {
        val response: SubsonicResponse = client.get("${baseUrl()}/rest/ping.view") {
            addAuthParams()
        }.body()
        val body = response.subsonicResponse
        return if (body.status == "ok") null else body.error ?: SubsonicError(message = "Connection failed")
    }

    suspend fun getArtists(): SubsonicResponse {
        return client.get("${baseUrl()}/rest/getArtists.view") {
            addAuthParams()
        }.body()
    }

    suspend fun getArtist(id: String): SubsonicResponse {
        return client.get("${baseUrl()}/rest/getArtist.view") {
            addAuthParams()
            parameter("id", id)
        }.body()
    }

    suspend fun getArtistInfo2(id: String): SubsonicResponse {
        return client.get("${baseUrl()}/rest/getArtistInfo2.view") {
            addAuthParams()
            parameter("id", id)
        }.body()
    }

    suspend fun getAlbum(id: String): SubsonicResponse {
        return client.get("${baseUrl()}/rest/getAlbum.view") {
            addAuthParams()
            parameter("id", id)
        }.body()
    }

    suspend fun getPlaylists(): SubsonicResponse {
        return client.get("${baseUrl()}/rest/getPlaylists.view") {
            addAuthParams()
        }.body()
    }

    suspend fun getPlaylist(id: String): SubsonicResponse {
        return client.get("${baseUrl()}/rest/getPlaylist.view") {
            addAuthParams()
            parameter("id", id)
        }.body()
    }

    suspend fun createPlaylist(name: String, songIds: List<String> = emptyList()): SubsonicResponse {
        return client.get("${baseUrl()}/rest/createPlaylist.view") {
            addAuthParams()
            parameter("name", name)
            songIds.forEach { parameter("songId", it) }
        }.body()
    }

    suspend fun updatePlaylist(
        playlistId: String,
        songIdsToAdd: List<String> = emptyList(),
        songIndicesToRemove: List<Int> = emptyList()
    ): SubsonicResponse {
        return client.get("${baseUrl()}/rest/updatePlaylist.view") {
            addAuthParams()
            parameter("playlistId", playlistId)
            songIdsToAdd.forEach { parameter("songIdToAdd", it) }
            songIndicesToRemove.forEach { parameter("songIndexToRemove", it) }
        }.body()
    }

    suspend fun search(query: String): SubsonicResponse {
        return client.get("${baseUrl()}/rest/search3.view") {
            addAuthParams()
            parameter("query", query)
            parameter("artistCount", 10)
            parameter("albumCount", 10)
            parameter("songCount", 20)
        }.body()
    }

    suspend fun searchSongs(query: String, songCount: Int = 500): SubsonicResponse {
        return client.get("${baseUrl()}/rest/search3.view") {
            addAuthParams()
            parameter("query", query)
            parameter("artistCount", 0)
            parameter("albumCount", 0)
            parameter("songCount", songCount)
        }.body()
    }

    /** OpenSubsonic extension "songLyrics"; servers without it answer with an error. */
    suspend fun getLyricsBySongId(id: String): SubsonicResponse {
        return client.get("${baseUrl()}/rest/getLyricsBySongId.view") {
            addAuthParams()
            parameter("id", id)
        }.body()
    }

    suspend fun getLyrics(artist: String, title: String): SubsonicResponse {
        return client.get("${baseUrl()}/rest/getLyrics.view") {
            addAuthParams()
            parameter("artist", artist)
            parameter("title", title)
        }.body()
    }

    suspend fun getStarred2(): SubsonicResponse {
        return client.get("${baseUrl()}/rest/getStarred2.view") {
            addAuthParams()
        }.body()
    }

    /** Stars ([star]) or unstars an item; [idParameter] is "id" for songs, "albumId" or "artistId". */
    suspend fun setStarred(idParameter: String, id: String, star: Boolean): SubsonicResponse {
        return client.get("${baseUrl()}/rest/${if (star) "star" else "unstar"}.view") {
            addAuthParams()
            parameter(idParameter, id)
        }.body()
    }

    /** Asks the server to rescan its music folders. Requires an admin user on Navidrome. */
    suspend fun startScan(): Boolean {
        val response: SubsonicResponse = client.get("${baseUrl()}/rest/startScan.view") {
            addAuthParams()
        }.body()
        return response.subsonicResponse.status == "ok"
    }

    fun getStreamUrl(songId: String): String {
        val cfg = config ?: throw IllegalStateException("Server not configured")
        return "${baseUrl()}/rest/stream.view?id=$songId&u=${cfg.username}&t=${cfg.token}&s=${cfg.salt}&v=1.16.1&c=KMusicPlayer"
    }

    /** The original file, without transcoding. */
    fun getDownloadUrl(songId: String): String = getStreamUrl(songId) + "&format=raw"

    fun getCoverArtUrl(coverArtId: String, size: Int = 300): String {
        val cfg = config ?: throw IllegalStateException("Server not configured")
        return "${baseUrl()}/rest/getCoverArt.view?id=$coverArtId&size=$size&u=${cfg.username}&t=${cfg.token}&s=${cfg.salt}&v=1.16.1&c=KMusicPlayer"
    }
}
