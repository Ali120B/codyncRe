package com.pokai.codync.android.ui.workbench

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokai.codync.data.SessionStore
import com.pokai.codync.design.CodyncIcons
import com.pokai.codync.design.CodyncIcon
import com.pokai.codync.design.CharacterAvatar
import com.pokai.codync.design.theme.AvatarPalette
import com.pokai.codync.design.CodyncSwitch
import com.pokai.codync.design.CodyncMenuAnchor
import com.pokai.codync.design.CodyncMenuItem
import com.pokai.codync.design.DestructiveButton
import com.pokai.codync.design.FilterChip
import com.pokai.codync.design.PrimaryButton
import com.pokai.codync.design.SecondaryButton
import com.pokai.codync.design.SectionCard
import com.pokai.codync.design.SettingsRow
import com.pokai.codync.design.theme.CodyncTheme
import com.pokai.codync.design.theme.CodyncType
import com.pokai.codync.model.Bot
import com.pokai.codync.model.MemberAccess
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Live bot/group editor. Every field is sent through `updateBot`; the host still validates
 * names, members, workspace paths and models. Group membership keeps the 2–50 rule.
 */
@Composable
fun LiveBotEditor(
    session: SessionStore,
    bot: Bot,
    initialSection: String = "Overview",
    onCancel: () -> Unit,
    onSaved: () -> Unit,
    onDeleted: () -> Unit,
) {
    val colors = CodyncTheme.colors
    val scope = rememberCoroutineScope()
    val creatingBot = bot.id.isBlank()
    val allBots by session.bots().collectAsState(initial = emptyList())
    val connection by session.connectionStatus().collectAsState(initial = com.pokai.codync.transport.ConnectionState())
    val backends = connection.hello?.backends.orEmpty()
    val isGroup = bot.kind == "group"
    var name by remember(bot.id) { mutableStateOf(if (creatingBot) "" else bot.name) }
    var description by remember(bot.id) { mutableStateOf(bot.description) }
    var instructions by remember(bot.id) { mutableStateOf(bot.instructions) }
    var backend by remember(bot.id) { mutableStateOf(if (creatingBot) "" else bot.backend) }
    var command by remember(bot.id) { mutableStateOf(bot.command.orEmpty()) }
    var workspace by remember(bot.id) { mutableStateOf(if (isGroup) bot.workspace else bot.cwd) }
    var model by remember(bot.id) { mutableStateOf(bot.model.orEmpty()) }
    var modelOptions by remember(bot.id) { mutableStateOf(bot.modelOptions) }
    var modelCatalog by remember(bot.id) { mutableStateOf<JsonObject?>(null) }
    var modelLoading by remember(bot.id) { mutableStateOf(false) }
    var modelError by remember(bot.id) { mutableStateOf<String?>(null) }
    var modelMenuOpen by remember(bot.id) { mutableStateOf(false) }
    var fastMode by remember(bot.id) { mutableStateOf(bot.fastMode) }
    var permission by remember(bot.id) { mutableStateOf(bot.permission) }
    var avatarColor by remember(bot.id) { mutableStateOf(bot.avatarColor) }
    var avatarShape by remember(bot.id) { mutableStateOf(bot.avatarShape) }
    var pinned by remember(bot.id) { mutableStateOf(bot.pinned) }
    var hidden by remember(bot.id) { mutableStateOf(bot.hidden) }
    var notify by remember(bot.id) { mutableStateOf(bot.notify ?: true) }
    var computer by remember(bot.id) { mutableStateOf(bot.computer) }
    var browser by remember(bot.id) { mutableStateOf(bot.browser) }
    var browserEngine by remember(bot.id) { mutableStateOf(bot.browserEngine) }
    var leader by remember(bot.id) { mutableStateOf(bot.leader) }
    var members by remember(bot.id) { mutableStateOf(bot.members.toSet()) }
    var memberSettings by remember(bot.id) { mutableStateOf(bot.memberSettings.orEmpty()) }
    var installedConnectors by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var installedSkills by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var enabledConnectors by remember(bot.id) { mutableStateOf(bot.connectors.toSet()) }
    var enabledSkills by remember(bot.id) { mutableStateOf(bot.skills.toSet()) }
    var extensionsModified by remember(bot.id) { mutableStateOf(false) }
    var saving by remember(bot.id) { mutableStateOf(false) }
    var error by remember(bot.id) { mutableStateOf<String?>(null) }
    var confirmDelete by remember(bot.id) { mutableStateOf(false) }
    var confirmGroupDeleteForMemberRemoval by remember(bot.id) { mutableStateOf(false) }
    var roleId by remember(bot.id) { mutableStateOf("") }
    var roleMenuOpen by remember(bot.id) { mutableStateOf(false) }
    var workspacePickerOpen by remember(bot.id) { mutableStateOf(false) }
    var settingsQuery by remember(bot.id) { mutableStateOf("") }
    var expandedSection by remember(bot.id) { mutableStateOf(initialSection) }
    val candidates = remember(allBots, bot.id) {
        allBots.filter { it.kind != "group" && it.id != bot.id }
            .sortedWith(compareByDescending<Bot> { it.pinned }.thenBy { it.position }.thenBy { it.name.lowercase() })
    }
    val groupsRemovedWithBot = remember(allBots, bot.id) {
        if (isGroup) emptyList() else allBots.filter {
            it.kind == "group" && bot.id in it.members && it.members.size <= 2
        }
    }
    val availableModels = modelCatalog?.editorArray("models").orEmpty().mapNotNull { it.editorObject() }
    val defaultModelId = availableModels.firstOrNull { it.editorText("name").orEmpty().startsWith("default", true) }?.editorText("id")
    val modelOptionsForSelection = buildList {
        addAll(modelCatalog?.editorArray("options").orEmpty().mapNotNull { it.editorObject() })
        addAll(availableModels.firstOrNull { it.editorText("id") == model }?.editorArray("options").orEmpty().mapNotNull { it.editorObject() })
    }.distinctBy { it.editorText("id") }
    val selectedBackend = backends.firstOrNull { it.id == backend }
    val fastModeAvailable = fastMode ||
        (!creatingBot && backend == bot.backend && bot.fastModeSupport != "unsupported") ||
        (backend != "custom" && modelCatalog?.get("fastMode")?.jsonPrimitive?.booleanOrNull == true)
    val canSave = name.isNotBlank() && !saving && connection.hello != null &&
        (!creatingBot || (backend == "custom" && command.isNotBlank()) || selectedBackend?.available == true) &&
        (isGroup || backend != "custom" || command.isNotBlank()) &&
        (!isGroup || members.size in 2..50)

    LaunchedEffect(settingsQuery, isGroup) {
        val query = settingsQuery.trim()
        if (query.isNotEmpty()) {
            val sections = buildList {
                add("Overview" to "name role purpose")
                add("Instructions" to "instructions standing rules")
                if (isGroup) add("Members & access" to "members browser computer leader workspace room group")
                else {
                    add("Model" to "agent model fast custom command")
                    add("Permissions" to "permissions approval ask auto full")
                    add("Access" to "workspace folder browser computer access")
                    add("Skills & apps" to "skills connectors apps mcp extensions vault")
                }
                add("Identity" to "identity avatar appearance color shape pinned hidden")
                add("Voice & alerts" to "voice alerts notifications")
            }
            expandedSection = sections.firstOrNull { (title, terms) -> editorSectionMatches(query, title, terms) }?.first.orEmpty()
        }
    }

    LaunchedEffect(creatingBot, backends) {
        if (creatingBot && backend.isBlank()) {
            backend = backends.firstOrNull { it.available && it.curated == true }?.id
                ?: backends.firstOrNull { it.available }?.id.orEmpty()
        }
    }

    LaunchedEffect(backend, connection.hello?.hostId) {
        modelCatalog = null
        modelError = null
        if (isGroup || backend.isBlank() || backend == "custom" || connection.hello == null) return@LaunchedEffect
        modelLoading = true
        session.call("agentModels", buildJsonObject { put("backend", backend) })
            .onSuccess { modelCatalog = runCatching { it.jsonObject }.getOrNull() }
            .onFailure { modelError = it.message ?: "Couldn't load the agent's models" }
        modelLoading = false
    }

    LaunchedEffect(bot.id, connection.hello?.hostId) {
        if (connection.hello == null || isGroup) return@LaunchedEffect
        session.call("connectors", buildJsonObject {}).onSuccess { response ->
            val items = response.jsonObject["items"]?.jsonArray?.mapNotNull { runCatching { it.jsonObject }.getOrNull() }.orEmpty()
            installedConnectors = items
            if (creatingBot) enabledConnectors = items.mapNotNull { it.editorText("id") }.toSet()
        }.onFailure { error = it.message ?: "Couldn't load installed connectors" }
        session.call("skills", buildJsonObject {}).onSuccess { response ->
            val items = response.jsonObject["items"]?.jsonArray?.mapNotNull { runCatching { it.jsonObject }.getOrNull() }.orEmpty()
            installedSkills = items
            if (creatingBot) enabledSkills = items.mapNotNull { it.editorText("id") }.toSet()
        }.onFailure { error = it.message ?: "Couldn't load installed skills" }
    }

    fun save() {
        scope.launch {
            saving = true
            error = null
            val patch = buildJsonObject {
                if (!creatingBot) put("id", bot.id)
                put("name", name.trim())
                put("description", description.trim())
                put("avatarColor", avatarColor)
                put("avatarShape", avatarShape)
                put("pinned", pinned)
                put("hidden", hidden)
                put("notify", notify)
                put("instructions", instructions)
                if (isGroup) {
                    put("members", buildJsonArray { members.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
                    put("memberSettings", buildJsonObject {
                        members.forEach { memberId ->
                            val access = memberSettings[memberId] ?: MemberAccess()
                            put(memberId, buildJsonObject {
                                put("browser", access.browser)
                                put("computer", access.computer)
                            })
                        }
                    })
                    put("leader", leader)
                    put("workspace", workspace)
                } else {
                    put("backend", backend)
                    if (backend == "custom") put("command", command.trim())
                    put("cwd", workspace)
                    put("model", if (model.isBlank()) JsonNull else kotlinx.serialization.json.JsonPrimitive(model.trim()))
                    put("modelOptions", buildJsonObject { modelOptions.forEach { (key, value) -> put(key, value) } })
                    put("fastMode", fastMode)
                    put("permission", permission)
                    put("computer", computer)
                    put("browser", browser)
                    put("browserEngine", browserEngine)
                    if (extensionsModified) {
                        put("connectors", buildJsonArray {
                            enabledConnectors.sorted().forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
                        })
                        put("skills", buildJsonArray {
                            enabledSkills.sorted().forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
                        })
                    }
                }
            }
            val result = session.call(if (creatingBot) "createBot" else "updateBot", patch)
            saving = false
            if (result.isSuccess) onSaved()
            else error = result.exceptionOrNull()?.message ?: "The host rejected these changes"
        }
    }

    fun setMemberAccess(memberId: String, browser: Boolean? = null, computer: Boolean? = null) {
        val current = memberSettings[memberId] ?: MemberAccess()
        memberSettings = memberSettings + (memberId to current.copy(
            browser = browser ?: current.browser,
            computer = computer ?: current.computer,
        ))
    }

    fun setMemberIncluded(memberId: String, included: Boolean) {
        if (!included && memberId in members && members.size == 2 && !creatingBot) {
            confirmGroupDeleteForMemberRemoval = true
            return
        }
        if (included && memberId !in members && members.size >= 50) {
            error = "A room can have at most 50 bots."
            return
        }
        val next = if (included) members + memberId else members - memberId
        members = next
        if (leader !in next) leader = ""
        error = null
    }

    WorkbenchScaffold(
        title = when {
            creatingBot -> "New bot"
            isGroup -> "Edit group"
            else -> "Edit bot"
        },
        subtitle = if (isGroup) "Members, room tools and instructions" else "Name, model, workspace and tools",
        onBack = onCancel,
    ) {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                EditorSearchField(settingsQuery, onChange = { settingsQuery = it })

                EditorSection(
                    title = "Overview",
                    subtitle = if (isGroup) "Name and purpose" else "Name and starting role",
                    visible = editorSectionMatches(settingsQuery, "Overview", "name role purpose"),
                    expanded = expandedSection == "Overview",
                    onToggle = { expandedSection = if (expandedSection == "Overview") "" else "Overview" },
                ) {
                SectionCard {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        if (creatingBot) {
                            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                CharacterAvatar(
                                    name.ifBlank { "New bot" },
                                    avatarColor,
                                    avatarShape,
                                    size = 72.dp,
                                    modifier = Modifier.clickable {
                                        val colors = AvatarPalette.colors.map { it.id }
                                        val shapes = AvatarPalette.shapes
                                        avatarColor = colors[(colors.indexOf(avatarColor) + 1).mod(colors.size)]
                                        avatarShape = shapes[(shapes.indexOf(avatarShape) + 1).mod(shapes.size)]
                                    },
                                )
                                Text(name.ifBlank { "Name your bot" }, color = colors.text, style = CodyncType.title)
                                Text("Tap the avatar to shuffle its look. Choose a role, then edit anything below.", color = colors.tertiary, style = CodyncType.caption)
                            }
                            Field("Name", name, singleLine = true) { name = it }
                            Text("Starting role", color = colors.tertiary, style = CodyncType.caption)
                            CodyncMenuAnchor(
                                expanded = roleMenuOpen,
                                onDismissRequest = { roleMenuOpen = false },
                                items = buildList {
                                    add(CodyncMenuItem("Custom", CodyncIcons.Edit, onClick = { roleId = ""; roleMenuOpen = false }))
                                    botStartingRoles.forEach { role ->
                                        add(CodyncMenuItem(role.title, onClick = {
                                            roleId = role.id
                                            name = role.name
                                            description = role.instructions
                                            instructions = ""
                                            roleMenuOpen = false
                                        }))
                                    }
                                },
                            ) {
                                val selectedRole = botStartingRoles.firstOrNull { it.id == roleId }
                                SettingsRow(
                                    title = selectedRole?.title ?: "Custom",
                                    subtitle = "Fills a useful starting name and standing instructions",
                                    icon = CodyncIcons.Bots,
                                    onClick = { roleMenuOpen = true },
                                )
                            }
                        } else {
                            Field("Name", name, singleLine = true) { name = it }
                        }
                        if (isGroup) Field("What the group works on", description, singleLine = true) { description = it }
                    }
                }
                }

                EditorSection(
                    title = "Instructions",
                    subtitle = if (isGroup) "Rules shared by all group members" else "Standing instructions for this bot",
                    visible = editorSectionMatches(settingsQuery, "Instructions", "instructions standing rules"),
                    expanded = expandedSection == "Instructions",
                    onToggle = { expandedSection = if (expandedSection == "Instructions") "" else "Instructions" },
                ) {
                    SectionCard {
                        Column(Modifier.padding(14.dp)) {
                            if (isGroup) Field("Instructions for all members", instructions, minLines = 3) { instructions = it }
                            else Field("Standing instructions", description, minLines = 3) { description = it }
                        }
                    }
                }

                if (!isGroup) {
                    EditorSection(
                        title = "Model",
                        subtitle = "Agent, model, custom command and Fast mode",
                        visible = editorSectionMatches(settingsQuery, "Model", "agent model fast custom command"),
                        expanded = expandedSection == "Model",
                        onToggle = { expandedSection = if (expandedSection == "Model") "" else "Model" },
                    ) {
                    SectionCard {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            Text("Agent", color = colors.tertiary, style = CodyncType.caption)
                            if (backends.isEmpty()) {
                                Text(
                                    if (connection.hello == null) "Connect to the computer to load its available agents."
                                    else "This computer has no agent backends available yet.",
                                    color = colors.secondary,
                                    style = CodyncType.footnote,
                                )
                            } else {
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                    backends.forEach { item ->
                                        val label = if (item.available) item.name else "${item.name} · not installed"
                                        FilterChip(
                                            label,
                                            backend == item.id,
                                            onClick = {
                                                backend = item.id
                                                model = ""
                                                modelOptions = emptyMap()
                                                fastMode = false
                                            },
                                        )
                                    }
                                }
                                selectedBackend?.let { selected ->
                                    Text(
                                        when {
                                            selected.available -> selected.description.orEmpty().ifBlank { "Available on this computer." }
                                            selected.installHint.isNotBlank() -> selected.installHint
                                            else -> "This agent isn't installed on the computer."
                                        },
                                        color = if (selected.available) colors.secondary else colors.warning,
                                        style = CodyncType.footnote,
                                    )
                                }
                            }
                            FilterChip(
                                "Custom command",
                                backend == "custom",
                                onClick = {
                                    backend = "custom"
                                    model = ""
                                    modelOptions = emptyMap()
                                    fastMode = false
                                },
                            )
                            if (backend == "custom") {
                                Field("ACP command", command, singleLine = true) { command = it }
                            }
                            Text("Model", color = colors.tertiary, style = CodyncType.caption)
                            CodyncMenuAnchor(
                                expanded = modelMenuOpen,
                                onDismissRequest = { modelMenuOpen = false },
                                items = buildList {
                                    add(CodyncMenuItem("Default", CodyncIcons.Done, onClick = { model = ""; modelOptions = emptyMap() }))
                                    availableModels.filterNot { it.editorText("id") == defaultModelId }.forEach { modelItem ->
                                        val id = modelItem.editorText("id") ?: return@forEach
                                        add(CodyncMenuItem(modelItem.editorText("name") ?: id, onClick = { model = id; modelOptions = emptyMap() }))
                                    }
                                },
                            ) {
                                SettingsRow(
                                    title = availableModels.firstOrNull { it.editorText("id") == model }?.editorText("name")
                                        ?: model.ifBlank { "Default" },
                                    subtitle = when {
                                        backend == "custom" -> "Enter a model ID if your agent needs one."
                                        modelLoading -> "Loading models from this computer…"
                                        modelError != null -> modelError!!
                                        availableModels.isEmpty() -> "The agent's default model will be used."
                                        else -> "Choose a model advertised by ${selectedBackend?.name ?: backend}."
                                    },
                                    icon = CodyncIcons.Settings,
                                    onClick = { modelMenuOpen = true },
                                )
                            }
                            if (backend == "custom" || (availableModels.isEmpty() && !modelLoading)) {
                                Field("Model ID (optional)", model, singleLine = true) { model = it; modelOptions = emptyMap() }
                            }
                            modelOptionsForSelection.forEach optionLoop@{ option ->
                                val id = option.editorText("id") ?: return@optionLoop
                                val values = option.editorArray("values").orEmpty().mapNotNull { it.editorObject() }
                                SettingsRow(
                                    title = option.editorText("name") ?: id,
                                    subtitle = values.firstOrNull { it.editorText("id") == modelOptions[id] }?.editorText("name") ?: "Default",
                                    showChevron = false,
                                )
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    FilterChip("Default", modelOptions[id].isNullOrBlank(), onClick = { modelOptions = modelOptions - id })
                                    values.forEach valueLoop@{ value ->
                                        val valueId = value.editorText("id") ?: return@valueLoop
                                        FilterChip(value.editorText("name") ?: valueId, modelOptions[id] == valueId, onClick = {
                                            modelOptions = modelOptions + (id to valueId)
                                        })
                                    }
                                }
                            }
                        }
                    }
                    }

                    EditorSection(
                        title = "Permissions",
                        subtitle = "Choose what the bot may do without asking",
                        visible = editorSectionMatches(settingsQuery, "Permissions", "permissions approval ask auto full"),
                        expanded = expandedSection == "Permissions",
                        onToggle = { expandedSection = if (expandedSection == "Permissions") "" else "Permissions" },
                    ) {
                        SectionCard {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Approval mode", color = colors.tertiary, style = CodyncType.caption)
                                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                    listOf("ask" to "Ask me", "auto" to "Auto", "full" to "Full access").forEach { (value, label) ->
                                        FilterChip(label, permission == value, onClick = { permission = value })
                                    }
                                }
                                if (fastModeAvailable) SettingsRow(
                                    title = "Fast mode",
                                    subtitle = if (selectedBackend?.available == false) "Set up this agent on the computer first" else "Prefer quicker models for this bot",
                                    showChevron = false,
                                    onClick = { if (selectedBackend?.available != false) fastMode = !fastMode },
                                    trailing = {
                                        CodyncSwitch(
                                            checked = fastMode,
                                            enabled = selectedBackend?.available != false,
                                            onCheckedChange = { fastMode = it },
                                        )
                                    },
                                )
                            }
                        }
                    }

                    EditorSection(
                        title = "Access",
                        subtitle = "Workspace, computer and browser permissions",
                        visible = editorSectionMatches(settingsQuery, "Access", "workspace folder browser computer access"),
                        expanded = expandedSection == "Access",
                        onToggle = { expandedSection = if (expandedSection == "Access") "" else "Access" },
                    ) {
                        SectionCard {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Column(Modifier.weight(1f)) {
                                        Field("Workspace", workspace, singleLine = true) { workspace = it }
                                    }
                                    SecondaryButton("Browse", onClick = { workspacePickerOpen = true })
                                }
                                SettingsRow(
                                    title = "Computer control",
                                    subtitle = "Let this bot use the host's screen and input tools",
                                    showChevron = false,
                                    onClick = { computer = !computer },
                                    trailing = { CodyncSwitch(computer, onCheckedChange = { computer = it }) },
                                )
                                SettingsRow(
                                    title = "Browser",
                                    subtitle = "Give this bot its own browser profile",
                                    showChevron = false,
                                    onClick = { browser = !browser },
                                    trailing = { CodyncSwitch(browser, onCheckedChange = { browser = it }) },
                                )
                                if (browser) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                        listOf("" to "Auto", "chrome" to "Chrome", "firefox" to "Firefox").forEach { (value, label) ->
                                            FilterChip(label, browserEngine == value, onClick = { browserEngine = value })
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (isGroup) {
                    EditorSection(
                        title = "Members & access",
                        subtitle = "Room members, permissions, leader and workspace",
                        visible = editorSectionMatches(settingsQuery, "Members & access", "members browser computer leader workspace room group"),
                        expanded = expandedSection == "Members & access",
                        onToggle = { expandedSection = if (expandedSection == "Members & access") "" else "Members & access" },
                    ) {
                    SectionCard {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Field("Shared workspace (optional)", workspace, singleLine = true) { workspace = it }
                                }
                                SecondaryButton("Browse", onClick = { workspacePickerOpen = true })
                            }
                            Text("Members · ${members.size} of 2–50", color = colors.tertiary, style = CodyncType.caption)
                            if (candidates.isEmpty()) {
                                Text("No other bots on this computer yet.", color = colors.tertiary, style = CodyncType.footnote)
                            }
                            candidates.forEach { candidate ->
                                Row(
                                    Modifier.fillMaxWidth().clickable {
                                        setMemberIncluded(candidate.id, candidate.id !in members)
                                    }.padding(horizontal = 14.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    CharacterAvatar(candidate.name, candidate.avatarColor, candidate.avatarShape, size = 34.dp)
                                    Spacer(Modifier.width(11.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(candidate.name, color = colors.text, style = CodyncType.body)
                                        Text(candidate.backend, color = colors.secondary, style = CodyncType.caption, maxLines = 1)
                                    }
                                    if (leader == candidate.id) {
                                        Text("Leader", color = colors.tertiary, style = CodyncType.caption)
                                        Spacer(Modifier.width(8.dp))
                                    }
                                    CodyncSwitch(
                                        checked = candidate.id in members,
                                        onCheckedChange = { setMemberIncluded(candidate.id, it) },
                                    )
                                }
                                if (candidate.id in members) {
                                    val access = memberSettings[candidate.id] ?: MemberAccess()
                                    Column(Modifier.padding(start = 14.dp)) {
                                        SettingsRow(
                                            title = "Browser in this room",
                                            subtitle = when {
                                                access.browser -> "Allowed for this member in the room"
                                                candidate.browser -> "Also allowed by the bot's own Browser setting"
                                                else -> "Off for this member in the room"
                                            },
                                            showChevron = false,
                                            trailing = {
                                                CodyncSwitch(access.browser, onCheckedChange = {
                                                    setMemberAccess(candidate.id, browser = it)
                                                })
                                            },
                                        )
                                        SettingsRow(
                                            title = "Computer in this room",
                                            subtitle = when {
                                                access.computer -> "Allowed for this member in the room"
                                                candidate.computer -> "Also allowed by the bot's own Computer setting"
                                                else -> "Off for this member in the room"
                                            },
                                            showChevron = false,
                                            trailing = {
                                                CodyncSwitch(access.computer, onCheckedChange = {
                                                    setMemberAccess(candidate.id, computer = it)
                                                })
                                            },
                                        )
                                    }
                                }
                            }
                            Text("Room leader", color = colors.tertiary, style = CodyncType.caption)
                            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                FilterChip("No leader", leader.isBlank(), onClick = { leader = "" })
                                members.forEach { id ->
                                    candidates.firstOrNull { it.id == id }?.let { member ->
                                        FilterChip(member.name, leader == id, onClick = { leader = id })
                                    }
                                }
                            }
                        }
                    }
                    }
                }

                if (!isGroup) {
                    EditorSection(
                        title = "Skills & apps",
                        subtitle = "Per-bot skills and connected apps",
                        visible = editorSectionMatches(settingsQuery, "Skills & apps", "skills connectors apps mcp extensions vault"),
                        expanded = expandedSection == "Skills & apps",
                        onToggle = { expandedSection = if (expandedSection == "Skills & apps") "" else "Skills & apps" },
                    ) {
                    SectionCard {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Extensions and skills", color = colors.tertiary, style = CodyncType.caption)
                            if (installedConnectors.isEmpty() && installedSkills.isEmpty()) {
                                Text("Install MCP connectors and skills in Extensions on this computer first.", color = colors.secondary, style = CodyncType.footnote)
                            }
                            installedConnectors.forEach { connector ->
                                val id = connector.editorText("id") ?: return@forEach
                                SettingsRow(
                                    title = connector.editorText("name") ?: id,
                                    subtitle = connector.editorText("description").orEmpty().ifBlank { connector.editorText("kind") ?: "MCP connector" },
                                    icon = CodyncIcons.Extension,
                                    showChevron = false,
                                    trailing = {
                                        CodyncSwitch(id in enabledConnectors, onCheckedChange = { enabled ->
                                            enabledConnectors = if (enabled) enabledConnectors + id else enabledConnectors - id
                                            extensionsModified = true
                                        })
                                    },
                                )
                            }
                            installedSkills.forEach { skill ->
                                val id = skill.editorText("id") ?: return@forEach
                                SettingsRow(
                                    title = skill.editorText("name") ?: id,
                                    subtitle = skill.editorText("description").orEmpty().ifBlank { "Skill" },
                                    icon = CodyncIcons.Memory,
                                    showChevron = false,
                                    trailing = {
                                        CodyncSwitch(id in enabledSkills, onCheckedChange = { enabled ->
                                            enabledSkills = if (enabled) enabledSkills + id else enabledSkills - id
                                            extensionsModified = true
                                        })
                                    },
                                )
                            }
                        }
                    }
                    }
                }

                EditorSection(
                    title = "Identity",
                    subtitle = "Avatar, roster visibility and appearance",
                    visible = editorSectionMatches(settingsQuery, "Identity", "identity avatar appearance color shape pinned hidden"),
                    expanded = expandedSection == "Identity",
                    onToggle = { expandedSection = if (expandedSection == "Identity") "" else "Identity" },
                ) {
                SectionCard {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CharacterAvatar(name, avatarColor, avatarShape, size = 52.dp)
                            Column {
                                Text("Avatar preview", color = colors.text, style = CodyncType.body)
                                Text("${avatarShape.replaceFirstChar { it.uppercase() }} · ${avatarColor.replaceFirstChar { it.uppercase() }}", color = colors.secondary, style = CodyncType.caption)
                            }
                        }
                        Text("Appearance", color = colors.tertiary, style = CodyncType.caption)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            AvatarPalette.colors.forEach { swatch ->
                                val value = swatch.id
                                FilterChip(value, avatarColor == value, onClick = { avatarColor = value })
                            }
                        }
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            AvatarPalette.shapes.forEach { value ->
                                FilterChip(value, avatarShape == value, onClick = { avatarShape = value })
                            }
                        }
                        SettingsRow(
                            title = "Pinned in roster",
                            showChevron = false,
                            onClick = { pinned = !pinned },
                            trailing = { CodyncSwitch(pinned, onCheckedChange = { pinned = it }) },
                        )
                        SettingsRow(
                            title = "Hidden",
                            subtitle = "Stays out of the main roster",
                            showChevron = false,
                            onClick = { hidden = !hidden },
                            trailing = { CodyncSwitch(hidden, onCheckedChange = { hidden = it }) },
                        )
                    }
                }
                }

                EditorSection(
                    title = "Voice & alerts",
                    subtitle = "Notifications when this bot finishes or needs you",
                    visible = editorSectionMatches(settingsQuery, "Voice & alerts", "voice alerts notifications"),
                    expanded = expandedSection == "Voice & alerts",
                    onToggle = { expandedSection = if (expandedSection == "Voice & alerts") "" else "Voice & alerts" },
                ) {
                    SectionCard {
                        SettingsRow(
                            title = "Notifications",
                            subtitle = "Get notified when this bot finishes or needs you",
                            showChevron = false,
                            onClick = { notify = !notify },
                            trailing = { CodyncSwitch(notify, onCheckedChange = { notify = it }) },
                        )
                    }
                }

                val sectionTerms = if (isGroup) {
                    listOf("Overview" to "name role purpose", "Instructions" to "instructions standing rules", "Members & access" to "members browser computer leader workspace room group", "Identity" to "identity avatar appearance color shape pinned hidden", "Voice & alerts" to "voice alerts notifications")
                } else {
                    listOf("Overview" to "name role purpose", "Instructions" to "instructions standing rules", "Model" to "agent model fast custom command", "Permissions" to "permissions approval ask auto full", "Access" to "workspace folder browser computer access", "Skills & apps" to "skills connectors apps mcp extensions vault", "Identity" to "identity avatar appearance color shape pinned hidden", "Voice & alerts" to "voice alerts notifications")
                }
                if (settingsQuery.isNotBlank() && sectionTerms.none { (title, terms) -> editorSectionMatches(settingsQuery, title, terms) }) {
                    Text("No settings match ‘$settingsQuery’.", color = colors.tertiary, style = CodyncType.footnote)
                }

                error?.let {
                    Text(it, color = colors.warning, style = CodyncType.footnote)
                }
                if (!creatingBot) {
                    DestructiveButton(
                        if (isGroup) "Delete group" else "Delete bot",
                        onClick = { confirmDelete = true },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .background(colors.background)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (isGroup) Arrangement.End else Arrangement.SpaceBetween,
            ) {
                if (!isGroup) {
                    SecondaryButton("Cancel", onClick = onCancel)
                    PrimaryButton(
                        text = when {
                            saving -> if (creatingBot) "Creating…" else "Saving…"
                            creatingBot -> "Create bot"
                            else -> "Done"
                        },
                        onClick = ::save,
                        enabled = canSave,
                    )
                } else {
                    SecondaryButton("Cancel", onClick = onCancel)
                    Spacer(Modifier.height(0.dp))
                    PrimaryButton(
                        text = if (saving) "Saving…" else "Done",
                        onClick = ::save,
                        enabled = canSave,
                    )
                }
            }
        }
    }

    if (workspacePickerOpen) {
        HostFolderPickerDialog(
            session = session,
            initialPath = connection.hello?.home,
            onDismiss = { workspacePickerOpen = false },
            onSelect = { workspace = it; workspacePickerOpen = false },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { if (!saving) confirmDelete = false },
            title = { Text(if (isGroup) "Delete this group?" else "Delete this bot?") },
            text = {
                Text(
                    when {
                        isGroup -> "The room and its messages are removed. Its member bots stay."
                        groupsRemovedWithBot.isNotEmpty() -> "Removing this bot leaves ${groupsRemovedWithBot.joinToString { it.name }} with fewer than two members, so those rooms and their messages will also be deleted. The bot's files stay on disk."
                        else -> "The bot is removed from this computer. Its files stay on disk."
                    },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !saving,
                    onClick = {
                        scope.launch {
                            saving = true
                            val result = session.call("deleteBot", buildJsonObject { put("botId", bot.id) })
                            saving = false
                            confirmDelete = false
                            if (result.isSuccess) onDeleted()
                            else error = result.exceptionOrNull()?.message ?: "Couldn't delete this bot"
                        }
                    },
                ) { Text(if (!isGroup && groupsRemovedWithBot.isNotEmpty()) "Delete bot and rooms" else "Delete") }
            },
            dismissButton = { TextButton(enabled = !saving, onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }

    if (confirmGroupDeleteForMemberRemoval) {
        AlertDialog(
            onDismissRequest = { if (!saving) confirmGroupDeleteForMemberRemoval = false },
            title = { Text("Delete this group?") },
            text = {
                Text("Groups need at least two bots. Removing this member would leave one, so the group and its messages must be deleted.")
            },
            confirmButton = {
                TextButton(
                    enabled = !saving,
                    onClick = {
                        scope.launch {
                            saving = true
                            val result = session.call("deleteBot", buildJsonObject { put("botId", bot.id) })
                            saving = false
                            confirmGroupDeleteForMemberRemoval = false
                            if (result.isSuccess) onDeleted()
                            else error = result.exceptionOrNull()?.message ?: "Couldn't delete this group"
                        }
                    },
                ) { Text("Delete group") }
            },
            dismissButton = {
                TextButton(enabled = !saving, onClick = { confirmGroupDeleteForMemberRemoval = false }) {
                    Text("Keep group")
                }
            },
        )
    }
}

@Composable
private fun EditorSearchField(query: String, onChange: (String) -> Unit) {
    val colors = CodyncTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface)
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CodyncIcon(CodyncIcons.Search, tint = colors.tertiary, size = 17.dp)
        Spacer(Modifier.width(8.dp))
        androidx.compose.foundation.layout.Box(Modifier.weight(1f)) {
            if (query.isBlank()) Text("Search settings", color = colors.tertiary, style = CodyncType.body)
            BasicTextField(
                value = query,
                onValueChange = onChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = TextStyle(color = colors.text, fontSize = 15.sp, fontFamily = CodyncType.body.fontFamily),
                cursorBrush = SolidColor(colors.text),
            )
        }
        if (query.isNotBlank()) {
            Text(
                "Clear",
                modifier = Modifier.clickable { onChange("") },
                color = colors.secondary,
                style = CodyncType.caption,
            )
        }
    }
}

