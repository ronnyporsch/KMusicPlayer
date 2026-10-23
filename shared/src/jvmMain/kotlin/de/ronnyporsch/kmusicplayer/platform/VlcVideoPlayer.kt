package de.ronnyporsch.kmusicplayer.platform

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat
import java.nio.ByteBuffer
import kotlin.math.min
import kotlin.math.roundToInt

/** libVLC of an installed VLC (3.x), which plays videos; javax.sound can only play audio. */
private object Vlc {
    /** Null if VLC isn't installed. */
    val factory: MediaPlayerFactory? by lazy {
        try {
            if (NativeDiscovery().discover()) MediaPlayerFactory("--no-video-title-show", "--quiet") else null
        } catch (e: Throwable) {
            // E.g. an incompatible VLC version
            System.err.println("VLC not usable, videos play as audio only: $e")
            null
        }
    }
}

/**
 * The player of [createAudioPlayer]: songs play through javax.sound ([AudioPlayerJvm]), videos through VLC if it's
 * installed (otherwise [supportsVideo] is false and videos get played as audio).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class JvmMediaPlayer : AudioPlayer {
    private val audio = AudioPlayerJvm()
    private val video: VlcVideoPlayer? by lazy { Vlc.factory?.let(::VlcVideoPlayer) }
    private val active = MutableStateFlow<AudioPlayer>(audio)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override val state: StateFlow<AudioPlayerState> =
        active.flatMapLatest { it.state }.stateIn(scope, SharingStarted.Eagerly, AudioPlayerState())

    init {
        // Finding and loading libVLC takes a moment, don't let the first video wait for it
        scope.launch { video }
    }

    /** The VLC player while it plays a video, else the javax.sound one; for [PlatformVideoSurface]. */
    internal val activePlayer: StateFlow<AudioPlayer> = active.asStateFlow()

    override val supportsVideo: Boolean get() = video != null

    override fun play(url: String) {
        video?.takeIf { active.value === it }?.stop()
        active.value = audio
        audio.play(url)
    }

    override fun playVideo(url: String, startPositionMs: Long) {
        val video = video ?: return super.playVideo(url, startPositionMs)
        audio.stop()
        active.value = video
        video.playVideo(url, startPositionMs)
    }

    override fun pause() = active.value.pause()
    override fun resume() = active.value.resume()
    override fun seekTo(positionMs: Long) = active.value.seekTo(positionMs)

    override fun stop() {
        audio.stop()
        video?.takeIf { active.value === it }?.stop()
    }

    override fun setVolume(volume: Float) {
        audio.setVolume(volume)
        video?.setVolume(volume)
    }

    override fun release() {
        scope.cancel()
        audio.release()
        video?.release()
    }
}

/** Plays videos (picture and sound) through libVLC; the picture is drawn by [PlatformVideoSurface]. */
class VlcVideoPlayer(factory: MediaPlayerFactory) : AudioPlayer {
    private val _state = MutableStateFlow(AudioPlayerState())
    override val state: StateFlow<AudioPlayerState> = _state.asStateFlow()

    internal val frames = VideoFrames { aspectRatio -> _state.value = _state.value.copy(videoAspectRatio = aspectRatio) }
    private val player = factory.mediaPlayers().newEmbeddedMediaPlayer()
    private val lock = Any()

    init {
        player.videoSurface().set(factory.videoSurfaces().newVideoSurface(frames, frames, true))
        // Called on a libVLC thread, which must not call back into the player
        player.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun playing(mediaPlayer: MediaPlayer) = update { copy(isPlaying = true, isBuffering = false) }
            override fun paused(mediaPlayer: MediaPlayer) = update { copy(isPlaying = false, isBuffering = false) }
            override fun buffering(mediaPlayer: MediaPlayer, newCache: Float) = update { copy(isBuffering = newCache < 100f) }
            override fun timeChanged(mediaPlayer: MediaPlayer, newTime: Long) = update { copy(positionMs = newTime) }
            override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) = update { copy(durationMs = newLength) }
            // At the end, so the end of the song is noticed like with the other players
            override fun finished(mediaPlayer: MediaPlayer) =
                update { copy(isPlaying = false, isBuffering = false, positionMs = durationMs) }
            override fun error(mediaPlayer: MediaPlayer) =
                update { AudioPlayerState(error = "VLC could not play the video") }
        })
    }

    private fun update(transform: AudioPlayerState.() -> AudioPlayerState) {
        synchronized(lock) { _state.value = _state.value.transform() }
    }

    override val supportsVideo: Boolean = true

    override fun play(url: String) = playVideo(url, 0)

    override fun playVideo(url: String, startPositionMs: Long) {
        update { AudioPlayerState(isBuffering = true, positionMs = startPositionMs) }
        val options = if (startPositionMs > 0) arrayOf(":start-time=${startPositionMs / 1000.0}") else emptyArray()
        player.media().play(url, *options)
    }

    override fun pause() = player.controls().setPause(true)

    override fun resume() {
        if (player.status().isPlayable) player.controls().setPause(false) else player.controls().play()
    }

    override fun seekTo(positionMs: Long) {
        player.controls().setTime(positionMs)
        update { copy(positionMs = positionMs) }
    }

    override fun stop() {
        player.controls().stop()
        frames.clear()
        update { AudioPlayerState() }
    }

    override fun setVolume(volume: Float) {
        player.audio().setVolume((volume.coerceIn(0f, 1f) * 100).roundToInt())
    }

    override fun release() {
        player.release()
    }
}

