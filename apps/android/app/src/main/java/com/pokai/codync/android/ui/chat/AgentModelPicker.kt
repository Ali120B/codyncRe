package com.pokai.codync.android.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import com.pokai.codync.data.SessionStore
import com.pokai.codync.design.CodyncIcon
import com.pokai.codync.design.CodyncIcons
import com.pokai.codync.design.FilterChip
import com.pokai.codync.design.GlassIconButton
import com.pokai.codync.design.SectionCard
import com.pokai.codync.design.SecondaryButton
import com.pokai.codync.design.SettingsRow
import com.pokai.codync.design.theme.CodyncTheme
import com.pokai.codync.design.theme.CodyncType
import com.pokai.codync.model.Bot
import com.pokai.codync.android.ui.SearchField
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

@Composable
internal fun AgentModelChip(session: SessionStore, bot: Bot) {
    val connection by session.connectionStatus().collectAsState(initial = com.pokai.codync.transport.ConnectionState())
    val backends = connection.hello?.backends.orEmpty()
    val backendName = backends.firstOrNull { it.id == bot.backend }?.name ?: bot.backend
    var open by remember(bot.id) { mutableStateOf(false) }
    Box(
        Modifier.size(32.dp).clip(CircleShape).background(CodyncTheme.colors.bubbleAgent)
            .clickable { open = true },
        contentAlignment = Alignment.Center,
    ) {
        CodyncIcon(CodyncIcons.Tune, tint = CodyncTheme.colors.text, size = 16.dp)
    }
    if (open) AgentModelPickerDialog(session, bot, backends, onDismiss = { open = false })
}

private data class ModelChoice(val id: String?, val name: String, val description: String?)

