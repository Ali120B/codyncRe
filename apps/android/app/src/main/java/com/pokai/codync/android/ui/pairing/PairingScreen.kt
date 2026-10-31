package com.pokai.codync.android.ui.pairing

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokai.codync.android.ui.HeaderIconButton
import com.pokai.codync.data.SessionStore
import com.pokai.codync.design.CodyncIcons
import com.pokai.codync.design.PrimaryButton
import com.pokai.codync.design.SecondaryButton
import com.pokai.codync.design.SectionCard
import com.pokai.codync.design.theme.CodyncTheme
import com.pokai.codync.design.theme.CodyncType
import com.pokai.codync.transport.PairUrl

@Composable
fun PairingScreen(
    store: SessionStore,
    deviceName: String,
    initialLink: String? = null,
    onDeepLinkConsumed: () -> Unit = {},
    onBack: () -> Unit,
    onPaired: () -> Unit,
) {
    val colors = CodyncTheme.colors
    val pairingMessage by store.pairingMessage().collectAsState(initial = null)
    var paste by remember { mutableStateOf("") }
    var scanning by remember { mutableStateOf(false) }
    var localStatus by remember { mutableStateOf<String?>(null) }
    var connecting by remember { mutableStateOf(false) }
    val status = pairingMessage ?: localStatus

    LaunchedEffect(pairingMessage, localStatus) {
        if (pairingMessage != null || localStatus != null) connecting = false
    }

    fun submit(raw: String) {
        paste = raw
        scanning = false
        val offer = PairUrl.parse(raw)
        if (offer == null) {
            localStatus = "Couldn't read that link. Copy it again from the computer."
            connecting = false
        } else {
            localStatus = null
            connecting = true
            store.pairFromPaste(raw, deviceName)
        }
    }

    fun leave(next: () -> Unit) {
        // A green/red Done button must not follow people back to this screen later.
        localStatus = null
        store.clearPairingMessage()
        next()
    }

    LaunchedEffect(Unit) {
        // Drop last visit's result before showing a fresh pairing screen.
        store.clearPairingMessage()
    }

    LaunchedEffect(initialLink) {
        initialLink?.takeIf { PairUrl.parse(it) != null }?.let { link ->
            submit(link)
            onDeepLinkConsumed()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderIconButton(CodyncIcons.Back, "Back", onClick = { leave(onBack) })
            Column(Modifier.weight(1f)) {
                Text("Pair a computer", color = colors.text, style = CodyncType.title)
                Text("Scan the QR or paste a Codync link", color = colors.secondary, style = CodyncType.footnote)
            }
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            AnimatedContent(
                targetState = scanning,
                transitionSpec = {
                    (fadeIn() + slideInVertically { it / 10 }) togetherWith
                        (fadeOut() + slideOutVertically { -it / 12 })
                },
                label = "pairMode",
            ) { scan ->
                if (scan) {
                    QrScannerPanel(
                        onCode = ::submit,
                        onClose = { scanning = false },
                    )
                } else {
                    SectionCard {
                        Column(Modifier.padding(16.dp)) {
                            Text("PAIRING LINK", color = colors.tertiary, style = CodyncType.caption)
                            Spacer(Modifier.height(8.dp))
                            BoxField(
                                value = paste,
                                onValueChange = {
                                    if (!connecting) {
                                        paste = it
                                        localStatus = null
                                    }
                                },
                                placeholder = "codync://pair?…",
                            )
                            Spacer(Modifier.height(12.dp))
                            PrimaryButton(
                                text = if (connecting) "Connecting…" else "Continue",
                                onClick = { submit(paste) },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = paste.isNotBlank() && !connecting,
                                leading = CodyncIcons.QrCode,
                            )
                            Spacer(Modifier.height(8.dp))
                            SecondaryButton(
                                text = "Scan QR",
                                onClick = { if (!connecting) scanning = true },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !connecting,
                                leading = CodyncIcons.Camera,
                            )
                        }
                    }
                }
            }
            val statusText = if (connecting) "Connecting…" else status
            if (statusText != null) {
                Spacer(Modifier.height(14.dp))
                Text(statusText, color = colors.secondary, style = CodyncType.body)
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "Pairs over LAN or the Cloudflare relay with Codync-Sig. One device — no multi-account.",
                color = colors.tertiary,
                style = CodyncType.caption,
            )
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(CircleShape)
                    .background(colors.bubbleUser)
                    .clickable(enabled = !connecting) { leave(onPaired) },
                contentAlignment = Alignment.Center,
            ) {
                Text("Done", color = Color.White, style = CodyncType.body.copy(fontWeight = FontWeight.Medium))
            }
        }
    }
}

@Composable
private fun BoxField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
) {
    val colors = CodyncTheme.colors
    androidx.compose.foundation.layout.Box(
        Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .padding(14.dp),
    ) {
        if (value.isEmpty()) {
            Text(placeholder, color = colors.tertiary, style = CodyncType.body)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxSize(),
            textStyle = TextStyle(
                color = colors.text,
                fontSize = 15.sp,
                fontFamily = CodyncType.body.fontFamily,
                fontWeight = FontWeight.Medium,
            ),
            cursorBrush = SolidColor(colors.text),
        )
    }
}
