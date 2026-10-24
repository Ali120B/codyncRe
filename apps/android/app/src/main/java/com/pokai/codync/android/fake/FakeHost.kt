package com.pokai.codync.android.fake

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class FakeTransport(val label: String) {
    Direct("Direct · LAN"),
    Relay("Cloudflare relay"),
    Offline("Offline"),
}

data class FakeComputer(
    val id: String,
    val name: String,
    val platform: String,
    val transport: FakeTransport,
    val version: String,
    val lastSeen: Long,
)

data class FakeBot(
    val id: String,
    val computerId: String,
    val name: String,
    val character: String,
    val color: String,
    val shape: String,
    val backend: String,
    val description: String,
    val status: String,
    val activity: String,
    val lastLine: String,
    val lastAt: Long,
    val unread: Int,
    val pinned: Boolean = false,
    val kind: String = "agent",
    val members: List<String> = emptyList(),
    val hidden: Boolean = false,
    val workingChat: String? = null,
)

data class FakeChat(
    val id: String,
    val botId: String,
    val title: String,
    val lastLine: String,
    val updatedAt: Long,
    val unread: Int = 0,
)

data class FakeMessage(
    val id: String,
    val chatId: String,
    val authorId: String,
    val kind: String,
    val text: String,
    val createdAt: Long,
    val detail: String? = null,
)

data class FakeNeedsItem(
    val id: String,
    val botId: String,
    val computerId: String,
    val chatId: String,
    val messageId: String?,
    val kind: String,
    val title: String,
    val detail: String,
    val createdAt: Long,
)

data class FakeRoutine(
    val id: String,
    val botId: String,
    val title: String,
    val schedule: String,
    val timezone: String = "Asia/Dubai",
    val webhook: String? = null,
    val prompt: String = "",
    val enabled: Boolean,
    val lastRunAt: Long?,
    val runs: List<FakeRoutineRun> = emptyList(),
)

data class FakeRoutineRun(
    val id: String,
    val at: Long,
    val status: String, // ok | failed | running
    val detail: String,
)

data class FakeMemory(
    val id: String,
    val botId: String,
    val text: String,
    val pinned: Boolean,
    val updatedAt: Long,
    val source: String = "manual",
)

data class FakeMarketItem(
    val id: String,
    val kind: String, // agent | mcp | skill | credential | composio
    val name: String,
    val detail: String,
    val installed: Boolean,
    val needsSignIn: Boolean = false,
    val setupLog: String = "",
    val enabledBots: Set<String> = emptySet(),
)

data class FakeAskEdge(
    val fromBotId: String,
    val toBotId: String,
    val label: String,
)

data class FakeFile(
    val id: String,
    val name: String,
    val botId: String?,
    val sizeLabel: String,
    val updatedAt: Long,
)

data class FakeBrowserFrame(
    val id: String,
    val title: String,
    val url: String,
    val computerId: String,
)

data class FakeUsageProvider(
    val id: String,
    val name: String,
    val used: String,
    val limit: String,
    val pct: Float,
)

data class FakeConnectionRequest(
    val id: String,
    val name: String,
    val kind: String,
    val createdAt: Long,
)

/**
 * M0 fixture. It exercises the shell and visual language before the real host transport lands.
 * Nothing here pretends to be a network call: the debug screen says "Fake host" everywhere.
 */
@Stable
class FakeHostState {
    private val now = System.currentTimeMillis()

    val computers = mutableStateListOf(
        FakeComputer("cmp_macbook", "Ali's MacBook Pro", "macOS", FakeTransport.Direct, "3.0.0", now),
        FakeComputer("cmp_studio", "Studio", "Linux", FakeTransport.Relay, "3.0.0", now - 42_000),
        FakeComputer("cmp_mini", "Office Mac mini", "macOS", FakeTransport.Offline, "2.9.4", now - 86_400_000),
    )

