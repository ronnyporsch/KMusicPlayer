package de.ronnyporsch.open_rgb.kmusicplayer.platform

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AudioPlayerAndroid : AudioPlayer {

    private val _state = MutableStateFlow(AudioPlayerState())
    override val state: StateFlow<AudioPlayerState> = _state.asStateFlow()

    private val player: ExoPlayer by lazy {
        ExoPlayer.Builder(AppContextHolder.context).build().also { exo ->
            exo.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    updateState()
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    updateState()
                }
            })
        }
    }

    private var positionJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private fun updateState() {
        _state.value = AudioPlayerState(
            isPlaying = player.isPlaying,
            positionMs = player.currentPosition,
            durationMs = player.duration.coerceAtLeast(0),
            isBuffering = player.playbackState == Player.STATE_BUFFERING,
        )
    }

    private fun startPositionTracking() {
        positionJob?.cancel()
        positionJob = scope.launch {
            while (isActive) {
                updateState()
                delay(500)
            }
        }
    }

    override fun play(url: String) {
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.play()
        startPositionTracking()
    }

    override fun pause() {
        player.pause()
        updateState()
    }

    override fun resume() {
        player.play()
        startPositionTracking()
    }

    override fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
        updateState()
    }

    override fun setVolume(volume: Float) {
        player.volume = volume.coerceIn(0f, 1f)
    }

    override fun stop() {
        player.stop()
        positionJob?.cancel()
        updateState()
    }

    override fun release() {
        positionJob?.cancel()
        scope.cancel()
        player.release()
    }
}

actual fun createAudioPlayer(): AudioPlayer = AudioPlayerAndroid()
