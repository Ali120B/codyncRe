package com.pokai.codync.design

import android.graphics.Region
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pokai.codync.design.theme.AvatarPalette
import com.pokai.codync.design.theme.CodyncTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

enum class AvatarMood { Idle, Working, NeedsInput }

/**
 * Desktop/iOS-parity character: silhouette filled with a shaded ball of dots
 * (highlight tint only). Identity is shape + color — no emoji overlay.
 */
@Composable
fun CharacterAvatar(
    name: String,
    colorId: String,
    shapeId: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    character: String? = null,
    statusRing: Color? = null,
    mood: AvatarMood = AvatarMood.Idle,
) {
    @Suppress("UNUSED_VARIABLE")
    val ignored = name to character
    val fill = AvatarPalette.color(colorId)
    val colors = CodyncTheme.colors
    val ink = if (colors.isDark) Color(0xFFF2F2F2) else Color(0xFF141414)
    val dots = remember(shapeId, size) { buildDotGrid(shapeId, size.value) }

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val px = this.size.minDimension
            if (statusRing != null) {
                drawPath(avatarPath(shapeId, px, px), statusRing.copy(alpha = 0.22f), style = Stroke(width = px * 0.08f))
            }
            drawDottedCharacter(dots = dots, sizePx = px, fill = fill, ink = ink, mood = mood)
        }
    }
}

private data class Dot(val row: Int, val col: Int, val nx: Float, val ny: Float)

private fun buildDotGrid(shape: String, sizeDp: Float): List<Dot> {
    val cells = when {
        sizeDp < 18f -> 7
        sizeDp < 28f -> 9
        else -> 13
    }
    val probe = 96
    val path = avatarPath(shape, probe.toFloat(), probe.toFloat())
    val androidPath = path.asAndroidPath()
    val bounds = Region(0, 0, probe, probe)
    val region = Region().also { it.setPath(androidPath, bounds) }
    val step = probe.toFloat() / cells
    val dots = ArrayList<Dot>(cells * cells / 2)
    for (row in 0 until cells) {
        for (col in 0 until cells) {
            val x = ((col + 0.5f) * step).toInt()
            val y = ((row + 0.5f) * step).toInt()
            if (region.contains(x, y)) {
                dots += Dot(row, col, x / probe.toFloat(), y / probe.toFloat())
            }
        }
    }
    return dots
}

private fun DrawScope.drawDottedCharacter(
    dots: List<Dot>,
    sizePx: Float,
    fill: Color,
    ink: Color,
    mood: AvatarMood,
) {
    val cells = when {
        sizePx < 18f * density -> 7
        sizePx < 28f * density -> 9
        else -> 13
    }
    val step = sizePx / cells
    val eyeColumns = when (cells) {
        7 -> listOf(2, 4)
        9 -> listOf(3, 6)
        else -> listOf(4, 8)
    }
    val eyeRows = when (cells) {
        13 -> listOf(4, 5, 6)
        9 -> listOf(3, 4)
        else -> listOf(2, 3)
    }
    val half = sizePx / 2f
    val yaw = when (mood) {
        AvatarMood.Working -> 0.4f
        AvatarMood.NeedsInput -> -0.2f
        AvatarMood.Idle -> -0.7f
    }
    val lx = sin(yaw.toDouble()).toFloat() * 0.8f
    val ly = 0.55f
    val lz = cos(yaw.toDouble()).toFloat() * 0.5f + 0.6f
    val ll = hypot3(lx, ly, lz)

    for (d in dots) {
        if (d.col in eyeColumns && d.row in eyeRows) continue
        val x = d.nx * sizePx
        val y = d.ny * sizePx
        val u = (x - half) / half
        val v = (half - y) / half
        val z = sqrt(max(0.2f, 1f - u * u - v * v))
        val nl = hypot3(u, v, z)
        var shade = 0.3f + 0.7f * max(0f, (u * lx + v * ly + z * lz) / (nl * ll))
        if (mood == AvatarMood.NeedsInput) {
            val ripple = 0.5f + 0.5f * sin(hypot(u.toDouble(), v.toDouble()) * 9.0).toFloat()
            shade *= 0.6f + 0.4f * ripple
        }
        val radius = step * 0.42f * (0.55f + 0.45f * shade)
        val inkA = if (cells < 13) 0.4f + 0.4f * shade else 0.2f + 0.4f * min(1f, shade / 0.7f)
        drawCircle(ink.copy(alpha = inkA), radius = radius, center = Offset(x, y), style = Fill)
        if (shade > 0.6f) {
            drawCircle(fill.copy(alpha = (shade - 0.6f) / 0.4f), radius = radius, center = Offset(x, y), style = Fill)
        }
    }
}

