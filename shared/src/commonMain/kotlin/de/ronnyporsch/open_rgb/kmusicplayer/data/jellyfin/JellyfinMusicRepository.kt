package de.ronnyporsch.open_rgb.kmusicplayer.data.jellyfin

import de.ronnyporsch.open_rgb.kmusicplayer.data.repository.MusicRepository
import de.ronnyporsch.open_rgb.kmusicplayer.data.repository.SearchResults
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.*
import de.ronnyporsch.open_rgb.kmusicplayer.getPlatform
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class JellyfinMusicRepository(
    private val api: JellyfinApiService,
) : MusicRepository {

    override val isConfigured: Boolean get() = api.isConfigured

    override suspend fun ping(): Boolean = api.ping()

    override suspend fun getArtists(): Result<List<Artist>> = runCatching {
        api.getAlbumArtists(mapOf("sortBy" to "SortName", "fields" to "ChildCount", "enableUserData" to false))
            .items.map { it.toArtist() }
    }

    override suspend fun getArtistAlbums(artistId: String): Result<List<Album>> = runCatching {
        albums("albumArtistIds" to artistId)
    }

    override suspend fun getAppearsOnAlbums(artistName: String, ownAlbumIds: List<String>): Result<List<Album>> = runCatching {
        val artist = findArtistByName(artistName).getOrNull() ?: return@runCatching emptyList()
        // Albums with tracks credited to the artist where they aren't an album artist
        albums("contributingArtistIds" to artist.id)
            .filterNot { album -> album.id in ownAlbumIds || album.artist.equals(artistName, ignoreCase = true) }
    }

    override suspend fun getArtistInfo(artistId: String): Result<ArtistInfo> = runCatching {
        val artist = api.getItem(artistId)
        val imageType = when {
            "Primary" in artist.imageTags -> "Primary"
            artist.backdropImageTags.isNotEmpty() -> "Backdrop"
            else -> null
        }
        ArtistInfo(
            biography = artist.overview?.takeIf { it.isNotBlank() },
            largeImageUrl = imageType?.let { api.getImageUrl(artistId, null, it) },
        )
    }

    override suspend fun getAlbum(albumId: String): Result<Album> = runCatching {
        coroutineScope {
            val songs = async { songs("parentId" to albumId, "sortBy" to "ParentIndexNumber,IndexNumber,SortName") }
            api.getItem(albumId).toAlbum().copy(songs = songs.await())
        }
    }

    override suspend fun getPlaylists(): Result<List<Playlist>> = runCatching {
        api.getItems(
            mapOf(
                "includeItemTypes" to "Playlist",
                "recursive" to true,
                "sortBy" to "SortName",
                "fields" to "ChildCount",
                "enableUserData" to false,
            )
        ).items
            .filter { it.mediaType == null || it.mediaType == "Audio" || it.mediaType == "Unknown" }
            .map { it.toPlaylist() }
    }

    override suspend fun getPlaylist(playlistId: String): Result<Playlist> = runCatching {
        coroutineScope {
            val songs = async { api.getPlaylistItems(playlistId).items.map { it.toSong() } }
            val playlist = api.getItem(playlistId).toPlaylist()
            val loaded = songs.await()
            playlist.copy(songs = loaded, songCount = loaded.size, duration = loaded.sumOf { it.duration })
        }
    }

    override suspend fun createPlaylist(name: String, songIds: List<String>): Result<Playlist> = runCatching {
        val created = api.createPlaylist(name, songIds)
        getPlaylist(created.id).getOrThrow()
    }

    override suspend fun addSongsToPlaylist(playlistId: String, songIds: List<String>): Result<Unit> = runCatching {
        api.addToPlaylist(playlistId, songIds)
    }

    override suspend fun removeSongFromPlaylist(playlistId: String, songIndex: Int): Result<Unit> = runCatching {
        // Jellyfin removes entries by their playlist entry id, not their position
        val entry = api.getPlaylistItems(playlistId).items.getOrNull(songIndex)?.playlistItemId
            ?: throw IllegalStateException("Song not found in playlist")
        api.removeFromPlaylist(playlistId, listOf(entry))
    }

    override suspend fun findArtistByName(name: String): Result<Artist?> = runCatching {
        if (name.isBlank()) return@runCatching null
        api.getArtists(mapOf("searchTerm" to name, "limit" to 20, "fields" to "ChildCount", "enableUserData" to false))
            .items.firstOrNull { it.name.equals(name, ignoreCase = true) }?.toArtist()
    }

    override suspend fun findSongsByArtistName(name: String): Result<List<Song>> = runCatching {
        val artist = findArtistByName(name).getOrNull() ?: return@runCatching emptyList()
        songs("artistIds" to artist.id, "sortBy" to "Album,ParentIndexNumber,IndexNumber")
    }

    override suspend fun getSongsForArtist(artistName: String, albumIds: List<String>): Result<List<Song>> = runCatching {
        coroutineScope {
            val albumSongs = async {
                if (albumIds.isEmpty()) emptyList()
                else songs("albumIds" to albumIds.joinToString(","), "sortBy" to "ProductionYear,Album,ParentIndexNumber,IndexNumber")
            }
            val creditedSongs = async { findSongsByArtistName(artistName).getOrDefault(emptyList()) }
            val fromAlbums = albumSongs.await()
            val knownIds = fromAlbums.mapTo(mutableSetOf()) { it.id }
            fromAlbums + creditedSongs.await().filterNot { it.id in knownIds }
        }
    }

    override suspend fun search(query: String): Result<SearchResults> = runCatching {
        coroutineScope {
            val artists = async {
                api.getArtists(mapOf("searchTerm" to query, "limit" to 10, "fields" to "ChildCount", "enableUserData" to false))
                    .items.map { it.toArtist() }
            }
            val albums = async { albums("searchTerm" to query, "limit" to 10, "sortBy" to "SortName") }
            val songs = async { songs("searchTerm" to query, "limit" to 20, "sortBy" to "SortName") }
            SearchResults(artists = artists.await(), albums = albums.await(), songs = songs.await())
        }
    }

    override suspend fun startScan(): Result<Boolean> = runCatching { api.refreshLibrary() }

    override suspend fun getLyrics(song: Song): Result<Lyrics?> = runCatching {
        api.getLyrics(song.id)?.toLyrics()?.let { return@runCatching it }
        // Not fetched yet (lyrics providers normally run during library scans): search them now, which
        // fails without the lyric management permission
        val results = try {
            api.searchRemoteLyrics(song.id).filter { it.lyrics.lyrics.isNotEmpty() }
        } catch (e: JellyfinApiException) {
            if (e.status == HttpStatusCode.Forbidden || e.status == HttpStatusCode.Unauthorized) return@runCatching null
            throw e
        }
        val best = results.firstOrNull { it.lyrics.toLyrics()?.isSynced == true } ?: results.firstOrNull()
            ?: return@runCatching null
        val saved = runCatching { api.downloadRemoteLyrics(song.id, best.id) }.getOrNull()
        saved?.toLyrics() ?: best.lyrics.toLyrics()
    }

    override fun getStreamUrl(songId: String): String = api.getStreamUrl(songId, getPlatform().audioContainers)

    override fun getDownloadUrl(songId: String): String = api.getOriginalAudioUrl(songId)

    override fun getCoverArtUrl(coverArtId: String, size: Int): String = api.getImageUrl(coverArtId, size)

    private suspend fun albums(vararg params: Pair<String, Any>): List<Album> =
        api.getItems(
            mapOf(
                "includeItemTypes" to "MusicAlbum",
                "recursive" to true,
                "sortBy" to "ProductionYear,SortName",
                "fields" to "ChildCount",
                "enableUserData" to false,
            ) + params
        ).items.map { it.toAlbum() }

    private suspend fun songs(vararg params: Pair<String, Any>): List<Song> =
        api.getItems(
            mapOf(
                "includeItemTypes" to "Audio",
                "recursive" to true,
                "fields" to "Genres",
                "enableUserData" to false,
            ) + params
        ).items.map { it.toSong() }
}