    val bots = mutableStateListOf(
        FakeBot(
            id = "bot_atlas",
            computerId = "cmp_macbook",
            name = "Atlas",
            character = "🦉",
            color = "violet",
            shape = "blob",
            backend = "Claude Code",
            description = "Ships the Android client",
            status = "working",
            activity = "Porting wire models",
            lastLine = "M0 is up: the shell and glass are running.",
            lastAt = now - 2 * 60_000,
            unread = 2,
            pinned = true,
            workingChat = "chat_atlas_main",
        ),
        FakeBot(
            id = "bot_scout",
            computerId = "cmp_macbook",
            name = "Scout",
            character = "🦊",
            color = "orange",
            shape = "pebble",
            backend = "Codex",
            description = "Keeps the release honest",
            status = "idle",
            activity = "",
            lastLine = "Signed APK first; Play Store is v2.",
            lastAt = now - 35 * 60_000,
            unread = 0,
            pinned = true,
        ),
        FakeBot(
            id = "bot_nova",
            computerId = "cmp_studio",
            name = "Nova",
            character = "🐙",
            color = "cyan",
            shape = "squircle",
            backend = "OpenCode",
            description = "Cloud, relay and migrations",
            status = "needsInput",
            activity = "Waiting for your answer",
            lastLine = "Should I delete the old migration?",
            lastAt = now - 8 * 60_000,
            unread = 1,
        ),
        FakeBot(
            id = "bot_echo",
            computerId = "cmp_studio",
            name = "Echo",
            character = "🐼",
            color = "green",
            shape = "hex",
            backend = "Gemini CLI",
            description = "Tests on real hardware",
            status = "idle",
            activity = "",
            lastLine = "Green on API 31 and API 36.",
            lastAt = now - 3 * 60 * 60_000,
            unread = 0,
        ),
        FakeBot(
            id = "bot_room",
            computerId = "cmp_macbook",
            name = "Launch Room",
            character = "🚀",
            color = "magenta",
            shape = "cloud",
            backend = "Group",
            description = "Atlas, Scout, Nova and you",
            status = "idle",
            activity = "",
            lastLine = "Atlas: The signed APK is attached.",
            lastAt = now - 60 * 60_000,
            unread = 3,
            kind = "group",
            members = listOf("bot_atlas", "bot_scout", "bot_nova"),
        ),
        FakeBot(
            id = "bot_pixel",
            computerId = "cmp_mini",
            name = "Pixel",
            character = "🫧",
            color = "blue",
            shape = "teardrop",
            backend = "Claude Code",
            description = "Watches the night build",
            status = "offline",
            activity = "",
            lastLine = "Nightly build passed.",
            lastAt = now - 2 * 86_400_000,
            unread = 0,
            hidden = true,
        ),
    )

    val chats = mutableStateListOf(
        FakeChat("chat_atlas_main", "bot_atlas", "Main", "M0 is up: the shell and glass are running.", now - 2 * 60_000, 2),
        FakeChat("chat_atlas_shell", "bot_atlas", "Android shell", "I'll keep the nav hidden in chats.", now - 50 * 60_000),
        FakeChat("chat_scout_main", "bot_scout", "Main", "Signed APK first; Play Store is v2.", now - 35 * 60_000),
        FakeChat("chat_nova_main", "bot_nova", "Main", "Should I delete the old migration?", now - 8 * 60_000, 1),
        FakeChat("chat_echo_main", "bot_echo", "Main", "Green on API 31 and API 36.", now - 3 * 60 * 60_000),
        FakeChat("chat_room_main", "bot_room", "Main", "Atlas: The signed APK is attached.", now - 60 * 60_000, 3),
        FakeChat("chat_pixel_main", "bot_pixel", "Main", "Nightly build passed.", now - 2 * 86_400_000),
    )

