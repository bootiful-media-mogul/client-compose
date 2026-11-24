#!/usr/bin/env bash
# makes every copy of the logo from brand/mogul-logo.svg: the whole logo, mark and words, that
# the studio draws in its top bar (brand/mogul-studio-logo.svg, by brand/wordmark.py), the
# favicons, and the android, ios and desktop app icons. run it after changing the svg, and check
# in what it writes. it needs rsvg-convert and imagemagick (brew install librsvg imagemagick),
# uv, for wordmark.py's fonttools, and iconutil, which comes with macos, for the .icns.
#
# the logo is the same in every palette, so it carries its own colors and its own navy tile:
# it reads the same on the day theme's sky blue as on the darkest night one. the variants below
# only drop the tile (where the system draws one: android's adaptive icons, ios's dark icon) or
# its color (android's themed icon, ios's tinted icon), and keep the mark itself unchanged
# shellcheck source-path=SCRIPTDIR source=common.sh
source "$(dirname "$0")/common.sh"

for tool in rsvg-convert magick uv iconutil; do
  command -v "$tool" >/dev/null || { echo "bin/brand.sh needs $tool" >&2; exit 1; }
done

logo=brand/mogul-logo.svg
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

# the variants, by the ids in the svg
cp "$logo" "$work/tile.svg"                                                  # the logo itself
sed 's/rx="20"/rx="0"/' "$logo" >"$work/square.svg"                          # for systems that round it themselves
sed 's|<rect id="tile"[^>]*/>|<circle cx="50" cy="50" r="50" fill="#1e293b"/>|' "$logo" >"$work/round.svg"
sed '/id="tile"/d' "$logo" >"$work/mark.svg"                                 # the mark alone, on nothing
sed -e '/id="tile"/d' -e 's/#609ea0/#ffffff/' -e 's/#22c55e/#ffffff/' "$logo" >"$work/mono.svg"

# png <variant> <size> <file> [viewBox]: the variant at size px square, optionally framed by a
# wider viewBox (the margin a system's icon grid wants around the mark)
png() {
  local svg="$work/$1.svg"
  if [ -n "${4:-}" ]; then
    svg="$work/$1-framed.svg"
    sed "s/viewBox=\"[^\"]*\"/viewBox=\"$4\"/" "$work/$1.svg" >"$svg"
  fi
  mkdir -p "$(dirname "$3")"
  rsvg-convert -w "$2" -h "$2" "$svg" -o "$3"
}

# the studio draws the whole logo in its top bar, and the desktop app's window icon is the mark
uv run -q --with fonttools python brand/wordmark.py # and says if BrandMark.kt's aspect needs changing
cp brand/mogul-studio-logo.svg shared/src/commonMain/composeResources/drawable/mogul_studio_logo.svg
cp "$logo" shared/src/commonMain/composeResources/drawable/mogul_logo.svg

# the web build. browsers that take an svg favicon get the logo itself
web=webApp/src/webMain/resources
cp "$logo" "$web/favicon.svg"
for size in 16 32 96; do png tile "$size" "$web/favicon-${size}x${size}.png"; done
for size in 16 32 48; do png tile "$size" "$work/favicon-$size.png"; done
magick "$work"/favicon-{16,32,48}.png "$web/favicon.ico"
for size in 36 48 72 96 144 192; do png tile "$size" "$web/android-icon-${size}x${size}.png"; done
for size in 70 144 150 310; do png tile "$size" "$web/ms-icon-${size}x${size}.png"; done
# ios rounds a home screen icon itself, so these are square
for size in 57 60 72 76 114 120 144 152 180; do png square "$size" "$web/apple-icon-${size}x${size}.png"; done
png square 192 "$web/apple-icon.png"
png square 192 "$web/apple-icon-precomposed.png"

# android. the legacy icons are the logo; an adaptive icon's tile is the launcher's own shape,
# filled with ic_launcher_background (the navy). the 72dp a launcher shows is the logo's 100
# units, so the mark is the size it is on every other icon (and well inside the 66dp circle
# every shape keeps whole); the 108dp layer is 150 units
res=androidApp/src/main/res
adaptive="-25 -25 150 150"
for density in mdpi:1 hdpi:1.5 xhdpi:2 xxhdpi:3 xxxhdpi:4; do
  name="${density%%:*}" scale="${density##*:}"
  legacy="$(awk "BEGIN { print int(48 * $scale) }")"
  layer="$(awk "BEGIN { print int(108 * $scale) }")"
  png tile "$legacy" "$res/mipmap-$name/ic_launcher.png"
  png round "$legacy" "$res/mipmap-$name/ic_launcher_round.png"
  png mark "$layer" "$res/mipmap-$name/ic_launcher_foreground.png" "$adaptive"
  png mono "$layer" "$res/mipmap-$name/ic_launcher_monochrome.png" "$adaptive"
done

# ios: the light icon is square (ios rounds it); the dark and tinted ones are the mark on
# nothing, which ios puts on its own dark tile, and tints the tinted one with the user's color
icons=iosApp/iosApp/Assets.xcassets/AppIcon.appiconset
png square 1024 "$icons/app-icon-1024.png"
png mark 1024 "$icons/app-icon-1024-dark.png"
png mono 1024 "$icons/app-icon-1024-tinted.png"
# xcode wants the light icon without an alpha channel
magick "$icons/app-icon-1024.png" -background '#1e293b' -alpha remove -alpha off "$icons/app-icon-1024.png"

# the desktop installers. a macos icon sits inside the icon grid, 824 of 1024 with a shadow's
# room around it; windows and linux take the logo edge to edge
desktop=desktopApp/icons
mkdir -p "$desktop" "$work/mogul.iconset"
for size in 16 32 128 256 512; do
  png tile "$size" "$work/mogul.iconset/icon_${size}x${size}.png" "-12.1 -12.1 124.2 124.2"
  png tile "$((size * 2))" "$work/mogul.iconset/icon_${size}x${size}@2x.png" "-12.1 -12.1 124.2 124.2"
done
iconutil -c icns "$work/mogul.iconset" -o "$desktop/mogul.icns"
for size in 16 24 32 48 64 128 256; do png tile "$size" "$work/ico-$size.png"; done
magick "$work"/ico-{16,24,32,48,64,128,256}.png "$desktop/mogul.ico"
png tile 512 "$desktop/mogul.png"

echo "made the logo's copies from $logo"
