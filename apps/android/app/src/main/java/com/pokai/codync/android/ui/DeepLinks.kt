package com.pokai.codync.android.ui

import android.net.Uri

/** Notification and deep-link targets that open a bot conversation directly. */
data class BotLink(
    val computerId: String,
    val botId: String,
    val chatId: String? = null,
    val messageId: String? = null,
) {
    companion object {
        private val schemes = setOf("codync", "codync-dev")

        fun parse(raw: String?): BotLink? {
            if (raw.isNullOrBlank()) return null
            val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return null
            if (uri.scheme !in schemes || uri.host != "bot") return null
            val segments = uri.pathSegments.filter { it.isNotBlank() }
            val computerId = segments.getOrNull(0) ?: return null
            val botId = segments.getOrNull(1) ?: return null
            return BotLink(
                computerId = computerId,
                botId = botId,
                chatId = uri.getQueryParameter("chatId")?.takeIf { it.isNotBlank() },
                messageId = uri.getQueryParameter("messageId")?.takeIf { it.isNotBlank() },
            )
        }
    }
}