private fun LyricDto.toLyrics(): Lyrics? =
    lyrics.takeIf { it.isNotEmpty() }?.let { lines ->
        Lyrics(lines.map { LyricLine(it.text, it.start?.let { ticks -> ticks / 10_000L }) })
    }

private fun BaseItemDto.ticksToSeconds(): Int = ((runTimeTicks ?: 0L) / 10_000_000L).toInt()

private fun NameIdPairDto.toArtist() = Artist(id = id, name = name)

private fun BaseItemDto.toArtist() = Artist(
    id = id,
    name = name,
    albumCount = albumCount ?: childCount ?: 0,
    coverArtId = id.takeIf { "Primary" in imageTags },
)

private fun BaseItemDto.toAlbum() = Album(
    id = id,
    name = name,
    artist = albumArtist ?: albumArtists.joinToString(", ") { it.name },
    artistId = albumArtists.firstOrNull()?.id.orEmpty(),
    artists = albumArtists.map { it.toArtist() },
    coverArtId = id.takeIf { "Primary" in imageTags },
    songCount = childCount ?: 0,
    year = productionYear,
    duration = ticksToSeconds(),
)

private fun BaseItemDto.toSong() = Song(
    id = id,
    title = name,
    artist = artistItems.joinToString(", ") { it.name }.ifBlank { albumArtist.orEmpty() },
    artistId = artistItems.firstOrNull()?.id.orEmpty(),
    artists = artistItems.map { it.toArtist() },
    album = album.orEmpty(),
    albumId = albumId.orEmpty(),
    // Songs usually only have the album's cover
    coverArtId = when {
        "Primary" in imageTags -> id
        albumPrimaryImageTag != null -> albumId
        else -> null
    },
    duration = ticksToSeconds(),
    track = indexNumber,
    discNumber = parentIndexNumber,
    year = productionYear,
    genre = genres.firstOrNull(),
)

private fun BaseItemDto.toPlaylist() = Playlist(
    id = id,
    name = name,
    songCount = childCount ?: 0,
    duration = ticksToSeconds(),
    coverArtId = id.takeIf { "Primary" in imageTags },
)
