package de.ronnyporsch.voidcast.platform

// Desktop passes window key events in main.kt; Android has no keyboard shortcuts
actual fun installGlobalShortcutListener() {}
