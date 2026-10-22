package de.ronnyporsch.open_rgb.kmusicplayer.ui.screen.albums

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.Song
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.formatDuration
import de.ronnyporsch.open_rgb.kmusicplayer.ui.CoverArtUrlProvider
import de.ronnyporsch.open_rgb.kmusicplayer.ui.LocalCoverArtUrlProvider
import de.ronnyporsch.open_rgb.kmusicplayer.ui.components.ClickableArtistText
import de.ronnyporsch.open_rgb.kmusicplayer.ui.components.CoverArtImage
import de.ronnyporsch.open_rgb.kmusicplayer.ui.components.SongRow
import de.ronnyporsch.open_rgb.kmusicplayer.ui.components.contextMenuSongs
import de.ronnyporsch.open_rgb.kmusicplayer.ui.theme.SpotifyGreen

@Composable
fun AlbumDetailScreen(
    viewModel: AlbumDetailViewModel,
    onBack: () -> Unit,
    currentSongId: String? = null,
) {
    val state by viewModel.state.collectAsState()
    val coverArt = LocalCoverArtUrlProvider.current
    val listState = rememberLazyListState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Text("\u2190", fontSize = 20.sp, color = MaterialTheme.colorScheme.onBackground)
            }
        }

        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = SpotifyGreen)
            }
        } else {
            val album = state.album ?: return

            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = 100.dp),
                modifier = Modifier.onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        val album = state.album ?: return@onKeyEvent false
                        val currentIndex = album.songs.indexOfFirst { it.id in state.selectedSongIds }
                        when (event.key) {
                            Key.DirectionDown -> {
                                if (currentIndex < album.songs.size - 1) {
                                    viewModel.onEvent(AlbumDetailEvent.SongClicked(album.songs[currentIndex + 1], currentIndex + 1))
                                    true
                                } else false
                            }
                            Key.DirectionUp -> {
                                if (currentIndex > 0) {
                                    viewModel.onEvent(AlbumDetailEvent.SongClicked(album.songs[currentIndex - 1], currentIndex - 1))
                                    true
                                } else false
                            }
                            else -> false
                        }
                    } else false
                }
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CoverArtImage(
                            url = coverArt.getUrl(album.coverArtId),
                            modifier = Modifier.size(200.dp),
                            placeholderFontSize = 60.sp,
                        )

                        Spacer(Modifier.height(16.dp))

                        Text(
                            text = album.name,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )

                        ClickableArtistText(
                            artists = album.artists,
                            fallback = album.artist,
                            onArtistClick = { name -> viewModel.onEvent(AlbumDetailEvent.ArtistClicked(name)) },
                            style = MaterialTheme.typography.bodyMedium,
                        )

                        val albumInfo = buildString {
                            append("Album")
                            if (album.year != null) {
                                append(" \u2022 ")
                                append(album.year)
                            }
                            if (album.songCount > 0) {
                                append(" \u2022 ")
                                append("${album.songCount} song${if (album.songCount != 1) "s" else ""}")
                            }
                        }
                        Text(
                            text = albumInfo,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Button(
                            onClick = { viewModel.onEvent(AlbumDetailEvent.PlayAll) },
                            modifier = Modifier.weight(1f).height(44.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
                            shape = RoundedCornerShape(0.dp),
                        ) {
                            Text("\u25B6  Play", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { viewModel.onEvent(AlbumDetailEvent.ShuffleAll) },
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(0.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onBackground),
                        ) {
                            Text("\u21C6  Shuffle", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                itemsIndexed(album.songs, key = { _, song -> song.id }) { index, song ->
                    SongRow(
                        song = song,
                        index = index,
                        onArtistClick = { name -> viewModel.onEvent(AlbumDetailEvent.ArtistClicked(name)) },
                        onSongClick = { viewModel.onEvent(AlbumDetailEvent.SongClicked(song, index)) },
                        onSongDoubleClick = { viewModel.onEvent(AlbumDetailEvent.SongDoubleClicked(song, index)) },
                        onSongCtrlClick = { viewModel.onEvent(AlbumDetailEvent.SongSelectionToggled(song)) },
                        contextMenuSongs = { album.songs.contextMenuSongs(song, state.selectedSongIds) },
                        isSelected = song.id in state.selectedSongIds,
                        isPlaying = currentSongId == song.id,
                    )
                }
            }
        }
    }
}

