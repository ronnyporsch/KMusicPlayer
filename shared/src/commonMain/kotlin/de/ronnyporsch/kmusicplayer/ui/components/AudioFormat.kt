package de.ronnyporsch.kmusicplayer.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import de.ronnyporsch.kmusicplayer.domain.model.AudioStreamInfo
import de.ronnyporsch.kmusicplayer.domain.model.Song
import de.ronnyporsch.kmusicplayer.domain.model.VideoStreamInfo
import de.ronnyporsch.kmusicplayer.domain.model.audioFormat
import de.ronnyporsch.kmusicplayer.domain.model.isYouTube

/** Codec and bitrate, e.g. "FLAC • 912 kbps"; nothing if both are unknown. */
@Composable
fun AudioFormatText(info: AudioStreamInfo?, modifier: Modifier = Modifier) {
    val text = info?.description().orEmpty()
    if (text.isEmpty()) return
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = modifier,
    )
}

/** E.g. "H.264 • 1080p30 • 2500 kbps"; nothing if all is unknown. */
@Composable
fun VideoFormatText(info: VideoStreamInfo?, modifier: Modifier = Modifier) {
    val resolution = info?.height?.let { height -> "${height}p" + (info.frameRate?.takeIf { it > 30 }?.toString() ?: "") }
    val text = listOfNotNull(info?.codec, resolution, info?.bitRate?.let { "$it kbps" }).joinToString(" • ")
    if (text.isEmpty()) return
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = modifier,
    )
}

/** E.g. "FLAC • 912 kbps"; empty if both are unknown. */
fun AudioStreamInfo.description(): String = listOfNotNull(codec, bitRate?.let { "$it kbps" }).joinToString(" • ")

/**
 * Codec and bitrate of [this] song's file in the library: library songs know their own, YouTube Music songs have
 * them in [youTubeFormats] once downloaded (or once their stream got looked up).
 */
fun Song.libraryFormat(youTubeFormats: Map<String, AudioStreamInfo>): AudioStreamInfo? =
    if (isYouTube) youTubeFormats[id] else audioFormat
