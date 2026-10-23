# Plan

## Now: Android app

Android is the next product, not an iOS port. It has to cover the iPhone app **and** every
desktop/host feature added since the fork. Scope is locked to one complete v1; there is no
public v1.0/v1.1 split.

No `apps/android/` code starts until Ali says go.

Detailed architecture, security, transport and test plan:
[docs/design/android-app.md](docs/design/android-app.md).

Version **3.0.0** is pushed on `main` and tagged. It marks the start of the Android line; the
host and desktop must stay on the same version.

## Decisions locked 2026-10-21

- **One all-at-once v1.** Build milestones are internal; no partial public release.
- **v1 = every phone feature plus every fork feature that applies to a phone.**
- **v2 only:** voice calls, remote screen, home-screen widgets/ongoing status and
  tablet/foldable layouts.
- **Multi-account switching is not planned.** One signed-in account or local-only pairing.
- **iOS stays until Ali explicitly says remove it.** Passing tests is not permission.
- **FCM gets a new Firebase project for Codync Android.**
- Native Kotlin + Compose, Android 12+, real glass, three tabs, iOS layout, Inter,
  Material Symbols Rounded, English, phone-first, sideloaded APK first.

## Android v1 — one complete release

V1 is the complete phone app for daily work, not a cut-down chat client. It includes the
fork-only chat, group, workbench and computer features that the iPhone never had. The build is
split into internal milestones below for risk control, but all of them ship as v1.

### Connect

- Optional Google sign-in through Clerk; local QR pairing works signed out.
- One active account context; multiple computers per account. Access requests, six-digit SAS
  approval, revoke/remove device, computer list and cloud link status.
- Direct LAN/Tailscale and encrypted Cloudflare relay transports; reconnect from the last
  host revision after network changes and process death.
- Offline reading from the cache; sends wait for a connection instead of pretending to queue.
- FCM registration, encrypted notifications, deep links and per-bot notification preferences.

### Roster and bots

- All computers grouped iOS-style, connection summary, show-only filters, collapsed sections,
  pin/hide/reorder and search.
- Bot rows with character avatars, status/activity, last message, unread and working state.
- Bot editor: name, description, character/avatar, instructions, agent, model, model options,
  Fast mode, workspace, approval mode, tools, skills, connectors, browser/computer access,
  notifications and memory.
- New bot from template, edit, delete, new session, new chat and reveal folder.
- Dynamic agent/model discovery (ACP registry, Cline, custom IDs); no hardcoded provider list.
- Workspace picker against the computer's filesystem, chosen once and remembered.
- Team map: bots and groups as a ring with who-asked-whom edges, group membership and
  click-through to the chat.

### Chats, threads and groups

- Main chat plus multiple chats per bot/group, each with its own context, title, last line and
  delete action; New chat; New session (fresh agent context, same transcript); roster follows
  the open chat.
- Streaming timeline, final replies, markdown, code blocks, tool/trace view and full
  conversation, copy, reactions, read/unread, history paging and jump-to-latest.
- Reply threads with unread counts, thread composer, root summaries and per-thread model pinning.
- Slash commands, Fast mode command, skill commands, drafts, message folding and Stop.
- Approvals: permission cards, allow once / always / deny, pending-approval bar, sign-ins,
  failed sends, bot-error and waiting-for-input states.
- Rewind to before a turn: restore project files and remove that turn and later messages, with
  confirmation and progress, in direct chats and group rooms.
- Group rooms: 2–50 members, group setup screen, room description, leader/rounds, @mentions,
  one-shot @mention responders, `@all`/`@everyone` merged standup, automatic handoff packets,
  room-scoped Browser/Computer access, group Files with author attribution, group routines,
  group Skills/Vault, add/remove members and delete.
- Bot-to-bot exchanges: grouped notices, waiting state, cancellation and click-through.
- Chat find, global search and command deck: bots, chats and actions (new chat/bot/group,
  Extensions, Settings, Computers, Usage, Team map, Account, approvals).
- Needs You inbox: permission cards, failed sends, sign-ins, device approvals, failed runs and
  bots waiting for an answer, sorted by time and deep-linked to the exact message.
- Attachments: photos, files, paste, camera, file chips, 100 MB chunked upload, image preview,
  bot `send_file` cards, download with SHA-256 verification, save/share, retry/cancel.
- Composer dictation through Android speech recognition (not a full voice call).
- Android share target for files/images; deep links from notifications and `codync://` links.

### Workbench

- Routines for bots and groups: list, create/edit, on/off, schedule (once, interval, cron,
  weekdays/monthly), IANA time zone, real time picker, next run, timeout, webhook URL/key,
  test run, run records and delete.
- Memory: search, add/edit/delete, pin, review, history/timeline, import/export and profile facts.
- Extensions/Marketplace: detect and install agents, Node.js setup, MCP connectors (registry,
  custom, imported config), Composio apps, skills, credentials/vault, connector sign-in and
  connection requests, per-bot toggles, show-more paging and setup terminal output/input.
- Usage: per-computer and per-provider limits, reset times and provider status.
- Computer panel: Computer, Cloud, Phones/Devices, Browser, Files and Routines; group tabs for
  Browser, Files, Skills, Vault and Routines; member picker for per-member tabs.
