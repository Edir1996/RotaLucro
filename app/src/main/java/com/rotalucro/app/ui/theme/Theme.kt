package com.rotalucro.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BrandNavy = Color(0xFF0B1220)
val BrandBlue = Color(0xFF2563EB)
val BrandGreen = Color(0xFF16A34A)
val BrandAmber = Color(0xFFEAB308)
val BrandRed = Color(0xFFDC2626)
val AppBackground = Color(0xFFF4F7FB)
val CardSurface = Color(0xFFFFFFFF)
val SlateText = Color(0xFF0F172A)
val MutedText = Color(0xFF64748B)
val BorderColor = Color(0xFFE2E8F0)

private val LightColors = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF172554),
    secondary = BrandGreen,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCFCE7),
    onSecondaryContainer = Color(0xFF14532D),
    error = BrandRed,
    background = AppBackground,
    onBackground = SlateText,
    surface = CardSurface,
    onSurface = SlateText,
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = MutedText,
    outline = BorderColor
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF60A5FA),
    secondary = Color(0xFF4ADE80),
    background = BrandNavy,
    surface = Color(0xFF111827),
    onSurface = Color(0xFFF8FAFC),
    onBackground = Color(0xFFF8FAFC)
)

@Composable
fun RotaLucroTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography(),
        content = content
    )
}
