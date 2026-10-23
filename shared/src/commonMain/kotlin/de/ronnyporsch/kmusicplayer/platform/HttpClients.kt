package de.ronnyporsch.kmusicplayer.platform

import io.ktor.client.*
import kotlin.concurrent.Volatile

/**
 * The host whose TLS certificate is accepted even if it isn't trusted (e.g. self-signed) or doesn't match the host
 * name, or null to verify all certificates. Only the music server gets this, everything else (e.g. YouTube
 * thumbnails) is still verified. Browsers verify certificates themselves, so this has no effect on the web.
 */
@Volatile
var untrustedCertificateHost: String? = null
    set(value) {
        if (field == value) return
        field = value
        closeIdleConnections()
    }

/** Drops pooled connections, which were established under the previous [untrustedCertificateHost]. */
internal expect fun closeIdleConnections()

/** Whether the platform honors [untrustedCertificateHost]. */
expect val canAcceptUntrustedCertificates: Boolean

/** Creates an HTTP client that honors [untrustedCertificateHost]. */
expect fun createHttpClient(block: HttpClientConfig<*>.() -> Unit = {}): HttpClient
