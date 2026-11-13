package com.sdumsd11.easytracker.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val lightScheme = lightColorScheme(
    primary = Primary600, // 600 (not 500) for white-text contrast on light surfaces
    onPrimary = Color.White,
    primaryContainer = Primary100,
    onPrimaryContainer = Primary900,
    secondary = Secondary600,
    onSecondary = Color.White,
    secondaryContainer = Secondary100,
    onSecondaryContainer = Secondary900,
    tertiary = Success600,
    onTertiary = Color.White,
    tertiaryContainer = Success100,
    onTertiaryContainer = Success800,
    error = Danger600,
    onError = Color.White,
    errorContainer = Danger100,
    onErrorContainer = Danger800,
    background = Neutral50,
    onBackground = Neutral900,
    surface = Neutral50,
    onSurface = Neutral900,
    surfaceVariant = Neutral100,
    onSurfaceVariant = Neutral700,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Neutral100,
    surfaceContainer = Primary100,
    surfaceContainerHigh = Primary200,
    outline = Neutral300,
    outlineVariant = Primary200,
)

// Cards: 16dp corners with a 1dp tonal outline; see EasyCard.
private val shapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(16.dp),
)

private val darkScheme = darkColorScheme(
    primary = Primary300,
    onPrimary = Primary950,
    primaryContainer = Primary800,
    onPrimaryContainer = Primary100,
    secondary = Secondary300,
    onSecondary = Secondary950,
    secondaryContainer = Secondary800,
    onSecondaryContainer = Secondary100,
    tertiary = Success300,
    onTertiary = Neutral950,
    tertiaryContainer = Success800,
    onTertiaryContainer = Success100,
    error = Danger300,
    onError = Neutral950,
    errorContainer = Danger800,
    onErrorContainer = Danger100,
    background = Neutral950,
    onBackground = Neutral50,
    surface = Neutral950,
    onSurface = Neutral50,
    surfaceVariant = Neutral800,
    onSurfaceVariant = Neutral300,
    surfaceContainerLowest = Neutral950,
    surfaceContainerLow = Neutral900,
    surfaceContainer = Primary900,
    surfaceContainerHigh = Primary800,
    outline = Neutral700,
    outlineVariant = Primary800,
)

@Composable
fun EasyTrackerTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false, // opt-in only: brand palette wins by default
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) darkScheme else lightScheme,
        shapes = shapes,
        typography = Typography,
        content = content,
    )
}
