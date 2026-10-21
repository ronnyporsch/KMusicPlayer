package de.ronnyporsch.open_rgb.kmusicplayer.ui.screen.playlist

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
import de.ronnyporsch.open_rgb.kmusicplayer.ui.theme.SpotifyGreen

@Composable
fun PlaylistDetailScreen(
    viewModel: PlaylistDetailViewModel,
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
            val playlist = state.playlist ?: return

            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = 100.dp),
                modifier = Modifier.onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        val playlist = state.playlist ?: return@onKeyEvent false
                        val currentIndex = playlist.songs.indexOfFirst { it.id == state.selectedSongId }
                        when (event.key) {
                            Key.DirectionDown -> {
                                if (currentIndex < playlist.songs.size - 1) {
                                    viewModel.onEvent(PlaylistDetailEvent.SongClicked(playlist.songs[currentIndex + 1], currentIndex + 1))
                                    true
                                } else false
                            }
                            Key.DirectionUp -> {
                                if (currentIndex > 0) {
                                    viewModel.onEvent(PlaylistDetailEvent.SongClicked(playlist.songs[currentIndex - 1], currentIndex - 1))
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
                            url = coverArt.getUrl(playlist.coverArtId),
                            modifier = Modifier.size(200.dp),
                            placeholderFontSize = 60.sp,
                        )

                        Spacer(Modifier.height(16.dp))

                        Text(
                            text = playlist.name,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )

                        Text(
                            text = "Playlist",
                            style = MaterialTheme.typography.bodyMedium,
                        )

                        Text(
                            text = "${playlist.songCount} songs",
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
                            onClick = { viewModel.onEvent(PlaylistDetailEvent.PlayAll) },
                            modifier = Modifier.weight(1f).height(44.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
                            shape = RoundedCornerShape(0.dp),
                        ) {
                            Text("\u25B6  Play", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { viewModel.onEvent(PlaylistDetailEvent.ShuffleAll) },
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(0.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onBackground),
                        ) {
                            Text("\u21C6  Shuffle", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                itemsIndexed(playlist.songs, key = { _, song -> song.id }) { index, song ->
                    SongRow(
                        song = song,
                        index = index,
                        onArtistClick = { name -> viewModel.onEvent(PlaylistDetailEvent.ArtistClicked(name)) },
                        onAlbumClick = { albumId -> viewModel.onEvent(PlaylistDetailEvent.AlbumClicked(albumId)) },
                        onSongClick = { viewModel.onEvent(PlaylistDetailEvent.SongClicked(song, index)) },
                        onSongDoubleClick = { viewModel.onEvent(PlaylistDetailEvent.SongDoubleClicked(song, index)) },
                        isSelected = state.selectedSongId == song.id,
                        isPlaying = currentSongId == song.id,
                        showAlbum = true
                    )
                }
            }
        }
    }
}

