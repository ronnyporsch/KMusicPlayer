#!/usr/bin/env python3
"""Generates every Voidcast icon and logo from one description of the artwork.

Writes the web favicon (the master SVG), the Android launcher foreground, the in-app logo, and renders all PNG and
ICO sizes from the SVG. Needs rsvg-convert (librsvg) and magick (ImageMagick).

The Android adaptive icon's background (solid colour) and monochrome layer are hand-written in
androidApp/src/main/res/drawable/ and not touched here.

Usage: packaging/icons/generate.py
"""
import subprocess
import tempfile
from pathlib import Path

root = Path(__file__).resolve().parents[2]

# Artwork is drawn on a 512 canvas, centred on 256,256
M, LIGHT, MID = "FF00FF", "FFB3FF", "FF80FF"
RING = "M114,256a142,142 0,1 0,284 0a142,142 0,1 0,-284 0z"
HALO = "M66,256a190,190 0,1 0,380 0a190,190 0,1 0,-380 0z"
CORE = "M235,256a21,21 0,1 0,42 0a21,21 0,1 0,-42 0z"
DOT = "M249,256a7,7 0,1 0,14 0a7,7 0,1 0,-14 0z"
TOP = "M252.5,114a3.5,3.5 0,1 0,7 0a3.5,3.5 0,1 0,-7 0z"
SIDES = "M395,256a3,3 0,1 0,6 0a3,3 0,1 0,-6 0zM111,256a3,3 0,1 0,6 0a3,3 0,1 0,-6 0z"
ARC = "M256,114A142,142 0,0 1,398 256"
LINES = "M46,256H225M287,256H466"
RING_STOPS = [(0, LIGHT), (.45, M), (.75, M), (1, MID)]
HALO_STOPS = [(0, 0), (.7, .12), (.88, .32), (1, 0)]

svg = f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="46 46 420 420">
  <defs>
    <radialGradient id="halo">
{chr(10).join(f'      <stop offset="{o}" stop-color="#{M}" stop-opacity="{a}"/>' for o, a in HALO_STOPS)}
    </radialGradient>
    <linearGradient id="ring" x1="0" y1="0" x2="1" y2="1">
{chr(10).join(f'      <stop offset="{o}" stop-color="#{c}"/>' for o, c in RING_STOPS)}
    </linearGradient>
    <filter id="glow" x="-50%" y="-50%" width="200%" height="200%">
      <feGaussianBlur stdDeviation="12"/>
    </filter>
  </defs>

  <circle cx="256" cy="256" r="190" fill="url(#halo)"/>

  <!-- Atmospheric glow -->
  <circle cx="256" cy="256" r="142" fill="none" stroke="#{M}" stroke-width="18" opacity=".75" filter="url(#glow)"/>

  <!-- Cosmic ring -->
  <circle cx="256" cy="256" r="142" fill="none" stroke="url(#ring)" stroke-width="5"/>
  <path d="M256 114 A142 142 0 0 1 398 256" fill="none" stroke="#{LIGHT}" stroke-width="2" opacity=".8"/>

  <!-- Horizontal transmission -->
  <path d="M46 256 H225 M287 256 H466" fill="none" stroke="#{M}" stroke-width="4"/>

  <!-- Tuning point -->
  <circle cx="256" cy="256" r="21" fill="none" stroke="#{M}" stroke-width="5"/>
  <circle cx="256" cy="256" r="7" fill="#{LIGHT}"/>

  <!-- Signal markers -->
  <circle cx="256" cy="114" r="3.5" fill="#{LIGHT}"/>
  <circle cx="398" cy="256" r="3" fill="#{M}"/>
  <circle cx="114" cy="256" r="3" fill="#{M}"/>
