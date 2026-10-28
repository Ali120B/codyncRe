package com.pokai.codync.android

import android.content.Context
import com.pokai.codync.data.SessionStore
import com.pokai.codync.model.Computer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Process-wide host session with a local, per-computer cache for offline reading. */
@OptIn(FlowPreview::class)
class AndroidHostSession(context: Context) {
    val identity = DeviceIdentity(context.applicationContext)
    private val prefs = context.applicationContext.getSharedPreferences("codync_host_session", Context.MODE_PRIVATE)
    private val cache = HostCacheDb(context.applicationContext)
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var pushManager: AndroidPushManager
    lateinit var store: SessionStore
        private set

    init {
        store = SessionStore(identity.transport(), onComputerSelected = { computer, switching ->
            saveComputer(computer)
            if (switching) {
                val cached = withContext(Dispatchers.IO) { cache.load(computer.id) }
                store.restoreCache(computer, cached.bots, cached.entriesByBot, cached.chatTitlesByBot)
            }
        }, outbox = cache)
        pushManager = AndroidPushManager(context.applicationContext, store, identity)

        val computers = loadComputers()
        scope.launch {
            computers.firstOrNull()?.let { computer ->
                val cached = withContext(Dispatchers.IO) { cache.load(computer.id) }
                store.restoreCache(computer, cached.bots, cached.entriesByBot, cached.chatTitlesByBot)
            }
            store.restoreComputers(computers)
        }
        scope.launch {
            store.snapshot
                .mapNotNull { state ->
                    state.connection.computer?.takeIf { state.connection.hello != null }?.let {
                        CacheWrite(it.id, state.bots, state.entriesByBot, state.chatTitlesByBot)
                    }
                }
                // Connection/revision updates keep the durable collections by reference. Avoid
                // a deep equality walk over the cached transcript or resetting the write timer
                // for host events that do not change cached data.
                .distinctUntilChanged { old, new ->
                    old.bots === new.bots &&
                        old.entriesByBot === new.entriesByBot &&
                        old.chatTitlesByBot === new.chatTitlesByBot
                }
                .debounce(350)
                .collect { write ->
                    val cached = CachedHostData(write.bots, write.entriesByBot, write.chatTitlesByBot)
                    withContext(Dispatchers.IO) { cache.save(write.computerId, cached) }
                }
        }
    }

    private fun saveComputer(computer: Computer) {
        val saved = listOf(computer) + loadComputers().filterNot { it.id == computer.id }
        prefs.edit().putString(KEY_COMPUTERS, json.encodeToString(saved)).remove(KEY_LEGACY_COMPUTER).apply()
    }

    private fun loadComputers(): List<Computer> {
        val current = prefs.getString(KEY_COMPUTERS, null)?.let { raw ->
            runCatching { json.decodeFromString<List<Computer>>(raw) }.getOrNull()
        }
        if (!current.isNullOrEmpty()) return current.distinctBy(Computer::id)
        val legacy = prefs.getString(KEY_LEGACY_COMPUTER, null)?.let { raw ->
            runCatching { json.decodeFromString<Computer>(raw) }.getOrNull()
        }
        return listOfNotNull(legacy)
    }

    private data class CacheWrite(
        val computerId: String,
        val bots: List<com.pokai.codync.model.Bot>,
        val entriesByBot: Map<String, List<com.pokai.codync.model.Entry>>,
        val chatTitlesByBot: Map<String, Map<String, String>>,
    )

    companion object {
        private const val KEY_COMPUTERS = "paired_computers"
        private const val KEY_LEGACY_COMPUTER = "paired_computer"
    }
}
