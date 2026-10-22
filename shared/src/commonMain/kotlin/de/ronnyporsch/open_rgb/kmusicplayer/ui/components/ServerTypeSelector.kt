package de.ronnyporsch.open_rgb.kmusicplayer.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.ServerType
import de.ronnyporsch.open_rgb.kmusicplayer.ui.theme.SpotifyGreen

@Composable
fun ServerTypeSelector(selected: ServerType, onSelected: (ServerType) -> Unit, modifier: Modifier = Modifier) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        ServerType.entries.forEachIndexed { index, type ->
            SegmentedButton(
                selected = type == selected,
                onClick = { onSelected(type) },
                shape = SegmentedButtonDefaults.itemShape(index, ServerType.entries.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = SpotifyGreen.copy(alpha = 0.25f),
                    activeBorderColor = SpotifyGreen,
                ),
            ) {
                Text(type.displayName)
            }
        }
    }
}

val ServerType.urlPlaceholder: String
    get() = when (this) {
        ServerType.Subsonic -> "https://navidrome.example.com"
        ServerType.Jellyfin -> "https://jellyfin.example.com"
    }
