@file:Suppress("FunctionName")

package de.ronnyporsch.open_rgb.kmusicplayer.platform

import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.PlaybackState
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.PlaybackStatus
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.RepeatMode
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.Song
import de.ronnyporsch.open_rgb.kmusicplayer.ui.screen.player.PlayerEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.interfaces.Properties
import org.freedesktop.dbus.messages.DBusSignal
import org.freedesktop.dbus.types.Variant
import kotlin.math.abs

actual fun createSystemMediaControls(
    playback: Flow<PlaybackState>,
    onEvent: (PlayerEvent) -> Unit,
    coverArtUrl: (Song) -> String?,
): SystemMediaControls? {
    // Linux desktops route media keys to MPRIS players; elsewhere they only reach the focused window
    if (!System.getProperty("os.name").startsWith("Linux") || System.getenv("DBUS_SESSION_BUS_ADDRESS") == null) {
        return null
    }
    return MprisMediaControls(playback, onEvent, coverArtUrl)
}

private const val OBJECT_PATH = "/org/mpris/MediaPlayer2"
private const val PLAYER_INTERFACE = "org.mpris.MediaPlayer2.Player"
private const val ROOT_INTERFACE = "org.mpris.MediaPlayer2"
private const val NO_TRACK = "/org/mpris/MediaPlayer2/TrackList/NoTrack"

@DBusInterfaceName(ROOT_INTERFACE)
interface MediaPlayer2 : DBusInterface {
    fun Raise()
    fun Quit()
}

@DBusInterfaceName(PLAYER_INTERFACE)
interface MediaPlayer2Player : DBusInterface {
    fun Next()
    fun Previous()
    fun Pause()
    fun PlayPause()
    fun Stop()
    fun Play()
    fun Seek(offset: Long)
    fun SetPosition(trackId: DBusPath, position: Long)
    fun OpenUri(uri: String)

    class Seeked(path: String, val position: Long) : DBusSignal(path, position)
}

