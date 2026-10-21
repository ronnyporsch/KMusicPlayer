package de.ronnyporsch.open_rgb.kmusicplayer.ui.screen.login

import de.ronnyporsch.open_rgb.kmusicplayer.data.api.SubsonicApiService
import de.ronnyporsch.open_rgb.kmusicplayer.data.repository.MusicRepository
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.ServerConfig
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.SettingsHolder
import de.ronnyporsch.open_rgb.kmusicplayer.mvi.MviStore
import de.ronnyporsch.open_rgb.kmusicplayer.util.generateSalt
import de.ronnyporsch.open_rgb.kmusicplayer.util.md5

class LoginViewModel(
    private val apiService: SubsonicApiService,
    private val repository: MusicRepository,
    private val settingsHolder: SettingsHolder,
) : MviStore<LoginState, LoginEvent, LoginEffect>(LoginState()) {

    override fun onEvent(event: LoginEvent) {
        when (event) {
            is LoginEvent.ServerUrlChanged -> setState { copy(serverUrl = event.url, error = null) }
            is LoginEvent.UsernameChanged -> setState { copy(username = event.username, error = null) }
            is LoginEvent.PasswordChanged -> setState { copy(password = event.password, error = null) }
            is LoginEvent.ConnectClicked -> connect()
        }
    }

    private fun connect() {
        val state = currentState
        if (state.serverUrl.isBlank() || state.username.isBlank() || state.password.isBlank()) {
            setState { copy(error = "All fields are required") }
            return
        }

        setState { copy(isLoading = true, error = null) }

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
                    setState { copy(isLoading = false) }
                    emitEffect(LoginEffect.NavigateToLibrary)
                } else {
                    setState { copy(isLoading = false, error = "Connection failed. Check your credentials.") }
                }
            } catch (e: Exception) {
                setState { copy(isLoading = false, error = e.message ?: "Connection error") }
            }
        }
    }
}
