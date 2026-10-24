package com.pokai.codync.android.ui.workbench

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokai.codync.data.SessionStore
import com.pokai.codync.design.CodyncIcons
import com.pokai.codync.design.DestructiveButton
import com.pokai.codync.design.FilterChip
import com.pokai.codync.design.PrimaryButton
import com.pokai.codync.design.SecondaryButton
import com.pokai.codync.design.SectionCard
import com.pokai.codync.design.SettingsRow
import com.pokai.codync.design.CodyncSwitch
import com.pokai.codync.design.theme.CodyncTheme
import com.pokai.codync.design.theme.CodyncType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.text.DateFormat
import java.util.Date
import java.util.TimeZone

@Composable
internal fun BotMemoryTab(session: SessionStore, botId: String) {
    val colors = CodyncTheme.colors
    val scope = rememberCoroutineScope()
    var query by remember(botId) { mutableStateOf("") }
    var title by remember(botId) { mutableStateOf("") }
    var content by remember(botId) { mutableStateOf("") }
    var facts by remember(botId) { mutableStateOf<List<JsonObject>>(emptyList()) }
    var loading by remember(botId) { mutableStateOf(false) }
    var error by remember(botId) { mutableStateOf<String?>(null) }

    suspend fun load() {
        loading = true
        val result = session.call("memory", buildJsonObject {
            put("botId", botId)
            put("query", query)
            put("limit", 50)
            put("offset", 0)
        })
        result.onSuccess { response ->
            facts = response.jsonObject["facts"]?.jsonArray?.mapNotNull { it.objectOrNull() }.orEmpty()
            error = null
        }.onFailure { error = it.message ?: "Couldn't load this bot's memory" }
        loading = false
    }

    LaunchedEffect(botId, query) {
        delay(if (query.isBlank()) 0 else 250)
        load()
    }

    Column(Modifier.fillMaxSize()) {
        DetailSearchField(query, "Search this bot's memory") { query = it }
        SectionCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailField("Title", title) { title = it }
                DetailField("Memory", content, multiline = true) { content = it }
                PrimaryButton(
                    text = if (loading) "Saving…" else "Add memory",
                    onClick = {
                        scope.launch {
                            val result = session.call("saveMemory", buildJsonObject {
                                put("botId", botId)
                                put("title", title.trim().ifBlank { content.trim().take(100) })
                                put("content", content.trim())
                                put("scope", "project")
                            })
                            result.onSuccess {
                                title = ""
                                content = ""
                                load()
                            }.onFailure { error = it.message ?: "Couldn't save this memory" }
                        }
                    },
                    enabled = content.isNotBlank() && !loading,
                    leading = CodyncIcons.Add,
                )
            }
        }
        if (error != null) DetailError(error!!)
        if (facts.isEmpty() && !loading && error == null) {
            Text(
                if (query.isBlank()) "No saved memories for this bot yet." else "No memories match this search.",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                color = colors.tertiary,
                style = CodyncType.footnote,
            )
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(facts, key = { it.text("id") ?: it.hashCode().toString() }) { fact ->
                val id = fact.text("id").orEmpty()
                SectionCard {
                    Column(Modifier.padding(14.dp)) {
                        Row {
                            Text(
                                fact.text("title") ?: fact.text("kind")?.replaceFirstChar { it.uppercase() } ?: "Memory",
                                color = colors.text,
                                style = CodyncType.body,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                fact.text("scope") ?: "project",
                                color = colors.tertiary,
                                style = CodyncType.caption,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(fact.text("content").orEmpty(), color = colors.secondary, style = CodyncType.footnote)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SecondaryButton(
                                text = if (fact.flag("pinned")) "Unpin" else "Pin",
                                onClick = {
                                    scope.launch {
                                        session.call("pinMemory", buildJsonObject {
                                            put("botId", botId)
                                            put("id", id)
                                            put("pinned", !fact.flag("pinned"))
                                        }).onSuccess { load() }.onFailure {
                                            error = it.message ?: "Couldn't update the memory"
                                        }
                                    }
                                },
                            )
                            DestructiveButton("Delete", onClick = {
                                scope.launch {
                                    session.call("forgetMemory", buildJsonObject {
                                        put("botId", botId)
                                        put("id", id)
                                    }).onSuccess { load() }.onFailure {
                                        error = it.message ?: "Couldn't delete the memory"
                                    }
                                }
                            })
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun BotRoutinesTab(session: SessionStore, botId: String) {
    val colors = CodyncTheme.colors
    val scope = rememberCoroutineScope()
    var routines by remember(botId) { mutableStateOf<List<JsonObject>>(emptyList()) }
    var runs by remember(botId) { mutableStateOf<List<JsonObject>>(emptyList()) }
    var editing by remember(botId) { mutableStateOf<RoutineDraft?>(null) }
    var loading by remember(botId) { mutableStateOf(false) }
    var error by remember(botId) { mutableStateOf<String?>(null) }

    suspend fun load() {
        loading = true
        session.call("routines", buildJsonObject { put("botId", botId) })
            .onSuccess { response ->
                val obj = response.jsonObject
                routines = obj["routines"]?.jsonArray?.mapNotNull { it.objectOrNull() }.orEmpty()
                runs = obj["runs"]?.jsonArray?.mapNotNull { it.objectOrNull() }.orEmpty()
                error = null
            }.onFailure { error = it.message ?: "Couldn't load routines" }
        loading = false
    }

    LaunchedEffect(botId) { load() }

    if (editing != null) {
        var draft by remember(editing?.id) { mutableStateOf(editing!!) }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            SectionCard {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(if (draft.id == null) "New routine" else "Edit routine", color = colors.text, style = CodyncType.title)
                    DetailField("Name", draft.name) { draft = draft.copy(name = it) }
                    DetailField("Prompt", draft.instruction, multiline = true) { draft = draft.copy(instruction = it) }
                    Text("Schedule", color = colors.tertiary, style = CodyncType.caption)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Keep", "Daily", "Weekdays", "Hourly", "Interval", "Webhook").forEach { kind ->
                            FilterChip(kind, draft.scheduleKind == kind, onClick = { draft = draft.copy(scheduleKind = kind) })
                        }
                    }
                    when (draft.scheduleKind) {
                        "Daily", "Weekdays" -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DetailField("Hour (0–23)", draft.hour, modifier = Modifier.weight(1f)) {
                                draft = draft.copy(hour = it.filter(Char::isDigit).take(2))
                            }
                            DetailField("Minute (0–59)", draft.minute, modifier = Modifier.weight(1f)) {
                                draft = draft.copy(minute = it.filter(Char::isDigit).take(2))
                            }
                        }
                        "Interval" -> DetailField("Every (seconds)", draft.intervalSeconds) {
                            draft = draft.copy(intervalSeconds = it.filter(Char::isDigit).take(8))
                        }
                        "Keep" -> Text(
                            draft.summary.ifBlank { "Select a schedule for this routine." },
                            color = colors.secondary,
                            style = CodyncType.footnote,
                        )
                        "Webhook" -> Text("Runs when its webhook receives an event.", color = colors.secondary, style = CodyncType.footnote)
                    }
                    Text("Time zone: ${draft.zone}", color = colors.secondary, style = CodyncType.footnote)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Enabled", modifier = Modifier.weight(1f), color = colors.secondary, style = CodyncType.footnote)
                        CodyncSwitch(
                            checked = draft.enabled,
                            onCheckedChange = { draft = draft.copy(enabled = it) },
                        )
                    }
                }
            }
            if (error != null) DetailError(error!!)
            Spacer(Modifier.height(10.dp))
            PrimaryButton(
                "Save routine",
                onClick = {
                    scope.launch {
                        val args = buildJsonObject {
                            put("botId", botId)
                            draft.id?.let { put("id", it) }
                            put("name", draft.name.trim())
                            put("instruction", draft.instruction.trim())
                            put("enabled", draft.enabled)
                            put("timeoutSeconds", draft.timeoutSeconds)
                            put("triggers", draft.triggerArray())
                        }
                        session.call("saveRoutine", args)
                            .onSuccess { editing = null; load() }
                            .onFailure { error = it.message ?: "Couldn't save the routine" }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = draft.name.isNotBlank() && draft.instruction.isNotBlank() && !loading,
            )
            Spacer(Modifier.height(8.dp))
            SecondaryButton("Cancel", onClick = { editing = null }, modifier = Modifier.fillMaxWidth())
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp)) {
            Text("${routines.count { it.flag("enabled") }} active", color = colors.secondary, style = CodyncType.footnote)
            Spacer(Modifier.weight(1f))
            SecondaryButton(
                "New routine",
                onClick = {
                    editing = RoutineDraft(
                        id = null,
                        name = "New routine",
                        instruction = "",
                        enabled = true,
                        scheduleKind = "Daily",
                        zone = TimeZone.getDefault().id,
                    )
                },
                leading = CodyncIcons.Add,
            )
        }
        if (error != null) DetailError(error!!)
        if (routines.isEmpty() && !loading) {
            Text("No routines for this bot yet.", modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp), color = colors.tertiary, style = CodyncType.footnote)
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(routines, key = { it.text("id") ?: it.hashCode().toString() }) { routine ->
                val id = routine.text("id").orEmpty()
                val summary = routine["triggerDescriptions"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.contentOrNull
                    ?: routine["triggers"]?.jsonArray?.firstOrNull()?.toString().orEmpty()
                SectionCard {
                    Column {
                        SettingsRow(
                            title = routine.text("name") ?: "Routine",
                            subtitle = listOfNotNull(summary, routine.text("lastError")).joinToString(" · "),
                            icon = CodyncIcons.Schedule,
                            trailing = {
                                CodyncSwitch(routine.flag("enabled"), onCheckedChange = { enabled ->
                                    scope.launch {
                                        session.call("setRoutineEnabled", buildJsonObject {
                                            put("botId", botId)
                                            put("id", id)
                                            put("enabled", enabled)
                                        }).onSuccess { load() }.onFailure {
                                            error = it.message ?: "Couldn't update the routine"
                                        }
                                    }
                                })
                            },
                            onClick = { editing = RoutineDraft.from(routine, summary) },
                        )
                        Row(Modifier.padding(start = 54.dp, end = 12.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SecondaryButton("Edit", onClick = { editing = RoutineDraft.from(routine, summary) })
                            SecondaryButton("Run now", leading = CodyncIcons.Play, onClick = {
                                scope.launch {
                                    session.call("runRoutine", buildJsonObject {
                                        put("botId", botId)
                                        put("id", id)
                                    }).onSuccess { load() }.onFailure {
                                        error = it.message ?: "Couldn't start the routine"
                                    }
                                }
                            })
                            DestructiveButton("Delete", onClick = {
                                scope.launch {
                                    session.call("deleteRoutine", buildJsonObject {
                                        put("botId", botId)
                                        put("id", id)
                                    }).onSuccess { load() }.onFailure {
                                        error = it.message ?: "Couldn't delete the routine"
                                    }
                                }
                            })
                        }
                    }
                }
            }
            if (runs.isNotEmpty()) {
                item { Text("Recent runs", modifier = Modifier.padding(top = 10.dp), color = colors.tertiary, style = CodyncType.caption) }
                items(runs.take(12), key = { it.text("id") ?: it.hashCode().toString() }) { run ->
                    SectionCard {
                        SettingsRow(
                            title = run.text("status")?.replaceFirstChar { it.uppercase() } ?: "Run",
                            subtitle = listOfNotNull(
                                run.long("createdAt")?.let(::formatDate),
                                run.text("detail"),
                            ).joinToString(" · "),
                            icon = if (run.text("status") == "succeeded") CodyncIcons.CheckCircle else CodyncIcons.Bolt,
                            showChevron = false,
                        )
                    }
                }
            }
        }
    }
}

private data class RoutineDraft(
    val id: String?,
    val name: String,
    val instruction: String,
    val enabled: Boolean,
    val scheduleKind: String,
    val zone: String,
    val hour: String = "9",
    val minute: String = "0",
    val intervalSeconds: String = "3600",
    val timeoutSeconds: Int = 3600,
    val originalTriggers: JsonArray = JsonArray(emptyList()),
    val summary: String = "",
) {
    fun triggerArray(): JsonArray = when (scheduleKind) {
        "Keep" -> originalTriggers
        "Webhook" -> buildJsonArray { add(buildJsonObject { put("type", "webhook") }) }
        "Interval" -> buildJsonArray {
            add(buildJsonObject { put("type", "interval"); put("seconds", intervalSeconds.toLongOrNull() ?: 0L) })
        }
        "Weekdays", "Daily" -> buildJsonArray {
            val hourValue = hour.toIntOrNull() ?: 9
            val minuteValue = minute.toIntOrNull() ?: 0
            add(buildJsonObject {
                put("type", "cron")
                put("expression", "$minuteValue $hourValue * * ${if (scheduleKind == "Weekdays") "1-5" else "*"}")
                put("timeZone", zone)
            })
        }
        "Hourly" -> buildJsonArray {
            val minuteValue = minute.toIntOrNull() ?: 0
            add(buildJsonObject { put("type", "cron"); put("expression", "$minuteValue * * * *"); put("timeZone", zone) })
        }
        else -> originalTriggers
    }

    companion object {
        fun from(routine: JsonObject, summary: String): RoutineDraft {
            val triggers = routine["triggers"]?.jsonArray ?: JsonArray(emptyList())
            val first = triggers.firstOrNull()?.objectOrNull()
            val type = first?.text("type")
            val schedule = when (type) {
                "webhook" -> "Webhook"
                "interval" -> "Interval"
                "cron" -> {
                    val expression = first?.text("expression").orEmpty().split(Regex("\\s+"))
                    when {
                        expression.size == 5 && expression[2] == "*" && expression[3] == "*" && expression[4] == "1-5" -> "Weekdays"
                        expression.size == 5 && expression[1] == "*" -> "Hourly"
                        expression.size == 5 && expression.drop(2) == listOf("*", "*", "*") -> "Daily"
                        else -> "Keep"
                    }
                }
                else -> "Keep"
            }
            val cron = first?.text("expression")?.split(Regex("\\s+"))
            return RoutineDraft(
                id = routine.text("id"),
                name = routine.text("name").orEmpty(),
                instruction = routine.text("instruction").orEmpty(),
                enabled = routine.flag("enabled"),
                scheduleKind = schedule,
                zone = first?.text("timeZone") ?: TimeZone.getDefault().id,
                hour = cron?.getOrNull(1)?.takeIf { it != "*" } ?: "9",
                minute = cron?.getOrNull(0)?.takeIf { it != "*" } ?: "0",
                intervalSeconds = first?.long("seconds")?.toString() ?: "3600",
                timeoutSeconds = routine.long("timeoutSeconds")?.toInt() ?: 3600,
                originalTriggers = triggers,
                summary = summary,
            )
        }
    }
}

@Composable
private fun DetailSearchField(value: String, placeholder: String, onValueChange: (String) -> Unit) {
    val colors = CodyncTheme.colors
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = colors.text, fontSize = 15.sp, fontFamily = CodyncType.body.fontFamily),
        cursorBrush = SolidColor(colors.text),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(16.dp)).background(colors.surface).padding(horizontal = 14.dp, vertical = 12.dp),
        decorationBox = { inner ->
            androidx.compose.foundation.layout.Box {
                if (value.isEmpty()) Text(placeholder, color = colors.tertiary, style = CodyncType.body)
                inner()
            }
        },
    )
}

@Composable
private fun DetailField(
    label: String,
    value: String,
    multiline: Boolean = false,
    modifier: Modifier = Modifier,
    onChange: (String) -> Unit,
) {
    val colors = CodyncTheme.colors
    Column(modifier) {
        Text(label, color = colors.tertiary, style = CodyncType.caption)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = !multiline,
            minLines = if (multiline) 3 else 1,
            maxLines = if (multiline) 7 else 1,
            textStyle = TextStyle(color = colors.text, fontSize = 15.sp, fontFamily = CodyncType.body.fontFamily),
            cursorBrush = SolidColor(colors.text),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.surface).padding(12.dp),
        )
    }
}

@Composable
private fun DetailError(message: String) {
    Text(message, modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp), color = CodyncTheme.colors.warning, style = CodyncType.footnote)
}

private fun JsonElement.objectOrNull(): JsonObject? = runCatching { jsonObject }.getOrNull()
private fun JsonObject.text(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
private fun JsonObject.long(key: String): Long? = this[key]?.jsonPrimitive?.longOrNull
private fun JsonObject.flag(key: String): Boolean = this[key]?.jsonPrimitive?.booleanOrNull == true
private fun formatDate(timestamp: Long): String = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(timestamp))
