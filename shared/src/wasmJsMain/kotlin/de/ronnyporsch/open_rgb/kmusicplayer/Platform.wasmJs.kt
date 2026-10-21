package de.ronnyporsch.open_rgb.kmusicplayer

class WasmPlatform : Platform {
    override val name: String = "Web with Kotlin/Wasm"
    override val isMobile: Boolean = false
}

actual fun getPlatform(): Platform = WasmPlatform()