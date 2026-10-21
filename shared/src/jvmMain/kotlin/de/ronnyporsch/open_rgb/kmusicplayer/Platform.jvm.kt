package de.ronnyporsch.open_rgb.kmusicplayer

class JVMPlatform : Platform {
    override val name: String = "Java ${System.getProperty("java.version")}"
    override val isMobile: Boolean = false
}

actual fun getPlatform(): Platform = JVMPlatform()