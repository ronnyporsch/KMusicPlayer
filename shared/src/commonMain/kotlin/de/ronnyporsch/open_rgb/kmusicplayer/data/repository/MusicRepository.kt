package de.ronnyporsch.open_rgb.kmusicplayer.data.repository

import de.ronnyporsch.open_rgb.kmusicplayer.data.api.*
import de.ronnyporsch.open_rgb.kmusicplayer.data.youtube.YouTubeMusicApi
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

interface MusicRepository {
    val isConfigured: Boolean
    suspend fun ping(): Boolean
    suspend fun getArtists(): Result<List<Artist>>
    suspend fun getArtistAlbums(artistId: String): Result<List<Album>>
    suspend fun getArtistInfo(artistId: String): Result<ArtistInfo>
    suspend fun getAlbum(albumId: String): Result<Album>
    suspend fun getPlaylists(): Result<List<Playlist>>
    suspend fun getPlaylist(playlistId: String): Result<Playlist>
    suspend fun createPlaylist(name: String, songId: String? = null): Result<Playlist>
    suspend fun addSongToPlaylist(playlistId: String, songId: String): Result<Unit>
    suspend fun removeSongFromPlaylist(playlistId: String, songIndex: Int): Result<Unit>
    suspend fun findArtistByName(name: String): Result<Artist?>
    suspend fun findSongsByArtistName(name: String): Result<List<Song>>

    /**
     * Every song of an artist: all songs of [albumIds] plus songs on other albums whose tags
     * credit the artist.
     */
    suspend fun getSongsForArtist(artistName: String, albumIds: List<String>): Result<List<Song>>
    suspend fun search(query: String): Result<SearchResults>
    suspend fun startScan(): Result<Boolean>
    fun getStreamUrl(songId: String): String
    fun getCoverArtUrl(coverArtId: String, size: Int = 300): String
}

data class SearchResults(
    val artists: List<Artist> = emptyList(),
    val albums: List<Album> = emptyList(),
    val songs: List<Song> = emptyList(),
)

