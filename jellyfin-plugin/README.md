# KMusicPlayer Jellyfin plugin

Lets KMusicPlayer clients (Android, desktop and web) search YouTube Music and download tracks into the Jellyfin
music library. The plugin searches YouTube Music for the clients; when a client picks a track, the plugin

1. downloads the best audio stream with [yt-dlp](https://github.com/yt-dlp/yt-dlp) (original codec, no re-encoding),
   optionally signed in with a cookies.txt file or the cookies of a browser on the server (e.g. Firefox),
2. looks up its album and tags it like MusicBrainz Picard would (title, artists, album, album artists, track number,
   year, cover, ...),
3. saves it as `Album Artist/YYYY - Album/NN Title.opus` (or `.m4a`) into the download folder and
4. starts a library scan.

Clients can also search YouTube Music artists and browse their albums, singles and EPs and top songs; album tracks
are streamed and downloaded like search results. The plugin also streams YouTube Music tracks for playback and
reports the codec/bitrate a download would get.

Requires Jellyfin 12.2. The plugin ships the standalone [yt-dlp](https://github.com/yt-dlp/yt-dlp) release for
Linux x64 and arm64 (what the official Docker image runs on) and updates it daily; elsewhere install yt-dlp on the
server's `PATH` or set *yt-dlp path*. It also ships [Deno](https://deno.com), the JavaScript runtime yt-dlp needs to
solve YouTube's challenges: signed in (with cookies), YouTube only offers formats behind them. Elsewhere install Deno
on the `PATH` for signed-in downloads; without it the plugin downloads without signing in. ffmpeg and ffprobe are used from the `PATH` if installed, otherwise (e.g. in
the official Docker image) Jellyfin's own.

## Building

Needs the .NET 10 SDK and network access: the build downloads the latest yt-dlp and Deno releases (verified against
their SHA-256 checksums) into `.tools/` and bundles them; delete that folder to bundle newer releases.

```sh
./build.sh [URL dist/ will be served from]
```

This writes `dist/kmusicplayer_<version>.zip` and `dist/manifest.json`, a Jellyfin plugin repository manifest.

## Installing

Either

- serve `dist/` over HTTP (e.g. `cd dist && python3 -m http.server 8765`), add `<URL>/manifest.json` as a repository
  under *Dashboard → Plugins → Repositories*, install *KMusicPlayer* from the catalog and restart Jellyfin, or
- unpack the zip into `<Jellyfin config dir>/plugins/KMusicPlayer_1.0.0.0/` and restart Jellyfin.

## Configuration

*Dashboard → Plugins → KMusicPlayer*:

| Setting | Default |
|---|---|
| Download folder | First folder of the first music library |
| Cookies file | None (blank: use the browser cookies) |
| Use cookies from browser | `firefox` (blank: download without signing in) |
| Browser profile | Default profile of the user Jellyfin runs as |
| yt-dlp path | The yt-dlp shipped with the plugin, else yt-dlp from the `PATH` |
| Allow all users to download | Off: only administrators can add tracks to the library |
| Parallel downloads | 2 |

Keep yt-dlp up to date (the bundled one updates itself), YouTube changes regularly break older versions.

### Cookies file with Docker

Export your YouTube cookies in Netscape format (e.g. with a "cookies.txt" browser extension while signed in to
music.youtube.com), mount the file and set *Cookies file* to the mount point:

```yaml
services:
  jellyfin:
    volumes:
      - /path/to/cookies.txt:/cookies.txt:ro
```

The file must exist before the container starts, otherwise Docker creates an empty directory in its place. yt-dlp
writes cookies back to the file it's given, so the plugin passes it a copy in Jellyfin's cache folder (refreshed when
the original changes) and the mount can stay read-only.

### Firefox cookies with Docker

The official Jellyfin image contains no browser, so mount your Firefox profile folder (read-only is enough, yt-dlp
copies the cookie database) and set *Browser profile* to the mount point:

```yaml
services:
  jellyfin:
    volumes:
      # Firefox keeps profiles in ~/.mozilla/firefox or (newer versions) ~/.config/mozilla/firefox
      - ~/.config/mozilla/firefox:/firefox:ro
```

yt-dlp uses the most recently used profile below that folder. If the cookies can't be read, the plugin downloads
without signing in and shows a warning in the KMusicPlayer settings.

## API

All endpoints require a Jellyfin access token (`Authorization: MediaBrowser Token="..."` header or `ApiKey` query
parameter).

| Endpoint | |
|---|---|
| `GET /KMusicPlayer/Info` | Plugin version, whether yt-dlp is available and whether the user may download |
| `GET /KMusicPlayer/YouTube/Search?query=...` | YouTube Music song search results |
| `POST /KMusicPlayer/Downloads` | Queue a download; the body is a search result as returned by the search |
| `GET /KMusicPlayer/Downloads[/{id}]` | Status of the user's downloads (`Queued`, `Downloading`, `Importing`, `Completed`, `Failed`) |
| `GET /KMusicPlayer/YouTube/{videoId}/StreamInfo` | Codec and bitrate of the stream a download would get |
| `GET /KMusicPlayer/YouTube/{videoId}/Audio[?format=mp3]` | The track's audio, for playback |
