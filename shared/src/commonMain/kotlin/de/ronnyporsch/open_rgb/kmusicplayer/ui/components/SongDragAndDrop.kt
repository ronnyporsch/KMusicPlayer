package de.ronnyporsch.open_rgb.kmusicplayer.ui.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.sp
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.Song
import de.ronnyporsch.open_rgb.kmusicplayer.ui.theme.AccentColor

/**
 * Dragging songs within the app, e.g. from any song list onto a playlist in the sidebar.
 * Positions are window coordinates, so sources and targets can be anywhere in the layout.
 */
class SongDragAndDrop {
    var songs by mutableStateOf<List<Song>>(emptyList())
        private set
    var position by mutableStateOf(Offset.Zero)
        private set
    var hoveredTarget by mutableStateOf<SongDropTarget?>(null)
        private set
    val isDragging: Boolean get() = songs.isNotEmpty()

    private val targets = mutableListOf<SongDropTarget>()

    internal fun start(songs: List<Song>, position: Offset) {
        this.songs = songs
        moveTo(position)
    }

    internal fun moveTo(position: Offset) {
        this.position = position
        hoveredTarget = targets.lastOrNull { position in it.bounds }
    }

    internal fun drop() {
        val songs = songs
        val target = hoveredTarget
        cancel()
        if (target != null && songs.isNotEmpty()) target.onDrop(songs)
    }

    internal fun cancel() {
        songs = emptyList()
        hoveredTarget = null
    }

    internal fun register(target: SongDropTarget) {
        targets += target
    }

    internal fun unregister(target: SongDropTarget) {
        targets -= target
        if (hoveredTarget == target) hoveredTarget = null
    }
}

class SongDropTarget internal constructor(private val dragAndDrop: SongDragAndDrop) {
    internal var bounds = Rect.Zero
    internal var onDrop: (List<Song>) -> Unit = {}

    /** Songs are being dragged, so this target could take them. */
    val isActive: Boolean get() = dragAndDrop.isDragging
    val isHovered: Boolean get() = dragAndDrop.hoveredTarget == this
}

/** Null where songs can't be dragged (mobile, where touch drags scroll instead). */
val LocalSongDragAndDrop = staticCompositionLocalOf<SongDragAndDrop?> { null }

/** A drop target that gets [onDrop] called with the songs dropped on it, null where nothing can be dragged. */
@Composable
fun rememberSongDropTarget(onDrop: (List<Song>) -> Unit): SongDropTarget? {
    val dragAndDrop = LocalSongDragAndDrop.current ?: return null
    val target = remember(dragAndDrop) { SongDropTarget(dragAndDrop) }
    target.onDrop = onDrop
    DisposableEffect(target) {
        dragAndDrop.register(target)
        onDispose { dragAndDrop.unregister(target) }
    }
    return target
}

/** Marks the area of [target]. Clipped by scrolling containers, so hidden parts don't take drops. */
fun Modifier.songDropTarget(target: SongDropTarget?): Modifier =
    if (target == null) this else onGloballyPositioned { target.bounds = it.boundsInWindow() }

private class CoordinatesHolder {
    var coordinates: LayoutCoordinates? = null
}

/** Lets the [songs] be dragged with the mouse. Must come before click handling, so a drag doesn't also click. */
fun Modifier.songDragSource(enabled: Boolean, songs: () -> List<Song>): Modifier = composed {
    val dragAndDrop = LocalSongDragAndDrop.current
    if (!enabled || dragAndDrop == null) return@composed Modifier
    val currentSongs by rememberUpdatedState(songs)
    val holder = remember { CoordinatesHolder() }
    Modifier
        .onGloballyPositioned { holder.coordinates = it }
        .pointerInput(dragAndDrop) {
            fun toWindow(offset: Offset) = holder.coordinates?.takeIf { it.isAttached }?.localToWindow(offset) ?: offset
            detectDragGestures(
                onDragStart = { dragAndDrop.start(currentSongs(), toWindow(it)) },
                onDragEnd = { dragAndDrop.drop() },
                onDragCancel = { dragAndDrop.cancel() },
            ) { change, _ ->
                change.consume()
                dragAndDrop.moveTo(toWindow(change.position))
            }
        }
}

/** Shows what is being dragged next to the pointer. Place it on top of the whole window content. */
@Composable
fun SongDragOverlay(dragAndDrop: SongDragAndDrop, modifier: Modifier = Modifier) {
    val holder = remember { CoordinatesHolder() }
    Box(modifier.fillMaxSize().onGloballyPositioned { holder.coordinates = it }) {
        if (!dragAndDrop.isDragging) return@Box
        val songs = dragAndDrop.songs
        val target = dragAndDrop.hoveredTarget
        Surface(
            color = if (target != null) AccentColor else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (target != null) Color.White else MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(4.dp),
            shadowElevation = 6.dp,
            modifier = Modifier
                .offset {
                    val local = holder.coordinates?.takeIf { it.isAttached }?.windowToLocal(dragAndDrop.position)
                        ?: dragAndDrop.position
                    (local + Offset(16f, 16f)).round()
                }
                .widthIn(max = 280.dp),
        ) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = songs.singleOrNull()?.title ?: "${songs.size} songs",
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