class MusicRepositoryImpl(
    private val api: SubsonicApiService,
) : MusicRepository {

    override val isConfigured: Boolean get() = api.isConfigured

    override suspend fun ping(): Boolean = api.ping()

    override suspend fun getArtists(): Result<List<Artist>> = runCatching {
        val response = api.getArtists()
        val body = response.subsonicResponse
        body.artists?.index?.flatMap { index ->
            index.artist.map { it.toDomain() }
        } ?: emptyList()
    }

    override suspend fun getArtistAlbums(artistId: String): Result<List<Album>> = runCatching {
        val response = api.getArtist(artistId)
        val body = response.subsonicResponse
        body.artist?.album?.map { it.toDomain() }?.distinctBy { it.id.ifEmpty { "${it.name}_${it.artist}" } } ?: emptyList()
    }

    override suspend fun getArtistInfo(artistId: String): Result<ArtistInfo> = runCatching {
        val response = api.getArtistInfo2(artistId)
        val body = response.subsonicResponse
        val info = body.artistInfo2
        ArtistInfo(
            biography = info?.biography?.takeIf { it.isNotBlank() },
            largeImageUrl = info?.largeImageUrl ?: info?.mediumImageUrl ?: info?.smallImageUrl,
        )
    }

    override suspend fun getAlbum(albumId: String): Result<Album> = runCatching {
        val response = api.getAlbum(albumId)
        val body = response.subsonicResponse
        body.album?.toDomain() ?: throw IllegalStateException("Album not found")
    }

    override suspend fun getPlaylists(): Result<List<Playlist>> = runCatching {
        val response = api.getPlaylists()
        val body = response.subsonicResponse
        body.playlists?.playlist?.map { it.toDomain() } ?: emptyList()
    }

    override suspend fun getPlaylist(playlistId: String): Result<Playlist> = runCatching {
        val response = api.getPlaylist(playlistId)
        val body = response.subsonicResponse
        body.playlist?.toDomain() ?: throw IllegalStateException("Playlist not found")
    }

    override suspend fun createPlaylist(name: String, songId: String?): Result<Playlist> = runCatching {
        val response = api.createPlaylist(name, songId)
        val body = response.subsonicResponse
        if (body.status != "ok") throw IllegalStateException(body.error?.message ?: "Error creating playlist")
        // Note: createPlaylist might not return the playlist object in some Subsonic versions, 
        // but it usually returns a minimal response. We might need to fetch all playlists again or guess.
        // However, most implementations return the created playlist.
        body.playlist?.toDomain() ?: throw IllegalStateException("Playlist not returned after creation")
    }

    override suspend fun addSongToPlaylist(playlistId: String, songId: String): Result<Unit> = runCatching {
        val response = api.updatePlaylist(playlistId, songIdsToAdd = listOf(songId))
        val body = response.subsonicResponse
        if (body.status != "ok") throw IllegalStateException(body.error?.message ?: "Error adding song to playlist")
    }

    override suspend fun removeSongFromPlaylist(playlistId: String, songIndex: Int): Result<Unit> = runCatching {
        val response = api.updatePlaylist(playlistId, songIndicesToRemove = listOf(songIndex))
        val body = response.subsonicResponse
        if (body.status != "ok") throw IllegalStateException(body.error?.message ?: "Error removing song from playlist")
    }

    override suspend fun findArtistByName(name: String): Result<Artist?> = runCatching {
        val response = api.search(name)
        val body = response.subsonicResponse
        body.searchResult3?.artist
            ?.map { it.toDomain() }
            ?.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }

    override suspend fun findSongsByArtistName(name: String): Result<List<Song>> = runCatching {
        val response = api.searchSongs(name)
        val body = response.subsonicResponse
        body.searchResult3?.song
            ?.map { it.toDomain() }
            ?.filter { song -> song.artists.any { it.name.equals(name, ignoreCase = true) } }
            ?: emptyList()
    }

    override suspend fun getSongsForArtist(
        artistName: String,
        albumIds: List<String>,
    ): Result<List<Song>> = runCatching {
        coroutineScope {
            val albumSongs = albumIds.map { albumId ->
                async { getAlbum(albumId).getOrNull()?.songs ?: emptyList() }
            }
            val creditedSongs = async {
                if (artistName.isBlank()) emptyList()
                else findSongsByArtistName(artistName).getOrDefault(emptyList())
            }
            val fromAlbums = albumSongs.awaitAll().flatten()
            val knownIds = fromAlbums.mapTo(mutableSetOf()) { it.id }
            fromAlbums + creditedSongs.await().filterNot { it.id in knownIds }
        }
    }

    override suspend fun search(query: String): Result<SearchResults> = runCatching {
        val response = api.search(query)
        val body = response.subsonicResponse
        val result = body.searchResult3
        SearchResults(
            artists = result?.artist?.map { it.toDomain() } ?: emptyList(),
            albums = result?.album?.map { it.toDomain() }?.distinctBy { it.id.ifEmpty { "${it.name}_${it.artist}" } } ?: emptyList(),
            songs = result?.song?.map { it.toDomain() } ?: emptyList(),
        )
    }

    override suspend fun startScan(): Result<Boolean> = runCatching { api.startScan() }

    override fun getStreamUrl(songId: String): String =
        if (songId.startsWith(YOUTUBE_SONG_ID_PREFIX)) {
            YOUTUBE_STREAM_SCHEME + songId.removePrefix(YOUTUBE_SONG_ID_PREFIX)
        } else {
            api.getStreamUrl(songId)
        }

    // YouTube Music songs carry their thumbnail URL as cover art id
    override fun getCoverArtUrl(coverArtId: String, size: Int): String =
        if (coverArtId.startsWith("https://")) {
            YouTubeMusicApi.resizeThumbnail(coverArtId, size)
        } else {
            api.getCoverArtUrl(coverArtId, size)
        }
}
