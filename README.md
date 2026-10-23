# KMusicPlayer

A **Jellyfin** music player that mixes your own library with **YouTube Music**. Search YouTube Music next to your
library, play songs and music videos right away, and download the ones you like into your Jellyfin library, tagged
and with covers. Written in Kotlin with Compose Multiplatform; one codebase runs as an Android app, a desktop app
(Linux, Windows, macOS) and a web app.

KMusicPlayer is made for **Jellyfin** 12.2+ together with the [KMusicPlayer Jellyfin plugin](./jellyfin-plugin),
which does the YouTube Music part on the server.

## YouTube Music

With the plugin installed, all apps can

- search YouTube Music for songs, artists, albums and music videos, with results shown next to your library,
- browse a YouTube Music artist's albums, singles, EPs and top songs,
- stream tracks and music videos (fullscreen, selectable quality; desktop video playback needs an installed VLC 3),
- download tracks and whole albums into the Jellyfin music library in their original codec. The server downloads
  them with yt-dlp, looks up the album and tags them (title, artists, album, track number, year, cover), and it can
  sign in to YouTube with a cookies file or browser cookies,
- see which tracks and albums are already in your library, and the codec and bitrate a download would get,
- add YouTube Music tracks to Jellyfin playlists: missing tracks are downloaded first, then added in order. This
  keeps running on the server after you close the app.

All YouTube work happens on the server. The apps never run yt-dlp or talk to YouTube themselves, so nothing extra
is needed on your phone or browser. The plugin ships yt-dlp and Deno for Linux x64 and arm64 (the official Jellyfin
Docker image) and updates yt-dlp daily. See the [plugin README](./jellyfin-plugin/README.md) for installation
(including the plugin repository URL for Jellyfin's catalog) and configuration.

## Player features

- Browse playlists, artists, albums and favorites, and search the library
- Playlists: create them, add songs from the context menu or by drag and drop (desktop), mark favorites
- Queue with drag-to-reorder, "add to queue" and a recently played history
- Gapless playback and crossfade (optional)
- Synced lyrics in the player
- Shows codec and bitrate of the playing track
- Multi-select and context menu for songs (right-click on desktop/web, long-press on Android)
- Offline playback on Android: download playlists or single songs in their original quality, and browse them
  without a connection
- System media controls: Android media session (notification, lock screen, Bluetooth) and MPRIS on Linux (media keys,
  desktop widgets)
- Remote control between devices through Jellyfin sessions: see what's playing elsewhere, control it, or move
  playback to another device
- Self-signed certificates can be allowed for your server (Android and desktop only; in the web app the browser
  decides)

### Navidrome / Subsonic

KMusicPlayer can also connect to Navidrome or another Subsonic-compatible server (choose the server type on the
login screen). This is a basic mode for playing your library: YouTube Music and remote control need Jellyfin and
aren't available there.

## Installing

### Arch Linux

```sh
cd packaging/arch && makepkg -si
```

This builds the desktop app from the committed `HEAD` of this repository and installs it as a jar that runs on the
system Java (`java-runtime>=21`). You get a `kmusicplayer` command and a menu entry. Install `vlc` to play YouTube
videos.

### Other desktop systems

Compose Desktop can build installers with a bundled JRE for the OS you run the build on:

- Linux: `./gradlew :desktopApp:packageDeb`
- Windows: `./gradlew :desktopApp:packageMsi`
- macOS: `./gradlew :desktopApp:packageDmg`
- Runnable jar for the current OS: `./gradlew :desktopApp:packageUberJarForCurrentOS`

The output goes to `desktopApp/build/compose/`.

### Android

`./gradlew :androidApp:assembleDebug` builds `androidApp/build/outputs/apk/debug/androidApp-debug.apk`. Android
blocks unencrypted HTTP, so the server has to be reachable over HTTPS.

### Web

`./gradlew :webApp:wasmJsBrowserDistribution` builds the web app as a static site that you can serve from any web
server. Use `jsBrowserDistribution` for older browsers, or `composeCompatibilityBrowserDistribution` for a build
that falls back to JS where Wasm isn't supported. The output goes to `webApp/build/dist/`. The music server must allow requests from that
origin (CORS).

## Development

### Requirements

- JDK 21
- Android SDK (compileSdk 36) for the Android app
- .NET 10 SDK only for the [Jellyfin plugin](./jellyfin-plugin)

### Running the apps

Use the run configurations in your IDE's run widget, or these Gradle tasks:

- Android app: `./gradlew :androidApp:assembleDebug`
- Desktop app:
    - Hot reload: `./gradlew :desktopApp:hotRun --auto`
    - Standard run: `./gradlew :desktopApp:run`
- Web app:
    - Wasm target (faster, modern browsers): `./gradlew :webApp:wasmJsBrowserDevelopmentRun`
    - JS target (slower, supports older browsers): `./gradlew :webApp:jsBrowserDevelopmentRun`

### Running tests

Use the run button in your IDE's editor gutter, or these Gradle tasks:

- Android tests: `./gradlew :shared:testAndroidHostTest`
- Desktop tests: `./gradlew :shared:jvmTest`
- Web tests:
    - Wasm target: `./gradlew :shared:wasmJsTest`
    - JS target: `./gradlew :shared:jsTest`

### Project structure

| Path | Contents |
|---|---|
| [`shared/`](./shared/src) | Almost all code: UI, view models, server clients, playback logic |
| `shared/src/commonMain` | Code for all platforms (`data/` server APIs and repositories, `ui/` screens, `mvi/` view model base) |
| `shared/src/jvmCommonMain` | Code shared by Android and desktop (OkHttp-based HTTP client) |
| `shared/src/androidMain` | ExoPlayer playback, media session, offline song storage |
| `shared/src/jvmMain` | Desktop audio (javax.sound with MP3/FLAC decoders), VLC video, MPRIS |
| `shared/src/jsMain`, `wasmJsMain` | Browser audio/video through HTML media elements |
| [`androidApp/`](./androidApp), [`desktopApp/`](./desktopApp), [`webApp/`](./webApp) | Thin entry points for each platform |
| [`jellyfin-plugin/`](./jellyfin-plugin) | The Jellyfin plugin (C#) for YouTube Music |
| [`packaging/`](./packaging) | Distribution packages (Arch Linux PKGBUILD) |

Screens follow an MVI pattern: each screen has a contract (state, events, effects) and a view model based on
`MviStore`. Platform differences (audio player, HTTP client, file storage, media controls) are `expect`/`actual`
declarations in `shared/src/commonMain/.../platform/`.

### Versions and releases

Versions come from git tags:

- Apps: the latest `vX.Y.Z` tag. You can override it with `-PappVersion=X.Y.Z`. The Android `versionCode` is
  `X*10000 + Y*100 + Z`.
- Jellyfin plugin: the latest `plugin-vX.Y.Z` tag plus the number of commits since then. Pushing such a tag runs the
  [release workflow](./.github/workflows/jellyfin-plugin.yml), which publishes the plugin to the plugin repository
  (see the [plugin README](./jellyfin-plugin/README.md#releasing)).
