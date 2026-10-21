package de.ronnyporsch.open_rgb.kmusicplayer.ui.screen.settings

data class SettingsState(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val gaplessEnabled: Boolean = false,
    val crossfadeEnabled: Boolean = false,
    val crossfadeDurationSeconds: Int = 5,
    val youTubeAvailable: Boolean = false,
    val youTubeDownloadDir: String = "",
    val youTubeCookiesBrowser: String = "",
    val isReconnecting: Boolean = false,
    val reconnectSuccess: Boolean = false,
    val error: String? = null,
)

sealed class SettingsEvent {
    data class ServerUrlChanged(val url: String) : SettingsEvent()
    data class UsernameChanged(val username: String) : SettingsEvent()
    data class PasswordChanged(val password: String) : SettingsEvent()
    data class GaplessToggled(val enabled: Boolean) : SettingsEvent()
    data class CrossfadeToggled(val enabled: Boolean) : SettingsEvent()
    data class CrossfadeDurationChanged(val seconds: Int) : SettingsEvent()
    data class YouTubeDownloadDirChanged(val dir: String) : SettingsEvent()
    /** Blank disables browser cookies. */
    data class YouTubeCookiesBrowserChanged(val browser: String) : SettingsEvent()
    data object Reconnect : SettingsEvent()
}

sealed class SettingsEffect {
    data class ShowMessage(val message: String) : SettingsEffect()
}
