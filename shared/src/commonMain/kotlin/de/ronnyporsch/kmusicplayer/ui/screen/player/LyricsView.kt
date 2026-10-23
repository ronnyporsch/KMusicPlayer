package de.ronnyporsch.kmusicplayer.ui.screen.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.ronnyporsch.kmusicplayer.domain.model.Lyrics
import de.ronnyporsch.kmusicplayer.ui.components.VerticalScrollbar
import de.ronnyporsch.kmusicplayer.ui.theme.AccentColor
import de.ronnyporsch.kmusicplayer.ui.theme.SpotifyLightGray

/** Lyrics of the current song; synced lyrics follow [positionMs] and seek to a line when it's clicked. */
@Composable
fun LyricsView(
    state: LyricsState,
    positionMs: Long,
    onSeek: (positionMs: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        when (state) {
            LyricsState.Loading -> CircularProgressIndicator(color = AccentColor)
            LyricsState.NotFound -> LyricsMessage("No lyrics found")
            is LyricsState.Failed -> LyricsMessage(state.message)
            is LyricsState.Loaded -> LyricLines(state.lyrics, positionMs, onSeek)
        }
    }
}

@Composable
private fun LyricsMessage(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = SpotifyLightGray, textAlign = TextAlign.Center)
}

@Composable
private fun LyricLines(lyrics: Lyrics, positionMs: Long, onSeek: (Long) -> Unit) {
    val synced = lyrics.isSynced
    val listState = rememberLazyListState()
    // -1 before the first line starts
    val currentIndex = if (synced) lyrics.lines.indexOfLast { it.startMs!! <= positionMs } else -1

    LaunchedEffect(lyrics) { listState.scrollToItem(0) }
    LaunchedEffect(currentIndex) {
        // Don't fight the user while they scroll through the lyrics
        if (synced && !listState.isScrollInProgress) listState.animateScrollToItem(currentIndex.coerceAtLeast(0))
    }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Scrolling to a line puts it below the top padding, i.e. at a third of the height
        val contentPadding = if (synced) {
            PaddingValues(top = maxHeight / 3, bottom = maxHeight * 2 / 3)
        } else {
            PaddingValues(vertical = 16.dp)
        }
        LazyColumn(
            state = listState,
            contentPadding = contentPadding,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            itemsIndexed(lyrics.lines) { index, line ->
                val color by animateColorAsState(
                    when {
                        !synced -> MaterialTheme.colorScheme.onBackground
                        index == currentIndex -> MaterialTheme.colorScheme.onBackground
                        index < currentIndex -> SpotifyLightGray.copy(alpha = 0.6f)
                        else -> SpotifyLightGray
                    }
                )
                val startMs = line.startMs
                Text(
                    // Empty synced lines mark instrumental parts
                    text = if (synced && line.text.isBlank()) "♪" else line.text,
                    style = if (synced) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
                    fontWeight = if (index == currentIndex) FontWeight.Bold else FontWeight.Normal,
                    color = color,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .widthIn(max = 640.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .then(if (startMs != null && synced) Modifier.clickable { onSeek(startMs) } else Modifier)
                        .padding(horizontal = 8.dp, vertical = if (synced) 6.dp else 2.dp),
                )
            }
        }
        VerticalScrollbar(Modifier.align(Alignment.CenterEnd).fillMaxHeight(), listState)
    }
}
