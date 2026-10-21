# Android app plan

Status: scope re-audited against the whole fork; architecture locked, release split awaiting
Ali's approval. No Android code exists yet. The iOS app stays in the repository until the
parity gate at the end of this document is explicitly signed off. The v1/v1.1/v2 feature
lists live in [plan.md](../../plan.md); this document is the build plan.

## Locked decisions

| Decision | Choice |
| --- | --- |
| UI stack | Native Kotlin + Jetpack Compose |
| Minimum Android | Android 12 / API 31 |
| Glass | Real backdrop blur on 12+, AGSL edge refraction on 13+, fallback on 12 |
| Glass surfaces | Bottom nav, composer, sheet headers |
| Navigation | Floating glass capsule: Bots / Needs You / Settings |
| Nav visibility | Hidden in chats and detail screens |
| Visual direction | iOS layout and feel, desktop density where it helps |
| Roster | iOS-style grouped list with computer headings |
| Composer | Floating glass capsule with attachments and dictation |
| Theme | Follow system light/dark |
| Font | Bundled Inter |
| Icons | Material Symbols Rounded |
| Language | English only for v1 |
| Devices | Phone-first |
| Accounts | Google sign-in + local QR pairing; account optional |
| Notifications | FCM in v1 |
| Offline | Read cached chats; sending waits for a connection |
| v1 scope | Everything except voice calls, remote screen, multi-account and tablet/foldable layouts |
| V1 shape | v1.0 daily driver + v1.1 workbench, pending approval |
| Distribution | Sideloaded APK first; Play Store in v2 |
| Package | `com.pokai.Codync.android`, dev suffix `.dev` |

## Goal

Build a native Android app that covers the current iPhone app plus the fork's 3.0.0 desktop
and host features that apply to a phone, then retire `apps/ios/` when Android is accepted as
complete. The Rust host remains the only place that owns bots, chats, groups, routines, files
and authorization. Android is a view over the same HTTP/SSE and encrypted-channel contracts as
the desktop and iPhone apps.

