# Plan

## Next setup (deferred)

- Own Clerk + Cloudflare setup instead of the upstream dev Clerk instance.
  - Create a Clerk application we control; use it for Google sign-in.
  - Use our own Clerk domain, desktop callback (`com.pokai.Codync.dev://callback`) and
    `dev-api.codync.dev`/relay hostname behind Cloudflare.
  - Keep local use account-free; account sign-in stays optional.
  - Never commit Clerk/Cloudflare secrets.

## Liked backlog

- Live session handoff between bots (keep as a later feature; not part of the current batch).

## Current batch

- Needs-you inbox above the account/plugins footer.
- App-wide command deck on `⌘K`.
- Per-turn file + chat rewind in chats and groups.
- Automatic handoff packets when a bot @mentions another in a room.
- `@all` room standup with one merged status/blocker message and owners.
