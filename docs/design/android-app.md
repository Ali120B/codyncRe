# Android app plan

Status: planning only. No Android code exists yet. The iOS app stays in the repository until
the parity gate at the end of this document is explicitly signed off.

## Goal

Build a native Android app that covers the current iPhone app plus the fork's 2.20.0 features,
then retire `apps/ios/` when Android is accepted as complete. The Rust host remains the only
place that owns bots, chats, groups, routines, files and authorization. Android is a view over
the same HTTP/SSE and encrypted-channel contracts as the desktop and iPhone apps.

Non-goals for the first release:

- A second implementation of host logic in Kotlin.
- Android-to-SSH tunneling. The desktop remains the SSH gateway.
- Account-required use. QR pairing stays first-class and works signed out.
- A tablet-specific layout before the phone app is complete.
- Android TV, Wear OS or Automotive.

## Recommended stack

| Area | Choice | Reason |
| --- | --- | --- |
| Language | Kotlin 2.x | Native Android, coroutines and no cross-platform runtime. |
| UI | Jetpack Compose + Material 3 | Fast iteration, good accessibility and no XML screen drift. |
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

Use the official Clerk Android SDK if its Google flow and session-token access are stable at
implementation time. Otherwise use Clerk's Frontend API with Chrome Custom Tabs, matching
`apps/desktop/src/main/account.ts`; do not put Clerk secret keys in the app.

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
  feature/roster/              # computers, bots, groups
  feature/thread/              # chats, replies, composer, attachments, approvals
  feature/details/             # bot/group settings, routines, memory, marketplace
  feature/screen/              # remote screen
  feature/call/                # voice calls
  feature/widgets/             # Glance widgets and ongoing status
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

| Area | Android scope | Priority |
| --- | --- | --- |
| Welcome / sign in | Google via Clerk, skip, local-only pairing | P0 |
| QR pairing | Camera, paste link, code confirmation | P0 |
| Account computers | Discover, ask access, SAS, revoke, remove | P0 |
| Bots roster | All computers, grouping, hidden/collapsed, reorder, pin | P0 |
| Bot editor | Name, avatar, instructions, agent/model, tools, notifications | P0 |
| Groups | 2–50 members, leader, room tools, settings, delete | P0 |
| Chat | Entries, markdown, code, tool/trace, streaming, stop | P0 |
| Composer | Text, attachments, files, slash commands, drafts | P0 |
| Approvals | Permission cards, allow/deny, access requests | P0 |
| Threads | Replies, unread/read, open/close | P0 |
| Groups in chat | @mentions, merged @all standup, handoff packets | P1 |
| Rewind | Per-turn file/chat restore confirmation and progress | P1 |
| Needs-you inbox | Cross-computer approvals/questions | P1 |
| Search | Chats, messages and actions; command palette equivalent | P1 |
| Attachments | Image/file preview, upload, download, save/share | P0 |
| Routines | List, editor, schedules, webhooks, test run | P1 |
| Memory | List, edit, history, import/export | P1 |
| Marketplace | Agents, connectors, skills, Composio, credentials | P1 |
| Usage | Per-computer usage and limits | P1 |
| Notifications | FCM alerts, deep links, per-bot mute | P0 |
| Ongoing status | Working/needs-you notification, no Live Activity clone | P1 |
| Voice | Android speech + TTS; OpenAI/Gemini realtime later | P2 |
| Remote screen | WebRTC view/control, orientation, TURN | P2 |
| Widgets | Bot/usage Glance widgets | P2 |
| Settings | Computers, notifications, analytics, support, about | P0 |

“P0” means required before public beta. “P1” means required before iOS removal. “P2” may ship
after the first Android release only if the removal gate explicitly allows it.

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
   - Define Android status updates for working/needs-you/done/failed. Do not promise an Android
     Live Activity or Dynamic Island equivalent.
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

### Phase 0 — foundation (3–5 days)

- Create `apps/android/` with Gradle, Compose, Hilt, Room, DataStore and dev/prod flavors.
- App IDs: `com.pokai.Codync.android` (prod), `com.pokai.Codync.android.dev` (dev). Confirm the
  final package with Ali before Play Console work.
- Add CI: assemble debug, lint, unit tests, dependency locking.
- Define theme tokens matching the desktop/iOS palette and typography.
- Build a fake host fixture and a debug screen that shows connection state.

Exit: debug APK launches on a phone and emulator; CI is green.

### Phase 1 — transport and pairing (1–2 weeks)

