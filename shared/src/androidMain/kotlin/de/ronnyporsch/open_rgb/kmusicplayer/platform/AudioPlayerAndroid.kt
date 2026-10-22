package de.ronnyporsch.open_rgb.kmusicplayer.platform

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AudioPlayerAndroid : AudioPlayer {

    private val _state = MutableStateFlow(AudioPlayerState())
    override val state: StateFlow<AudioPlayerState> = _state.asStateFlow()

    private val player: ExoPlayer by lazy {
        val context = AppContextHolder.context
        // Streams over OkHttp, so a self-signed server certificate can be accepted
        val dataSourceFactory = DefaultDataSource.Factory(context, OkHttpDataSource.Factory(sharedOkHttpClient))
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build().also { exo ->
                exo.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        updateState()
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        updateState()
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        positionJob?.cancel()
                        // The cause carries the useful detail, e.g. "Cleartext HTTP traffic to ... not permitted"
                        val detail = generateSequence<Throwable>(error) { it.cause }.last().message
                        _state.value = _state.value.copy(
                            isPlaying = false,
                            isBuffering = false,
                            error = detail ?: error.errorCodeName,
                        )
                    }
                })
            }
    }

    private var positionJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private fun updateState() {
        _state.value = _state.value.copy(
            isPlaying = player.isPlaying,
            positionMs = player.currentPosition,
            durationMs = player.duration.coerceAtLeast(0),
            // Buffering while paused counts as paused, the player isn't trying to play
            isBuffering = player.playbackState == Player.STATE_BUFFERING && player.playWhenReady,
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
        _state.value = _state.value.copy(error = null)
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
