package com.muyassir.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muyassir.app.R
import com.muyassir.app.core.Prefs

/**
 * Muyassir design system ("Paper & Ink").
 * Light is the default: warm-neutral paper, ink text, one action colour (indigo) used sparingly,
 * saffron reserved for the brand dot and favourites. Dark mode is its own palette, not an inversion.
 * All text pairs below meet WCAG AA (≥ 4.5:1) on their surfaces.
 */
object Brand {
    val Indigo = Color(0xFF3346D3)      // actions (white text 6.1:1)
    val IndigoDark = Color(0xFF9AA6FF)  // actions in dark mode (ink text 8.4:1)
    val Ink = Color(0xFF141A2E)
    val Saffron = Color(0xFFF2B01E)
    val Mint = Color(0xFF14936B)
    val Danger = Color(0xFFC62F3A)
    // kept for older call sites
    val Lapis = Indigo
    val LapisDeep = Color(0xFF2433A8)
    val Muted = Color(0xFF4F586E)
    val Surface = Color(0xFFF6F7FA)
    val Line = Color(0xFFE3E6EE)
}

/** Extra tokens Material's ColorScheme has no slot for. */
@Immutable
data class Tokens(
    val dark: Boolean,
    val textPrimary: Color, val textSecondary: Color, val textTertiary: Color,
    val surface: Color, val surfaceRaised: Color, val surfaceSunken: Color, val border: Color, val borderStrong: Color,
    val accent: Color, val onAccent: Color, val accentSoft: Color, val shadow: Color,
)

val LocalTokens = staticCompositionLocalOf { lightTokens }

private val lightTokens = Tokens(
    dark = false,
    textPrimary = Color(0xFF141A2E), textSecondary = Color(0xFF4F586E), textTertiary = Color(0xFF6A7389),
    surface = Color(0xFFFFFFFF), surfaceRaised = Color(0xFFFFFFFF), surfaceSunken = Color(0xFFF0F2F6),
    border = Color(0xFFE3E6EE), borderStrong = Color(0xFFCDD2DE),
    accent = Brand.Indigo, onAccent = Color.White, accentSoft = Color(0xFFECEEFD), shadow = Color(0x14141A2E),
)
private val darkTokens = Tokens(
    dark = true,
    textPrimary = Color(0xFFF2F4FA), textSecondary = Color(0xFFB7BDCE), textTertiary = Color(0xFF959DB3),
    surface = Color(0xFF181D2B), surfaceRaised = Color(0xFF1F2536), surfaceSunken = Color(0xFF121623),
    border = Color(0xFF2B3245), borderStrong = Color(0xFF3A4258),
    accent = Brand.IndigoDark, onAccent = Color(0xFF0E1220), accentSoft = Color(0xFF242B4A), shadow = Color(0x00000000),
)

/** Accent colours for Plus (all give ≥ 4.5:1 with white text in light mode). */
val Accents = listOf(
    "نيلي" to Brand.Indigo, "زمردي" to Color(0xFF0B7F7A), "عنابي" to Color(0xFFA3264A),
    "برتقالي" to Color(0xFFB0500A), "بنفسجي" to Color(0xFF6A3FC4), "فحمي" to Color(0xFF2A2F3A),
)

val tokens: Tokens @Composable @ReadOnlyComposable get() = LocalTokens.current

/** Category colours are tuned to one lightness so no category shouts; in dark mode they are lifted for contrast. */
@Composable @ReadOnlyComposable
fun tone(c: Color): Color = if (LocalTokens.current.dark) lerp(c, Color.White, 0.38f) else c

@Composable @ReadOnlyComposable
fun toneSoft(c: Color): Color = if (LocalTokens.current.dark) lerp(Color(0xFF181D2B), c, 0.28f) else lerp(Color.White, c, 0.10f)

object Space { val xs = 4.dp; val s = 8.dp; val m = 12.dp; val l = 16.dp; val xl = 20.dp; val xxl = 24.dp; val xxxl = 32.dp }
object Radius { val s = 10.dp; val m = 14.dp; val l = 18.dp; val xl = 24.dp }

