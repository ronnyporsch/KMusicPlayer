package de.ronnyporsch.open_rgb.kmusicplayer.ui.screen.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.ronnyporsch.open_rgb.kmusicplayer.data.remote.RemoteDevice
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.PlaybackStatus
import de.ronnyporsch.open_rgb.kmusicplayer.getPlatform
import de.ronnyporsch.open_rgb.kmusicplayer.ui.components.TooltipIconButton
import de.ronnyporsch.open_rgb.kmusicplayer.ui.theme.AccentColor
import de.ronnyporsch.open_rgb.kmusicplayer.ui.theme.SpotifyLightGray

/** Picks the device that plays: this one or another one of the user's (Jellyfin only, hidden otherwise). */
@Composable
fun DevicesButton(
    state: PlayerState,
    onEvent: (PlayerEvent) -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = SpotifyLightGray,
    iconSize: Dp = 24.dp,
) {
    if (!state.remoteControlAvailable) return
    var expanded by remember { mutableStateOf(false) }
    val selected = state.remoteDevice
    Box {
        TooltipIconButton(
            onClick = { expanded = true },
            icon = Icons.Default.Devices,
            contentDescription = selected?.let { "Playing on ${it.name}" } ?: "Play on another device",
            tint = if (selected != null) AccentColor else tint,
            modifier = modifier,
            iconSize = iconSize,
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.widthIn(max = 360.dp)) {
            Text(
                "Play on",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
            DeviceItem(
                name = "This device",
                details = getPlatform().deviceName,
                isSelected = selected == null,
                onClick = {
                    expanded = false
                    onEvent(PlayerEvent.SelectDevice(null))
                },
            )
            state.devices.forEach { device ->
                DeviceItem(
                    name = device.name,
                    details = device.details(),
                    isSelected = device.sessionId == selected?.sessionId,
                    onClick = {
                        expanded = false
                        onEvent(PlayerEvent.SelectDevice(device.sessionId))
                    },
                )
            }
            if (state.devices.isEmpty()) {
                Text(
                    "No other devices. Open KMusicPlayer or Jellyfin on another device with the same account.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun DeviceItem(name: String, details: String, isSelected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Column {
                Text(name, color = if (isSelected) AccentColor else Color.Unspecified, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        leadingIcon = {
            if (isSelected) Icon(Icons.Default.Check, contentDescription = null, tint = AccentColor)
            else Spacer(Modifier.size(24.dp))
        },
        onClick = onClick,
    )
}

/** The app and what plays there. */
private fun RemoteDevice.details(): String {
    val song = playback.currentSong ?: return client
    val what = listOf(song.title, song.artist).filter { it.isNotBlank() }.joinToString(" – ")
    return if (playback.status == PlaybackStatus.Paused) "$client · Paused: $what" else "$client · $what"
}

/** "Playing on …" while another device plays, else nothing. */
@Composable
fun PlayingOnText(state: PlayerState, modifier: Modifier = Modifier) {
    val device = state.remoteDevice ?: return
    Text(
        "Playing on ${device.name}",
        style = MaterialTheme.typography.labelSmall,
        color = AccentColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}
