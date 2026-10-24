package com.pokai.codync.android

import com.clerk.api.Clerk
import com.clerk.api.network.serialization.ClerkResult
import com.pokai.codync.data.SessionStore
import com.pokai.codync.model.CloudAccessTicket
import com.pokai.codync.model.CloudComputer
import com.pokai.codync.model.Computer
import com.pokai.codync.transport.CloudAccountClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class AndroidAccountState(
    val configured: Boolean = BuildConfig.CLERK_PUBLISHABLE_KEY.isNotBlank(),
    val loading: Boolean = false,
    val connecting: Boolean = false,
    val computers: List<CloudComputer> = emptyList(),
    val ticket: CloudAccessTicket? = null,
    val message: String? = null,
)

/** Optional Clerk account path. Local QR pairing continues to work when no account is configured. */
class AndroidAccountSession(
    identity: DeviceIdentity,
    private val host: SessionStore,
    cloudUrl: String,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(AndroidAccountState())
    val state: StateFlow<AndroidAccountState> = _state.asStateFlow()
    private val client = CloudAccountClient(
        baseUrl = cloudUrl,
        deviceKey = identity.privateKey,
        deviceName = identity.deviceName,
        token = {
            when (val result = Clerk.auth.getToken()) {
                is ClerkResult.Success -> result.value
                is ClerkResult.Failure -> error(result.error.toString().ifBlank { "Sign in to Codync Cloud again" })
            }
        },
    )

    suspend fun refresh() {
        if (!_state.value.configured) return
        _state.update { it.copy(loading = true, message = null) }
        runCatching {
            client.registerDevice()
            client.computers()
        }.onSuccess { computers ->
            _state.update { it.copy(loading = false, computers = computers, message = null) }
        }.onFailure { error ->
            _state.update { it.copy(loading = false, message = error.message ?: "Couldn't load Codync Cloud") }
        }
    }

    fun clearAccount() {
        _state.update { it.copy(loading = false, connecting = false, computers = emptyList(), ticket = null, message = null) }
    }

    suspend fun requestAccess(computer: CloudComputer) {
        _state.update { it.copy(loading = true, ticket = null, message = null) }
        runCatching { client.requestAccess(computer) }
            .onSuccess { ticket -> _state.update { it.copy(loading = false, ticket = ticket, message = null) } }
            .onFailure { error -> _state.update { it.copy(loading = false, message = error.message ?: "Couldn't request access") } }
    }

    suspend fun connectIfApproved(ticket: CloudAccessTicket) {
        _state.update { it.copy(connecting = true, message = "Waiting for approval on ${computerName(ticket.computerId)}…") }
        try {
            while (System.currentTimeMillis() < ticket.expiresAt) {
                val status = client.accessStatus(ticket.requestId)
                when (status.status) {
                    "approved" -> {
                        val remote = _state.value.computers.firstOrNull { it.computerId == ticket.computerId }
                            ?: client.computers().firstOrNull { it.computerId == ticket.computerId }
                            ?: error("This computer is no longer in your account")
                        if (remote.access != "granted") refresh()
                        host.connect(remote.toHostComputer())
                        _state.update { it.copy(connecting = false, ticket = null, message = "Connecting to ${remote.name}…") }
                        return
                    }
                    "pending" -> delay(1_500)
                    else -> error("Access request ${status.status}")
                }
            }
            error("Access request expired")
        } catch (error: Throwable) {
            _state.update { it.copy(connecting = false, message = error.message ?: "Couldn't connect to this computer") }
        }
    }

    suspend fun cancelAccess(ticket: CloudAccessTicket) {
        runCatching { client.cancelAccess(ticket.requestId) }
        _state.update { it.copy(ticket = null, connecting = false, message = null) }
    }

    fun connect(computer: CloudComputer) {
        if (computer.access != "granted") {
            _state.update { it.copy(message = "Request access from this phone before connecting") }
            return
        }
        host.connect(computer.toHostComputer())
    }

    private fun computerName(computerId: String): String =
        _state.value.computers.firstOrNull { it.computerId == computerId }?.name ?: "computer"

    private fun CloudComputer.toHostComputer() = Computer(
        id = computerId,
        name = name,
        signKey = signKey,
        boxKey = boxKey,
        cloud = cloudUrl(),
        device = device,
    )

    private fun cloudUrl(): String = BuildConfig.CLOUD_URL
}
