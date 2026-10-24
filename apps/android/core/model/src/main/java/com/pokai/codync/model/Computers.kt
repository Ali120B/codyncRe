package com.pokai.codync.model

import kotlinx.serialization.Serializable

@Serializable
data class Hello(
    val hostId: String,
    val name: String,
    val version: String,
    val os: String,
    val device: String? = null,
    val home: String? = null,
    val backends: List<Backend> = emptyList(),
    val rev: Long = 0,
    val urls: List<String>? = null,
    val computerId: String? = null,
    val signKey: String? = null,
    val boxKey: String? = null,
    val minApp: String? = null,
    val cloud: String? = null,
    val analytics: Boolean? = null,
)

@Serializable
data class Backend(
    val id: String,
    val name: String,
    val available: Boolean = false,
    val command: String = "",
    val installHint: String = "",
    val registry: String? = null,
    val description: String? = null,
    val installed: Boolean? = null,
    val curated: Boolean? = null,
    val signedIn: Boolean? = null,
    val canInstall: Boolean? = null,
    val node: String? = null,
)

/** A computer this device talks to. Holds no secrets: only pinned public keys and addresses. */
@Serializable
data class Computer(
    val id: String,
    val name: String,
    val signKey: String,
    val boxKey: String? = null,
    val urls: List<String> = emptyList(),
    val cloud: String? = null,
    val device: String? = null,
    val color: String? = null,
)

@Serializable
data class PairingInfo(
    val pairingUrl: String,
    val urls: List<String> = emptyList(),
    val expiresAt: Long? = null,
)

@Serializable
data class AccessRequest(
    val requestId: String,
    val deviceKey: String,
    val deviceName: String,
    val platform: String? = null,
    val email: String? = null,
    val code: String? = null,
    val createdAt: Long? = null,
    val expiresAt: Long? = null,
)

@Serializable
data class AuthorizedDevice(
    val key: String,
    val name: String,
    val platform: String? = null,
    val source: String = "local",
    val scopes: List<String>? = null,
    val leaseUntil: Long? = null,
    val createdAt: Long? = null,
    val lastSeenAt: Long? = null,
    val connected: Boolean? = null,
)

@Serializable
data class CloudStatus(
    val enabled: Boolean = false,
    val url: String? = null,
    val registered: Boolean? = null,
    val connected: Boolean? = null,
    val owner: CloudOwner? = null,
    val lastError: String? = null,
    val approval: String? = null,
)

@Serializable
data class CloudOwner(
    val userId: String,
    val email: String? = null,
)

/** Parsed `codync://pair?...` offer from a QR / deep link. */
@Serializable
data class PairOffer(
    val v: Int = 1,
    val computerId: String,
    val signKey: String,
    val boxKey: String? = null,
    val name: String = "",
    val urls: List<String> = emptyList(),
    val code: String = "",
    val relay: String? = null,
)
