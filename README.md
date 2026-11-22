# Mogul Studio (Compose)

Mogul Studio, the web client for Mogul, rebuilt in Kotlin with Compose Multiplatform for the web (Wasm, with a JS fallback).
It is a port of the Vue client in `~/code/mogul/client`, and talks to the same api through the same gateway.

## Running it

The studio is only ever reached through the gateway, which signs you in and routes `/api` to the api and everything else to the client.
The dev server therefore listens where Vite used to, on `127.0.0.1:5173` (see `webApp/webpack.config.d/devServer.js`).

```bash
./gradlew :webApp:wasmJsBrowserDevelopmentRun --continuous   # dev server on :5173, behind the gateway on :1010
./gradlew :webApp:wasmJsBrowserDistribution                  # production build, in webApp/build/dist/wasmJs/productionExecutable
./gradlew :shared:wasmJsTest                                 # tests
```

Start the gateway and api as you would for the Vue client, then open `http://127.0.0.1:1010`.

A production build is static files, served the way the Vue client's are: `/config.js` is answered from the environment (`window.MOGUL_API_URL`), and every unknown path falls back to `index.html`.
nginx must serve `.wasm` as `application/wasm`.

## Layout

Everything lives in `shared/src/commonMain/kotlin/com/joshlong/mogul/client`:

- `api/` - the GraphQL client (Ktor), the models, and one service per area, with the Vue client's queries field for field. `Mogul` wires them all.
- `platform/` - the one seam to the browser (history, storage, uploads, audio, Ably); the implementation is in `webMain`.
- `nav/` - the routes, with the Vue router's paths, kept in step with the address bar.
- `theme/` - the palettes from `base.css` (day, midnight, graphite, ember) and the same auto dark-mode rules, stored under the same `localStorage` key.
- `i18n/` - the vue-i18n messages, generated into `Messages.kt` with the same keys and `{name}` / `one | many` syntax; `t()` and `tc()` look them up.
- `ui/` - the shared controls: chrome buttons, fieldsets, text fields with tool panels, icons.
- one package per area of the studio: `home`, `podcasts`, `blogs`, `publications`, `notes`, `search`, and so on.
