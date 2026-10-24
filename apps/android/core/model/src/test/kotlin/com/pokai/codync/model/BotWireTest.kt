package com.pokai.codync.model

import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BotWireTest {
    @Test
    fun botRoundTripIgnoresUnknown() {
        val raw = """
            {"id":"bot_atlas","name":"Atlas","avatarColor":"violet","avatarShape":"blob",
             "status":"working","unread":2,"extraFutureField":true,"members":[]}
        """.trimIndent()
        val bot = WireJson.json.decodeFromString(Bot.serializer(), raw)
        assertEquals("bot_atlas", bot.id)
        assertEquals("Atlas", bot.name)
        assertEquals("violet", bot.avatarColor)
        assertEquals("working", bot.status)
        val encoded = WireJson.json.encodeToString(bot)
        assertTrue(encoded.contains("\"avatarColor\":\"violet\""))
    }

    @Test
    fun entryChatVisibility() {
        val user = Entry(id = "1", botId = "b", kind = "user", data = EntryData(text = "hi"))
        val thought = Entry(id = "2", botId = "b", kind = "thought", data = EntryData(text = "…"))
        val finalAgent = Entry(id = "3", botId = "b", kind = "agent", data = EntryData(text = "ok", final = true))
        assertTrue(user.isChatVisible())
        assertTrue(!thought.isChatVisible())
        assertTrue(finalAgent.isChatVisible())
    }
}
