package com.megalofit.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Lime = Color(0xFFD4FF3A)
val Ink = Color(0xFF0B0B0B)
val TileColor = Color(0xFF161616)
val TileHigh = Color(0xFF202020)
val Muted = Color(0xFF8C8C8C)
val Line = Color(0xFF2A2A2A)

private val colors = darkColorScheme(
    primary = Lime,
    onPrimary = Color.Black,
    secondary = Color.White,
    onSecondary = Color.Black,
    secondaryContainer = TileHigh,
    onSecondaryContainer = Color.White,
    background = Ink,
    onBackground = Color(0xFFF2F2F2),
    surface = Ink,
    onSurface = Color(0xFFF2F2F2),
    surfaceVariant = TileColor,
    onSurfaceVariant = Muted,
    surfaceContainerLowest = Ink,
    surfaceContainerLow = TileColor,
    surfaceContainer = TileColor,
    surfaceContainerHigh = TileHigh,
    surfaceContainerHighest = TileHigh,
    outline = Line,
    outlineVariant = Line,
    error = Color(0xFFFF5A5A),
)

private val base = Typography()
private val typography = Typography(
    displayLarge = base.displayLarge.copy(fontWeight = FontWeight.Black, fontFeatureSettings = "tnum"),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
    labelSmall = base.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp),
)

/** MegaloFit es siempre oscuro: estética de gimnasio, minimalista, con un único acento lima. */
@Composable
fun MegaloFitTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}
