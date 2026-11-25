package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val ThennavalDarkColorScheme = darkColorScheme(
    primary = AccentPurple,
    onPrimary = TextPrimary,
    primaryContainer = AccentPurpleContainer,
    onPrimaryContainer = AccentPurpleLight,
    secondary = AccentBlue,
    onSecondary = TextPrimary,
    secondaryContainer = AccentBlueContainer,
    onSecondaryContainer = AccentBlueLight,
    tertiary = StatusCyan,
    onTertiary = NavyBlack,
    background = NavyBlack,
    onBackground = TextPrimary,
    surface = NavySurface,
    onSurface = TextPrimary,
    surfaceVariant = NavyCard,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = NavyBlack,
    surfaceContainerLow = NavySurface,
    surfaceContainer = NavyCard,
    surfaceContainerHigh = NavyCardElevated,
    outline = NavyBorder,
    outlineVariant = NavyBorder,
    error = StatusRed,
    onError = TextPrimary,
    errorContainer = StatusRedBg,
    onErrorContainer = StatusRed
)

val ThennavalShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun ThennavalComboTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ThennavalDarkColorScheme,
        typography = ThennavalTypography,
        shapes = ThennavalShapes,
        content = content
    )
}
