package de.ronnyporsch.open_rgb.kmusicplayer.ui.screen.search

import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.Album
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.Artist
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.Song
import de.ronnyporsch.open_rgb.kmusicplayer.platform.AudioStreamInfo

sealed class DownloadStatus {
    data object InProgress : DownloadStatus()
    data class Done(val message: String) : DownloadStatus()
    data class Failed(val message: String) : DownloadStatus()
}

data class SearchState(
    val query: String = "",
    val artists: List<Artist> = emptyList(),
    val albums: List<Album> = emptyList(),
    val songs: List<Song> = emptyList(),
    val isLoading: Boolean = false,
    val hasSearched: Boolean = false,
    val selectedSongId: String? = null,
    val youTubeAvailable: Boolean = false,
    val youTubeSongs: List<Song> = emptyList(),
    val isYouTubeLoading: Boolean = false,
    val youTubeError: String? = null,
    val downloads: Map<String, DownloadStatus> = emptyMap(),
    /** Keyed by song id; filled in after the search results arrive. */
    val youTubeStreamInfo: Map<String, AudioStreamInfo> = emptyMap(),
)

sealed class SearchEvent {
    data class QueryChanged(val query: String) : SearchEvent()
    data object Search : SearchEvent()
    data class ArtistClicked(val artist: Artist) : SearchEvent()
    data class ArtistNameClicked(val artistName: String) : SearchEvent()
    data class AlbumClicked(val album: Album) : SearchEvent()
    data class SongClicked(val song: Song, val index: Int) : SearchEvent()
    data class SongDoubleClicked(val song: Song, val index: Int) : SearchEvent()
    data class YouTubeSongClicked(val song: Song, val index: Int) : SearchEvent()
    data class YouTubeSongDoubleClicked(val song: Song, val index: Int) : SearchEvent()
    data class DownloadYouTubeSong(val song: Song) : SearchEvent()
}

sealed class SearchEffect {
    data class NavigateToArtist(val artistId: String?, val artistName: String) : SearchEffect()
    data class NavigateToAlbum(val albumId: String) : SearchEffect()
    data class PlaySongs(val songs: List<Song>, val startIndex: Int) : SearchEffect()
}
