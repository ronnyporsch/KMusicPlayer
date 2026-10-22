package de.ronnyporsch.open_rgb.kmusicplayer.ui.screen.search

import androidx.lifecycle.viewModelScope
import de.ronnyporsch.open_rgb.kmusicplayer.data.repository.MusicRepository
import de.ronnyporsch.open_rgb.kmusicplayer.data.youtube.YouTubeService
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.Song
import de.ronnyporsch.open_rgb.kmusicplayer.getPlatform
import de.ronnyporsch.open_rgb.kmusicplayer.mvi.MviStore
import de.ronnyporsch.open_rgb.kmusicplayer.ui.components.toggled
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SearchViewModel(
    private val repository: MusicRepository,
    private val youTube: YouTubeService,
) : MviStore<SearchState, SearchEvent, SearchEffect>(SearchState()) {

    private var youTubeSearchJob: Job? = null

    init {
        // Server-side YouTube support depends on the connected server
        viewModelScope.launch {
            youTube.capabilities.collect { capabilities ->
                setState {
                    copy(
                        youTubeAvailable = capabilities.available,
                        youTubeDownloadAvailable = capabilities.canDownload,
                    )
                }
            }
        }
    }

    override fun onEvent(event: SearchEvent) {
        when (event) {
            is SearchEvent.SongSelectionToggled ->
                setState { copy(selectedSongIds = selectedSongIds.toggled(event.song.id)) }
            is SearchEvent.QueryChanged -> {
                setState { copy(query = event.query) }
                if (event.query.length >= 2) {
                    performSearch(event.query)
                }
            }
            is SearchEvent.Search -> performSearch(currentState.query)
            is SearchEvent.ArtistClicked ->
                emitEffect(SearchEffect.NavigateToArtist(event.artist.id, event.artist.name))
            is SearchEvent.ArtistNameClicked ->
                emitEffect(SearchEffect.NavigateToArtist(null, event.artistName))
            is SearchEvent.AlbumClicked ->
                emitEffect(SearchEffect.NavigateToAlbum(event.album.id))
            is SearchEvent.SongClicked -> selectOrPlay(currentState.songs, event.song, event.index)
            is SearchEvent.SongDoubleClicked -> play(currentState.songs, event.song, event.index)
            is SearchEvent.YouTubeSongClicked -> selectOrPlay(currentState.youTubeSongs, event.song, event.index)
            is SearchEvent.YouTubeSongDoubleClicked -> play(currentState.youTubeSongs, event.song, event.index)
            is SearchEvent.DownloadYouTubeSong -> download(event.song)
        }
    }

    private fun selectOrPlay(songs: List<Song>, song: Song, index: Int) {
        if (getPlatform().isMobile) {
            emitEffect(SearchEffect.PlaySongs(songs, index))
        } else {
            setState { copy(selectedSongIds = setOf(song.id)) }
        }
    }

    private fun play(songs: List<Song>, song: Song, index: Int) {
        setState { copy(selectedSongIds = setOf(song.id)) }
        emitEffect(SearchEffect.PlaySongs(songs, index))
    }

    private fun performSearch(query: String) {
        if (query.isBlank()) return
        setState { copy(isLoading = true) }
        launch {
            repository.search(query)
                .onSuccess {
                    setState {
                        copy(
                            artists = it.artists,
                            albums = it.albums,
                            songs = it.songs,
                            isLoading = false,
                            hasSearched = true,
                        )
                    }
                }
                .onFailure {
                    setState { copy(isLoading = false, hasSearched = true) }
                }
        }
        if (currentState.youTubeAvailable) searchYouTube(query)
    }

    private fun searchYouTube(query: String) {
        youTubeSearchJob?.cancel()
        setState { copy(isYouTubeLoading = true) }
        youTubeSearchJob = viewModelScope.launch {
            // Debounce: the query changes on every keystroke
            delay(400)
            try {
                val songs = youTube.searchSongs(query)
                setState { copy(youTubeSongs = songs, isYouTubeLoading = false, youTubeError = null) }
                loadYouTubeStreamInfo(songs)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setState {
                    copy(
                        youTubeSongs = emptyList(),
                        isYouTubeLoading = false,
                        youTubeError = e.message ?: "YouTube Music search failed",
                    )
                }
            }
        }
    }

    /** The search API doesn't report codecs/bitrates, so look them up per song and fill them in as they arrive. */
    private suspend fun loadYouTubeStreamInfo(songs: List<Song>) {
        coroutineScope {
            for (song in songs) {
                launch {
                    val info = runCatching { youTube.audioStreamInfo(song) }
                        .onFailure { if (it is CancellationException) throw it }
                        .getOrNull() ?: return@launch
                    setState {
                        copy(
                            youTubeStreamInfo = youTubeStreamInfo + (song.id to info),
                            youTubeSongs = youTubeSongs.map { if (it.id == song.id) it.copy(bitRate = info.bitRate) else it },
                        )
                    }
                }
            }
        }
    }

    private fun download(song: Song) {
        if (!currentState.youTubeDownloadAvailable) return
        if (currentState.downloads[song.id] == DownloadStatus.InProgress) return
        setDownloadStatus(song, DownloadStatus.InProgress)
        launch {
            try {
                setDownloadStatus(song, DownloadStatus.Done(youTube.download(song)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setDownloadStatus(song, DownloadStatus.Failed(e.message ?: "Download failed"))
            }
        }
    }

    private fun setDownloadStatus(song: Song, status: DownloadStatus) {
        setState { copy(downloads = downloads + (song.id to status)) }
    }
}
