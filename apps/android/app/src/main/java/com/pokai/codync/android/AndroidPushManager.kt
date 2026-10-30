package com.pokai.codync.android

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.pokai.codync.data.SessionStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URL

/** Registers this install's FCM destination with every connected host, independently. */
class AndroidPushManager(
    context: Context,
    private val store: SessionStore,
    private val identity: DeviceIdentity,
) {
    private val appContext = context.applicationContext
    private val hostPrefs = appContext.getSharedPreferences(HOST_PREFS, Context.MODE_PRIVATE)
    private val appPrefs = appContext.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val token = MutableStateFlow(hostPrefs.getString(KEY_TOKEN, null))
    private val enabled = MutableStateFlow(appPrefs.getBoolean(KEY_PUSH_ENABLED, true))
    // A registration can land while a host is still starting up or the relay is briefly down.
    // Re-register periodically instead of waiting for the socket to change state again.
    private val retry = MutableStateFlow(0)
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { preferences, key ->
        when {
            preferences === hostPrefs && key == KEY_TOKEN -> token.value = hostPrefs.getString(KEY_TOKEN, null)
            preferences === appPrefs && key == KEY_PUSH_ENABLED -> enabled.value = appPrefs.getBoolean(KEY_PUSH_ENABLED, true)
        }
    }

    init {
        hostPrefs.registerOnSharedPreferenceChangeListener(listener)
        appPrefs.registerOnSharedPreferenceChangeListener(listener)
        if (FirebaseApp.getApps(appContext).isNotEmpty()) {
            runCatching { FirebaseMessaging.getInstance().token }
                .onSuccess { task ->
                    task.addOnSuccessListener { value -> saveToken(value) }
                }
        }

        val targets = store.snapshot.map { state ->
            state.connections.mapValues { (computerId, connection) ->
                Target(computerId, connection.kind != com.pokai.codync.transport.TransportKind.Offline && connection.hello != null)
            }
        }.distinctUntilChanged()

        scope.launch {
            while (true) {
                delay(REGISTER_RETRY_MS)
                retry.value += 1
            }
        }

        scope.launch {
            combine(targets, token, enabled, retry) { current, fcmToken, notificationsEnabled, attempt ->
                SyncRequest(current, fcmToken, notificationsEnabled, attempt)
            }.distinctUntilChanged().collect { request ->
                val ticket = if (request.enabled && request.token != null) runCatching { relayTicket(request.token) }.getOrNull() else null
                request.targets.forEach { (computerId, target) ->
                    // Each host owns its own ticket and must know when this phone is reachable.
                    if (target.connected && request.enabled && ticket != null) {
                        runCatching {
                            store.callOn(computerId, "registerDevice", buildJsonObject {
                                put("ticket", ticket)
                                put("relay", RELAY_URL)
                                put("name", identity.deviceName)
                                put("pushKey", identity.pushPublicKey)
                                put("ctx", "android-local")
                            }).getOrThrow()
                        }.onFailure { failure ->
                            if (failure is CancellationException) throw failure
                            android.util.Log.w(TAG, "Couldn't register push on $computerId", failure)
                        }
                    } else if (target.connected && !request.enabled) {
                        store.callOn(computerId, "unregisterDevice", buildJsonObject {}).onFailure {
                            android.util.Log.w(TAG, "Couldn't unregister push on $computerId", it)
                        }
                    }
                }
            }
        }
    }

    private fun saveToken(value: String) {
        hostPrefs.edit().putString(KEY_TOKEN, value).apply()
    }

    private suspend fun relayTicket(fcmToken: String): String = withContext(Dispatchers.IO) {
        val connection = URL("$RELAY_URL/register").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            val body = buildJsonObject {
                put("platform", "android")
                put("token", fcmToken)
                put("kind", "alert")
            }.toString()
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val response = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            check(status in 200..299) { "Push relay registration failed ($status)" }
            Json.parseToJsonElement(response).jsonObject["ticket"]?.jsonPrimitive?.content
                ?: error("Push relay returned no ticket")
        } finally {
            connection.disconnect()
        }
    }

    private data class Target(val computerId: String, val connected: Boolean)
    private data class SyncRequest(
        val targets: Map<String, Target>,
        val token: String?,
        val enabled: Boolean,
        val attempt: Int = 0,
    )

    companion object {
        private const val TAG = "CodyncPush"
        private const val RELAY_URL = "https://codync-relay.codyncre.workers.dev"
        private const val HOST_PREFS = "codync_push"
        private const val APP_PREFS = "codync_prefs"
        private const val KEY_TOKEN = "fcm_token"
        private const val KEY_PUSH_ENABLED = "push"
        private const val REGISTER_RETRY_MS = 60 * 1000L

        fun saveUpdatedToken(context: Context, token: String) {
            context.applicationContext.getSharedPreferences(HOST_PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_TOKEN, token).apply()
        }
    }
}
