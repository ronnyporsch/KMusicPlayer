package de.ronnyporsch.voidcast.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.browser.document
import org.w3c.dom.events.Event

@Composable
actual fun rememberSystemFullscreen(onExited: () -> Unit): SystemFullscreen {
    val currentOnExited by rememberUpdatedState(onExited)
    DisposableEffect(Unit) {
        // E.g. Escape leaves the browser's fullscreen
        val listener: (Event) -> Unit = { if (document.fullscreenElement == null) currentOnExited() }
        document.addEventListener("fullscreenchange", listener)
        onDispose { document.removeEventListener("fullscreenchange", listener) }
    }
    return remember {
        // Browsers only allow this while handling a click or key press
        SystemFullscreen { enabled, _ ->
            if (enabled) {
                if (document.fullscreenElement == null) document.documentElement?.requestFullscreen()
            } else if (document.fullscreenElement != null) {
                document.exitFullscreen()
            }
        }
    }
}
