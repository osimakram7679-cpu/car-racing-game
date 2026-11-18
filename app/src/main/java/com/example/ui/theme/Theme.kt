package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val TurboDarkColorScheme = darkColorScheme(
    primary = NitroOrange,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3E190B),
    onPrimaryContainer = NitroOrangeBright,
    secondary = ElectricCyan,
    onSecondary = CarbonBlack,
    secondaryContainer = Color(0xFF0A2E38),
    onSecondaryContainer = ElectricCyan,
    tertiary = TurboGold,
    onTertiary = CarbonBlack,
    error = RedlineCrimson,
    onError = Color.White,
    background = CarbonBlack,
    onBackground = TextPrimary,
    surface = AsphaltSurface,
    onSurface = TextPrimary,
    surfaceVariant = ElevatedCarbon,
    onSurfaceVariant = TextSecondary,
    outline = SteelBorder
)

@Composable
fun TurboRoarTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = TurboDarkColorScheme,
        typography = TurboTypography,
        content = content
    )
}
