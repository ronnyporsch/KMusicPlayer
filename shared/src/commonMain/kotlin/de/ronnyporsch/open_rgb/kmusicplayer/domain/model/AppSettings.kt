package de.ronnyporsch.open_rgb.kmusicplayer.domain.model

import de.ronnyporsch.open_rgb.kmusicplayer.platform.loadSettings
import de.ronnyporsch.open_rgb.kmusicplayer.platform.saveSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlaybackSettings(
    val gaplessEnabled: Boolean = false,
    val crossfadeEnabled: Boolean = false,
    val crossfadeDurationSeconds: Int = 5,
)

data class AppSettings(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val playback: PlaybackSettings = PlaybackSettings(),
    /** Folder (usually the server's music folder, local or mounted) that YouTube Music downloads are written to. */
    val youTubeDownloadDir: String = "",
    /** Browser yt-dlp reads YouTube cookies from (one of [YT_DLP_COOKIE_BROWSERS]), or blank for none. */
    val youTubeCookiesBrowser: String = "",
) {
    val hasSavedConnection: Boolean
        get() = serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()
}

/** Browsers supported by yt-dlp's `--cookies-from-browser`. */
val YT_DLP_COOKIE_BROWSERS = listOf("brave", "chrome", "chromium", "edge", "firefox", "opera", "safari", "vivaldi", "whale")

class SettingsHolder {
    private val _settings = MutableStateFlow(loadSettings() ?: AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun update(transform: AppSettings.() -> AppSettings) {
        _settings.value = _settings.value.transform()
        saveSettings(_settings.value)
    }
}
