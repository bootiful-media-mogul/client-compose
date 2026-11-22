# Mogul Studio (Compose)

Mogul Studio, the web client for Mogul, rebuilt in Kotlin with Compose Multiplatform.
It is a port of the Vue client in `~/code/mogul/client`, and talks to the same api through the same gateway.
The same code runs on the web (Wasm, with a JS fallback), as a desktop app, on an iPad, and on an Android tablet.

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

## The apps

The apps are the web build's studio, page for page; on a wide screen they lay out as the desktop does.
They differ from it only in how they reach Mogul.
The web build rides the gateway's session cookie.
The apps are oauth clients of their own, as Mogul Clip is: they sign in to auth0 in the system browser with PKCE, keep their tokens on the device, and send the access token to the gateway, which checks it and passes it on to the api.
They use Mogul Clip's native auth0 application, named in `gradle.properties`.
Where the gateway is comes from `mogul.server` (in `local.properties` or `-P`), and defaults to `http://127.0.0.1:1010`.

```bash
./gradlew :desktopApp:run                       # the desktop app
./gradlew :desktopApp:packageDmg                # a macOS installer (Msi and Deb on the other systems)
./gradlew :androidApp:installDebug              # the Android app, on a running emulator or a connected tablet
open iosApp/iosApp.xcodeproj                    # the iOS app: run it on an iPad simulator from Xcode
```

* **Desktop.** It can't register a URL scheme while it runs from Gradle, so auth0 sends the browser back to a port it listens on: `http://127.0.0.1:53682/callback`, which has to be among the auth0 application's allowed callback URLs (or set `mogul.desktopRedirectUri` to one that is). Tokens are kept in the user's preferences, audio plays through JavaFX, and downloads go to `~/Downloads`.
* **Android.** The emulator reaches this machine at `10.0.2.2`, and the app makes that substitution itself. Debug builds may speak plain http to it, and since Android 17 they ask for the local network permission to do so. Tokens are kept encrypted, and downloads go to Downloads.
* **iOS.** The simulator shares this machine's network, so `127.0.0.1` is the gateway. Tokens are kept in the keychain, and downloads go to the app's documents, which the Files app shows.

Realtime notifications come over Ably's server-sent events in the apps, since the Ably library the web build loads is a browser script.

## Layout

Everything lives in `shared/src/commonMain/kotlin/com/joshlong/mogul/client`:

- `api/` - the GraphQL client (Ktor), the models, and one service per area, with the Vue client's queries field for field. `Mogul` wires them all.
- `platform/` - the one seam to the browser (history, storage, uploads, audio, Ably); the web's implementation is in `webMain`, the apps' in `appMain`, with the little that differs between them in `androidMain`, `iosMain`, and `desktopMain`.
- `nav/` - the routes, with the Vue router's paths, kept in step with the address bar.
- `theme/` - the palettes from `base.css` (day, midnight, graphite, ember) and the same auto dark-mode rules, stored under the same `localStorage` key.
- `i18n/` - the vue-i18n messages, generated into `Messages.kt` with the same keys and `{name}` / `one | many` syntax; `t()` and `tc()` look them up.
- `ui/` - the shared controls: chrome buttons, fieldsets, text fields with tool panels, icons.
- one package per area of the studio: `home`, `podcasts`, `blogs`, `publications`, `notes`, `search`, and so on.
