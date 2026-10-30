@file:OptIn(ExperimentalLayoutApi::class)

package com.pokai.codync.android.ui.settings

import android.content.Intent
import android.Manifest
import android.net.Uri
import android.os.Build
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.google.firebase.FirebaseApp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokai.codync.android.BuildConfig
import com.pokai.codync.android.AndroidAccountSession
import com.pokai.codync.android.AndroidAccountState
import com.pokai.codync.android.ui.AppPreferences
import com.pokai.codync.android.ui.LargeHeader
import com.pokai.codync.android.ui.SectionLabel
import com.pokai.codync.android.ui.ThemeMode
import com.pokai.codync.android.ui.relativeShort
import com.pokai.codync.data.SessionStore
import com.clerk.api.Clerk
import com.pokai.codync.design.CodyncIcon
import com.pokai.codync.design.CodyncSwitch
import com.pokai.codync.design.FilterChip
import com.pokai.codync.design.GlassIconButton
import com.pokai.codync.design.PrimaryButton
import com.pokai.codync.design.CodyncIcons
import com.pokai.codync.design.DividerLine
import com.pokai.codync.design.Pill
import com.pokai.codync.design.SectionCard
import com.pokai.codync.design.SettingsRow
import com.pokai.codync.design.StatusDot
import com.pokai.codync.design.SecondaryButton
import com.pokai.codync.design.hasAgsGlass
import com.pokai.codync.design.theme.CodyncTheme
import com.pokai.codync.design.theme.CodyncType
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
    prefs: AppPreferences,
    session: SessionStore,
    account: AndroidAccountSession,
    active: Boolean = true,
    onOpenGlassLab: () -> Unit,
    onOpenPairing: () -> Unit = {},
    onOpenWorkbench: () -> Unit = {},
) {
    val colors = CodyncTheme.colors
    val context = LocalContext.current
    var showAgents by remember { mutableStateOf(false) }
    val computers by session.computers().collectAsState(initial = emptyList())
    val connections by session.computerConnections().collectAsState(initial = emptyMap())
    val connection by session.connectionStatus().collectAsState(initial = com.pokai.codync.transport.ConnectionState())
    var pushEnabled by remember(prefs) { mutableStateOf(prefs.push) }
    val pushPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pushEnabled = granted
        prefs.push = granted
    }
    val firebaseConfigured = remember { FirebaseApp.getApps(context).isNotEmpty() }
    val scope = rememberCoroutineScope()
    var hostWorkspaces by remember { mutableStateOf(prefs.workspaces.sorted()) }
    val connected = connection.computer != null && connection.hello != null
    LaunchedEffect(connected, active) {
        if (!active || !connected) return@LaunchedEffect
        session.call("workspaces", buildJsonObject {}).onSuccess { el ->
            val list = runCatching { el.jsonObject["workspaces"]?.jsonArray?.map { it.jsonPrimitive.content } }.getOrNull()
            if (list != null) {
                hostWorkspaces = list
                prefs.workspaces = list.toSet()
            }
        }
    }

    LaunchedEffect(firebaseConfigured, active, pushEnabled) {
        if (firebaseConfigured && active && pushEnabled && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            pushPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun setPushEnabled(enabled: Boolean) {
        if (!enabled) {
            pushEnabled = false
            prefs.push = false
        } else if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            pushPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            pushEnabled = true
            prefs.push = true
        }
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        LargeHeader(
            title = "Settings",
            subtitle = "Codync ${BuildConfig.VERSION_NAME}",
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 118.dp),
        ) {
            item { AccountCard(account, active) }

            item {
                SectionCard(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    SettingsRow(
                        title = "Open Workbench",
                        subtitle = "Open each bot or group for its details, memory and routines",
                        icon = CodyncIcons.Build,
                        onClick = onOpenWorkbench,
                    )
                }
            }

            item { SectionLabel("Computers & devices", trailing = "${computers.size}") }
            item {
                SectionCard(Modifier.padding(horizontal = 16.dp)) {
                    SettingsRow(
                        title = "Pair a computer",
                        subtitle = "Paste a Codync QR link from the desktop app",
                        icon = CodyncIcons.QrCode,
                        onClick = onOpenPairing,
                    )
                    DividerLine(inset = 54.dp)
                    computers.forEachIndexed { index, computer ->
                        if (index > 0) DividerLine(inset = 54.dp)
                        // Every paired computer keeps its own socket, so each row shows its own state.
                        val state = connections[computer.id]
                        val online = state?.hello != null
                        SettingsRow(
                            title = computer.name,
                            subtitle = if (online) listOfNotNull(
                                state?.hello?.os,
                                state?.hello?.version,
                                state?.connectedAt?.let(::relativeShort),
                            ).joinToString(" · ").ifBlank { "Connected" } else "Paired with this device",
                            icon = CodyncIcons.Computer,
                            showChevron = false,
                            trailing = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    StatusDot(
                                        color = when (state?.kind) {
                                            com.pokai.codync.transport.TransportKind.Direct,
                                            com.pokai.codync.transport.TransportKind.Relay -> colors.added
                                            else -> colors.tertiary
                                        },
                                    )
                                    Spacer(Modifier.width(5.dp))
                                    Text(
                                        when (state?.kind) {
                                            com.pokai.codync.transport.TransportKind.Direct -> "Direct"
                                            com.pokai.codync.transport.TransportKind.Relay -> "Relay"
                                            else -> "Offline"
                                        },
                                        color = colors.tertiary,
                                        style = CodyncType.caption,
                                    )
                                }
                            },
                        )
                    }
                }
            }

            item { SectionLabel("Notifications") }
            item {
                SectionCard(Modifier.padding(horizontal = 16.dp)) {
                    SettingsRow(
                        title = "Push delivery",
                        subtitle = when {
                            !firebaseConfigured -> "Add this flavor's Codync Firebase config to enable encrypted alerts."
                            !pushEnabled -> "Push alerts are turned off on this device."
                            Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED -> "Allow notifications in Android settings to see alerts while Codync is closed."
                            else -> "Host-sealed alerts use FCM; the relay also needs its Firebase service account configured."
                        },
                        icon = CodyncIcons.Notifications,
                        showChevron = false,
                        trailing = { CodyncSwitch(pushEnabled, onCheckedChange = ::setPushEnabled, enabled = firebaseConfigured) },
                        onClick = { setPushEnabled(!pushEnabled) },
                    )
                }
            }

            item { SectionLabel("Appearance") }
            item {
                SectionCard(Modifier.padding(horizontal = 16.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CodyncIcon(CodyncIcons.Contrast, tint = colors.text, size = 18.dp)
                            Spacer(Modifier.width(9.dp))
                            Text("Theme", color = colors.text, style = CodyncType.body)
                            Spacer(Modifier.weight(1f))
                            Text(themeMode.label, color = colors.tertiary, style = CodyncType.footnote)
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            ThemeMode.entries.forEach { mode ->
                                FilterChip(
                                    text = mode.label,
                                    selected = themeMode == mode,
                                    onClick = { onThemeMode(mode) },
                                )
                            }
                        }
                    }
                    DividerLine(inset = 14.dp)
                    SettingsRow(
                        title = "Text size",
                        subtitle = "Follows the Android system setting",
                        icon = CodyncIcons.Tune,
                        showChevron = false,
                        trailing = { Text("System", color = colors.tertiary, style = CodyncType.footnote) },
                    )
                    DividerLine(inset = 54.dp)
                    SettingsRow(
                        title = "Reduced motion",
                        subtitle = "Follows the system accessibility setting",
                        icon = CodyncIcons.Speed,
                        showChevron = false,
                        trailing = { Text("System", color = colors.tertiary, style = CodyncType.footnote) },
                    )
                }
            }

            item { SectionLabel("Workspaces", trailing = "${hostWorkspaces.size}") }
            item {
                SectionCard(Modifier.padding(horizontal = 16.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (hostWorkspaces.isEmpty()) {
                            Text(if (connection.computer == null) "Connect to a computer to see its saved workspaces." else "No saved workspaces yet. Add a folder path below.", color = colors.secondary, style = CodyncType.footnote)
                        } else {
                            hostWorkspaces.sorted().forEach { path ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(path.substringAfterLast('/').ifBlank { path }, color = colors.text, style = CodyncType.body)
                                        Text(path, color = colors.tertiary, style = CodyncType.caption, maxLines = 1)
                                    }
                                    GlassIconButton(CodyncIcons.Close, "Remove workspace", onClick = {
                                        scope.launch {
                                            session.call("removeWorkspace", buildJsonObject { put("path", path) })
                                            hostWorkspaces = hostWorkspaces - path
                                            prefs.workspaces = hostWorkspaces.toSet()
                                        }
                                    }, size = 30.dp, iconSize = 16.dp)
                                }
                            }
                        }
                        var newPath by remember { mutableStateOf("") }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            BasicTextField(
                                value = newPath,
                                onValueChange = { newPath = it },
                                modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(colors.surface).padding(10.dp),
                                textStyle = TextStyle(color = colors.text, fontSize = 14.sp),
                                cursorBrush = SolidColor(colors.text),
                                singleLine = true,
                                decorationBox = { inner -> if (newPath.isEmpty()) Text("Folder path", color = colors.tertiary, style = CodyncType.footnote) ; inner() },
                            )
                            PrimaryButton("Add", enabled = newPath.isNotBlank(), onClick = {
                                val clean = newPath.trim()
                                scope.launch {
                                    session.call("addWorkspace", buildJsonObject { put("path", clean) }).onSuccess { el ->
                                        val list = runCatching { el.jsonObject["workspaces"]?.jsonArray?.map { it.jsonPrimitive.content } }.getOrNull()
                                        if (list != null) hostWorkspaces = list
                                    }
                                    if (!hostWorkspaces.contains(clean)) hostWorkspaces = hostWorkspaces + clean
                                    prefs.workspaces = hostWorkspaces.toSet()
                                    newPath = ""
                                }
                            })
                        }
                        if (connection.computer != null) {
                            Text("Synced with this computer's saved workspaces.", color = colors.tertiary, style = CodyncType.caption)
                        }
                    }
                }
            }

            item { SectionLabel("Agents") }
            item {
                SectionCard(Modifier.padding(horizontal = 16.dp)) {
                    val connection by session.connectionStatus().collectAsState(initial = com.pokai.codync.transport.ConnectionState())
                    val backends = connection.hello?.backends.orEmpty()
                    SettingsRow(
                        title = "Agent setup",
                        subtitle = if (backends.isEmpty()) "Connect to a computer to manage its agents." else "${backends.count { it.available }} of ${backends.size} agents installed",
                        icon = CodyncIcons.Build,
                        onClick = { showAgents = true },
                    )
                }
            }
            item { SectionLabel("Support") }
            item {
                SectionCard(Modifier.padding(horizontal = 16.dp)) {
                    SettingsRow(
                        title = "Email support",
                        subtitle = "support@codync.dev",
                        icon = CodyncIcons.Help,
                        onClick = {
                            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:support@codync.dev")).apply {
                                putExtra(Intent.EXTRA_SUBJECT, "Codync Android ${BuildConfig.VERSION_NAME}")
                            }
                            runCatching { context.startActivity(intent) }
                        },
                    )
                    DividerLine(inset = 54.dp)
                    SettingsRow(
                        title = "About",
                        subtitle = "Codync ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · host ${connection.hello?.version ?: "offline"}",
                        icon = CodyncIcons.Info,
                        showChevron = false,
                    )
                    DividerLine(inset = 54.dp)
                    SettingsRow(
                        title = "Host compatibility",
                        subtitle = "${connection.hello?.version ?: "No host connected"} · Android 12+",
                        icon = CodyncIcons.Cloud,
                        showChevron = false,
                        trailing = {
                            val compatible = connection.kind != com.pokai.codync.transport.TransportKind.Offline &&
                                connection.hello != null
                            Pill(
                                if (compatible) "Online" else "Offline",
                                fill = (if (compatible) colors.added else colors.tertiary).copy(alpha = 0.12f),
                                color = if (compatible) colors.added else colors.tertiary,
                            )
                        },
                    )
                }
            }

            if (BuildConfig.FLAVOR == "dev") {
                item { SectionLabel("Developer", trailing = "M0") }
                item {
                    SectionCard(Modifier.padding(horizontal = 16.dp)) {
                        SettingsRow(
                            title = "API level",
                            subtitle = "minSdk 31 · targetSdk 36 · ${Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"}",
                            icon = CodyncIcons.Phone,
                            showChevron = false,
                        )
                    }
                }
            }

            item {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Codync for Android", color = colors.tertiary, style = CodyncType.caption)
                    Text("One complete v1 · no voice, screen or widgets", color = colors.tertiary, style = CodyncType.caption2)
                }
            }
        }
        val connection by session.connectionStatus().collectAsState(initial = com.pokai.codync.transport.ConnectionState())
        val backends = connection.hello?.backends.orEmpty()
        if (showAgents) {
            Dialog(onDismissRequest = { showAgents = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Box(Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.82f).clip(RoundedCornerShape(26.dp)).background(colors.background)) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Agents", color = colors.text, style = CodyncType.headline, modifier = Modifier.weight(1f))
                            GlassIconButton(CodyncIcons.Close, "Close", onClick = { showAgents = false })
                        }
                        backends.forEach { backend ->
                            SectionCard {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(backend.name, color = colors.text, style = CodyncType.body)
                                    Text(if (backend.available) "Installed" else backend.installHint.ifBlank { "Not installed" }, color = colors.tertiary, style = CodyncType.caption)
                                    if (!backend.available) {
                                        SecondaryButton("Set up on computer", onClick = {
                                            scope.launch {
                                                session.call("agentSetup", buildJsonObject {
                                                    put("backend", backend.id)
                                                    put("step", "install")
                                                    put("method", "")
                                                    put("cols", 80)
                                                    put("rows", 24)
                                                })
                                            }
                                        })
                                        Text("Setup runs on the computer; view the terminal there.", color = colors.tertiary, style = CodyncType.caption)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountCard(account: AndroidAccountSession, active: Boolean) {
    val state by account.state.collectAsState()
    if (state.configured) {
        ConfiguredAccountCard(account, state, active)
    } else {
        val colors = CodyncTheme.colors
        SectionCard(Modifier.padding(horizontal = 16.dp)) {
            SettingsRow(
                title = "Local device",
                subtitle = "QR pairing works without an account. Add a Clerk publishable key to enable cloud computer discovery.",
                icon = CodyncIcons.Lock,
                showChevron = false,
                trailing = { Pill("Local", fill = colors.bubbleAgent, color = colors.secondary) },
            )
        }
    }
}

@Composable
private fun ConfiguredAccountCard(account: AndroidAccountSession, state: AndroidAccountState, active: Boolean) {
    val colors = CodyncTheme.colors
    val scope = rememberCoroutineScope()
    val signedInUser by Clerk.userFlow.collectAsState(initial = null)
    val currentUser = signedInUser
    LaunchedEffect(active, currentUser?.id) {
        if (!active) return@LaunchedEffect
        if (currentUser == null) account.clearAccount() else account.refresh()
    }

    SectionCard(Modifier.padding(horizontal = 16.dp)) {
        if (currentUser == null) {
            SettingsRow(
                title = "Sign in to Codync Cloud",
                subtitle = "Use the account configured for Codync. Local QR pairing stays available.",
                icon = CodyncIcons.Lock,
                onClick = { scope.launch { Clerk.auth.startHostedAuth() } },
            )
        } else {
            SettingsRow(
                title = "Codync Cloud account",
                subtitle = currentUser.firstName?.takeIf { it.isNotBlank() } ?: "Signed in · one account",
                icon = CodyncIcons.Lock,
                showChevron = false,
                trailing = { Pill("Signed in", fill = colors.added.copy(alpha = 0.12f), color = colors.added) },
            )
            SettingsRow(
                title = if (state.loading) "Refreshing account…" else "Refresh cloud computers",
                subtitle = "Find computers claimed by this account",
                icon = CodyncIcons.Refresh,
                onClick = { scope.launch { account.refresh() } },
            )
            SettingsRow(
                title = "Sign out",
                subtitle = "Your local paired computers remain on this device",
                icon = CodyncIcons.Back,
                onClick = { scope.launch { Clerk.auth.signOut(); account.clearAccount() } },
            )
            if (state.computers.isNotEmpty()) {
                DividerLine(inset = 54.dp)
                state.computers.forEach { computer ->
                    val accessLabel = when (computer.access) {
                        "granted" -> "Access granted"
                        "pending" -> "Approval pending"
                        else -> "Request access"
                    }
                    SettingsRow(
                        title = computer.name,
                        subtitle = listOfNotNull(
                            computer.platform,
                            computer.version,
                            if (computer.online) "Online" else "Offline",
                        ).joinToString(" · "),
                        icon = CodyncIcons.Computer,
                        onClick = when (computer.access) {
                            "granted" -> ({ account.connect(computer) })
                            "pending" -> ({})
                            else -> ({ scope.launch { account.requestAccess(computer) } })
                        },
                        trailing = {
                            Pill(
                                accessLabel,
                                fill = if (computer.access == "granted") colors.added.copy(alpha = 0.12f) else colors.surface,
                                color = if (computer.access == "granted") colors.added else colors.secondary,
                            )
                        },
                    )
                }
            }
        }
        state.message?.let { message ->
            Text(message, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = colors.secondary, style = CodyncType.caption)
        }
    }

    state.ticket?.let { ticket ->
        AlertDialog(
            onDismissRequest = { if (!state.connecting) scope.launch { account.cancelAccess(ticket) } },
            title = { Text("Compare this code") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(ticket.code, color = colors.text, style = CodyncType.title)
                    Text(
                        "Check that the computer shows the same six digits. Approve the request on the computer, then continue here.",
                        color = colors.secondary,
                        style = CodyncType.footnote,
                    )
                    state.message?.let { Text(it, color = colors.tertiary, style = CodyncType.caption) }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !state.connecting,
                    onClick = { scope.launch { account.connectIfApproved(ticket) } },
                ) { Text(if (state.connecting) "Waiting…" else "I matched it") }
            },
            dismissButton = {
                TextButton(
                    enabled = !state.connecting,
                    onClick = { scope.launch { account.cancelAccess(ticket) } },
                ) { Text("Cancel") }
            },
        )
    }
}
