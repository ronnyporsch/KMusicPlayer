package de.ronnyporsch.open_rgb.kmusicplayer.ui.screen.library

import de.ronnyporsch.open_rgb.kmusicplayer.data.offline.PlaylistDownloadStatus
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.Album
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.Artist
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.Playlist
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.Song

enum class LibrarySidebarTab { Playlists, Artists, Favorites }

enum class LibraryDetailView { Artists, Albums, Playlists, FavoriteSongs }

data class LibraryState(
    val selectedSidebarTab: LibrarySidebarTab = LibrarySidebarTab.Playlists,
    val activeDetailView: LibraryDetailView = LibraryDetailView.Playlists,
    val artists: List<Artist> = emptyList(),
    val selectedArtistId: String? = null,
    val selectedArtist: Artist? = null,
    val selectedArtistAlbums: List<Album> = emptyList(),
    val selectedArtistAppearsOnAlbums: List<Album> = emptyList(),
    val selectedArtistBiography: String? = null,
    val selectedArtistLargeImageUrl: String? = null,
    val selectedArtistSongs: List<Song> = emptyList(),
    val selectedArtistSongsLoading: Boolean = false,
    val selectedAlbumId: String? = null,
    val selectedAlbum: Album? = null,
    val playlists: List<Playlist> = emptyList(),
    val selectedPlaylistId: String? = null,
    val selectedPlaylist: Playlist? = null,
    val selectedPlaylistLoading: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedSongIds: Set<String> = emptySet(),
    /** The server isn't reachable; only the playlists stored on the device are shown. */
    val isOffline: Boolean = false,
    /** Whether playlists can be downloaded on this platform. */
    val canDownloadPlaylists: Boolean = false,
    /** Downloaded playlists, by id. */
    val playlistDownloads: Map<String, PlaylistDownloadStatus> = emptyMap(),
    val favoriteArtists: List<Artist> = emptyList(),
    val favoriteAlbums: List<Album> = emptyList(),
    val favoriteSongs: List<Song> = emptyList(),
)

sealed class LibraryEvent {
    data class SidebarTabSelected(val tab: LibrarySidebarTab) : LibraryEvent()
    data object Refresh : LibraryEvent()
    /** Connecting to the server failed; show what is stored on the device meanwhile. */
    data object ConnectionFailed : LibraryEvent()
    data class DownloadPlaylist(val playlist: Playlist) : LibraryEvent()
    data class RemovePlaylistDownload(val playlist: Playlist) : LibraryEvent()
    /** Mobile: open the item on its own screen instead of the detail pane. */
    data class OpenArtistScreen(val artist: Artist) : LibraryEvent()
    data class OpenAlbumScreen(val album: Album) : LibraryEvent()
    data class OpenPlaylistScreen(val playlist: Playlist) : LibraryEvent()
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
    data class RemoveSongFromPlaylist(val playlist: Playlist, val index: Int) : LibraryEvent()
    data object PlayAllPlaylistSongs : LibraryEvent()
    data object ShuffleAllPlaylistSongs : LibraryEvent()
    data class SongClicked(val song: Song, val index: Int) : LibraryEvent()
    data class SongDoubleClicked(val song: Song, val index: Int) : LibraryEvent()
    /** Ctrl-click: adds the song to or removes it from the selection. */
    data class SongSelectionToggled(val song: Song) : LibraryEvent()
    data class SongArtistClicked(val artistName: String) : LibraryEvent()
    /** Shows the favorite songs in the detail pane. */
    data object FavoriteSongsClicked : LibraryEvent()
    data object OpenFavoriteSongsScreen : LibraryEvent()
    data object PlayAllFavoriteSongs : LibraryEvent()
    data object ShuffleAllFavoriteSongs : LibraryEvent()
    data class SongAlbumClicked(val albumId: String) : LibraryEvent()
}

sealed class LibraryEffect {
    data class NavigateToArtist(val artistId: String?, val artistName: String) : LibraryEffect()
    data class NavigateToAlbum(val albumId: String) : LibraryEffect()
    data class NavigateToPlaylist(val playlistId: String) : LibraryEffect()
    data object NavigateToFavoriteSongs : LibraryEffect()
    data class PlaySongs(val songs: List<Song>, val startIndex: Int, val shuffle: Boolean? = null) : LibraryEffect()
}