    val messages = mutableStateListOf(
        FakeMessage("m_a1", "chat_atlas_main", "user", "user", "Good morning. Where are we on the Android shell?", now - 22 * 60_000),
        FakeMessage(
            "m_a2",
            "chat_atlas_main",
            "bot_atlas",
            "agent",
            "M0 is up: Compose shell, the iOS palette, Inter, and real Haze glass. The debug APK builds.",
            now - 20 * 60_000,
        ),
        FakeMessage(
            "m_a3",
            "chat_atlas_main",
            "bot_atlas",
            "tool",
            "Ran ./gradlew :app:assembleDevDebug",
            now - 19 * 60_000,
            detail = "BUILD SUCCESSFUL in 38s\n42 actionable tasks: 12 executed, 30 up-to-date",
        ),
        FakeMessage(
            "m_a4",
            "chat_atlas_main",
            "bot_atlas",
            "agent",
            "I'm porting the wire models next, then the direct channel and QR pairing.",
            now - 18 * 60_000,
        ),
        FakeMessage(
            "m_a5",
            "chat_atlas_main",
            "bot_atlas",
            "approval",
            "Run `cargo test` in host/",
            now - 12 * 60_000,
            detail = "Permission requested · shell",
        ),
        FakeMessage("m_a6", "chat_atlas_main", "user", "user", "Use the dev host, not production.", now - 5 * 60_000),
        FakeMessage(
            "m_a7",
            "chat_atlas_main",
            "bot_atlas",
            "agent",
            "Understood — codync-cloud-dev and the 19223 host only.",
            now - 2 * 60_000,
        ),
        FakeMessage("m_a8", "chat_atlas_shell", "user", "user", "Keep the bottom nav hidden in chats and detail screens.", now - 55 * 60_000),
        FakeMessage("m_a8b", "chat_atlas_shell", "bot_atlas", "agent", "Done. It only floats over the three tabs.", now - 50 * 60_000),
        FakeMessage("m_s1", "chat_scout_main", "user", "user", "Draft release notes for 3.0.0.", now - 40 * 60_000),
        FakeMessage(
            "m_s2",
            "chat_scout_main",
            "bot_scout",
            "agent",
            "Done. Signed sideload APK first, no Play Store, and iOS stays until Ali says otherwise.",
            now - 35 * 60_000,
        ),
        FakeMessage("m_n1", "chat_nova_main", "user", "user", "Can I drop migration 0002?", now - 10 * 60_000),
        FakeMessage(
            "m_n2",
            "chat_nova_main",
            "bot_nova",
            "agent",
            "Should I delete the old migration? A new 0004 that adds android is safer.",
            now - 8 * 60_000,
        ),
        FakeMessage("m_e1", "chat_echo_main", "bot_echo", "agent", "Green on API 31 and API 36. Glass falls back cleanly on 12.", now - 3 * 60 * 60_000),
        FakeMessage("m_r1", "chat_room_main", "bot_atlas", "agent", "@all standup: transport is green, pairing next.", now - 64 * 60_000),
        FakeMessage("m_r2", "chat_room_main", "bot_scout", "agent", "Desktop still builds on 3.0.0.", now - 63 * 60_000),
        FakeMessage("m_r3", "chat_room_main", "bot_nova", "agent", "The android platform migration is queued.", now - 62 * 60_000),
        FakeMessage("m_r4", "chat_room_main", "user", "user", "@all thanks — ship it.", now - 60 * 60_000),
        FakeMessage("m_p1", "chat_pixel_main", "bot_pixel", "agent", "Nightly build passed.", now - 2 * 86_400_000),
    )

    val needsYou = mutableStateListOf(
        FakeNeedsItem(
            id = "need_1",
            botId = "bot_atlas",
            computerId = "cmp_macbook",
            chatId = "chat_atlas_main",
            messageId = "m_a5",
            kind = "approval",
            title = "Atlas needs permission",
            detail = "Run `cargo test` in host/",
            createdAt = now - 12 * 60_000,
        ),
        FakeNeedsItem(
            id = "need_2",
            botId = "bot_nova",
            computerId = "cmp_studio",
            chatId = "chat_nova_main",
            messageId = "m_n2",
            kind = "question",
            title = "Nova is waiting for an answer",
            detail = "Should I delete the old migration?",
            createdAt = now - 8 * 60_000,
        ),
        FakeNeedsItem(
            id = "need_3",
            botId = "bot_scout",
            computerId = "cmp_mini",
            chatId = "chat_scout_main",
            messageId = null,
            kind = "failed",
            title = "A message failed to send",
            detail = "Office Mac mini is offline · retry when it returns",
            createdAt = now - 26 * 60_000,
        ),
        FakeNeedsItem(
            id = "need_4",
            botId = "bot_echo",
            computerId = "cmp_studio",
            chatId = "chat_echo_main",
            messageId = "m_e1",
            kind = "signin",
            title = "Gemini CLI needs a sign-in",
            detail = "Finish sign-in on Studio",
            createdAt = now - 3 * 60 * 60_000,
        ),
    )

