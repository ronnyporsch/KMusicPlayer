package de.ronnyporsch.open_rgb.kmusicplayer.ui.screen.albums

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.Album
import de.ronnyporsch.open_rgb.kmusicplayer.ui.LocalCoverArtUrlProvider
import de.ronnyporsch.open_rgb.kmusicplayer.ui.components.ArtistImage
import de.ronnyporsch.open_rgb.kmusicplayer.ui.components.CoverArtImage
import de.ronnyporsch.open_rgb.kmusicplayer.ui.components.ExpandableText
import de.ronnyporsch.open_rgb.kmusicplayer.ui.components.SongRow
import de.ronnyporsch.open_rgb.kmusicplayer.ui.components.contextMenuSongs
import de.ronnyporsch.open_rgb.kmusicplayer.ui.theme.SpotifyGreen
import de.ronnyporsch.open_rgb.kmusicplayer.ui.theme.SpotifyLightGray

@Composable
fun ArtistAlbumsScreen(
    viewModel: ArtistAlbumsViewModel,
    onBack: () -> Unit,
    currentSongId: String? = null,
) {
    val state by viewModel.state.collectAsState()

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
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                contentPadding = PaddingValues(bottom = 100.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        val currentIndex = state.songs.indexOfFirst { it.id in state.selectedSongIds }
                        when (event.key) {
                            Key.DirectionDown -> {
                                if (currentIndex < state.songs.size - 1) {
                                    viewModel.onEvent(ArtistAlbumsEvent.SongClicked(state.songs[currentIndex + 1], currentIndex + 1))
                                    true
                                } else false
                            }
                            Key.DirectionUp -> {
                                if (currentIndex > 0) {
                                    viewModel.onEvent(ArtistAlbumsEvent.SongClicked(state.songs[currentIndex - 1], currentIndex - 1))
                                    true
                                } else false
                            }
                            else -> false
                        }
                    } else false
                }
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ArtistHeader(
                        artistName = state.artistName,
                        imageUrl = state.largeImageUrl,
                        biography = state.biography,
                    )
                }

                if (state.albums.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = "Albums",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }

                    items(state.albums, key = { it.id }) { album ->
                        AlbumCard(album) {
                            viewModel.onEvent(ArtistAlbumsEvent.AlbumClicked(album))
                        }
                    }
                }

                if (state.appearsOnAlbums.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = "Appears on",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                        )
                    }

                    items(state.appearsOnAlbums, key = { it.id }) { album ->
                        AlbumCard(album) {
                            viewModel.onEvent(ArtistAlbumsEvent.AlbumClicked(album))
                        }
                    }
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = "Songs",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                    )
                }

                if (state.isSongsLoading) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = SpotifyGreen, modifier = Modifier.size(24.dp))
                        }
                    }
                } else if (state.songs.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Button(
                                onClick = { viewModel.onEvent(ArtistAlbumsEvent.PlayAllSongs) },
                                modifier = Modifier.weight(1f).height(44.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
                                shape = RoundedCornerShape(0.dp),
                            ) {
                                Text("\u25B6  Play All", fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = { viewModel.onEvent(ArtistAlbumsEvent.ShuffleAllSongs) },
                                modifier = Modifier.weight(1f).height(44.dp),
                                shape = RoundedCornerShape(0.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onBackground),
                            ) {
                                Text("\u21C6  Shuffle", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    itemsIndexed(
                        state.songs,
                        key = { _, song -> song.id },
                        span = { _, _ -> GridItemSpan(maxLineSpan) },
                    ) { index, song ->
                        SongRow(
                            song = song,
                            index = index,
                            onSongClick = { viewModel.onEvent(ArtistAlbumsEvent.SongClicked(song, index)) },
                            onSongDoubleClick = { viewModel.onEvent(ArtistAlbumsEvent.SongDoubleClicked(song, index)) },
                            onSongCtrlClick = { viewModel.onEvent(ArtistAlbumsEvent.SongSelectionToggled(song)) },
                            contextMenuSongs = { state.songs.contextMenuSongs(song, state.selectedSongIds) },
                            onArtistClick = { /* Already in artist view */ },
                            isSelected = song.id in state.selectedSongIds,
                            isPlaying = currentSongId == song.id,
                            showAlbum = true,
                            showArtist = false
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistHeader(
    artistName: String,
    imageUrl: String?,
    biography: String?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ArtistImage(
            url = imageUrl,
            artistName = artistName,
            modifier = Modifier.size(180.dp),
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = artistName,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )

        if (!biography.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            ExpandableText(
                text = biography,
                style = MaterialTheme.typography.bodyMedium,
                color = SpotifyLightGray,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}

@Composable
private fun AlbumCard(album: Album, onClick: () -> Unit) {
    val coverArt = LocalCoverArtUrlProvider.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(0.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        CoverArtImage(
            url = coverArt.getUrl(album.coverArtId),
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            placeholderFontSize = 40.sp,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = album.name,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Text(
            text = album.year?.toString() ?: "",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

