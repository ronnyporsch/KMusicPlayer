package de.ronnyporsch.open_rgb.kmusicplayer.ui.screen.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.PlaybackStatus
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.formatDurationMs
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.ronnyporsch.open_rgb.kmusicplayer.platform.AudioPlayer
import de.ronnyporsch.open_rgb.kmusicplayer.platform.VideoSurface
import de.ronnyporsch.open_rgb.kmusicplayer.ui.components.CoverArtImage
import de.ronnyporsch.open_rgb.kmusicplayer.ui.components.TooltipIconButton
import de.ronnyporsch.open_rgb.kmusicplayer.ui.theme.AccentColor
import kotlinx.coroutines.delay

/**
 * The picture of the video [player] plays, letterboxed, with a fullscreen button at the bottom right (unless
 * [showFullscreenButton] is false); double click/tap toggles fullscreen too. In fullscreen the button and
 * [controls] hide when the pointer rests.
 */
@Composable
fun VideoPlayerView(
    player: AudioPlayer,
    /** Of the picture; null until known, meanwhile [thumbnailUrl] is shown. */
    aspectRatio: Float?,
    thumbnailUrl: String?,
    isBuffering: Boolean,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
    /** Shown along with the fullscreen button, e.g. playback controls; leaves room for the button at the bottom right. */
    controls: (@Composable BoxScope.() -> Unit)? = null,
    /** False if the fullscreen button is elsewhere; double click/tap still toggles fullscreen. */
    showFullscreenButton: Boolean = true,
) {
    var interactions by remember { mutableIntStateOf(0) }
    var controlsVisible by remember { mutableStateOf(true) }
    LaunchedEffect(isFullscreen, interactions) {
        controlsVisible = true
        if (isFullscreen) {
            delay(3000)
            controlsVisible = false
        }
    }

    Box(
        modifier
            .background(Color.Black)
            // Before the children see them, so moving over the controls keeps them shown too
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Move || event.type == PointerEventType.Press) interactions++
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.aspectRatio(aspectRatio ?: (16f / 9f))) {
            if (aspectRatio == null) {
                CoverArtImage(url = thumbnailUrl, modifier = Modifier.fillMaxSize(), placeholderFontSize = 80.sp)
            }
            VideoSurface(player, Modifier.fillMaxSize())
        }
        if (isBuffering) {
            CircularProgressIndicator(color = AccentColor)
        }
        // On top of the picture, which (an Android view, a browser element) mustn't get the input
        Box(
            Modifier
                .matchParentSize()
                .pointerInput(onToggleFullscreen) {
                    detectTapGestures(onDoubleTap = { onToggleFullscreen() })
                }
        )
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.matchParentSize(),
        ) {
            Box(Modifier.fillMaxSize()) {
                controls?.invoke(this)
                // The button's modifier applies inside its tooltip, so it's aligned by a box around it
                if (showFullscreenButton) Box(Modifier.align(Alignment.BottomEnd).padding(8.dp)) {
                    TooltipIconButton(
                        onClick = onToggleFullscreen,
                        icon = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = if (isFullscreen) "Exit fullscreen" else "Fullscreen",
                        tint = Color.White,
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape),
                    )
                }
            }
        }
    }
}

/** Seek bar, times and play/pause, previous and next over the bottom of a fullscreen video. */
@Composable
fun BoxScope.FullscreenVideoControls(viewModel: PlayerViewModel) {
    val state by viewModel.state.collectAsState()
    val playback = state.playback
    val song = playback.currentSong
    Column(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
            // Room for the fullscreen button at the bottom right
            .padding(start = 24.dp, end = 64.dp, top = 32.dp, bottom = 8.dp),
    ) {
        if (song != null) {
            Text(song.title, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                song.artists.joinToString(", ") { it.name }.ifBlank { song.artist },
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(playback.positionMs.formatDurationMs(), color = Color.White, style = MaterialTheme.typography.bodySmall)
            Slider(
                value = if (playback.durationMs > 0) (playback.positionMs.toFloat() / playback.durationMs).coerceIn(0f, 1f) else 0f,
                onValueChange = { fraction -> viewModel.onEvent(PlayerEvent.SeekTo((fraction * playback.durationMs).toLong())) },
                colors = SliderDefaults.colors(
                    thumbColor = AccentColor,
                    activeTrackColor = AccentColor,
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                ),
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            )
            Text(playback.durationMs.formatDurationMs(), color = Color.White, style = MaterialTheme.typography.bodySmall)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            TooltipIconButton(
                onClick = { viewModel.onEvent(PlayerEvent.Previous) },
                icon = Icons.Default.SkipPrevious,
                contentDescription = "Previous song",
                tint = Color.White,
            )
            Spacer(Modifier.width(24.dp))
            TooltipIconButton(
                onClick = { viewModel.onEvent(PlayerEvent.PlayPause) },
                icon = when (playback.status) {
                    PlaybackStatus.Playing, PlaybackStatus.Buffering -> Icons.Default.Pause
                    PlaybackStatus.Paused, PlaybackStatus.Idle -> Icons.Default.PlayArrow
                },
                contentDescription = "Play or pause",
                tint = Color.White,
                modifier = Modifier.size(56.dp),
                iconSize = 40.dp,
            )
            Spacer(Modifier.width(24.dp))
            TooltipIconButton(
                onClick = { viewModel.onEvent(PlayerEvent.Next) },
                icon = Icons.Default.SkipNext,
                contentDescription = "Next song",
                tint = Color.White,
            )
        }
    }
}
