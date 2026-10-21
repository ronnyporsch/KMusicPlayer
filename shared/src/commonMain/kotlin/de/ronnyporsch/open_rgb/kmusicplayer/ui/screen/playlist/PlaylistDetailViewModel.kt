package de.ronnyporsch.open_rgb.kmusicplayer.ui.screen.playlist

import de.ronnyporsch.open_rgb.kmusicplayer.data.repository.MusicRepository
import de.ronnyporsch.open_rgb.kmusicplayer.getPlatform
import de.ronnyporsch.open_rgb.kmusicplayer.mvi.MviStore

class PlaylistDetailViewModel(
    private val playlistId: String,
    private val repository: MusicRepository,
) : MviStore<PlaylistDetailState, PlaylistDetailEvent, PlaylistDetailEffect>(PlaylistDetailState()) {

    init {
        loadPlaylist()
    }

    override fun onEvent(event: PlaylistDetailEvent) {
        when (event) {
            is PlaylistDetailEvent.SongClicked -> {
                if (getPlatform().isMobile) {
                    val songs = currentState.playlist?.songs ?: return
                    emitEffect(PlaylistDetailEffect.PlaySongs(songs, event.index))
                } else {
                    setState { copy(selectedSongId = event.song.id) }
                }
            }
            is PlaylistDetailEvent.SongDoubleClicked -> {
                setState { copy(selectedSongId = event.song.id) }
                val songs = currentState.playlist?.songs ?: return
                emitEffect(PlaylistDetailEffect.PlaySongs(songs, event.index))
            }
            is PlaylistDetailEvent.PlayAll -> {
                val songs = currentState.playlist?.songs ?: return
                emitEffect(PlaylistDetailEffect.PlaySongs(songs, 0))
            }
            is PlaylistDetailEvent.ShuffleAll -> {
                val songs = currentState.playlist?.songs ?: return
                emitEffect(PlaylistDetailEffect.PlaySongs(songs, 0, shuffle = true))
            }
            is PlaylistDetailEvent.ArtistClicked -> {
                emitEffect(PlaylistDetailEffect.NavigateToArtist(event.artistName))
            }
            is PlaylistDetailEvent.AlbumClicked -> {
                emitEffect(PlaylistDetailEffect.NavigateToAlbum(event.albumId))
            }
        }
    }

    private fun loadPlaylist() {
        setState { copy(isLoading = true) }
        launch {
            repository.getPlaylist(playlistId)
                .onSuccess { setState { copy(playlist = it, isLoading = false) } }
                .onFailure { setState { copy(error = it.message, isLoading = false) } }
        }
    }
}
