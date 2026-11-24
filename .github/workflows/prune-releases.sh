#!/usr/bin/env bash
# keeps, of each major version's releases, the first one and the newest few (KEEP_NEWEST, 5 by
# default), and deletes the rest with their tags: 1.x keeps v1.0.1 and its newest five, 2.x
# v2.0.0 and its newest five, and so on, so every series' beginning and end is still there to
# go back to. releases tagged other than vX.Y.Z are left alone. DRY_RUN=1 only says what it
# would delete.
set -euo pipefail

: "${GITHUB_REPOSITORY:?GITHUB_REPOSITORY is required, like bootiful-media-mogul/studio-compose}"
keep="${KEEP_NEWEST:-5}"

tags="$(gh release list -R "$GITHUB_REPOSITORY" --limit 10000 --json tagName --jq '.[].tagName' |
  { grep -E '^v[0-9]+\.[0-9]+\.[0-9]+$' || true; } | sort -t. -k1.2,1n -k2,2n -k3,3n)"
[ -n "$tags" ] || { echo "no releases to prune"; exit 0; }

while read -r major <&3; do
  series="$(grep -E "^v$major\." <<<"$tags")"
  first="$(head -n 1 <<<"$series")"
  newest="$(tail -n "$keep" <<<"$series")"
  while read -r tag; do
    if [ "$tag" = "$first" ] || grep -qxF "$tag" <<<"$newest"; then
      continue
    fi
    if [ -n "${DRY_RUN:-}" ]; then
      echo "would delete $tag"
    else
      echo "deleting $tag"
      gh release delete "$tag" -R "$GITHUB_REPOSITORY" --cleanup-tag --yes
    fi
  done <<<"$series"
done 3< <(sed -E 's/^v([0-9]+)\..*/\1/' <<<"$tags" | sort -un)
