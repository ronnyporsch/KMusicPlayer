package de.ronnyporsch.voidcast.platform

import androidx.compose.runtime.Composable

/** Puts the app into fullscreen and back, e.g. for a video. */
fun interface SystemFullscreen {
    /** [landscape] turns phones sideways, for landscape videos. */
    fun set(enabled: Boolean, landscape: Boolean)
}

/** [onExited] is called when the system leaves fullscreen by itself, e.g. the browser on Escape. */
@Composable
expect fun rememberSystemFullscreen(onExited: () -> Unit): SystemFullscreen
