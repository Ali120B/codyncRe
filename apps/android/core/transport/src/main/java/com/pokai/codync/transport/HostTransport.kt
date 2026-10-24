package com.pokai.codync.transport

import com.pokai.codync.model.Computer
import com.pokai.codync.model.Hello
import com.pokai.codync.model.PairOffer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

enum class TransportKind { Direct, Relay, Offline }

data class ConnectionState(
    val kind: TransportKind = TransportKind.Offline,
    val computer: Computer? = null,
    val hello: Hello? = null,
    val rev: Long = 0,
    val lastError: String? = null,
    val connectedAt: Long? = null,
)

/** Phone-to-host API over the authenticated encrypted channel, direct or relayed through Codync Cloud. */
interface HostTransport {
    val state: StateFlow<ConnectionState>
    val events: Flow<HostEvent>

    suspend fun pair(offer: PairOffer, deviceName: String): Result<Computer>
    suspend fun connect(computer: Computer): Result<Hello>
    suspend fun disconnect()
    suspend fun sendUserMessage(botId: String, chatId: String, text: String, clientNonce: String): Result<Unit>
    suspend fun respondPermission(entryId: String, optionId: String): Result<Unit>

    /** Calls a host API over the authenticated channel. */
    suspend fun call(method: String, body: JsonObject): Result<JsonElement> =
        Result.failure(UnsupportedOperationException("This transport does not support host calls"))
}
