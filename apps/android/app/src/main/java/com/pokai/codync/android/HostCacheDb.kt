package com.pokai.codync.android

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.pokai.codync.model.Bot
import com.pokai.codync.model.Entry
import com.pokai.codync.model.WireJson
import com.pokai.codync.data.PendingSend
import com.pokai.codync.data.PendingSendOutbox
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Serializable
data class CachedHostData(
    val bots: List<Bot> = emptyList(),
    val entriesByBot: Map<String, List<Entry>> = emptyMap(),
    val chatTitlesByBot: Map<String, Map<String, String>> = emptyMap(),
)

/** Device-local, per-computer mirror for offline reading. Only the latest 500 rows per bot are kept. */
internal class HostCacheDb(context: Context) : SQLiteOpenHelper(context, "codync-host-cache.db", null, 3), PendingSendOutbox {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE bots (computer_id TEXT NOT NULL, bot_id TEXT NOT NULL, payload TEXT NOT NULL, PRIMARY KEY(computer_id, bot_id))")
        db.execSQL("CREATE TABLE entries (computer_id TEXT NOT NULL, bot_id TEXT NOT NULL, entry_id TEXT NOT NULL, seq INTEGER NOT NULL, payload TEXT NOT NULL, PRIMARY KEY(computer_id, entry_id))")
        db.execSQL("CREATE INDEX entries_by_chat ON entries(computer_id, bot_id, seq)")
        db.execSQL("CREATE TABLE chat_titles (computer_id TEXT NOT NULL, bot_id TEXT NOT NULL, payload TEXT NOT NULL, PRIMARY KEY(computer_id, bot_id))")
        createOutbox(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("CREATE TABLE IF NOT EXISTS chat_titles (computer_id TEXT NOT NULL, bot_id TEXT NOT NULL, payload TEXT NOT NULL, PRIMARY KEY(computer_id, bot_id))")
        }
        if (oldVersion < 3) createOutbox(db)
    }

    fun load(computerId: String): CachedHostData {
        val db = readableDatabase
        val bots = mutableListOf<Bot>()
        db.query("bots", arrayOf("payload"), "computer_id = ?", arrayOf(computerId), null, null, null).use { cursor ->
            while (cursor.moveToNext()) {
                runCatching { WireJson.json.decodeFromString<Bot>(cursor.getString(0)) }.getOrNull()?.let(bots::add)
            }
        }
        val entries = mutableMapOf<String, MutableList<Entry>>()
        db.query("entries", arrayOf("bot_id", "payload"), "computer_id = ?", arrayOf(computerId), null, null, "seq ASC").use { cursor ->
            while (cursor.moveToNext()) {
                val botId = cursor.getString(0)
                runCatching { WireJson.json.decodeFromString<Entry>(cursor.getString(1)) }.getOrNull()?.let { entry ->
                    entries.getOrPut(botId) { mutableListOf() }.add(entry)
                }
            }
        }
        val titles = mutableMapOf<String, Map<String, String>>()
        db.query("chat_titles", arrayOf("bot_id", "payload"), "computer_id = ?", arrayOf(computerId), null, null, null).use { cursor ->
            while (cursor.moveToNext()) {
                val botId = cursor.getString(0)
                runCatching { WireJson.json.decodeFromString<Map<String, String>>(cursor.getString(1)) }
                    .getOrNull()?.let { titles[botId] = it }
            }
        }
        return CachedHostData(bots, entries, titles)
    }

    fun save(computerId: String, data: CachedHostData) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("bots", "computer_id = ?", arrayOf(computerId))
            db.delete("entries", "computer_id = ?", arrayOf(computerId))
            db.delete("chat_titles", "computer_id = ?", arrayOf(computerId))
            data.bots.forEach { bot ->
                db.insertOrThrow("bots", null, ContentValues().apply {
                    put("computer_id", computerId)
                    put("bot_id", bot.id)
                    put("payload", WireJson.json.encodeToString(bot))
                })
            }
            data.entriesByBot.forEach { (botId, rows) ->
                rows
                    .filter { !it.id.startsWith("local-") && it.seq != Long.MAX_VALUE }
                    .takeLast(500)
                    .forEach { entry ->
                        db.insertOrThrow("entries", null, ContentValues().apply {
                            put("computer_id", computerId)
                            put("bot_id", botId)
                            put("entry_id", entry.id)
                            put("seq", entry.seq)
                            put("payload", WireJson.json.encodeToString(entry))
                        })
                    }
            }
            data.chatTitlesByBot.forEach { (botId, titles) ->
                db.insertOrThrow("chat_titles", null, ContentValues().apply {
                    put("computer_id", computerId)
                    put("bot_id", botId)
                    put("payload", WireJson.json.encodeToString(titles))
                })
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    override suspend fun enqueue(send: PendingSend) {
        withContext(Dispatchers.IO) {
            writableDatabase.insertWithOnConflict("queued_sends", null, ContentValues().apply {
                put("computer_id", send.computerId)
                put("bot_id", send.botId)
                put("chat_id", send.chatId)
                put("text", send.text)
                put("client_nonce", send.clientNonce)
                put("created_at", send.createdAt)
            }, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    override suspend fun pending(computerId: String): List<PendingSend> = withContext(Dispatchers.IO) {
        val sends = mutableListOf<PendingSend>()
        readableDatabase.query(
            "queued_sends",
            arrayOf("computer_id", "bot_id", "chat_id", "text", "client_nonce", "created_at"),
            "computer_id = ?",
            arrayOf(computerId),
            null,
            null,
            "created_at ASC",
        ).use { cursor ->
            while (cursor.moveToNext()) sends += PendingSend(
                computerId = cursor.getString(0),
                botId = cursor.getString(1),
                chatId = cursor.getString(2),
                text = cursor.getString(3),
                clientNonce = cursor.getString(4),
                createdAt = cursor.getLong(5),
            )
        }
        sends
    }

    override suspend fun remove(clientNonce: String) {
        withContext(Dispatchers.IO) {
            writableDatabase.delete("queued_sends", "client_nonce = ?", arrayOf(clientNonce))
        }
    }

    private fun createOutbox(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS queued_sends (computer_id TEXT NOT NULL, bot_id TEXT NOT NULL, chat_id TEXT NOT NULL, text TEXT NOT NULL, client_nonce TEXT PRIMARY KEY, created_at INTEGER NOT NULL)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS queued_sends_by_computer ON queued_sends(computer_id, created_at)")
    }
}
