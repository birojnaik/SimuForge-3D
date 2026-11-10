package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SimuForgeDarkScheme = darkColorScheme(
    primary = CyanFluidPrimary,
    onPrimary = Color(0xFF00262C),
    primaryContainer = Color(0xFF004854),
    onPrimaryContainer = Color(0xFFB8F8FF),
    secondary = AmberClothSecondary,
    onSecondary = Color(0xFF332000),
    secondaryContainer = Color(0xFF4D3200),
    onSecondaryContainer = Color(0xFFFFDFAA),
    tertiary = VioletGravityTertiary,
    onTertiary = Color(0xFF360042),
    tertiaryContainer = Color(0xFF560067),
    onTertiaryContainer = Color(0xFFFAD4FF),
    error = CrimsonStressAlert,
    background = StudioObsidian,
    onBackground = TextPrimaryLight,
    surface = StudioDeepNavy,
    onSurface = TextPrimaryLight,
    surfaceVariant = StudioPanelSurface,
    onSurfaceVariant = TextSecondaryMuted,
    outline = StudioBorderSubtle
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = SimuForgeDarkScheme,
        typography = Typography,
        content = content
    )
}
