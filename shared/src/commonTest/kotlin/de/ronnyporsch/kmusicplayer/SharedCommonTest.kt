package de.ronnyporsch.kmusicplayer

import de.ronnyporsch.kmusicplayer.data.api.toDomain
import de.ronnyporsch.kmusicplayer.data.dto.AlbumDto
import de.ronnyporsch.kmusicplayer.data.dto.ArtistDto
import de.ronnyporsch.kmusicplayer.data.dto.SongDto
import de.ronnyporsch.kmusicplayer.data.repository.collectAppearsOnAlbumIds
import de.ronnyporsch.kmusicplayer.domain.model.Artist
import de.ronnyporsch.kmusicplayer.domain.model.Song
import de.ronnyporsch.kmusicplayer.domain.model.formatDuration
import de.ronnyporsch.kmusicplayer.domain.model.formatDurationMs
import de.ronnyporsch.kmusicplayer.util.md5
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SharedCommonTest {

    @Test
    fun md5_produces_correct_hash() {
        assertEquals("5d41402abc4b2a76b9719d911017c592", md5("hello"))
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", md5(""))
        assertEquals("098f6bcd4621d373cade4e832627b4f6", md5("test"))
    }

    @Test
    fun md5_subsonic_auth_token() {
        val password = "sesame"
        val salt = "c19b2d"
        val token = md5(password + salt)
        assertEquals("26719a1196d2a940705a59634eb18eab", token)
    }

    @Test
    fun formatDuration_formats_correctly() {
        assertEquals("0:00", 0.formatDuration())
        assertEquals("1:00", 60.formatDuration())
        assertEquals("3:45", 225.formatDuration())
        assertEquals("10:05", 605.formatDuration())
    }

    @Test
    fun formatDurationMs_formats_correctly() {
        assertEquals("0:00", 0L.formatDurationMs())
        assertEquals("1:00", 60000L.formatDurationMs())
        assertEquals("3:45", 225000L.formatDurationMs())
    }

    @Test
    fun artistDto_maps_to_domain() {
        val dto = ArtistDto(id = "ar1", name = "Test Artist", albumCount = 5, coverArt = "cover1")
        val domain = dto.toDomain()
        assertEquals("ar1", domain.id)
        assertEquals("Test Artist", domain.name)
        assertEquals(5, domain.albumCount)
        assertEquals("cover1", domain.coverArtId)
    }

    @Test
    fun albumDto_maps_to_domain() {
        val dto = AlbumDto(
            id = "al1", name = "Test Album", artist = "Artist",
            artistId = "ar1", coverArt = "c1", songCount = 10, year = 2024, duration = 3600
        )
        val domain = dto.toDomain()
        assertEquals("al1", domain.id)
        assertEquals("Test Album", domain.name)
        assertEquals("Artist", domain.artist)
        assertEquals(10, domain.songCount)
        assertEquals(2024, domain.year)
    }

    @Test
    fun songDto_maps_to_domain() {
        val dto = SongDto(
            id = "s1", title = "My Song", artist = "Artist", artistId = "ar1",
            album = "Album", albumId = "al1", duration = 180, track = 3
        )
        val domain = dto.toDomain()
        assertEquals("s1", domain.id)
        assertEquals("My Song", domain.title)
        assertEquals(180, domain.duration)
        assertEquals(3, domain.track)
    }

    @Test
    fun albums_with_different_artists_are_deduplicated_by_id() {
        val albumFromArtist1 = AlbumDto(
            id = "album_collab",
            name = "Collaborative Album",
            artist = "Artist A",
            artistId = "ar1",
            coverArt = "c1",
            songCount = 10,
            year = 2024,
            duration = 3600
        ).toDomain()

        val albumFromArtist2 = AlbumDto(
            id = "album_collab",
            name = "Collaborative Album",
            artist = "Artist B",
            artistId = "ar2",
            coverArt = "c1",
            songCount = 10,
            year = 2024,
            duration = 3600
        ).toDomain()

        val rawAlbums = listOf(albumFromArtist1, albumFromArtist2)
        val deduplicated = rawAlbums.distinctBy { it.id.ifEmpty { "${it.name}_${it.artist}" } }

        assertEquals(1, deduplicated.size)
        assertEquals("album_collab", deduplicated[0].id)
        assertEquals("Collaborative Album", deduplicated[0].name)
    }

    @Test
    fun song_artists_come_from_server_tags() {
        val song = SongDto(
            id = "s1",
            title = "Otherside (feat. Foo)",
            artist = "Main Artist feat. Foo",
            artistId = "a1",
            artists = listOf(ArtistDto(id = "a1", name = "Main Artist"), ArtistDto(id = "a2", name = "Foo")),
        ).toDomain()

        assertEquals(listOf("a1", "a2"), song.artists.map { it.id })
        assertEquals(listOf("Main Artist", "Foo"), song.artists.map { it.name })
    }

    @Test
    fun combined_artist_tag_is_not_split() {
        val song = SongDto(id = "s1", title = "Song", artist = "Simon & Garfunkel", artistId = "a1").toDomain()

        assertEquals(listOf("Simon & Garfunkel"), song.artists.map { it.name })
        assertEquals("a1", song.artists.single().id)
    }

    @Test
    fun song_without_artist_has_no_credits() {
        assertTrue(SongDto(id = "s1", title = "Song").toDomain().artists.isEmpty())
    }

    @Test
    fun appears_on_album_ids_exclude_primary_albums_and_duplicates() {
        val songs = listOf(
            Song(
                id = "s1",
                title = "Track 1",
                artist = "Main Artist",
                album = "Collaborative Album",
                albumId = "feat-1",
                artists = listOf(Artist(id = "a1", name = "Featured Artist"), Artist(id = "a2", name = "Main Artist")),
            ),
            Song(
                id = "s2",
                title = "Track 2",
                artist = "Other Artist",
                album = "Another Album",
                albumId = "feat-2",
                artists = listOf(Artist(id = "a1", name = "Featured Artist")),
            ),
            Song(
                id = "s3",
                title = "Track 3",
                artist = "Main Artist",
                album = "Own Album",
                albumId = "own-1",
                artists = listOf(Artist(id = "a2", name = "Main Artist")),
            ),
            Song(
                id = "s4",
                title = "Track 4",
                artist = "Other Artist",
                album = "Collaborative Album",
                albumId = "feat-1",
                artists = listOf(Artist(id = "a1", name = "Featured Artist"), Artist(id = "a2", name = "Main Artist")),
            ),
        )

        val albumIds = collectAppearsOnAlbumIds("Featured Artist", listOf("own-1"), songs)

        assertEquals(listOf("feat-1", "feat-2"), albumIds)
    }
}
