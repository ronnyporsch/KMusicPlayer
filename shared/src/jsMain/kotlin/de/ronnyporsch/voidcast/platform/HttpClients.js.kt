package de.ronnyporsch.voidcast.platform

import io.ktor.client.*

actual val canAcceptUntrustedCertificates: Boolean = false

internal actual fun closeIdleConnections() {}

actual fun createHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient = HttpClient(block)
