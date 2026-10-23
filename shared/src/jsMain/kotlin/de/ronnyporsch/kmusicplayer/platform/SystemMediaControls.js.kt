package de.ronnyporsch.kmusicplayer.platform

import de.ronnyporsch.kmusicplayer.domain.model.PlaybackState
import de.ronnyporsch.kmusicplayer.domain.model.Song
import de.ronnyporsch.kmusicplayer.ui.screen.player.PlayerEvent
import kotlinx.coroutines.flow.Flow

actual fun createSystemMediaControls(
    playback: Flow<PlaybackState>,
    onEvent: (PlayerEvent) -> Unit,
    coverArtUrl: (Song) -> String?,
): SystemMediaControls? = null
