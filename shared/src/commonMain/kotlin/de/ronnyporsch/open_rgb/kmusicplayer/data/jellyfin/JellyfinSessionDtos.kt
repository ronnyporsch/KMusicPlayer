package de.ronnyporsch.open_rgb.kmusicplayer.data.jellyfin

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@OptIn(ExperimentalSerializationApi::class)
/** What this client can do when other clients control it. */
@Serializable
data class ClientCapabilitiesDto(
    @SerialName("PlayableMediaTypes") val playableMediaTypes: List<String>,
    /** Names of Jellyfin's GeneralCommandType. */
    @SerialName("SupportedCommands") val supportedCommands: List<String>,
    @SerialName("SupportsMediaControl") val supportsMediaControl: Boolean,
    @EncodeDefault @SerialName("SupportsPersistentIdentifier") val supportsPersistentIdentifier: Boolean = true,
)

@Serializable
data class QueueItemDto(
    @SerialName("Id") val id: String,
    @SerialName("PlaylistItemId") val playlistItemId: String,
)

@OptIn(ExperimentalSerializationApi::class)
/** Jellyfin's PlaybackStartInfo, PlaybackProgressInfo and PlaybackStopInfo, which share these fields. */
@Serializable
data class PlaybackReportDto(
    /** A library item; the server rejects anything else. */
    @SerialName("ItemId") val itemId: String,
    @SerialName("PositionTicks") val positionTicks: Long,
    @EncodeDefault @SerialName("IsPaused") val isPaused: Boolean = false,
    @EncodeDefault @SerialName("CanSeek") val canSeek: Boolean = true,
    @EncodeDefault @SerialName("PlayMethod") val playMethod: String = "DirectStream",
    @SerialName("PlaySessionId") val playSessionId: String? = null,
    /** Jellyfin's RepeatMode: RepeatNone, RepeatAll or RepeatOne. */
    @EncodeDefault @SerialName("RepeatMode") val repeatMode: String = "RepeatNone",
    /** Default or Shuffle. */
    @EncodeDefault @SerialName("PlaybackOrder") val playbackOrder: String = "Default",
    /** E.g. timeupdate, pause, unpause; only for progress reports. */
    @SerialName("EventName") val eventName: String? = null,
    @SerialName("NowPlayingQueue") val nowPlayingQueue: List<QueueItemDto>? = null,
    @SerialName("PlaylistItemId") val playlistItemId: String? = null,
)

@Serializable
data class PlayerStateInfoDto(
    @SerialName("PositionTicks") val positionTicks: Long? = null,
    @SerialName("CanSeek") val canSeek: Boolean = false,
    @SerialName("IsPaused") val isPaused: Boolean = false,
    @SerialName("RepeatMode") val repeatMode: String? = null,
    @SerialName("PlaybackOrder") val playbackOrder: String? = null,
)

/** The subset of Jellyfin's SessionInfoDto used for remote control. */
@Serializable
data class SessionInfoDto(
    @SerialName("Id") val id: String,
    @SerialName("UserId") val userId: String? = null,
    @SerialName("Client") val client: String = "",
    @SerialName("DeviceId") val deviceId: String = "",
    @SerialName("DeviceName") val deviceName: String = "",
    @SerialName("SupportsRemoteControl") val supportsRemoteControl: Boolean = false,
    @SerialName("PlayableMediaTypes") val playableMediaTypes: List<String> = emptyList(),
    @SerialName("NowPlayingItem") val nowPlayingItem: BaseItemDto? = null,
    @SerialName("PlayState") val playState: PlayerStateInfoDto = PlayerStateInfoDto(),
    /** ISO 8601. */
    @SerialName("LastActivityDate") val lastActivityDate: String? = null,
)

@Serializable
data class GeneralCommandDto(
    /** One of Jellyfin's GeneralCommandType, e.g. SetRepeatMode. */
    @SerialName("Name") val name: String,
    @SerialName("Arguments") val arguments: Map<String, String> = emptyMap(),
)

/** A message on Jellyfin's /socket websocket; [data] depends on [messageType]. */
@Serializable
data class SocketMessageDto(
    @SerialName("MessageType") val messageType: String,
    @SerialName("Data") val data: JsonElement? = null,
)

/** Data of a "Play" socket message: another client wants this one to play these items. */
@Serializable
data class PlayRequestDto(
    @SerialName("ItemIds") val itemIds: List<String> = emptyList(),
    @SerialName("StartPositionTicks") val startPositionTicks: Long? = null,
    /** PlayNow, PlayNext, PlayLast, PlayInstantMix or PlayShuffle. */
    @SerialName("PlayCommand") val playCommand: String = "PlayNow",
    @SerialName("StartIndex") val startIndex: Int? = null,
)

/** Data of a "Playstate" socket message. */
@Serializable
data class PlaystateRequestDto(
    /** Stop, Pause, Unpause, NextTrack, PreviousTrack, Seek, Rewind, FastForward or PlayPause. */
    @SerialName("Command") val command: String,
    @SerialName("SeekPositionTicks") val seekPositionTicks: Long? = null,
)
