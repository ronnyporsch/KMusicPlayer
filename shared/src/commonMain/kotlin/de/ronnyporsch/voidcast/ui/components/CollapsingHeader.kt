package de.ronnyporsch.voidcast.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Tracks how far the header (the first item of a lazy list/grid) has been scrolled away, so the
 * header image can fade out and its image + title can move into the top bar once the title is
 * scrolled out of view (like Spotify).
 */
@Stable
class CollapsingHeaderState internal constructor(
    private val firstVisibleItemIndex: () -> Int,
    private val firstVisibleItemScrollOffset: () -> Int,
) {
    internal var headerHeight by mutableIntStateOf(0)
    internal var titleBottom by mutableIntStateOf(0)

    /** 0 = header fully visible, 1 = header scrolled out of view. */
    val fraction: Float by derivedStateOf {
        when {
            firstVisibleItemIndex() > 0 -> 1f
            headerHeight == 0 -> 0f
            else -> (firstVisibleItemScrollOffset().toFloat() / headerHeight).coerceIn(0f, 1f)
        }
    }

    /** True once the header title has scrolled behind the top bar. */
    val isCollapsed: Boolean by derivedStateOf {
        firstVisibleItemIndex() > 0 ||
            (titleBottom > 0 && firstVisibleItemScrollOffset() >= titleBottom)
    }
}

@Composable
fun rememberCollapsingHeaderState(listState: LazyListState): CollapsingHeaderState = remember(listState) {
    CollapsingHeaderState({ listState.firstVisibleItemIndex }, { listState.firstVisibleItemScrollOffset })
}

@Composable
fun rememberCollapsingHeaderState(gridState: LazyGridState): CollapsingHeaderState = remember(gridState) {
    CollapsingHeaderState({ gridState.firstVisibleItemIndex }, { gridState.firstVisibleItemScrollOffset })
}

/** Apply to the header container (the first lazy item). */
fun Modifier.collapsingHeader(state: CollapsingHeaderState): Modifier =
    onSizeChanged { state.headerHeight = it.height }

/** Apply to the header title; it must be a direct child of the header container. */
fun Modifier.collapsingHeaderTitle(state: CollapsingHeaderState): Modifier =
    onGloballyPositioned { state.titleBottom = (it.positionInParent().y + it.size.height).toInt() }

/** Apply to the large header image to shrink and fade it while scrolling. */
fun Modifier.collapsingHeaderImage(state: CollapsingHeaderState): Modifier = graphicsLayer {
    val f = state.fraction
    val scale = 1f - 0.4f * f
    scaleX = scale
    scaleY = scale
    alpha = (1f - 1.5f * f).coerceIn(0f, 1f)
    transformOrigin = TransformOrigin(0.5f, 1f)
}

/**
 * Top bar with a back button. Once [state] is collapsed, [image] and [title] slide in next to it.
 */
@Composable
fun CollapsingTopBar(
    onBack: () -> Unit,
    state: CollapsingHeaderState,
    title: String,
    image: @Composable (Modifier) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onBack) {
            Text("←", fontSize = 20.sp, color = MaterialTheme.colorScheme.onBackground)
        }

        AnimatedVisibility(
            visible = state.isCollapsed,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
        ) {
            CollapsedTitle(title, image)
        }
    }
}

/**
 * Bar for screens without a top bar (e.g. the desktop library detail panes): place it on top of
 * the list inside a Box; it appears with [image] and [title] once [state] is collapsed.
 */
@Composable
fun CollapsedHeaderOverlay(
    state: CollapsingHeaderState,
    title: String,
    image: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
) {
    AnimatedVisibility(
        visible = state.isCollapsed,
        modifier = modifier,
        enter = fadeIn() + slideInVertically { -it / 2 },
        exit = fadeOut() + slideOutVertically { -it / 2 },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(color)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            CollapsedTitle(title, image)
        }
    }
}

@Composable
private fun CollapsedTitle(title: String, image: @Composable (Modifier) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        image(Modifier.size(36.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
