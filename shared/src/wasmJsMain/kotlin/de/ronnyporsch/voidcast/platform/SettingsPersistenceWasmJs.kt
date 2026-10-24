package de.ronnyporsch.voidcast.platform

import de.ronnyporsch.voidcast.domain.model.AppSettings
import kotlinx.browser.localStorage
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

private const val SETTINGS_KEY = "voidcast_settings"
/** Key of the app's former name, KMusicPlayer. */
private const val OLD_SETTINGS_KEY = "kmusicplayer_settings"
private val json = Json { ignoreUnknownKeys = true }

actual fun saveSettings(settings: AppSettings) {
    try {
        val json = json.encodeToString(settings)
        localStorage.setItem(SETTINGS_KEY, json)
    } catch (e: Exception) {
        // Ignore or log
    }
}

actual fun loadSettings(): AppSettings? {
    return try {
        (localStorage.getItem(SETTINGS_KEY) ?: localStorage.getItem(OLD_SETTINGS_KEY))?.let {
            json.decodeFromString<AppSettings>(it)
        }
    } catch (e: Exception) {
        null
    }
}
