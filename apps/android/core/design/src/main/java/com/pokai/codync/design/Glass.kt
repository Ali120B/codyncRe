package com.pokai.codync.design

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.pokai.codync.design.theme.CodyncTheme
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * Live glass parameters. The M0 Glass lab edits these; they will be persisted in Settings.
 */
@Immutable
data class GlassConfig(
    val blurRadius: Dp = 26.dp,
    val tintAlpha: Float = 0.52f,
    val refractionStrength: Float = 0.78f,
    val refractionHeight: Float = 0.26f,
    val refractionDisplacement: Dp = 22.dp,
    val specular: Float = 0.46f,
    val noise: Float = 0.035f,
    val quality: Float = 0.6f,
)

val LocalGlassConfig = staticCompositionLocalOf { GlassConfig() }

/**
 * Real glass. Haze captures and blurs the content behind the surface on Android 12+. On
 * Android 13+ an AGSL RuntimeShader also warps that captured backdrop around the rim, so the
 * edge actually lenses instead of only painting a translucent gradient. Android 12 gets the
 * same shape with the blurred, noisy, tinted frosted pane.
 *
 * Keep this off everything except the bottom nav, composer and sheet headers: the capture is
 * the expensive part.
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
fun GlassSurface(
    state: HazeState,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(28.dp),
    tintOverride: Color? = null,
    contentPadding: PaddingValues = PaddingValues(),
    content: @Composable BoxScope.() -> Unit,
) {
    val config = LocalGlassConfig.current
    val colors = CodyncTheme.colors
    val density = LocalDensity.current
    var size by remember { mutableStateOf(IntSize.Zero) }
    val tint = tintOverride ?: if (colors.isDark) {
        Color(0xFF141414).copy(alpha = config.tintAlpha)
    } else {
        Color.White.copy(alpha = config.tintAlpha)
    }
    val border = if (colors.isDark) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.55f)
    val style = remember(config, tint) {
        HazeStyle(
            backgroundColor = Color.Transparent,
            tint = HazeTint(tint),
            blurRadius = config.blurRadius,
            noiseFactor = config.noise,
            fallbackTint = HazeTint(tint),
        )
    }
    val edgeEffect = remember(size, config, density) {
        if (Build.VERSION.SDK_INT >= 33 && size.width > 0 && size.height > 0) {
            buildEdgeRefraction(size, config, density.density)
        } else {
            null
        }
    }

    Box(
        modifier
            .onSizeChanged { size = it }
            .clip(shape),
    ) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { renderEffect = edgeEffect?.asComposeRenderEffect() }
                .hazeEffect(state = state, style = style) {
                    blurEnabled = true
                    inputScale = HazeInputScale.Fixed(config.quality.coerceIn(0.35f, 1f))
                },
        )
        // A pair of very soft gradients give the pane depth without hiding the blur.
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = if (colors.isDark) 0.055f else 0.20f),
                        0.5f to Color.Transparent,
                        1f to Color.Black.copy(alpha = if (colors.isDark) 0.10f else 0.03f),
                    ),
                ),
        )
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = if (colors.isDark) 0.03f else 0.10f),
                            Color.Transparent,
                            Color.White.copy(alpha = if (colors.isDark) 0.02f else 0.06f),
                        ),
                    ),
                ),
        )
        Box(Modifier.matchParentSize().border(0.7.dp, border, shape))
        Box(Modifier.padding(contentPadding), content = content)
    }
}

private const val EDGE_SHADER = """
uniform shader content;
uniform float2 size;
uniform float band;
uniform float strength;
uniform float displacement;

half4 main(float2 coord) {
    float2 edge = min(coord, size - coord);
    float d = min(edge.x, edge.y);
    float t = smoothstep(0.0, band, d);
    float2 centre = size * 0.5;
    float2 dir = coord - centre;
    float len = length(dir);
    dir = len > 0.001 ? dir / len : float2(0.0, 0.0);
    float2 tangent = float2(-dir.y, dir.x);
    float warp = (1.0 - t) * (1.0 - t);
    float2 offset = dir * warp * displacement * strength
                 + tangent * warp * displacement * 0.32 * strength;
    half4 col = content.eval(coord + offset);
    float rim = pow(1.0 - t, 4.0);
    col.rgb += rim * 0.13 * strength;
    return col;
}
"""

@androidx.annotation.RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun buildEdgeRefraction(
    size: IntSize,
    config: GlassConfig,
    density: Float,
): RenderEffect? = runCatching {
    val shader = RuntimeShader(EDGE_SHADER)
    shader.setFloatUniform("size", size.width.toFloat(), size.height.toFloat())
    shader.setFloatUniform("band", (config.refractionHeight.coerceIn(0.05f, 0.6f) * size.height).coerceAtLeast(12f * density))
    shader.setFloatUniform("strength", config.refractionStrength.coerceIn(0f, 1f))
    shader.setFloatUniform("displacement", config.refractionDisplacement.value * density)
    RenderEffect.createRuntimeShaderEffect(shader, "content")
}.getOrNull()

/** Whether this device renders AGSL refraction or the Android 12 frosted fallback. */
val hasAgsGlass: Boolean
    get() = Build.VERSION.SDK_INT >= 33
