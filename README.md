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

## Releasing

Each platform is released by a script in `bin`, which reads the project's details from `app.properties` and points the build at production (`https://studio.mogul.tools`, or `APP_SERVER=<url>`).
GitHub Actions runs the same scripts, each on the system it needs, and a laptop with the secrets in its environment can run them too.

| What | Script | Workflow | When |
|---|---|---|---|
| Web, to Cloud Foundry and GKE | `.github/workflows/build-container.sh` | `web.yaml` (Linux) | every push to `main` |
| iOS, to TestFlight | `bin/ios-testflight.sh` | `ios.yaml` (macOS) | every push to `main` |
| Android, to Google Play's internal testing track | `bin/android-play.sh` | `android.yaml` (Linux) | every push to `main` |
| Desktop installers, to a GitHub release | `bin/desktop.sh` | `desktop.yaml` (macOS, Windows, Linux) | a tag like `v1.2.3` |

A push that only changes another platform's code doesn't start a run, and every workflow can be run by hand.

* **Web.** The build publishes the `client` image (`composeCompatibilityBrowserDistribution`, served by the nginx buildpack with the config in `.github/nginx-buildpack-config`), then sends the `pipeline` repository the dispatch that deploys it.
  `PUBLISH= ROOT_DIR=$PWD IMAGE_NAME=<name> .github/workflows/build-container.sh` builds it into the local Docker daemon instead.
  The Vue client's workflow now runs only by hand, to roll back to it.
  Secrets: `GCLOUD_SA_KEY` and `MOGUL_GITHUB_PERSONAL_ACCESS_TOKEN`, as for the other images.
* **iOS.** The App Store Connect API key (`ASC_KEY_ID`, `ASC_ISSUER_ID`, `ASC_KEY`) signs, makes the provisioning profiles, and uploads.
  In CI, `IOS_CERTIFICATE` (an Apple Development certificate and its key, as a base64 `.p12`) and `IOS_CERTIFICATE_PASSWORD` give Xcode a certificate to sign the archive with, and the export is signed with Apple's cloud-managed distribution certificate, which takes an API key with the Admin role.
  Builds are numbered with the time.
* **Android.** `bin/android-play.sh` builds the bundle with `bin/android.sh`, signed with the upload key (`ANDROID_KEYSTORE`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`; in CI the keystore is the base64 `ANDROID_KEYSTORE_BASE64` secret), and releases it through the Google Play Developer API as a service account (`PLAY_SERVICE_ACCOUNT`, its JSON key).
  Invite the service account in Play Console with release permissions.
  Play takes an app's first bundle only by hand, and while Play Console has the app as a draft, set the `PLAY_RELEASE_STATUS` variable to `draft`.
  Version codes are the minutes since 1970, so every build is newer than the last.
* **Desktop.** jlink trims a Java runtime to what the app needs and jpackage puts it inside a `.dmg`, `.msi` or `.deb`, so nobody needs Java installed.
  jpackage only packages for the system it runs on, so the workflow runs on Apple silicon and Intel Macs, Windows and Linux, then attaches the installers to the release for the tag.
  On macOS, `MAC_CERTIFICATE` (a Developer ID Application certificate and its key, as a base64 `.p12`), `MAC_CERTIFICATE_PASSWORD` and `MAC_SIGNING_IDENTITY` (its name, as Keychain Access shows it) sign the app, and the App Store Connect API key notarizes it; without them, Gatekeeper won't open the `.dmg` on another Mac.
  The Windows installer is unsigned, so SmartScreen warns about it.

## Layout

Everything lives in `shared/src/commonMain/kotlin/com/joshlong/mogul/client`:

- `api/` - the GraphQL client (Ktor), the models, and one service per area, with the Vue client's queries field for field. `Mogul` wires them all.
- `platform/` - the one seam to the browser (history, storage, uploads, audio, Ably); the web's implementation is in `webMain`, the apps' in `appMain`, with the little that differs between them in `androidMain`, `iosMain`, and `desktopMain`.
- `nav/` - the routes, with the Vue router's paths, kept in step with the address bar.
- `theme/` - the palettes from `base.css` (day, midnight, graphite, ember) and the same auto dark-mode rules, stored under the same `localStorage` key.
- `i18n/` - the vue-i18n messages, generated into `Messages.kt` with the same keys and `{name}` / `one | many` syntax; `t()` and `tc()` look them up.
- `ui/` - the shared controls: chrome buttons, fieldsets, text fields with tool panels, icons.
- one package per area of the studio: `home`, `podcasts`, `blogs`, `publications`, `notes`, `search`, and so on.
