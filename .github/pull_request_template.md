## What this changes

<!-- A short description, and the issue it fixes, e.g. "Fixes #123". -->

## How it was tested

<!-- What you ran and what you tried by hand. -->

## Checklist

- [ ] `cd backend && npm run build` passes (if the backend changed)
- [ ] `cd frontend && npm run build` passes (if the web app changed)
- [ ] `cd apps && ./gradlew :app:compileDebugKotlin :desktop:compileKotlin` passes (if the apps changed)
- [ ] Schema changes come with a new Prisma migration
- [ ] No secrets, `.env` files, keystores or personal data are included
- [ ] User-facing text is clear and in plain English
