package com.pokai.codync.design.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Codync's black-and-white palette, ported from the iPhone app.
 * Colour comes from bot avatars; amber means "needs you", red means an error.
 */
@Immutable
data class CodyncColors(
    val background: Color,
    val surface: Color,
    val bubbleAgent: Color,
    val bubbleUser: Color,
    /** Outgoing chat bubbles only; unlike [bubbleUser] this never tints buttons or switches. */
    val bubbleSent: Color,
    val border: Color,
    val text: Color,
    val secondary: Color,
    val tertiary: Color,
    val accentFill: Color,
    val accent: Color,
    val onAccent: Color,
    val accentDim: Color,
    val danger: Color,
    val warning: Color,
    val attention: Color,
    val codeBackground: Color,
    val added: Color,
    val removed: Color,
    val glassTint: Color,
    val glassBorder: Color,
    val scrim: Color,
    val isDark: Boolean,
)

val LightCodyncColors = CodyncColors(
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFF4F4F4),
    bubbleAgent = Color(0xFFF4F4F4),
    bubbleUser = Color(0xFFE2E2E2),
    // WhatsApp-style outgoing tint keeps sent and received bubbles distinct at a glance.
    bubbleSent = Color(0xFFDCF6D0),
    border = Color(0xFFE3E3E3),
    text = Color(0xFF141414),
    secondary = Color(0xFF6B6B6B),
    tertiary = Color(0xFF767676),
    accentFill = Color(0xFF000000),
    accent = Color(0xFF000000),
    onAccent = Color(0xFFFFFFFF),
    accentDim = Color(0xFFD9D9D9),
    danger = Color(0xFFC23A2B),
    warning = Color(0xFFF0A030),
    attention = Color(0xFF936000),
    codeBackground = Color(0xFFF4F4F4),
    added = Color(0xFF2E7D32),
    removed = Color(0xFFC62828),
    glassTint = Color(0x8CFFFFFF),
    glassBorder = Color(0x66FFFFFF),
    scrim = Color(0x1F000000),
    isDark = false,
)

val DarkCodyncColors = CodyncColors(
    background = Color(0xFF0A0A0A),
    surface = Color(0xFF141414),
    bubbleAgent = Color(0xFF1C1C1C),
    bubbleUser = Color(0xFF363636),
    bubbleSent = Color(0xFF1F4A38),
    border = Color(0xFF262626),
    text = Color(0xFFF2F2F2),
    secondary = Color(0xFF9A9A9A),
    tertiary = Color(0xFF929292),
    accentFill = Color(0xFFFFFFFF),
    accent = Color(0xFFFFFFFF),
    onAccent = Color(0xFF000000),
    accentDim = Color(0xFF333333),
    danger = Color(0xFFF0A7A7),
    warning = Color(0xFFF0A030),
    attention = Color(0xFFECAF52),
    codeBackground = Color(0xFF111111),
    added = Color(0xFF8FD18B),
    removed = Color(0xFFF0A7A7),
    glassTint = Color(0x52141414),
    glassBorder = Color(0x2EFFFFFF),
    scrim = Color(0x66000000),
    isDark = true,
)

val LocalCodyncColors = staticCompositionLocalOf { LightCodyncColors }

data class AvatarSwatch(val id: String, val label: String, val color: Color)

object AvatarPalette {
    val colors = listOf(
        AvatarSwatch("black", "Black", Color(0xFF2B2B2B)),
        AvatarSwatch("brown", "Brown", Color(0xFF936439)),
        AvatarSwatch("red", "Red", Color(0xFFFF263C)),
        AvatarSwatch("orange", "Orange", Color(0xFFFF6700)),
        AvatarSwatch("yellow", "Yellow", Color(0xFFFF9800)),
        AvatarSwatch("green", "Green", Color(0xFF00C972)),
        AvatarSwatch("cyan", "Cyan", Color(0xFF00BCA6)),
        AvatarSwatch("blue", "Blue", Color(0xFF1084FE)),
        AvatarSwatch("violet", "Violet", Color(0xFF9159FE)),
        AvatarSwatch("magenta", "Magenta", Color(0xFFFF309B)),
        AvatarSwatch("lime", "Lime", Color(0xFF84CC16)),
        AvatarSwatch("emerald", "Emerald", Color(0xFF10B981)),
        AvatarSwatch("sky", "Sky", Color(0xFF0EA5E9)),
        AvatarSwatch("indigo", "Indigo", Color(0xFF6366F1)),
        AvatarSwatch("slate", "Slate", Color(0xFF64748B)),
        AvatarSwatch("gray", "Gray", Color(0xFF777777)),
    )

    val shapes = listOf("blob", "pebble", "squircle", "tablet", "wedge", "hex", "cloud", "teardrop")

    fun color(id: String): Color = (colors.firstOrNull { it.id == id } ?: colors[7]).color
}
