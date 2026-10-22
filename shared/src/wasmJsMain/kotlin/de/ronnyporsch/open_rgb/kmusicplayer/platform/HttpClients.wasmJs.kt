package de.ronnyporsch.open_rgb.kmusicplayer.platform

import io.ktor.client.*

actual val canAcceptUntrustedCertificates: Boolean = false

internal actual fun closeIdleConnections() {}

actual fun createHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient = HttpClient(block)
