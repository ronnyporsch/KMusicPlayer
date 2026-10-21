package de.ronnyporsch.open_rgb.kmusicplayer.platform

import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.AppSettings
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.PlaybackSettings
import kotlinx.browser.window

actual fun saveSettings(settings: AppSettings) {
    val storage = window.localStorage
    storage.setItem("serverUrl", settings.serverUrl)
    storage.setItem("username", settings.username)
    storage.setItem("password", settings.password)
    storage.setItem("gaplessEnabled", settings.playback.gaplessEnabled.toString())
    storage.setItem("crossfadeEnabled", settings.playback.crossfadeEnabled.toString())
    storage.setItem("crossfadeDurationSeconds", settings.playback.crossfadeDurationSeconds.toString())
}

actual fun loadSettings(): AppSettings? {
    val storage = window.localStorage
    val serverUrl = storage.getItem("serverUrl") ?: return null
    if (serverUrl.isBlank()) return null
    return AppSettings(
        serverUrl = serverUrl,
        username = storage.getItem("username") ?: "",
        password = storage.getItem("password") ?: "",
        playback = PlaybackSettings(
            gaplessEnabled = storage.getItem("gaplessEnabled")?.toBooleanStrictOrNull() ?: false,
            crossfadeEnabled = storage.getItem("crossfadeEnabled")?.toBooleanStrictOrNull() ?: false,
            crossfadeDurationSeconds = storage.getItem("crossfadeDurationSeconds")?.toIntOrNull() ?: 5,
        ),
    )
}
