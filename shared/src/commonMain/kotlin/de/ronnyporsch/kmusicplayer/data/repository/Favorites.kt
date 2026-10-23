package de.ronnyporsch.kmusicplayer.data.repository

import de.ronnyporsch.kmusicplayer.domain.model.FavoriteKind
import de.ronnyporsch.kmusicplayer.domain.model.isYouTubeId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The user's favorite songs, albums and artists, shared by all screens so a change shows everywhere at once.
 * Toggling updates [ids] right away and reverts it if the server refuses; [items] follow once the server confirmed.
 */
class Favorites(private val repository: MusicRepository) {
    // One thread, so the change bookkeeping below needs no locking
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))

    private val _ids = MutableStateFlow<Set<String>>(emptySet())
    val ids: StateFlow<Set<String>> = _ids.asStateFlow()

    private val _items = MutableStateFlow(SearchResults())
    /** The favorites as last loaded; may still contain items that [ids] no longer has. */
    val items: StateFlow<SearchResults> = _items.asStateFlow()

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** User-facing messages of failed changes. */
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    /** Changes sent to the server but not answered yet. */
    private var pendingChanges = 0
    /** Counts changes, so a load that overlapped one doesn't overwrite it with outdated favorites. */
    private var changeCount = 0
    private var loadJob: Job? = null

    /** Loads the favorites of the connected server, e.g. after connecting. */
    suspend fun reload() {
        scope.launch {
            // Another server's favorites must not show meanwhile
            _ids.value = emptySet()
            _items.value = SearchResults()
            load()
        }.join()
    }

    /** Only library items can be favorites, YouTube Music ones can't. */
    fun canBeFavorite(id: String): Boolean = id.isNotBlank() && !id.isYouTubeId

    fun toggle(kind: FavoriteKind, id: String) = set(kind, id, id !in _ids.value)

    fun set(kind: FavoriteKind, id: String, favorite: Boolean) {
        if (!canBeFavorite(id)) return
        _ids.update { if (favorite) it + id else it - id }
        scope.launch {
            pendingChanges++
            changeCount++
            val result = repository.setFavorite(kind, id, favorite)
            pendingChanges--
            result.onFailure { error ->
                _ids.update { if (favorite) it - id else it + id }
                val action = if (favorite) "add to" else "remove from"
                _errors.emit("Couldn't $action favorites: ${error.message}")
            }
            // Reload the items once all changes are through, for the new ones
            if (pendingChanges == 0) {
                loadJob?.cancel()
                loadJob = launch { load() }
            }
        }
    }

    private suspend fun load() {
        val changesBefore = changeCount
        val loaded = repository.getFavorites().getOrNull() ?: return
        if (changeCount != changesBefore || pendingChanges > 0) return
        _items.value = loaded
        _ids.value = buildSet {
            loaded.artists.mapTo(this) { it.id }
            loaded.albums.mapTo(this) { it.id }
            loaded.songs.mapTo(this) { it.id }
        }
    }
}
