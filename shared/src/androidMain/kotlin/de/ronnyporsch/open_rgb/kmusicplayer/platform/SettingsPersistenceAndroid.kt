package de.ronnyporsch.open_rgb.kmusicplayer.platform

import android.content.Context
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.AppSettings
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.PlaybackSettings

private fun prefs() =
    AppContextHolder.context.getSharedPreferences("kmusicplayer_settings", Context.MODE_PRIVATE)

actual fun saveSettings(settings: AppSettings) {
    prefs().edit()
        .putString("serverUrl", settings.serverUrl)
        .putString("username", settings.username)
        .putString("password", settings.password)
        .putBoolean("gaplessEnabled", settings.playback.gaplessEnabled)
        .putBoolean("crossfadeEnabled", settings.playback.crossfadeEnabled)
        .putInt("crossfadeDurationSeconds", settings.playback.crossfadeDurationSeconds)
        .apply()
}

actual fun loadSettings(): AppSettings? {
    val p = prefs()
    val serverUrl = p.getString("serverUrl", "") ?: ""
    if (serverUrl.isBlank()) return null
    return AppSettings(
        serverUrl = serverUrl,
        username = p.getString("username", "") ?: "",
        password = p.getString("password", "") ?: "",
        playback = PlaybackSettings(
            gaplessEnabled = p.getBoolean("gaplessEnabled", false),
            crossfadeEnabled = p.getBoolean("crossfadeEnabled", false),
            crossfadeDurationSeconds = p.getInt("crossfadeDurationSeconds", 5),
        ),
    )
}
