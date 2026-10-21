package de.ronnyporsch.open_rgb.kmusicplayer.platform

import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.Song

/**
 * Platform support for YouTube Music tracks. When available, the platform [AudioPlayer]
 * can also play `ytmusic:<videoId>` stream URLs.
 */
interface YouTubeDownloader {
    /**
     * Browser to read YouTube cookies from (e.g. "firefox"), or null to make anonymous requests.
     * Signed-in requests can access higher quality streams (YouTube Music Premium) and avoid bot checks.
     */
    var cookiesBrowser: String?

    /**
     * Downloads [song] in its original codec as a tagged audio file into [targetDir]
     * (organized as `Album Artist/Album/NN Title.<ext>`, e.g. `.opus` or `.m4a`), tagged like MusicBrainz Picard would.
     * Returns the path of the written file.
     */
    suspend fun download(song: Song, targetDir: String): String

    /** Returns the best audio stream available for [song] (the one that gets downloaded), or null if unknown. */
    suspend fun audioStreamInfo(song: Song): AudioStreamInfo?
}

/** [codec] is a display name such as "Opus" or "AAC"; [bitRate] is in kbps. */
data class AudioStreamInfo(val codec: String?, val bitRate: Int?)

/** Returns null if YouTube Music playback/downloads are not supported on this platform. */
expect fun createYouTubeDownloader(): YouTubeDownloader?
