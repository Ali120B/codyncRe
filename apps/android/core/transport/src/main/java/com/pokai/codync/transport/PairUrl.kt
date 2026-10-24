package com.pokai.codync.transport

import com.pokai.codync.model.PairOffer
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Parses `codync://pair?...` QR / paste / deep-link payloads.
 */
object PairUrl {
    fun parse(raw: String): PairOffer? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        return runCatching {
            when {
                trimmed.startsWith("{") -> parseJsonish(trimmed)
                "codync://pair" in trimmed || trimmed.startsWith("http") -> parseQuery(extractQuery(trimmed))
                '=' in trimmed -> parseQuery(trimmed.trimStart('?'))
                else -> null
            }
        }.getOrNull()
    }

    private fun extractQuery(raw: String): String {
        val q = raw.indexOf('?')
        return if (q >= 0) raw.substring(q + 1) else raw.substringAfter("pair").trimStart('/', '?')
    }

    private fun parseQuery(query: String): PairOffer? {
        val map = linkedMapOf<String, String>()
        query.split('&').forEach { part ->
            val i = part.indexOf('=')
            if (i <= 0) return@forEach
            val k = urlDecode(part.substring(0, i))
            val v = urlDecode(part.substring(i + 1))
            map[k] = v
        }
        // Desktop/iOS QR uses id/sk/bk/cloud; keep the longer aliases accepted by dev builds too.
        val computerId = map["id"] ?: map["computerId"] ?: map["cid"] ?: return null
        val signKey = map["signKey"] ?: map["sk"] ?: return null
        val urls = (map["urls"] ?: map["u"]).orEmpty()
            .split(',', ' ')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return PairOffer(
            v = map["v"]?.toIntOrNull() ?: 3,
            computerId = computerId,
            signKey = signKey,
            boxKey = map["boxKey"] ?: map["bk"],
            name = map["name"] ?: map["n"].orEmpty(),
            urls = urls,
            code = map["code"] ?: map["c"].orEmpty(),
            relay = map["cloud"] ?: map["relay"] ?: map["r"],
        )
    }

    private fun parseJsonish(raw: String): PairOffer? {
        fun field(name: String): String? =
            Regex("\"$name\"\\s*:\\s*\"([^\"]*)\"").find(raw)?.groupValues?.get(1)

        fun fieldArr(name: String): List<String> {
            val body = Regex("\"$name\"\\s*:\\s*\\[(.*?)]").find(raw)?.groupValues?.get(1) ?: return emptyList()
            return Regex("\"([^\"]+)\"").findAll(body).map { it.groupValues[1] }.toList()
        }
        val computerId = field("id") ?: field("computerId") ?: return null
        val signKey = field("signKey") ?: field("sk") ?: return null
        return PairOffer(
            v = field("v")?.toIntOrNull() ?: 3,
            computerId = computerId,
            signKey = signKey,
            boxKey = field("boxKey") ?: field("bk"),
            name = field("name").orEmpty(),
            urls = fieldArr("urls"),
            code = field("code").orEmpty(),
            relay = field("cloud") ?: field("relay"),
        )
    }

    private fun urlDecode(s: String): String =
        URLDecoder.decode(s.replace('+', ' '), StandardCharsets.UTF_8)
}
