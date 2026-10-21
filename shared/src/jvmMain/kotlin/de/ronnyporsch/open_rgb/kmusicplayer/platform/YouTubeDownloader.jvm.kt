package de.ronnyporsch.open_rgb.kmusicplayer.platform

import de.ronnyporsch.open_rgb.kmusicplayer.data.youtube.YouTubeAlbum
import de.ronnyporsch.open_rgb.kmusicplayer.data.youtube.YouTubeMusicApi
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.Song
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.youTubeVideoId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.ByteBuffer
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO
import java.util.concurrent.Semaphore
import kotlin.math.roundToInt

/** Wraps the `yt-dlp` and `ffmpeg` command line tools, which must be on the PATH. */
internal object YtDlp {

    val isAvailable: Boolean by lazy { isOnPath("yt-dlp") && isOnPath("ffmpeg") }

    private val cacheDir = File(System.getProperty("java.io.tmpdir"), "kmusicplayer-yt").apply {
        deleteRecursively()
        mkdirs()
    }
    private val cacheLocks = ConcurrentHashMap<String, Any>()
    private val videoIdRegex = Regex("[A-Za-z0-9_-]+")
    private val streamInfoCache = ConcurrentHashMap<String, AudioStreamInfo>()

    /** See [YouTubeDownloader.cookiesBrowser]. */
    @Volatile
    var cookiesBrowser: String? = null
        set(value) {
            if (value == field) return
            field = value
            // Signed in, other (e.g. Premium) streams may be available
            streamInfoCache.clear()
        }
    // Each lookup spawns a yt-dlp process; don't start one per search result at once
    private val streamInfoLookups = Semaphore(4)

    /**
     * Picks the best codec/bitrate combination: YouTube's quality tier first (so e.g. a 256 kbps AAC
     * stream beats 128 kbps Opus), then channels, then the more efficient codec (Opus > Vorbis > AAC > MP3),
     * then bitrate.
     */
    private val BEST_AUDIO = arrayOf("-f", "bestaudio", "-S", "quality,channels,acodec,abr")

    /** Returns codec and bitrate of the audio stream [BEST_AUDIO] selects for [videoId], or null if unknown. */
    fun audioStreamInfo(videoId: String): AudioStreamInfo? {
        requireValidId(videoId)
        streamInfoCache[videoId]?.let { return it }
        streamInfoLookups.acquire()
        try {
            val output = ytDlp(
                "--no-playlist", "--quiet", "--no-warnings", "--skip-download",
                *BEST_AUDIO, "--print", "%(acodec)s %(abr)s",
                watchUrl(videoId),
            )
            val (acodec, abr) = output.lines().firstOrNull { it.isNotBlank() }?.trim()?.split(" ")
                ?.takeIf { it.size == 2 } ?: return null
            val info = AudioStreamInfo(codec = codecName(acodec), bitRate = abr.toDoubleOrNull()?.roundToInt())
            if (info.codec == null && info.bitRate == null) return null
            streamInfoCache[videoId] = info
            return info
        } finally {
            streamInfoLookups.release()
        }
    }

    /** Maps yt-dlp codec ids (e.g. `mp4a.40.2`, `opus`) to display names. */
    private fun codecName(acodec: String): String? = when {
        acodec == "none" || acodec == "NA" -> null
        acodec.startsWith("mp4a") -> "AAC"
        acodec == "opus" -> "Opus"
        acodec == "vorbis" -> "Vorbis"
        acodec.startsWith("mp3") -> "MP3"
        acodec.startsWith("ac-3") -> "AC-3"
        acodec.startsWith("ec-3") -> "E-AC-3"
        else -> acodec.uppercase()
    }

    /**
     * Downloads the audio of [videoId] as WAV (decodable by javax.sound without extra SPIs)
     * into a temp cache and returns the file. Blocks until done.
     */
    fun fetchPlayableAudio(videoId: String): File {
        requireValidId(videoId)
        return synchronized(cacheLocks.computeIfAbsent(videoId) { Any() }) {
            val file = File(cacheDir, "$videoId.wav")
            if (!file.exists()) {
                ytDlp(
                    "--no-playlist", "--quiet", "--no-warnings",
                    *BEST_AUDIO, "-x", "--audio-format", "wav",
                    "--postprocessor-args", "ExtractAudio:-ac 2 -sample_fmt s16",
                    "-o", File(cacheDir, "$videoId.%(ext)s").path,
                    watchUrl(videoId),
                )
                if (!file.exists()) throw IOException("yt-dlp did not produce $file")
                file.deleteOnExit()
            }
            file
        }
    }