</svg>
'''

def alpha(a): return f"{round(a * 255):02X}"

def vector(viewport, translate, comment):
    halo = "\n".join(f'                    <item android:color="#{alpha(a)}{M}" android:offset="{o}"/>' for o, a in HALO_STOPS)
    ring = "\n".join(f'                    <item android:color="#FF{c}" android:offset="{o}"/>' for o, c in RING_STOPS)
    return f'''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
        xmlns:aapt="http://schemas.android.com/aapt"
        android:width="108dp"
        android:height="108dp"
        android:viewportWidth="{viewport}"
        android:viewportHeight="{viewport}">
    <!-- {comment} -->
    <group android:translateX="{translate}" android:translateY="{translate}">
        <!-- Halo -->
        <path android:pathData="{HALO}">
            <aapt:attr name="android:fillColor">
                <gradient
                        android:type="radial"
                        android:centerX="256"
                        android:centerY="256"
                        android:gradientRadius="190">
{halo}
                </gradient>
            </aapt:attr>
        </path>
        <!-- Atmospheric glow; vector drawables can't blur, so it's stacked translucent strokes -->
        <path android:pathData="{RING}" android:strokeWidth="64" android:strokeColor="#10{M}"/>
        <path android:pathData="{RING}" android:strokeWidth="44" android:strokeColor="#1C{M}"/>
        <path android:pathData="{RING}" android:strokeWidth="28" android:strokeColor="#2E{M}"/>
        <path android:pathData="{RING}" android:strokeWidth="16" android:strokeColor="#36{M}"/>
        <!-- Cosmic ring -->
        <path android:pathData="{RING}" android:strokeWidth="5">
            <aapt:attr name="android:strokeColor">
                <gradient
                        android:type="linear"
                        android:startX="114"
                        android:startY="114"
                        android:endX="398"
                        android:endY="398">
{ring}
                </gradient>
            </aapt:attr>
        </path>
        <path android:pathData="{ARC}" android:strokeWidth="2" android:strokeColor="#CC{LIGHT}"/>
        <!-- Horizontal transmission -->
        <path android:pathData="{LINES}" android:strokeWidth="4" android:strokeColor="#FF{M}"/>
        <!-- Tuning point -->
        <path android:pathData="{CORE}" android:strokeWidth="5" android:strokeColor="#FF{M}"/>
        <path android:pathData="{DOT}" android:fillColor="#FF{LIGHT}"/>
        <!-- Signal markers -->
        <path android:pathData="{TOP}" android:fillColor="#FF{LIGHT}"/>
        <path android:pathData="{SIDES}" android:fillColor="#FF{M}"/>
    </group>
</vector>
'''

(root / "webApp/src/webMain/resources/favicon.svg").write_text(svg)
(root / "androidApp/src/main/res/drawable-v24/ic_launcher_foreground.xml").write_text(vector(630, 59,
    "Artwork is drawn on a 512 canvas (see webApp favicon.svg); the larger viewport lets the transmission line end at the edge of the visible area"))
(root / "shared/src/commonMain/composeResources/drawable/voidcast_logo.xml").write_text(vector(420, -46,
    "App logo; same artwork as webApp favicon.svg, cropped to the transmission line"))


def render(size, out, svg=root / "webApp/src/webMain/resources/favicon.svg"):
    subprocess.run(["rsvg-convert", "-w", str(size), "-h", str(size), str(svg), "-o", str(out)], check=True)


res = root / "androidApp/src/main/res"
for density, size in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)]:
    for name in ["ic_launcher", "ic_launcher_round"]:
        render(size, res / f"mipmap-{density}/{name}.png")

render(512, root / "desktopApp/icons/icon.png")
render(256, root / "desktopApp/src/main/resources/icon.png")

with tempfile.TemporaryDirectory() as tmp:
    sizes = [16, 24, 32, 48, 64, 128, 256]
    for size in sizes:
        render(size, f"{tmp}/{size}.png")
    subprocess.run(["magick", *[f"{tmp}/{s}.png" for s in sizes], str(root / "desktopApp/icons/icon.ico")], check=True)
    subprocess.run(["magick", *[f"{tmp}/{s}.png" for s in [16, 32, 48]],
                    str(root / "webApp/src/webMain/resources/favicon.ico")], check=True)
    # iOS needs an opaque square
    render(180, f"{tmp}/touch.png")
    subprocess.run(["magick", f"{tmp}/touch.png", "-background", "#08080C", "-flatten", "-alpha", "off",
                    str(root / "webApp/src/webMain/resources/apple-touch-icon.png")], check=True)

print("Icons generated. Bump the ?v= on the icon links in webApp/src/webMain/resources/index.html so browsers reload them.")