- Browser: state, address/navigation, live frames, tabs, start/stop, take control/release,
  expanded view and full-screen takeover; no desktop window assumptions.
- Files: browse/download shared and bot files where the host API allows; group Files.

### Shell, notifications and settings

- Floating liquid-glass nav: Bots / Needs You / Settings, hidden in chats and detail screens.
- Glass composer and sheet headers, real backdrop blur on 12+, AGSL edge refraction on 13+,
  frosted fallback on 12.
- iOS layout and motion, bundled Inter, Material Symbols Rounded, light/dark system theme.
- Settings: account, computers/devices, notifications, appearance, analytics opt-in, support,
  about and sign out. Computer access/screen settings appear only where they apply.
- Host compatibility/update-needed states, connection and offline states.
- Accessibility, large fonts, reduced motion, haptics, battery/network profiling and crash
  reporting with redaction.

### Release

- Signed sideload APK, versioning and release notes.
- No Play Store work in v1. Play internal/closed/public is v2.
- Two real phones and at least two computers in the acceptance pass.

## Android v2 — optional after v1

Only these remain. They do not block v1.

- Voice calls: Android speech/TTS, OpenAI and Gemini realtime engines, group calls, call
  settings and background audio.
- Remote screen: WebRTC view/control, touch mapping, orientation, TURN, takeover rules and
  permission/setup states.
- Home-screen Glance widgets and an ongoing work/needs-you status surface.
- Tablet/foldable adaptive layouts and split view.
- Play Store internal/closed/public tracks, data safety and store assets.
- Optional notification actions (approve/deny without opening the chat).

**Not planned:** multiple signed-in accounts or account switching.

## Fork feature audit

Audited the 45 commits since the initial import and the current desktop/host UI. These are the
features that did not exist in the iPhone app or changed after the fork. All of them are v1.

| Fork feature | v1 |
| --- | --- |
| Multiple chats per bot/group, chat panel, auto-naming, last line, per-chat context | Yes |
| New session from chat header/menu and per-chat | Yes |
| Per-thread model pinning and "all back to bot model" | Yes |
| Team map with ask arrows and group membership | Yes |
| Group rooms: 2–50, setup screen, leader/rounds, room tools, group Files, group routines, group Skills/Vault | Yes |
| One-shot @mention responders and automatic handoff packets | Yes |
| `@all` / `@everyone` merged standup | Yes |
| Rewind files + chat for direct and group turns | Yes |
| Needs You inbox | Yes |
| Command deck / global actions | Yes |
| Chat find and chat header `⋯` menu | Yes |
| Browser full view/takeover, Cloud and Phones tabs, computer panel | Yes |
| Group setup and computer cards | Yes |
| Workspace picker chosen once, scrollable swatches | Yes |
| Routine time picker and group routines | Yes |
| Plugin/skill show-more, live skills after install | Yes |
| New chat compose with recipient chips and multi-bot group setup | Yes |
| Cline real models and dynamic model/options support | Yes |
| Done/Cancel editor footers, New chat context action, no Room standup button | Yes |
| Fork release/update plumbing | Desktop only; Android gets its own signed APK flow |

Only voice, remote screen, widgets/ongoing status and tablet/foldable are v2. Multi-account is
not planned.

## Explicitly not Android

- Notch island, menu bar/tray, desktop window layout and desktop auto-updater.
- Running/installing the host, launchd/systemd/Windows service management and local loopback
  setup.
- Android-to-SSH tunneling and SSH machine configuration; the desktop remains the SSH gateway.
- OS-level screen/computer-use permission setup; Android can show status and use the computer,
  but permissions are granted on the computer.
- Multiple signed-in accounts or account switching.
- Android TV, Wear OS and Automotive.

## Delivery order and estimate

One complete release; internal milestones only. One experienced Android engineer, with Ali
testing on a physical phone:

| Milestone | Scope | Estimate |
| --- | --- | --- |
| M0 | SDK/CI, Compose shell, design tokens, fake host, glass component | 1 week |
| M1 | Models, crypto vectors, direct/relay transport, QR pairing | 2–3 weeks |
| M2 | Account, computers, roster, core chat, approvals, threads, attachments, FCM | 3–4 weeks |
| M3 | Multiple chats, groups, team map, command deck, Needs You, rewind, handoffs | 3–4 weeks |
| M4 | Editors, routines, memory, marketplace, usage, computer panel/browser/files | 4–5 weeks |
| M5 | Offline, accessibility, performance, widgets are **not** here, signed APK, device pass | 2–3 weeks |

Rough total: **15–20 weeks** for v1. Internal debug builds arrive earlier; the first public
build is the complete v1. That assumes host/cloud Android platform support and FCM are done in
parallel. It is not a two-week port.

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

## Start gate

Confirmed: one all-at-once v1, every fork feature in v1, voice/screen/widgets/tablet in v2,
no multi-account, new Firebase project, iOS only removed when Ali says.

Still needed before M0:

1. Ali says **go**.
2. Firebase project is created and the Android app's `google-services.json` is supplied
   outside git (M1).
3. Release keystore location is chosen outside the repository (M5).

Until then, no Android code.
