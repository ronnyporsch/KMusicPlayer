package de.ronnyporsch.open_rgb.kmusicplayer.ui.screen.library

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import de.ronnyporsch.open_rgb.kmusicplayer.data.offline.PlaylistDownloads
import de.ronnyporsch.open_rgb.kmusicplayer.data.repository.MusicRepository
import de.ronnyporsch.open_rgb.kmusicplayer.getPlatform
import de.ronnyporsch.open_rgb.kmusicplayer.mvi.MviStore
import de.ronnyporsch.open_rgb.kmusicplayer.ui.components.toggled
import de.ronnyporsch.open_rgb.kmusicplayer.util.newestAlbumsFirst
import de.ronnyporsch.open_rgb.kmusicplayer.util.newestFirst
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class LibraryViewModel(
    private val repository: MusicRepository,
    /** Show the loading indicator until the first (auto-)login has connected the repository. */
    awaitingConnection: Boolean = false,
    /** Ids of playlists changed elsewhere, e.g. through the song context menu. */
    playlistChanges: Flow<String> = emptyFlow(),
    private val downloads: PlaylistDownloads? = null,
) : MviStore<LibraryState, LibraryEvent, LibraryEffect>(
    LibraryState(isLoading = awaitingConnection, canDownloadPlaylists = downloads?.isSupported == true)
) {

    val artistSidebarState = LazyListState()
    val artistDetailState = LazyGridState()
    val albumSidebarState = LazyListState()
    val albumDetailState = LazyListState()
    val playlistSidebarState = LazyListState()
    val playlistDetailState = LazyListState()

    init {
        if (repository.isConfigured) {
            loadAll()
        }
        launch {
            playlistChanges.collect { playlistId ->
                repository.getPlaylists().onSuccess { setState { copy(playlists = it) } }
                if (currentState.selectedPlaylistId == playlistId) loadPlaylist(playlistId, force = true)
            }
        }
        if (downloads != null) {
            launch { downloads.status.collect { setState { copy(playlistDownloads = it) } } }
        }
    }

    override fun onEvent(event: LibraryEvent) {
        when (event) {
            is LibraryEvent.SongSelectionToggled ->
                setState { copy(selectedSongIds = selectedSongIds.toggled(event.song.id)) }
            is LibraryEvent.SidebarTabSelected -> {
                setState { copy(selectedSidebarTab = event.tab) }
                loadTabData(event.tab)
            }
            is LibraryEvent.Refresh -> loadAll()
            is LibraryEvent.ConnectionFailed -> if (currentState.isLoading) loadOffline()
            is LibraryEvent.DownloadPlaylist -> downloads?.download(event.playlist)
            is LibraryEvent.RemovePlaylistDownload -> downloads?.remove(event.playlist.id)
            is LibraryEvent.ArtistClicked -> {
                setState {
                    copy(
                        activeDetailView = LibraryDetailView.Artists,
                        selectedArtist = event.artist,
                        selectedArtistId = event.artist.id,
                        selectedArtistAlbums = emptyList(),
                        selectedArtistAppearsOnAlbums = emptyList(),
                        selectedArtistBiography = null,
                        selectedArtistLargeImageUrl = null,
                        selectedArtistSongs = emptyList(),
                        selectedArtistSongsLoading = false,
                        selectedAlbumId = null,
                        selectedAlbum = null,
                        selectedPlaylistId = null,
                        selectedPlaylist = null,
                        selectedPlaylistLoading = false,
                    )
                }
                if (event.artist.id.isNotEmpty()) {
                    loadArtistAlbums(event.artist.id)
                    loadArtistInfo(event.artist.id)
                } else {
                    resolveArtistByName(event.artist.name)
                }
            }
            is LibraryEvent.ArtistDoubleClicked -> {
                onEvent(LibraryEvent.ArtistClicked(event.artist))
                launch {
                    val albumIds = if (event.artist.id.isEmpty()) {
                        emptyList()
                    } else {
                        repository.getArtistAlbums(event.artist.id).getOrDefault(emptyList()).map { it.id }
                    }
                    repository.getSongsForArtist(event.artist.name, albumIds).onSuccess { songs ->
                        if (songs.isNotEmpty()) {
                            emitEffect(LibraryEffect.PlaySongs(songs, 0, shuffle = true))
                        }
                    }
                }
            }
            is LibraryEvent.PlayAllArtistSongs -> {
                val songs = currentState.selectedArtistSongs
                if (songs.isNotEmpty()) emitEffect(LibraryEffect.PlaySongs(songs, 0))
            }
            is LibraryEvent.ShuffleAllArtistSongs -> {
                val songs = currentState.selectedArtistSongs
                if (songs.isNotEmpty()) emitEffect(LibraryEffect.PlaySongs(songs, 0, shuffle = true))
            }
            is LibraryEvent.ArtistSongClicked -> {
                if (getPlatform().isMobile) {
                    emitEffect(LibraryEffect.PlaySongs(currentState.selectedArtistSongs, event.index))
                } else {
                    setState { copy(selectedSongIds = setOf(event.song.id)) }
                }
            }
            is LibraryEvent.ArtistSongDoubleClicked -> {
                setState { copy(selectedSongIds = setOf(event.song.id)) }
                emitEffect(LibraryEffect.PlaySongs(currentState.selectedArtistSongs, event.index))
            }
            is LibraryEvent.AlbumClicked -> {
                setState {
                    copy(
                        activeDetailView = LibraryDetailView.Albums,
                        selectedAlbum = event.album,
                        selectedAlbumId = event.album.id,
                        selectedArtistId = null,
                        selectedArtist = null,
                        selectedArtistAlbums = emptyList(),
                        selectedArtistAppearsOnAlbums = emptyList(),
                        selectedArtistBiography = null,
                        selectedArtistLargeImageUrl = null,
                        selectedArtistSongs = emptyList(),
                        selectedArtistSongsLoading = false,
                        selectedPlaylistId = null,
                        selectedPlaylist = null,
                        selectedPlaylistLoading = false,
                    )
                }
                if (event.album.id.isNotEmpty()) {
                    loadAlbum(event.album.id)
                }
            }
            is LibraryEvent.AlbumDoubleClicked -> {
                onEvent(LibraryEvent.AlbumClicked(event.album))
                launch {
                    repository.getAlbum(event.album.id).onSuccess { fullAlbum ->
                        if (fullAlbum.songs.isNotEmpty()) {
                            emitEffect(LibraryEffect.PlaySongs(fullAlbum.songs, 0, shuffle = true))
                        }
                    }
                }
            }
            is LibraryEvent.AlbumOpened -> {
                setState {
                    copy(
                        activeDetailView = LibraryDetailView.Albums,
                        selectedAlbum = event.album,
                        selectedAlbumId = event.album.id,
                        selectedArtistId = null,
                        selectedArtist = null,
                        selectedArtistAlbums = emptyList(),
                        selectedArtistAppearsOnAlbums = emptyList(),
                        selectedArtistBiography = null,
                        selectedArtistLargeImageUrl = null,
                        selectedArtistSongs = emptyList(),
                        selectedArtistSongsLoading = false,
                        selectedPlaylistId = null,
                        selectedPlaylist = null,
                        selectedPlaylistLoading = false,
                    )
                }
                if (event.album.id.isNotEmpty()) {
                    loadAlbum(event.album.id)
                    emitEffect(LibraryEffect.NavigateToAlbum(event.album.id))
                }
            }
            is LibraryEvent.PlayAllAlbumSongs -> {
                val songs = currentState.selectedAlbum?.songs.orEmpty()
                if (songs.isNotEmpty()) emitEffect(LibraryEffect.PlaySongs(songs, 0))
            }
            is LibraryEvent.ShuffleAllAlbumSongs -> {
                val songs = currentState.selectedAlbum?.songs.orEmpty()
                if (songs.isNotEmpty()) emitEffect(LibraryEffect.PlaySongs(songs, 0, shuffle = true))
            }
            is LibraryEvent.PlaylistClicked -> {
                setState {
                    copy(
                        activeDetailView = LibraryDetailView.Playlists,
                        selectedPlaylistId = event.playlist.id,
                        selectedPlaylist = null,
                        selectedPlaylistLoading = true,
                        selectedArtistId = null,
                        selectedArtist = null,
                        selectedAlbumId = null,
                        selectedAlbum = null,
                        selectedArtistAlbums = emptyList(),
                        selectedArtistAppearsOnAlbums = emptyList(),
                        selectedArtistBiography = null,
                        selectedArtistLargeImageUrl = null,
                        selectedArtistSongs = emptyList(),
                        selectedArtistSongsLoading = false,
                    )
                }
                loadPlaylist(event.playlist.id)
            }
            is LibraryEvent.PlaylistDoubleClicked -> {
                onEvent(LibraryEvent.PlaylistClicked(event.playlist))
                launch {
                    repository.getPlaylist(event.playlist.id).onSuccess { fullPlaylist ->
                        if (fullPlaylist.songs.isNotEmpty()) {
                            emitEffect(LibraryEffect.PlaySongs(fullPlaylist.songs, 0, shuffle = true))
                        }
                    }
                }
            }
            // The playlists get reloaded through playlistChanges
            is LibraryEvent.CreatePlaylist -> launch { repository.createPlaylist(event.name) }
            is LibraryEvent.AddSongToPlaylist ->
                launch { repository.addSongsToPlaylist(event.playlist.id, listOf(event.song.id)) }
            is LibraryEvent.RemoveSongFromPlaylist ->
                launch { repository.removeSongFromPlaylist(event.playlist.id, event.index) }
            is LibraryEvent.PlayAllPlaylistSongs -> {
                val songs = currentState.selectedPlaylist?.songs.orEmpty()
                if (songs.isNotEmpty()) emitEffect(LibraryEffect.PlaySongs(songs, 0))
            }
            is LibraryEvent.ShuffleAllPlaylistSongs -> {
                val songs = currentState.selectedPlaylist?.songs.orEmpty()
                if (songs.isNotEmpty()) emitEffect(LibraryEffect.PlaySongs(songs, 0, shuffle = true))
            }
            is LibraryEvent.SongClicked -> {
                val songs = when (currentState.activeDetailView) {
                    LibraryDetailView.Albums -> currentState.selectedAlbum?.songs.orEmpty()
                    LibraryDetailView.Playlists -> currentState.selectedPlaylist?.songs.orEmpty()
                    LibraryDetailView.Artists -> currentState.selectedArtistSongs
                }
                if (songs.isNotEmpty()) {
                    if (getPlatform().isMobile) {
                        emitEffect(LibraryEffect.PlaySongs(songs, event.index))
                    } else {
                        setState { copy(selectedSongIds = setOf(event.song.id)) }
                    }
                }
            }
            is LibraryEvent.SongDoubleClicked -> {
                setState { copy(selectedSongIds = setOf(event.song.id)) }
                val songs = when (currentState.activeDetailView) {
                    LibraryDetailView.Albums -> currentState.selectedAlbum?.songs.orEmpty()
                    LibraryDetailView.Playlists -> currentState.selectedPlaylist?.songs.orEmpty()
                    LibraryDetailView.Artists -> currentState.selectedArtistSongs
                }
                if (songs.isNotEmpty()) {
                    emitEffect(LibraryEffect.PlaySongs(songs, event.index))
                }
            }
            is LibraryEvent.OpenArtistScreen ->
                emitEffect(LibraryEffect.NavigateToArtist(event.artist.id.ifEmpty { null }, event.artist.name))
            is LibraryEvent.OpenAlbumScreen ->
                emitEffect(LibraryEffect.NavigateToAlbum(event.album.id))
            is LibraryEvent.OpenPlaylistScreen ->
                emitEffect(LibraryEffect.NavigateToPlaylist(event.playlist.id))
            is LibraryEvent.SongArtistClicked -> {
                emitEffect(LibraryEffect.NavigateToArtist(null, event.artistName))
            }
            is LibraryEvent.SongAlbumClicked -> {
                emitEffect(LibraryEffect.NavigateToAlbum(event.albumId))
            }
        }
    }

    /** Shows the playlists stored on the device, as long as the server isn't reachable. */
    private fun loadOffline() {
        launch {
            val playlists = repository.getPlaylists().getOrDefault(emptyList())
            setState {
                copy(
                    isLoading = false,
                    isOffline = true,
                    playlists = playlists,
                    selectedSidebarTab = LibrarySidebarTab.Playlists,
                    activeDetailView = LibraryDetailView.Playlists,
                )
            }
            ensureDefaultSelection()
        }
    }

    private fun loadAll() {
        setState { copy(isLoading = true, isOffline = false, error = null) }
        launch {
            val artistsResult = repository.getArtists()
            val playlistsResult = repository.getPlaylists()

            val artists = artistsResult.getOrDefault(emptyList())
            val playlists = playlistsResult.getOrDefault(emptyList())

            setState {
                copy(
                    isLoading = false,
                    artists = artists,
                    playlists = playlists,
                    error = artistsResult.exceptionOrNull()?.message,
                )
            }

            ensureDefaultSelection()
            loadTabData(currentState.selectedSidebarTab)
        }
    }

    private fun loadTabData(tab: LibrarySidebarTab) {
        when (tab) {
            LibrarySidebarTab.Artists -> {
                if (currentState.artists.isEmpty()) {
                    launch {
                        repository.getArtists().onSuccess {
                            setState { copy(artists = it) }
                            ensureDefaultSelection()
                        }
                    }
                } else {
                    ensureDefaultSelection()
                }
            }
            LibrarySidebarTab.Albums -> {
                if (currentState.albums.isEmpty()) {
                    loadAlbums()
                }
            }
            LibrarySidebarTab.Playlists -> {
                if (currentState.playlists.isEmpty()) {
                    launch {
                        repository.getPlaylists().onSuccess {
                            setState { copy(playlists = it) }
                        }
                    }
                }
            }
        }
    }

    private fun loadAlbums() {
        if (currentState.albums.isNotEmpty() || currentState.albumsLoading) {
            return
        }

        setState { copy(albumsLoading = true, error = null) }
        launch {
            val artists = currentState.artists.ifEmpty {
                repository.getArtists().getOrDefault(emptyList())
            }

            val albums = coroutineScope {
                artists
                    .filter { it.id.isNotEmpty() }
                    .map { artist ->
                        async {
                            repository.getArtistAlbums(artist.id).getOrDefault(emptyList())
                        }
                    }
                    .flatMap { it.await() }
                    .distinctBy { it.id.ifEmpty { "${it.name}_${it.artist}" } }
                    .toMutableList()
            }

            setState {
                copy(
                    albums = albums.sortedBy { it.name.lowercase() },
                    albumsLoading = false,
                )
            }
        }
    }

    private fun ensureDefaultSelection() {
        when (currentState.activeDetailView) {
            LibraryDetailView.Artists -> {
                if (currentState.selectedArtist == null && currentState.artists.isNotEmpty()) {
                    val artist = currentState.artists.first()
                    setState {
                        copy(
                            selectedArtist = artist,
                            selectedArtistId = artist.id,
                            selectedArtistAlbums = emptyList(),
                            selectedArtistAppearsOnAlbums = emptyList(),
                            selectedArtistBiography = null,
                            selectedArtistLargeImageUrl = null,
                            selectedArtistSongs = emptyList(),
                            selectedAlbumId = null,
                            selectedAlbum = null,
                            selectedPlaylistId = null,
                            selectedPlaylist = null,
                            selectedPlaylistLoading = false,
                        )
                    }
                    if (artist.id.isNotEmpty()) {
                        loadArtistAlbums(artist.id)
                        loadArtistInfo(artist.id)
                    } else {
                        resolveArtistByName(artist.name)
                    }
                } else if (currentState.selectedArtist != null && currentState.selectedArtistAlbums.isEmpty() && currentState.selectedArtistId?.isNotEmpty() == true) {
                    loadArtistAlbums(currentState.selectedArtistId!!)
                    if (currentState.selectedArtistBiography == null) {
                        loadArtistInfo(currentState.selectedArtistId!!)
                    }
                }
            }
            LibraryDetailView.Albums -> {
                if (currentState.selectedAlbum == null && currentState.albums.isNotEmpty()) {
                    val album = currentState.albums.first()
                    setState {
                        copy(
                            selectedAlbum = album,
                            selectedAlbumId = album.id,
                            selectedArtistId = null,
                            selectedArtist = null,
                            selectedPlaylistId = null,
                            selectedPlaylist = null,
                            selectedPlaylistLoading = false,
                        )
                    }
                    if (album.id.isNotEmpty()) {
                        loadAlbum(album.id)
                    }
                } else if (currentState.selectedAlbumId?.isNotEmpty() == true && currentState.selectedAlbum == null) {
                    loadAlbum(currentState.selectedAlbumId!!)
                }
            }
            LibraryDetailView.Playlists -> {
                if (currentState.selectedPlaylistId == null && currentState.playlists.isNotEmpty()) {
                    val playlist = currentState.playlists.first()
                    setState { copy(selectedPlaylistId = playlist.id, selectedPlaylist = null, selectedPlaylistLoading = true) }
                    loadPlaylist(playlist.id)
                }
            }
        }
    }

    /**
     * Artists opened by name (e.g. from a song's artist credit) carry no server id. Look one up
     * by name so their albums and biography still load; artists missing from the index fall
     * back to a song search.
     */
    private fun resolveArtistByName(artistName: String) {
        if (artistName.isBlank()) return

        launch {
            val artist = repository.findArtistByName(artistName).getOrNull()
            if (currentState.selectedArtist?.name != artistName) return@launch
            if (artist != null) {
                setState { copy(selectedArtistId = artist.id) }
                loadArtistAlbums(artist.id)
                loadArtistInfo(artist.id)
            } else {
                loadAppearsOnAlbums(artistName, emptyList())
                loadArtistSongs(artistName, emptyList())
            }
        }
    }

    private fun loadArtistAlbums(artistId: String) {
        if (artistId.isBlank()) return

        launch {
            repository.getArtistAlbums(artistId)
                .onSuccess { albums ->
                    val dedupedAlbums = albums.distinctBy { it.id.ifEmpty { "${it.name}_${it.artist}" } }.newestFirst()
                    setState { copy(selectedArtistAlbums = dedupedAlbums) }
                    loadAppearsOnAlbums(
                        currentState.selectedArtist?.name.orEmpty(),
                        dedupedAlbums.map { it.id },
                    )
                    loadArtistSongs(
                        currentState.selectedArtist?.name.orEmpty(),
                        dedupedAlbums.map { it.id },
                    )
                }
        }
    }

    private fun loadAppearsOnAlbums(artistName: String, albumIds: List<String>) {
        if (artistName.isBlank()) return

        launch {
            repository.getAppearsOnAlbums(artistName, albumIds)
                .onSuccess { albums ->
                    setState { copy(selectedArtistAppearsOnAlbums = albums.newestFirst()) }
                }
        }
    }

    private fun loadArtistInfo(artistId: String) {
        if (artistId.isBlank()) return
        launch {
            repository.getArtistInfo(artistId)
                .onSuccess { info ->
                    setState {
                        copy(
                            selectedArtistBiography = info.biography,
                            selectedArtistLargeImageUrl = info.largeImageUrl,
                        )
                    }
                }
        }
    }

    private fun loadArtistSongs(artistName: String, albumIds: List<String>) {
        setState { copy(selectedArtistSongsLoading = true) }
        launch {
            repository.getSongsForArtist(artistName, albumIds)
                .onSuccess { songs -> setState { copy(selectedArtistSongs = songs.newestAlbumsFirst(), selectedArtistSongsLoading = false) } }
                .onFailure { setState { copy(selectedArtistSongsLoading = false) } }
        }
    }

    private fun loadAlbum(albumId: String) {
        if (albumId.isBlank()) {
            return
        }

        launch {
            repository.getAlbum(albumId)
                .onSuccess { album ->
                    setState { copy(selectedAlbum = album) }
                }
        }
    }

    private fun loadPlaylist(playlistId: String, force: Boolean = false) {
        if (!force && currentState.selectedPlaylistId == playlistId && currentState.selectedPlaylist != null && !currentState.selectedPlaylistLoading) {
            return
        }

        setState {
            copy(
                selectedPlaylistId = playlistId,
                selectedPlaylist = null,
                selectedPlaylistLoading = true,
                error = null,
            )
        }

        launch {
            repository.getPlaylist(playlistId)
                .onSuccess { playlist ->
                    setState { copy(selectedPlaylist = playlist, selectedPlaylistLoading = false) }
                }
                .onFailure { error ->
                    // Offline only playlists that were opened or downloaded before are available; the pane says so
                    setState {
                        copy(
                            selectedPlaylistLoading = false,
                            error = if (isOffline) null else error.message ?: "Unable to load playlist",
                        )
                    }
                }
        }
    }
}
