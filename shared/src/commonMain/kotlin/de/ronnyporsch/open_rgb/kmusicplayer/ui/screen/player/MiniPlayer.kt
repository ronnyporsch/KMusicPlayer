package de.ronnyporsch.open_rgb.kmusicplayer.ui.screen.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.PlaybackStatus
import de.ronnyporsch.open_rgb.kmusicplayer.ui.LocalCoverArtUrlProvider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import de.ronnyporsch.open_rgb.kmusicplayer.ui.components.ClickableArtistText
import de.ronnyporsch.open_rgb.kmusicplayer.ui.components.CoverArtImage
import de.ronnyporsch.open_rgb.kmusicplayer.ui.components.HoverLinkText
import de.ronnyporsch.open_rgb.kmusicplayer.ui.components.TooltipIconButton
import de.ronnyporsch.open_rgb.kmusicplayer.ui.theme.SpotifyGreen
import de.ronnyporsch.open_rgb.kmusicplayer.ui.theme.SpotifyLightGray
import de.ronnyporsch.open_rgb.kmusicplayer.ui.theme.SpotifySurface
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.RepeatMode

@Composable
fun MiniPlayer(
    viewModel: PlayerViewModel,
    onArtistClick: (artistName: String) -> Unit,
    onAlbumClick: (albumId: String) -> Unit,
    onClick: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val playback = state.playback
    val song = playback.currentSong ?: return
    val coverArt = LocalCoverArtUrlProvider.current

    val progress = if (playback.durationMs > 0) {
        (playback.positionMs.toFloat() / playback.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp))
            .background(SpotifySurface)
            .clickable(onClick = onClick),
    ) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            Slider(
                value = progress,
                onValueChange = {
                    viewModel.onEvent(PlayerEvent.SeekTo((it * playback.durationMs).toLong()))
                },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                colors = SliderDefaults.colors(
                    activeTrackColor = SpotifyGreen,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                    thumbColor = Color.Transparent
                ),
                thumb = {
                    // Empty thumb to keep it looking like a progress bar
                }
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CoverArtImage(
                    url = coverArt.getUrl(song.coverArtId),
                    modifier = Modifier.size(40.dp),
                    cornerRadius = 0.dp,
                    placeholderFontSize = 16.sp,
                )

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.fillMaxWidth()) {
                    HoverLinkText(
                        text = song.title,
                        onClick = song.albumId.takeIf { it.isNotBlank() }?.let { { onAlbumClick(it) } },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(2.dp))
                    ClickableArtistText(
                        artists = song.artists,
                        fallback = song.artist,
                        onArtistClick = onArtistClick,
                    )
                }
            }

            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TooltipIconButton(
                    onClick = { viewModel.onEvent(PlayerEvent.ToggleShuffle) },
                    icon = Icons.Default.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (playback.shuffle) SpotifyGreen else SpotifyLightGray,
                    modifier = Modifier.size(32.dp)
                )

                TooltipIconButton(
                    onClick = { viewModel.onEvent(PlayerEvent.Previous) },
                    icon = Icons.Default.SkipPrevious,
                    contentDescription = "Previous song",
                    tint = MaterialTheme.colorScheme.onSurface,
                )

                TooltipIconButton(
                    onClick = { viewModel.onEvent(PlayerEvent.PlayPause) },
                    icon = when (playback.status) {
                        PlaybackStatus.Playing -> Icons.Default.Pause
                        PlaybackStatus.Buffering -> Icons.Default.PlayArrow
                        else -> Icons.Default.PlayArrow
                    },
                    contentDescription = "Play or pause",
                    tint = MaterialTheme.colorScheme.onSurface,
                )

                TooltipIconButton(
                    onClick = { viewModel.onEvent(PlayerEvent.Next) },
                    icon = Icons.Default.SkipNext,
                    contentDescription = "Next song",
                    tint = MaterialTheme.colorScheme.onSurface,
                )

                TooltipIconButton(
                    onClick = { viewModel.onEvent(PlayerEvent.ToggleRepeat) },
                    icon = when (playback.repeatMode) {
                        RepeatMode.Off -> Icons.Default.Repeat
                        RepeatMode.All -> Icons.Default.Repeat
                        RepeatMode.One -> Icons.Default.RepeatOne
                    },
                    contentDescription = "Repeat",
                    tint = if (playback.repeatMode != RepeatMode.Off) SpotifyGreen else SpotifyLightGray,
                    modifier = Modifier.size(32.dp)
                )

                TooltipIconButton(
                    onClick = { viewModel.onEvent(PlayerEvent.ToggleCrossfade) },
                    icon = Icons.Default.GraphicEq,
                    contentDescription = "Crossfade",
                    tint = if (playback.crossfadeEnabled) SpotifyGreen else SpotifyLightGray,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}
