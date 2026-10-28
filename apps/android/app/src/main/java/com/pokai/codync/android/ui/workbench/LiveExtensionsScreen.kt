package com.pokai.codync.android.ui.workbench

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokai.codync.data.SessionStore
import com.pokai.codync.design.CodyncIcons
import com.pokai.codync.design.DestructiveButton
import com.pokai.codync.design.FilterChip
import com.pokai.codync.design.Pill
import com.pokai.codync.design.SecondaryButton
import com.pokai.codync.design.SectionCard
import com.pokai.codync.design.SettingsRow
import com.pokai.codync.design.theme.CodyncTheme
import com.pokai.codync.design.theme.CodyncType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private val extensionTabs = listOf("Installed", "MCP", "Skills", "Apps", "Vault")

@Composable
fun LiveExtensionsScreen(session: SessionStore, onBack: () -> Unit) {
    val colors = CodyncTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableStateOf("Installed") }
    var query by rememberSaveable { mutableStateOf("") }
    var refreshKey by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var busyId by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var installedConnectors by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var installedSkills by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var registryConnectors by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var registrySkills by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var composioApps by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var composioStatus by remember { mutableStateOf<JsonObject?>(null) }
    var credentialStatus by remember { mutableStateOf<JsonObject?>(null) }
    var savedLogins by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var connectorToInstall by remember { mutableStateOf<JsonObject?>(null) }
    var selectedOption by remember { mutableStateOf<JsonObject?>(null) }
    var skillToRead by remember { mutableStateOf<JsonObject?>(null) }
    var skillInstructions by remember { mutableStateOf<String?>(null) }
    var formTitle by remember { mutableStateOf<String?>(null) }
    var formFields by remember { mutableStateOf<List<Pair<String, Boolean>>>(emptyList()) }
    var formAction by remember { mutableStateOf<(Map<String, String>) -> Unit>({}) }
    val formValues = remember { mutableStateMapOf<String, String>() }

    LaunchedEffect(tab, query, refreshKey) {
        loading = true
        error = null
        try {
            if (tab == "MCP" || tab == "Installed") {
                val installed = session.call("connectors", buildJsonObject {}).getOrThrow().jsonObject
                installedConnectors = installed.array("items")
            }
            if (tab == "Skills" || tab == "Installed") {
                val installed = session.call("skills", buildJsonObject {}).getOrThrow().jsonObject
                installedSkills = installed.array("items")
            }
            when (tab) {
                "MCP" -> {
                    delay(250)
                    registryConnectors = session.call("marketConnectors", buildJsonObject {
                        put("search", query.trim())
                        put("cursor", "")
                    }).getOrThrow().jsonObject.array("items")
                }
                "Skills" -> registrySkills = session.call("marketSkills", buildJsonObject {}).getOrThrow().jsonObject.array("items")
                "Apps" -> {
                    composioStatus = session.call("composioStatus", buildJsonObject {}).getOrThrow().jsonObject
                    if (composioStatus?.bool("configured") == true) {
                        delay(250)
                        composioApps = session.call("composioToolkits", buildJsonObject {
                            put("search", query.trim())
                            put("cursor", "")
                        }).getOrThrow().jsonObject.array("items")
                    } else composioApps = emptyList()
                }
                "Vault" -> {
                    credentialStatus = session.call("credentialStatus", buildJsonObject {}).getOrThrow().jsonObject
                    savedLogins = session.call("credentialLogins", buildJsonObject {}).getOrThrow().jsonObject.array("items")
                }
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            error = failure.message ?: "Couldn't load this section from the computer"
        } finally {
            loading = false
        }
    }

    fun runAction(id: String, action: suspend () -> Unit, reload: Boolean = true) {
        scope.launch {
            busyId = id
            error = null
            runCatching { action() }
                .onSuccess { if (reload) refreshKey++ }
                .onFailure { error = it.message ?: "The host couldn't complete that action" }
            busyId = null
        }
    }

    fun requestForm(title: String, fields: List<Pair<String, Boolean>>, action: (Map<String, String>) -> Unit) {
        formValues.clear()
        formTitle = title
        formFields = fields
        formAction = action
    }

    WorkbenchScaffold("Extensions", "Install on this computer, then enable per bot", onBack) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                extensionTabs.forEach { item -> FilterChip(item, tab == item, onClick = { tab = item }) }
            }
            if (tab in setOf("MCP", "Skills", "Apps")) {
                ExtensionSearch(query, "Search ${if (tab == "Apps") "apps" else tab.lowercase()}") { query = it }
            }
            error?.let {
                Text(it, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp), color = colors.warning, style = CodyncType.footnote)
            }
            if (loading) {
                Text("Loading from the computer…", modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp), color = colors.tertiary, style = CodyncType.footnote)
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 5.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (tab) {
                    "Installed" -> {
                        item { ExtensionLabel("MCP servers", installedConnectors.size) }
                        items(installedConnectors, key = { "connector-${it.text("id")}" }) { connector ->
                            SectionCard {
                                SettingsRow(
                                    title = connector.text("name") ?: "Connector",
                                    subtitle = connector.text("description") ?: connector.text("url") ?: connector.text("command").orEmpty(),
                                    icon = CodyncIcons.Extension,
                                    trailing = {
                                        when {
                                            busyId == connector.text("id") -> Pill("Working", fill = colors.bubbleAgent, color = colors.secondary)
                                            connector.text("auth") == "signedOut" -> SecondaryButton("Sign in", onClick = {
                                                runAction(connector.text("id").orEmpty(), {
                                                    val plan = session.call("connectorSignIn", buildJsonObject { put("id", connector.text("id").orEmpty()) }).getOrThrow().jsonObject
                                                    val url = plan.text("url") ?: error("The host returned no sign-in URL")
                                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                                }, reload = false)
                                            })
                                            else -> SecondaryButton("Remove", onClick = {
                                                runAction(connector.text("id").orEmpty(), {
                                                    session.call("removeConnector", buildJsonObject { put("id", connector.text("id").orEmpty()) }).getOrThrow()
                                                })
                                            })
                                        }
                                    },
                                    onClick = {
                                        runAction(connector.text("id").orEmpty(), {
                                            session.call("connectorVerify", buildJsonObject { put("id", connector.text("id").orEmpty()) }).getOrThrow()
                                        }, reload = false)
                                    },
                                )
                            }
                        }
                        item { ExtensionLabel("Skills", installedSkills.size) }
                        items(installedSkills, key = { "skill-${it.text("id")}" }) { skill ->
                            SectionCard {
                                SettingsRow(
                                    title = skill.text("name") ?: "Skill",
                                    subtitle = skill.text("description").orEmpty(),
                                    icon = CodyncIcons.Memory,
                                    onClick = {
                                        skillToRead = skill
                                        runAction("skill-details", {
                                            val markdown = session.call("skillDetails", buildJsonObject { put("id", skill.text("id").orEmpty()) }).getOrThrow().jsonObject.text("markdown").orEmpty()
                                            skillInstructions = markdown
                                        }, reload = false)
                                    },
                                    trailing = { SecondaryButton("Remove", onClick = {
                                        runAction(skill.text("id").orEmpty(), {
                                            session.call("removeSkill", buildJsonObject { put("id", skill.text("id").orEmpty()) }).getOrThrow()
                                        })
                                    }) },
                                )
                            }
                        }
                        item {
                            SectionCard {
                                SettingsRow(
                                    title = "Per-bot access",
                                    subtitle = "Open a bot's details → Edit → Extensions to choose its MCP servers and skills.",
                                    icon = CodyncIcons.Lock,
                                    showChevron = false,
                                )
                            }
                        }
                    }
                    "MCP" -> {
                        item { ExtensionLabel("MCP Registry", registryConnectors.size) }
                        items(registryConnectors, key = { "market-${it.text("name")}" }) { item ->
                            val isInstalled = item.bool("installed") || installedConnectors.any { it.text("registryName") == item.text("name") }
                            SectionCard {
                                SettingsRow(
                                    title = item.text("title") ?: item.text("name") ?: "MCP server",
                                    subtitle = item.text("description") ?: item.text("website") ?: "",
                                    icon = CodyncIcons.Extension,
                                    trailing = {
                                        if (isInstalled) Pill("Installed", fill = colors.added.copy(alpha = 0.12f), color = colors.added)
                                        else SecondaryButton("Add", onClick = {
                                            connectorToInstall = item
                                            selectedOption = item.array("options").firstOrNull()
                                            formValues.clear()
                                        })
                                    },
                                    onClick = {
                                        connectorToInstall = item
                                        selectedOption = item.array("options").firstOrNull()
                                        formValues.clear()
                                    },
                                )
                            }
                        }
                    }
                    "Skills" -> {
                        item { ExtensionLabel("Skill catalog", registrySkills.size) }
                        items(registrySkills, key = { "catalog-${it.text("source")}" }) { skill ->
                            val installed = skill.bool("installed") || installedSkills.any { it.text("source") == skill.text("source") }
                            SectionCard {
                                SettingsRow(
                                    title = skill.text("name") ?: skill.text("source") ?: "Skill",
                                    subtitle = skill.text("description").orEmpty(),
                                    icon = CodyncIcons.Memory,
                                    trailing = if (installed) {
                                        { Pill("Installed", fill = colors.added.copy(alpha = 0.12f), color = colors.added) }
                                    } else {
                                        { SecondaryButton("Install", enabled = busyId != skill.text("source"), onClick = {
                                            runAction(skill.text("source").orEmpty(), {
                                                session.call("installSkill", buildJsonObject { put("source", skill.text("source").orEmpty()) }).getOrThrow()
                                            })
                                        }) }
                                    },
                                    onClick = {
                                        skillToRead = skill
                                        runAction("skill-details", {
                                            val markdown = session.call("skillDetails", buildJsonObject { put("source", skill.text("source").orEmpty()) }).getOrThrow().jsonObject.text("markdown").orEmpty()
                                            skillInstructions = markdown
                                        }, reload = false)
                                    },
                                )
                            }
                        }
                    }
                    "Apps" -> {
                        item {
                            SectionCard {
                                Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Composio", color = colors.text, style = CodyncType.body.copy(fontWeight = FontWeight.SemiBold))
                                    Text(
                                        if (composioStatus?.bool("configured") == true) "Apps connect through the host's Composio account."
                                        else "Add the Composio key on the host to browse and connect apps.",
                                        color = colors.secondary,
                                        style = CodyncType.footnote,
                                    )
                                    if (composioStatus?.bool("configured") == true) {
                                        SecondaryButton("Refresh apps", onClick = { refreshKey++ })
                                    } else {
                                        SecondaryButton("Set Composio key", onClick = {
                                            requestForm("Composio API key", listOf("key" to true)) { values ->
                                                runAction("composio-key", {
                                                    session.call("setComposioKey", buildJsonObject { put("key", values["key"].orEmpty()) }).getOrThrow()
                                                })
                                            }
                                        })
                                    }
                                }
                            }
                        }
                        items(composioApps, key = { "app-${it.text("slug")}" }) { app ->
                            val connected = app["connection"]?.let { runCatching { it.jsonObject.text("status") == "active" }.getOrDefault(false) } == true
                            SectionCard {
                                SettingsRow(
                                    title = app.text("name") ?: app.text("slug") ?: "App",
                                    subtitle = app.text("description").orEmpty(),
                                    icon = CodyncIcons.Extension,
                                    trailing = {
                                        if (connected) Pill("Connected", fill = colors.added.copy(alpha = 0.12f), color = colors.added)
                                        else SecondaryButton("Connect", onClick = {
                                            runAction(app.text("slug").orEmpty(), {
                                                val response = session.call("composioConnect", buildJsonObject { put("toolkit", app.text("slug").orEmpty()) }).getOrThrow().jsonObject
                                                when (response.text("status")) {
                                                    "redirect" -> {
                                                        val url = response.text("url") ?: error("Composio returned no sign-in link")
                                                        val id = response.text("connection")
                                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                                        if (!id.isNullOrBlank()) {
                                                            for (attempt in 0 until 60) {
                                                                delay(2_000)
                                                                val state = session.call("composioConnection", buildJsonObject { put("id", id) }).getOrThrow().jsonObject
                                                                if (state.text("status") == "active") break
                                                            }
                                                        }
                                                    }
                                                    "needsFields" -> {
                                                        requestForm("Connect ${app.text("name") ?: "app"}", response.array("fields").mapNotNull { field ->
                                                            field.text("name")?.let { it to field.bool("secret") }
                                                        }) { values ->
                                                            runAction("composio-fields", {
                                                                session.call("composioConnectFields", buildJsonObject {
                                                                    put("toolkit", app.text("slug").orEmpty())
                                                                    put("mode", response.text("mode").orEmpty())
                                                                    put("fields", buildJsonObject { values.forEach { (name, value) -> put(name, value) } })
                                                                }).getOrThrow()
                                                            })
                                                        }
                                                    }
                                                    else -> error("Composio returned an invalid connection plan")
                                                }
                                            }, reload = false)
                                        })
                                    },
                                )
                            }
                        }
                    }
                    "Vault" -> {
                        item {
                            SectionCard {
                                Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Credential storage", color = colors.text, style = CodyncType.body.copy(fontWeight = FontWeight.SemiBold))
                                    Text(credentialStatus?.text("provider") ?: "Host secure credential store", color = colors.secondary, style = CodyncType.footnote)
                                    Text(
                                        if (credentialStatus?.bool("onePasswordConnected") == true) "1Password is connected on the host."
                                        else "1Password is not connected on the host.",
                                        color = colors.secondary,
                                        style = CodyncType.footnote,
                                    )
                                    SecondaryButton("Configure 1Password", onClick = {
                                        requestForm("1Password service account", listOf("token" to true)) { values ->
                                            runAction("vault-token", {
                                                session.call("credentialSetOnePassword", buildJsonObject { put("token", values["token"].orEmpty()) }).getOrThrow()
                                            })
                                        }
                                    })
                                }
                            }
                        }
                        item {
                            SectionCard {
                                SettingsRow(
                                    title = "Saved logins",
                                    subtitle = "Stored in the host vault and available to sign-in requests.",
                                    icon = CodyncIcons.Lock,
                                    trailing = { SecondaryButton("Add", onClick = {
                                        requestForm("Save login", listOf("site" to false, "username" to false, "password" to true)) { values ->
                                            runAction("save-login", {
                                                session.call("credentialSaveLogin", buildJsonObject {
                                                    put("site", values["site"].orEmpty())
                                                    put("username", values["username"].orEmpty())
                                                    put("password", values["password"].orEmpty())
                                                }).getOrThrow()
                                            })
                                        }
                                    }) },
                                    showChevron = false,
                                )
                            }
                        }
                        items(savedLogins, key = { "login-${it.text("id")}" }) { login ->
                            SectionCard {
                                SettingsRow(
                                    title = login.text("site") ?: "Saved login",
                                    subtitle = login.text("username").orEmpty(),
                                    icon = CodyncIcons.Lock,
                                    trailing = { DestructiveButton("Remove", onClick = {
                                        runAction(login.text("id").orEmpty(), {
                                            session.call("credentialRemoveLogin", buildJsonObject { put("id", login.text("id").orEmpty()) }).getOrThrow()
                                        })
                                    }) },
                                    showChevron = false,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    connectorToInstall?.let { item ->
        val options = item.array("options")
        val option = selectedOption ?: options.firstOrNull()
        AlertDialog(
            onDismissRequest = { connectorToInstall = null },
            title = { Text(item.text("title") ?: "Install connector") },
            text = {
                Column(Modifier.fillMaxWidth().height(420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(item.text("description").orEmpty(), color = colors.secondary, style = CodyncType.footnote)
                    if (options.size > 1) {
                        Text("Install method", color = colors.tertiary, style = CodyncType.caption)
                        options.forEach { candidate ->
                            FilterChip(
                                candidate.text("label") ?: candidate.text("kind") ?: "Option",
                                selected = candidate == option,
                                onClick = { selectedOption = candidate; formValues.clear() },
                            )
                        }
                    }
                    option?.array("inputs")?.forEach { field ->
                        val name = field.text("name") ?: return@forEach
                        ExtensionField(
                            label = name,
                            value = formValues[name].orEmpty(),
                            secret = field.bool("secret"),
                            placeholder = field.text("default") ?: field.text("placeholder") ?: "",
                            onValueChange = { formValues[name] = it },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val selected = option ?: return@TextButton
                    val inputs = buildJsonObject {
                        selected.array("inputs").forEach { field ->
                            val name = field.text("name") ?: return@forEach
                            val value = formValues[name].orEmpty().ifBlank { field.text("default").orEmpty() }
                            if (value.isNotBlank()) put(name, value)
                        }
                    }
                    runAction(item.text("name").orEmpty(), {
                        session.call("installConnector", buildJsonObject {
                            put("registryName", item.text("name").orEmpty())
                            put("option", selected.text("id").orEmpty())
                            put("inputs", inputs)
                        }).getOrThrow()
                    })
                    connectorToInstall = null
                }) { Text(if (busyId == item.text("name")) "Installing…" else "Install") }
            },
            dismissButton = { TextButton(onClick = { connectorToInstall = null }) { Text("Cancel") } },
        )
    }

    if (skillToRead != null && skillInstructions != null) {
        AlertDialog(
            onDismissRequest = { skillToRead = null; skillInstructions = null },
            title = { Text(skillToRead?.text("name") ?: skillToRead?.text("source") ?: "Skill") },
            text = {
                Column(Modifier.fillMaxWidth().height(420.dp).verticalScroll(rememberScrollState())) {
                    Text(skillInstructions.orEmpty(), color = colors.secondary, style = CodyncType.footnote)
                }
            },
            confirmButton = { TextButton(onClick = { skillToRead = null; skillInstructions = null }) { Text("Done") } },
        )
    }

    formTitle?.let { title ->
        AlertDialog(
            onDismissRequest = { formTitle = null },
            title = { Text(title) },
            text = {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    formFields.forEach { (name, secret) ->
                        ExtensionField(name, formValues[name].orEmpty(), secret, "", { formValues[name] = it })
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val values = formValues.toMap()
                    formTitle = null
                    formAction(values)
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { formTitle = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ExtensionSearch(value: String, placeholder: String, onValueChange: (String) -> Unit) {
    val colors = CodyncTheme.colors
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = colors.text, fontSize = 15.sp),
        cursorBrush = SolidColor(colors.text),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp)).background(colors.surface).padding(horizontal = 14.dp, vertical = 12.dp),
        decorationBox = { inner ->
            Column {
                if (value.isEmpty()) Text(placeholder, color = colors.tertiary, style = CodyncType.body)
                inner()
            }
        },
    )
}

@Composable
private fun ExtensionField(
    label: String,
    value: String,
    secret: Boolean,
    placeholder: String,
    onValueChange: (String) -> Unit,
) {
    val colors = CodyncTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = colors.secondary, style = CodyncType.caption)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            textStyle = TextStyle(color = colors.text, fontSize = 14.sp),
            cursorBrush = SolidColor(colors.text),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.surface)
                .padding(horizontal = 11.dp, vertical = 10.dp),
            decorationBox = { inner ->
                Column {
                    if (value.isEmpty() && placeholder.isNotEmpty()) Text(placeholder, color = colors.tertiary, style = CodyncType.footnote)
                    inner()
                }
            },
        )
    }
}

@Composable
private fun ExtensionLabel(title: String, count: Int) {
    val colors = CodyncTheme.colors
    Text("$title · $count", modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp), color = colors.tertiary, style = CodyncType.caption)
}

private fun JsonObject.array(key: String): List<JsonObject> = this[key]?.let {
    runCatching { it.jsonArray.mapNotNull { row -> runCatching { row.jsonObject }.getOrNull() } }.getOrNull()
}.orEmpty()

private fun JsonObject.text(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

private fun JsonObject.bool(key: String): Boolean = this[key]?.jsonPrimitive?.contentOrNull == "true"