/** Receives the decoded frames from libVLC and keeps the latest one for drawing. */
internal class VideoFrames(private val onAspectRatio: (Float?) -> Unit) : BufferFormatCallback, RenderCallback {
    private val lock = Any()
    private var info: ImageInfo? = null
    private var bytes = ByteArray(0)
    private val bitmap = Bitmap()
    private var hasFrame = false
    /** The picture without the padding decoders add at the bottom/right of the buffer; null until known. */
    private var visibleSize: IntSize? = null

    /** Changes with every frame; reading it while drawing redraws for every frame. */
    val frameCount = mutableLongStateOf(0)

    override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat {
        synchronized(lock) {
            // RV32 is BGRX in memory
            info = ImageInfo(sourceWidth, sourceHeight, ColorType.BGRA_8888, ColorAlphaType.OPAQUE)
            bytes = ByteArray(sourceWidth * sourceHeight * 4)
            hasFrame = false
            visibleSize = null
        }
        return RV32BufferFormat(sourceWidth, sourceHeight)
    }

    override fun allocatedBuffers(buffers: Array<out ByteBuffer>) {}

    override fun display(mediaPlayer: MediaPlayer, nativeBuffers: Array<out ByteBuffer>, bufferFormat: BufferFormat) {
        val buffer = nativeBuffers.firstOrNull() ?: return
        var aspectRatio: Float? = null
        synchronized(lock) {
            val info = info ?: return
            if (visibleSize == null) {
                // The buffer size may include padding, e.g. 1280x738 for a 1280x720 video
                val dimension = mediaPlayer.video().videoDimension()
                val size = if (dimension != null && dimension.width > 0 && dimension.height > 0) {
                    IntSize(min(dimension.width, info.width), min(dimension.height, info.height))
                } else {
                    IntSize(info.width, info.height)
                }
                visibleSize = size
                aspectRatio = size.width.toFloat() / size.height
            }
            buffer.rewind()
            buffer.get(bytes, 0, min(bytes.size, buffer.remaining()))
            hasFrame = bitmap.installPixels(info, bytes, info.width * 4)
        }
        aspectRatio?.let(onAspectRatio)
        frameCount.longValue++
    }

    fun clear() {
        synchronized(lock) {
            hasFrame = false
            visibleSize = null
        }
        onAspectRatio(null)
        frameCount.longValue++
    }

    /** Draws the latest frame, scaled to fit and centered. */
    fun draw(scope: DrawScope) {
        synchronized(lock) {
            val visible = visibleSize
            if (!hasFrame || visible == null) return
            val scale = min(scope.size.width / visible.width, scope.size.height / visible.height)
            val size = Size(visible.width * scale, visible.height * scale)
            scope.drawImage(
                bitmap.asComposeImageBitmap(),
                srcSize = visible,
                dstOffset = IntOffset(((scope.size.width - size.width) / 2).roundToInt(), ((scope.size.height - size.height) / 2).roundToInt()),
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
            )
        }
    }
}

@Composable
internal actual fun PlatformVideoSurface(player: AudioPlayer, modifier: Modifier) {
    val active = (player as? JvmMediaPlayer)?.activePlayer?.collectAsState()?.value ?: return
    val frames = (active as? VlcVideoPlayer)?.frames ?: return
    Canvas(modifier) {
        frames.frameCount.longValue
        frames.draw(this)
    }
}
