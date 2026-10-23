package de.ronnyporsch.open_rgb.kmusicplayer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.Artist
import de.ronnyporsch.open_rgb.kmusicplayer.ui.theme.AccentColor

/**
 * Shows the artists credited by the server's tags, each one clickable.
 * [fallback] is shown as plain text when no artist is credited.
 */
@Composable
fun ClickableArtistText(
    artists: List<Artist>,
    onArtistClick: (artistName: String) -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodySmall,
    prefix: String = "",
    suffix: String = "",
    fallback: String = "",
) {
    if (artists.isEmpty()) {
        Text(
            text = "$prefix$fallback$suffix",
            style = style,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier,
        )
        return
    }

    if (artists.size == 1) {
        val name = artists.single().name
        Text(
            text = "$prefix$name$suffix",
            style = style,
            color = AccentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier.clickable { onArtistClick(name) },
        )
        return
    }

    Row(modifier = modifier) {
        if (prefix.isNotEmpty()) {
            Text(text = prefix, style = style, maxLines = 1)
        }
        artists.forEachIndexed { index, artist ->
            if (index > 0) {
                Text(text = ", ", style = style, maxLines = 1)
            }
            Text(
                text = artist.name,
                style = style,
                color = AccentColor,
                maxLines = 1,
                modifier = Modifier.clickable { onArtistClick(artist.name) },
            )
        }
        if (suffix.isNotEmpty()) {
            Text(text = suffix, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