@Composable
private fun EditorSection(
    title: String,
    subtitle: String,
    visible: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (!visible) return
    val colors = CodyncTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionCard {
            SettingsRow(
                title = title,
                subtitle = subtitle,
                icon = CodyncIcons.Settings,
                showChevron = false,
                onClick = onToggle,
                trailing = {
                    CodyncIcon(
                        if (expanded) CodyncIcons.ChevronDown else CodyncIcons.ChevronRight,
                        tint = colors.tertiary,
                        size = 17.dp,
                    )
                },
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) { content() }
    }
}

private fun editorSectionMatches(query: String, title: String, terms: String): Boolean {
    val normalized = query.trim().lowercase()
    val words = normalized.split(Regex("\\s+")).filter(String::isNotBlank)
    if (words.isEmpty()) return true
    val searchable = "$title $terms".lowercase()
    val labels = searchable.split(Regex("[^a-z0-9]+"))
    return searchable.contains(normalized) || words.any { word ->
        word.length >= 2 && labels.any { it.startsWith(word) }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    singleLine: Boolean = false,
    minLines: Int = 1,
    onChange: (String) -> Unit,
) {
    val colors = CodyncTheme.colors
    Column {
        Text(label, color = colors.tertiary, style = CodyncType.caption)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = singleLine,
            minLines = minLines,
            maxLines = if (singleLine) 1 else 8,
            textStyle = TextStyle(color = colors.text, fontSize = 15.sp, fontFamily = CodyncType.body.fontFamily),
            cursorBrush = SolidColor(colors.text),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surface)
                .padding(12.dp),
        )
    }
}

