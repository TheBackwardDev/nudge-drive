<h1 align="center">Nudge Drive</h1>

<p align="center">
  Pool several Google Drive accounts and S3-compatible buckets into one storage space.<br />
  Web app, Android app and desktop app (Windows / macOS), with a self-hostable server.
</p>

<p align="center">
  <a href="LICENSE"><img alt="License: Apache 2.0" src="https://img.shields.io/badge/license-Apache%202.0-4f46e5" /></a>
  <img alt="Node.js 20+" src="https://img.shields.io/badge/node-20%2B-339933?logo=node.js&logoColor=white" />
  <img alt="MySQL 8" src="https://img.shields.io/badge/mysql-8-4479A1?logo=mysql&logoColor=white" />
  <img alt="Kotlin Compose Multiplatform" src="https://img.shields.io/badge/kotlin-compose%20multiplatform-7F52FF?logo=kotlin&logoColor=white" />
</p>

Every Google account comes with 15 GB of free storage. If you have a few accounts, you have plenty of
space, but it is split up and one account always fills up first. Nudge Drive joins them: connect your
Google Drive accounts (and any S3-compatible bucket), and you get one storage pool with one file
manager, one photo timeline and one upload button. Each upload goes to a drive that has room.

Nudge Drive is free. A hosted instance runs at **https://nudge.qd.je**. You can also run your own
server with Docker Compose (see [Self-hosting](#self-hosting-quick-start)).

Your files stay in your own Google Drive and S3 storage. The server keeps a list of them (names,
sizes, folders) and passes file data between your devices and your storage.

## Download

<p align="center">
  <a href="https://github.com/TheBackwardDev/nudge-drive/releases/latest/download/NudgeDrive-Android.apk"><img alt="Download for Android (APK)" src="https://img.shields.io/badge/Android-Download%20APK-3DDC84?style=for-the-badge&logo=android&logoColor=white" /></a>
  <a href="https://github.com/TheBackwardDev/nudge-drive/releases/latest/download/NudgeDrive-Windows.exe"><img alt="Download for Windows" src="https://img.shields.io/badge/Windows-Download%20.exe-0078D4?style=for-the-badge&logo=windows&logoColor=white" /></a>
  <a href="https://github.com/TheBackwardDev/nudge-drive/releases/latest/download/NudgeDrive-macOS.dmg"><img alt="Download for macOS" src="https://img.shields.io/badge/macOS-Download%20.dmg-000000?style=for-the-badge&logo=apple&logoColor=white" /></a>
  <a href="https://nudge.qd.je"><img alt="Open the web app (works on iPhone)" src="https://img.shields.io/badge/iPhone%20%26%20Web-Open%20web%20app-7059F0?style=for-the-badge&logo=safari&logoColor=white" /></a>
</p>

| Platform | How to get it |
| --- | --- |
| **Android** 8.0+ | [Download the APK](https://github.com/TheBackwardDev/nudge-drive/releases/latest/download/NudgeDrive-Android.apk), open it and allow "Install unknown apps" when asked. |
| **Windows** 10/11 | [Download the installer (.exe)](https://github.com/TheBackwardDev/nudge-drive/releases/latest/download/NudgeDrive-Windows.exe) or the [.msi](https://github.com/TheBackwardDev/nudge-drive/releases/latest/download/NudgeDrive-Windows.msi). If SmartScreen warns you, choose **More info → Run anyway**. |
| **macOS** 12+ (Apple silicon) | [Download the .dmg](https://github.com/TheBackwardDev/nudge-drive/releases/latest/download/NudgeDrive-macOS.dmg). The first time, right-click the app and choose **Open**. |
| **iPhone / iPad** | There is no iOS app yet. Open **https://nudge.qd.je** in Safari, tap **Share**, then **Add to Home Screen**; it opens like an app. |
| **Web** | Use it in any browser at **https://nudge.qd.je**. |

All versions and release notes are on the [Releases page](https://github.com/TheBackwardDev/nudge-drive/releases).
The installers are not code-signed yet, which is why Windows and macOS show a warning the first time.

## Demo video

<p align="center">
  <a href="docs/media/nudge-drive-demo.mp4">
    <img src="docs/media/nudge-drive-demo-poster.png" alt="Nudge Drive demo video: click to play" width="100%" />
  </a>
  <br />
  <sub>Click the picture to open the video (<a href="docs/media/nudge-drive-demo.mp4"><code>docs/media/nudge-drive-demo.mp4</code></a>).</sub>
</p>

## Screenshots

<table>
  <tr>
    <td colspan="2"><img src="docs/media/screens/desk-home-light.webp" alt="Web app home page with the storage pool and recent files" /><br /><sub><b>Web / desktop home</b>: the storage pool, your drives and recent files.</sub></td>
  </tr>
  <tr>
    <td colspan="2"><img src="docs/media/screens/desk-photos-dark.webp" alt="Photo timeline in dark mode" /><br /><sub><b>Photos</b>: every photo and video from every drive, grouped by the month it was taken.</sub></td>
  </tr>
</table>

<table>
  <tr>
    <td width="25%"><img src="docs/media/screens/phone-home.webp" alt="Android home screen" /><br /><sub><b>Home</b></sub></td>
    <td width="25%"><img src="docs/media/screens/phone-drives.webp" alt="Android drives screen" /><br /><sub><b>Drives</b> and upload rule</sub></td>
    <td width="25%"><img src="docs/media/screens/phone-photos.webp" alt="Android photo timeline" /><br /><sub><b>Photos</b></sub></td>
    <td width="25%"><img src="docs/media/screens/phone-viewer.webp" alt="Android file viewer" /><br /><sub><b>Viewer</b></sub></td>
  </tr>
  <tr>
    <td><img src="docs/media/screens/phone-backup.webp" alt="Android auto backup settings" /><br /><sub><b>Auto backup</b></sub></td>
    <td><img src="docs/media/screens/phone-nearby.webp" alt="Android Nearby share result" /><br /><sub><b>Nearby share</b></sub></td>
    <td></td>
    <td></td>
  </tr>
</table>

## Features

**Storage pool**
- Connect any number of Google Drive accounts and S3-compatible buckets (AWS S3, Cloudflare R2, MinIO, Wasabi, Backblaze B2, or a custom endpoint).
- See the combined free space and what uses it (photos, videos, documents), plus each drive's own quota.
- Upload rules: **most free space** (default), **round robin**, or **your own order**.
- When you connect a Google Drive, the files already on it are imported in the background, so you see everything, not only what Nudge Drive uploaded.

**Files and photos**
- Upload, browse, search, rename, move, download, trash and restore. Folders can be nested, coloured and given icons.
- Previews for images, video, audio, PDF and Office / Google documents, with fast thumbnails.
- Photo timeline of every photo and video across all drives, ordered by the date it was taken.
- In-app editing of Word, Excel and PowerPoint files (and Google Docs, Sheets and Slides) with a self-hosted Collabora Online server.
- Activity log and a notifications inbox.

**Sharing**
- Public links with a viewer page, which you can turn off at any time.
- Invite people by email to a file or folder.
- Share files out to other apps from the phone, and save files shared from other apps into Nudge Drive.

**Free up space**
- Move files off a full Google Drive to your other drives. Google-to-Google moves are copies made inside Google, so no file data passes through the server.
- Google Photos: pick photos in Google's own Photos Picker, Nudge Drive copies the originals to your other drives, then guides you through deleting them in Google Photos to get the space back. (Google does not let apps delete from Google Photos.)
- Empty the Drive trash for an account in one step.

**Phone (Android)**
- Auto backup of the phone folders you choose (Camera, WhatsApp, Downloads and so on), only new files each time.
- Nearby share: send files phone to phone over Bluetooth / Wi-Fi (Google Nearby Connections), or phone to PC over the local Wi-Fi network with an encrypted connection.
- Native Google sign-in with the phone's account picker.

**Desktop (Windows and macOS)**
- The same app on a big screen, built with Compose Multiplatform and sharing code with Android.
- Auto backup of PC folders (only new or changed files are uploaded).
- Nearby share with phones on the same network, in-app document editor, Google sign-in through the system browser.

**Web**
- Full file manager in the browser, installable as a PWA, light and dark themes.
- Landing page, privacy policy, terms and account-deletion pages.

**Accounts and API**
- Email and password sign-up with email verification (when SMTP is configured) and optional reCAPTCHA, or Google sign-in.
- Account deletion from the app or the web, with all server-side data removed.
- API keys for an upload endpoint (`POST /api/v1/uploads`) to send files from scripts or CI.

## Architecture

```mermaid
flowchart LR
    subgraph Clients
        W[Web app<br/>React + Vite]
        A[Android app<br/>Kotlin + Compose]
        D[Desktop app<br/>Compose Multiplatform]
    end

    subgraph Server["Your server (Docker Compose)"]
        C[Caddy<br/>HTTPS reverse proxy]
        F[frontend<br/>nginx serving the web build]
        B[backend<br/>Node.js, Express, TypeScript, Prisma]
        M[(MySQL 8)]
        CO[Collabora Online<br/>document editor]
    end

    G[(Google Drive API<br/>Google Photos Picker API)]
    S[(S3-compatible storage)]

    W & A & D -->|HTTPS| C
    C -->|/| F
    C -->|/api/*| B
    C -->|/browser, /cool, ...| CO
    CO -->|WOPI| B
    B --> M
    B --> G
    B --> S
```

The backend never keeps file contents on disk (except a small cache of preview thumbnails). Folders
exist only in the database; files are stored flat in a `Nudge Drive` folder (Google Drive) or a `nudge-drive` prefix (S3).
See [docs/architecture.md](docs/architecture.md) for the data model, sync, upload routing, free up
space and the recovery sweep.

## Tech stack

| Part | Stack |
| --- | --- |
| Backend | Node.js 20, Express 5, TypeScript, Prisma 6, MySQL 8, Zod, googleapis, AWS SDK v3, Argon2, JWT, Nodemailer |
| Web | React 19, Vite 8, TypeScript, React Router 7, Tailwind CSS 4, vite-plugin-pwa |
| Android | Kotlin, Jetpack Compose, Material 3, OkHttp, Coil, Media3, WorkManager, Credential Manager, Nearby Connections |
| Desktop | Kotlin, Compose Multiplatform (JVM), KCEF (embedded Chromium for the editor), JavaFX media |
| Infra | Docker Compose, Caddy 2 (automatic HTTPS), nginx, Collabora Online CODE |

## Project layout

```
.
├── backend/                 Express API (TypeScript), Prisma schema and migrations
├── frontend/                Web app (React + Vite), served by nginx in Docker
├── apps/                    Gradle project for the native apps
│   ├── app/                 Android app
│   ├── desktop/             Windows / macOS app
│   └── shared/              Code shared by both (API client, models, theme, components)
├── static/                  Files Caddy serves as-is (editor host page, assetlinks example)
├── docs/                    Self-hosting, architecture and app build guides
├── docker-compose.yml       MySQL + backend + frontend
├── docker-compose.prod.yml  Adds Caddy (HTTPS) and Collabora for production
├── Caddyfile                Reverse proxy config (uses the DOMAIN variable)
├── .env.docker.example      All settings, with placeholders
└── setup.sh / setup.ps1     Local development setup without Docker
```

## Self-hosting quick start

You need a Linux server with Docker (Compose 2.24 or newer), a domain pointing at it, and a Google
Cloud OAuth client. The full walkthrough is in [docs/self-hosting.md](docs/self-hosting.md).

```bash
git clone https://github.com/TheBackwardDev/nudge-drive.git
cd nudge-drive
cp .env.docker.example .env
# Edit .env: DOMAIN, FRONTEND_URL, secrets, Google OAuth client, ADMIN_EMAILS, SMTP
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```

Then open `https://<your domain>`, create an account with an email listed in `ADMIN_EMAILS`, and
connect a Google Drive.

To try it on your own computer without a domain, run only the base file:

```bash
cp .env.docker.example .env      # keep the localhost values
docker compose up -d --build     # web: http://localhost:5173, API: http://localhost:4000
```

## Building the apps

The Android and desktop apps live in `apps/` (one Gradle project, JDK 17).

```bash
cd apps
./gradlew :app:assembleDebug          # Android debug APK
./gradlew :desktop:run                # run the desktop app
./gradlew :desktop:packageReleaseMsi  # Windows installer (build on Windows)
./gradlew :desktop:packageReleaseDmg  # macOS installer (build on a Mac)
```

The apps talk to `https://nudge.qd.je` by default. To use your own server, change the server URL
and set up your own Google OAuth Android client; see [docs/building-apps.md](docs/building-apps.md).
GitHub Actions builds every app for you: push a tag like `v1.9.4` and `.github/workflows/release.yml`
builds the Android APK, the Windows installers and the macOS DMG and publishes them as a release.

## Configuration

Set these in `.env` (Docker) or `backend/.env` (running the backend directly). Never commit real values.

| Variable | Required | Description |
| --- | --- | --- |
| `DOMAIN` | production | Public domain; used by Caddy and Collabora (`docker-compose.prod.yml`). |
| `FRONTEND_URL` | yes | Public URL of the web app, e.g. `https://drive.example.com`. Used for CORS and redirects. |
| `VITE_API_URL` | no | Not used by the current web app, which always calls `/api` on its own origin (nginx and Caddy route it to the backend). Kept as a build argument for compatibility. |
| `VITE_RECAPTCHA_SITE_KEY` | no | reCAPTCHA v2 site key for the sign-up form. Empty turns the captcha off. |
| `RECAPTCHA_SECRET_KEY` | no | reCAPTCHA secret; the server checks captchas only when this is set. |
| `MYSQL_ROOT_PASSWORD`, `MYSQL_DATABASE`, `MYSQL_USER`, `MYSQL_PASSWORD` | yes (Docker) | MySQL container settings; the backend `DATABASE_URL` is built from them. |
| `DATABASE_URL` | yes (no Docker) | `mysql://user:password@host:3306/database`. |
| `APP_PORT` | no | Backend port (default `4000`). |
| `JWT_ACCESS_SECRET` | yes | Signs access tokens. At least 32 random characters. |
| `TOKEN_ENCRYPTION_KEY` | yes | Encrypts Google / S3 credentials in the database. At least 32 characters. Keep it safe: changing it makes stored tokens unreadable. |
| `ACCESS_TOKEN_TTL_SECONDS` | no | Access token lifetime (default `900`; Docker example uses `3600`). |
| `REFRESH_TOKEN_TTL_DAYS` | no | Refresh token lifetime (default `30`). |
| `MAX_UPLOAD_BYTES` | no | Largest single upload (default 5 GB). |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | yes, for Google | OAuth "Web application" client. Saved (encrypted) to the database on start; can also be set later in the admin settings. |
| `GOOGLE_REDIRECT_URI` | yes, for Google | `https://<domain>/api/connected-accounts/google/callback` behind Caddy. |
| `GOOGLE_APP_FOLDER` | no | Folder in each Google Drive that new uploads go into. Default `Nudge Drive`. When upgrading an existing install, set it to the folder name already in use. |
| `ADMIN_EMAILS` | recommended | Comma-separated emails of users allowed to change server-wide settings (for example the Google OAuth credentials). |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `SMTP_PASS`, `MAIL_FROM` | no | Email for verification and account-deletion codes. With `SMTP_USER`/`SMTP_PASS` empty, email verification is skipped. |
| `COLLABORA_ADMIN_USER`, `COLLABORA_ADMIN_PASSWORD` | production | Login for the Collabora admin console. |
| `PREVIEW_CACHE_DIR` | no | Thumbnail cache folder (default `/tmp/nudge-preview-cache`; set to a volume in production compose). |
| `COLLABORA_URL`, `WOPI_BASE` | no | Internal Collabora and WOPI addresses (defaults `http://collabora:9980` and `http://backend:4000/wopi/files`). |

## Contributing

Bug reports, fixes and ideas are welcome. Please read [CONTRIBUTING.md](CONTRIBUTING.md) first.
Report security problems privately as described in [SECURITY.md](SECURITY.md).

## Credits

Parts of the server code are derived from an earlier Apache-2.0 licensed project. Its copyright
notice is kept in [LICENSE](LICENSE), as the license requires. See [NOTICE](NOTICE) for details.

## License

[Apache License 2.0](LICENSE). See [NOTICE](NOTICE) for attribution.
