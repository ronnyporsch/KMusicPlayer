package de.ronnyporsch.open_rgb.kmusicplayer.platform

import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.PlaybackState
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.Song
import de.ronnyporsch.open_rgb.kmusicplayer.ui.screen.player.PlayerEvent
import kotlinx.coroutines.flow.Flow

actual fun createSystemMediaControls(
    playback: Flow<PlaybackState>,
    onEvent: (PlayerEvent) -> Unit,
    coverArtUrl: (Song) -> String?,
): SystemMediaControls? = null