    /**
     * Downloads [song] in its original codec (no re-encoding) as a file tagged with [tags] into
     * [targetDir]/Album Artist/Album/NN Title.<ext> (Picard's default naming) and returns the file.
     */
    fun download(song: Song, tags: TrackTags, targetDir: File): File {
        val videoId = song.youTubeVideoId ?: throw IllegalArgumentException("Not a YouTube Music song: ${song.id}")
        requireValidId(videoId)
        val workDir = Files.createTempDirectory("kmusicplayer-dl").toFile()
        try {
            // -x without --audio-format only remuxes the stream into an audio container (e.g. webm -> opus)
            ytDlp(
                "--no-playlist", "--quiet", "--no-warnings",
                *BEST_AUDIO, "-x",
                "-o", File(workDir, "audio.%(ext)s").path,
                watchUrl(videoId),
            )
            val audio = workDir.listFiles { file -> file.name.startsWith("audio.") }?.singleOrNull()
                ?: throw IOException("yt-dlp did not produce an audio file")
            val ext = audio.extension.lowercase()

            // The YouTube Music search thumbnail is the square album cover; the video thumbnail often isn't
            val cover = song.coverArtId?.let { url ->
                runCatching {
                    File(workDir, "cover.jpg").also { file ->
                        URI(YouTubeMusicApi.resizeThumbnail(url, 544)).toURL().openStream().use { input ->
                            file.outputStream().use { input.copyTo(it) }
                        }
                    }
                }.getOrNull()
            }

            val tagged = File(workDir, "tagged.$ext")
            when (ext) {
                // Ogg containers (Opus, Vorbis) can't hold a picture stream; the cover goes into a Vorbis comment
                "opus", "ogg", "oga" -> {
                    Files.copy(audio.toPath(), tagged.toPath())
                    val comments = tags.vorbisComments() +
                        listOfNotNull(cover?.let { "METADATA_BLOCK_PICTURE" to flacPictureBlock(it) })
                    AudioTags.writeOggComments(tagged, comments)
                }
                else -> {
                    tagWithFfmpeg(audio, tagged, tags, cover)
                    if (ext in setOf("m4a", "mp4", "m4b")) AudioTags.addMp4FreeformTags(tagged, tags.mp4FreeformTags())
                }
            }

            val pathParts = listOfNotNull(
                tags.albumArtist ?: tags.artist.ifBlank { "Unknown Artist" },
                tags.album,
            ).map(::sanitizeFileName)
            val dir = pathParts.fold(targetDir) { parent, part -> File(parent, part) }
            if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Could not create directory $dir")
            val trackPrefix = tags.trackNumber?.takeIf { tags.albumArtist != null }?.let { it.toString().padStart(2, '0') + " " }
            val target = File(dir, sanitizeFileName(trackPrefix.orEmpty() + tags.title) + ".$ext")
            // Copy under a non-audio extension first so the server never scans a half-written file
            val partial = File(dir, target.name + ".part")
            Files.copy(tagged.toPath(), partial.toPath(), StandardCopyOption.REPLACE_EXISTING)
            Files.move(partial.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            return target
        } finally {
            workDir.deleteRecursively()
        }
    }

    /** Writes the single-valued [tags] and [cover] with ffmpeg (multi-valued tags are joined with "; "). */
    private fun tagWithFfmpeg(audio: File, output: File, tags: TrackTags, cover: File?) {
        val metadataTags = buildMap {
            put("title", tags.title)
            if (tags.artist.isNotBlank()) put("artist", tags.artist)
            tags.album?.let { put("album", it) }
            tags.albumArtist?.let { put("album_artist", it) }
            tags.trackNumber?.let { put("track", listOfNotNull(it, tags.totalTracks).joinToString("/")) }
            tags.year?.let { put("date", it.toString()) }
            put("comment", tags.comment)
            if (output.extension.lowercase() !in setOf("m4a", "mp4", "m4b")) {
                // ffmpeg writes these as e.g. ID3 TXXX frames; MP4 gets proper freeform atoms instead
                if (tags.artists.isNotEmpty()) put("ARTISTS", tags.artists.joinToString("; "))
                if (tags.albumArtists.isNotEmpty()) put("ALBUMARTISTS", tags.albumArtists.joinToString("; "))
                tags.releaseType?.let { put("RELEASETYPE", it) }
                tags.media?.let { put("MEDIA", it) }
            }
        }
        // Passed as a file: values must not be split into command line arguments
        val metadata = File(output.parentFile, "metadata.txt").apply {
            writeText(";FFMETADATA1\n" + metadataTags.entries.joinToString("") { (k, v) -> "$k=${escapeFfMetadata(v)}\n" })
        }

        val cmd = mutableListOf("ffmpeg", "-y", "-loglevel", "error", "-i", audio.path, "-f", "ffmetadata", "-i", metadata.path)
        if (cover != null) cmd += listOf("-i", cover.path)
        cmd += listOf("-map", "0:a", "-map_metadata", "1")
        if (cover != null) {
            cmd += listOf(
                "-map", "2:v", "-disposition:v", "attached_pic",
                "-metadata:s:v", "title=Album cover", "-metadata:s:v", "comment=Cover (front)",
            )
        }
        cmd += listOf("-c", "copy")
        if (output.extension.lowercase() == "mp3") cmd += listOf("-id3v2_version", "3")
        cmd += output.path
        run(*cmd.toTypedArray())
    }

    /** Base64 FLAC picture block, the standard way to embed a front cover in Ogg Vorbis comments. */
    private fun flacPictureBlock(jpeg: File): String {
        val data = jpeg.readBytes()
        val image = runCatching { ImageIO.read(jpeg) }.getOrNull()
        val mime = "image/jpeg".toByteArray()
        val description = "Album cover".toByteArray()
        val block = ByteBuffer.allocate(32 + mime.size + description.size + data.size)
            .putInt(3) // front cover
            .putInt(mime.size).put(mime)
            .putInt(description.size).put(description)
            .putInt(image?.width ?: 0).putInt(image?.height ?: 0)
            .putInt(24).putInt(0) // color depth, palette size
            .putInt(data.size).put(data)
        return Base64.getEncoder().encodeToString(block.array())
    }

    private fun escapeFfMetadata(value: String): String =
        value.replace(Regex("""[=;#\\\n]""")) { "\\" + it.value }

    /** Runs yt-dlp with [args], signed in via the configured browser's cookies if any. */
    private fun ytDlp(vararg args: String): String {
        val cookies = cookiesBrowser?.let { arrayOf("--cookies-from-browser", it) } ?: emptyArray()
        return run("yt-dlp", *cookies, *args)
    }

    private fun run(vararg cmd: String): String {
        val process = ProcessBuilder(*cmd).redirectErrorStream(true).start()
        try {
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                val reason = output.lines().lastOrNull { it.isNotBlank() } ?: "exit code $exitCode"
                throw IOException("${cmd.first()} failed: $reason")
            }
            return output
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
    }

    fun watchUrl(videoId: String) = "https://music.youtube.com/watch?v=$videoId"

    private fun requireValidId(videoId: String) {
        require(videoIdRegex.matches(videoId)) { "Invalid YouTube video id: $videoId" }
    }

    private fun sanitizeFileName(name: String): String =
        name.replace(Regex("""[\\/:*?"<>|\p{Cntrl}]"""), "_").trim().trim('.').take(120).ifBlank { "Unknown" }

    private fun isOnPath(executable: String): Boolean {
        val names = if (System.getProperty("os.name").startsWith("Windows")) {
            listOf("$executable.exe", "$executable.cmd", "$executable.bat")
        } else {
            listOf(executable)
        }
        return System.getenv("PATH").orEmpty().split(File.pathSeparator).any { dir ->
            names.any { File(dir, it).canExecute() }
        }
    }
}

/**
 * Tags of a downloaded track, modelled after what MusicBrainz Picard writes: [artist] and [albumArtist]
 * are the credits as displayed, [artists] and [albumArtists] the individual (multi-valued) artists.
 */
internal data class TrackTags(
    val title: String,
    val artist: String,
    val artists: List<String>,
    val album: String? = null,
    val albumArtist: String? = null,
    val albumArtists: List<String> = emptyList(),
    val trackNumber: Int? = null,
    val totalTracks: Int? = null,
    val year: Int? = null,
    /** MusicBrainz primary type, e.g. "album", "single" or "ep". */
    val releaseType: String? = null,
    val media: String? = null,
    val comment: String,
) {
    /** Vorbis comment names as used by Picard; repeated keys hold multiple values. */
    fun vorbisComments(): List<Pair<String, String>> = buildList {
        add("TITLE" to title)
        if (artist.isNotBlank()) add("ARTIST" to artist)
        artists.forEach { add("ARTISTS" to it) }
        album?.let { add("ALBUM" to it) }
        albumArtist?.let { add("ALBUMARTIST" to it) }
        albumArtists.forEach { add("ALBUMARTISTS" to it) }
        trackNumber?.let { add("TRACKNUMBER" to it.toString()) }
        totalTracks?.let {
            add("TRACKTOTAL" to it.toString())
            add("TOTALTRACKS" to it.toString())
        }
        year?.let { add("DATE" to it.toString()) }
        releaseType?.let { add("RELEASETYPE" to it) }
        media?.let { add("MEDIA" to it) }
        add("COMMENT" to comment)
    }

    /** Picard's iTunes freeform atom names for the tags MP4 has no standard atom for. */
    fun mp4FreeformTags(): Map<String, List<String>> = mapOf(
        "ARTISTS" to artists,
        "ALBUMARTISTS" to albumArtists,
        "MusicBrainz Album Type" to listOfNotNull(releaseType),
        "MEDIA" to listOfNotNull(media),
    )

    companion object {
        fun of(song: Song, album: YouTubeAlbum?): TrackTags {
            val artists = song.artists.map { it.name }.ifEmpty { listOfNotNull(song.artist.takeIf { it.isNotBlank() }) }
            val base = TrackTags(
                title = song.title,
                artist = song.artist,
                artists = artists,
                comment = song.youTubeVideoId?.let(YtDlp::watchUrl).orEmpty(),
            )
            if (album == null) {
                // Without album details the track artists are the best guess for the album artists
                if (song.album.isBlank()) return base
                return base.copy(album = song.album, albumArtist = song.artist.ifBlank { null }, albumArtists = artists)
            }
            val videoId = song.youTubeVideoId
            val track = album.tracks.firstOrNull { it.videoId == videoId }
                // The album may list another video (e.g. the official audio) of the same track
                ?: album.tracks.singleOrNull { it.title.equals(song.title, ignoreCase = true) }
            val albumArtists = album.artists.map { it.name }.ifEmpty { artists }
            return base.copy(
                album = album.title,
                albumArtist = album.artist.ifBlank { albumArtists.joinToString(", ") },
                albumArtists = albumArtists,
                trackNumber = track?.trackNumber,
                totalTracks = album.trackCount,
                year = album.year,
                releaseType = album.releaseType?.lowercase()?.takeIf { it in setOf("album", "single", "ep") },
                media = "Digital Media",
            )
        }
    }
}

private class YtDlpDownloader : YouTubeDownloader {
    private val api by lazy { YouTubeMusicApi() }

    override var cookiesBrowser: String?
        get() = YtDlp.cookiesBrowser
        set(value) { YtDlp.cookiesBrowser = value }

    override suspend fun download(song: Song, targetDir: String): String = withContext(Dispatchers.IO) {
        val album = song.albumId.takeIf { it.isNotBlank() }?.let { albumId ->
            try {
                api.getAlbum(albumId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null // Tag what the search result provides
            }
        }
        val tags = TrackTags.of(song, album)
        YtDlp.download(song, tags, File(targetDir)).absolutePath
    }

    override suspend fun audioStreamInfo(song: Song): AudioStreamInfo? {
        val videoId = song.youTubeVideoId ?: return null
        return withContext(Dispatchers.IO) { YtDlp.audioStreamInfo(videoId) }
    }
}

actual fun createYouTubeDownloader(): YouTubeDownloader? = if (YtDlp.isAvailable) YtDlpDownloader() else null
