package de.ronnyporsch.open_rgb.kmusicplayer.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.window.WindowPlacement
import java.awt.KeyboardFocusManager
import java.awt.Window

@Composable
actual fun rememberSystemFullscreen(onExited: () -> Unit): SystemFullscreen = remember {
    var previous = WindowPlacement.Maximized
    SystemFullscreen { enabled, _ ->
        // The app's window; the Window composable picks up placement changes of it into its state
        val window = KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow as? ComposeWindow
            ?: Window.getWindows().filterIsInstance<ComposeWindow>().firstOrNull { it.isShowing }
            ?: return@SystemFullscreen
        if (enabled) {
            if (window.placement != WindowPlacement.Fullscreen) previous = window.placement
            window.placement = WindowPlacement.Fullscreen
        } else if (window.placement == WindowPlacement.Fullscreen) {
            val device = window.graphicsConfiguration.device
            if (device.fullScreenWindow === window) {
                // What fullscreen is on Linux and Windows. Placing the window Maximized wouldn't leave it, Floating
                // would unmaximize it first (briefly showing it at its floating bounds, possibly on another screen)
                device.fullScreenWindow = null
                if (window.placement != previous) window.placement = previous
            } else {
                // macOS
                window.placement = previous
            }
        }
    }
}
