package de.ronnyporsch.open_rgb.kmusicplayer.platform

import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.AppSettings
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.PlaybackSettings
import java.util.prefs.Preferences

private val prefs = Preferences.userNodeForPackage(AppSettings::class.java)

actual fun saveSettings(settings: AppSettings) {
    prefs.put("serverUrl", settings.serverUrl)
    prefs.put("username", settings.username)
    prefs.put("password", settings.password)
    prefs.putBoolean("gaplessEnabled", settings.playback.gaplessEnabled)
    prefs.putBoolean("crossfadeEnabled", settings.playback.crossfadeEnabled)
    prefs.putInt("crossfadeDurationSeconds", settings.playback.crossfadeDurationSeconds)
    prefs.put("youTubeDownloadDir", settings.youTubeDownloadDir)
    prefs.put("youTubeCookiesBrowser", settings.youTubeCookiesBrowser)
    prefs.flush()
}

actual fun loadSettings(): AppSettings? {
    val serverUrl = prefs.get("serverUrl", "") ?: ""
    if (serverUrl.isBlank()) return null
    return AppSettings(
        serverUrl = serverUrl,
        username = prefs.get("username", "") ?: "",
        password = prefs.get("password", "") ?: "",
        playback = PlaybackSettings(
            gaplessEnabled = prefs.getBoolean("gaplessEnabled", false),
            crossfadeEnabled = prefs.getBoolean("crossfadeEnabled", false),
            crossfadeDurationSeconds = prefs.getInt("crossfadeDurationSeconds", 5),
        ),
        youTubeDownloadDir = prefs.get("youTubeDownloadDir", "") ?: "",
        youTubeCookiesBrowser = prefs.get("youTubeCookiesBrowser", "") ?: "",
    )
}
