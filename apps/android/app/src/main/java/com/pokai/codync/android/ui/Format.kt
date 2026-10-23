package com.pokai.codync.android.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val timeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
private val monthDay = DateTimeFormatter.ofPattern("MMM d", Locale.US)
private val weekday = DateTimeFormatter.ofPattern("EEEE", Locale.US)

fun relativeShort(millis: Long, now: Long = System.currentTimeMillis()): String {
    val seconds = ((now - millis).coerceAtLeast(0)) / 1000
    return when {
        seconds < 60 -> "now"
        seconds < 3600 -> "${seconds / 60}m"
        seconds < 86_400 -> "${seconds / 3600}h"
        seconds < 86_400 * 7 -> "${seconds / 86_400}d"
        else -> Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(monthDay)
    }
}

fun rosterStamp(millis: Long, now: Long = System.currentTimeMillis()): String {
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(millis).atZone(zone)
    val today = LocalDate.now(zone)
    return when {
        date.toLocalDate() == today -> date.format(timeFormat)
        date.toLocalDate() == today.minusDays(1) -> "Yesterday"
        today.toEpochDay() - date.toLocalDate().toEpochDay() < 6 -> date.format(weekday)
        else -> date.format(monthDay)
    }
}

fun chatSeparator(millis: Long, now: Long = System.currentTimeMillis()): String {
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(millis).atZone(zone)
    val today = LocalDate.now(zone)
    val day = when (date.toLocalDate()) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(monthDay)
    }
    return "$day ${date.format(timeFormat)}"
}
