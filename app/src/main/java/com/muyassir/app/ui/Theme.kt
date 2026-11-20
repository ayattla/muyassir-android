package com.muyassir.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.muyassir.app.R

/** Muyassir brand tokens. Lapis is the action colour; saffron marks the one thing that matters on a screen. */
object Brand {
    val Lapis = Color(0xFF2F4BFF)
    val LapisDeep = Color(0xFF2236D9)
    val Ink = Color(0xFF101935)
    val Mint = Color(0xFF12C29A)
    val Saffron = Color(0xFFFFC23D)
    val Surface = Color(0xFFF3F5FA)
    val Line = Color(0xFFE2E6F0)
    val Muted = Color(0xFF5B6480)
}

val Plex = FontFamily(
    Font(R.font.plex_regular, FontWeight.Normal),
    Font(R.font.plex_medium, FontWeight.Medium),
    Font(R.font.plex_semibold, FontWeight.SemiBold),
    Font(R.font.plex_bold, FontWeight.Bold),
)

private fun t(size: Int, w: FontWeight, lh: Int) = TextStyle(fontFamily = Plex, fontWeight = w, fontSize = size.sp, lineHeight = lh.sp)

private val Type = Typography(
    displaySmall = t(32, FontWeight.Bold, 42),
    headlineMedium = t(26, FontWeight.Bold, 36),
    headlineSmall = t(22, FontWeight.Bold, 30),
    titleLarge = t(20, FontWeight.SemiBold, 28),
    titleMedium = t(16, FontWeight.SemiBold, 24),
    titleSmall = t(14, FontWeight.SemiBold, 20),
    bodyLarge = t(16, FontWeight.Normal, 26),
    bodyMedium = t(14, FontWeight.Normal, 22),
    bodySmall = t(12, FontWeight.Normal, 18),
    labelLarge = t(14, FontWeight.SemiBold, 20),
    labelMedium = t(12, FontWeight.Medium, 16),
    labelSmall = t(11, FontWeight.Medium, 14),
)

@Composable
fun MuyassirTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = if (dark) darkColorScheme(
        primary = Color(0xFF8C9BFF), onPrimary = Brand.Ink, secondary = Brand.Mint, tertiary = Brand.Saffron,
        background = Color(0xFF0D1226), surface = Color(0xFF151B33), surfaceVariant = Color(0xFF1E2542),
        onBackground = Color(0xFFE8EBF5), onSurface = Color(0xFFE8EBF5), onSurfaceVariant = Color(0xFFA9B0C8),
        outlineVariant = Color(0xFF2B3354),
    ) else lightColorScheme(
        primary = Brand.Lapis, onPrimary = Color.White, secondary = Brand.Mint, tertiary = Brand.Saffron,
        background = Brand.Surface, surface = Color.White, surfaceVariant = Color(0xFFEDF0F7),
        onBackground = Brand.Ink, onSurface = Brand.Ink, onSurfaceVariant = Brand.Muted, outlineVariant = Brand.Line,
    )
    MaterialTheme(colorScheme = scheme, typography = Type, content = content)
}
