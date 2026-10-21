# Plan

## Now: Android app

Android is the next product, not an iOS port. It has to cover the iPhone app **and** the
desktop/host features added since the fork, except voice calls and remote screen in v1.
No `apps/android/` code starts until Ali approves the scope, split and removal gate below.

Detailed architecture, security, transport and test plan:
[docs/design/android-app.md](docs/design/android-app.md).

Version **3.0.0** is pushed on `main` and tagged. It marks the start of the Android line; the
host and desktop must stay on the same version.

## Android v1 — every phone feature except voice calls and remote screen

V1 is the complete phone app for daily work, not a cut-down chat client. It is single-account
and phone-first; multi-account switching and tablet/foldable layouts are v2. V1 is delivered
in two installable milestones so real use can start early; both are v1 and both must pass
before iOS can be removed.

### v1.0 — daily driver

**Connect**

- Optional Google sign-in through Clerk; local QR pairing works signed out.
- Multiple computers per account, access requests, six-digit SAS approval, revoke/remove
  device, computer list and cloud link status.
- Direct LAN/Tailscale and encrypted Cloudflare relay transports; reconnect from the last
  host revision after network changes and process death.
- Offline reading from the cache; sends wait for a connection instead of pretending to queue.
- FCM registration, encrypted notifications, deep links, per-bot notification preferences.

**Roster and bots**

- All computers grouped iOS-style, connection summary, show-only filters, collapsed sections,
  pin/hide/reorder, search.
- Bot rows with character avatars, status/activity, last message, unread and working state.
- Bot editor: name, description, character/avatar, instructions, agent, model, model options,
  Fast mode, workspace, approval mode, tools, skills, connectors, browser/computer access,
  notifications, memory.
- New bot from template, edit, delete, new session, new chat, reveal folder.
- Dynamic agent/model discovery (ACP registry, Cline, custom IDs); no hardcoded provider list.
- Workspace picker against the computer's filesystem, chosen once and remembered.
- Team map: bots and groups as a ring with who-asked-whom edges, group membership and
  click-through to the chat.

**Chats, threads and groups**

- Main chat plus multiple chats per bot/group, each with its own context, title, last line and
  delete action; New chat; New session (fresh agent context, same transcript); roster follows
  the open chat.
- Streaming timeline, final replies, markdown, code blocks, tool/trace view and full
  conversation, copy, reactions, read/unread, history paging, jump-to-latest.
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

**Shell and settings**

- Floating liquid-glass nav: Bots / Needs You / Settings, hidden in chats and detail screens.
- Glass composer and sheet headers, real backdrop blur on 12+, AGSL edge refraction on 13+,
  frosted fallback on 12.
- iOS layout and motion, bundled Inter, Material Symbols Rounded, light/dark system theme.
- Settings: account, computers/devices, notifications, appearance, analytics opt-in, support,
  about, sign out. Computer access/screen settings appear only where they apply.
- Host compatibility/update-needed states, connection and offline states.

### v1.1 — the rest of the workbench

- Routines for bots and groups: list, create/edit, on/off, schedule (once, interval, cron,
  weekdays/monthly), IANA time zone, real time picker, next run, timeout, webhook URL/key,
  test run, run records and delete.
- Memory: search, add/edit/delete, pin, review, history/timeline, import/export, profile facts.
- Extensions/Marketplace: detect and install agents, Node.js setup, MCP connectors (registry,
  custom, imported config), Composio apps, skills, credentials/vault, connector sign-in and
  connection requests, per-bot toggles, show-more paging, setup terminal output/input.
- Usage: per-computer and per-provider limits, reset times, provider status.
- Computer panel: Computer, Cloud, Phones/Devices, Browser, Files, Routines; group tabs for
  Browser, Files, Skills, Vault and Routines; member picker for per-member tabs.
- Browser: state, address/navigation, live frames, tabs, start/stop, take control/release,
  expanded view and full-screen takeover; no desktop window assumptions.
- Files: browse/download shared and bot files where the host API allows; group Files.
- Settings/device management: pending access requests, revoke devices, remove computer,
  analytics, support, about, host update state.
- Home-screen widgets: bots and usage, plus a working/needs-you ongoing notification (Android's
  practical equivalent of the iPhone Live Activity; not a Dynamic Island clone).
- Accessibility, large fonts, reduced motion, haptics, battery/network profiling, crash reporting.
- Signed sideload APK, versioning and release notes. Play Store work is deferred.

## Android v2 — after v1 is accepted

- Voice calls: Android speech/TTS, OpenAI and Gemini realtime engines, group calls, call
  settings and background audio.
- Remote screen: WebRTC view/control, touch mapping, orientation, TURN, takeover rules and
  permission/setup states.
- Multiple signed-in accounts and fast account switching.
- Tablet/foldable adaptive layouts and split view.
- Play Store internal/closed/public tracks, data safety and store assets.
- Optional notification actions (approve/deny without opening the chat).

## Fork feature audit

Audited the 45 commits since the initial import and the current desktop/host UI. These are the
features that did not exist in the iPhone app or changed after the fork; none may be dropped
from Android v1 unless explicitly waived.

| Fork feature | Android v1 |
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

Still v2, not v1: voice calls, remote screen, multi-account switching, tablet/foldable layouts
and Play Store distribution. Home-screen widgets/ongoing status are v1.1 because they are not
voice or remote screen.

## Explicitly not Android

- Notch island, menu bar/tray, desktop window layout and desktop auto-updater.
- Running/installing the host, launchd/systemd/Windows service management and local loopback
  setup.
- Android-to-SSH tunneling and SSH machine configuration; the desktop remains the SSH gateway.
- OS-level screen/computer-use permission setup; Android can show status and use the computer,
  but permissions are granted on the computer.
- Android TV, Wear OS and Automotive.

## Delivery order and estimate

One experienced Android engineer, with Ali testing on a physical phone:

| Milestone | Scope | Estimate |
| --- | --- | --- |
| M0 | SDK/CI, Compose shell, design tokens, fake host, glass component | 1 week |
| M1 | Models, crypto vectors, direct/relay transport, QR pairing | 2–3 weeks |
| M2 | Account, computers, roster, core chat, approvals, threads, attachments, FCM | 3–4 weeks |
| M3 | Multiple chats, groups, team map, command deck, Needs You, rewind, handoffs | 3–4 weeks |
| M4 | Editors, routines, memory, marketplace, usage, computer panel/browser/files | 4–5 weeks |
| M5 | Offline, accessibility, performance, signed APK, device pass | 2–3 weeks |

Rough total: **15–20 weeks** for complete v1; first daily-driver APK around **9–12 weeks**.
That assumes host/cloud Android platform support and FCM are done in parallel. It is not a
two-week port.

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

Before M0, Ali must confirm:

1. The staged v1.0/v1.1 split, or one all-at-once v1.
2. Whether widgets/ongoing status stay in v1.1 (recommended) or move to v2, and whether
   multi-account switching is v2.
3. Whether team map, computer panel/browser and files are v1.1 (recommended, since they are
   fork features).
4. Whether iOS removal waits for v2 voice/screen or can happen after v1 with a written waiver.
5. The Firebase project owner for FCM.
6. Where the release keystore lives (outside the repository).

Until then, no Android code.
