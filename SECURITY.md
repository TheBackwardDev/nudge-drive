# Security policy

## Reporting a vulnerability

Please do **not** open a public issue for security problems.

Email **support@nudge.qd.je** with:

- what the problem is and where (file, endpoint or screen),
- steps to reproduce it, or a proof of concept,
- what an attacker could do with it.

You will get a reply within a few days. Please give us a reasonable time to fix the problem
before you share details publicly. We will credit you in the release notes if you want.

## Scope

- The server (`backend/`), the web app (`frontend/`), the Android and desktop apps (`apps/`) and the
  deployment files in this repository.
- The hosted instance at https://nudge.qd.je. Do not access other people's data, run load or
  denial-of-service tests, or use automated scanners against it. Test against your own instance
  where you can.

## Supported versions

Only the latest code on the default branch gets security fixes.

## For self-hosters

- Keep `.env` private and use long random values for `JWT_ACCESS_SECRET`, `TOKEN_ENCRYPTION_KEY`
  and the database passwords.
- Keep the backend, MySQL and Collabora off the public internet; only Caddy should be exposed
  (this is what `docker-compose.prod.yml` does).
- Update regularly (`git pull`, then rebuild the containers).
