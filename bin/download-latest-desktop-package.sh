#!/usr/bin/env bash
# downloads the installer that the latest github release has for the system this runs on: a .dmg
# on macos, an .msi on windows, a .deb on linux, for this cpu. bin/desktop.sh names them
# <name>-<version>-<system>-<cpu>.<format>, and ci attaches them to the release.
#
#   bin/download-latest-desktop-package.sh [directory]
#
# it saves to the directory, or ~/Downloads if there's none, and prints the path.
#
#   GITHUB_REPOSITORY   the owner/name of the repository (the origin remote's otherwise)
#   GITHUB_TOKEN        a token, if the repository is private or the api is rate limiting you
#                       (gh's login works too, if gh is installed)
# shellcheck source-path=SCRIPTDIR source=common.sh
source "$(dirname "$0")/common.sh"

destination="${1:-$HOME/Downloads}"

repository="${GITHUB_REPOSITORY:-}"
if [ -z "$repository" ]; then
  remote="$(git remote get-url origin 2>/dev/null || true)"
  repository="$(sed -E 's#^(git@[^:]+:|https?://[^/]+/|ssh://[^/]+/)##; s#\.git$##' <<<"$remote")"
fi
[[ "$repository" =~ ^[^/]+/[^/]+$ ]] || { echo "can't tell which github repository to look in: set GITHUB_REPOSITORY to owner/name" >&2; exit 1; }

# the same as bin/desktop.sh, so the suffix matches what it names the installer
case "$(uname -s)" in
  Darwin) system=macos format=dmg ;;
  MINGW* | MSYS* | CYGWIN*) system=windows format=msi ;;
  *) system=linux format=deb ;;
esac
case "$(uname -m)" in
  arm64 | aarch64) cpu=arm64 ;;
  *) cpu=x64 ;;
esac
suffix="-$system-$cpu.$format"

# the latest release's tag and asset names and urls, one per line: gh if it's here, else the api
use_gh=""
if command -v gh >/dev/null 2>&1 && gh auth status >/dev/null 2>&1; then
  use_gh=yes
  release="$(gh release view --repo "$repository" --json tagName,assets \
    --jq '"tag \(.tagName)", (.assets[] | "asset \(.name) \(.url)")')"
else
  auth=()
  [ -z "${GITHUB_TOKEN:-}" ] || auth=(-H "Authorization: Bearer $GITHUB_TOKEN")
  json="$(curl -fsSL -H "Accept: application/vnd.github+json" ${auth[@]+"${auth[@]}"} \
    "https://api.github.com/repos/$repository/releases/latest")" ||
    { echo "couldn't read the latest release of $repository" >&2; exit 1; }
  # no jq needed: the api prints each field on its own line
  release="$(
    sed -n 's/^  "tag_name": "\(.*\)",$/tag \1/p' <<<"$json"
    sed -n 's/^ *"browser_download_url": "\(.*\)"$/\1/p' <<<"$json" | while read -r url; do
      echo "asset ${url##*/} $url"
    done
  )"
fi

tag="$(sed -n 's/^tag //p' <<<"$release")"
[ -n "$tag" ] || { echo "$repository has no latest release" >&2; exit 1; }

read -r name url < <(awk -v suffix="$suffix" '$1 == "asset" && substr($2, length($2) - length(suffix) + 1) == suffix { print $2, $3; exit }' <<<"$release") || true
if [ -z "${name:-}" ]; then
  echo "release $tag of $repository has nothing ending in $suffix; it has:" >&2
  sed -n 's/^asset \([^ ]*\) .*/  \1/p' <<<"$release" >&2
  exit 1
fi

mkdir -p "$destination"
file="$destination/$name"
echo "downloading $name from release $tag of $repository" >&2
partial="$file.part"
trap 'rm -f "$partial"' EXIT
if [ -n "$use_gh" ]; then
  gh release download "$tag" --repo "$repository" --pattern "$name" --output "$partial" --clobber
else
  curl -fL --progress-bar ${auth[@]+"${auth[@]}"} -o "$partial" "$url"
fi
mv "$partial" "$file"
echo "$file"
