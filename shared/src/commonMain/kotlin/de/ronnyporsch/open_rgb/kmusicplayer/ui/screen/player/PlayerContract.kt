package de.ronnyporsch.open_rgb.kmusicplayer.ui.screen.player

import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.PlaybackState
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.Song

data class PlayerState(
    val playback: PlaybackState = PlaybackState(),
)

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
    data class SeekTo(val positionMs: Long) : PlayerEvent()
}

sealed class PlayerEffect {
    data class PlaybackError(val message: String) : PlayerEffect()
}
