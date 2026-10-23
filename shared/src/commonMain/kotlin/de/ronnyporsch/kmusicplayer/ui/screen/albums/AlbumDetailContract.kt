package de.ronnyporsch.kmusicplayer.ui.screen.albums

import de.ronnyporsch.kmusicplayer.domain.model.AudioStreamInfo
import de.ronnyporsch.kmusicplayer.data.youtube.DownloadStatus
import de.ronnyporsch.kmusicplayer.domain.model.Album
import de.ronnyporsch.kmusicplayer.domain.model.Song

data class AlbumDetailState(
    val album: Album? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedSongIds: Set<String> = emptySet(),
    /** Whether this is a YouTube Music album, whose songs can be downloaded into the library if [canDownload]. */
    val isYouTube: Boolean = false,
    val canDownload: Boolean = false,
    /** YouTube Music downloads by song id. */
    val downloads: Map<String, DownloadStatus> = emptyMap(),
    /** Codec and bitrate of downloaded YouTube Music songs, by song id. */
    val libraryFormats: Map<String, AudioStreamInfo> = emptyMap(),
)

sealed class AlbumDetailEvent {
    data class SongClicked(val song: Song, val index: Int) : AlbumDetailEvent()
    data class SongDoubleClicked(val song: Song, val index: Int) : AlbumDetailEvent()
    /** Ctrl-click: adds the song to or removes it from the selection. */
    data class SongSelectionToggled(val song: Song) : AlbumDetailEvent()
    data object PlayAll : AlbumDetailEvent()
    data object ShuffleAll : AlbumDetailEvent()
    data class ArtistClicked(val artistName: String) : AlbumDetailEvent()
    /** Downloads a YouTube Music song into the library. */
    data class DownloadSong(val song: Song) : AlbumDetailEvent()
    /** Downloads all songs of a YouTube Music album into the library. */
    data object DownloadAll : AlbumDetailEvent()
}

sealed class AlbumDetailEffect {
    data class PlaySongs(val songs: List<Song>, val startIndex: Int, val shuffle: Boolean? = null) : AlbumDetailEffect()
    /** [artistId] is only known for YouTube Music artists. */
    data class NavigateToArtist(val artistName: String, val artistId: String? = null) : AlbumDetailEffect()
}
