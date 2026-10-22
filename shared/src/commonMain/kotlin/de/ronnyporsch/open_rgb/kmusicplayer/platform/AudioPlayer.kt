package de.ronnyporsch.open_rgb.kmusicplayer.platform

import kotlinx.coroutines.flow.StateFlow

data class AudioPlayerState(
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isBuffering: Boolean = false,
    /** Why the last [AudioPlayer.play] failed, cleared by the next one. */
    val error: String? = null,
)

interface AudioPlayer {
    val state: StateFlow<AudioPlayerState>
    fun play(url: String)
    fun pause()
    fun resume()
    fun seekTo(positionMs: Long)
    fun stop()
    fun setVolume(volume: Float)
    fun release()
}

expect fun createAudioPlayer(): AudioPlayer
