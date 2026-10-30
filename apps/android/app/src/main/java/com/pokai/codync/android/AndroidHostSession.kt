package com.pokai.codync.android

import android.content.Context
import com.pokai.codync.data.SessionStore
import com.pokai.codync.model.Computer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
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
        store = SessionStore(
            createTransport = { identity.transport() },
            outbox = cache,
            onComputerSaved = ::saveComputer,
        )
        pushManager = AndroidPushManager(context.applicationContext, store, identity)

        val computers = loadComputers()
        scope.launch {
            // Cached transcripts make every paired computer readable before its socket is up.
            computers.forEach { computer ->
                val cached = withContext(Dispatchers.IO) { cache.load(computer.id) }
                store.restoreCache(computer, cached.bots, cached.entriesByBot, cached.chatTitlesByBot)
            }
            store.restoreComputers(computers)
        }
        scope.launch {
            val jobs = HashMap<String, Job>()
            val written = HashMap<String, CachedHostData>()
            store.sessions.collect { lanes ->
                jobs.keys.retainAll(lanes.keys)
                written.keys.retainAll(lanes.keys)
                lanes.forEach { (computerId, lane) ->
                    if (jobs.containsKey(computerId)) return@forEach
                    jobs[computerId] = scope.launch {
                        var last: CachedHostData? = null
                        lane.snapshot.debounce(350).collect { state ->
                            if (state.connection.hello == null) return@collect
                            val data = CachedHostData(state.bots, state.entriesByBot, state.chatTitlesByBot)
                            val previous = last
                            // Connection/revision ticks reuse the durable collections. Avoid
                            // rewriting an unchanged transcript or resetting the write timer.
                            if (previous != null &&
                                previous.bots === data.bots &&
                                previous.entriesByBot === data.entriesByBot &&
                                previous.chatTitlesByBot === data.chatTitlesByBot
                            ) return@collect
                            last = data
                            written[computerId] = data
                            withContext(Dispatchers.IO) { cache.save(computerId, data) }
                        }
                    }
                }
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

    companion object {
        private const val KEY_COMPUTERS = "paired_computers"
        private const val KEY_LEGACY_COMPUTER = "paired_computer"
    }
}
