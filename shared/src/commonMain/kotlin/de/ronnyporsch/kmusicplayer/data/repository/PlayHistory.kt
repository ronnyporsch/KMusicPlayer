package de.ronnyporsch.kmusicplayer.data.repository

import de.ronnyporsch.kmusicplayer.domain.model.Song
import de.ronnyporsch.kmusicplayer.platform.readAppFile
import de.ronnyporsch.kmusicplayer.platform.writeAppFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class PlayHistoryFile(
    val server: String,
    /** Most recent first, each song once. */
    val songs: List<Song> = emptyList(),
)

/** The songs played most recently on the server identified by [serverKey], stored on the device. */
class PlayHistory(private val serverKey: () -> String) {
    private val mutex = Mutex()
    private var file: PlayHistoryFile? = null

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    /** Most recent first. */
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    /** Loads the history of the current server, e.g. after logging in to another one. */
    suspend fun reload() = mutex.withLock { _songs.value = load().songs }

    /** Puts [song] at the top, removing an earlier play of it. */
    suspend fun add(song: Song) = mutex.withLock {
        val old = load()
        val new = old.copy(songs = (listOf(song) + old.songs.filter { it.id != song.id }).take(MAX_SONGS))
        file = new
        _songs.value = new.songs
        writeAppFile(FILE_NAME, json.encodeToString(new))
    }

    private suspend fun load(): PlayHistoryFile {
        val server = serverKey()
        file?.takeIf { it.server == server }?.let { return it }
        val stored = readAppFile(FILE_NAME)?.let { runCatching { json.decodeFromString<PlayHistoryFile>(it) }.getOrNull() }
        // Only one server is stored, playing songs of another one replaces it
        return (stored?.takeIf { it.server == server } ?: PlayHistoryFile(server)).also { file = it }
    }

    private companion object {
        const val FILE_NAME = "play-history.json"
        const val MAX_SONGS = 100
        val json = Json { ignoreUnknownKeys = true }
    }
}
