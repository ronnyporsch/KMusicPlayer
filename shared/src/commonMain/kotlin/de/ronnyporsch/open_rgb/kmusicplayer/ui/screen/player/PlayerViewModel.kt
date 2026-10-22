package de.ronnyporsch.open_rgb.kmusicplayer.ui.screen.player

import de.ronnyporsch.open_rgb.kmusicplayer.data.repository.MusicRepository
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.PlaybackStatus
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.RepeatMode
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.SettingsHolder
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.Song
import de.ronnyporsch.open_rgb.kmusicplayer.mvi.MviStore
import de.ronnyporsch.open_rgb.kmusicplayer.platform.AudioPlayer
import de.ronnyporsch.open_rgb.kmusicplayer.platform.CrossfadeAudioPlayer
import kotlinx.coroutines.flow.collectLatest

class PlayerViewModel(
    private val audioPlayer: AudioPlayer,
    private val repository: MusicRepository,
    private val settingsHolder: SettingsHolder,
) : MviStore<PlayerState, PlayerEvent, PlayerEffect>(PlayerState()) {

    private var hasAutoAdvanced = false
    private var hasPreparedNext = false
    private var hasCrossfadeStarted = false
    private var lastError: String? = null

    private val crossfadePlayer: CrossfadeAudioPlayer?
        get() = audioPlayer as? CrossfadeAudioPlayer

    init {
        launch {
            audioPlayer.state.collectLatest { playerState ->
                if (playerState.error != null && playerState.error != lastError) {
                    emitEffect(PlayerEffect.PlaybackError(playerState.error))
                }
                lastError = playerState.error

                setState {
                    copy(
                        playback = playback.copy(
                            status = when {
                                playerState.isBuffering -> PlaybackStatus.Buffering
                                playerState.isPlaying -> PlaybackStatus.Playing
                                playback.currentSong != null && !playerState.isPlaying -> PlaybackStatus.Paused
                                else -> PlaybackStatus.Idle
                            },
                            positionMs = playerState.positionMs,
                            durationMs = playerState.durationMs,
                        )
                    )
                }

                // Reset flags when a new track starts playing
                if (playerState.isPlaying && playerState.positionMs < 1000) {
                    hasAutoAdvanced = false
                    hasPreparedNext = false
                    hasCrossfadeStarted = false
                }

                val playback = currentState.playback
                val settings = settingsHolder.settings.value.playback
                val durationMs = playerState.durationMs
                val positionMs = playerState.positionMs
                val remainingMs = durationMs - positionMs

                if (durationMs > 0 && playback.currentSong != null && playerState.isPlaying) {
                    val nextSong = getNextSong()

                    // Pre-buffer the next track
                    if (!hasPreparedNext && nextSong != null && crossfadePlayer != null) {
                        val preBufferThreshold = if (settings.crossfadeEnabled) {
                            (settings.crossfadeDurationSeconds * 1000L) + 3000L
                        } else if (settings.gaplessEnabled) {
                            5000L
                        } else {
                            -1L // don't pre-buffer
                        }
                        if (preBufferThreshold > 0 && remainingMs <= preBufferThreshold) {
                            hasPreparedNext = true
                            streamUrl(nextSong)?.let { crossfadePlayer?.prepareNext(it) }
                        }
                    }

                    // Start crossfade
                    if (settings.crossfadeEnabled && !hasCrossfadeStarted && nextSong != null) {
                        val crossfadeDurationMs = settings.crossfadeDurationSeconds * 1000L
                        if (remainingMs <= crossfadeDurationMs) {
                            hasCrossfadeStarted = true
                            hasAutoAdvanced = true
                            crossfadePlayer?.startCrossfade(crossfadeDurationMs)
                            advanceToNextSong()
                        }
                    }
                }

                // Auto-advance when track ends (no crossfade case)
                if (!hasAutoAdvanced
                    && !playerState.isPlaying && !playerState.isBuffering
                    && durationMs > 0
                    && positionMs >= durationMs - 500
                    && playback.currentSong != null
                ) {
                    hasAutoAdvanced = true
                    if (settings.gaplessEnabled && crossfadePlayer != null && getNextSong() != null) {
                        crossfadePlayer?.playNextGapless()
                        advanceToNextSong()
                    } else {
                        playNext()
                    }
                }
            }
        }
        launch {
            settingsHolder.settings.collectLatest { settings ->
                setState {
                    copy(playback = playback.copy(crossfadeEnabled = settings.playback.crossfadeEnabled))
                }
            }
        }
    }

    override fun onEvent(event: PlayerEvent) {
        when (event) {
            is PlayerEvent.PlaySongs -> playSongs(event.songs, event.startIndex, event.shuffle)
            is PlayerEvent.PlayPause -> togglePlayPause()
            is PlayerEvent.Play -> when (currentState.playback.status) {
                PlaybackStatus.Paused -> audioPlayer.resume()
                PlaybackStatus.Idle -> playCurrent()
                PlaybackStatus.Playing, PlaybackStatus.Buffering -> {}
            }
            is PlayerEvent.Pause -> when (currentState.playback.status) {
                PlaybackStatus.Playing, PlaybackStatus.Buffering -> audioPlayer.pause()
                PlaybackStatus.Paused, PlaybackStatus.Idle -> {}
            }
            is PlayerEvent.Next -> playNext()
            is PlayerEvent.Previous -> playPrevious()
            is PlayerEvent.ToggleShuffle -> toggleShuffle()
            is PlayerEvent.ToggleRepeat -> toggleRepeat()
            is PlayerEvent.ToggleCrossfade -> toggleCrossfade()
            is PlayerEvent.SeekTo -> {
                audioPlayer.seekTo(event.positionMs)
                setState { copy(playback = playback.copy(positionMs = event.positionMs)) }
            }
        }
    }

    private fun getNextSong(): Song? {
        val state = currentState.playback
        return when {
            state.repeatMode == RepeatMode.One -> state.currentSong
            state.currentIndex + 1 < state.queue.size -> state.queue[state.currentIndex + 1]
            state.repeatMode == RepeatMode.All && state.queue.isNotEmpty() -> state.queue[0]
            else -> null
        }
    }

    private fun advanceToNextSong() {
        val state = currentState.playback
        val nextIndex = state.currentIndex + 1
        when {
            state.repeatMode == RepeatMode.One -> {
                // state stays the same
            }
            nextIndex < state.queue.size -> {
                setState {
                    copy(
                        playback = playback.copy(
                            currentIndex = nextIndex,
                            currentSong = playback.queue[nextIndex],
                        )
                    )
                }
            }
            state.repeatMode == RepeatMode.All && state.queue.isNotEmpty() -> {
                setState {
                    copy(
                        playback = playback.copy(
                            currentIndex = 0,
                            currentSong = playback.queue[0],
                        )
                    )
                }
            }
        }
    }

    private fun playSongs(songs: List<Song>, startIndex: Int, shuffle: Boolean?) {
        val targetShuffle = shuffle ?: currentState.playback.shuffle
        val queue: List<Song>
        val index: Int

        if (targetShuffle && songs.isNotEmpty()) {
            val clickedSong = songs.getOrNull(startIndex)
            if (clickedSong != null) {
                val otherSongs = songs.toMutableList()
                otherSongs.removeAt(startIndex)
                queue = listOf(clickedSong) + otherSongs.shuffled()
                index = 0
            } else {
                queue = songs.shuffled()
                index = 0
            }
        } else {
            queue = songs
            index = startIndex
        }

        hasAutoAdvanced = false
        hasPreparedNext = false
        hasCrossfadeStarted = false
        setState {
            copy(
                playback = playback.copy(
                    queue = queue,
                    currentIndex = index,
                    currentSong = queue.getOrNull(index),
                    shuffle = targetShuffle,
                    status = PlaybackStatus.Buffering,
                )
            )
        }
        playCurrent()
    }

    private fun playCurrent() {
        val song = currentState.playback.currentSong ?: return
        val url = streamUrl(song)
        if (url == null) {
            audioPlayer.stop()
            emitEffect(PlayerEffect.PlaybackError("\"${song.title}\" isn't available offline"))
            return
        }
        audioPlayer.play(url)
    }

    /** Null if the song can't be played, i.e. while not connected to the server it isn't downloaded. */
    private fun streamUrl(song: Song): String? =
        try {
            repository.getStreamUrl(song.id)
        } catch (_: IllegalStateException) {
            null
        }

    private fun togglePlayPause() {
        val status = currentState.playback.status
        when (status) {
            PlaybackStatus.Playing -> audioPlayer.pause()
            PlaybackStatus.Paused -> audioPlayer.resume()
            PlaybackStatus.Idle -> playCurrent()
            PlaybackStatus.Buffering -> {}
        }
    }

    private fun playNext() {
        val state = currentState.playback
        val nextIndex = state.currentIndex + 1
        hasPreparedNext = false
        hasCrossfadeStarted = false
        when {
            state.repeatMode == RepeatMode.One -> {
                hasAutoAdvanced = false
                playCurrent()
            }
            nextIndex < state.queue.size -> {
                hasAutoAdvanced = false
                setState {
                    copy(
                        playback = playback.copy(
                            currentIndex = nextIndex,
                            currentSong = playback.queue[nextIndex],
                        )
                    )
                }
                playCurrent()
            }
            state.repeatMode == RepeatMode.All && state.queue.isNotEmpty() -> {
                hasAutoAdvanced = false
                setState {
                    copy(
                        playback = playback.copy(
                            currentIndex = 0,
                            currentSong = playback.queue[0],
                        )
                    )
                }
                playCurrent()
            }
            else -> {
                audioPlayer.stop()
                setState {
                    copy(
                        playback = playback.copy(
                            status = PlaybackStatus.Idle,
                            currentSong = null,
                            currentIndex = -1,
                        )
                    )
                }
            }
        }
    }

    private fun playPrevious() {
        val state = currentState.playback
        if (state.positionMs > 3000) {
            audioPlayer.seekTo(0)
            return
        }
        val prevIndex = state.currentIndex - 1
        if (prevIndex >= 0) {
            hasAutoAdvanced = false
            hasPreparedNext = false
            hasCrossfadeStarted = false
            setState {
                copy(
                    playback = playback.copy(
                        currentIndex = prevIndex,
                        currentSong = playback.queue[prevIndex],
                    )
                )
            }
            playCurrent()
        }
    }

    private fun toggleShuffle() {
        setState {
            copy(playback = playback.copy(shuffle = !playback.shuffle))
        }
    }

    private fun toggleRepeat() {
        val next = when (currentState.playback.repeatMode) {
            RepeatMode.Off -> RepeatMode.All
            RepeatMode.All -> RepeatMode.One
            RepeatMode.One -> RepeatMode.Off
        }
        setState { copy(playback = playback.copy(repeatMode = next)) }
    }

    private fun toggleCrossfade() {
        settingsHolder.update {
            copy(playback = playback.copy(crossfadeEnabled = !playback.crossfadeEnabled))
        }
    }
}
