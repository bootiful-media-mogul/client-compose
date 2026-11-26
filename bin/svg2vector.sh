#!/usr/bin/env bash
# bin/svg2vector.sh in.svg out.xml: an svg as an android vector drawable (bin/svg2vector.java),
# for compose resources, which android can't draw as svgs. it runs android studio's own
# converter, out of the android build's tools in gradle's cache, so build an android app first
set -euo pipefail
[ $# -eq 2 ] || { echo "usage: $0 in.svg out.xml" >&2; exit 1; }
command -v java >/dev/null || { echo "$0 needs java" >&2; exit 1; }

jars="${GRADLE_USER_HOME:-$HOME/.gradle}/caches/modules-2/files-2.1"
newest() { # the newest jar named $2 that gradle has fetched for group $1
  find "$jars/$1/$2" -name "$2-*.jar" ! -name '*-sources.jar' 2>/dev/null | sort -V | tail -1
}
sdk_common="$(newest com.android.tools sdk-common)"
common="$(newest com.android.tools common)"
guava="$(find "$jars/com.google.guava/guava" -name 'guava-*-jre.jar' 2>/dev/null | sort -V | tail -1)"
[ -n "$sdk_common" ] && [ -n "$common" ] && [ -n "$guava" ] ||
  { echo "no android build tools in $jars: build an android app first" >&2; exit 1; }

java -cp "$sdk_common:$common:$guava" "$(dirname "$0")/svg2vector.java" "$1" "$2"
