package de.ronnyporsch.open_rgb.kmusicplayer.data.repository

import de.ronnyporsch.open_rgb.kmusicplayer.data.api.SubsonicApiService
import de.ronnyporsch.open_rgb.kmusicplayer.data.jellyfin.JellyfinApiException
import de.ronnyporsch.open_rgb.kmusicplayer.data.jellyfin.JellyfinApiService
import de.ronnyporsch.open_rgb.kmusicplayer.data.jellyfin.JellyfinMusicRepository
import de.ronnyporsch.open_rgb.kmusicplayer.data.jellyfin.PluginInfoDto
import de.ronnyporsch.open_rgb.kmusicplayer.data.offline.OfflineMusicRepository
import de.ronnyporsch.open_rgb.kmusicplayer.data.offline.PlaylistCache
import de.ronnyporsch.open_rgb.kmusicplayer.data.offline.PlaylistDownloads
import de.ronnyporsch.open_rgb.kmusicplayer.data.youtube.resizeYouTubeThumbnail
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.*
import de.ronnyporsch.open_rgb.kmusicplayer.getPlatform
import de.ronnyporsch.open_rgb.kmusicplayer.platform.createSongFileStore
import de.ronnyporsch.open_rgb.kmusicplayer.platform.untrustedCertificateHost
import de.ronnyporsch.open_rgb.kmusicplayer.util.generateSalt
import de.ronnyporsch.open_rgb.kmusicplayer.util.md5
import io.ktor.http.HttpStatusCode
import io.ktor.http.parseUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The server rejected the username or password, as opposed to e.g. not being reachable. */
class InvalidCredentialsException(message: String) : Exception(message.ifBlank { "Wrong username or password" })

/**
 * The music server the app is connected to (Navidrome/Subsonic or Jellyfin). [repository] always talks to the
 * current one, so view models can keep it across reconnects.
 */
class MusicServerConnection(private val settingsHolder: SettingsHolder) {
    private val subsonicApi = SubsonicApiService()
    val jellyfinApi = JellyfinApiService()
    private val subsonic = MusicRepositoryImpl(subsonicApi)
    private val jellyfin = JellyfinMusicRepository(jellyfinApi)
    private var active: MusicRepository = subsonic

    private val _serverType = MutableStateFlow<ServerType?>(null)
    /** The type of the connected server, or null while not connected. */
    val serverType: StateFlow<ServerType?> = _serverType.asStateFlow()

    private val _plugin = MutableStateFlow<PluginInfoDto?>(null)
    /** The KMusicPlayer plugin of the connected Jellyfin server, or null if there is none. */
    val plugin: StateFlow<PluginInfoDto?> = _plugin.asStateFlow()

    private val _connections = MutableStateFlow(0)
    /** Counts successful [connect]s. */
    val connections: StateFlow<Int> = _connections.asStateFlow()

    private val online: MusicRepository = ActiveRepository()
    private val songFiles = createSongFileStore()

    /** Playlists downloaded for offline playback. */
    val downloads = PlaylistDownloads(online, songFiles, ::serverKey)

    val repository = OfflineMusicRepository(online, PlaylistCache(::serverKey), downloads, songFiles)

    /** Identifies the saved server for the offline data. */
    private fun serverKey(): String = settingsHolder.settings.value.let { "${it.serverType}|${it.serverUrl}|${it.username}" }

    /**
     * Connects to the server (and remembers the connection if [save]). Throws with a user-facing message on failure,
     * an [InvalidCredentialsException] if the server rejected the login.
     */
    suspend fun connect(
        type: ServerType,
        serverUrl: String,
        username: String,
        password: String,
        allowUntrustedCertificate: Boolean,
        save: Boolean = true,
    ) {
        val url = serverUrl.trim().trimEnd('/')
        untrustedCertificateHost = if (allowUntrustedCertificate) parseUrl(url)?.host else null
        var deviceId = settingsHolder.settings.value.jellyfinDeviceId
        when (type) {
            ServerType.Subsonic -> {
                val salt = generateSalt()
                subsonicApi.configure(ServerConfig(url, username, md5(password + salt), salt))
                val error = subsonicApi.checkCredentials()
                // 40: wrong username or password, 41: token authentication not supported for this user
                if (error != null && error.code in 40..41) throw InvalidCredentialsException(error.message)
                if (error != null) throw IllegalStateException(error.message.ifBlank { "Connection failed" })
                active = subsonic
                _plugin.value = null
            }
            ServerType.Jellyfin -> {
                if (deviceId.isBlank()) deviceId = "kmusicplayer-" + generateSalt(24)
                try {
                    jellyfinApi.authenticate(url, username, password, deviceId)
                } catch (e: JellyfinApiException) {
                    if (e.status == HttpStatusCode.Unauthorized) throw InvalidCredentialsException(e.message ?: "")
                    throw e
                }
                active = jellyfin
                refreshPlugin()
            }
        }
        _serverType.value = type
        if (save) {
            settingsHolder.update {
                copy(
                    serverType = type,
                    serverUrl = url,
                    username = username,
                    password = password,
                    allowUntrustedCertificate = allowUntrustedCertificate,
                    jellyfinDeviceId = deviceId,
                )
            }
        } else if (deviceId != settingsHolder.settings.value.jellyfinDeviceId) {
            settingsHolder.update { copy(jellyfinDeviceId = deviceId) }
        }
        _connections.value++
        downloads.syncAll()
    }

