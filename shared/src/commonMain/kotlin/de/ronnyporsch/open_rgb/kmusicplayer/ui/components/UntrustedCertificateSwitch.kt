package de.ronnyporsch.open_rgb.kmusicplayer.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.ronnyporsch.open_rgb.kmusicplayer.platform.canAcceptUntrustedCertificates
import de.ronnyporsch.open_rgb.kmusicplayer.ui.theme.AccentColor
import de.ronnyporsch.open_rgb.kmusicplayer.ui.theme.SpotifyLightGray

/** Lets the user accept a self-signed server certificate. Not shown in browsers, which decide that themselves. */
@Composable
fun UntrustedCertificateSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    if (!canAcceptUntrustedCertificates) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Allow self-signed certificate", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Skips verifying the server's HTTPS certificate.",
                style = MaterialTheme.typography.bodySmall,
                color = SpotifyLightGray,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = AccentColor,
                checkedTrackColor = AccentColor.copy(alpha = 0.4f),
            ),
        )
    }
}
