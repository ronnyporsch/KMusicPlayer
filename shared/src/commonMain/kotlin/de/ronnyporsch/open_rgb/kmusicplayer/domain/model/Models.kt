package de.ronnyporsch.open_rgb.kmusicplayer.domain.model

import kotlinx.serialization.Serializable

data class ServerConfig(
    val serverUrl: String,
    val username: String,
    val token: String,
    val salt: String,
)

@Serializable
data class Artist(
    val id: String,
    val name: String,
    val albumCount: Int = 0,
    val coverArtId: String? = null,
)

data class ArtistInfo(
    val biography: String? = null,
    val largeImageUrl: String? = null,
)

data class Album(
    val id: String,
    val name: String,
    val artist: String = "",
    val artistId: String = "",
    val artists: List<Artist> = emptyList(),
    val coverArtId: String? = null,
    val songCount: Int = 0,
    val year: Int? = null,
    val duration: Int = 0,
    val songs: List<Song> = emptyList(),
)

@Serializable
data class Song(
    val id: String,
    val title: String,
    val artist: String = "",
    val artistId: String = "",
    val artists: List<Artist> = emptyList(),
    val album: String = "",
    val albumId: String = "",
    val coverArtId: String? = null,
    val duration: Int = 0,
    val track: Int? = null,
    val discNumber: Int? = null,
    val year: Int? = null,
    val genre: String? = null,
    val bitRate: Int? = null,
)

@Serializable
data class Playlist(
    val id: String,
    val name: String,
    val songCount: Int = 0,
    val duration: Int = 0,
    val coverArtId: String? = null,
    val songs: List<Song> = emptyList(),
)

enum class PlaybackStatus {
    Idle, Playing, Paused, Buffering
}

data class PlaybackState(
    val status: PlaybackStatus = PlaybackStatus.Idle,
    val currentSong: Song? = null,
    val queue: List<Song> = emptyList(),
    val currentIndex: Int = -1,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.Off,
    val crossfadeEnabled: Boolean = false,
)

enum class RepeatMode {
    Off, All, One
}

fun Int.formatDuration(): String {
    val minutes = this / 60
    val seconds = this % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

fun Long.formatDurationMs(): String {
    val totalSeconds = (this / 1000).toInt()
    return totalSeconds.formatDuration()
}

/** Songs from YouTube Music carry this prefix in [Song.id]; they are not part of the Subsonic library. */
const val YOUTUBE_SONG_ID_PREFIX = "yt:"

val Song.isYouTube: Boolean
    get() = id.startsWith(YOUTUBE_SONG_ID_PREFIX)

val Song.youTubeVideoId: String?
    get() = if (isYouTube) id.removePrefix(YOUTUBE_SONG_ID_PREFIX) else null

/** A line of [Lyrics]; [startMs] is null for unsynced lyrics. */
data class LyricLine(
    val text: String,
    val startMs: Long? = null,
)

data class Lyrics(
    val lines: List<LyricLine>,
) {
    /** Synced lyrics have a start time for every line, so the current line can be highlighted. */
    val isSynced: Boolean get() = lines.isNotEmpty() && lines.all { it.startMs != null }
}
