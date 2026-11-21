# Contributing to Nudge Drive

Thanks for helping. This guide covers how the repository is organised, how to run each part, and
the conventions the code follows.

## Before you start

- For a bug, open an issue with steps to reproduce (use the bug report template).
- For a bigger change or a new feature, open an issue first so we can agree on the approach.
- Security problems: do not open an issue; follow [SECURITY.md](SECURITY.md).

## Repository layout

| Folder | What it is | Docs |
| --- | --- | --- |
| `backend/` | Express 5 API in TypeScript, Prisma schema and migrations | [docs/architecture.md](docs/architecture.md) |
| `frontend/` | Web app (React 19, Vite, Tailwind CSS 4) | |
| `apps/` | Gradle project: `:app` (Android), `:desktop` (Windows/macOS), `:shared` | [docs/building-apps.md](docs/building-apps.md) |
| `static/` | Files Caddy serves directly | [docs/self-hosting.md](docs/self-hosting.md) |

## Running things locally

**Backend** (Node.js 20+, MySQL 8+):

```bash
./setup.sh                 # or setup.ps1 on Windows: writes backend/.env with random secrets, installs, generates Prisma
cd backend
npm run prisma:migrate     # create / update the database schema
npm run dev                # http://localhost:4000
```

**Web app**:

```bash
cd frontend
npm install
npm run dev                # http://localhost:5173; /api is proxied to http://localhost:4000
```

Set `ND_API_PROXY` to point the dev server at another backend.

**Apps**: see [docs/building-apps.md](docs/building-apps.md).

## Checks before you open a pull request

There is no full test suite yet, so type-checking and building are the main checks:

- `cd backend && npm run build`
- `cd frontend && npm run build`
- `cd apps && ./gradlew :app:compileDebugKotlin :desktop:compileKotlin`
- `cd apps && ./gradlew :shared:desktopTest` (the LAN share tests)
- Schema changes: add a Prisma migration (`npm run prisma:migrate`), never edit an existing one.

Please also try the change by hand: sign in, connect a drive, upload, preview, share, and whatever
your change touches.

## Backend conventions

- The backend is ESM (`module: NodeNext`): relative imports need a `.js` suffix even in `.ts` files.
- Routes live in `backend/src/modules/<feature>/<feature>.routes.ts` and are mounted in `backend/src/app.ts`.
  Provider-specific code lives in `modules/google/` and `modules/s3/`.
- Use `requireAuth` for signed-in routes and `AuthRequest` to read `req.user`. Public-token routes stay
  outside `requireAuth` and must check the token hash, status and expiry.
- Validate request bodies and query strings with Zod.
- Use the Prisma client from `backend/src/config/prisma.ts`. Change the schema only through Prisma migrations.
- Return JSON errors with a stable `code` and a readable `message`; pass unexpected errors to `next(error)`.
- Convert `bigint` values to strings before sending JSON.
- Status columns are plain strings: use the constants in `backend/src/utils/states.ts`.
- Code that touches a stored file must handle both providers (`google_drive` and `s3`).

## Security rules

- Never commit `.env` files, keystores, `keystore.properties` or any secret.
- Never log access or refresh tokens, OAuth secrets, JWT secrets, encryption keys or raw share tokens.
- Google and S3 credentials are encrypted before they are stored; app refresh tokens, share tokens,
  preview tokens and API keys are stored as hashes. Keep it that way.
- Never put app access or refresh tokens in URL query strings (Google sign-in uses one-time hand-off tokens).
- Keep CORS restricted to `FRONTEND_URL`.

## Web app conventions

- Import files under `frontend/src` with the `@/` alias.
- Routes are registered in `frontend/src/App.tsx`.
- API calls go through `frontend/src/lib/api.ts`; it handles the access token refresh.
- Reuse the components in `frontend/src/components/ui/` before adding new primitives.
- The PWA service worker (`frontend/vite.config.ts`) has a `navigateFallbackDenylist` of server paths.
  Add any new top-level server path there.

## App conventions

- Code that both apps need (API client, models, repository, theme, shared components) goes in `apps/shared`.
- Every server endpoint the apps use is listed in `apps/shared/src/commonMain/kotlin/com/nudge/drive/data/Repository.kt`.
- Keep the Android and desktop behaviour in step when you change a shared feature.

## Style

- Small, focused pull requests are easier to review.
- Follow the style of the surrounding code; comments explain *why*, not *what*.
- Keep line endings as they are in the file you edit.
- Write user-facing text in plain, friendly English.

## License

By contributing, you agree that your contributions are licensed under the [Apache License 2.0](LICENSE).
