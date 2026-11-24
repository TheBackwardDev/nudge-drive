# Self-hosting Nudge Drive

This guide sets up your own Nudge Drive server with Docker Compose: MySQL, the backend, the web app,
Caddy for HTTPS and Collabora Online for in-app document editing.

Throughout this guide, replace `drive.example.com` with your own domain.

## What you need

- A Linux server with at least 2 CPU cores and 4 GB of RAM (Collabora alone may use up to 2 GB;
  without it, 2 GB is enough).
- Docker Engine with the Compose plugin, version 2.24 or newer (`docker compose version`).
- A domain or subdomain you control.
- A Google account for the Google Cloud console.
- Optional: an SMTP account for email verification (for example Gmail with an app password).

## 1. Point your domain at the server

Create a DNS `A` record (and `AAAA` if you use IPv6) for `drive.example.com` pointing to your
server's public IP address. Open ports **80** and **443** (TCP, and 443/UDP for HTTP/3) in your
firewall or cloud security group. Caddy needs both ports to get a certificate from Let's Encrypt.

Keep every other port closed. In production, only Caddy is published; MySQL, the backend, the
frontend and Collabora are reachable only inside the Docker network.

## 2. Set up Google Cloud

Nudge Drive uses one Google OAuth client for Google sign-in and for connecting Google Drive accounts.

