package de.ronnyporsch.open_rgb.kmusicplayer.ui.screen.albums

import de.ronnyporsch.open_rgb.kmusicplayer.data.repository.MusicRepository
import de.ronnyporsch.open_rgb.kmusicplayer.getPlatform
import de.ronnyporsch.open_rgb.kmusicplayer.mvi.MviStore
import de.ronnyporsch.open_rgb.kmusicplayer.ui.components.toggled
import de.ronnyporsch.open_rgb.kmusicplayer.util.newestAlbumsFirst
import de.ronnyporsch.open_rgb.kmusicplayer.util.newestFirst

class ArtistAlbumsViewModel(
    private val artistId: String?,
    private val artistName: String,
    private val repository: MusicRepository,
) : MviStore<ArtistAlbumsState, ArtistAlbumsEvent, ArtistAlbumsEffect>(
    ArtistAlbumsState(artistName = artistName)
) {

    private var resolvedArtistId: String? = artistId

    init {
        if (artistId != null) {
            loadAlbums(artistId)
            loadArtistInfo(artistId)
        } else {
            resolveArtistByName()
        }
    }

    override fun onEvent(event: ArtistAlbumsEvent) {
        when (event) {
            is ArtistAlbumsEvent.SongSelectionToggled ->
                setState { copy(selectedSongIds = selectedSongIds.toggled(event.song.id)) }
            is ArtistAlbumsEvent.AlbumClicked ->
                emitEffect(ArtistAlbumsEffect.NavigateToAlbum(event.album.id))
            is ArtistAlbumsEvent.SongClicked -> {
                if (getPlatform().isMobile) {
                    emitEffect(ArtistAlbumsEffect.PlaySongs(state.value.songs, event.index))
                } else {
                    setState { copy(selectedSongIds = setOf(event.song.id)) }
                }
            }
            is ArtistAlbumsEvent.SongDoubleClicked -> {
                setState { copy(selectedSongIds = setOf(event.song.id)) }
                emitEffect(ArtistAlbumsEffect.PlaySongs(state.value.songs, event.index))
            }
            is ArtistAlbumsEvent.PlayAllSongs -> {
                val songs = state.value.songs
                if (songs.isNotEmpty()) emitEffect(ArtistAlbumsEffect.PlaySongs(songs, 0))
            }
            is ArtistAlbumsEvent.ShuffleAllSongs -> {
                val songs = state.value.songs
                if (songs.isNotEmpty()) emitEffect(ArtistAlbumsEffect.PlaySongs(songs, 0, shuffle = true))
            }
        }
    }

    private fun resolveArtistByName() {
        setState { copy(isLoading = true) }
        launch {
            repository.findArtistByName(artistName)
                .onSuccess { artist ->
                    if (artist != null) {
                        resolvedArtistId = artist.id
                        loadAlbums(artist.id)
                        loadArtistInfo(artist.id)
                    } else {
                        // Artist not in index (e.g. featured/collaborative artist) — search songs
                        setState { copy(isLoading = false) }
                        loadAppearsOnAlbums(artistName, emptyList())
                        loadSongs(emptyList())
                    }
                }
                .onFailure {
                    setState { copy(isLoading = false) }
                    loadAppearsOnAlbums(artistName, emptyList())
                    loadSongs(emptyList())
                }
        }
    }

    private fun loadAlbums(id: String) {
        setState { copy(isLoading = true) }
        launch {
            repository.getArtistAlbums(id)
                .onSuccess { albums ->
                    val dedupedAlbums = albums.distinctBy { it.id.ifEmpty { "${it.name}_${it.artist}" } }.newestFirst()
                    setState { copy(albums = dedupedAlbums, isLoading = false) }
                    loadAppearsOnAlbums(artistName, dedupedAlbums.map { it.id })
                    loadSongs(dedupedAlbums.map { it.id })
                }
                .onFailure { setState { copy(error = it.message, isLoading = false) } }
        }
    }

    private fun loadAppearsOnAlbums(artistName: String, albumIds: List<String>) {
        launch {
            repository.getAppearsOnAlbums(artistName, albumIds)
                .onSuccess { setState { copy(appearsOnAlbums = it.newestFirst()) } }
        }
    }

    private fun loadSongs(albumIds: List<String>) {
        setState { copy(isSongsLoading = true) }
        launch {
            repository.getSongsForArtist(artistName, albumIds)
                .onSuccess { songs -> setState { copy(songs = songs.newestAlbumsFirst(), isSongsLoading = false) } }
                .onFailure { setState { copy(isSongsLoading = false) } }
        }
    }

    private fun loadArtistInfo(id: String) {
        launch {
            repository.getArtistInfo(id)
                .onSuccess { info ->
                    setState {
                        copy(
                            biography = info.biography,
                            largeImageUrl = info.largeImageUrl,
                        )
                    }
                }
        }
    }
}

