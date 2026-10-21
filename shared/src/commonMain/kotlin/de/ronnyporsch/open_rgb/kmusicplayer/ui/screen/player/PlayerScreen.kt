package de.ronnyporsch.open_rgb.kmusicplayer.ui.screen.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.PlaybackStatus
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.RepeatMode
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.formatDurationMs
import de.ronnyporsch.open_rgb.kmusicplayer.ui.LocalCoverArtUrlProvider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.GraphicEq
import de.ronnyporsch.open_rgb.kmusicplayer.ui.components.ClickableArtistText
import de.ronnyporsch.open_rgb.kmusicplayer.ui.components.CoverArtImage
import de.ronnyporsch.open_rgb.kmusicplayer.ui.components.TooltipIconButton
import de.ronnyporsch.open_rgb.kmusicplayer.ui.theme.SpotifyGreen
import de.ronnyporsch.open_rgb.kmusicplayer.ui.theme.SpotifyLightGray
import de.ronnyporsch.open_rgb.kmusicplayer.ui.theme.SpotifySurface

@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onArtistClick: (artistName: String) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val playback = state.playback
    val song = playback.currentSong
    val coverArt = LocalCoverArtUrlProvider.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
        }

        Spacer(Modifier.weight(0.5f))

        CoverArtImage(
            url = coverArt.getUrl(song?.coverArtId),
            modifier = Modifier.size(280.dp),
            cornerRadius = 0.dp,
            placeholderFontSize = 80.sp,
        )

        Spacer(Modifier.weight(0.5f))

        Text(
            text = song?.title ?: "Not Playing",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )

        if (song != null) {
            ClickableArtistText(
                artists = song.artists,
                fallback = song.artist,
                onArtistClick = onArtistClick,
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            Text(
                text = "",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(24.dp))

        Column(modifier = Modifier.fillMaxWidth()) {
            Slider(
                value = if (playback.durationMs > 0) {
                    (playback.positionMs.toFloat() / playback.durationMs.toFloat()).coerceIn(0f, 1f)
                } else 0f,
                onValueChange = { fraction ->
                    val pos = (fraction * playback.durationMs).toLong()
                    viewModel.onEvent(PlayerEvent.SeekTo(pos))
                },
                colors = SliderDefaults.colors(
                    thumbColor = SpotifyGreen,
                    activeTrackColor = SpotifyGreen,
                    inactiveTrackColor = SpotifySurface,
                ),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(playback.positionMs.formatDurationMs(), style = MaterialTheme.typography.bodySmall)
                Text(playback.durationMs.formatDurationMs(), style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TooltipIconButton(
                onClick = { viewModel.onEvent(PlayerEvent.ToggleShuffle) },
                icon = Icons.Default.Shuffle,
                contentDescription = "Shuffle",
                tint = if (playback.shuffle) SpotifyGreen else SpotifyLightGray,
            )

            TooltipIconButton(
                onClick = { viewModel.onEvent(PlayerEvent.Previous) },
                icon = Icons.Default.SkipPrevious,
                contentDescription = "Previous song",
                tint = MaterialTheme.colorScheme.onBackground,
            )

            Box(
                modifier = Modifier.size(64.dp).clip(CircleShape).background(SpotifyGreen),
                contentAlignment = Alignment.Center,
            ) {
                TooltipIconButton(
                    onClick = { viewModel.onEvent(PlayerEvent.PlayPause) },
                    icon = when (playback.status) {
                        PlaybackStatus.Playing -> Icons.Default.Pause
                        PlaybackStatus.Buffering -> Icons.Default.PlayArrow
                        else -> Icons.Default.PlayArrow
                    },
                    contentDescription = "Play or pause",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            TooltipIconButton(
                onClick = { viewModel.onEvent(PlayerEvent.Next) },
                icon = Icons.Default.SkipNext,
                contentDescription = "Next song",
                tint = MaterialTheme.colorScheme.onBackground,
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
            )

            TooltipIconButton(
                onClick = { viewModel.onEvent(PlayerEvent.ToggleCrossfade) },
                icon = Icons.Default.GraphicEq,
                contentDescription = "Crossfade",
                tint = if (playback.crossfadeEnabled) SpotifyGreen else SpotifyLightGray,
            )
        }

        Spacer(Modifier.height(32.dp))
    }
}
