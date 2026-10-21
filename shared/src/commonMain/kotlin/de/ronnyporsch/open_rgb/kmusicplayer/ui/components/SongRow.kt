package de.ronnyporsch.open_rgb.kmusicplayer.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.Song
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.formatDuration
import de.ronnyporsch.open_rgb.kmusicplayer.getPlatform
import de.ronnyporsch.open_rgb.kmusicplayer.ui.LocalCoverArtUrlProvider
import de.ronnyporsch.open_rgb.kmusicplayer.ui.theme.SpotifyGreen

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongRow(
    song: Song,
    onSongClick: () -> Unit,
    onArtistClick: (String) -> Unit,
    index: Int? = null,
    onAlbumClick: ((String) -> Unit)? = null,
    showCoverArt: Boolean = true,
    showArtist: Boolean = true,
    showAlbum: Boolean = false,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    isSelected: Boolean = false,
    isPlaying: Boolean = false,
    onSongDoubleClick: (() -> Unit)? = null,
    onRemoveClick: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    onDragStart: (Song) -> Unit = {},
    onDragEnd: () -> Unit = {},
) {
    val coverArt = LocalCoverArtUrlProvider.current
    val platform = getPlatform()

    val currentOnSongClick by rememberUpdatedState(onSongClick)
    val currentOnSongDoubleClick by rememberUpdatedState(onSongDoubleClick)

    val rowModifier = if (platform.isMobile) {
        modifier
            .fillMaxWidth()
            .background(if (isSelected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
            .clickable(onClick = onSongClick)
    } else {
        modifier
            .fillMaxWidth()
            .background(if (isSelected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
            .combinedClickable(
                onClick = onSongClick,
                onDoubleClick = onSongDoubleClick,
                onLongClick = { onDragStart(song) }
            )
    }

    Row(
        modifier = rowModifier.padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (index != null) {
            Text(
                text = "${index + 1}.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(32.dp),
            )
        }

        if (showCoverArt) {
            CoverArtImage(
                url = coverArt.getUrl(song.coverArtId),
                modifier = Modifier.size(40.dp),
                cornerRadius = 0.dp,
                placeholderFontSize = 16.sp,
            )
            Spacer(Modifier.width(12.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isPlaying) SpotifyGreen else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showArtist) {
                    ClickableArtistText(
                        artists = song.artists,
                        fallback = song.artist.ifBlank { "Unknown artist" },
                        onArtistClick = onArtistClick,
                    )
                }
                if (showAlbum && song.album.isNotEmpty()) {
                    if (showArtist) {
                        Text(text = " \u2022 ", style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        text = song.album,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (onAlbumClick != null) SpotifyGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (onAlbumClick != null) Modifier.clickable { onAlbumClick(song.albumId) } else Modifier,
                    )
                }
            }
        }

        Spacer(Modifier.width(12.dp))

        Text(
            text = song.duration.formatDuration(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (onRemoveClick != null) {
            Spacer(Modifier.width(12.dp))
            TooltipIconButton(
                onClick = onRemoveClick,
                icon = Icons.Default.Close,
                contentDescription = "Remove from playlist",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(24.dp)
            )
        }

        if (trailingContent != null) {
            Spacer(Modifier.width(12.dp))
            trailingContent()
        }
    }
}