    var transport by mutableStateOf(FakeTransport.Direct)
        private set
    var rev by mutableIntStateOf(412)
        private set
    var lastEvent by mutableStateOf("Fixture ready · 3 computers grouped")
        private set
    var connectedAt by mutableStateOf(now)
        private set

    fun note(event: String) {
        lastEvent = event
    }

    fun bot(id: String?): FakeBot? = bots.firstOrNull { it.id == id }

    fun computer(id: String?): FakeComputer? = computers.firstOrNull { it.id == id }

    fun chatsFor(botId: String): List<FakeChat> = chats.filter { it.botId == botId }.sortedByDescending { it.updatedAt }

    fun mainChatId(botId: String): String = chatsFor(botId).firstOrNull { it.title == "Main" }?.id
        ?: chatsFor(botId).firstOrNull()?.id
        ?: "chat_${botId}_main"

    fun messagesFor(chatId: String): List<FakeMessage> = messages.filter { it.chatId == chatId }.sortedBy { it.createdAt }

    fun needsFor(botId: String): List<FakeNeedsItem> = needsYou.filter { it.botId == botId }

    fun visibleBots(): List<FakeBot> = bots.filter { !it.hidden }

    fun togglePin(botId: String) {
        val index = bots.indexOfFirst { it.id == botId }
        if (index >= 0) bots[index] = bots[index].copy(pinned = !bots[index].pinned)
    }

    fun hideBot(botId: String) {
        val index = bots.indexOfFirst { it.id == botId }
        if (index >= 0) bots[index] = bots[index].copy(hidden = true)
    }

    fun markRead(botId: String) {
        val index = bots.indexOfFirst { it.id == botId }
        if (index >= 0) bots[index] = bots[index].copy(unread = 0)
        for (i in chats.indices) {
            if (chats[i].botId == botId) chats[i] = chats[i].copy(unread = 0)
        }
        needsYou.removeAll { it.botId == botId && it.kind == "question" }
    }

    fun createChat(botId: String): String {
        val bot = bot(botId) ?: return mainChatId(botId)
        val id = "chat_${botId}_${chats.count { it.botId == botId } + 1}"
        chats.add(FakeChat(id, botId, if (bot.kind == "group") "Room ${chats.count { it.botId == botId } + 1}" else "Chat ${chats.count { it.botId == botId } + 1}", "New chat", System.currentTimeMillis()))
        needsYou.removeAll { it.chatId == id }
        return id
    }

    fun createBot(
        name: String,
        computerId: String = computers.firstOrNull { it.transport != FakeTransport.Offline }?.id
            ?: computers.first().id,
        description: String = "New bot",
        backend: String = "Claude Code",
        character: String = "✨",
        color: String = "violet",
        shape: String = "blob",
    ): String {
        val id = "bot_local_${System.currentTimeMillis()}"
        val at = System.currentTimeMillis()
        bots.add(
            0,
            FakeBot(
                id = id,
                computerId = computerId,
                name = name.trim().ifBlank { "New bot" },
                character = character,
                color = color,
                shape = shape,
                backend = backend,
                description = description,
                status = "idle",
                activity = "",
                lastLine = "Ready when you are.",
                lastAt = at,
                unread = 0,
            ),
        )
        val chatId = "chat_${id}_main"
        chats.add(0, FakeChat(chatId, id, "Main", "Ready when you are.", at))
        rev++
        lastEvent = "created bot · ${name.trim().ifBlank { "New bot" }}"
        return id
    }

    fun createGroup(
        name: String,
        memberIds: List<String>,
        computerId: String = computers.firstOrNull { it.transport != FakeTransport.Offline }?.id
            ?: computers.first().id,
        description: String = "Group room",
        character: String = "🚀",
        color: String = "magenta",
        shape: String = "cloud",
    ): String {
        val id = "bot_room_${System.currentTimeMillis()}"
        val at = System.currentTimeMillis()
        val members = memberIds.filter { bot(it)?.kind != "group" }.distinct()
        bots.add(
            0,
            FakeBot(
                id = id,
                computerId = computerId,
                name = name.trim().ifBlank { "New room" },
                character = character,
                color = color,
                shape = shape,
                backend = "Group",
                description = description,
                status = "idle",
                activity = "",
                lastLine = "Room created.",
                lastAt = at,
                unread = 0,
                kind = "group",
                members = members,
            ),
        )
        val chatId = "chat_${id}_main"
        chats.add(0, FakeChat(chatId, id, "Main", "Room created.", at))
        messages.add(
            FakeMessage(
                "m_${id}_notice",
                chatId,
                "system",
                "notice",
                "You created ${name.trim().ifBlank { "New room" }}.",
                at,
            ),
        )
        rev++
        lastEvent = "created room · ${name.trim().ifBlank { "New room" }}"
        return id
    }