private fun JsonObject.editorText(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

private fun JsonObject.editorArray(key: String): kotlinx.serialization.json.JsonArray? =
    this[key]?.let { runCatching { it.jsonArray }.getOrNull() }

private fun kotlinx.serialization.json.JsonElement.editorObject(): JsonObject? =
    runCatching { jsonObject }.getOrNull()

private data class BotStartingRole(val id: String, val name: String, val title: String, val instructions: String)

private val botStartingRoles = listOf(
    BotStartingRole("assistant", "Assistant", "General assistant", "You are a capable, plain-spoken assistant. Ask one clarifying question when a request is ambiguous; otherwise do the work and show the result. Keep replies short and concrete."),
    BotStartingRole("researcher", "Scout", "Researcher", "You research topics on the web and in files, compare sources, and report findings with links or paths. Flag what you could not verify. Never present a guess as a fact."),
    BotStartingRole("coder", "Dev", "Coding partner", "You write and review code on this computer. Keep changes small and tested; explain what you changed and why in one short paragraph. Never push without being asked."),
    BotStartingRole("inbox", "Inbox", "Email triage", "You triage email: summarize what needs a reply, draft short replies for approval, and archive noise. Never send anything without approval. Ask when unsure."),
    BotStartingRole("watcher", "Watch", "Community monitor", "You watch feeds, repos, and pages the user points at, and post a short digest when something notable happens. Stay quiet otherwise. Flag anything urgent immediately."),
    BotStartingRole("ops", "Ops", "Operations", "You run recurring tasks on a schedule: backups, checks, reports. Do the work, log what happened in one line, and only alert when a task actually fails."),
)
