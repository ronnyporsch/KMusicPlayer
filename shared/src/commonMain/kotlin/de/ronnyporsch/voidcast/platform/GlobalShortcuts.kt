package de.ronnyporsch.voidcast.platform

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * App-wide keyboard shortcuts that work no matter which element has focus (or whether any has).
 * Compose only routes key events to the focused node, so platforms feed them in from the window:
 * desktop via `Window(onPreviewKeyEvent)`, web via a document listener ([installGlobalShortcutListener]).
 */
object GlobalShortcuts {
    private val _focusSearch = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val focusSearch: SharedFlow<Unit> = _focusSearch.asSharedFlow()

    /** Ctrl+K (Cmd+K on macOS) */
    fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown || event.key != Key.K) return false
        if (!event.isCtrlPressed && !event.isMetaPressed) return false
        requestSearchFocus()
        return true
    }

    fun requestSearchFocus() {
        _focusSearch.tryEmit(Unit)
    }
}

/** Hooks [GlobalShortcuts] into platforms whose window isn't reachable from the app's entry point. */
expect fun installGlobalShortcutListener()
