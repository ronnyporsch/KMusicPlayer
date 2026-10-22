package de.ronnyporsch.open_rgb.kmusicplayer.data.offline

import de.ronnyporsch.open_rgb.kmusicplayer.data.repository.MusicRepository
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.Playlist
import de.ronnyporsch.open_rgb.kmusicplayer.platform.SongFileStore
import de.ronnyporsch.open_rgb.kmusicplayer.platform.readAppFile
import de.ronnyporsch.open_rgb.kmusicplayer.platform.writeAppFile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

@Serializable
private data class DownloadedPlaylist(val server: String, val playlist: Playlist)

data class PlaylistDownloadStatus(
    val downloadedSongs: Int,
    val totalSongs: Int,
    /** Songs are being downloaded right now. */
    val isRunning: Boolean,
) {
    val isComplete: Boolean get() = !isRunning && downloadedSongs >= totalSongs
}

/**
 * Playlists the user downloaded for offline playback. Their songs are downloaded in original quality into [files] and
 * kept in sync with the server: songs added to a playlist get downloaded, removed ones deleted.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistDownloads(
    /** Must talk to the server, not fall back to offline data. */
    private val repository: MusicRepository,
    private val files: SongFileStore?,
    private val serverKey: () -> String,
) {
    val isSupported: Boolean get() = files != null

    // One thread, so the state below needs no locking; the downloads themselves run on the file store's dispatcher
    private val dispatcher = Dispatchers.Default.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var entries: List<DownloadedPlaylist>? = null
    private val jobs = mutableMapOf<String, Job>()

    private val _status = MutableStateFlow<Map<String, PlaylistDownloadStatus>>(emptyMap())
    /** The downloaded playlists of the current server, by playlist id. */
    val status: StateFlow<Map<String, PlaylistDownloadStatus>> = _status.asStateFlow()

    init {
        if (files != null) scope.launch { publish() }
    }

    /** Downloads [playlist] and keeps it downloaded. */
    fun download(playlist: Playlist) {
        if (files == null) return
        scope.launch {
            val server = serverKey()
            if (entry(server, playlist.id) == null) {
                save(load() + DownloadedPlaylist(server, playlist))
            }
            sync(server, playlist.id)
        }
    }

    fun remove(playlistId: String) {
        if (files == null) return
        scope.launch {
            val server = serverKey()
            jobs.remove(key(server, playlistId))?.cancelAndJoin()
            val entry = entry(server, playlistId) ?: return@launch
            save(load() - entry)
            deleteUnused(entry.playlist.songs.map { it.id })
            publish()
        }
    }

    /** Resumes unfinished downloads and syncs all downloaded playlists of the current server, e.g. after connecting. */
    fun syncAll() {
        if (files == null) return
        scope.launch {
            val server = serverKey()
            load().filter { it.server == server }.forEach { sync(server, it.playlist.id) }
        }
    }

    /** Call after [playlistId] got changed, to download its new songs. */
    fun onPlaylistChanged(playlistId: String) {
        if (files == null) return
        scope.launch {
            val server = serverKey()
            if (entry(server, playlistId) == null) return@launch
            // A running sync may have fetched the playlist before the change
            jobs.remove(key(server, playlistId))?.cancelAndJoin()
            sync(server, playlistId)
        }
    }

    /** The downloaded [playlistId] of the current server, with its songs. */
    suspend fun playlist(playlistId: String): Playlist? = withContext(dispatcher) {
        entry(serverKey(), playlistId)?.playlist?.takeIf { it.songs.isNotEmpty() }
    }

    suspend fun playlists(): List<Playlist> = withContext(dispatcher) {
        val server = serverKey()
        load().filter { it.server == server }.map { it.playlist }
    }

    private suspend fun sync(server: String, playlistId: String) {
        val files = files ?: return
        val key = key(server, playlistId)
        if (jobs[key]?.isActive == true) return
        jobs[key] = scope.launch {
            // Picks up changes made elsewhere; offline the stored song list is downloaded as far as possible
            repository.getPlaylist(playlistId).onSuccess { fresh ->
                val entry = entry(server, playlistId) ?: return@launch
                if (serverKey() != server) return@launch
                save(load().map { if (it == entry) entry.copy(playlist = fresh) else it })
                deleteUnused(entry.playlist.songs.map { it.id } - fresh.songs.mapTo(mutableSetOf()) { it.id })
            }
            publish()
            val songs = entry(server, playlistId)?.playlist?.songs.orEmpty().distinctBy { it.id }
            for (song in songs) {
                if (files.uriOf(song.id) != null) continue
                if (serverKey() != server) break
                try {
                    files.download(song.id, repository.getDownloadUrl(song.id))
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Stays missing; the next sync tries again
                }
                publish()
            }
        }.also { job ->
            job.invokeOnCompletion {
                scope.launch {
                    if (jobs[key] === job) jobs.remove(key)
                    publish()
                }
            }
        }
        publish()
    }

    /** Deletes the files of [songIds] that no downloaded playlist (of any server) contains anymore. */
    private suspend fun deleteUnused(songIds: Collection<String>) {
        val files = files ?: return
        val used = load().flatMapTo(mutableSetOf()) { entry -> entry.playlist.songs.map { it.id } }
        songIds.filterNot { it in used }.forEach { files.delete(it) }
    }

    private suspend fun publish() {
        val files = files ?: return
        val server = serverKey()
        _status.value = load()
            .filter { it.server == server }
            .associate { entry ->
                val songs = entry.playlist.songs.distinctBy { it.id }
                entry.playlist.id to PlaylistDownloadStatus(
                    downloadedSongs = songs.count { files.uriOf(it.id) != null },
                    // Until the songs got fetched only the count is known
                    totalSongs = if (songs.isEmpty()) entry.playlist.songCount else songs.size,
                    isRunning = jobs[key(server, entry.playlist.id)]?.isActive == true,
                )
            }
    }

    private suspend fun entry(server: String, playlistId: String): DownloadedPlaylist? =
        load().firstOrNull { it.server == server && it.playlist.id == playlistId }

    private suspend fun load(): List<DownloadedPlaylist> = entries ?: (
        readAppFile(FILE_NAME)?.let {
            runCatching { offlineJson.decodeFromString<List<DownloadedPlaylist>>(it) }.getOrNull()
        } ?: emptyList()
    ).also { entries = it }

    private suspend fun save(new: List<DownloadedPlaylist>) {
        entries = new
        writeAppFile(FILE_NAME, offlineJson.encodeToString(new))
    }

    private fun key(server: String, playlistId: String) = "$server\n$playlistId"

    private companion object {
        const val FILE_NAME = "downloaded-playlists.json"
    }
}
