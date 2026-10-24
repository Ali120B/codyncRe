@file:OptIn(ExperimentalLayoutApi::class)

package com.pokai.codync.android.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pokai.codync.android.BuildConfig
import com.pokai.codync.android.AndroidAccountSession
import com.pokai.codync.android.AndroidAccountState
import com.pokai.codync.android.fake.FakeHostState
import com.pokai.codync.android.fake.FakeTransport
import com.pokai.codync.android.ui.AppPreferences
import com.pokai.codync.android.ui.LargeHeader
import com.pokai.codync.android.ui.SectionLabel
import com.pokai.codync.android.ui.ThemeMode
import com.pokai.codync.android.ui.relativeShort
import com.pokai.codync.data.SessionStore
import com.clerk.api.Clerk
import com.pokai.codync.design.CodyncIcon
import com.pokai.codync.design.FilterChip
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

@Composable
fun SettingsScreen(
    fake: FakeHostState,
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
    prefs: AppPreferences,
    session: SessionStore,
    account: AndroidAccountSession,
    onOpenGlassLab: () -> Unit,
    onOpenFakeHost: () -> Unit,
    onOpenPairing: () -> Unit = {},
    onOpenWorkbench: () -> Unit = {},
) {
    val colors = CodyncTheme.colors
    val context = LocalContext.current
    val computers by session.computers().collectAsState(initial = emptyList())
    val connection by session.connection().collectAsState(initial = com.pokai.codync.transport.ConnectionState())
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        LargeHeader(
            title = "Settings",
            subtitle = "Codync ${BuildConfig.VERSION_NAME}",
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 118.dp),
        ) {
            item { AccountCard(account) }

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
                        val selected = connection.computer?.id == computer.id
                        SettingsRow(
                            title = computer.name,
                            subtitle = if (selected) listOfNotNull(
                                connection.hello?.os,
                                connection.hello?.version,
                                connection.connectedAt?.let(::relativeShort),
                            ).joinToString(" · ").ifBlank { "Selected computer" } else "Paired with this device",
                            icon = CodyncIcons.Computer,
                            onClick = { if (!selected) session.connect(computer) },
                            showChevron = !selected,
                            trailing = {
                                if (!selected) {
                                    Pill("Paired", fill = colors.surface, color = colors.secondary)
                                } else Row(verticalAlignment = Alignment.CenterVertically) {
                                    StatusDot(
                                        color = when (connection.kind) {
                                            com.pokai.codync.transport.TransportKind.Direct -> colors.added
                                            com.pokai.codync.transport.TransportKind.Relay -> colors.added
                                            com.pokai.codync.transport.TransportKind.Offline -> colors.tertiary
                                        },
                                    )
                                    Spacer(Modifier.width(5.dp))
                                    Text(
                                        when (connection.kind) {
                                            com.pokai.codync.transport.TransportKind.Direct -> "Direct"
                                            com.pokai.codync.transport.TransportKind.Relay -> "Relay"
                                            com.pokai.codync.transport.TransportKind.Offline -> "Offline"
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
                        subtitle = "FCM setup is required before this device can receive alerts while Codync is closed.",
                        icon = CodyncIcons.Notifications,
                        showChevron = false,
                        trailing = { Pill("Setup needed", fill = colors.warning.copy(alpha = 0.12f), color = colors.warning) },
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
                            title = "Fake host",
                            subtitle = fake.lastEvent,
                            icon = CodyncIcons.Science,
                            onClick = onOpenFakeHost,
                            trailing = {
                                Text(
                                    when (fake.transport) {
                                        FakeTransport.Direct -> "Direct"
                                        FakeTransport.Relay -> "Relay"
                                        FakeTransport.Offline -> "Offline"
                                    },
                                    color = colors.tertiary,
                                    style = CodyncType.caption,
                                )
                            },
                        )
                        DividerLine(inset = 54.dp)
                        SettingsRow(
                            title = "Glass lab",
                            subtitle = if (hasAgsGlass) "AGSL refraction · Android ${Build.VERSION.RELEASE}" else "Frosted fallback · Android ${Build.VERSION.RELEASE}",
                            icon = CodyncIcons.Palette,
                            onClick = onOpenGlassLab,
                        )
                        DividerLine(inset = 54.dp)
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
    }
}

@Composable
private fun AccountCard(account: AndroidAccountSession) {
    val state by account.state.collectAsState()
    if (state.configured) {
        ConfiguredAccountCard(account, state)
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
private fun ConfiguredAccountCard(account: AndroidAccountSession, state: AndroidAccountState) {
    val colors = CodyncTheme.colors
    val scope = rememberCoroutineScope()
    val signedInUser by Clerk.userFlow.collectAsState(initial = null)
    val currentUser = signedInUser
    LaunchedEffect(currentUser?.id) {
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
