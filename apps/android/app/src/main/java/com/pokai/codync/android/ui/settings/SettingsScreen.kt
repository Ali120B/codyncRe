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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pokai.codync.android.BuildConfig
import com.pokai.codync.android.fake.FakeHostState
import com.pokai.codync.android.fake.FakeTransport
import com.pokai.codync.android.ui.LargeHeader
import com.pokai.codync.android.ui.SectionLabel
import com.pokai.codync.android.ui.ThemeMode
import com.pokai.codync.android.ui.relativeShort
import com.pokai.codync.design.CodyncIcon
import com.pokai.codync.design.FilterChip
import com.pokai.codync.design.CodyncIcons
import com.pokai.codync.design.CodyncSwitch
import com.pokai.codync.design.DividerLine
import com.pokai.codync.design.GlassIconButton
import com.pokai.codync.design.Pill
import com.pokai.codync.design.SectionCard
import com.pokai.codync.design.SettingsRow
import com.pokai.codync.design.StatusDot
import com.pokai.codync.design.hasAgsGlass
import com.pokai.codync.design.theme.CodyncTheme
import com.pokai.codync.design.theme.CodyncType

@Composable
fun SettingsScreen(
    fake: FakeHostState,
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
    onOpenGlassLab: () -> Unit,
    onOpenFakeHost: () -> Unit,
    onOpenSearch: () -> Unit,
) {
    val colors = CodyncTheme.colors
    val context = LocalContext.current
    var push by rememberSaveable { mutableStateOf(true) }
    var replies by rememberSaveable { mutableStateOf(true) }
    var needsYou by rememberSaveable { mutableStateOf(true) }
    var analytics by rememberSaveable { mutableStateOf(false) }
    var haptics by rememberSaveable { mutableStateOf(true) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        LargeHeader(
            title = "Settings",
            subtitle = "Codync 3.0.0 · Android M0",
            trailing = {
                GlassIconButton(CodyncIcons.Search, "Search settings", onClick = onOpenSearch)
            },
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 118.dp),
        ) {
            item {
                SectionCard(Modifier.padding(horizontal = 16.dp)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(colors.bubbleUser),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("A", color = colors.text, style = CodyncType.headline)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Local only", color = colors.text, style = CodyncType.body.copy(fontWeight = FontWeight.SemiBold))
                            Text(
                                "Not signed in. Pairing works without an account.",
                                color = colors.secondary,
                                style = CodyncType.footnote,
                            )
                        }
                        Pill("Optional", fill = colors.bubbleAgent, color = colors.secondary)
                    }
                    DividerLine(inset = 16.dp)
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                        CodyncIcon(CodyncIcons.Lock, tint = colors.added, size = 16.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Google sign-in arrives with accounts (M2)", color = colors.secondary, style = CodyncType.footnote)
                    }
                }
            }

            item { SectionLabel("Computers & devices", trailing = "${fake.computers.size}") }
            item {
                SectionCard(Modifier.padding(horizontal = 16.dp)) {
                    fake.computers.forEachIndexed { index, computer ->
                        if (index > 0) DividerLine(inset = 54.dp)
                        SettingsRow(
                            title = computer.name,
                            subtitle = "${computer.platform} · ${computer.version} · ${relativeShort(computer.lastSeen)}",
                            icon = CodyncIcons.Computer,
                            showChevron = false,
                            trailing = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    StatusDot(
                                        color = when (computer.transport) {
                                            FakeTransport.Direct -> colors.added
                                            FakeTransport.Relay -> colors.warning
                                            FakeTransport.Offline -> colors.tertiary
                                        },
                                    )
                                    Spacer(Modifier.width(5.dp))
                                    Text(
                                        when (computer.transport) {
                                            FakeTransport.Direct -> "Direct"
                                            FakeTransport.Relay -> "Relay"
                                            FakeTransport.Offline -> "Offline"
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
                        title = "Push notifications",
                        subtitle = "Working, needs you and finished alerts",
                        icon = CodyncIcons.Notifications,
                        showChevron = false,
                        trailing = { CodyncSwitch(push, onCheckedChange = { push = it }) },
                        onClick = { push = !push },
                    )
                    DividerLine(inset = 54.dp)
                    SettingsRow(
                        title = "Bot replies",
                        subtitle = "Every final reply, not only mentions",
                        icon = CodyncIcons.Chat,
                        showChevron = false,
                        trailing = { CodyncSwitch(replies, onCheckedChange = { replies = it }) },
                        onClick = { replies = !replies },
                    )
                    DividerLine(inset = 54.dp)
                    SettingsRow(
                        title = "Needs You",
                        subtitle = "Approvals and waiting bots",
                        icon = CodyncIcons.Fingerprint,
                        showChevron = false,
                        trailing = { CodyncSwitch(needsYou, onCheckedChange = { needsYou = it }) },
                        onClick = { needsYou = !needsYou },
                    )
                    DividerLine(inset = 54.dp)
                    SettingsRow(
                        title = "Haptics",
                        subtitle = "Taps, approvals and the tab bar",
                        icon = CodyncIcons.Tune,
                        showChevron = false,
                        trailing = { CodyncSwitch(haptics, onCheckedChange = { haptics = it }) },
                        onClick = { haptics = !haptics },
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

            item { SectionLabel("Privacy") }
            item {
                SectionCard(Modifier.padding(horizontal = 16.dp)) {
                    SettingsRow(
                        title = "Analytics",
                        subtitle = "Anonymous usage and crash reports; no prompts or message text",
                        icon = CodyncIcons.Science,
                        showChevron = false,
                        trailing = { CodyncSwitch(analytics, onCheckedChange = { analytics = it }) },
                        onClick = { analytics = !analytics },
                    )
                    DividerLine(inset = 54.dp)
                    SettingsRow(
                        title = "Crash reports",
                        subtitle = "Redacted stack traces, no keys or tokens",
                        icon = CodyncIcons.Warning,
                        showChevron = false,
                        trailing = { Text("On", color = colors.tertiary, style = CodyncType.footnote) },
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
                        subtitle = "Codync 3.0.0 (1) · host 3.0.0",
                        icon = CodyncIcons.Info,
                        showChevron = false,
                    )
                    DividerLine(inset = 54.dp)
                    SettingsRow(
                        title = "Host compatibility",
                        subtitle = "3.0.0 · Android 12+",
                        icon = CodyncIcons.Cloud,
                        showChevron = false,
                        trailing = { Pill("OK", fill = colors.added.copy(alpha = 0.12f), color = colors.added) },
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
