package de.ronnyporsch.voidcast.ui

import androidx.compose.runtime.compositionLocalOf
import de.ronnyporsch.voidcast.data.repository.MusicRepository

val LocalCoverArtUrlProvider = compositionLocalOf<CoverArtUrlProvider> {
    error("No CoverArtUrlProvider provided")
}

val LocalMusicRepository = compositionLocalOf<MusicRepository> {
    error("No MusicRepository provided")
}

class CoverArtUrlProvider(private val repository: MusicRepository) {
    fun getUrl(coverArtId: String?, size: Int = 300): String? {
        return coverArtId?.let {
            try {
                repository.getCoverArtUrl(it, size)
            } catch (_: Exception) {
                null
            }
        }
    }
}
