package de.ronnyporsch.open_rgb.kmusicplayer.ui.screen.albums

import de.ronnyporsch.open_rgb.kmusicplayer.data.repository.MusicRepository
import de.ronnyporsch.open_rgb.kmusicplayer.getPlatform
import de.ronnyporsch.open_rgb.kmusicplayer.mvi.MviStore

class AlbumDetailViewModel(
    private val albumId: String,
    private val repository: MusicRepository,
) : MviStore<AlbumDetailState, AlbumDetailEvent, AlbumDetailEffect>(AlbumDetailState()) {

    init {
        loadAlbum()
    }

    override fun onEvent(event: AlbumDetailEvent) {
        when (event) {
            is AlbumDetailEvent.SongClicked -> {
                if (getPlatform().isMobile) {
                    val songs = currentState.album?.songs ?: return
                    emitEffect(AlbumDetailEffect.PlaySongs(songs, event.index))
                } else {
                    setState { copy(selectedSongId = event.song.id) }
                }
            }
            is AlbumDetailEvent.SongDoubleClicked -> {
                setState { copy(selectedSongId = event.song.id) }
                val songs = currentState.album?.songs ?: return
                emitEffect(AlbumDetailEffect.PlaySongs(songs, event.index))
            }
            is AlbumDetailEvent.PlayAll -> {
                val songs = currentState.album?.songs ?: return
                emitEffect(AlbumDetailEffect.PlaySongs(songs, 0))
            }
            is AlbumDetailEvent.ShuffleAll -> {
                val songs = currentState.album?.songs ?: return
                emitEffect(AlbumDetailEffect.PlaySongs(songs, 0, shuffle = true))
            }
            is AlbumDetailEvent.ArtistClicked -> {
                emitEffect(AlbumDetailEffect.NavigateToArtist(event.artistName))
            }
        }
    }

    private fun loadAlbum() {
        setState { copy(isLoading = true) }
        launch {
            repository.getAlbum(albumId)
                .onSuccess { setState { copy(album = it, isLoading = false) } }
                .onFailure { setState { copy(error = it.message, isLoading = false) } }
        }
    }
}
