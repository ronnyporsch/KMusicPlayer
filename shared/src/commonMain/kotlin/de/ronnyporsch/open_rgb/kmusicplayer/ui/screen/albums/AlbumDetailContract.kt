package de.ronnyporsch.open_rgb.kmusicplayer.ui.screen.albums

import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.Album
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.Song

data class AlbumDetailState(
    val album: Album? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedSongIds: Set<String> = emptySet(),
)

sealed class AlbumDetailEvent {
    data class SongClicked(val song: Song, val index: Int) : AlbumDetailEvent()
    data class SongDoubleClicked(val song: Song, val index: Int) : AlbumDetailEvent()
    /** Ctrl-click: adds the song to or removes it from the selection. */
    data class SongSelectionToggled(val song: Song) : AlbumDetailEvent()
    data object PlayAll : AlbumDetailEvent()
    data object ShuffleAll : AlbumDetailEvent()
    data class ArtistClicked(val artistName: String) : AlbumDetailEvent()
}

sealed class AlbumDetailEffect {
    data class PlaySongs(val songs: List<Song>, val startIndex: Int, val shuffle: Boolean? = null) : AlbumDetailEffect()
    data class NavigateToArtist(val artistName: String) : AlbumDetailEffect()
}
