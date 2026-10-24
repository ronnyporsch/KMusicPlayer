package de.ronnyporsch.voidcast.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import de.ronnyporsch.voidcast.domain.model.ServerType
import de.ronnyporsch.voidcast.ui.theme.AccentColor

@Composable
fun ServerTypeSelector(selected: ServerType, onSelected: (ServerType) -> Unit, modifier: Modifier = Modifier) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        ServerType.entries.forEachIndexed { index, type ->
            SegmentedButton(
                selected = type == selected,
                onClick = { onSelected(type) },
                shape = SegmentedButtonDefaults.itemShape(index, ServerType.entries.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = AccentColor.copy(alpha = 0.25f),
                    activeBorderColor = AccentColor,
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
