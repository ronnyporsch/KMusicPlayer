package de.ronnyporsch.open_rgb.kmusicplayer

class WasmPlatform : Platform {
    override val name: String = "Web with Kotlin/Wasm"
    override val isMobile: Boolean = false
    override val audioContainers: List<String> = BROWSER_AUDIO_CONTAINERS
}

actual fun getPlatform(): Platform = WasmPlatform()