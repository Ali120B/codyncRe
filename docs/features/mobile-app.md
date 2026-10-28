# Mobile app (Android)

## Overview
Native Android client (Jetpack Compose) that pairs with a Codync host over the local
network or relay. Shares the same host data model as desktop; no separate backend.

## Pairing
- QR / manual code pairing via `DirectPairingClient`.
- Multi-computer list persisted in `AppPreferences`; switching computers resets synced state.

## Chat
- Live streaming entries over SSE (`DirectSseTransport`).
- Threads, reactions, files, mentions, slash commands.
- Jump-to-latest bubble (centered glass).
- Composer with attachments (up to 10 files, 100 MB each).
- IME-aware scroll: scrolls to bottom when keyboard opens if near latest.

## Bots & groups
- Roster with status, unread badges, working animation bubble.
- Create bot / group chat; group creation reuses nothing (always new group).
- Bot details, member settings, workspace picker.

## Computer panel
- Slide-in panel: files, terminals, processes, system stats.
- Host folder picker for workspaces.

## Workspaces
- Synced with host via `workspaces`/`addWorkspace`/`removeWorkspace`/`setWorkspaces` RPC.
- Phone cache in `AppPreferences.workspaces`.

## Settings
- Theme, account, analytics consent, workspaces management.
- Deep links to pairing/workbench.

## Notifications
- FCM push for new agent replies; suppressed when viewing that bot.
- Unread counters from host `bot.unread`.

## Liquid glass
- Bottom nav and composer use Kyant backdrop liquid glass.
- Menus use spring open/close animations.

## Build
- `./gradlew :app:assembleDevDebug` (dev) / `assembleProdRelease` (prod).
- Version in `app/build.gradle.kts`.
