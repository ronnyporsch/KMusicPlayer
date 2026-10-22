package de.ronnyporsch.open_rgb.kmusicplayer.platform

/** Reads a text file the app stored with [writeAppFile], or null if there is none. */
expect suspend fun readAppFile(name: String): String?

/** Stores [content] in the app's private storage under [name]; null deletes it. */
expect suspend fun writeAppFile(name: String, content: String?)

/** Song files kept on the device for offline playback. */
interface SongFileStore {
    /** URI the audio player can play the downloaded song from, or null if it isn't downloaded. */
    fun uriOf(songId: String): String?

    /** Downloads [url] as the file of [songId]. Throws if that fails. */
    suspend fun download(songId: String, url: String)

    suspend fun delete(songId: String)
}

/** Null where songs can't be downloaded. */
expect fun createSongFileStore(): SongFileStore?
