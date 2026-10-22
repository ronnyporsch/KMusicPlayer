package de.ronnyporsch.open_rgb.kmusicplayer.platform

import kotlinx.browser.document
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.w3c.dom.HTMLAudioElement
import kotlin.js.ExperimentalWasmJsInterop

@OptIn(ExperimentalWasmJsInterop::class)
class AudioPlayerWasmJs : AudioPlayer {

    private val _state = MutableStateFlow(AudioPlayerState())
    override val state: StateFlow<AudioPlayerState> = _state.asStateFlow()

    private val audio = (document.createElement("audio") as HTMLAudioElement)
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var positionJob: Job? = null

    init {
        audio.addEventListener("playing", {
            updateState(isPlaying = true)
            startPositionTracking()
        })
        audio.addEventListener("pause", {
            updateState(isPlaying = false)
            positionJob?.cancel()
        })
        audio.addEventListener("ended", {
            _state.value = AudioPlayerState()
            positionJob?.cancel()
        })
        audio.addEventListener("waiting", {
            _state.value = _state.value.copy(isBuffering = true)
        })
        audio.addEventListener("error", {
            positionJob?.cancel()
            _state.value = AudioPlayerState(error = describeMediaError(audio.error?.code?.toInt()))
        })
    }

    private fun updateState(isPlaying: Boolean) {
        _state.value = AudioPlayerState(
            isPlaying = isPlaying,
            positionMs = (audio.currentTime * 1000).toLong(),
            durationMs = if (audio.duration.isNaN()) 0L else (audio.duration * 1000).toLong(),
        )
    }

    private fun startPositionTracking() {
        positionJob?.cancel()
        positionJob = scope.launch {
            while (isActive) {
                updateState(isPlaying = true)
                delay(500)
            }
        }
    }

    private fun describeMediaError(code: Int?) = when (code) {
        2 -> "Network error while loading the audio stream"
        3 -> "The audio stream could not be decoded"
        4 -> "The audio stream is unreachable or its format is not supported"
        else -> "Could not load the audio stream"
    }

    override fun play(url: String) {
        _state.value = AudioPlayerState(isBuffering = true)
        audio.src = url
        audio.load()
        audio.play()
    }

    override fun pause() {
        audio.pause()
    }

    override fun resume() {
        audio.play()
    }

    override fun seekTo(positionMs: Long) {
        audio.currentTime = positionMs / 1000.0
        updateState(isPlaying = !audio.paused)
    }

    override fun setVolume(volume: Float) {
        audio.volume = volume.toDouble().coerceIn(0.0, 1.0)
    }

    override fun stop() {
        audio.pause()
        audio.currentTime = 0.0
        positionJob?.cancel()
        _state.value = AudioPlayerState()
    }

    override fun release() {
        stop()
        scope.cancel()
    }
}

actual fun createAudioPlayer(): AudioPlayer = AudioPlayerWasmJs()
