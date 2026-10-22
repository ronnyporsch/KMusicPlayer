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
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import de.ronnyporsch.open_rgb.kmusicplayer.data.youtube.YouTubeService
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.Song
import de.ronnyporsch.open_rgb.kmusicplayer.platform.createHttpClient
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
import de.ronnyporsch.open_rgb.kmusicplayer.ui.components.LocalPlaylistEditor
import de.ronnyporsch.open_rgb.kmusicplayer.ui.components.PlaylistEditor
import de.ronnyporsch.open_rgb.kmusicplayer.ui.components.onMouseNavigation
import de.ronnyporsch.open_rgb.kmusicplayer.ui.theme.SpotifyGreen
import de.ronnyporsch.open_rgb.kmusicplayer.ui.theme.SpotifyLightGray
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun App() {
    // Cover art is loaded from the music server, whose certificate may only be accepted by our HTTP client
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory(httpClient = { createHttpClient() })) }
            .build()
    }
    // The connection and the player outlive the UI, e.g. an Android activity that gets closed while playing
    val session = remember { AppSession.get() }
    val settingsHolder = session.settingsHolder
    val connection = session.connection
    val repository = connection.repository
    val youTube = remember { YouTubeService(connection) }
    // With saved login info, start directly on the library while auto-login is still connecting in the background
    val loggedIn = remember { session.autoLoginStatus.value.let { it != AutoLoginStatus.None && it !is AutoLoginStatus.Rejected } }
    val navigator = remember { Navigator(if (loggedIn) Screen.Library else Screen.Login) }
    val playerViewModel = session.playerViewModel
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val playlistEditor = remember {
        PlaylistEditor(repository, scope) { message ->
            snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
        }
    }
    val libraryViewModel = remember {
        LibraryViewModel(
            repository,
            awaitingConnection = loggedIn,
            playlistChanges = repository.playlistChanges,
            downloads = connection.downloads,
        )
    }
    val searchViewModel = remember { SearchViewModel(repository, youTube) }
    val coverArtUrlProvider = remember { CoverArtUrlProvider(repository) }

    // (Re)load the library whenever a server got connected, it may be a different one
    LaunchedEffect(connection) {
        connection.connections.collect { count ->
            if (count > 0) libraryViewModel.onEvent(LibraryEvent.Refresh)
        }
    }

    LaunchedEffect(session) {
        session.autoLoginStatus.collect { status ->
            when (status) {
                is AutoLoginStatus.Rejected -> if (navigator.currentScreen.value !is Screen.Login) {
                    navigator.replaceAll(Screen.Login)
                    snackbarHostState.showSnackbar("Login failed: ${status.message}", withDismissAction = true)
                }
                is AutoLoginStatus.Retrying -> {
                    // Meanwhile show the playlists stored on the device
                    libraryViewModel.onEvent(LibraryEvent.ConnectionFailed)
                    launch {
                        snackbarHostState.showSnackbar("${status.message}. Retrying…", duration = SnackbarDuration.Short)
                    }
                }
                AutoLoginStatus.None, AutoLoginStatus.Connecting, AutoLoginStatus.Connected -> {}
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

    LaunchedEffect(playerViewModel) {
        playerViewModel.effects.collect { effect ->
            when (effect) {
                is PlayerEffect.PlaybackError ->
                    snackbarHostState.showSnackbar("Playback failed: ${effect.message}", withDismissAction = true)
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
            LocalPlaylistEditor provides playlistEditor,
        ) {
        val currentScreen by navigator.currentScreen.collectAsState()
        val playerState by playerViewModel.state.collectAsState()
        val hasSong = playerState.playback.currentSong != null

        // System back (Android back button/gesture) navigates within the app while there is a previous screen.
        // Keyed on currentScreen so this scope recomposes on navigation (the back stack itself isn't observable)
        val canGoBack = remember(currentScreen) { navigator.canGoBack() }
        NavigationBackHandler(
            state = rememberNavigationEventState(NavigationEventInfo.None),
            isBackEnabled = canGoBack,
            onBackCompleted = { navigator.pop() },
        )

        Scaffold(
            // Mouse back/forward buttons work anywhere in the window, including the top/bottom bars
            modifier = Modifier.onMouseNavigation(
                onBack = { if (navigator.canGoBack()) navigator.pop() },
                onForward = { if (navigator.canGoForward()) navigator.forward() }
            ),
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                if (currentScreen !is Screen.Login && currentScreen !is Screen.Player) {
                    val searchState by searchViewModel.state.collectAsState()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // These screens draw their own back button in their header
                        val hasOwnBackButton = currentScreen is Screen.ArtistAlbums || currentScreen is Screen.AlbumSongs ||
                            currentScreen is Screen.PlaylistSongs || currentScreen is Screen.Settings
                        if (navigator.canGoBack() && !hasOwnBackButton) {
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
                                // Results only show while there is a query; clearing it goes back
                                if (it.isEmpty()) {
                                    if (currentScreen is Screen.Search) navigator.pop()
                                } else if (currentScreen !is Screen.Search) {
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
                                focusedPlaceholderColor = SpotifyLightGray,
                                unfocusedPlaceholderColor = SpotifyLightGray,
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
                                onAlbumClick = { navigator.push(Screen.AlbumSongs(it)) },
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
                    },
            ) {
                when (val screen = currentScreen) {
                    is Screen.Login -> {
                        val vm = remember { LoginViewModel(connection, settingsHolder.settings.value) }
                        LaunchedEffect(vm) {
                            vm.effects.collectLatest { effect ->
                                when (effect) {
                                    LoginEffect.NavigateToLibrary -> navigator.replaceAll(Screen.Library)
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
                            onAlbumClick = { navigator.push(Screen.AlbumSongs(it)) },
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
                        val vm = remember { SettingsViewModel(connection, settingsHolder) }
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
