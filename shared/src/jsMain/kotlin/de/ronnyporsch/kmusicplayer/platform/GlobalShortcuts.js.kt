package de.ronnyporsch.kmusicplayer.platform

import kotlinx.browser.document
import org.w3c.dom.events.KeyboardEvent

private var installed = false

actual fun installGlobalShortcutListener() {
    if (installed) return
    installed = true
    // Capture phase, so the browser's own Ctrl+K (focus web search) is suppressed before anything else sees it
    document.addEventListener("keydown", { event ->
        event as KeyboardEvent
        if ((event.ctrlKey || event.metaKey) && event.key.lowercase() == "k") {
            event.preventDefault()
            event.stopPropagation()
            GlobalShortcuts.requestSearchFocus()
        }
    }, true)
}
