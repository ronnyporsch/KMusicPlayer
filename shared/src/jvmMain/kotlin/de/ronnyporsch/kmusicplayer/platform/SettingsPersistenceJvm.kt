package de.ronnyporsch.kmusicplayer.platform

import de.ronnyporsch.kmusicplayer.domain.model.AppSettings
import de.ronnyporsch.kmusicplayer.domain.model.PlaybackSettings
import de.ronnyporsch.kmusicplayer.domain.model.ServerType
import java.util.prefs.Preferences

private val prefs = Preferences.userNodeForPackage(AppSettings::class.java).also(::migrateFromOldPackage)

/** Copies settings saved under the old `de.ronnyporsch.open_rgb.kmusicplayer` package node once. */
private fun migrateFromOldPackage(prefs: Preferences) {
    val oldPath = "/de/ronnyporsch/open_rgb/kmusicplayer/domain/model"
    val root = Preferences.userRoot()
    if (prefs.keys().isNotEmpty() || !root.nodeExists(oldPath)) return
    val old = root.node(oldPath)
    old.keys().forEach { key -> old.get(key, null)?.let { prefs.put(key, it) } }
    prefs.flush()
}

actual fun saveSettings(settings: AppSettings) {
    prefs.put("serverType", settings.serverType.name)
    prefs.put("serverUrl", settings.serverUrl)
    prefs.put("username", settings.username)
    prefs.put("password", settings.password)
    prefs.putBoolean("allowUntrustedCertificate", settings.allowUntrustedCertificate)
    prefs.putBoolean("gaplessEnabled", settings.playback.gaplessEnabled)
    prefs.putBoolean("crossfadeEnabled", settings.playback.crossfadeEnabled)
    prefs.putInt("crossfadeDurationSeconds", settings.playback.crossfadeDurationSeconds)
    prefs.put("jellyfinDeviceId", settings.jellyfinDeviceId)
    prefs.flush()
}

actual fun loadSettings(): AppSettings? {
    val serverUrl = prefs.get("serverUrl", "") ?: ""
    if (serverUrl.isBlank()) return null
    return AppSettings(
        serverType = ServerType.entries.firstOrNull { it.name == prefs.get("serverType", "") } ?: ServerType.Subsonic,
        serverUrl = serverUrl,
        username = prefs.get("username", "") ?: "",
        password = prefs.get("password", "") ?: "",
        allowUntrustedCertificate = prefs.getBoolean("allowUntrustedCertificate", false),
        playback = PlaybackSettings(
            gaplessEnabled = prefs.getBoolean("gaplessEnabled", false),
            crossfadeEnabled = prefs.getBoolean("crossfadeEnabled", false),
            crossfadeDurationSeconds = prefs.getInt("crossfadeDurationSeconds", 5),
        ),
        jellyfinDeviceId = prefs.get("jellyfinDeviceId", "") ?: "",
    )
}