    fun send(chatId: String, text: String) {
        val chat = chats.firstOrNull { it.id == chatId } ?: return
        val at = System.currentTimeMillis()
        messages.add(FakeMessage("local_${at}_${messages.size}", chatId, "user", "user", text, at))
        updateChat(chatId, text, at)
        rev++
        lastEvent = "user message · ${chat.title}"
    }

    fun reply(chatId: String, authorId: String, text: String, kind: String = "agent") {
        val chat = chats.firstOrNull { it.id == chatId } ?: return
        val at = System.currentTimeMillis()
        messages.add(FakeMessage("local_${at}_${messages.size}", chatId, authorId, kind, text, at))
        updateChat(chatId, text, at)
        rev++
        lastEvent = "$kind · ${bot(authorId)?.name ?: "you"}"
    }

    fun resolveNeedByMessage(messageId: String) {
        needsYou.removeAll { it.messageId == messageId }
        rev++
        lastEvent = "approval resolved"
    }

    fun resolveNeed(id: String) {
        needsYou.removeAll { it.id == id }
        rev++
        lastEvent = "approval resolved"
    }

    fun useTransport(next: FakeTransport) {
        transport = next
        if (next == FakeTransport.Offline) {
            val idx = computers.indexOfFirst { it.id == "cmp_macbook" }
            if (idx >= 0) computers[idx] = computers[idx].copy(transport = FakeTransport.Offline)
            lastEvent = "connection lost · waiting for a link"
        } else {
            val idx = computers.indexOfFirst { it.id == "cmp_macbook" }
            if (idx >= 0) computers[idx] = computers[idx].copy(transport = next, lastSeen = System.currentTimeMillis())
            connectedAt = System.currentTimeMillis()
            lastEvent = "resumed from rev ${rev}"
        }
    }

    fun simulateEvent() {
        rev++
        lastEvent = "bot event · rev $rev"
    }

    val routines = mutableStateListOf(
        FakeRoutine(
            id = "rtn_standup",
            botId = "bot_atlas",
            title = "Morning standup",
            schedule = "Weekdays 9:00",
            timezone = "Asia/Dubai",
            webhook = null,
            prompt = "@all What shipped yesterday and what's blocked?",
            enabled = true,
            lastRunAt = now - 3_600_000,
            runs = listOf(
                FakeRoutineRun("rr1", now - 3_600_000, "ok", "Posted standup to #ship"),
                FakeRoutineRun("rr0", now - 90_000_000, "ok", "Posted standup to #ship"),
            ),
        ),
        FakeRoutine(
            id = "rtn_review",
            botId = "bot_nova",
            title = "PR review sweep",
            schedule = "Every 2 hours",
            timezone = "Asia/Dubai",
            webhook = "https://hooks.codync.dev/rtn_review",
            prompt = "Review open PRs and leave a checklist.",
            enabled = true,
            lastRunAt = now - 7_200_000,
            runs = listOf(FakeRoutineRun("rr2", now - 7_200_000, "ok", "3 PRs reviewed")),
        ),
        FakeRoutine(
            id = "rtn_backup",
            botId = "bot_atlas",
            title = "Nightly memory export",
            schedule = "Daily 02:00",
            timezone = "UTC",
            prompt = "Export pinned memories to files.",
            enabled = false,
            lastRunAt = null,
        ),
    )

    val memories = mutableStateListOf(
        FakeMemory("mem_1", "bot_atlas", "Prefer Kotlin over Java for Android patches.", true, now - 86_400_000, "chat"),
        FakeMemory("mem_2", "bot_atlas", "Ship APKs to ~/Downloads as Codync-Dev-*.apk.", true, now - 40_000_000, "manual"),
        FakeMemory("mem_3", "bot_nova", "Group room #ship uses @all for handoffs.", false, now - 10_000_000, "group"),
        FakeMemory("mem_4", "bot_atlas", "Glass nav pill must track swipe offset, not settledPage.", false, now - 5_000_000, "chat"),
    )

