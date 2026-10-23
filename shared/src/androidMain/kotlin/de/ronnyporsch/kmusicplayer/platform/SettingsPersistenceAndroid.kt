package de.ronnyporsch.kmusicplayer.platform

import android.content.Context
import de.ronnyporsch.kmusicplayer.domain.model.AppSettings
import de.ronnyporsch.kmusicplayer.domain.model.PlaybackSettings
import de.ronnyporsch.kmusicplayer.domain.model.ServerType

private fun prefs() =
    AppContextHolder.context.getSharedPreferences("kmusicplayer_settings", Context.MODE_PRIVATE)

actual fun saveSettings(settings: AppSettings) {
    prefs().edit()
        .putString("serverType", settings.serverType.name)
        .putString("serverUrl", settings.serverUrl)
        .putString("username", settings.username)
        .putString("password", settings.password)
        .putBoolean("allowUntrustedCertificate", settings.allowUntrustedCertificate)
        .putBoolean("gaplessEnabled", settings.playback.gaplessEnabled)
        .putBoolean("crossfadeEnabled", settings.playback.crossfadeEnabled)
        .putInt("crossfadeDurationSeconds", settings.playback.crossfadeDurationSeconds)
        .putString("jellyfinDeviceId", settings.jellyfinDeviceId)
        .apply()
}

actual fun loadSettings(): AppSettings? {
    val p = prefs()
    val serverUrl = p.getString("serverUrl", "") ?: ""
    if (serverUrl.isBlank()) return null
    return AppSettings(
        serverType = ServerType.entries.firstOrNull { it.name == p.getString("serverType", "") } ?: ServerType.Subsonic,
        serverUrl = serverUrl,
        username = p.getString("username", "") ?: "",
        password = p.getString("password", "") ?: "",
        allowUntrustedCertificate = p.getBoolean("allowUntrustedCertificate", false),
        playback = PlaybackSettings(
            gaplessEnabled = p.getBoolean("gaplessEnabled", false),
            crossfadeEnabled = p.getBoolean("crossfadeEnabled", false),
            crossfadeDurationSeconds = p.getInt("crossfadeDurationSeconds", 5),
        ),
        jellyfinDeviceId = p.getString("jellyfinDeviceId", "") ?: "",
    )
}
