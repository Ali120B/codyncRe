# codyncRe — working notes

This is Ali120B's personal fork of Codync (upstream: `leepokai/Codync`). Keep it fast,
local-first, and independent of services this fork does not own.

## What ships

- `apps/desktop/` — Electron + React + TypeScript desktop app. Primary UI; one codebase
  for Linux, Linux and Windows.
- `host/` — Rust daemon: agents, sessions, SQLite, HTTP/SSE API, routines, marketplace.
- Keep Linux support. Android is upstream's for now; do not delete it without being asked.
- `cloud/`, `relay/`, `web/` are optional infrastructure. Do not silently point the app at
  upstream's cloud or replace its identity.
- Releases, tags and updates must come from `Ali120B/codyncRe`. Automatic host updates
  default off; never restore upstream updater URLs.

## Run and test

Two isolated profiles — never mix them:

| | Dev | Stable |
|---|---|---|
| Data | `~/.codync-dev` | `~/.codync` |
| Host | `codync-dev-host.service`, port 19223 | `codync-host.service`, port 19222 |
| App | `cd apps/desktop && npm run dev` | installed app |

- Host change: `cd host && cargo build`, then restart `codync-dev-host.service`.
- Desktop renderer changes hot-reload. Restart the app after `src/main`/preload changes.
- Checks: `cd apps/desktop && npx tsc --noEmit`; `cd host && cargo check`.
- The user tests the UI himself. Do not run Playwright/E2E/UI suites or long full test
  runs unless he asks. "Don't do useless tests."
- Back up `~/.codync*/codync.db` before anything that changes storage or schema.

## Product rules

- No fake or demo controls. Every visible control works, or it does not ship.
- No account is required for pairing or local use. Account/cloud sign-in is optional.
- Groups hold 2–50 bots. Removing the second-to-last bot offers to delete the group.
- A group member's Browser/PC switch is room-only, starts off, and never changes the bot's
  own settings. A bot's own switch also permits room use; show effective access.
- Group Computer tabs: Browser, Files, Skills, Vault, Routines. Bot Computer tabs:
  Computer, Browser, Files, Routines, Cloud.
- Computer panels use capsule tabs inside a grey rounded card; tabs fill the width.
- Editor footers stay fixed while the form scrolls. Bot: Cancel left, Done right.
  Group: Done white, Cancel subtle red, both bottom right.
- README is user-facing: mention real features, not every button or micro-fix.

## Code

- Two-space TypeScript, four-space Rust. Reuse `apps/desktop/src/renderer/components/`.
- Views render state; the host owns logic and validation. Add a host method rather than a
  client-only copy of behavior.
- Prefer direct file edits. Do not use throwaway Python scripts for simple edits.
- One logical change per commit, Conventional Commits. Do not reformat or revert unrelated
  work.
- Shipped code (`apps/`, `host/`, `packaging/`) gets a version bump: patch for fixes,
  minor for features, never major. Update `apps/desktop/package.json` (+ lock),
  `host/Cargo.toml` (+ lock), `apps/project.yml`, then rebuild the host.
- Docs, README and this file do not bump the version.
- Never commit secrets (Composio/Clerk keys, signing files, Firebase JSON, tokens).