    val marketplace = mutableStateListOf(
        FakeMarketItem("mkt_cursor", "agent", "Cursor ACP", "Agent backend for Cursor", true, setupLog = "cursor-agent ready"),
        FakeMarketItem("mkt_claude", "agent", "Claude Code", "Anthropic agent backend", true, setupLog = "claude signed in"),
        FakeMarketItem("mkt_node", "agent", "Node runner", "Local Node agent host", false, setupLog = ""),
        FakeMarketItem("mkt_gh", "mcp", "GitHub", "Issues, PRs and checks", true, setupLog = "gh auth ok"),
        FakeMarketItem("mkt_drive", "mcp", "Google Drive", "Files and docs", false, needsSignIn = true),
        FakeMarketItem("mkt_custom", "mcp", "Custom MCP", "Import from URL or npx", false),
        FakeMarketItem("mkt_composio", "composio", "Composio", "100+ SaaS connectors", false, needsSignIn = true),
        FakeMarketItem("mkt_skill_ship", "skill", "Ship checklist", "Pre-release QA steps", true),
        FakeMarketItem("mkt_vault", "credential", "OpenAI", "API key in vault", true),
        FakeMarketItem("mkt_anthropic", "credential", "Anthropic", "API key in vault", true),
    )

    val askEdges = mutableStateListOf(
        FakeAskEdge("bot_atlas", "bot_nova", "asks"),
        FakeAskEdge("bot_nova", "bot_atlas", "reviews"),
    )

    val files = mutableStateListOf(
        FakeFile("f1", "ship-notes.md", "bot_atlas", "12 KB", now - 8_000_000),
        FakeFile("f2", "result.zip", "bot_nova", "2.4 MB", now - 20_000_000),
        FakeFile("f3", "standup-2026-10-22.txt", "bot_atlas", "4 KB", now - 86_400_000),
        FakeFile("f4", "screenshot-browser.png", null, "380 KB", now - 3_600_000),
    )

    val browserFrames = mutableStateListOf(
        FakeBrowserFrame("br1", "Codync cloud", "https://codync.dev/dashboard", "cmp_macbook"),
        FakeBrowserFrame("br2", "GitHub PR #482", "https://github.com/pokai/codync/pull/482", "cmp_macbook"),
    )

    val usage = mutableStateListOf(
        FakeUsageProvider("u_claude", "Claude", "42%", "of plan", 0.42f),
        FakeUsageProvider("u_gpt", "OpenAI", "18%", "of plan", 0.18f),
        FakeUsageProvider("u_gemini", "Gemini", "7%", "of plan", 0.07f),
    )

    val connectionRequests = mutableStateListOf(
        FakeConnectionRequest("cr1", "Slack workspace", "composio", now - 600_000),
        FakeConnectionRequest("cr2", "Linear", "mcp", now - 3_600_000),
    )

    var fastMode by mutableStateOf(false)
    var selectedWorkspace by mutableStateOf("~/projects/realbot")

    fun toggleRoutine(id: String) {
        val i = routines.indexOfFirst { it.id == id }
        if (i >= 0) {
            routines[i] = routines[i].copy(enabled = !routines[i].enabled)
            lastEvent = "routine ${if (routines[i].enabled) "on" else "off"} · ${routines[i].title}"
        }
    }

    fun updateRoutine(updated: FakeRoutine) {
        val i = routines.indexOfFirst { it.id == updated.id }
        if (i >= 0) {
            routines[i] = updated
            lastEvent = "routine saved · ${updated.title}"
        } else {
            routines.add(0, updated)
            lastEvent = "routine created · ${updated.title}"
        }
    }

    fun deleteRoutine(id: String) {
        routines.removeAll { it.id == id }
        lastEvent = "routine deleted"
    }

    fun testRoutine(id: String) {
        val i = routines.indexOfFirst { it.id == id }
        if (i < 0) return
        val at = System.currentTimeMillis()
        val run = FakeRoutineRun("rr_${at}", at, "ok", "Test run completed")
        val cur = routines[i]
        routines[i] = cur.copy(lastRunAt = at, runs = listOf(run) + cur.runs)
        lastEvent = "routine test · ${cur.title}"
    }

