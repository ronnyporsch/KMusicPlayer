package de.ronnyporsch.open_rgb.kmusicplayer.data.youtube

import de.ronnyporsch.open_rgb.kmusicplayer.data.jellyfin.PluginInfoDto
import de.ronnyporsch.open_rgb.kmusicplayer.data.jellyfin.YouTubeArtistDto
import de.ronnyporsch.open_rgb.kmusicplayer.data.jellyfin.YouTubeSongDto
import de.ronnyporsch.open_rgb.kmusicplayer.data.repository.MusicServerConnection
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.Artist
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.Song
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.YOUTUBE_SONG_ID_PREFIX
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.youTubeVideoId
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class YouTubeCapabilities(
    /** Whether YouTube Music can be searched and played. */
    val available: Boolean = false,
    /** Whether tracks can be downloaded into the server's library. */
    val canDownload: Boolean = false,
)

/** [codec] is a display name such as "Opus" or "AAC"; [bitRate] is in kbps. */
data class AudioStreamInfo(val codec: String?, val bitRate: Int?)

/**
 * YouTube Music support, provided by the KMusicPlayer plugin of the connected Jellyfin server: it searches, streams
 * and downloads (into its library) on behalf of all platforms. Without the plugin, YouTube Music is unavailable.
 */
class YouTubeService(private val connection: MusicServerConnection) {

    val capabilities: Flow<YouTubeCapabilities> = connection.plugin.map(::capabilitiesFor)

    val currentCapabilities: YouTubeCapabilities get() = capabilitiesFor(connection.plugin.value)

    private fun capabilitiesFor(plugin: PluginInfoDto?): YouTubeCapabilities {
        val server = plugin?.takeIf { it.ytDlpAvailable }
        return YouTubeCapabilities(available = server != null, canDownload = server?.canDownload == true)
    }

    suspend fun searchSongs(query: String): List<Song> = connection.jellyfinApi.searchYouTube(query).map { it.toSong() }

    /** Codec and bitrate of the stream a download of [song] would get, or null if unknown. */
    suspend fun audioStreamInfo(song: Song): AudioStreamInfo? {
        val videoId = song.youTubeVideoId ?: return null
        return connection.jellyfinApi.getYouTubeStreamInfo(videoId)?.let { AudioStreamInfo(it.codec, it.bitRate) }
    }

    /** Downloads [song] into the server's library and returns a message describing where it went. */
    suspend fun download(song: Song): String {
        if (!currentCapabilities.canDownload) throw IllegalStateException("Downloads are not available")
        val jellyfin = connection.jellyfinApi
        var job = jellyfin.requestDownload(song.toDto())
        // Done once the file is in the library folder; the server scans it in the background
        while (job.state == "Queued" || job.state == "Downloading") {
            delay(1500)
            job = jellyfin.getDownload(job.id)
        }
        if (job.state == "Failed") throw IllegalStateException(job.message ?: "Download failed")
        return job.message ?: "Saved to ${job.path} on the server"
    }
}

/** YouTube thumbnail URLs encode their size (e.g. `=w60-h60-l90-rj`); request a specific one instead. */
fun resizeYouTubeThumbnail(url: String, size: Int): String =
    url.replace(Regex("""=w\d+-h\d+"""), "=w$size-h$size")

private fun YouTubeSongDto.toSong() = Song(
    id = YOUTUBE_SONG_ID_PREFIX + videoId,
    title = title,
    artist = artist,
    artists = artists.map { Artist(id = it.id, name = it.name) },
    album = album,
    albumId = albumId,
    coverArtId = thumbnailUrl,
    duration = duration,
)

private fun Song.toDto() = YouTubeSongDto(
    videoId = youTubeVideoId ?: throw IllegalArgumentException("Not a YouTube Music song: $id"),
    title = title,
    artist = artist,
    artists = artists.map { YouTubeArtistDto(id = it.id, name = it.name) },
    album = album,
    albumId = albumId,
    thumbnailUrl = coverArtId,
    duration = duration,
)
