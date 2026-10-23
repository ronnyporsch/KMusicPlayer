package de.ronnyporsch.kmusicplayer

class WasmPlatform : Platform {
    override val name: String = "Web with Kotlin/Wasm"
    override val deviceName: String = "Web browser"
    override val isMobile: Boolean = false
    override val audioContainers: List<String> = BROWSER_AUDIO_CONTAINERS
}

actual fun getPlatform(): Platform = WasmPlatform()