- Port wire models, `Codync-Sig`, device identities and crypto.
- Implement direct SSE + HTTP and relay WebSocket transports.
- QR scan/paste/deep-link pairing; one computer in the roster.
- Store one bot mirror and stream entries.
- Run shared crypto and protocol vectors in Kotlin.

Exit: pair a real phone to a real host over LAN and over Cloudflare; receive events after
reconnect and process death.

### Phase 2 — account and core chat (1–2 weeks)

- Clerk sign-in (Google), session restoration, account switch/sign-out.
- Account computer discovery, access request/SAS, approval, revoke.
- Multi-computer roster, bot rows, pinned/reordered/hidden state.
- Chat timeline, markdown/code, composer, send/stop, approvals, threads, attachments.
- FCM registration and encrypted notifications, deep links.

Exit: daily-driver chat on Android with the phone away from the computer; no debug host.

### Phase 3 — editors, groups and fork features (2 weeks)

- Bot editor, group editor (2–50), group chat, member tools and leader.
- @mentions, @all standup, handoff packets, rewind.
- Needs-you inbox, search/command deck, drafts.
- Settings, computers, analytics and notification preferences.

Exit: feature parity with the 2.20.0 desktop batch that applies to mobile.

### Phase 4 — routines, memory, marketplace and usage (2 weeks)

- Routines list/editor, schedules, webhooks and test run.
- Memory editor/history/import/export.
- Marketplace: agents, connectors, skills, Composio and credentials.
- Usage views and computer status.
- File save/share and large attachment handling.

Exit: a user can configure and operate a computer entirely from Android.

### Phase 5 — voice, screen, widgets and polish (2–3 weeks)

- On-device speech/TTS; OpenAI/Gemini speech/realtime engines.
- Remote screen with WebRTC, touch mapping, orientation and TURN; verify H.264.
- Glance bot/usage widgets and ongoing work notification.
- Accessibility pass, large fonts, dark mode, reduced motion, haptics.
- Battery/network profiling and crash reporting.

Exit: P2 features are usable on a physical device; release candidate is stable for a week.

### Phase 6 — release and iOS retirement

- Play Console internal testing, then closed/open testing.
- Data safety, permissions declaration, privacy policy, content rating.
- Signing, AAB, versioning and release CI.
- Run the parity matrix with two real phones and at least two computers.
- Only after Ali explicitly signs off: remove iOS.

## Testing strategy

- Unit tests for models, JSON compatibility, crypto vectors, `Codync-Sig`, URL parsing, SAS,
  reducer/event ordering and Room migrations.
- `MockWebServer` tests for HTTP, SSE, WebSocket reconnect, relay close codes and mailbox.
- Fake host integration tests for pairing, account approval, send/receive, approvals and rewind.
- Compose screenshot tests for roster, thread, composer, approval and settings at phone sizes.
- Physical-device checks: camera QR, notifications, background/foreground transitions, network
  switching, Doze, file share, audio routing, rotation and remote screen.
- No Playwright/E2E desktop suites. UI acceptance is Ali on hardware, per repository rules.

## Release and iOS removal gate

Do not delete iOS merely because Android compiles. Remove it only when all of these are true:

- P0 and P1 rows in the parity matrix pass on a physical Android phone.
- P2 rows are either accepted or explicitly waived by Ali.
- The Android app can pair, sign in, recover from process death and receive push without a
  connected desktop.
- The release pipeline produces a signed AAB and the Play testing track is live.
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

## First sprint backlog

1. Confirm package IDs, minimum SDK and Play Console ownership.
2. Create the Android Gradle skeleton and CI workflow.
3. Port `CodyncKit/Models` to Kotlin with JSON round-trip fixtures.
4. Build `core/crypto` and pass all shared vectors.
5. Implement OkHttp SSE + direct RPC against the dev host.
6. Build a debug pairing screen and stream a real bot's entries.
7. Add `android` platform support to host/cloud and a dev migration.
8. Spike Clerk Android Google sign-in and FCM delivery; record the chosen SDK versions.

## Open decisions

- Final Android application ID and whether the fork uses a new brand/domain.
- Whether Clerk's Android SDK is mature enough or FAPI + Custom Tabs is the safer path.
- Whether FCM support belongs in `relay/` or a separate Worker.
- Whether voice and remote screen are P0, P1 or post-removal.
- Whether the first release targets phones only or also tablets/foldables.
- Whether the fork keeps the Codync name and icon or renames the Android app.