The scope is not “port the iPhone screens”. The iPhone predates most of the fork's chat,
group, workbench and computer features. Android v1 must include the full fork delta listed in
[plan.md](../../plan.md#fork-feature-audit), or the fork's desktop users lose features on the
phone.

Not in v1 (v2 unless explicitly waived):

- Voice calls and realtime voice engines. Composer dictation is in v1.
- Remote screen viewing/control.
- Multiple signed-in accounts and account switching.
- Tablet/foldable-specific layouts.
- Play Store distribution.

Non-goals:

- A second implementation of host logic in Kotlin.
- Android-to-SSH tunneling. The desktop remains the SSH gateway.
- Account-required use. QR pairing stays first-class and works signed out.
- Android TV, Wear OS or Automotive.

## Recommended stack

| Area | Choice | Reason |
| --- | --- | --- |
| Language | Kotlin 2.x | Native Android, coroutines and no cross-platform runtime. |
| UI | Jetpack Compose + Material 3 base | Fast iteration, good accessibility and no XML screen drift. |
| Minimum SDK | Android 12 / API 31 | Real backdrop blur everywhere; AGSL refraction on 13+. |
| Glass | Haze/custom backdrop blur + AGSL shader | Real blurred background and edge lensing, not a fake translucent fill. |
| Font | Bundled Inter | Closest consistent match to SF Pro/desktop. |
| Icons | Material Symbols Rounded | Closest native match to SF Symbols with a complete set. |
| Navigation | Navigation Compose with typed routes | Deep links, back stack and tab state. |
| State | `StateFlow` + immutable UI state | Mirrors the host event model and survives config changes. |
| HTTP/SSE/WebSocket | OkHttp | Mature SSE streaming, WebSocket and connection control. |
| JSON | kotlinx.serialization | Explicit wire models and forward-compatible decoding. |
| Local storage | Room + DataStore | Cached mirrors, drafts and device-local preferences. |
| DI | Hilt | Standard Android wiring without a service locator. |
| Images | Coil | Avatars, attachments and remote images. |
| QR | CameraX + ML Kit barcode scanning | QR pairing and pasted `codync://pair` links. |
| Crypto | Bouncy Castle + Android Keystore | Ed25519, X25519, HKDF and ChaCha20-Poly1305; Keystore wraps local key seeds. |
| WebRTC | A maintained Android WebRTC build | Remote screen; verify H.264 and data-channel compatibility before committing. |
| Push | Firebase Cloud Messaging | Android delivery; the existing relay must learn FCM. |
| Build | Gradle version catalog + AGP | Reproducible dev/prod flavors. |

Use the official Clerk Android SDK (`com.clerk:clerk-android-api` /
`clerk-android-ui`). Clerk's Android quickstart confirms it supports hosted and native
authentication. Pin a tested SDK version in the Gradle catalog. Do not put Clerk secret keys in
the app.

## Repository layout

```text
apps/android/
  settings.gradle.kts
  build.gradle.kts
  gradle/libs.versions.toml
  app/                         # wiring, deep links, application lifecycle
  core/model/                  # wire models ported from CodyncKit
  core/crypto/                 # Codync-Sig, channel handshake, push sealing
  core/transport/              # direct SSE, encrypted WebSocket, relay client
  core/data/                   # AccountStore, BotStore, Room cache, preferences
  core/design/                 # theme, controls, avatars, markdown
  feature/onboarding/          # welcome, sign-in, QR pairing
  feature/roster/              # computers, bots, groups, team map
  feature/chats/               # chat list, chats panel, new chat/session
  feature/thread/              # timeline, replies, composer, attachments, approvals, rewind
  feature/groups/              # room setup, members, room tools, group Files
  feature/inbox/               # needs-you inbox and command deck
  feature/details/             # bot/group settings, routines, memory, marketplace
  feature/computer/            # computer panel, browser, files, cloud, devices
  feature/settings/            # account, notifications, appearance, about
  feature/screen/              # v2 remote screen
  feature/call/                # v2 voice calls
  feature/widgets/             # v1.1 Glance widgets and ongoing status
```

Keep wire models in `core/model` as Kotlin `@Serializable` classes with the same JSON names as
`apps/ios/Kit/Sources/CodyncKit/Models/`. Never add a field to one client without adding it to
the host and the other clients.

## Runtime architecture

```text
Compose screens
      |
      v
AccountStore (account context, computers, selection)
      |
      +--> BotStore per computer (roster, entries, events, connection)
                 |
                 +--> HostTransport
                        +--> Direct SSE + HTTP (LAN/Tailscale/loopback)
                        +--> Encrypted WebSocket (direct or Cloudflare relay)
      |
      v
Room cache + DataStore preferences
```

- `AccountSession` owns Clerk sign-in and session JWTs. One Clerk instance per environment.
- `AccountStore` owns one account context, partitions computers and keys by Clerk user ID, and
  retires late responses when the account changes.
- `BotStore` mirrors one computer. It starts from `rev`, follows events, sends optimistic user
  entries, and reconciles with host events. Group turns still run on member bots.
- `HostTransport` is an interface. Direct and relay transports share the same inner RPC and
  event payloads; only the outer envelope differs.
- No screen calls the host directly. Store methods map to host RPCs and update flows.

## Security and identities

1. Device key: one Ed25519 key per app install × account context. Store the seed wrapped by a
   non-exportable Android Keystore AES-GCM key. Never store a raw seed in Room or DataStore.
2. Push key: one X25519 key per install × account context, wrapped the same way.
3. Pairing: scan `codync://pair?v=3...`, verify `computerId` against the host signing key, then
   send the one-time code and `platform:"android"` inside the encrypted channel.
4. Account access: register the device, request access, run the commit-then-reveal SAS, show the
   six-digit code, and pin the host key after approval. Cloud metadata never grants access.
5. Channel: signed ephemeral X25519 ECDH, HKDF-SHA256, ChaCha20-Poly1305. Reconnect on `4011`.
6. Signed HTTP: `Codync-Sig` exactly as defined in `docs/reference/remote-relay.md`, including
   lowercase authority, exact request target and body hash.
7. Logout/removal: destroy the context's device key, push key, cache, drafts and widget data.
8. Never log keys, pairing codes, session tokens, push tickets or decrypted notification text.

Crypto acceptance is not “it compiles.” The Kotlin implementation must reproduce every shared
vector in `docs/reference/fixtures/remote-relay-vectors.json`, including request signatures,
channel handshake keys, mailbox sealing and push sealing.

## Transport plan

### Direct

- Parse the QR `urls` candidates and race them with a 1.5-second budget, then prefer the relay.
- SSE endpoint for host events; HTTP RPC for commands; attach the loopback bearer token only on
  direct local/SSH-style connections where the host expects it.
- Reconnect with bounded backoff and resume from the last host revision.
- Respect network changes: Wi-Fi to cellular, process death, Doze and background limits.

### Relay

- `POST /v1/relay` WebSocket upgrade with a `Codync-Sig`.
- Implement ACL rejection, mailbox delivery, pairing mode and close codes exactly as the iOS
  `ChannelTransport` does.
- Keep `link` multiplexing and `data`/`close` ordering.
- Foreground work uses the channel; background work uses FCM where possible.

### Pairing and account flows

- QR scan, paste link, and deep link.
- Account discovery of computers, access request polling, SAS approval, revocation and removal.
- “Skip” onboarding and later pairing from Settings.
- Device name defaults to the phone's model; platform is always `android`.

## Feature parity matrix

Priorities are the delivery milestones in [plan.md](../../plan.md): **v1.0** is the daily
driver, **v1.1** completes v1, **v2** is after v1 acceptance. A v1.1 row is still required
before iOS removal.

| Area | Android scope | Priority |
| --- | --- | --- |
| Welcome / sign in | Google via Clerk, skip, local-only pairing, account restoration | v1.0 |
| QR pairing | Camera, paste link, deep link, code confirmation | v1.0 |
| Account computers | Discover, ask access, SAS, approve, revoke, remove, claim | v1.0 |
| Transports | Direct SSE/HTTP + relay WebSocket, resume by `rev`, network/process recovery | v1.0 |
| Offline | Cached chat reading, drafts, send-waits-for-link, honest connection state | v1.0 |
| Push | FCM registration, encrypted alerts, deep links, per-bot mute, foreground handling | v1.0 |
| Roster | All computers grouped, connection summary, filters, collapse, pin/reorder/hide, search | v1.0 |
| Bot editor | Name, description, character/avatar, instructions, workspace, approval mode, tools, notifications | v1.0 |
| Agents/models | ACP registry discovery, install state, model picker, model options, Fast mode, Cline, custom IDs | v1.0 |
| Workspace picker | Browse the selected computer, choose once, remember per bot and group | v1.0 |
| Multiple chats | Main + named chats per bot/group, own context, last line, delete, chats panel | v1.0 |
| New chat / session | Recipient chips, multi-bot group setup, new session, per-chat new session | v1.0 |
| Chat timeline | Streaming, final replies, markdown, code, trace/full conversation, copy, reactions, paging, jump-to-latest | v1.0 |
| Composer | Text, files/photos/camera/paste, chips, slash commands, drafts, stop, dictation | v1.0 |
| Approvals | Permission cards, allow once/always/deny, pending bar, sign-ins, failed sends, bot errors | v1.0 |
| Threads | Replies, unread/read, root summaries, thread composer, per-thread model pinning | v1.0 |
| Groups | 2–50 members, room setup, leader/rounds, member tools, group settings, delete | v1.0 |
| Group room features | @mentions, one-shot responders, `@all` standup, handoffs, group Files, group routines, group Skills/Vault, room-scoped Browser/Computer | v1.0 |
| Bot collaboration | Team MCP notices, grouped exchanges, pending requests, stop/cancel | v1.0 |
| Rewind | Per-turn file + chat restore, confirmation/progress, direct and group | v1.0 |
| Needs You | Permission cards, failed sends, sign-ins, device approvals, failed/waiting bots | v1.0 |
| Command deck / search | Bots, chats, messages, app actions, approval review, exact-message deep links | v1.0 |
| Team map | Bots and groups as a ring, ask arrows, group membership, click-through | v1.0 |
| Attachments | Upload/download, 100 MB chunks, image preview, bot `send_file`, save/share, retry/cancel | v1.0 |
| Settings | Account, computers/devices, notifications, appearance, analytics, support, about, sign out | v1.0 |
| Routines | Bot and group routines, schedules, time zone/time picker, webhook, test run, records, delete | v1.1 |
| Memory | Search, CRUD, pin, review, history/timeline, import/export, profile facts | v1.1 |
| Marketplace | Agents/Node setup, MCP registry/custom/import, Composio, skills, credentials/vault, sign-in, per-bot toggles, setup terminal | v1.1 |
| Usage | Per-computer/provider limits, reset times, provider status | v1.1 |
| Computer panel | Computer, Cloud, Phones, Browser, Files, Routines; group tabs and member picker | v1.1 |
| Browser | State, address, frames, tabs, start/stop, take control/release, expanded view | v1.1 |
| Files | Browse/download allowed host files, group Files, share/save | v1.1 |
| Accessibility/polish | Large fonts, reduced motion, haptics, contrast, battery/network profiling, crash reports | v1.1 |
| Voice calls | Android speech/TTS, OpenAI/Gemini realtime, group calls, settings, background audio | v2 |
| Remote screen | WebRTC view/control, touch mapping, orientation, TURN, permissions/takeover | v2 |
| Widgets/status | Glance bot/usage widgets and ongoing work/needs-you surface | v1.1 |
| Account switching | Multiple signed-in accounts, isolated caches/keys, fast switch | v2 |
| Tablet/foldable | Adaptive list/detail and split layouts | v2 |
| Distribution | Signed sideload APK; Play internal/closed/public in v2 | v1.0 / v2 |

## Host, cloud and relay changes

These are shared changes and should land before or alongside the Android client:

1. **Android platform**
   - Accept `platform:"android"` in host pairing and access requests.
   - Add `android` to the cloud `devices.platform` and `access_requests.platform` checks with a
     new migration. Rebuild tables; do not edit old migrations.
   - Desktop approval UI and device lists must display Android correctly.
2. **Push**
   - Extend `relay/` to accept FCM registration tokens alongside APNs tickets, or add a second
     Worker with the same ticket contract. Prefer one Worker with provider dispatch.
   - Add `FCM_SERVICE_ACCOUNT` (or equivalent) as a Worker secret; never commit
     `google-services.json` or a service-account JSON.
   - Keep encrypted title/body; Android decrypts locally and posts a notification.
   - Define Android status updates for working/needs-you/done/failed. Ongoing status is a
     v1.1 surface; it supplements normal FCM alerts rather than replacing them.
   - Register FCM tokens per install/account context, retire them on logout/removal, and keep
     APNs tickets working.
3. **Deep links**
   - `codync://bot/<computerId>/<botId>` and existing notification links must resolve inside
     Android, including from a cold start.
4. **Compatibility**
   - Add Android to `minApp` / `minHost` compatibility tests and release notes.
5. **Remote screen**
   - Confirm the Android WebRTC client negotiates H.264 and the host's existing `screen*` RPCs.
   - No new host protocol if the existing SDP/ICE flow is reused.
6. **Voice**
   - Reuse host `voice*` RPCs. Android audio capture and playback stay on-device; no audio through
     the host.
7. **Cloud setup**
   - The Android app uses the same Codync cloud URL and Clerk instance as desktop. Do not fork the
     account model per platform.

## Delivery phases

These are the milestones in [plan.md](../../plan.md#delivery-order-and-estimate). They are
ordered by risk, not by screen count: transport and event ordering first, then the fork's
chat/work layer, then the configuration workbench.

### M0 — foundation (1 week)

- Create `apps/android/` with Gradle, Compose, Hilt, Room, DataStore and dev/prod flavors.
- App IDs: `com.pokai.Codync.android` (prod), `com.pokai.Codync.android.dev` (dev).
- Add CI: assemble debug, lint, unit tests, dependency locking.
- Define theme tokens matching the desktop/iOS palette and typography.
- Build the liquid-glass component and fallback once, with a debug surface to tune blur,
  tint, refraction and performance.
- Build a fake host fixture and a debug screen that shows connection state.

Exit: debug APK launches on a phone and emulator; glass holds 60 fps on a mid-range phone;
CI is green.

### M1 — transport and pairing (2–3 weeks)

- Port wire models, `Codync-Sig`, device identities, push keys and crypto.
- Implement direct SSE + HTTP and relay WebSocket transports.
- QR scan/paste/deep-link pairing; one computer in the roster.
- Store one bot mirror, its chats and stream entries.
- Run shared crypto and protocol vectors in Kotlin.

Exit: pair a real phone to a real host over LAN and Cloudflare; receive events after reconnect
and process death; shared vectors pass.

### M2 — account, roster and core chat (3–4 weeks)

- Clerk sign-in (Google), session restoration, sign-out; single active account in v1.
- Account computer discovery, access request/SAS, approval, revoke, remove.
- Multi-computer roster, bot rows, connection states, pinned/reordered/hidden rows, filters.
- Multiple chats per bot/group, chat titles, last line, chats panel, new chat/new session.
- Chat timeline, markdown/code, trace/full conversation, streaming, stop, reactions, read state,
  history paging, find, jump-to-latest.
- Composer, drafts, slash commands, attachments and dictation.
- Approvals, failed sends, sign-ins, bot errors.
- Threads, per-thread model pinning and unread state.
- FCM registration, encrypted notifications and deep links.
- Settings and account/computer management.

Exit: daily-driver chat with the phone away from the computer, including attachments, threads
and push; no debug host.

### M3 — fork work layer (3–4 weeks)

- Group rooms: creation/setup, 2–50 members, room-scoped tools, group Files, group routines,
  group Skills/Vault, @mentions, one-shot responders, `@all` standup and handoffs.
- Bot-collaboration notices, waiting/cancel states.
- Rewind with checkpoint progress and confirmation.
- Needs You inbox and exact-message navigation.
- Command deck/global search and all app actions.
- Team map with ask edges and group membership.
- Bot/group editors, model options/Fast mode, workspace picker, templates, delete.
- UI shell: glass nav, hidden-in-chat behavior, settings, connection states.

Exit: v1.0 usable as the daily driver; every fork chat feature in the audit table works.

### M4 — workbench (4–5 weeks)

- Routines list/editor, schedules, time zone/time picker, webhooks, test run and records.
- Memory search/CRUD/pin/review/history/import/export.
- Marketplace: agents/Node, MCP connectors (registry/custom/import), Composio, skills,
  credentials/vault, connector sign-in, connection requests, per-bot toggles, setup terminal.
- Usage and provider limits.
- Computer panel: Computer, Cloud, Phones, Browser, Files, Routines; group tabs/member picker.
- Browser frames/takeover/expanded view and file download/share.
- Glance bot/usage widgets and the working/needs-you ongoing notification.

Exit: a user can configure and operate a computer entirely from Android; v1.1 is complete.

### M5 — hardening and v1 release (2–3 weeks)

- Offline/process-death/Doze/network-switch testing.
- Accessibility, large fonts, reduced motion, contrast, haptics.
- Battery/network profiling, crash reporting and redaction checks.
- Signed release APK, versioning, release notes and install/upgrade path.
- Run the parity matrix with two phones and at least two computers.
- Ali's hardware acceptance.

Exit: signed v1 APK installed and accepted; iOS still in the repository.

### v2 — voice, screen, accounts and store

- Voice calls and realtime engines; remote screen/WebRTC/TURN.
- Multi-account switching; tablet/foldable layouts.
- Play internal/closed/public, data safety, store assets.
- iOS removal only after the gate below, not at v1 by default.

## Testing strategy

- Unit tests for models, JSON compatibility, crypto vectors, `Codync-Sig`, URL parsing, SAS,
  reducer/event ordering and Room migrations.
- `MockWebServer` tests for HTTP, SSE, WebSocket reconnect, relay close codes and mailbox.
- Fake host integration tests for pairing, account approval, send/receive, approvals and rewind.
- Compose screenshot tests for roster, chats, group room, team map, needs-you, approval,
  composer, routines, marketplace and settings at phone sizes.
- Glass checks on at least one Android 12 device and one Android 13+ device: blur, edge
  refraction, dark/light tint, keyboard overlap and frame time while scrolling/streaming.
- Physical-device checks: camera QR, notifications, background/foreground transitions, network
  switching, Doze, file share, process death, downloads and dictation.
- v2 adds audio routing/background voice and remote-screen rotation/input/TURN checks.
- No Playwright/E2E desktop suites. UI acceptance is Ali on hardware, per repository rules.

## Release and iOS removal gate

Do not delete iOS merely because Android compiles, and do not treat v1 as “100%” if v2
features still matter to Ali. Remove iOS only when all of these are true:

- Every v1.0 and v1.1 row in the parity matrix passes on a physical Android phone.
- v2 rows (voice, remote screen, account switching, tablet/foldable) either pass or are
  explicitly waived in writing by Ali.
- The Android app can pair, sign in, recover from process death and receive push without a
  connected desktop.
- A signed release APK installs over the previous build; if Play is used, its testing track is
  live with the same build.
- The host and cloud accept Android devices in production, not only dev.
- Desktop approval and device lists show Android correctly.
- README, AGENTS, docs and release notes name Android as the phone client.
- Ali says Android is 100% code.

When that happens, remove in this order:

1. Stop iOS release workflows (`ios-submit`, Kit tests, App Store checks) and the pbxproj
   auto-tag trigger.
2. Remove `apps/ios/` and iOS/Widgets/NotificationService targets from `apps/project.yml`;
   preserve the macOS `Screen` target and its signing.
3. Regenerate `apps/Codync.xcodeproj` only if the macOS screen target still needs it.
4. Remove iPhone references from the cloud/relay docs, README and AGENTS; keep historical
   release notes.
5. Move version/tag ownership to Android plus `host/Cargo.toml` and
   `apps/desktop/package.json`.
6. Keep the old iOS releases downloadable; do not rewrite tags or delete history.

## First sprint backlog (after approval)

1. Create the Android Gradle skeleton, CI workflow and glass prototype.
2. Port `CodyncKit/Models` to Kotlin with JSON round-trip fixtures.
3. Build `core/crypto` and pass all shared vectors.
4. Implement OkHttp SSE + direct RPC against the dev host.
5. Build a debug pairing screen and stream a real bot's chats and entries.
6. Add `android` platform support to host/cloud and a dev D1 migration.
7. Spike Clerk Android Google sign-in, FCM delivery and the official SDK versions.
8. Add the fork-only host methods to the Android wire inventory: chats, rewind, standup,
   handoffs, team map data, computer panel/browser, group room tabs.

## Open decisions

- Approve the v1.0/v1.1 staging or require one all-at-once v1.
- Confirm that widgets/ongoing status stay in v1.1 and multi-account/tablet/foldable are v2.
- Confirm that team map, computer panel, browser and files are v1.1.
- Confirm whether iOS removal waits for v2 voice/screen or can follow v1 with a written waiver.
- Firebase project ownership for FCM.
- Release keystore location outside the repository.
- Whether Clerk's Android SDK is mature enough or FAPI + Custom Tabs is the safer path; decide
  during the M0 spike.
