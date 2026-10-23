package de.ronnyporsch.kmusicplayer.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import java.awt.event.MouseEvent

/**
 * Enables forward and back navigation using mouse buttons 4 and 5
 */
actual fun Modifier.onMouseNavigation(
    onBack: () -> Unit,
    onForward: () -> Unit
): Modifier = this.pointerInput(Unit) {
    awaitEachGesture {
        val event = awaitPointerEvent()
        // Compose reports side-button presses on Linux as PointerEventType.Unknown (it has no pressed
        // state for AWT buttons 6/7), so check the native AWT event instead
        val native = event.nativeEvent as? MouseEvent ?: return@awaitEachGesture
        if (native.id != MouseEvent.MOUSE_PRESSED) return@awaitEachGesture
        // AWT buttons 4/5 on Windows/macOS, 6/7 on Linux (X11 buttons 4-7 are the scroll wheels)
        when (native.button) {
            4, 6 -> onBack()
            5, 7 -> onForward()
        }
    }
}
