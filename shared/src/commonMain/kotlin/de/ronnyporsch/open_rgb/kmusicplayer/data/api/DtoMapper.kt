package de.ronnyporsch.open_rgb.kmusicplayer.data.api

import de.ronnyporsch.open_rgb.kmusicplayer.data.dto.*
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.Album
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.Artist
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.Playlist
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.Song
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.codecDisplayName

fun ArtistDto.toDomain() = Artist(
    id = id,
    name = name,
    albumCount = albumCount,
    coverArtId = coverArt,
)

fun AlbumDto.toDomain() = Album(
    id = id,
    name = name,
    artist = artist,
    artistId = artistId,
    artists = creditedArtists(artists, artistId, artist),
    coverArtId = coverArt,
    songCount = songCount,
    year = year,
    duration = duration,
)

fun AlbumWithSongsDto.toDomain() = Album(
    id = id,
    name = name,
    artist = artist,
    artistId = artistId,
    artists = creditedArtists(artists, artistId, artist),
    coverArtId = coverArt,
    songCount = songCount,
    year = year,
    duration = duration,
    songs = song.map { it.toDomain() },
)

fun SongDto.toDomain() = Song(
    id = id,
    title = title,
    artist = artist,
    artistId = artistId,
    artists = creditedArtists(artists, artistId, artist),
    album = album,
    albumId = albumId,
    coverArtId = coverArt,
    duration = duration,
    track = track,
    discNumber = discNumber,
    year = year,
    genre = genre,
    bitRate = bitRate,
    codec = suffix?.takeIf { it.isNotBlank() }?.let(::codecDisplayName),
)

fun PlaylistDto.toDomain() = Playlist(
    id = id,
    name = name,
    songCount = songCount,
    duration = duration,
    coverArtId = coverArt,
)

fun PlaylistWithSongsDto.toDomain() = Playlist(
    id = id,
    name = name,
    songCount = songCount,
    duration = duration,
    coverArtId = coverArt,
    songs = entry.map { it.toDomain() },
)

/** Falls back to the single display artist for servers without OpenSubsonic multi-artist support. */
private fun creditedArtists(artists: List<ArtistDto>, artistId: String, artist: String): List<Artist> = when {
    artists.isNotEmpty() -> artists.map { it.toDomain() }
    artist.isNotBlank() -> listOf(Artist(id = artistId, name = artist))
    else -> emptyList()
}