val Plex = FontFamily(
    Font(R.font.plex_regular, FontWeight.Normal),
    Font(R.font.plex_medium, FontWeight.Medium),
    Font(R.font.plex_semibold, FontWeight.SemiBold),
    Font(R.font.plex_bold, FontWeight.Bold),
)

private fun t(size: Int, w: FontWeight, lh: Int) = TextStyle(fontFamily = Plex, fontWeight = w, fontSize = size.sp, lineHeight = lh.sp)

private val Type = Typography(
    displaySmall = t(30, FontWeight.Bold, 40),
    headlineMedium = t(26, FontWeight.Bold, 36),
    headlineSmall = t(22, FontWeight.Bold, 30),
    titleLarge = t(19, FontWeight.SemiBold, 28),
    titleMedium = t(16, FontWeight.SemiBold, 24),
    titleSmall = t(14, FontWeight.SemiBold, 21),
    bodyLarge = t(16, FontWeight.Normal, 26),
    bodyMedium = t(14, FontWeight.Normal, 22),
    bodySmall = t(12.5f, FontWeight.Normal, 19),
    labelLarge = t(14, FontWeight.SemiBold, 20),
    labelMedium = t(12, FontWeight.Medium, 17),
    labelSmall = t(11, FontWeight.Medium, 15),
)
private fun t(size: Float, w: FontWeight, lh: Int) = TextStyle(fontFamily = Plex, fontWeight = w, fontSize = size.sp, lineHeight = lh.sp)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(Radius.s), medium = RoundedCornerShape(Radius.m),
    large = RoundedCornerShape(Radius.l), extraLarge = RoundedCornerShape(Radius.xl),
)

private fun scheme(k: Tokens): ColorScheme = if (k.dark) darkColorScheme(
    primary = k.accent, onPrimary = k.onAccent, primaryContainer = k.accentSoft, onPrimaryContainer = k.textPrimary,
    secondary = Color(0xFF5FD3AE), tertiary = Brand.Saffron,
    background = Color(0xFF0F1320), onBackground = k.textPrimary, surface = k.surface, onSurface = k.textPrimary,
    surfaceVariant = k.surfaceSunken, onSurfaceVariant = k.textSecondary, outline = k.borderStrong, outlineVariant = k.border,
    surfaceContainer = k.surfaceRaised, surfaceContainerHigh = k.surfaceRaised, surfaceContainerLow = k.surface,
    error = Color(0xFFFF8A8F), onError = Color(0xFF2A0508),
) else lightColorScheme(
    primary = k.accent, onPrimary = k.onAccent, primaryContainer = k.accentSoft, onPrimaryContainer = Color(0xFF1B2788),
    secondary = Brand.Mint, tertiary = Brand.Saffron,
    background = Color(0xFFF6F7FA), onBackground = k.textPrimary, surface = k.surface, onSurface = k.textPrimary,
    surfaceVariant = k.surfaceSunken, onSurfaceVariant = k.textSecondary, outline = k.borderStrong, outlineVariant = k.border,
    surfaceContainer = Color(0xFFFFFFFF), surfaceContainerHigh = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFFAFBFC),
    error = Brand.Danger, onError = Color.White,
)

@Composable
fun MuyassirTheme(content: @Composable () -> Unit) {
    // Light by default; dark only when the user picks it (or picks "follow the system" and the system is dark).
    val dark = when (Prefs.theme) { "dark" -> true; "system" -> isSystemInDarkTheme(); else -> false }
    val base = if (dark) darkTokens else lightTokens
    // Plus: optional accent colour (kept only while Plus is open to the user)
    val pick = if (com.muyassir.app.core.Plus.unlocked) Prefs.accent else 0
    val k = if (pick in 1 until Accents.size) Accents[pick].second.let { c ->
        if (dark) base.copy(accent = lerp(c, Color.White, 0.45f), accentSoft = lerp(Color(0xFF181D2B), c, 0.35f))
        else base.copy(accent = c, accentSoft = lerp(Color.White, c, 0.10f))
    } else base
    CompositionLocalProvider(LocalTokens provides k) {
        MaterialTheme(colorScheme = scheme(k), typography = Type, shapes = AppShapes, content = content)
    }
}
