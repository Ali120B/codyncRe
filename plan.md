# Plan

## Next: Android

Build the native Android app, keep iOS until the parity gate is signed off, then remove
`apps/ios/`. Detailed phases, stack, host/cloud changes and the removal gate:
[docs/design/android-app.md](docs/design/android-app.md).

## Then: own Clerk + Cloudflare

Fast path first; custom domains and production polish come after the dev path works.

- **Cloudflare**
  - Create/choose the Cloudflare account and run `npx wrangler login` from `cloud/`.
  - Create a D1 database `codync-dev`; put its real `database_id` in `[env.dev]`.
  - For the fastest start, deploy the dev Worker on `workers.dev` (add `workers_dev = true`,
    remove the `dev-api.codync.dev` route). No DNS wait.
  - Apply migrations, then set `CLERK_SECRET_KEY` and `CLERK_WEBHOOK_SECRET`.
  - Deploy with `npx wrangler deploy --env dev`; check `/health`.
- **Clerk**
  - Create our own Clerk application (start with a Development instance).
  - Enable Google sign-in; create the native Android app once its package ID is fixed.
  - Copy the publishable key and the Frontend API URL into app config.
  - Add the Worker webhook `https://<worker>/v1/webhooks/clerk` for `user.deleted`; copy its
    signing secret to the Worker.
- **Point clients at it**
  - Update `apps/shared/Config/dev.plist` (Clerk publishable key + cloud URL).
  - For dev, point the host with `CODYNC_CLOUD_URL`; for release builds, change the dev/main
    URL in `host/src/environment.rs`.
  - Keep local pairing account-free.
- Never commit Clerk secret keys, webhook secrets, Cloudflare tokens, `google-services.json` or
  service-account JSON.

Custom domains (`clerk.<domain>`, `api.<domain>`) and a production Clerk instance are a second
step, once the dev flow is proven.

## Shipped in 2.20.0

- Needs-you inbox above the account/plugins footer.
- App-wide command deck on `⌘K`.
- Per-turn file + chat rewind in chats and groups.
- Automatic handoff packets when a bot @mentions another in a room.
- `@all` room standup with one merged status/blocker message and owners.
