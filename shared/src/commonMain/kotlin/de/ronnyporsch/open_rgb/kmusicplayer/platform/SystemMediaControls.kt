package de.ronnyporsch.open_rgb.kmusicplayer.platform

import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.PlaybackState
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.Song
import de.ronnyporsch.open_rgb.kmusicplayer.ui.screen.player.PlayerEvent
import kotlinx.coroutines.flow.Flow

/**
 * Lets the operating system show and control playback: the media notification (and lock screen, Bluetooth buttons)
 * on Android, MPRIS on Linux desktops, which is what media keys and the desktop's media widgets talk to.
 */
interface SystemMediaControls {
    fun release()
}

/**
 * Mirrors [playback] to the OS and passes its commands to [onEvent] on the main thread. Returns null where the
 * platform has nothing to offer.
 */
expect fun createSystemMediaControls(
    playback: Flow<PlaybackState>,
    onEvent: (PlayerEvent) -> Unit,
    coverArtUrl: (Song) -> String?,
): SystemMediaControls?
