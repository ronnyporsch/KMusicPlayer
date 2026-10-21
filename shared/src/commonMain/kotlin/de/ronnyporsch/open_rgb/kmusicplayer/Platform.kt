package de.ronnyporsch.open_rgb.kmusicplayer

interface Platform {
    val name: String
    val isMobile: Boolean
}

expect fun getPlatform(): Platform