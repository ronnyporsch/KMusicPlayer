package de.ronnyporsch.open_rgb.kmusicplayer.ui.screen.player

import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.AudioStreamInfo
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.Lyrics
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.PlaybackState
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.Song

data class PlayerState(
    val playback: PlaybackState = PlaybackState(),
    val showLyrics: Boolean = false,
    /** Lyrics of the current song. */
    val lyrics: LyricsState = LyricsState.Loading,
    /** Codec and bitrate of the current song, once known. */
    val audioFormat: AudioStreamInfo? = null,
)

sealed interface LyricsState {
    data object Loading : LyricsState
    data class Loaded(val lyrics: Lyrics) : LyricsState
    data object NotFound : LyricsState
    data class Failed(val message: String) : LyricsState
}

sealed class PlayerEvent {
    data class PlaySongs(val songs: List<Song>, val startIndex: Int, val shuffle: Boolean? = null) : PlayerEvent()
    data object PlayPause : PlayerEvent()
    data object Play : PlayerEvent()
    data object Pause : PlayerEvent()
    data object Next : PlayerEvent()
    data object Previous : PlayerEvent()
    data object ToggleShuffle : PlayerEvent()
    data object ToggleRepeat : PlayerEvent()
    data object ToggleCrossfade : PlayerEvent()
    data object ToggleLyrics : PlayerEvent()
    data object ShowLyrics : PlayerEvent()
    data class SeekTo(val positionMs: Long) : PlayerEvent()
}

sealed class PlayerEffect {
    data class PlaybackError(val message: String) : PlayerEffect()
}
