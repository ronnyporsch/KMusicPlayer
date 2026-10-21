package de.ronnyporsch.open_rgb.kmusicplayer.platform

import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.YOUTUBE_STREAM_SCHEME
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URI
import javax.sound.sampled.*

class AudioPlayerJvm : AudioPlayer {

    private val _state = MutableStateFlow(AudioPlayerState())
    override val state: StateFlow<AudioPlayerState> = _state.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var playbackJob: Job? = null
    private var currentLine: SourceDataLine? = null
    private var pcmData: ByteArray? = null
    private var decodedFormat: AudioFormat? = null
    @Volatile private var isPaused = false
    @Volatile private var stopRequested = false
    @Volatile private var playbackPositionBytes: Long = 0
    @Volatile private var totalDurationMs: Long = 0
    @Volatile private var volumeScale: Float = 1.0f

    override fun play(url: String) {
        stop()
        stopRequested = false
        isPaused = false

        playbackJob = scope.launch {
            try {
                _state.value = AudioPlayerState(isBuffering = true)

                // Download entire file first — ByteArrayInputStream supports
                // unlimited mark/reset, which SPI probing (FLAC, MP3) requires
                val rawBytes = if (url.startsWith(YOUTUBE_STREAM_SCHEME)) {
                    YtDlp.fetchPlayableAudio(url.removePrefix(YOUTUBE_STREAM_SCHEME)).readBytes()
                } else {
                    URI(url).toURL().openStream().use { it.readBytes() }
                }
                if (stopRequested) return@launch
                val audioStream = AudioSystem.getAudioInputStream(ByteArrayInputStream(rawBytes))
                val baseFormat = audioStream.format

                val sampleRate = if (baseFormat.sampleRate > 0) baseFormat.sampleRate else 44100f
                val channels = if (baseFormat.channels > 0) baseFormat.channels else 2

                val targetFormat = AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    sampleRate,
                    16,
                    channels,
                    channels * 2,
                    sampleRate,
                    false,
                )
                val decodedStream = AudioSystem.getAudioInputStream(targetFormat, audioStream)

                // Read all decoded PCM data into memory for seeking support
                val buffer = ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                var bytesRead: Int
                while (decodedStream.read(chunk).also { bytesRead = it } != -1) {
                    buffer.write(chunk, 0, bytesRead)
                    if (stopRequested) return@launch
                }
                decodedStream.close()
                audioStream.close()

                val data = buffer.toByteArray()
                pcmData = data
                decodedFormat = targetFormat

                val bytesPerSecond = targetFormat.sampleRate * targetFormat.frameSize
                totalDurationMs = ((data.size.toDouble() / bytesPerSecond) * 1000).toLong()

                if (stopRequested) return@launch

                // Open audio line and start streaming
                val line = getSourceDataLine(targetFormat)
                line.open(targetFormat)
                line.start()
                currentLine = line

                playbackPositionBytes = 0
                val writeChunk = ByteArray(4096)

                _state.value = AudioPlayerState(
                    isPlaying = true,
                    positionMs = 0,
                    durationMs = totalDurationMs,
                )

                while (playbackPositionBytes < data.size && !stopRequested) {
                    if (isPaused) {
                        delay(50)
                        continue
                    }

                    val offset = playbackPositionBytes.toInt()
                    val remaining = data.size - offset
                    val toWrite = minOf(writeChunk.size, remaining)
                    System.arraycopy(data, offset, writeChunk, 0, toWrite)

                    // Apply volume scaling to PCM samples
                    val vol = volumeScale
                    if (vol < 1.0f) {
                        var i = 0
                        while (i + 1 < toWrite) {
                            val sample = ((writeChunk[i + 1].toInt() shl 8) or (writeChunk[i].toInt() and 0xFF)).toShort()
                            val scaled = (sample * vol).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                            writeChunk[i] = (scaled and 0xFF).toByte()
                            writeChunk[i + 1] = ((scaled shr 8) and 0xFF).toByte()
                            i += 2
                        }
                    }

                    val written = line.write(writeChunk, 0, toWrite)
                    playbackPositionBytes += written

                    val currentMs = ((playbackPositionBytes.toDouble() / bytesPerSecond) * 1000).toLong()
                    _state.value = AudioPlayerState(
                        isPlaying = true,
                        positionMs = currentMs,
                        durationMs = totalDurationMs,
                    )
                }

                // Wait for line to finish draining
                if (!stopRequested) {
                    line.drain()
                }

                line.stop()
                line.close()
                currentLine = null

                if (!stopRequested) {
                    // Playback finished naturally
                    _state.value = AudioPlayerState(
                        isPlaying = false,
                        positionMs = totalDurationMs,
                        durationMs = totalDurationMs,
                    )
                }

            } catch (e: Exception) {
                if (!stopRequested) {
                    e.printStackTrace()
                    _state.value = AudioPlayerState()
                }
            }
        }
    }

    override fun pause() {
        isPaused = true
        currentLine?.stop()
        val bytesPerSecond = decodedFormat?.let { it.sampleRate * it.frameSize } ?: return
        val currentMs = ((playbackPositionBytes.toDouble() / bytesPerSecond) * 1000).toLong()
        _state.value = AudioPlayerState(
            isPlaying = false,
            positionMs = currentMs,
            durationMs = totalDurationMs,
        )
    }

    override fun resume() {
        currentLine?.start()
        isPaused = false
        _state.value = _state.value.copy(isPlaying = true)
    }

    override fun seekTo(positionMs: Long) {
        val format = decodedFormat ?: return
        val data = pcmData ?: return
        val bytesPerSecond = format.sampleRate * format.frameSize
        val frameSize = format.frameSize
        var targetBytes = ((positionMs / 1000.0) * bytesPerSecond).toLong()
        // Align to frame boundary
        targetBytes = (targetBytes / frameSize) * frameSize
        targetBytes = targetBytes.coerceIn(0, data.size.toLong())
        playbackPositionBytes = targetBytes

        // Flush the line buffer to avoid hearing old audio
        currentLine?.flush()

        _state.value = _state.value.copy(positionMs = positionMs)
    }

    override fun setVolume(volume: Float) {
        volumeScale = volume.coerceIn(0f, 1f)
    }

    override fun stop() {
        stopRequested = true
        isPaused = false
        playbackJob?.cancel()
        currentLine?.let {
            it.stop()
            it.flush()
            it.close()
        }
        currentLine = null
        pcmData = null
        decodedFormat = null
        _state.value = AudioPlayerState()
    }

    override fun release() {
        stop()
        scope.cancel()
    }

    private fun getSourceDataLine(format: AudioFormat): SourceDataLine {
        val info = DataLine.Info(SourceDataLine::class.java, format)

        // 1. Try system default mixer first (AudioSystem.getMixer(null))
        try {
            val defaultMixer = AudioSystem.getMixer(null)
            if (defaultMixer != null && defaultMixer.isLineSupported(info)) {
                return defaultMixer.getLine(info) as SourceDataLine
            }
        } catch (_: Exception) {}

        // 2. Try mixers with "default", "pulse", or "pipewire" in their name (to avoid hardware loopback devices)
        val mixers = AudioSystem.getMixerInfo()
        for (mixerInfo in mixers) {
            val name = mixerInfo.name.lowercase()
            if (name.contains("default") || name.contains("pulse") || name.contains("pipewire")) {
                try {
                    val mixer = AudioSystem.getMixer(mixerInfo)
                    if (mixer.isLineSupported(info)) {
                        return mixer.getLine(info) as SourceDataLine
                    }
                } catch (_: Exception) {}
            }
        }

        // 3. Try any mixer that is NOT a loopback device
        for (mixerInfo in mixers) {
            val name = mixerInfo.name.lowercase()
            if (!name.contains("loopback")) {
                try {
                    val mixer = AudioSystem.getMixer(mixerInfo)
                    if (mixer.isLineSupported(info)) {
                        return mixer.getLine(info) as SourceDataLine
                    }
                } catch (_: Exception) {}
            }
        }

        // 4. Fallback to standard AudioSystem.getLine
        return AudioSystem.getLine(info) as SourceDataLine
    }
}

actual fun createAudioPlayer(): AudioPlayer = AudioPlayerJvm()