1. Open the [Google Cloud console](https://console.cloud.google.com/) and create a project.
2. **APIs & Services → Library**: enable
   - **Google Drive API**
   - **Google Photos Picker API** (used by *Free up space → Google Photos*; you can skip it if you
     do not need that feature).
3. **APIs & Services → OAuth consent screen** (Google Auth Platform):
   - User type **External**.
   - App name, support email, and your domain as an authorised domain.
   - Scopes: `.../auth/drive`, `.../auth/userinfo.email`, `.../auth/userinfo.profile`, and, for
     Google Photos, `.../auth/photospicker.mediaitems.readonly`.
   - While the app is in **Testing** mode, add every Google account that will sign in as a test user
     (up to 100). The full `drive` scope is a *restricted* scope: to open the app to everyone you
     must publish it and pass Google's verification, which includes a security assessment.
4. **APIs & Services → Credentials → Create credentials → OAuth client ID**:
   - Application type **Web application**.
   - Authorised JavaScript origins: `https://drive.example.com`
   - Authorised redirect URIs:
     - `https://drive.example.com/api/connected-accounts/google/callback`
     - `https://drive.example.com/api/auth/google/callback`
   - Copy the **client ID** and **client secret**.

If you will build the Android app for your server, you also need an **Android** OAuth client in the
same project; see [building-apps.md](building-apps.md).

## 3. Set up email (optional)

Email is used for the 6-digit verification code after sign-up and for the account-deletion code.
If `SMTP_USER` and `SMTP_PASS` are empty, email verification is skipped and users who signed up with
a password confirm account deletion with their password.

With Gmail: turn on 2-step verification for the sending account, create an **app password**
(Google Account → Security → App passwords), and use:

```
SMTP_HOST=smtp.gmail.com
SMTP_PORT=465
SMTP_USER=sender@your-domain.example
SMTP_PASS=<the 16-character app password>
MAIL_FROM="Nudge Drive <sender@your-domain.example>"
```

Any other SMTP provider works too. Port 465 uses TLS from the start; other ports use STARTTLS.

## 4. Get the code and configure it

```bash
git clone https://github.com/TheBackwardDev/nudge-drive.git
cd nudge-drive
cp .env.docker.example .env
chmod 600 .env
```

Edit `.env`:

| Setting | Value |
| --- | --- |
| `DOMAIN` | `drive.example.com` |
| `FRONTEND_URL` | `https://drive.example.com` |
| `MYSQL_ROOT_PASSWORD`, `MYSQL_PASSWORD` | long random strings |
| `JWT_ACCESS_SECRET`, `TOKEN_ENCRYPTION_KEY` | long random strings (`openssl rand -hex 32`) |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | from step 2 |
| `GOOGLE_REDIRECT_URI` | `https://drive.example.com/api/connected-accounts/google/callback` |
| `ADMIN_EMAILS` | the email you will register with (comma-separate several) |
| `SMTP_*`, `MAIL_FROM` | from step 3, or leave empty |
| `COLLABORA_ADMIN_USER`, `COLLABORA_ADMIN_PASSWORD` | a login for the Collabora admin console |
| `VITE_RECAPTCHA_SITE_KEY`, `RECAPTCHA_SECRET_KEY` | optional reCAPTCHA v2 keys for the sign-up form |

Keep a safe copy of `TOKEN_ENCRYPTION_KEY`. It encrypts the Google and S3 credentials stored in the
database; if you lose or change it, every connected drive has to be connected again.

The full list of settings is in the [README](../README.md#configuration).

### About the Google credentials

When the backend starts, it saves `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` and
`GOOGLE_REDIRECT_URI` (encrypted) to the database, so **values in `.env` win on every restart**.
If you would rather manage them from the web app (Settings, admin section, visible to `ADMIN_EMAILS`
users), leave the placeholders `your-google-client-id` / `your-google-client-secret` in `.env`.

## 5. Static files for Caddy

Caddy serves two files from the `static/` folder:

- `static/editor-host.html`: host page for the desktop app's document editor. It is included; no
  changes needed.
- `static/assetlinks.json`: Android App Links, so the Android app can catch the Google sign-in
  redirect. Only needed if you build and sign the Android app for your own domain. Copy
  `static/assetlinks.example.json` to `static/assetlinks.json` and put in your package name and the
  SHA-256 fingerprint of your signing key (see [building-apps.md](building-apps.md)).

## 6. Start it

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```

The first build takes a few minutes. Then:

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml ps
docker compose -f docker-compose.yml -f docker-compose.prod.yml logs -f backend caddy
```

On every start the backend runs the database migrations (`prisma migrate deploy`), saves the Google
config if set, and starts listening. Caddy gets the HTTPS certificate on the first request.

Tip: to avoid typing both files every time, add `COMPOSE_FILE=docker-compose.yml:docker-compose.prod.yml`
to `.env`; then plain `docker compose up -d --build` uses both.

## 7. Create the first admin

1. Open `https://drive.example.com` and sign up with an email listed in `ADMIN_EMAILS` (or use
   **Continue with Google** with that Google account).
2. Connect a Google Drive. Its existing files are imported in the background.
3. In **Settings**, the admin section lets you check or change the Google OAuth credentials.

There are no other roles: every user manages only their own drives and files. `ADMIN_EMAILS` only
controls who may change server-wide settings.

## 8. Make it yours

The web app ships with the hosted instance's contact details and legal pages. Before you open your
server to other people, review and replace:

- `frontend/src/content/privacy-policy.md`, `terms-of-service.md`, `account-deletion.md`
- `SUPPORT_EMAIL` in `frontend/src/lib/links.ts` and `frontend/src/landing/Chrome.tsx`
- the `nudge.qd.je` host in `frontend/src/lib/markdown.tsx` (links to it stay inside the app) and the
  example link on `frontend/src/pages/LandingPage.tsx`

Then rebuild the frontend: `docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build frontend`.

## Backups

Your files live in Google Drive and S3, but the database holds the file list, folders, shares and
the encrypted credentials. Back up the database and your `.env`.

```bash
# Dump (run from the project folder; the password is read inside the container)
docker compose exec -T mysql sh -c 'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction "$MYSQL_DATABASE"' \
  | gzip > backup-$(date +%F).sql.gz

# Restore into the running database
gunzip -c backup-2026-01-01.sql.gz \
  | docker compose exec -T mysql sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"'
```

Run the dump from cron (for example nightly) and copy the files off the server. The backup and
restore buttons in the admin settings come from the original project and expect a database file
on disk; with MySQL, use `mysqldump` as shown.

## Updating

```bash
cd nudge-drive
git pull
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```

Migrations run automatically when the new backend starts. Take a backup first. `docker image prune`
removes old images afterwards.

## Running without Caddy or Collabora

- **Local test**: `docker compose up -d --build` starts only MySQL, the backend (port 4000) and the
  web app (port 5173). Keep the localhost values in `.env`. In-app editing is not available.
- **Your own reverse proxy**: route `/api/*` to the backend (port 4000, with the `/api` prefix
  removed), `/browser/*`, `/hosting/*`, `/cool/*` and `/lool/*` to Collabora (port 9980, with
  websocket support), `/editor-host.html` and `/.well-known/assetlinks.json` to the files in
  `static/`, and everything else to the frontend. Turn off request buffering for `/api` so large
  uploads stream.

## Troubleshooting

- **Google says `redirect_uri_mismatch`**: the redirect URI in Google Cloud must match
  `GOOGLE_REDIRECT_URI` exactly, including `https` and `/api`.
- **"Access blocked: app has not completed verification"**: add the account as a test user on the
  OAuth consent screen, or complete Google's verification.
- **No certificate**: check that the DNS record points at the server and ports 80/443 are open, then
  look at `docker compose ... logs caddy`.
- **Editor does not open**: check `docker compose ... logs collabora`; the server needs enough memory,
  and `DOMAIN` must match the domain in the browser.
- **Verification emails do not arrive**: check `docker compose ... logs backend` for SMTP errors; Gmail
  needs an app password, not the account password.
