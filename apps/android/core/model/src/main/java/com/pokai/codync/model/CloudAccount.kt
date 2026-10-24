package com.pokai.codync.model

import kotlinx.serialization.Serializable

@Serializable
data class CloudComputer(
    val computerId: String,
    val name: String,
    val signKey: String,
    val boxKey: String? = null,
    val platform: String? = null,
    val device: String? = null,
    val version: String? = null,
    val online: Boolean = false,
    val lastSeenAt: Long? = null,
    val claimedAt: Long? = null,
    val access: String? = null,
)

@Serializable
data class CloudAccessStatus(
    val requestId: String,
    val computerId: String? = null,
    val status: String,
    val expiresAt: Long? = null,
    val hostNonce: String? = null,
    val grantId: String? = null,
)

@Serializable
data class CloudAccessTicket(
    val requestId: String,
    val expiresAt: Long,
    val code: String,
    val computerId: String,
    val signKey: String,
)
