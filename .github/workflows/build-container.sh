#!/usr/bin/env bash
# builds the web studio (wasm, falling back to js where the browser can't run it) and packages
# it as an nginx container image with paketo, the way the vue client was. any failure stops it:
# an image without the application in it must never be published.
set -euo pipefail

: "${ROOT_DIR:?ROOT_DIR is required}"
: "${IMAGE_NAME:?IMAGE_NAME is required}"

echo "the root directory is ${ROOT_DIR}."
cd "$ROOT_DIR"
dist=webApp/build/dist/composeWebCompatibility/productionExecutable
rm -rf "$dist" build/container
./gradlew :webApp:composeCompatibilityBrowserDistribution
[ -f "$dist/index.html" ] || { echo "the build produced no $dist/index.html" >&2; exit 1; }
ls "$dist"/*.wasm >/dev/null 2>&1 || { echo "the build produced no .wasm in $dist" >&2; exit 1; }

# config.js is answered by nginx at runtime; the copy in the build is only for the dev server
mkdir -p build/container/public
cp .github/workflows/nginx-buildpack-config/* build/container
cp -r "$dist"/* build/container/public
rm -f build/container/public/config.js
cd build/container

# PUBLISH= (empty) builds into the local docker daemon instead; ci leaves it unset.
publish=(--publish)
[ -n "${PUBLISH-unset}" ] || publish=()
pack build "$IMAGE_NAME" \
  --builder paketobuildpacks/builder-jammy-base \
  --buildpack paketo-buildpacks/nginx \
  --env PORT=8080 \
  ${publish[@]+"${publish[@]}"}