    /** Re-reads what the KMusicPlayer Jellyfin plugin offers, e.g. after an admin changed its settings. */
    suspend fun refreshPlugin() {
        _plugin.value = if (active === jellyfin) {
            try {
                jellyfinApi.getPluginInfo()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
    }

    private inner class ActiveRepository : MusicRepository {
        override val isConfigured: Boolean get() = active.isConfigured
        override suspend fun ping() = active.ping()
        override suspend fun getArtists() = active.getArtists()
        override suspend fun getArtistAlbums(artistId: String) = active.getArtistAlbums(artistId)
        override suspend fun getAppearsOnAlbums(artistName: String, ownAlbumIds: List<String>) =
            active.getAppearsOnAlbums(artistName, ownAlbumIds)
        override suspend fun getArtistInfo(artistId: String) = active.getArtistInfo(artistId)
        override suspend fun getAlbum(albumId: String) = active.getAlbum(albumId)
        override suspend fun getPlaylists() = active.getPlaylists()
        override suspend fun getPlaylist(playlistId: String) = active.getPlaylist(playlistId)
        override suspend fun createPlaylist(name: String, songIds: List<String>) = active.createPlaylist(name, songIds)
        override suspend fun addSongsToPlaylist(playlistId: String, songIds: List<String>) =
            active.addSongsToPlaylist(playlistId, songIds)
        override suspend fun removeSongFromPlaylist(playlistId: String, songIndex: Int) =
            active.removeSongFromPlaylist(playlistId, songIndex)
        override suspend fun findArtistByName(name: String) = active.findArtistByName(name)
        override suspend fun findSongsByArtistName(name: String) = active.findSongsByArtistName(name)
        override suspend fun getSongsForArtist(artistName: String, albumIds: List<String>) =
            active.getSongsForArtist(artistName, albumIds)
        override suspend fun search(query: String) = active.search(query)
        override suspend fun startScan() = active.startScan()

        // YouTube Music tracks aren't library items, the server has no lyrics for them
        override suspend fun getLyrics(song: Song) =
            if (song.isYouTube) Result.success(null) else active.getLyrics(song)

        override suspend fun getAudioFormat(song: Song): Result<AudioStreamInfo?> {
            val videoId = song.youTubeVideoId ?: return active.getAudioFormat(song)
            // The downloaded file if there is one (cheap), else the stream yt-dlp picks (served the same way)
            return runCatching {
                jellyfinApi.getDownloadedYouTube(listOf(videoId)).firstOrNull()
                    ?.takeIf { it.codec != null || it.bitRate != null }
                    ?.let { AudioStreamInfo(it.codec, it.bitRate) }
                    ?: jellyfinApi.getYouTubeStreamInfo(videoId)?.let { AudioStreamInfo(it.codec, it.bitRate) }
            }
        }

        override fun getStreamUrl(songId: String): String {
            val videoId = songId.takeIf { it.startsWith(YOUTUBE_SONG_ID_PREFIX) }?.removePrefix(YOUTUBE_SONG_ID_PREFIX)
                ?: return active.getStreamUrl(songId)
            // YouTube Music tracks are only streamed by the KMusicPlayer Jellyfin plugin; the original stream is Opus or AAC
            return jellyfinApi.getYouTubeAudioUrl(videoId, mp3 = "opus" !in getPlatform().audioContainers)
        }

        override fun getDownloadUrl(songId: String): String {
            val videoId = songId.takeIf { it.startsWith(YOUTUBE_SONG_ID_PREFIX) }?.removePrefix(YOUTUBE_SONG_ID_PREFIX)
                ?: return active.getDownloadUrl(songId)
            return jellyfinApi.getYouTubeAudioUrl(videoId, mp3 = false)
        }

        // YouTube Music songs carry their thumbnail URL as cover art id
        override fun getCoverArtUrl(coverArtId: String, size: Int): String =
            if (coverArtId.startsWith("https://")) {
                resizeYouTubeThumbnail(coverArtId, size)
            } else {
                active.getCoverArtUrl(coverArtId, size)
            }
    }
}
