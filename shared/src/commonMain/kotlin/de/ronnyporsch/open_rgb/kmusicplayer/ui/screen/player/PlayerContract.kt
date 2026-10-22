package de.ronnyporsch.open_rgb.kmusicplayer.ui.screen.player

import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.AudioStreamInfo
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.Lyrics
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.PlaybackState
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.Song

data class PlayerState(
    val playback: PlaybackState = PlaybackState(),
    val showLyrics: Boolean = false,
    /** Lyrics of the current song. */
    val lyrics: LyricsState = LyricsState.Loading,
    /** Codec and bitrate of the current song, once known. */
    val audioFormat: AudioStreamInfo? = null,
    /** Most recent first, including the current song. */
    val recentlyPlayed: List<Song> = emptyList(),
)

sealed interface LyricsState {
    data object Loading : LyricsState
    data class Loaded(val lyrics: Lyrics) : LyricsState
    data object NotFound : LyricsState
    data class Failed(val message: String) : LyricsState
}

sealed class PlayerEvent {
    /**
     * Plays [songs] from [startIndex]. [shuffle] true shuffles all of them (ignoring [startIndex]), null keeps the
     * shuffle mode and, if it's on, plays the song at [startIndex] first.
     */
    data class PlaySongs(val songs: List<Song>, val startIndex: Int, val shuffle: Boolean? = null) : PlayerEvent()
    data object PlayPause : PlayerEvent()
    data object Play : PlayerEvent()
    data object Pause : PlayerEvent()
    data object Next : PlayerEvent()
    data object Previous : PlayerEvent()
    data object ToggleShuffle : PlayerEvent()
    data object ToggleRepeat : PlayerEvent()
    data object ToggleCrossfade : PlayerEvent()
    data object ToggleLyrics : PlayerEvent()
    data object ShowLyrics : PlayerEvent()
    /** Plays [songs] after the current song and the ones queued before. */
    data class AddToQueue(val songs: List<Song>) : PlayerEvent()
    /** Moves the song at queue index [from] to [to]. */
    data class MoveInQueue(val from: Int, val to: Int) : PlayerEvent()
    data class RemoveFromQueue(val index: Int) : PlayerEvent()
    /** Jumps to the song at this queue index. */
    data class PlayQueueIndex(val index: Int) : PlayerEvent()
    /** Plays [song] now without replacing the queue, e.g. from the recently played songs. */
    data class PlayNow(val song: Song) : PlayerEvent()
    data class SeekTo(val positionMs: Long) : PlayerEvent()
}

sealed class PlayerEffect {
    data class PlaybackError(val message: String) : PlayerEffect()
}
