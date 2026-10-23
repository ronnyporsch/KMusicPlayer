#!/usr/bin/env bash
# Builds the plugin into dist/: the plugin zip plus a Jellyfin plugin repository manifest.
# Usage: ./build.sh [base URL dist/ is served from, used in manifest.json]
# The version comes from the latest plugin-v<major>.<minor>.<patch> git tag, with the number of commits since the tag
# as fourth part (so builds between releases still get new versions Jellyfin installs), or from $VERSION. The
# changelog is the tag's message (or $CHANGELOG), the target ABI the Jellyfin.Controller version the plugin builds on.
set -euo pipefail
cd "$(dirname "$0")"

TAG=$(git describe --tags --match 'plugin-v[0-9]*' --abbrev=0 2>/dev/null || true)
if [ -z "${VERSION:-}" ]; then
  if [ -z "$TAG" ]; then echo "No plugin-v<major>.<minor>.<patch> tag found, set VERSION" >&2; exit 1; fi
  VERSION="${TAG#plugin-v}.$(git rev-list --count "$TAG..HEAD")"
fi
if [ -z "${CHANGELOG:-}" ] && [ -n "$TAG" ]; then
  CHANGELOG=$(git tag -l --format='%(contents)' "$TAG" | sed '/-----BEGIN PGP SIGNATURE-----/,$d')
fi
CSPROJ=Jellyfin.Plugin.KMusicPlayer/Jellyfin.Plugin.KMusicPlayer.csproj
TARGET_ABI="$(sed -n 's/.*Include="Jellyfin.Controller" Version="\([0-9.]*\)".*/\1/p' "$CSPROJ").0"
BASE_URL=${1:-http://localhost:8765}
DOTNET=${DOTNET:-$(command -v dotnet || echo "$HOME/.dotnet/dotnet")}

"$DOTNET" build Jellyfin.Plugin.KMusicPlayer -c Release -p:Version="$VERSION" --nologo -v quiet

# Standalone yt-dlp releases (bundle their own Python) and Deno (the JavaScript runtime yt-dlp needs to solve
# YouTube's challenges, without it signed-in requests get no formats) for the architectures of the official Jellyfin
# image, shipped in the plugin zip under yt-dlp/ and deno/. Cached in .tools/; delete it to bundle the latest releases.
YT_DLP_ASSETS=(yt-dlp_linux yt-dlp_linux_aarch64)
DENO_ARCHS=(x86_64 aarch64)
TOOLS=.tools
if [ ! -f "$TOOLS/complete" ]; then
  rm -rf "$TOOLS" && mkdir -p "$TOOLS"
  for asset in SHA2-256SUMS "${YT_DLP_ASSETS[@]}"; do
    curl -fsSL -o "$TOOLS/$asset" "https://github.com/yt-dlp/yt-dlp/releases/latest/download/$asset"
  done
  for arch in "${DENO_ARCHS[@]}"; do
    zip="deno-$arch-unknown-linux-gnu.zip"
    curl -fsSL -o "$TOOLS/$zip" "https://github.com/denoland/deno/releases/latest/download/$zip"
    curl -fsSL -o "$TOOLS/$zip.sha256sum" "https://github.com/denoland/deno/releases/latest/download/$zip.sha256sum"
  done
  touch "$TOOLS/complete"
fi
(
  cd "$TOOLS"
  grep -E " ($(IFS='|'; echo "${YT_DLP_ASSETS[*]}"))\$" SHA2-256SUMS | sha256sum --check --quiet
  for arch in "${DENO_ARCHS[@]}"; do sha256sum --check --quiet "deno-$arch-unknown-linux-gnu.zip.sha256sum"; done
)

rm -rf dist && mkdir -p dist
ZIP="kmusicplayer_$VERSION.zip"
python3 - "dist/$ZIP" Jellyfin.Plugin.KMusicPlayer/bin/Release/net10.0/Jellyfin.Plugin.KMusicPlayer.dll "$TOOLS" "${YT_DLP_ASSETS[*]}" "${DENO_ARCHS[*]}" <<'PY'
import sys, zipfile
zip_path, dll, tools, yt_dlp_assets, deno_archs = sys.argv[1:]

def add_executable(z, name, data):
    info = zipfile.ZipInfo(name)
    info.compress_type = zipfile.ZIP_DEFLATED
    info.external_attr = 0o100755 << 16  # executable, for extractors that keep Unix permissions
    z.writestr(info, data)

with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as z:
    z.write(dll, "Jellyfin.Plugin.KMusicPlayer.dll")
    for asset in yt_dlp_assets.split():
        with open(f"{tools}/{asset}", "rb") as f:
            add_executable(z, "yt-dlp/" + asset, f.read())
    for arch in deno_archs.split():
        with zipfile.ZipFile(f"{tools}/deno-{arch}-unknown-linux-gnu.zip") as deno:
            add_executable(z, f"deno/deno_{arch}", deno.read("deno"))
PY

python3 - dist/manifest.json "$VERSION" "$TARGET_ABI" "$BASE_URL/$ZIP" "$(md5sum "dist/$ZIP" | cut -d' ' -f1)" "${CHANGELOG:-}" <<'PY'
import datetime, json, sys
manifest_path, version, target_abi, source_url, checksum, changelog = sys.argv[1:]
manifest = [{
    "guid": "38651385-45e3-4f8e-bc70-c45f5910a4a0",
    "name": "KMusicPlayer",
    "description": "Searches YouTube Music for KMusicPlayer clients and downloads the tracks they pick into the music library using yt-dlp.",
    "overview": "YouTube Music search and downloads for KMusicPlayer",
    "owner": "KMusicPlayer",
    "category": "General",
    "versions": [{
        "version": version,
        "changelog": changelog.strip(),
        "targetAbi": target_abi,
        "sourceUrl": source_url,
        "checksum": checksum,
        "timestamp": datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
    }],
}]
with open(manifest_path, "w") as f:
    json.dump(manifest, f, indent=2)
PY
echo "Built dist/$ZIP and dist/manifest.json (served from $BASE_URL)"
