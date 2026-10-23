package de.ronnyporsch.open_rgb.kmusicplayer

class JVMPlatform : Platform {
    override val name: String = "Java ${System.getProperty("java.version")}"
    override val deviceName: String by lazy {
        // Without a network lookup, which InetAddress.getLocalHost() may do
        val host = System.getenv("HOSTNAME") ?: System.getenv("COMPUTERNAME")
            ?: runCatching { java.io.File("/etc/hostname").readText().trim() }.getOrNull()
        host?.takeIf { it.isNotBlank() } ?: System.getProperty("os.name") ?: "Desktop"
    }
    override val isMobile: Boolean = false
    // javax.sound with the MP3 and FLAC SPIs
    override val audioContainers: List<String> = listOf("mp3", "flac", "wav", "aiff")
}

actual fun getPlatform(): Platform = JVMPlatform()