# Codync relay

Cloudflare Worker that forwards APNs and FCM pushes for codync-host. Phones exchange their
provider token for an AES-GCM **ticket** (`POST /register`); hosts only ever see tickets
(`POST /push`). A ticket is a bearer capability: anyone holding it can ask this Worker to push
to its device. Only the Worker can decrypt the raw provider token. Do not log tickets.

## Deploy

```bash
npm install
npx wrangler login
npx wrangler whoami                         # confirm this is your Codync Cloudflare account
npm run deploy                              # creates codync-relay in this account
openssl rand -base64 32 | npx wrangler secret put TICKET_KEY
npx wrangler secret put FCM_SERVICE_ACCOUNT # paste the full Firebase service-account JSON when prompted
```

The Android-only setup needs `TICKET_KEY` and `FCM_SERVICE_ACCOUNT`. Add the following secrets
only if configuring iOS push: `APNS_TEAM_ID`, `APNS_KEY_ID`, and `APNS_SIGNING_KEY`.

Android's Firebase config is flavor-specific at
`apps/android/app/src/dev/google-services.json` and
`apps/android/app/src/prod/google-services.json`. Keep those files out of git too. The Worker
accepts Android registration tickets, then sends data-only FCM messages; the app decrypts the
host-sealed alert before it posts a notification. Set `FCM_SERVICE_ACCOUNT` to the Codync Android
Firebase service-account JSON; do not check in that credential.

It deploys as `codync-relay`. In Ali120B's Cloudflare account, Android uses
`https://codync-relay.codyncre.workers.dev`. Rotating `TICKET_KEY` invalidates every ticket;
phones re-register when they connect to a host.

## Test

```bash
npm test        # ticket seal/open round trip, `mutableContent` → `mutable-content: 1`
npm run typecheck
```

## Notifications and Live Activities

See the [notification design](../docs/design/push-and-live-activity.md) for event
copy, encrypted content, ActivityKit payloads, configuration and device acceptance.
The Worker handles alerts and updates/ends for activities started on the phone.
Dynamic Island shares the ActivityKit update stream. This Worker does not start
activities remotely or refresh Home Screen widgets.
