package de.ronnyporsch.open_rgb.kmusicplayer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.ui.input.key.*
import de.ronnyporsch.open_rgb.kmusicplayer.data.api.SubsonicApiService
import de.ronnyporsch.open_rgb.kmusicplayer.data.repository.MusicRepositoryImpl
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.ServerConfig
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.SettingsHolder
import de.ronnyporsch.open_rgb.kmusicplayer.util.generateSalt
import de.ronnyporsch.open_rgb.kmusicplayer.util.md5
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.Song
import de.ronnyporsch.open_rgb.kmusicplayer.platform.CrossfadeAudioPlayer
import de.ronnyporsch.open_rgb.kmusicplayer.platform.createAudioPlayer
import de.ronnyporsch.open_rgb.kmusicplayer.platform.createYouTubeDownloader
import de.ronnyporsch.open_rgb.kmusicplayer.ui.CoverArtUrlProvider
import de.ronnyporsch.open_rgb.kmusicplayer.ui.LocalCoverArtUrlProvider
import de.ronnyporsch.open_rgb.kmusicplayer.ui.LocalMusicRepository
import de.ronnyporsch.open_rgb.kmusicplayer.ui.navigation.Navigator
import de.ronnyporsch.open_rgb.kmusicplayer.ui.navigation.Screen
import de.ronnyporsch.open_rgb.kmusicplayer.ui.screen.albums.*
import de.ronnyporsch.open_rgb.kmusicplayer.ui.screen.library.*
import de.ronnyporsch.open_rgb.kmusicplayer.ui.screen.login.*
import de.ronnyporsch.open_rgb.kmusicplayer.ui.screen.player.*
import de.ronnyporsch.open_rgb.kmusicplayer.ui.screen.playlist.*
import de.ronnyporsch.open_rgb.kmusicplayer.ui.screen.search.*
import de.ronnyporsch.open_rgb.kmusicplayer.ui.screen.settings.*
import de.ronnyporsch.open_rgb.kmusicplayer.ui.theme.KMusicPlayerTheme
import de.ronnyporsch.open_rgb.kmusicplayer.ui.components.onMouseNavigation
import de.ronnyporsch.open_rgb.kmusicplayer.ui.theme.SpotifyGreen
import de.ronnyporsch.open_rgb.kmusicplayer.ui.theme.SpotifyLightGray
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun App() {
    val apiService = remember { SubsonicApiService() }
    val repository = remember { MusicRepositoryImpl(apiService) }
    val settingsHolder = remember { SettingsHolder() }
    val crossfadePlayer = remember {
        CrossfadeAudioPlayer(createAudioPlayer(), createAudioPlayer())
    }
    val navigator = remember { Navigator() }
    val playerViewModel = remember { PlayerViewModel(crossfadePlayer, repository, settingsHolder) }
    val libraryViewModel = remember { LibraryViewModel(repository) }
    val youTubeDownloader = remember { createYouTubeDownloader() }
    val searchViewModel = remember { SearchViewModel(repository, settingsHolder, youTubeDownloader) }
    val coverArtUrlProvider = remember { CoverArtUrlProvider(repository) }

    LaunchedEffect(youTubeDownloader) {
        val downloader = youTubeDownloader ?: return@LaunchedEffect
        settingsHolder.settings.collect { downloader.cookiesBrowser = it.youTubeCookiesBrowser.ifBlank { null } }
    }

    // Auto-login if saved settings exist
    LaunchedEffect(Unit) {
        val saved = settingsHolder.settings.value
        if (saved.hasSavedConnection) {
            try {
                val salt = generateSalt()
                val token = md5(saved.password + salt)
                apiService.configure(
                    ServerConfig(
                        serverUrl = saved.serverUrl,
                        username = saved.username,
                        token = token,
                        salt = salt,
                    )
                )
                val success = repository.ping()
                if (success) {
                    libraryViewModel.onEvent(LibraryEvent.Refresh)
                    navigator.replaceAll(Screen.Library)
                }
            } catch (_: Exception) {
                // Auto-login failed, stay on login screen
            }
        }
    }

    LaunchedEffect(libraryViewModel) {
        libraryViewModel.effects.collectLatest { effect ->
            when (effect) {
                is LibraryEffect.NavigateToArtist ->
                    navigator.push(Screen.ArtistAlbums(effect.artistId, effect.artistName))
                is LibraryEffect.NavigateToAlbum ->
                    navigator.push(Screen.AlbumSongs(effect.albumId))
                is LibraryEffect.NavigateToPlaylist ->
                    navigator.push(Screen.PlaylistSongs(effect.playlistId))
                is LibraryEffect.PlaySongs ->
                    playSongs(playerViewModel, effect.songs, effect.startIndex, effect.shuffle)
            }
        }
    }

    LaunchedEffect(searchViewModel) {
        searchViewModel.effects.collectLatest { effect ->
            when (effect) {
                is SearchEffect.NavigateToArtist ->
                    navigator.push(Screen.ArtistAlbums(effect.artistId, effect.artistName))
                is SearchEffect.NavigateToAlbum ->
                    navigator.push(Screen.AlbumSongs(effect.albumId))
                is SearchEffect.PlaySongs ->
                    playSongs(playerViewModel, effect.songs, effect.startIndex, null)
            }
        }
    }

    KMusicPlayerTheme {
        CompositionLocalProvider(
            LocalCoverArtUrlProvider provides coverArtUrlProvider,
            LocalMusicRepository provides repository,
        ) {
        val currentScreen by navigator.currentScreen.collectAsState()
        val playerState by playerViewModel.state.collectAsState()
        val hasSong = playerState.playback.currentSong != null

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                if (currentScreen !is Screen.Login && currentScreen !is Screen.Player) {
                    val searchState by searchViewModel.state.collectAsState()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (navigator.canGoBack()) {
                            IconButton(onClick = { navigator.pop() }) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "Back",
                                    tint = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }

                        OutlinedTextField(
                            value = searchState.query,
                            onValueChange = {
                                searchViewModel.onEvent(SearchEvent.QueryChanged(it))
                                if (currentScreen !is Screen.Search) {
                                    navigator.push(Screen.Search)
                                }
                            },
                            placeholder = { Text("Search artists, albums, songs...") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = SpotifyGreen,
                                cursorColor = SpotifyGreen,
                            ),
                            shape = RoundedCornerShape(8.dp),
                        )
                    }
                }
            },
            bottomBar = {
                if (currentScreen !is Screen.Login && currentScreen !is Screen.Player) {
                    Column {
                        if (hasSong) {
                            MiniPlayer(
                                viewModel = playerViewModel,
                                onArtistClick = { name ->
                                    navigator.push(Screen.ArtistAlbums(null, name))
                                },
                                onClick = { navigator.push(Screen.Player) },
                            )
                        } else {
                            Box(Modifier.height(66.dp))
                        }
                        BottomNavigationBar(currentScreen, navigator)
                    }
                }
            },
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyUp) {
                            when (event.key) {
                                Key.MediaPlayPause -> {
                                    playerViewModel.onEvent(PlayerEvent.PlayPause)
                                    true
                                }
                                Key.MediaNext -> {
                                    playerViewModel.onEvent(PlayerEvent.Next)
                                    true
                                }
                                Key.MediaPrevious -> {
                                    playerViewModel.onEvent(PlayerEvent.Previous)
                                    true
                                }
                                else -> false
                            }
                        } else false
                    }
                    .onMouseNavigation(
                        onBack = { if (navigator.canGoBack()) navigator.pop() },
                        onForward = { if (navigator.canGoForward()) navigator.forward() }
                    ),
            ) {
                when (val screen = currentScreen) {
                    is Screen.Login -> {
                        val vm = remember { LoginViewModel(apiService, repository, settingsHolder) }
                        LaunchedEffect(vm) {
                            vm.effects.collectLatest { effect ->
                                when (effect) {
                                    LoginEffect.NavigateToLibrary -> {
                                        libraryViewModel.onEvent(LibraryEvent.Refresh)
                                        navigator.replaceAll(Screen.Library)
                                    }
                                }
                            }
                        }
                        LoginScreen(vm)
                    }

                    is Screen.Library -> {
                        LibraryScreen(
                            viewModel = libraryViewModel,
                            currentSongId = playerState.playback.currentSong?.id
                        )
                    }

                    is Screen.ArtistAlbums -> {
                        val vm = remember(screen.artistId, screen.artistName) {
                            ArtistAlbumsViewModel(screen.artistId, screen.artistName, repository)
                        }
                        LaunchedEffect(vm) {
                            vm.effects.collectLatest { effect ->
                                when (effect) {
                                    is ArtistAlbumsEffect.NavigateToAlbum ->
                                        navigator.push(Screen.AlbumSongs(effect.albumId))
                                    is ArtistAlbumsEffect.PlaySongs ->
                                        playSongs(playerViewModel, effect.songs, effect.startIndex, effect.shuffle)
                                }
                            }
                        }
                        ArtistAlbumsScreen(
                            viewModel = vm,
                            onBack = { navigator.pop() },
                            currentSongId = playerState.playback.currentSong?.id
                        )
                    }

                    is Screen.AlbumSongs -> {
                        val vm = remember(screen.albumId) {
                            AlbumDetailViewModel(screen.albumId, repository)
                        }
                        LaunchedEffect(vm) {
                            vm.effects.collectLatest { effect ->
                                when (effect) {
                                    is AlbumDetailEffect.PlaySongs ->
                                        playSongs(playerViewModel, effect.songs, effect.startIndex, effect.shuffle)
                                    is AlbumDetailEffect.NavigateToArtist ->
                                        navigator.push(Screen.ArtistAlbums(null, effect.artistName))
                                }
                            }
                        }
                        AlbumDetailScreen(
                            viewModel = vm,
                            onBack = { navigator.pop() },
                            currentSongId = playerState.playback.currentSong?.id
                        )
                    }

                    is Screen.PlaylistSongs -> {
                        val vm = remember(screen.playlistId) {
                            PlaylistDetailViewModel(screen.playlistId, repository)
                        }
                        LaunchedEffect(vm) {
                            vm.effects.collectLatest { effect ->
                                when (effect) {
                                    is PlaylistDetailEffect.PlaySongs ->
                                        playSongs(playerViewModel, effect.songs, effect.startIndex, effect.shuffle)
                                    is PlaylistDetailEffect.NavigateToArtist ->
                                        navigator.push(Screen.ArtistAlbums(null, effect.artistName))
                                    is PlaylistDetailEffect.NavigateToAlbum ->
                                        navigator.push(Screen.AlbumSongs(effect.albumId))
                                }
                            }
                        }
                        PlaylistDetailScreen(
                            viewModel = vm,
                            onBack = { navigator.pop() },
                            currentSongId = playerState.playback.currentSong?.id
                        )
                    }

                    is Screen.Player -> {
                        PlayerScreen(
                            viewModel = playerViewModel,
                            onArtistClick = { name ->
                                navigator.push(Screen.ArtistAlbums(null, name))
                            },
                            onBack = { navigator.pop() },
                        )
                    }

                    is Screen.Search -> {
                        SearchScreen(
                            viewModel = searchViewModel,
                            onBack = { navigator.pop() },
                            currentSongId = playerState.playback.currentSong?.id
                        )
                    }

                    is Screen.Settings -> {
                        val vm = remember { SettingsViewModel(apiService, repository, settingsHolder, youTubeDownloader != null) }
                        SettingsScreen(vm) { navigator.pop() }
                    }
                }
            }
        }
        }
    }
}

