package de.ronnyporsch.kmusicplayer.ui.screen.playlist

import de.ronnyporsch.kmusicplayer.domain.model.Playlist
import de.ronnyporsch.kmusicplayer.domain.model.Song

data class PlaylistDetailState(
    val playlist: Playlist? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedSongIds: Set<String> = emptySet(),
)

sealed class PlaylistDetailEvent {
    data class SongClicked(val song: Song, val index: Int) : PlaylistDetailEvent()
    data class SongDoubleClicked(val song: Song, val index: Int) : PlaylistDetailEvent()
    /** Ctrl-click: adds the song to or removes it from the selection. */
    data class SongSelectionToggled(val song: Song) : PlaylistDetailEvent()
    /** Removes the entries at [indices] (positions in the playlist) from it. */
    data class RemoveSongs(val indices: List<Int>) : PlaylistDetailEvent()
    data object PlayAll : PlaylistDetailEvent()
    data object ShuffleAll : PlaylistDetailEvent()
    data class ArtistClicked(val artistName: String) : PlaylistDetailEvent()
    data class AlbumClicked(val albumId: String) : PlaylistDetailEvent()
}

sealed class PlaylistDetailEffect {
    data class PlaySongs(val songs: List<Song>, val startIndex: Int, val shuffle: Boolean? = null) : PlaylistDetailEffect()
    data class NavigateToArtist(val artistName: String) : PlaylistDetailEffect()
    data class NavigateToAlbum(val albumId: String) : PlaylistDetailEffect()
}