    fun pinMemory(id: String) {
        val i = memories.indexOfFirst { it.id == id }
        if (i >= 0) {
            memories[i] = memories[i].copy(pinned = !memories[i].pinned, updatedAt = System.currentTimeMillis())
            lastEvent = "memory pin · ${memories[i].text.take(32)}"
        }
    }

    fun addMemory(botId: String, text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        memories.add(0, FakeMemory("mem_${System.currentTimeMillis()}", botId, t, false, System.currentTimeMillis()))
        lastEvent = "memory added"
    }

    fun deleteMemory(id: String) {
        memories.removeAll { it.id == id }
        lastEvent = "memory deleted"
    }

    fun toggleMarket(id: String) {
        val i = marketplace.indexOfFirst { it.id == id }
        if (i >= 0) {
            marketplace[i] = marketplace[i].copy(installed = !marketplace[i].installed)
            lastEvent = "${if (marketplace[i].installed) "installed" else "removed"} · ${marketplace[i].name}"
        }
    }

    fun setMarketForBot(id: String, botId: String, enabled: Boolean) {
        val i = marketplace.indexOfFirst { it.id == id }
        if (i < 0) return
        val item = marketplace[i]
        val current = if (item.enabledBots.isNotEmpty()) {
            item.enabledBots
        } else if (item.installed) {
            visibleBots().map { it.id }.toSet()
        } else {
            emptySet()
        }
        val next = if (enabled) current + botId else current - botId
        marketplace[i] = item.copy(enabledBots = next)
        lastEvent = "${if (enabled) "enabled" else "disabled"} ${item.name} for ${bot(botId)?.name ?: "bot"}"
    }

    fun resolveConnectionRequest(id: String, allow: Boolean) {
        connectionRequests.removeAll { it.id == id }
        lastEvent = if (allow) "connector allowed" else "connector denied"
    }

    fun updateBot(
        id: String,
        name: String? = null,
        description: String? = null,
        backend: String? = null,
        color: String? = null,
        shape: String? = null,
        character: String? = null,
        hidden: Boolean? = null,
        pinned: Boolean? = null,
        members: List<String>? = null,
    ) {
        val i = bots.indexOfFirst { it.id == id }
        if (i < 0) return
        val b = bots[i]
        bots[i] = b.copy(
            name = name ?: b.name,
            description = description ?: b.description,
            backend = backend ?: b.backend,
            color = color ?: b.color,
            shape = shape ?: b.shape,
            character = character ?: b.character,
            hidden = hidden ?: b.hidden,
            pinned = pinned ?: b.pinned,
            members = members ?: b.members,
        )
        lastEvent = "bot updated · ${bots[i].name}"
    }

    fun deleteBot(id: String) {
        bots.removeAll { it.id == id }
        chats.removeAll { it.botId == id }
        lastEvent = "bot deleted"
    }

    fun rewindChat(chatId: String, toMessageId: String) {
        val idx = messages.indexOfFirst { it.id == toMessageId && it.chatId == chatId }
        if (idx < 0) return
        val dropIds = messages
            .withIndex()
            .filter { (i, m) -> m.chatId == chatId && i >= idx }
            .map { it.value.id }
            .toSet()
        messages.removeAll { it.id in dropIds }
        needsYou.removeAll { it.messageId != null && it.messageId in dropIds }
        messages.add(
            FakeMessage(
                "m_rewind_${System.currentTimeMillis()}",
                chatId,
                "system",
                "notice",
                "Rewound before selected message.",
                System.currentTimeMillis(),
            ),
        )
        lastEvent = "rewind · $chatId"
        rev++
    }

    private fun updateChat(chatId: String, lastLine: String, at: Long) {
        val index = chats.indexOfFirst { it.id == chatId }
        if (index >= 0) chats[index] = chats[index].copy(lastLine = lastLine, updatedAt = at)
        val botId = chats.firstOrNull { it.id == chatId }?.botId ?: return
        val botIndex = bots.indexOfFirst { it.id == botId }
        if (botIndex >= 0) bots[botIndex] = bots[botIndex].copy(lastLine = lastLine, lastAt = at)
    }
}