private fun playSongs(playerViewModel: PlayerViewModel, songs: List<Song>, startIndex: Int, shuffle: Boolean?) {
    playerViewModel.onEvent(PlayerEvent.PlaySongs(songs, startIndex, shuffle))
}

@Composable
private fun BottomNavigationBar(currentScreen: Screen, navigator: Navigator) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        NavigationBarItem(
            selected = currentScreen is Screen.Library,
            onClick = {
                if (currentScreen !is Screen.Library) {
                    navigator.replaceAll(Screen.Library)
                }
            },
            icon = {
                Icon(Icons.Default.LibraryMusic, contentDescription = null, tint = if (currentScreen is Screen.Library) SpotifyGreen else SpotifyLightGray)
            },
            label = {
                Text(
                    "Library",
                    color = if (currentScreen is Screen.Library) SpotifyGreen else SpotifyLightGray,
                    fontWeight = if (currentScreen is Screen.Library) FontWeight.Bold else FontWeight.Normal,
                )
            },
            colors = NavigationBarItemDefaults.colors(
                indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        )

        NavigationBarItem(
            selected = currentScreen is Screen.Search,
            onClick = {
                if (currentScreen !is Screen.Search) {
                    navigator.push(Screen.Search)
                }
            },
            icon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = if (currentScreen is Screen.Search) SpotifyGreen else SpotifyLightGray)
            },
            label = {
                Text(
                    "Search",
                    color = if (currentScreen is Screen.Search) SpotifyGreen else SpotifyLightGray,
                    fontWeight = if (currentScreen is Screen.Search) FontWeight.Bold else FontWeight.Normal,
                )
            },
            colors = NavigationBarItemDefaults.colors(
                indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        )

        NavigationBarItem(
            selected = currentScreen is Screen.Settings,
            onClick = {
                if (currentScreen !is Screen.Settings) {
                    navigator.push(Screen.Settings)
                }
            },
            icon = {
                Icon(Icons.Default.Settings, contentDescription = null, tint = if (currentScreen is Screen.Settings) SpotifyGreen else SpotifyLightGray)
            },
            label = {
                Text(
                    "Settings",
                    color = if (currentScreen is Screen.Settings) SpotifyGreen else SpotifyLightGray,
                    fontWeight = if (currentScreen is Screen.Settings) FontWeight.Bold else FontWeight.Normal,
                )
            },
            colors = NavigationBarItemDefaults.colors(
                indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        )
    }
}
