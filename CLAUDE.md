# Claude notes

`AGENTS.md` is the source of truth for this fork. Read it first.

Quick reminders:

- Dev profile: `~/.codync-dev`, host on 19223, app via `cd apps/desktop && npm run dev`.
  Stable profile: `~/.codync`, host on 19222. Never mix them.
- The user tests the UI. Run typecheck/targeted builds, not UI or full E2E suites, unless
  asked.
- Shipped code gets a 2.x version bump; docs and README do not.
- Keep releases and the updater on `Ali120B/codyncRe`.
- Prefer direct edits over throwaway scripts, and keep changes scoped.
