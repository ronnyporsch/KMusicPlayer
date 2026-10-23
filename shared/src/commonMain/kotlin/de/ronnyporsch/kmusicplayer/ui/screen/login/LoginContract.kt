package de.ronnyporsch.kmusicplayer.ui.screen.login

import de.ronnyporsch.kmusicplayer.domain.model.ServerType

data class LoginState(
    val serverType: ServerType = ServerType.Jellyfin,
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val allowUntrustedCertificate: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
)

sealed class LoginEvent {
    data class ServerTypeChanged(val type: ServerType) : LoginEvent()
    data class ServerUrlChanged(val url: String) : LoginEvent()
    data class UsernameChanged(val username: String) : LoginEvent()
    data class PasswordChanged(val password: String) : LoginEvent()
    data class AllowUntrustedCertificateToggled(val allow: Boolean) : LoginEvent()
    data object ConnectClicked : LoginEvent()
}

sealed class LoginEffect {
    data object NavigateToLibrary : LoginEffect()
}
