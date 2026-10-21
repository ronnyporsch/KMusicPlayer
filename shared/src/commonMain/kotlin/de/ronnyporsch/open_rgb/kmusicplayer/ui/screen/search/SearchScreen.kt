package de.ronnyporsch.open_rgb.kmusicplayer.ui.screen.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.Album
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.Artist
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.Song
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.formatDuration
import de.ronnyporsch.open_rgb.kmusicplayer.ui.LocalCoverArtUrlProvider
import de.ronnyporsch.open_rgb.kmusicplayer.ui.components.ArtistImage
import de.ronnyporsch.open_rgb.kmusicplayer.ui.components.ClickableArtistText
import de.ronnyporsch.open_rgb.kmusicplayer.ui.components.CoverArtImage
import de.ronnyporsch.open_rgb.kmusicplayer.ui.components.SongRow
import de.ronnyporsch.open_rgb.kmusicplayer.ui.components.TooltipIconButton
import de.ronnyporsch.open_rgb.kmusicplayer.ui.theme.SpotifyGreen
import de.ronnyporsch.open_rgb.kmusicplayer.ui.theme.SpotifyLightGray

@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onBack: () -> Unit,
    currentSongId: String? = null,
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {

        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = SpotifyGreen)
            }
        } else if (!state.hasSearched) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (state.youTubeAvailable) "Search your music library and YouTube Music" else "Search your music library", style = MaterialTheme.typography.bodyMedium, color = SpotifyLightGray)
            }
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = 100.dp),
                modifier = Modifier.onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        // Library songs and YouTube Music songs form one navigable list
                        val allSongs = state.songs + state.youTubeSongs
                        val currentIndex = allSongs.indexOfFirst { it.id == state.selectedSongId }
                        val targetIndex = when (event.key) {
                            Key.DirectionDown -> currentIndex + 1
                            Key.DirectionUp -> currentIndex - 1
                            else -> return@onKeyEvent false
                        }
                        if (currentIndex >= 0 && targetIndex in allSongs.indices) {
                            val song = allSongs[targetIndex]
                            viewModel.onEvent(
                                if (targetIndex < state.songs.size) {
                                    SearchEvent.SongClicked(song, targetIndex)
                                } else {
                                    SearchEvent.YouTubeSongClicked(song, targetIndex - state.songs.size)
                                }
                            )
                            true
                        } else false
                    } else false
                }
            ) {
                if (state.artists.isNotEmpty()) {
                    item {
                        Text("Artists", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                    items(state.artists, key = { "artist-${it.id}" }) { artist ->
                        SearchArtistRow(artist) {
                            viewModel.onEvent(SearchEvent.ArtistClicked(artist))
                        }
                    }
                }

                if (state.albums.isNotEmpty()) {
                    item {
                        Text("Albums", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                    items(state.albums, key = { "album-${it.id}" }) { album ->
                        SearchAlbumRow(
                            album = album,
                            onArtistClick = { name -> viewModel.onEvent(SearchEvent.ArtistNameClicked(name)) },
                            onClick = { viewModel.onEvent(SearchEvent.AlbumClicked(album)) },
                        )
                    }
                }

                if (state.songs.isNotEmpty()) {
                    item {
                        Text("Songs", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                    itemsIndexed(state.songs, key = { _, song -> "song-${song.id}" }) { index, song ->
                        SongRow(
                            song = song,
                            onArtistClick = { name -> viewModel.onEvent(SearchEvent.ArtistNameClicked(name)) },
                            onSongClick = { viewModel.onEvent(SearchEvent.SongClicked(song, index)) },
                            onSongDoubleClick = { viewModel.onEvent(SearchEvent.SongDoubleClicked(song, index)) },
                            isSelected = state.selectedSongId == song.id,
                            isPlaying = currentSongId == song.id,
                            showAlbum = true
                        )
                    }
                }

                if (state.youTubeAvailable) {
                    item {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("YouTube Music", style = MaterialTheme.typography.titleLarge)
                            if (state.isYouTubeLoading) {
                                Spacer(Modifier.width(12.dp))
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = SpotifyGreen,
                                    strokeWidth = 2.dp,
                                )
                            }
                        }
                    }
                    state.youTubeError?.let { error ->
                        item {
                            Text(
                                error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    }
                    if (!state.isYouTubeLoading && state.youTubeError == null && state.youTubeSongs.isEmpty()) {
                        item {
                            Text(
                                "No results on YouTube Music",
                                style = MaterialTheme.typography.bodySmall,
                                color = SpotifyLightGray,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    }
                    itemsIndexed(state.youTubeSongs, key = { _, song -> "yt-${song.id}" }) { index, song ->
                        val download = state.downloads[song.id]
                        Column {
                            SongRow(
                                song = song,
                                onArtistClick = { name -> viewModel.onEvent(SearchEvent.ArtistNameClicked(name)) },
                                onSongClick = { viewModel.onEvent(SearchEvent.YouTubeSongClicked(song, index)) },
                                onSongDoubleClick = { viewModel.onEvent(SearchEvent.YouTubeSongDoubleClicked(song, index)) },
                                isSelected = state.selectedSongId == song.id,
                                isPlaying = currentSongId == song.id,
                                showAlbum = true,
                                trailingContent = {
                                    state.youTubeStreamInfo[song.id]?.let { info ->
                                        Text(
                                            listOfNotNull(info.codec, info.bitRate?.let { "$it kbps" }).joinToString(" \u2022 "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(end = 4.dp),
                                        )
                                    }
                                    DownloadButton(download) { viewModel.onEvent(SearchEvent.DownloadYouTubeSong(song)) }
                                },
                            )
                            val message = when (download) {
                                is DownloadStatus.Done -> download.message
                                is DownloadStatus.Failed -> download.message
                                else -> null
                            }
                            if (message != null) {
                                Text(
                                    message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (download is DownloadStatus.Failed) MaterialTheme.colorScheme.error else SpotifyLightGray,
                                    modifier = Modifier.padding(start = 68.dp, end = 16.dp, bottom = 8.dp),
                                )
                            }
                        }
                    }
                }

                if (state.artists.isEmpty() && state.albums.isEmpty() && state.songs.isEmpty() && !state.youTubeAvailable) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("No results found", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadButton(status: DownloadStatus?, onDownload: () -> Unit) {
    when (status) {
        DownloadStatus.InProgress -> Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = SpotifyGreen, strokeWidth = 2.dp)
        }
        is DownloadStatus.Done -> TooltipIconButton(
            onClick = {},
            icon = Icons.Default.CheckCircle,
            contentDescription = "Added to library",
            tint = SpotifyGreen,
        )
        is DownloadStatus.Failed -> TooltipIconButton(
            onClick = onDownload,
            icon = Icons.Default.ErrorOutline,
            contentDescription = "Download failed, click to retry",
            tint = MaterialTheme.colorScheme.error,
        )
        null -> TooltipIconButton(
            onClick = onDownload,
            icon = Icons.Default.Download,
            contentDescription = "Download to library",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SearchArtistRow(artist: Artist, onClick: () -> Unit) {
    val coverArt = LocalCoverArtUrlProvider.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtistImage(
            url = coverArt.getUrl(artist.coverArtId),
            artistName = artist.name,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(artist.name, style = MaterialTheme.typography.bodyLarge)
            Text("Artist", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SearchAlbumRow(album: Album, onArtistClick: (String) -> Unit, onClick: () -> Unit) {
    val coverArt = LocalCoverArtUrlProvider.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverArtImage(
            url = coverArt.getUrl(album.coverArtId),
            modifier = Modifier.size(48.dp),
            cornerRadius = 0.dp,
            placeholderFontSize = 18.sp,
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(album.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            ClickableArtistText(
                artists = album.artists,
                fallback = album.artist,
                onArtistClick = onArtistClick,
                prefix = "Album \u2022 ",
            )
        }
    }
}

