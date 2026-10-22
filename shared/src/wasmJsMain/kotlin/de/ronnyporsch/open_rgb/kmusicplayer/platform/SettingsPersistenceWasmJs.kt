package de.ronnyporsch.open_rgb.kmusicplayer.platform

import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.AppSettings
import kotlinx.browser.localStorage
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

private const val SETTINGS_KEY = "kmusicplayer_settings"
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
        localStorage.getItem(SETTINGS_KEY)?.let {
            json.decodeFromString<AppSettings>(it)
        }
    } catch (e: Exception) {
        null
    }
}
