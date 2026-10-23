package de.ronnyporsch.kmusicplayer.ui.screen.library

import de.ronnyporsch.kmusicplayer.domain.model.Album as AlbumModel
import de.ronnyporsch.kmusicplayer.domain.model.Artist as ArtistModel

/** What the library's detail pane shows (desktop). Part of the library screen, so back returns to the previous item. */
sealed class LibrarySelection {
    data class Artist(val artist: ArtistModel) : LibrarySelection()
    data class Album(val album: AlbumModel) : LibrarySelection()
    data class Playlist(val playlistId: String) : LibrarySelection()
    data object FavoriteSongs : LibrarySelection()

    /** Identifies the item, e.g. an album before and after its songs got loaded. */
    val key: String
        get() = when (this) {
            is Artist -> "artist:" + artist.id.ifEmpty { artist.name }
            is Album -> "album:" + album.id
            is Playlist -> "playlist:$playlistId"
            FavoriteSongs -> "favoriteSongs"
        }
}

val LibraryState.selection: LibrarySelection?
    get() = when (activeDetailView) {
        LibraryDetailView.Artists -> selectedArtist?.let { LibrarySelection.Artist(it) }
        LibraryDetailView.Albums -> selectedAlbum?.let { LibrarySelection.Album(it) }
        LibraryDetailView.Playlists -> selectedPlaylistId?.let { LibrarySelection.Playlist(it) }
        LibraryDetailView.FavoriteSongs -> LibrarySelection.FavoriteSongs
    }
