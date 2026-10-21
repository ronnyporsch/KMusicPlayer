package de.ronnyporsch.open_rgb.kmusicplayer.ui.screen.login

data class LoginState(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
)

sealed class LoginEvent {
    data class ServerUrlChanged(val url: String) : LoginEvent()
    data class UsernameChanged(val username: String) : LoginEvent()
    data class PasswordChanged(val password: String) : LoginEvent()
    data object ConnectClicked : LoginEvent()
}

sealed class LoginEffect {
    data object NavigateToLibrary : LoginEffect()
}
