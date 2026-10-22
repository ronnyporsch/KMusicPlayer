package de.ronnyporsch.open_rgb.kmusicplayer.data.jellyfin

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AuthenticateByNameRequest(
    @SerialName("Username") val username: String,
    @SerialName("Pw") val password: String,
)

@Serializable
data class AuthenticationResultDto(
    @SerialName("AccessToken") val accessToken: String,
    @SerialName("User") val user: UserDto,
)

@Serializable
data class UserDto(
    @SerialName("Id") val id: String,
    @SerialName("Name") val name: String = "",
)

@Serializable
data class ItemsResultDto(
    @SerialName("Items") val items: List<BaseItemDto> = emptyList(),
    @SerialName("TotalRecordCount") val totalRecordCount: Int = 0,
)

@Serializable
data class NameIdPairDto(
    @SerialName("Name") val name: String = "",
    @SerialName("Id") val id: String = "",
)

/** The subset of Jellyfin's BaseItemDto used for artists, albums, songs and playlists. */
@Serializable
data class BaseItemDto(
    @SerialName("Id") val id: String,
    @SerialName("Name") val name: String = "",
    @SerialName("Type") val type: String = "",
    @SerialName("MediaType") val mediaType: String? = null,
    @SerialName("Overview") val overview: String? = null,
    @SerialName("Album") val album: String? = null,
    @SerialName("AlbumId") val albumId: String? = null,
    @SerialName("AlbumPrimaryImageTag") val albumPrimaryImageTag: String? = null,
    @SerialName("AlbumArtist") val albumArtist: String? = null,
    @SerialName("AlbumArtists") val albumArtists: List<NameIdPairDto> = emptyList(),
    @SerialName("ArtistItems") val artistItems: List<NameIdPairDto> = emptyList(),
    @SerialName("ImageTags") val imageTags: Map<String, String> = emptyMap(),
    @SerialName("BackdropImageTags") val backdropImageTags: List<String> = emptyList(),
    /** In units of 100 ns. */
    @SerialName("RunTimeTicks") val runTimeTicks: Long? = null,
    @SerialName("ProductionYear") val productionYear: Int? = null,
    @SerialName("IndexNumber") val indexNumber: Int? = null,
    @SerialName("ParentIndexNumber") val parentIndexNumber: Int? = null,
    @SerialName("ChildCount") val childCount: Int? = null,
    @SerialName("AlbumCount") val albumCount: Int? = null,
    @SerialName("Genres") val genres: List<String> = emptyList(),
    /** Identifies the entry within a playlist (the same song can be in it more than once). */
    @SerialName("PlaylistItemId") val playlistItemId: String? = null,
)

@Serializable
data class CreatePlaylistDto(
    @SerialName("Name") val name: String,
    @SerialName("Ids") val ids: List<String>,
    @SerialName("UserId") val userId: String,
    @SerialName("MediaType") val mediaType: String = "Audio",
)

@Serializable
data class PlaylistCreationResultDto(
    @SerialName("Id") val id: String,
)

@Serializable
data class LyricDto(
    @SerialName("Lyrics") val lyrics: List<LyricLineDto> = emptyList(),
)

@Serializable
data class LyricLineDto(
    @SerialName("Text") val text: String = "",
    /** In units of 100 ns, null for unsynced lyrics. */
    @SerialName("Start") val start: Long? = null,
)

/** A lyrics search result of a lyrics provider plugin such as LrcLib. */
@Serializable
data class RemoteLyricInfoDto(
    @SerialName("Id") val id: String,
    @SerialName("ProviderName") val providerName: String = "",
    @SerialName("Lyrics") val lyrics: LyricDto = LyricDto(),
)

// KMusicPlayer Jellyfin plugin (see jellyfin-plugin/)

@Serializable
data class PluginInfoDto(
    @SerialName("Version") val version: String = "",
    @SerialName("YtDlpAvailable") val ytDlpAvailable: Boolean = false,
    @SerialName("YtDlpVersion") val ytDlpVersion: String? = null,
    @SerialName("CanDownload") val canDownload: Boolean = false,
    @SerialName("DownloadDirectory") val downloadDirectory: String? = null,
    @SerialName("Warning") val warning: String? = null,
)

@Serializable
data class YouTubeArtistDto(
    /** YouTube Music channel id, empty for artists without an artist page. */
    @SerialName("Id") val id: String = "",
    @SerialName("Name") val name: String = "",
)

/** A YouTube Music search result of the plugin; sent back as is to download the track. */
@Serializable
data class YouTubeSongDto(
    @SerialName("VideoId") val videoId: String,
    @SerialName("Title") val title: String = "",
    @SerialName("Artist") val artist: String = "",
    @SerialName("Artists") val artists: List<YouTubeArtistDto> = emptyList(),
    @SerialName("Album") val album: String = "",
    @SerialName("AlbumId") val albumId: String = "",
    @SerialName("ThumbnailUrl") val thumbnailUrl: String? = null,
    /** In seconds. */
    @SerialName("Duration") val duration: Int = 0,
)

@Serializable
data class DownloadJobDto(
    @SerialName("Id") val id: String,
    @SerialName("VideoId") val videoId: String = "",
    @SerialName("Title") val title: String = "",
    /** Queued, Downloading, Importing, Completed or Failed. */
    @SerialName("State") val state: String = "",
    @SerialName("Message") val message: String? = null,
    @SerialName("Path") val path: String? = null,
    @SerialName("ItemId") val itemId: String? = null,
) {
    val isFinished: Boolean get() = state == "Completed" || state == "Failed"
}

@Serializable
data class AudioStreamInfoDto(
    @SerialName("Codec") val codec: String? = null,
    @SerialName("BitRate") val bitRate: Int? = null,
)
