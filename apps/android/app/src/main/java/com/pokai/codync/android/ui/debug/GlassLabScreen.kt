package com.pokai.codync.android.ui.debug

import android.os.Build
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokai.codync.android.ui.HeaderIconButton
import com.pokai.codync.android.ui.SectionLabel
import com.pokai.codync.design.CharacterAvatar
import com.pokai.codync.design.CodyncIcon
import com.pokai.codync.design.CodyncIcons
import com.pokai.codync.design.GlassConfig
import com.pokai.codync.design.GlassSurface
import com.pokai.codync.design.SectionCard
import com.pokai.codync.design.Spinner
import com.pokai.codync.design.StatusDot
import com.pokai.codync.design.hasAgsGlass
import com.pokai.codync.design.theme.CodyncTheme
import com.pokai.codync.design.theme.CodyncType
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

@Composable
fun GlassLabScreen(
    config: GlassConfig,
    onConfig: (GlassConfig) -> Unit,
    onBack: () -> Unit,
) {
    val colors = CodyncTheme.colors
    val hazeState = rememberHazeState()

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
                Text("Glass lab", color = colors.text, style = CodyncType.headline)
                Text(
                    if (hasAgsGlass) "AGSL edge refraction" else "Android 12 frosted fallback",
                    color = colors.secondary,
                    style = CodyncType.caption,
                )
            }
            FpsMeter()
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .hazeSource(hazeState),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 190.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(6) { index ->
                    SampleCard(index)
                }
            }
            GlassSample(
                hazeState = hazeState,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
            )
        }

        SectionCard(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Live tuning", color = colors.text, style = CodyncType.headline)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "API ${Build.VERSION.SDK_INT} · ${if (hasAgsGlass) "AGSL" else "RenderEffect"}",
                        color = colors.tertiary,
                        style = CodyncType.caption,
                    )
                }
                TuneSlider("Blur", "${config.blurRadius.value.toInt()} dp", config.blurRadius.value / 48f) {
                    onConfig(config.copy(blurRadius = (it * 48f).dp))
                }
                TuneSlider("Tint", "${(config.tintAlpha * 100).toInt()}%", config.tintAlpha / 0.9f) {
                    onConfig(config.copy(tintAlpha = (it * 0.9f).coerceIn(0f, 0.9f)))
                }
                TuneSlider("Refraction", "${(config.refractionStrength * 100).toInt()}%", config.refractionStrength) {
                    onConfig(config.copy(refractionStrength = it))
                }
                TuneSlider("Edge height", "${(config.refractionHeight * 100).toInt()}%", config.refractionHeight / 0.6f) {
                    onConfig(config.copy(refractionHeight = (it * 0.6f).coerceIn(0f, 0.6f)))
                }
                TuneSlider("Displacement", "${config.refractionDisplacement.value.toInt()} dp", config.refractionDisplacement.value / 40f) {
                    onConfig(config.copy(refractionDisplacement = (it * 40f).dp))
                }
                TuneSlider("Specular", "${(config.specular * 100).toInt()}%", config.specular) {
                    onConfig(config.copy(specular = it))
                }
                TuneSlider("Quality", "${(config.quality * 100).toInt()}%", config.quality) {
                    onConfig(config.copy(quality = it.coerceIn(0f, 1f)))
                }
                TuneSlider("Noise (12)", "${(config.noise * 100).toInt()}%", config.noise / 0.2f) {
                    onConfig(config.copy(noise = (it * 0.2f).coerceIn(0f, 0.2f)))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.pokai.codync.design.SecondaryButton("Reset", onClick = { onConfig(GlassConfig()) })
                    com.pokai.codync.design.SecondaryButton("60 fps target", onClick = {
                        onConfig(config.copy(quality = 0.5f))
                    })
                }
            }
        }
    }
}

@Composable
private fun GlassSample(hazeState: HazeState, modifier: Modifier = Modifier) {
    val colors = CodyncTheme.colors
    Box(modifier.fillMaxWidth().height(170.dp).padding(horizontal = 16.dp)) {
        LazyColumnBehind()
        GlassSurface(
            state = hazeState,
            shape = RoundedCornerShape(30.dp),
            modifier = Modifier.align(Alignment.Center).fillMaxWidth(0.90f),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CodyncIcon(CodyncIcons.Bots, tint = colors.text, size = 22.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Floating glass", color = colors.text, style = CodyncType.headline)
                    Text("Backdrop blur + edge refraction", color = colors.secondary, style = CodyncType.caption)
                }
                Spinner(size = 16.dp, color = colors.secondary)
            }
        }
    }
}

@Composable
private fun LazyColumnBehind() {
    Column(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF2B2B2B), Color(0xFF3D2A55), Color(0xFF1A3546)),
                ),
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(4) { index ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                CharacterAvatar("A", listOf("violet", "cyan", "orange", "green")[index], "blob", size = 26.dp)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.height(9.dp).fillMaxWidth(0.35f).clip(CircleShape).background(Color.White.copy(alpha = 0.28f)))
                Spacer(Modifier.weight(1f))
                Box(Modifier.height(7.dp).width(34.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f)))
            }
        }
    }
}

@Composable
private fun SampleCard(index: Int) {
    val colors = CodyncTheme.colors
    val palettes = listOf(
        listOf(Color(0xFFFFE0B2), Color(0xFFFFCDD2)),
        listOf(Color(0xFFB2EBF2), Color(0xFFBBDEFB)),
        listOf(Color(0xFFD1C4E9), Color(0xFFF8BBD0)),
        listOf(Color(0xFFC8E6C9), Color(0xFFFFF9C4)),
        listOf(Color(0xFFCFD8DC), Color(0xFFD7CCC8)),
        listOf(Color(0xFFB3E5FC), Color(0xFFE1BEE7)),
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(palettes[index % palettes.size]))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CharacterAvatar("Bot $index", "black", "squircle", size = 38.dp, character = "◐")
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("Scroll behind the glass", color = Color(0xFF141414), style = CodyncType.body.copy(fontWeight = FontWeight.SemiBold))
            Text("Sample row ${index + 1} · watch the edge", color = Color(0xFF444444), style = CodyncType.caption)
        }
        StatusDot(Color(0xFF2E7D32), size = 7.dp)
    }
}

@Composable
private fun TuneSlider(label: String, valueText: String, value: Float, onChange: (Float) -> Unit) {
    val colors = CodyncTheme.colors
    Column(Modifier.padding(top = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = colors.secondary, style = CodyncType.caption, modifier = Modifier.weight(1f))
            Text(valueText, color = colors.tertiary, style = CodyncType.caption2)
        }
        Slider(
            value = value.coerceIn(0f, 1f),
            onValueChange = onChange,
            colors = SliderDefaults.colors(
                thumbColor = colors.text,
                activeTrackColor = colors.text,
                inactiveTrackColor = colors.border,
            ),
            modifier = Modifier.height(22.dp),
        )
    }
}

@Composable
private fun FpsMeter() {
    val colors = CodyncTheme.colors
    var fps by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        var frames = 0
        var last = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { now ->
                frames++
                if (last == 0L) last = now
                if (now - last >= 1_000_000_000L) {
                    fps = frames
                    frames = 0
                    last = now
                }
            }
        }
    }
    Row(
        Modifier
            .clip(CircleShape)
            .background(colors.surface)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(if (fps >= 55) colors.added else if (fps >= 40) colors.warning else colors.danger, size = 6.dp)
        Spacer(Modifier.width(5.dp))
        Text("$fps fps", color = colors.secondary, style = CodyncType.caption2.copy(fontSize = 11.sp))
    }
}
