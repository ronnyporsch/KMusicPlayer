package de.ronnyporsch.open_rgb.kmusicplayer.util

import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.Album
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.Song

/** Newest albums first; albums without a year go last. Stable, so equal years keep the server's order. */
fun List<Album>.newestFirst(): List<Album> =
    sortedWith(compareByDescending(nullsFirst()) { it.year })

/**
 * Groups songs by album and orders the albums newest first, keeping the track order within each album.
 * Songs without a year go last.
 */
fun List<Song>.newestAlbumsFirst(): List<Song> {
    val albumYears = groupBy { it.albumId.ifEmpty { it.album } }
        .mapValues { (_, songs) -> songs.mapNotNull { it.year }.maxOrNull() }
    val albumOrder = albumYears.keys.withIndex().associate { (index, key) -> key to index }
    return sortedWith(
        compareByDescending<Song, Int?>(nullsFirst()) { albumYears[it.albumId.ifEmpty { it.album }] }
            .thenBy { albumOrder[it.albumId.ifEmpty { it.album }] }
    )
}
