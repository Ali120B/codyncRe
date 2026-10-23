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

    private fun updateChat(chatId: String, lastLine: String, at: Long) {
        val index = chats.indexOfFirst { it.id == chatId }
        if (index >= 0) chats[index] = chats[index].copy(lastLine = lastLine, updatedAt = at)
        val botId = chats.firstOrNull { it.id == chatId }?.botId ?: return
        val botIndex = bots.indexOfFirst { it.id == botId }
        if (botIndex >= 0) bots[botIndex] = bots[botIndex].copy(lastLine = lastLine, lastAt = at)
    }
}
