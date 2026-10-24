package de.ronnyporsch.voidcast.platform

import de.ronnyporsch.voidcast.domain.model.PlaybackState
import de.ronnyporsch.voidcast.domain.model.Song
import de.ronnyporsch.voidcast.ui.screen.player.PlayerEvent
import kotlinx.coroutines.flow.Flow

actual fun createSystemMediaControls(
    playback: Flow<PlaybackState>,
    onEvent: (PlayerEvent) -> Unit,
    coverArtUrl: (Song) -> String?,
): SystemMediaControls? = null