private fun hypot3(a: Float, b: Float, c: Float): Float =
    sqrt(a * a + b * b + c * c)

fun avatarPath(shape: String, w: Float, h: Float): Path {
    val path = Path()
    val cx = w / 2f
    val cy = h / 2f
    when (shape) {
        // Kept for bots created by older Android builds; current choices match desktop.
        "circle" -> path.addOval(Rect(w * 0.04f, h * 0.04f, w * 0.96f, h * 0.96f))
        "squircle" -> {
            val r = min(w, h) * 0.30f
            path.addRoundRect(RoundRect(Rect(w * 0.04f, h * 0.04f, w * 0.96f, h * 0.96f), CornerRadius(r, r)))
        }
        "tablet" -> {
            val r = min(w, h) * 0.22f
            path.addRoundRect(RoundRect(Rect(w * 0.14f, 0f, w * 0.86f, h), CornerRadius(r, r)))
        }
        "hex" -> {
            for (i in 0 until 6) {
                val a = (i * PI / 3) - PI / 2
                val x = cx + cos(a).toFloat() * w * 0.49f
                val y = cy + sin(a).toFloat() * h * 0.49f
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
        }
        "wedge" -> {
            path.moveTo(w * 0.5f, h * 0.04f)
            path.quadraticTo(w * 0.9f, h * 0.4f, w * 0.98f, h * 0.86f)
            path.quadraticTo(w * 0.5f, h * 1.04f, w * 0.02f, h * 0.86f)
            path.quadraticTo(w * 0.1f, h * 0.4f, w * 0.5f, h * 0.04f)
            path.close()
        }
        "cloud" -> {
            path.addOval(Rect(0f, h * 0.3f, w * 0.55f, h * 0.85f))
            path.addOval(Rect(w * 0.45f, h * 0.3f, w, h * 0.85f))
            path.addOval(Rect(w * 0.18f, h * 0.08f, w * 0.82f, h * 0.72f))
        }
        "teardrop" -> {
            path.moveTo(w * 0.5f, 0f)
            path.cubicTo(w * 0.62f, h * 0.2f, w * 0.94f, h * 0.38f, w * 0.94f, h * 0.62f)
            path.arcTo(Rect(w * 0.06f, h * 0.18f, w * 0.94f, h * 1.06f), 0f, 180f, false)
            path.cubicTo(w * 0.06f, h * 0.38f, w * 0.38f, h * 0.2f, w * 0.5f, 0f)
            path.close()
        }
        "pebble" -> path.addOval(Rect(0f, h * 0.1f, w, h * 0.9f))
        else -> {
            val steps = 64
            for (i in 0..steps) {
                val a = (i.toFloat() / steps) * 2f * PI.toFloat()
                val rr = 0.46f + 0.035f * sin(a * 3f + 0.6f)
                val x = w / 2f + cos(a) * w * rr
                val y = h / 2f + sin(a) * h * rr
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
        }
    }
    return path
}

@Composable
fun StatusDot(color: Color, size: Dp = 7.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        drawCircle(color)
    }
}
