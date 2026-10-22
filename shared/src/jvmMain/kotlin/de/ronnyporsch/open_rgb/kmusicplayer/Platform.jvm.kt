package de.ronnyporsch.open_rgb.kmusicplayer

class JVMPlatform : Platform {
    override val name: String = "Java ${System.getProperty("java.version")}"
    override val isMobile: Boolean = false
    // javax.sound with the MP3 and FLAC SPIs
    override val audioContainers: List<String> = listOf("mp3", "flac", "wav", "aiff")
}

actual fun getPlatform(): Platform = JVMPlatform()