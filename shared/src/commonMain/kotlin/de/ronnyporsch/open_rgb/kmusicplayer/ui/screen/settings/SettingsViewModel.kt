package de.ronnyporsch.open_rgb.kmusicplayer.ui.screen.settings

import de.ronnyporsch.open_rgb.kmusicplayer.data.api.SubsonicApiService
import de.ronnyporsch.open_rgb.kmusicplayer.data.repository.MusicRepository
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.ServerConfig
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.SettingsHolder
import de.ronnyporsch.open_rgb.kmusicplayer.mvi.MviStore
import de.ronnyporsch.open_rgb.kmusicplayer.util.generateSalt
import de.ronnyporsch.open_rgb.kmusicplayer.util.md5

class SettingsViewModel(
    private val apiService: SubsonicApiService,
    private val repository: MusicRepository,
    private val settingsHolder: SettingsHolder,
    youTubeAvailable: Boolean,
) : MviStore<SettingsState, SettingsEvent, SettingsEffect>(SettingsState()) {

    init {
        val current = settingsHolder.settings.value
        setState {
            copy(
                serverUrl = current.serverUrl,
                username = current.username,
                password = current.password,
                gaplessEnabled = current.playback.gaplessEnabled,
                crossfadeEnabled = current.playback.crossfadeEnabled,
                crossfadeDurationSeconds = current.playback.crossfadeDurationSeconds,
                youTubeAvailable = youTubeAvailable,
                youTubeDownloadDir = current.youTubeDownloadDir,
                youTubeCookiesBrowser = current.youTubeCookiesBrowser,
            )
        }
    }

    override fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.ServerUrlChanged -> setState { copy(serverUrl = event.url, error = null) }
            is SettingsEvent.UsernameChanged -> setState { copy(username = event.username, error = null) }
            is SettingsEvent.PasswordChanged -> setState { copy(password = event.password, error = null) }
            is SettingsEvent.GaplessToggled -> {
                setState { copy(gaplessEnabled = event.enabled) }
                updatePlaybackSettings()
            }
            is SettingsEvent.CrossfadeToggled -> {
                setState { copy(crossfadeEnabled = event.enabled) }
                updatePlaybackSettings()
            }
            is SettingsEvent.CrossfadeDurationChanged -> {
                setState { copy(crossfadeDurationSeconds = event.seconds) }
                updatePlaybackSettings()
            }
            is SettingsEvent.YouTubeDownloadDirChanged -> {
                setState { copy(youTubeDownloadDir = event.dir) }
                settingsHolder.update { copy(youTubeDownloadDir = event.dir.trim()) }
            }
            is SettingsEvent.YouTubeCookiesBrowserChanged -> {
                setState { copy(youTubeCookiesBrowser = event.browser) }
                settingsHolder.update { copy(youTubeCookiesBrowser = event.browser) }
            }
            is SettingsEvent.Reconnect -> reconnect()
        }
    }

    private fun updatePlaybackSettings() {
        val state = currentState
        settingsHolder.update {
            copy(
                playback = playback.copy(
                    gaplessEnabled = state.gaplessEnabled,
                    crossfadeEnabled = state.crossfadeEnabled,
                    crossfadeDurationSeconds = state.crossfadeDurationSeconds,
                )
            )
        }
    }

    private fun reconnect() {
        val state = currentState
        if (state.serverUrl.isBlank() || state.username.isBlank() || state.password.isBlank()) {
            setState { copy(error = "All fields are required") }
            return
        }

        setState { copy(isReconnecting = true, error = null, reconnectSuccess = false) }

        launch {
            try {
                val salt = generateSalt()
                val token = md5(state.password + salt)
                val config = ServerConfig(
                    serverUrl = state.serverUrl.trimEnd('/'),
                    username = state.username,
                    token = token,
                    salt = salt,
                )
                apiService.configure(config)

                val success = repository.ping()
                if (success) {
                    settingsHolder.update {
                        copy(
                            serverUrl = state.serverUrl.trimEnd('/'),
                            username = state.username,
                            password = state.password,
                        )
                    }
                    setState { copy(isReconnecting = false, reconnectSuccess = true) }
                    emitEffect(SettingsEffect.ShowMessage("Connected successfully"))
                } else {
                    setState { copy(isReconnecting = false, error = "Connection failed") }
                }
            } catch (e: Exception) {
                setState { copy(isReconnecting = false, error = e.message ?: "Connection error") }
            }
        }
    }
}
