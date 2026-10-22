package de.ronnyporsch.open_rgb.kmusicplayer.data.offline

import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.Playlist
import de.ronnyporsch.open_rgb.kmusicplayer.platform.readAppFile
import de.ronnyporsch.open_rgb.kmusicplayer.platform.writeAppFile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal val offlineJson = Json { ignoreUnknownKeys = true }

@Serializable
private data class PlaylistCacheFile(
    val server: String,
    /** Without songs. */
    val playlists: List<Playlist> = emptyList(),
    /** Playlists with their songs, by id. Only those that were opened. */
    val details: Map<String, Playlist> = emptyMap(),
)

/** The playlists last loaded from the server identified by [serverKey], so they can be shown while it isn't reachable. */
class PlaylistCache(private val serverKey: () -> String) {
    private val mutex = Mutex()
    private var cache: PlaylistCacheFile? = null

    suspend fun playlists(): List<Playlist> = mutex.withLock { load().playlists }

    suspend fun playlist(id: String): Playlist? = mutex.withLock { load().details[id] }

    suspend fun putPlaylists(playlists: List<Playlist>) = update {
        val ids = playlists.mapTo(mutableSetOf()) { it.id }
        copy(playlists = playlists.map { it.copy(songs = emptyList()) }, details = details.filterKeys { it in ids })
    }

    suspend fun putPlaylist(playlist: Playlist) = update {
        copy(
            playlists = playlists.map { if (it.id == playlist.id) playlist.copy(songs = emptyList()) else it },
            details = details + (playlist.id to playlist),
        )
    }

    private suspend fun load(): PlaylistCacheFile {
        val server = serverKey()
        cache?.takeIf { it.server == server }?.let { return it }
        val stored = readAppFile(FILE_NAME)?.let {
            runCatching { offlineJson.decodeFromString<PlaylistCacheFile>(it) }.getOrNull()
        }
        // Only one server is cached, logging in to another one replaces it
        return (stored?.takeIf { it.server == server } ?: PlaylistCacheFile(server)).also { cache = it }
    }

    private suspend fun update(transform: PlaylistCacheFile.() -> PlaylistCacheFile) = mutex.withLock {
        val old = load()
        val new = old.transform()
        if (new == old) return@withLock
        cache = new
        writeAppFile(FILE_NAME, offlineJson.encodeToString(new))
    }

    private companion object {
        const val FILE_NAME = "playlist-cache.json"
    }
}
