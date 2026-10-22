package de.ronnyporsch.open_rgb.kmusicplayer.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Diagonal karaoke microphone (like Spotify's lyrics button), Material has none without a cable. */
object LyricsIcons {
    val Lyrics: ImageVector by lazy {
        lyricsIcon("Lyrics") { microphone() }
    }

    private fun lyricsIcon(name: String, content: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(content).build()

    private fun ImageVector.Builder.microphone() {
        // Head
        outline {
            moveTo(19.7f, 8.5f)
            arcTo(4.2f, 4.2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 11.3f, 8.5f)
            arcTo(4.2f, 4.2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 19.7f, 8.5f)
            close()
        }
        // Handle, tapering towards the bottom left
        outline {
            moveTo(9.7f, 11.6f)
            lineTo(4.2f, 18.4f)
            arcTo(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, 5.6f, 19.8f)
            lineTo(12.4f, 14.3f)
            close()
        }
    }

    private fun ImageVector.Builder.outline(pathBuilder: PathBuilder.() -> Unit) {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = pathBuilder,
        )
    }
}
