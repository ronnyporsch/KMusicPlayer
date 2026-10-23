package de.ronnyporsch.kmusicplayer.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.ronnyporsch.kmusicplayer.data.youtube.DownloadStatus
import de.ronnyporsch.kmusicplayer.ui.theme.AccentColor
import de.ronnyporsch.kmusicplayer.ui.theme.SpotifyLightGray

/** Downloads a YouTube Music song into the server's library and shows the progress. */
@Composable
fun YouTubeDownloadButton(status: DownloadStatus?, onDownload: () -> Unit) {
    when (status) {
        DownloadStatus.InProgress -> Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = AccentColor, strokeWidth = 2.dp)
        }
        is DownloadStatus.Done, DownloadStatus.InLibrary -> TooltipIconButton(
            onClick = {},
            icon = Icons.Default.CheckCircle,
            contentDescription = if (status is DownloadStatus.Done) "Added to library" else "Already in library",
            tint = AccentColor,
        )
        is DownloadStatus.Failed -> TooltipIconButton(
            onClick = onDownload,
            icon = Icons.Default.ErrorOutline,
            contentDescription = "Download failed, click to retry",
            tint = MaterialTheme.colorScheme.error,
        )
        null -> TooltipIconButton(
            onClick = onDownload,
            icon = Icons.Default.Download,
            contentDescription = "Download to library",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Where a finished download went or why it failed; shown below the song's row. */
@Composable
fun YouTubeDownloadMessage(status: DownloadStatus?) {
    val message = when (status) {
        is DownloadStatus.Done -> status.message
        is DownloadStatus.Failed -> status.message
        else -> return
    }
    Text(
        message,
        style = MaterialTheme.typography.bodySmall,
        color = if (status is DownloadStatus.Failed) MaterialTheme.colorScheme.error else SpotifyLightGray,
        modifier = Modifier.padding(start = 68.dp, end = 16.dp, bottom = 8.dp),
    )
}
