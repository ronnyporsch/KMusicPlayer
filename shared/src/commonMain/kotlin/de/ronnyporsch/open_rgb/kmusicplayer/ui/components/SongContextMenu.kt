package de.ronnyporsch.open_rgb.kmusicplayer.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlaylistRemove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import de.ronnyporsch.open_rgb.kmusicplayer.data.repository.MusicRepository
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.FavoriteKind
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.Playlist
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.Song
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.isYouTube
import de.ronnyporsch.open_rgb.kmusicplayer.ui.theme.SpotifyGreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Adds songs to playlists from the song context menu (and drag and drop), on any screen. Reports the outcome to [onMessage]. */
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

    /** Adds [songs] that aren't in the playlist yet; a playlist never contains a song twice. */
    fun addToPlaylist(playlist: Playlist, songs: List<Song>) {
        scope.launch {
            // If the playlist can't be loaded, add anyway rather than not at all
            val existingIds = repository.getPlaylist(playlist.id).getOrNull()?.songs.orEmpty().map { it.id }.toSet()
            val (duplicates, new) = songs.distinctBy { it.id }.partition { it.id in existingIds }
            if (new.isEmpty()) {
                val what = duplicates.singleOrNull()?.let { "\"${it.title}\" is" } ?: "These songs are"
                onMessage("$what already in \"${playlist.name}\"")
                return@launch
            }
            repository.addSongsToPlaylist(playlist.id, new.map { it.id })
                .onSuccess {
                    val skipped = if (duplicates.isEmpty()) "" else " (${duplicates.describe()} already in it)"
                    onMessage("Added ${new.describe()} to \"${playlist.name}\"$skipped")
                }
                .onFailure { onMessage("Couldn't add to \"${playlist.name}\": ${it.message}") }
        }
    }

    fun createPlaylist(name: String, songs: List<Song>) {
        scope.launch {
            val songs = songs.distinctBy { it.id }
            repository.createPlaylist(name, songs.map { it.id })
                .onSuccess { onMessage("Created \"$name\" with ${songs.describe()}") }
                .onFailure { onMessage("Couldn't create \"$name\": ${it.message}") }
        }
    }

    private fun List<Song>.describe() = singleOrNull()?.let { "\"${it.title}\"" } ?: "$size songs"
}

val LocalPlaylistEditor = staticCompositionLocalOf<PlaylistEditor?> { null }

/** Adds songs to the player's queue, from the song context menu. */
val LocalAddToQueue = staticCompositionLocalOf<((List<Song>) -> Unit)?> { null }

fun Set<String>.toggled(songId: String): Set<String> = if (songId in this) this - songId else this + songId

/** The songs a context menu on [song] acts on: the selection if [song] is part of it, otherwise only [song]. */
fun List<Song>.contextMenuSongs(song: Song, selectedSongIds: Set<String>): List<Song> =
    if (song.id in selectedSongIds) filter { it.id in selectedSongIds }.distinctBy { it.id } else listOf(song)

/** Playlist positions a context menu on the song at [index] acts on: those of the selection if the song is part of it, otherwise only [index]. */
fun List<Song>.contextMenuIndices(index: Int, selectedSongIds: Set<String>): List<Int> =
    if (this[index].id in selectedSongIds) indices.filter { this[it].id in selectedSongIds } else listOf(index)

/** Context menu of one or more songs, opened at [offset] relative to the bottom left of the element it belongs to. */
@Composable
fun SongContextMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    songs: List<Song>,
    offset: DpOffset,
    /** Shows "Remove from playlist", for songs listed in a playlist. */
    onRemoveFromPlaylist: (() -> Unit)? = null,
) {
    val editor = LocalPlaylistEditor.current
    val addToQueue = LocalAddToQueue.current
    if (editor == null && addToQueue == null && onRemoveFromPlaylist == null) return
    // YouTube Music results aren't in the library, so they can't be added to playlists
    val canAddToPlaylist = editor != null && songs.none { it.isYouTube }
    var showPlaylists by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    // The menu forgets the songs when it closes, but the dialog outlives it
    var dialogSongs by remember { mutableStateOf(emptyList<Song>()) }
    val playlists by (editor?.playlists ?: remember { MutableStateFlow(emptyList<Playlist>()) }).collectAsState()
    val favorites = LocalFavorites.current
    val favoriteIds by (favorites?.ids ?: remember { MutableStateFlow(emptySet<String>()) }).collectAsState()

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
            if (addToQueue != null) {
                DropdownMenuItem(
                    text = { Text("Add to queue") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null) },
                    onClick = {
                        addToQueue(songs)
                        onDismiss()
                    },
                )
            }
            if (favorites != null) {
                val favoriteSongs = songs.filter { favorites.canBeFavorite(it.id) }
                if (favoriteSongs.isNotEmpty()) {
                    // Adds all unless all of them are favorites already
                    val add = favoriteSongs.any { it.id !in favoriteIds }
                    DropdownMenuItem(
                        text = { Text(if (add) "Add to favorites" else "Remove from favorites") },
                        leadingIcon = {
                            Icon(if (add) Icons.Default.FavoriteBorder else Icons.Default.Favorite, contentDescription = null)
                        },
                        onClick = {
                            favoriteSongs.forEach { favorites.set(FavoriteKind.Song, it.id, add) }
                            onDismiss()
                        },
                    )
                }
            }
            if (canAddToPlaylist) {
                DropdownMenuItem(
                    text = { Text("Add to playlist") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null) },
                    onClick = {
                        editor?.refreshPlaylists()
                        showPlaylists = true
                    },
                )
            }
            if (onRemoveFromPlaylist != null) {
                DropdownMenuItem(
                    text = { Text("Remove from playlist") },
                    leadingIcon = { Icon(Icons.Default.PlaylistRemove, contentDescription = null) },
                    onClick = {
                        onRemoveFromPlaylist()
                        onDismiss()
                    },
                )
            }
        } else if (editor != null) {
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

    if (showCreateDialog && editor != null) {
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
