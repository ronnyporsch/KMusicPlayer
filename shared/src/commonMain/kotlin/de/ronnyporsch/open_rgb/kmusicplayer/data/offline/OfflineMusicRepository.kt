package de.ronnyporsch.open_rgb.kmusicplayer.data.offline

import de.ronnyporsch.open_rgb.kmusicplayer.data.repository.MusicRepository
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.Playlist
import de.ronnyporsch.open_rgb.kmusicplayer.platform.SongFileStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * [server], except that playlists come from [cache] and [downloads] while the server isn't reachable, and that
 * downloaded songs play from their [files].
 */
class OfflineMusicRepository(
    private val server: MusicRepository,
    private val cache: PlaylistCache,
    private val downloads: PlaylistDownloads,
    private val files: SongFileStore?,
) : MusicRepository by server {

    private val _playlistChanges = MutableSharedFlow<String>(extraBufferCapacity = 16)
    /** Ids of playlists that got changed through this repository. */
    val playlistChanges: SharedFlow<String> = _playlistChanges.asSharedFlow()

    override suspend fun getPlaylists(): Result<List<Playlist>> =
        server.getPlaylists()
            .onSuccess { cache.putPlaylists(it) }
            .recoverOffline {
                val cached = cache.playlists()
                val cachedIds = cached.mapTo(mutableSetOf()) { it.id }
                (cached + downloads.playlists().filterNot { it.id in cachedIds }.map { it.copy(songs = emptyList()) })
                    .sortedBy { it.name.lowercase() }
                    .ifEmpty { null }
            }

    override suspend fun getPlaylist(playlistId: String): Result<Playlist> =
        server.getPlaylist(playlistId)
            .onSuccess { cache.putPlaylist(it) }
            .recoverOffline { downloads.playlist(playlistId) ?: cache.playlist(playlistId) }

    override suspend fun createPlaylist(name: String, songIds: List<String>): Result<Playlist> =
        server.createPlaylist(name, songIds).onSuccess { _playlistChanges.emit(it.id) }

    override suspend fun addSongsToPlaylist(playlistId: String, songIds: List<String>): Result<Unit> =
        server.addSongsToPlaylist(playlistId, songIds).onSuccess { changed(playlistId) }

    override suspend fun removeSongsFromPlaylist(playlistId: String, songIndices: List<Int>): Result<Unit> =
        server.removeSongsFromPlaylist(playlistId, songIndices).onSuccess { changed(playlistId) }

    override fun getStreamUrl(songId: String): String = files?.uriOf(songId) ?: server.getStreamUrl(songId)

    private suspend fun changed(playlistId: String) {
        downloads.onPlaylistChanged(playlistId)
        _playlistChanges.emit(playlistId)
    }

    /** Replaces a failure with [fallback], unless that is null too. */
    private suspend fun <T> Result<T>.recoverOffline(fallback: suspend () -> T?): Result<T> {
        val error = exceptionOrNull() ?: return this
        if (error is CancellationException) return this
        return fallback()?.let { Result.success(it) } ?: this
    }
}
