package com.pokai.codync.android

import com.pokai.codync.android.fake.FakeHostState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeHostTest {

    @Test
    fun testInitialState() {
        val state = FakeHostState()
        assertEquals(3, state.computers.size)
        assertTrue(state.bots.isNotEmpty())
        assertTrue(state.needsYou.isNotEmpty())

        val atlas = state.bots.firstOrNull { it.name == "Atlas" }
        assertNotNull(atlas)
        assertEquals("bot_atlas", atlas?.id)

        val chats = state.chatsFor("bot_atlas")
        assertTrue(chats.isNotEmpty())
        val mainChatId = state.mainChatId("bot_atlas")
        assertNotNull(mainChatId)
    }

    @Test
    fun testSendMessage() {
        val state = FakeHostState()
        val chatId = state.mainChatId("bot_atlas")
        val initialCount = state.messagesFor(chatId).size

        state.send(chatId, "Hello test message")
        val newMessages = state.messagesFor(chatId)
        assertEquals(initialCount + 1, newMessages.size)
        assertEquals("Hello test message", newMessages.last().text)
    }

    @Test
    fun testCreateBotAndGroup() {
        val state = FakeHostState()
        val botId = state.createBot("Pixel 2", character = "🫧", color = "blue")
        assertNotNull(state.bot(botId))
        assertEquals("Pixel 2", state.bot(botId)?.name)
        assertTrue(state.chatsFor(botId).isNotEmpty())

        val groupId = state.createGroup("Ship Room", listOf("bot_atlas", "bot_scout"))
        val group = state.bot(groupId)
        assertNotNull(group)
        assertEquals("group", group?.kind)
        assertEquals(2, group?.members?.size)
    }
}
