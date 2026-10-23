package de.ronnyporsch.open_rgb.kmusicplayer

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import de.ronnyporsch.open_rgb.kmusicplayer.platform.GlobalShortcuts
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    val currentSong by AppSession.get().currentSong.collectAsState(null)
    Window(
        onCloseRequest = {
            AppSession.get().release()
            exitApplication()
        },
        title = currentSong?.let { if (it.artist.isBlank()) it.title else "${it.artist} - ${it.title}" } ?: "KMusicPlayer",
        state = rememberWindowState(placement = WindowPlacement.Maximized, width = 1024.dp, height = 768.dp),
        onPreviewKeyEvent = GlobalShortcuts::onKeyEvent,
    ) {
        App()
    }
}
