package com.pokai.codync.android

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Decrypts host-sealed alert contents locally; FCM carries only generic fallback text. */
class CodyncMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        AndroidPushManager.saveUpdatedToken(this, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        if (getSharedPreferences("codync_prefs", MODE_PRIVATE).getBoolean("push", true).not()) return
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return

        val computerId = data["computerId"]
        val botId = data["botId"]
        val appPrefs = getSharedPreferences(AppPresence.PREFS, MODE_PRIVATE)
        // Suppress only while the app is actually on screen and the user can already see it:
        // Home (all bots) or this exact chat. Background chats and every other page still alert.
        if (appPrefs.getBoolean(AppPresence.KEY_FOREGROUND, false)) {
            val surface = appPrefs.getString(AppPresence.KEY_SURFACE, AppPresence.OTHER)
            if (surface == AppPresence.HOME || surface == AppPresence.chat(computerId, botId)) return
        }
        val alert = if (!computerId.isNullOrBlank() && !data["sealed"].isNullOrBlank()) {
            runCatching {
                val plain = DeviceIdentity(applicationContext).openPush(data.getValue("sealed"), computerId)
                Json.parseToJsonElement(plain.toString(Charsets.UTF_8)).jsonObject
            }.getOrNull()
        } else null
        val title = alert?.get("title")?.jsonPrimitive?.content ?: data["fallbackTitle"] ?: "Codync"
        val body = alert?.get("body")?.jsonPrimitive?.content ?: data["fallbackBody"] ?: "Open Codync to review activity."
        val deepLink = if (!computerId.isNullOrBlank() && !botId.isNullOrBlank()) {
            Uri.parse("codync://bot/${Uri.encode(computerId)}/${Uri.encode(botId)}")
        } else null

        ensureChannel()
        val openApp = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            setData(deepLink)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            ("$computerId:$botId").hashCode(),
            openApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(this, ALERT_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_codync)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
        NotificationIcon.render(alert)?.let(builder::setLargeIcon)
        val notification = builder.build()
        runCatching {
            NotificationManagerCompat.from(this).notify(("$computerId:$botId").hashCode(), notification)
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(ALERT_CHANNEL) != null) return
        manager.createNotificationChannel(
            NotificationChannel(ALERT_CHANNEL, getString(R.string.push_channel_name), NotificationManager.IMPORTANCE_HIGH).apply {
                description = getString(R.string.push_channel_description)
            },
        )
    }

    companion object {
        private const val ALERT_CHANNEL = "codync_alerts"
    }
}