/** Implements the [MPRIS](https://specifications.freedesktop.org/mpris-spec/latest/) D-Bus interface. */
private class MprisMediaControls(
    playback: Flow<PlaybackState>,
    private val onEvent: (PlayerEvent) -> Unit,
    private val coverArtUrl: (Song) -> String?,
) : SystemMediaControls, MediaPlayer2, MediaPlayer2Player, Properties {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    @Volatile private var connection: DBusConnection? = null
    @Volatile private var playback = PlaybackState()
    private var lastPositionMs = 0L
    private var lastPositionTime = 0L
    private var emittedProperties: Map<String, Variant<*>> = emptyMap()

    init {
        scope.launch {
            try {
                val connection = DBusConnectionBuilder.forSessionBus().build()
                connection.exportObject(OBJECT_PATH, this@MprisMediaControls)
                // Another instance may already have the plain name
                try {
                    connection.requestBusName("org.mpris.MediaPlayer2.kmusicplayer")
                } catch (_: Exception) {
                    connection.requestBusName("org.mpris.MediaPlayer2.kmusicplayer.instance${ProcessHandle.current().pid()}")
                }
                this@MprisMediaControls.connection = connection
                playback.collect(::update)
            } catch (e: Exception) {
                println("MPRIS not available: ${e.message}")
            }
        }
    }

    override fun release() {
        scope.cancel()
        connection?.let {
            connection = null
            it.unExportObject(OBJECT_PATH)
            it.disconnect()
        }
    }

    @Synchronized
    private fun update(state: PlaybackState) {
        val previous = playback
        val now = System.currentTimeMillis()
        val expectedPositionMs = lastPositionMs + if (previous.status == PlaybackStatus.Playing) now - lastPositionTime else 0
        val seeked = state.currentSong != null && state.currentIndex == previous.currentIndex &&
            abs(state.positionMs - expectedPositionMs) > 2_000
        playback = state
        lastPositionMs = state.positionMs
        lastPositionTime = now

        val connection = connection ?: return
        // Position isn't announced, clients poll it; only jumps get a signal
        val properties = playerProperties() - "Position"
        val changed = properties.filter { (name, value) -> emittedProperties[name] != value }
        emittedProperties = properties
        if (changed.isNotEmpty()) {
            connection.sendMessage(Properties.PropertiesChanged(OBJECT_PATH, PLAYER_INTERFACE, changed, emptyList()))
        }
        if (seeked) connection.sendMessage(MediaPlayer2Player.Seeked(OBJECT_PATH, state.positionMs * 1000))
    }

    private fun send(event: PlayerEvent) {
        // The player view model is used from the UI thread
        CoroutineScope(Dispatchers.Main).launch { onEvent(event) }
    }

    private fun trackId(state: PlaybackState) =
        if (state.currentSong == null) NO_TRACK else "/de/ronnyporsch/kmusicplayer/track/${state.currentIndex}"

    private fun playerProperties(): Map<String, Variant<*>> {
        val state = playback
        val song = state.currentSong
        val hasNext = state.currentIndex + 1 < state.queue.size || state.repeatMode != RepeatMode.Off
        return mapOf(
            "PlaybackStatus" to Variant(
                when (state.status) {
                    PlaybackStatus.Playing, PlaybackStatus.Buffering -> "Playing"
                    PlaybackStatus.Paused -> "Paused"
                    PlaybackStatus.Idle -> if (song != null) "Paused" else "Stopped"
                }
            ),
            "LoopStatus" to Variant(
                when (state.repeatMode) {
                    RepeatMode.Off -> "None"
                    RepeatMode.One -> "Track"
                    RepeatMode.All -> "Playlist"
                }
            ),
            "Shuffle" to Variant(state.shuffle),
            "Metadata" to Variant(metadata(state), "a{sv}"),
            "Position" to Variant(state.positionMs * 1000),
            "Rate" to Variant(1.0),
            "MinimumRate" to Variant(1.0),
            "MaximumRate" to Variant(1.0),
            "Volume" to Variant(1.0),
            "CanGoNext" to Variant(song != null && hasNext),
            "CanGoPrevious" to Variant(song != null),
            "CanPlay" to Variant(song != null),
            "CanPause" to Variant(song != null),
            "CanSeek" to Variant(song != null),
            "CanControl" to Variant(true),
        )
    }

    private fun metadata(state: PlaybackState): Map<String, Variant<*>> {
        val song = state.currentSong ?: return mapOf("mpris:trackid" to Variant(DBusPath(NO_TRACK)))
        val durationMs = state.durationMs.takeIf { it > 0 } ?: (song.duration * 1000L)
        return buildMap {
            put("mpris:trackid", Variant(DBusPath(trackId(state))))
            put("mpris:length", Variant(durationMs * 1000))
            put("xesam:title", Variant(song.title))
            if (song.artist.isNotBlank()) put("xesam:artist", Variant(listOf(song.artist), "as"))
            if (song.album.isNotBlank()) put("xesam:album", Variant(song.album))
            song.track?.let { put("xesam:trackNumber", Variant(it)) }
            coverArtUrl(song)?.let { put("mpris:artUrl", Variant(it)) }
        }
    }

    private fun rootProperties(): Map<String, Variant<*>> = mapOf(
        "CanQuit" to Variant(false),
        "CanRaise" to Variant(false),
        "HasTrackList" to Variant(false),
        "Identity" to Variant("KMusicPlayer"),
        "SupportedUriSchemes" to Variant(emptyList<String>(), "as"),
        "SupportedMimeTypes" to Variant(emptyList<String>(), "as"),
    )

    // org.freedesktop.DBus.Properties

    override fun GetAll(interfaceName: String): Map<String, Variant<*>> = when (interfaceName) {
        PLAYER_INTERFACE -> playerProperties()
        ROOT_INTERFACE -> rootProperties()
        else -> emptyMap()
    }

    @Suppress("UNCHECKED_CAST")
    override fun <A : Any?> Get(interfaceName: String, propertyName: String): A =
        GetAll(interfaceName)[propertyName] as A

    override fun <A : Any?> Set(interfaceName: String, propertyName: String, value: A) {
        if (interfaceName != PLAYER_INTERFACE) return
        val state = playback
        when (propertyName) {
            "Shuffle" -> if (value != state.shuffle) send(PlayerEvent.ToggleShuffle)
            "LoopStatus" -> {
                val target = when (value) {
                    "Track" -> RepeatMode.One
                    "Playlist" -> RepeatMode.All
                    else -> RepeatMode.Off
                }
                // ToggleRepeat cycles Off -> All -> One
                val order = listOf(RepeatMode.Off, RepeatMode.All, RepeatMode.One)
                val steps = (order.indexOf(target) - order.indexOf(state.repeatMode) + order.size) % order.size
                repeat(steps) { send(PlayerEvent.ToggleRepeat) }
            }
        }
    }

    // org.mpris.MediaPlayer2

    override fun Raise() {}
    override fun Quit() {}

    // org.mpris.MediaPlayer2.Player

    override fun Next() = send(PlayerEvent.Next)
    override fun Previous() = send(PlayerEvent.Previous)
    override fun Pause() = send(PlayerEvent.Pause)
    override fun PlayPause() = send(PlayerEvent.PlayPause)
    override fun Stop() = send(PlayerEvent.Pause)
    override fun Play() = send(PlayerEvent.Play)

    override fun Seek(offset: Long) {
        val state = playback
        val positionMs = (state.positionMs + offset / 1000).coerceAtLeast(0)
        if (state.durationMs in 1..positionMs) send(PlayerEvent.Next) else send(PlayerEvent.SeekTo(positionMs))
    }

    override fun SetPosition(trackId: DBusPath, position: Long) {
        val state = playback
        if (trackId.path != trackId(state) || position < 0) return
        if (state.durationMs > 0 && position / 1000 > state.durationMs) return
        send(PlayerEvent.SeekTo(position / 1000))
    }

    override fun OpenUri(uri: String) {}

    override fun getObjectPath() = OBJECT_PATH
}
