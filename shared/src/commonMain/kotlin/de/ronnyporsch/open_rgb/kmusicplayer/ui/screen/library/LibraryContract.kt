package de.ronnyporsch.open_rgb.kmusicplayer.ui.screen.library

import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.Album
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.Artist
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.Playlist
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.Song

enum class LibrarySidebarTab { Artists, Albums, Playlists }

enum class LibraryDetailView { Artists, Albums, Playlists }

data class LibraryState(
    val selectedSidebarTab: LibrarySidebarTab = LibrarySidebarTab.Artists,
    val activeDetailView: LibraryDetailView = LibraryDetailView.Artists,
    val artists: List<Artist> = emptyList(),
    val selectedArtistId: String? = null,
    val selectedArtist: Artist? = null,
    val selectedArtistAlbums: List<Album> = emptyList(),
    val selectedArtistAppearsOnAlbums: List<Album> = emptyList(),
    val selectedArtistBiography: String? = null,
    val selectedArtistLargeImageUrl: String? = null,
    val selectedArtistSongs: List<Song> = emptyList(),
    val selectedArtistSongsLoading: Boolean = false,
    val albums: List<Album> = emptyList(),
    val selectedAlbumId: String? = null,
    val selectedAlbum: Album? = null,
    val playlists: List<Playlist> = emptyList(),
    val selectedPlaylistId: String? = null,
    val selectedPlaylist: Playlist? = null,
    val selectedPlaylistLoading: Boolean = false,
    val albumsLoading: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedSongId: String? = null,
)

sealed class LibraryEvent {
    data class SidebarTabSelected(val tab: LibrarySidebarTab) : LibraryEvent()
    data object Refresh : LibraryEvent()
    data class ArtistClicked(val artist: Artist) : LibraryEvent()
    data class ArtistDoubleClicked(val artist: Artist) : LibraryEvent()
    data object PlayAllArtistSongs : LibraryEvent()
    data object ShuffleAllArtistSongs : LibraryEvent()
    data class ArtistSongClicked(val song: Song, val index: Int) : LibraryEvent()
    data class ArtistSongDoubleClicked(val song: Song, val index: Int) : LibraryEvent()
    data class AlbumClicked(val album: Album) : LibraryEvent()
    data class AlbumDoubleClicked(val album: Album) : LibraryEvent()
    data class AlbumOpened(val album: Album) : LibraryEvent()
    data object PlayAllAlbumSongs : LibraryEvent()
    data object ShuffleAllAlbumSongs : LibraryEvent()
    data class PlaylistClicked(val playlist: Playlist) : LibraryEvent()
    data class PlaylistDoubleClicked(val playlist: Playlist) : LibraryEvent()
    data class CreatePlaylist(val name: String) : LibraryEvent()
    data class AddSongToPlaylist(val playlist: Playlist, val song: Song) : LibraryEvent()
    data class RemoveSongFromPlaylist(val playlist: Playlist, val index: Int) : LibraryEvent()
    data object PlayAllPlaylistSongs : LibraryEvent()
    data object ShuffleAllPlaylistSongs : LibraryEvent()
    data class SongClicked(val song: Song, val index: Int) : LibraryEvent()
    data class SongDoubleClicked(val song: Song, val index: Int) : LibraryEvent()
    data class SongArtistClicked(val artistName: String) : LibraryEvent()
    data class SongAlbumClicked(val albumId: String) : LibraryEvent()
}

sealed class LibraryEffect {
    data class NavigateToArtist(val artistId: String?, val artistName: String) : LibraryEffect()
    data class NavigateToAlbum(val albumId: String) : LibraryEffect()
    data class NavigateToPlaylist(val playlistId: String) : LibraryEffect()
    data class PlaySongs(val songs: List<Song>, val startIndex: Int, val shuffle: Boolean? = null) : LibraryEffect()
}
