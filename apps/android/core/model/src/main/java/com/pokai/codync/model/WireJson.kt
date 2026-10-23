package com.pokai.codync.model

import kotlinx.serialization.json.Json

/**
 * Wire JSON matches the Rust host and both existing clients: camelCase fields, epoch-ms
 * timestamps, and unknown fields ignored so a newer host cannot break an older phone.
 */
object WireJson {
    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        coerceInputValues = true
        isLenient = false
    }
}

/** Versions that must agree across host, desktop, iPhone and Android. */
object Compatibility {
    const val APP_VERSION = "3.0.0"
    const val PROTOCOL_VERSION = 1
    const val MIN_HOST_VERSION = "3.0.0"
}
