# Plan

## Next: Android

Build the native Android app, keep iOS until the parity gate is signed off, then remove
`apps/ios/`. Detailed phases, stack, host/cloud changes and the removal gate:
[docs/design/android-app.md](docs/design/android-app.md).

## Dev infrastructure (done)

- Worker: `https://codync-cloud-dev.codyncre.workers.dev` (`codync-cloud-dev`, workers.dev).
- D1: `codync-dev` (`f1a063d1-5b1e-4534-99bc-7231ca1b6d31`), migrations applied.
- Clerk: development app `codync dev`, issuer
  `https://sweeping-marmoset-6208.clerk.accounts.dev`, Google enabled.
- `apps/shared/Config/dev.plist` and the dev host point at this Worker.
- Worker secrets `CLERK_SECRET_KEY` and `CLERK_WEBHOOK_SECRET` are set; the Clerk
  `user.deleted` webhook points at `/v1/webhooks/clerk`.
- Dev desktop signed in and this computer claimed to the Clerk account.

## Production infrastructure (later)

- Custom domains instead of workers.dev.
- Clerk production instance with our own Google OAuth credentials.
- Apply migrations to the production D1 database, set production Worker secrets.
- Change the main cloud URL in `host/src/environment.rs` and `apps/shared/Config/main.plist`.

Never commit Clerk secret keys, webhook secrets, Cloudflare tokens, `google-services.json` or
service-account JSON.

## Shipped in 2.20.0

- Needs-you inbox above the account/plugins footer.
- App-wide command deck on `⌘K`.
- Per-turn file + chat rewind in chats and groups.
- Automatic handoff packets when a bot @mentions another in a room.
- `@all` room standup with one merged status/blocker message and owners.
