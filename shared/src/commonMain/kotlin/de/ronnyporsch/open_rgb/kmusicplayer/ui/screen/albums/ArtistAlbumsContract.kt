package de.ronnyporsch.open_rgb.kmusicplayer.ui.screen.albums

import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.Album
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.Song

data class ArtistAlbumsState(
    val artistName: String = "",
    val coverArtId: String? = null,
    val biography: String? = null,
    val largeImageUrl: String? = null,
    val albums: List<Album> = emptyList(),
    val appearsOnAlbums: List<Album> = emptyList(),
    val songs: List<Song> = emptyList(),
    val isLoading: Boolean = false,
    val isSongsLoading: Boolean = false,
    val error: String? = null,
    val selectedSongId: String? = null,
)

sealed class ArtistAlbumsEvent {
    data class AlbumClicked(val album: Album) : ArtistAlbumsEvent()
    data class SongClicked(val song: Song, val index: Int) : ArtistAlbumsEvent()
    data class SongDoubleClicked(val song: Song, val index: Int) : ArtistAlbumsEvent()
    data object PlayAllSongs : ArtistAlbumsEvent()
    data object ShuffleAllSongs : ArtistAlbumsEvent()
}

sealed class ArtistAlbumsEffect {
    data class NavigateToAlbum(val albumId: String) : ArtistAlbumsEffect()
    data class PlaySongs(val songs: List<Song>, val startIndex: Int, val shuffle: Boolean? = null) : ArtistAlbumsEffect()
}
