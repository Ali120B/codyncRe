package com.pokai.codync.android

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.pokai.codync.design.characterAvatarBitmap
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Draws the notification's large icon from the sealed `faces` the host includes with every
 * alert. This is the real bot character (or the two leading members of a room), not a generic
 * app glyph, and it works even when the app process has no roster loaded.
 */
object NotificationIcon {
    fun render(alert: JsonObject?): Bitmap? {
        val faces = alert?.get("faces")?.let { runCatching { it.jsonArray }.getOrNull() } ?: return null
        val parsed = faces.mapNotNull { face ->
            runCatching { face.jsonObject }.getOrNull()?.let { obj ->
                Face(
                    kind = obj.text("kind") ?: "agent",
                    color = obj.text("avatarColor") ?: "blue",
                    shape = obj.text("avatarShape") ?: "blob",
                    members = obj["members"]?.let { runCatching { it.jsonArray }.getOrNull() }.orEmpty(),
                )
            }
        }
        val first = parsed.firstOrNull() ?: return null
        val size = 128
        if (first.kind == "group" && first.members.isNotEmpty()) {
            val members = parsed.drop(1).take(2).ifEmpty { parsed.take(2) }
            if (members.size >= 2) {
                val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawCircle(
                    size / 2f,
                    size / 2f,
                    size / 2f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF7F7F7.toInt() },
                )
                val faceSize = 78
                val left = characterAvatarBitmap(members[0].color, members[0].shape, faceSize, background = false)
                val right = characterAvatarBitmap(members[1].color, members[1].shape, faceSize, background = false)
                canvas.drawBitmap(left, 8f, 25f, null)
                canvas.drawBitmap(right, size - faceSize - 8f, 25f, null)
                return bitmap
            }
        }
        return characterAvatarBitmap(first.color, first.shape, size)
    }

    private data class Face(
        val kind: String,
        val color: String,
        val shape: String,
        val members: List<JsonElement>,
    )

    private fun JsonObject.text(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
}