@Composable
private fun AgentModelPickerDialog(
    session: SessionStore,
    bot: Bot,
    backends: List<com.pokai.codync.model.Backend>,
    onDismiss: () -> Unit,
) {
    val colors = CodyncTheme.colors
    val scope = rememberCoroutineScope()
    val choices = remember(backends, bot.backend) {
        backends.filter { it.available || it.id == bot.backend }
            .ifEmpty { backends.filter { it.id == bot.backend } }
    }
    var backend by remember(bot.id) { mutableStateOf(bot.backend) }
    var catalog by remember(bot.id) { mutableStateOf<JsonObject?>(null) }
    var loading by remember(bot.id) { mutableStateOf(false) }
    var error by remember(bot.id) { mutableStateOf<String?>(null) }
    var query by remember(bot.id) { mutableStateOf("") }
    var saving by remember(bot.id) { mutableStateOf(false) }
    var retry by remember(bot.id) { mutableStateOf(0) }

    LaunchedEffect(backend, retry) {
        if (backend == "custom") {
            catalog = null
            error = "Custom commands do not advertise a model list. Edit the model in bot settings."
            return@LaunchedEffect
        }
        loading = true
        error = null
        session.call("agentModels", buildJsonObject { put("backend", backend) })
            .onSuccess { result ->
                catalog = runCatching { result.jsonObject }.getOrNull()
                if (catalog == null) error = "The host returned an invalid model list."
            }
            .onFailure { error = it.message ?: "Couldn't load models from this computer." }
        loading = false
    }

    val rawModels = catalog?.array("models").orEmpty().mapNotNull(JsonElement::objectOrNull)
    val defaultModelId = rawModels.firstOrNull { it.text("name").orEmpty().startsWith("default", ignoreCase = true) }?.text("id")
    val modelChoices = buildList {
        add(ModelChoice(null, "Default", rawModels.firstOrNull { it.text("id") == defaultModelId }?.text("name") ?: "Use the agent's own model"))
        rawModels.filterNot { it.text("id") == defaultModelId }.forEach { model ->
            val id = model.text("id") ?: return@forEach
            add(ModelChoice(id, model.text("name") ?: id, model.text("description")))
        }
    }
    val filtered = modelChoices.filter { item ->
        val q = query.trim()
        q.isBlank() || item.name.contains(q, true) || item.id?.contains(q, true) == true || item.description?.contains(q, true) == true
    }
    val currentModel = when {
        backend != bot.backend -> null
        bot.model == defaultModelId -> null
        else -> bot.model
    }
    val currentOptions = if (backend == bot.backend) bot.modelOptions else emptyMap()
    val selectedModel = rawModels.firstOrNull { it.text("id") == currentModel }
    val options = if (backend == bot.backend) buildList {
        addAll(catalog?.array("options").orEmpty().mapNotNull(JsonElement::objectOrNull))
        addAll(selectedModel?.array("options").orEmpty().mapNotNull(JsonElement::objectOrNull))
    } else emptyList()

    suspend fun saveModel(modelId: String?) {
        saving = true
        val changedBackend = backend != bot.backend
        val result = session.call("updateBot", buildJsonObject {
            put("id", bot.id)
            put("backend", backend)
            put("model", modelId?.let(::JsonPrimitive) ?: JsonNull)
            put("modelOptions", buildJsonObject {
                if (!changedBackend && modelId == bot.model) {
                    bot.modelOptions.forEach { (key, value) -> put(key, value) }
                }
            })
        })
        saving = false
        result.onSuccess { onDismiss() }.onFailure { error = it.message ?: "Couldn't update this bot's model." }
    }

    suspend fun saveOption(optionId: String, value: String) {
        saving = true
        val next = bot.modelOptions.toMutableMap()
        if (value.isBlank()) next.remove(optionId) else next[optionId] = value
        val result = session.call("updateBot", buildJsonObject {
            put("id", bot.id)
            put("backend", bot.backend)
            put("model", bot.model?.let(::JsonPrimitive) ?: JsonNull)
            put("modelOptions", buildJsonObject { next.forEach { (key, selected) -> put(key, selected) } })
        })
        saving = false
        result.onSuccess { onDismiss() }.onFailure { error = it.message ?: "Couldn't update this model option." }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.82f)
                .clip(RoundedCornerShape(26.dp)).background(colors.surface).padding(vertical = 10.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Choose model", color = colors.text, style = CodyncType.headline)
                    Text("Only agents available on this computer are listed.", color = colors.tertiary, style = CodyncType.caption)
                }
                GlassIconButton(CodyncIcons.Close, "Close model picker", onClick = onDismiss)
            }
            if (choices.isNotEmpty()) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    choices.forEach { item ->
                        FilterChip(item.name, backend == item.id, onClick = {
                            backend = item.id
                            query = ""
                        })
                    }
                }
            }
            SearchField(query, { query = it }, placeholder = "Search models", modifier = Modifier.padding(horizontal = 14.dp))
            if (loading) Text("Loading models from ${choices.firstOrNull { it.id == backend }?.name ?: backend}…", modifier = Modifier.padding(18.dp), color = colors.secondary, style = CodyncType.footnote)
            error?.let { message ->
                Text(message, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp), color = colors.warning, style = CodyncType.footnote)
                if (!loading && backend != "custom") SecondaryButton("Retry", onClick = { retry += 1 }, modifier = Modifier.padding(horizontal = 16.dp))
            }
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                contentPadding = PaddingValues(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                items(filtered, key = { it.id ?: "default" }) { item ->
                    SectionCard {
                        SettingsRow(
                            title = item.name,
                            subtitle = item.description ?: item.id ?: "Agent's default model",
                            icon = if (item.id == currentModel && backend == bot.backend) CodyncIcons.Done else null,
                            showChevron = false,
                            onClick = { if (!saving) scope.launch { saveModel(item.id) } },
                        )
                    }
                }
                if (filtered.isEmpty() && !loading && error == null) {
                    item { Text("No models match that search.", modifier = Modifier.padding(14.dp), color = colors.tertiary, style = CodyncType.footnote) }
                }
                options.forEach optionLoop@{ option ->
                    val id = option.text("id") ?: return@optionLoop
                    val values = option.array("values").orEmpty().mapNotNull(JsonElement::objectOrNull)
                    item(key = "option:$id") {
                        SectionCard {
                            Column(Modifier.padding(12.dp)) {
                                Text(option.text("name") ?: id, color = colors.secondary, style = CodyncType.caption)
                                Spacer(Modifier.height(6.dp))
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    val fallback = option.text("default").orEmpty()
                                    FilterChip("Default", currentOptions[id].orEmpty().isBlank(), onClick = {
                                        if (!saving) scope.launch { saveOption(id, "") }
                                    })
                                    values.forEach valueLoop@{ value ->
                                        val valueId = value.text("id") ?: return@valueLoop
                                        FilterChip(value.text("name") ?: valueId, currentOptions[id] == valueId || (currentOptions[id].isNullOrBlank() && valueId == fallback), onClick = {
                                            if (!saving) scope.launch { saveOption(id, valueId) }
                                        })
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (saving) Text("Saving…", modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp), color = colors.tertiary, style = CodyncType.caption)
        }
    }
}

private fun JsonObject.text(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
private fun JsonObject.array(key: String): JsonArray? = this[key]?.let { runCatching { it.jsonArray }.getOrNull() }
private fun JsonElement.objectOrNull(): JsonObject? = runCatching { jsonObject }.getOrNull()
