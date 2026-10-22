package de.ronnyporsch.open_rgb.kmusicplayer.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import de.ronnyporsch.open_rgb.kmusicplayer.data.repository.MusicRepository
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.Playlist
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.Song
import de.ronnyporsch.open_rgb.kmusicplayer.ui.theme.SpotifyGreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Adds songs to playlists from the song context menu, on any screen. Reports the outcome to [onMessage]. */
class PlaylistEditor(
    private val repository: MusicRepository,
    private val scope: CoroutineScope,
    private val onMessage: suspend (String) -> Unit,
) {
    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    fun refreshPlaylists() {
        scope.launch { repository.getPlaylists().onSuccess { _playlists.value = it } }
    }

    fun addToPlaylist(playlist: Playlist, songs: List<Song>) {
        scope.launch {
            repository.addSongsToPlaylist(playlist.id, songs.map { it.id })
                .onSuccess { onMessage("Added ${songs.describe()} to \"${playlist.name}\"") }
                .onFailure { onMessage("Couldn't add to \"${playlist.name}\": ${it.message}") }
        }
    }

    fun createPlaylist(name: String, songs: List<Song>) {
        scope.launch {
            repository.createPlaylist(name, songs.map { it.id })
                .onSuccess { onMessage("Created \"$name\" with ${songs.describe()}") }
                .onFailure { onMessage("Couldn't create \"$name\": ${it.message}") }
        }
    }

    private fun List<Song>.describe() = singleOrNull()?.let { "\"${it.title}\"" } ?: "$size songs"
}

val LocalPlaylistEditor = staticCompositionLocalOf<PlaylistEditor?> { null }

fun Set<String>.toggled(songId: String): Set<String> = if (songId in this) this - songId else this + songId

/** The songs a context menu on [song] acts on: the selection if [song] is part of it, otherwise only [song]. */
fun List<Song>.contextMenuSongs(song: Song, selectedSongIds: Set<String>): List<Song> =
    if (song.id in selectedSongIds) filter { it.id in selectedSongIds }.distinctBy { it.id } else listOf(song)

/** Context menu of one or more songs, opened at [offset] relative to the bottom left of the element it belongs to. */
@Composable
fun SongContextMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    songs: List<Song>,
    offset: DpOffset,
) {
    val editor = LocalPlaylistEditor.current ?: return
    var showPlaylists by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    // The menu forgets the songs when it closes, but the dialog outlives it
    var dialogSongs by remember { mutableStateOf(emptyList<Song>()) }
    val playlists by editor.playlists.collectAsState()

    LaunchedEffect(expanded) {
        if (!expanded) showPlaylists = false
    }

    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss, offset = offset) {
        if (songs.size > 1) {
            Text(
                "${songs.size} songs selected",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
            HorizontalDivider()
        }
        if (!showPlaylists) {
            DropdownMenuItem(
                text = { Text("Add to playlist") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null) },
                onClick = {
                    editor.refreshPlaylists()
                    showPlaylists = true
                },
            )
        } else {
            DropdownMenuItem(
                text = { Text("Back") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) },
                onClick = { showPlaylists = false },
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("New playlist…", color = SpotifyGreen) },
                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, tint = SpotifyGreen) },
                onClick = {
                    dialogSongs = songs
                    showCreateDialog = true
                    onDismiss()
                },
            )
            playlists.forEach { playlist ->
                DropdownMenuItem(
                    text = { Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = {
                        editor.addToPlaylist(playlist, songs)
                        onDismiss()
                    },
                )
            }
        }
    }

    if (showCreateDialog) {
        var name by remember { mutableStateOf("") }
        val create = {
            if (name.isNotBlank()) {
                editor.createPlaylist(name.trim(), dialogSongs)
                showCreateDialog = false
            }
        }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create New Playlist") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Playlist Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(onClick = create, colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen)) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
            },
        )
    }
}
