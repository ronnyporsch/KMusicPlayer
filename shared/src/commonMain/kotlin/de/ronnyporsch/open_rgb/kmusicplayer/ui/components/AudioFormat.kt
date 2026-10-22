package de.ronnyporsch.open_rgb.kmusicplayer.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.AudioStreamInfo
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.Song
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.audioFormat
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.isYouTube

/** Codec and bitrate, e.g. "FLAC • 912 kbps"; nothing if both are unknown. */
@Composable
fun AudioFormatText(info: AudioStreamInfo?, modifier: Modifier = Modifier) {
    val text = listOfNotNull(info?.codec, info?.bitRate?.let { "$it kbps" }).joinToString(" • ")
    if (text.isEmpty()) return
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = modifier,
    )
}

/**
 * Codec and bitrate of [this] song's file in the library: library songs know their own, YouTube Music songs have
 * them in [youTubeFormats] once downloaded.
 */
fun Song.libraryFormat(youTubeFormats: Map<String, AudioStreamInfo>): AudioStreamInfo? =
    if (isYouTube) youTubeFormats[id] else audioFormat
