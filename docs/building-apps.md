# Building the Android and desktop apps

The native apps are one Gradle project in `apps/`:

| Module | What it is |
| --- | --- |
| `:app` | Android app (Jetpack Compose), package `com.nudge.drive` |
| `:desktop` | Windows / macOS app (Compose Multiplatform on the JVM) |
| `:shared` | Code used by both: API client, models, repository, theme, shared UI components, LAN share |

## Requirements

- JDK 17 (`JAVA_HOME` pointing at it).
- For Android: the Android SDK with platform 36 (Android Studio installs it). Tell Gradle where the
  SDK is with `ANDROID_HOME`, or a `apps/local.properties` file containing
  `sdk.dir=/path/to/android-sdk` (this file is ignored by git).
- About 4 GB of free RAM for Gradle (`apps/gradle.properties` sets `-Xmx4g`; lower it if needed,
  for example `./gradlew --max-workers=2 -Dorg.gradle.jvmargs=-Xmx3g …`).

All commands below run in `apps/` (use `gradlew.bat` on Windows).

## Point the apps at your server

By default the apps use the hosted instance at `https://nudge.qd.je`. To use your own server, change
these places to your domain:

1. `apps/app/build.gradle.kts`: the `API_BASE` and `WEB_BASE` build config fields (Android).
2. `apps/shared/src/commonMain/kotlin/com/nudge/drive/Endpoints.kt`: `API_BASE` and `WEB_BASE`
   (desktop, and thumbnail URLs on both platforms). Keep the two files in step.
3. `apps/app/src/main/AndroidManifest.xml`: the two `android:host` values of the App Links intent
   filter (`/google-auth` and `/google-connected`). These let the Android app catch the Google
   sign-in redirect from your domain.

`API_BASE` is `https://<your domain>/api` and `WEB_BASE` is `https://<your domain>`.

If you publish your own build, also change `applicationId` in `apps/app/build.gradle.kts` (and, for
the desktop app, `bundleID` and `upgradeUuid` in `apps/desktop/build.gradle.kts`) so your app does
not clash with the official one.

## Google sign-in on Android

The Android app signs in with the phone's own Google account picker (Credential Manager). It asks
your server for the OAuth **web** client ID (`GET /auth/google/native-config`), so there is no client
ID to put in the app. Google only accepts the request if the same Google Cloud project also has an
**Android** OAuth client for your app:

1. Get the SHA-1 fingerprint of each key you sign with:
   ```bash
   # Debug key (used by debug builds)
   keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
   # Your release / upload key
   keytool -list -v -keystore /path/to/your-release.jks -alias your-alias
   ```
   If you publish on Google Play with Play App Signing, also use the **app signing key** fingerprint
   shown in Play Console → Setup → App integrity.
2. In Google Cloud → Credentials, create an **OAuth client ID** of type **Android** for each
   fingerprint, with your package name (`com.nudge.drive` or your own `applicationId`).
3. For App Links, publish `https://<your domain>/.well-known/assetlinks.json` with the **SHA-256**
   fingerprints of the same keys (see `static/assetlinks.example.json` and
   [self-hosting.md](self-hosting.md#5-static-files-for-caddy)).

The desktop app needs no extra client: it opens Google's sign-in page in the system browser and gets
the result back on a loopback address through your server.

## Android builds

```bash
./gradlew :app:assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:installDebug         # install on a connected phone or emulator
./gradlew :app:assembleRelease      # optimised APK (R8)
./gradlew :app:bundleRelease        # Android App Bundle for Google Play
```

Set `versionCode` and `versionName` in `apps/app/build.gradle.kts` before a release.

### Release signing

Release builds are signed with the key described in `apps/keystore.properties`. Create that file
(it is ignored by git, never commit it) next to `settings.gradle.kts`:

```properties
storeFile=/absolute/path/to/your-upload-key.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Without the file, release builds fall back to the debug key, which is fine for testing but cannot be
published. Create a key with Android Studio (Build → Generate Signed Bundle / APK) or with
`keytool -genkeypair -v -keystore upload.jks -keyalg RSA -keysize 2048 -validity 10000 -alias upload`.
Keep the keystore and its passwords backed up outside the repository.

## Desktop builds

```bash
./gradlew :desktop:run                       # run from source
./gradlew :desktop:packageReleaseMsi         # Windows .msi  (run on Windows)
./gradlew :desktop:packageReleaseExe         # Windows .exe  (run on Windows)
./gradlew :desktop:packageReleaseDmg         # macOS .dmg    (run on macOS)
./gradlew :desktop:packageReleaseMsi -PdesktopVersion=1.2.3   # set the version
```

Installers are written to `apps/desktop/build/compose/binaries/main-release/`. Each installer must be
built on its own operating system (jpackage cannot cross-build). The installers bundle a Java
runtime, so users do not need Java.

Set the environment variable `NUDGE_DEBUG=1` when running or packaging to turn on extra debug logging.

The in-app document editor uses an embedded Chromium (KCEF) that is downloaded on first use (about
100 MB), and loads `https://<your domain>/editor-host.html`, which the server serves from `static/`.

### Building installers on GitHub

`.github/workflows/release.yml` builds everything for a release: push a tag like `v1.3.0` (or run it
from the Actions tab) and it builds the Android APK, the Windows MSI and EXE and the macOS DMG, then
publishes them as a GitHub Release. To sign the APK with your own key, add the repository secrets
`ANDROID_KEYSTORE_BASE64` (the .jks file in base64), `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`
and `ANDROID_KEY_PASSWORD`; without them the APK is signed with a throwaway debug key.

For a one-off desktop build without a release, `.github/workflows/desktop.yml` builds the Windows installers (and optionally the macOS DMG) on
GitHub's runners. Run it from the Actions tab (**Run workflow**, enter a version) or push a tag like
`desktop-v1.2.3`. The installers are attached to the workflow run as artifacts. The installers are not
code-signed; Windows SmartScreen and macOS Gatekeeper will warn users until you add signing.

## Tests and checks

```bash
./gradlew :app:compileDebugKotlin :desktop:compileKotlin   # quick compile check of both apps
./gradlew :shared:desktopTest                              # LAN share tests
```
