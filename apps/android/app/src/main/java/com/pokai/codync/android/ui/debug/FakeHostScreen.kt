package com.pokai.codync.android.ui.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pokai.codync.android.BuildConfig
import com.pokai.codync.android.fake.FakeHostState
import com.pokai.codync.android.fake.FakeTransport
import com.pokai.codync.android.ui.HeaderIconButton
import com.pokai.codync.android.ui.SectionLabel
import com.pokai.codync.android.ui.relativeShort
import com.pokai.codync.design.CodyncIcon
import com.pokai.codync.design.CodyncIcons
import com.pokai.codync.design.FilterChip
import com.pokai.codync.design.Pill
import com.pokai.codync.design.PrimaryButton
import com.pokai.codync.design.SecondaryButton
import com.pokai.codync.design.SectionCard
import com.pokai.codync.design.SettingsRow
import com.pokai.codync.design.StatusDot
import com.pokai.codync.design.theme.CodyncTheme
import com.pokai.codync.design.theme.CodyncType

@Composable
fun FakeHostScreen(
    fake: FakeHostState,
    onBack: () -> Unit,
    onOpenGlassLab: () -> Unit,
) {
    val colors = CodyncTheme.colors
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderIconButton(CodyncIcons.Back, "Back", onBack)
            Column(Modifier.weight(1f)) {
                Text("Fake host", color = colors.text, style = CodyncType.headline)
                Text("M0 fixture · not a real connection", color = colors.secondary, style = CodyncType.caption)
            }
            Pill(
                "Dev",
                fill = colors.warning.copy(alpha = 0.13f),
                color = colors.attention,
            )
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            modifier = Modifier.weight(1f),
        ) {
            item {
                SectionCard {
                    Column(Modifier.padding(15.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .height(28.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.warning.copy(alpha = 0.14f))
                                    .padding(horizontal = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("FIXTURE", color = colors.attention, style = CodyncType.caption2.copy(fontWeight = FontWeight.Bold))
                            }
                            Spacer(Modifier.width(8.dp))
                            Text("No network requests are made.", color = colors.text, style = CodyncType.footnote)
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "The roster, chats, approvals and counts come from FakeHostState.kt. M1 replaces this screen with the real direct/relay transport and QR pairing.",
                            color = colors.secondary,
                            style = CodyncType.footnote,
                        )
                    }
                }
            }

            item { SectionLabel("Transport") }
            item {
                SectionCard {
                    Column(Modifier.padding(15.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            FakeTransport.entries.forEach { option ->
                                FilterChip(option.label, fake.transport == option, onClick = { fake.useTransport(option) })
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusDot(
                                color = when (fake.transport) {
                                    FakeTransport.Direct -> colors.added
                                    FakeTransport.Relay -> colors.warning
                                    FakeTransport.Offline -> colors.tertiary
                                },
                            )
                            Spacer(Modifier.width(7.dp))
                            Text(fake.lastEvent, color = colors.secondary, style = CodyncType.footnote)
                        }
                    }
                }
            }

            item { SectionLabel("Host state") }
            item {
                SectionCard {
                    SettingsRow("Version", subtitle = "3.0.3 · protocol v1", icon = CodyncIcons.Cloud, showChevron = false, trailing = { Text("OK", color = colors.added, style = CodyncType.caption) })
                    com.pokai.codync.design.DividerLine(inset = 54.dp)
                    SettingsRow("Revision", subtitle = "resume point after reconnect", icon = CodyncIcons.Sync, showChevron = false, trailing = { Text("${fake.rev}", color = colors.tertiary, style = CodyncType.caption) })
                    com.pokai.codync.design.DividerLine(inset = 54.dp)
                    SettingsRow("Computer", subtitle = "cmp_macbook · Ali's MacBook Pro", icon = CodyncIcons.Computer, showChevron = false, trailing = { Text("Direct", color = colors.tertiary, style = CodyncType.caption) })
                    com.pokai.codync.design.DividerLine(inset = 54.dp)
                    SettingsRow("Connected", subtitle = relativeShort(fake.connectedAt), icon = CodyncIcons.CloudDone, showChevron = false)
                    com.pokai.codync.design.DividerLine(inset = 54.dp)
                    SettingsRow("Build", subtitle = "${BuildConfig.FLAVOR} · ${BuildConfig.VERSION_NAME}", icon = CodyncIcons.Terminal, showChevron = false)
                }
            }

            item { SectionLabel("Actions") }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton("Simulate a host event", onClick = { fake.simulateEvent() }, modifier = Modifier.fillMaxWidth(), leading = CodyncIcons.Bolt)
                    SecondaryButton("Reconnect direct", onClick = { fake.useTransport(FakeTransport.Direct) }, modifier = Modifier.fillMaxWidth(), leading = CodyncIcons.Refresh)
                    SecondaryButton("Preview offline cache", onClick = { fake.useTransport(FakeTransport.Offline) }, modifier = Modifier.fillMaxWidth(), leading = CodyncIcons.CloudOff)
                    SecondaryButton("Open glass lab", onClick = onOpenGlassLab, modifier = Modifier.fillMaxWidth(), leading = CodyncIcons.Palette)
                }
            }

            item {
                Text(
                    "M1 will show a real host here: QR pairing, Codync-Sig, encrypted channel, resume from rev, and reconnect after process death.",
                    color = colors.tertiary,
                    style = CodyncType.footnote,
                    modifier = Modifier.padding(vertical = 18.dp),
                )
            }
        }
    }
}
