package de.ronnyporsch.open_rgb.kmusicplayer.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.AudioStreamInfo
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.FavoriteKind
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.Song
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.formatDuration
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.isYouTube
import de.ronnyporsch.open_rgb.kmusicplayer.getPlatform
import de.ronnyporsch.open_rgb.kmusicplayer.ui.LocalCoverArtUrlProvider
import de.ronnyporsch.open_rgb.kmusicplayer.ui.theme.SpotifyGreen

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongRow(
    song: Song,
    onSongClick: () -> Unit,
    /** Makes the artist names links; null shows them as plain text. */
    onArtistClick: ((String) -> Unit)?,
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
    removeDescription: String = "Remove from playlist",
    trailingContent: (@Composable () -> Unit)? = null,
    /** Codec and bitrate, shown left of the duration. */
    audioFormat: AudioStreamInfo? = null,
    /** Shows a heart to mark the song as favorite (library songs only). */
    showFavorite: Boolean = false,
    /** Click while Ctrl (Cmd on macOS) is held, to add the song to or remove it from the selection. Desktop and web only. */
    onSongCtrlClick: (() -> Unit)? = null,
    /** The songs the context menu acts on, see [contextMenuSongs]. */
    contextMenuSongs: () -> List<Song> = { listOf(song) },
    /** Adds "Remove from playlist" to the context menu; removes the songs the menu acts on. */
    onRemoveFromPlaylist: (() -> Unit)? = null,
) {
    val coverArt = LocalCoverArtUrlProvider.current
    val platform = getPlatform()
    val density = LocalDensity.current

    val hasContextMenu = LocalPlaylistEditor.current != null || LocalAddToQueue.current != null || onRemoveFromPlaylist != null
    // YouTube Music results aren't in the library, so they can't be dropped onto playlists
    val isDraggable = LocalPlaylistEditor.current != null && !song.isYouTube
    var menuSongs by remember { mutableStateOf<List<Song>?>(null) }
    var pressPosition by remember { mutableStateOf(Offset.Zero) }
    var ctrlPressed by remember { mutableStateOf(false) }
    var rowHeight by remember { mutableStateOf(0) }
    val currentContextMenuSongs by rememberUpdatedState(contextMenuSongs)

    val openMenu = { if (hasContextMenu) menuSongs = currentContextMenuSongs() }
    val background = if (isSelected || menuSongs != null) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent

    val rowModifier = if (platform.isMobile) {
        modifier
            .fillMaxWidth()
            .background(background)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Press) pressPosition = event.changes.first().position
                    }
                }
            }
            .combinedClickable(onClick = onSongClick, onLongClick = openMenu)
    } else {
        modifier
            .fillMaxWidth()
            .background(background)
            // Library songs can be dragged onto playlists; with a selection the drag takes all selected songs
            .songDragSource(enabled = isDraggable) { currentContextMenuSongs() }
            .pointerInput(hasContextMenu) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type != PointerEventType.Press) continue
                        pressPosition = event.changes.first().position
                        ctrlPressed = event.keyboardModifiers.isCtrlPressed || event.keyboardModifiers.isMetaPressed
                        if (event.buttons.isSecondaryPressed && hasContextMenu) {
                            event.changes.forEach { it.consume() }
                            openMenu()
                        }
                    }
                }
            }
            .combinedClickable(
                onClick = {
                    val ctrlClick = onSongCtrlClick
                    if (ctrlPressed && ctrlClick != null) ctrlClick() else onSongClick()
                },
                onDoubleClick = onSongDoubleClick,
            )
    }

    Box {
        Row(
            modifier = rowModifier.onSizeChanged { rowHeight = it.height }.padding(contentPadding),
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
                        if (onArtistClick != null) {
                            ClickableArtistText(
                                artists = song.artists,
                                fallback = song.artist.ifBlank { "Unknown artist" },
                                onArtistClick = onArtistClick,
                            )
                        } else {
                            Text(
                                text = song.artists.joinToString(", ") { it.name }.ifBlank { song.artist.ifBlank { "Unknown artist" } },
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
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

            if (showFavorite) {
                FavoriteButton(FavoriteKind.Song, song.id, Modifier.size(32.dp).padding(end = 4.dp), iconSize = 18.dp)
            }

            AudioFormatText(audioFormat, Modifier.padding(end = 12.dp))

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
                    contentDescription = removeDescription,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(24.dp)
                )
            }

            if (trailingContent != null) {
                Spacer(Modifier.width(12.dp))
                trailingContent()
            }
        }

        // The menu is placed below its anchor, so move it up to where the row got clicked
        SongContextMenu(
            expanded = menuSongs != null,
            onDismiss = { menuSongs = null },
            songs = menuSongs.orEmpty(),
            offset = with(density) { DpOffset(pressPosition.x.toDp(), (pressPosition.y - rowHeight).toDp()) },
            onRemoveFromPlaylist = onRemoveFromPlaylist,
        )
    }
}
