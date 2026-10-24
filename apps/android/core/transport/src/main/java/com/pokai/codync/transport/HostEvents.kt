package com.pokai.codync.transport

import com.pokai.codync.model.AccessRequest
import com.pokai.codync.model.Bot
import com.pokai.codync.model.CloudStatus
import com.pokai.codync.model.Entry
import com.pokai.codync.model.Hello
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

sealed class HostEvent {
    data class Hello(val hello: com.pokai.codync.model.Hello) : HostEvent()
    data class BotUpsert(val bot: Bot) : HostEvent()
    data class BotDeleted(val id: String, val rev: Long) : HostEvent()
    data class EntryUpsert(val entry: Entry) : HostEvent()
    data class EntriesDeleted(val botId: String, val ids: List<String>, val rev: Long) : HostEvent()
    data class Bots(val bots: List<Bot>) : HostEvent()
    data class Entries(val chatId: String, val entries: List<Entry>) : HostEvent()
    data class AccessRequests(val requests: List<AccessRequest>) : HostEvent()
    data class Cloud(val cloud: CloudStatus) : HostEvent()
    data class ChatDeleted(val botId: String, val chatId: String) : HostEvent()
    data class ChatTitle(val botId: String, val chatId: String, val title: String) : HostEvent()
    data class Rev(val rev: Long) : HostEvent()
    data class Resync(val reason: String? = null) : HostEvent()
    data class Error(val message: String) : HostEvent()
    data class Undecodable(val kind: String) : HostEvent()
}

object HostEventParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parse(line: String): HostEvent? {
        val raw = runCatching { json.parseToJsonElement(line).jsonObject }.getOrNull() ?: return null
        return when (raw.str("type")) {
            "hello" -> {
                val hello = runCatching { json.decodeFromJsonElement(Hello.serializer(), raw) }.getOrNull()
                    ?: Hello(
                        hostId = raw.str("hostId").orEmpty(),
                        name = raw.str("name").orEmpty(),
                        version = raw.str("version").orEmpty(),
                        os = raw.str("os").orEmpty(),
                        rev = raw.long("rev") ?: 0,
                    )
                HostEvent.Hello(hello)
            }
            "bot" -> {
                val botEl = raw["bot"]?.jsonObject ?: return HostEvent.Undecodable("bot")
                val deleted = botEl["deleted"]?.jsonPrimitive?.booleanOrNull == true
                val id = botEl.str("id") ?: return HostEvent.Undecodable("bot")
                if (deleted) {
                    HostEvent.BotDeleted(id, botEl.long("rev") ?: 0)
                } else {
                    runCatching { json.decodeFromJsonElement(Bot.serializer(), botEl) }
                        .map { HostEvent.BotUpsert(it) }
                        .getOrElse { HostEvent.Undecodable("bot") }
                }
            }
            "entry" -> {
                val entryEl = raw["entry"] ?: return HostEvent.Undecodable("entry")
                runCatching { json.decodeFromJsonElement(Entry.serializer(), entryEl) }
                    .map { HostEvent.EntryUpsert(it) }
                    .getOrElse { HostEvent.Undecodable("entry") }
            }
            "entriesDeleted" -> {
                val botId = raw.str("botId") ?: return HostEvent.Undecodable("entriesDeleted")
                val ids = raw["ids"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
                HostEvent.EntriesDeleted(botId, ids, raw.long("rev") ?: 0)
            }
            "accessRequests" -> {
                val reqs = raw["requests"]?.jsonArray?.mapNotNull {
                    runCatching { json.decodeFromJsonElement(AccessRequest.serializer(), it) }.getOrNull()
                }.orEmpty()
                HostEvent.AccessRequests(reqs)
            }
            "cloud" -> {
                val cloudEl = raw["cloud"] ?: return null
                runCatching { json.decodeFromJsonElement(CloudStatus.serializer(), cloudEl) }
                    .map { HostEvent.Cloud(it) }
                    .getOrNull()
            }
            "chatDeleted" -> {
                val botId = raw.str("botId") ?: return null
                val chatId = raw.str("chatId") ?: return null
                HostEvent.ChatDeleted(botId, chatId)
            }
            "chatTitle" -> {
                val botId = raw.str("botId") ?: return null
                val chatId = raw.str("chatId") ?: return null
                val title = raw.str("title") ?: return null
                HostEvent.ChatTitle(botId, chatId, title)
            }
            "resync" -> HostEvent.Resync()
            else -> {
                if (raw.containsKey("bots")) {
                    val bots = raw["bots"]?.jsonArray?.mapNotNull {
                        runCatching { json.decodeFromJsonElement(Bot.serializer(), it) }.getOrNull()
                    }.orEmpty()
                    HostEvent.Bots(bots)
                } else {
                    null
                }
            }
        }
    }

    private fun JsonObject.str(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull

    private fun JsonObject.long(key: String): Long? =
        this[key]?.jsonPrimitive?.longOrNull
}
