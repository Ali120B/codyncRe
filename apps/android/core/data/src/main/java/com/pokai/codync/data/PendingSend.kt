package com.pokai.codync.data

/** Plain text waiting for its paired host. Attachments are uploaded only while connected. */
data class PendingSend(
    val computerId: String,
    val botId: String,
    val chatId: String,
    val text: String,
    val clientNonce: String,
    val createdAt: Long,
)

interface PendingSendOutbox {
    suspend fun enqueue(send: PendingSend)
    suspend fun pending(computerId: String): List<PendingSend>
    suspend fun remove(clientNonce: String)
}
