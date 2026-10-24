package de.ronnyporsch.voidcast.platform

import kotlinx.browser.localStorage

private fun key(name: String) = "voidcast_file_$name"

/** Key of the app's former name, KMusicPlayer; read until the file is written again. */
private fun oldKey(name: String) = "kmusicplayer_file_$name"

actual suspend fun readAppFile(name: String): String? = localStorage.getItem(key(name)) ?: localStorage.getItem(oldKey(name))

actual suspend fun writeAppFile(name: String, content: String?) {
    // localStorage throws once its quota (usually 5 MB) is used up
    try {
        if (content == null) localStorage.removeItem(key(name)) else localStorage.setItem(key(name), content)
        localStorage.removeItem(oldKey(name))
    } catch (_: Throwable) {
    }
}

actual fun createSongFileStore(): SongFileStore? = null
