package de.ronnyporsch.open_rgb.kmusicplayer

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    Window(
        onCloseRequest = {
            AppSession.get().release()
            exitApplication()
        },
        title = "KMusicPlayer",
        state = rememberWindowState(width = 1024.dp, height = 768.dp),
    ) {
        App()
    }
}
