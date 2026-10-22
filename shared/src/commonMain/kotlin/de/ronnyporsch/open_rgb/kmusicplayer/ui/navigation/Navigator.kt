package de.ronnyporsch.open_rgb.kmusicplayer.ui.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class Screen {
    data object Login : Screen()
    data object Library : Screen()
    data class ArtistAlbums(val artistId: String?, val artistName: String) : Screen()
    data class AlbumSongs(val albumId: String) : Screen()
    data class PlaylistSongs(val playlistId: String) : Screen()
    data object Player : Screen()
    data object Search : Screen()
    data object Settings : Screen()
}

class Navigator(initialScreen: Screen = Screen.Login) {
    private val backStack = mutableListOf(initialScreen)
    private val forwardStack = mutableListOf<Screen>()

    private val _currentScreen = MutableStateFlow(initialScreen)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    fun push(screen: Screen) {
        if (_currentScreen.value == screen) return
        forwardStack.clear()
        backStack.add(screen)
        _currentScreen.value = screen
    }

    fun pop(): Boolean {
        if (backStack.size <= 1) return false
        val current = backStack.removeLast()
        forwardStack.add(current)
        _currentScreen.value = backStack.last()
        return true
    }

    fun forward(): Boolean {
        if (forwardStack.isEmpty()) return false
        val screen = forwardStack.removeLast()
        backStack.add(screen)
        _currentScreen.value = screen
        return true
    }

    fun replaceAll(screen: Screen) {
        backStack.clear()
        forwardStack.clear()
        backStack.add(screen)
        _currentScreen.value = screen
    }

    fun canGoBack(): Boolean = backStack.size > 1
    fun canGoForward(): Boolean = forwardStack.isNotEmpty()
}
