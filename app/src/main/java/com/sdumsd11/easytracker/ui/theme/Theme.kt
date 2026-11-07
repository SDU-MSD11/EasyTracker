package com.sdumsd11.easytracker.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val colorScheme = lightColorScheme(
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
    outline = Neutral300,
)

@Composable
fun EasyTrackerTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false, // opt-in only: brand palette wins by default
    content: @Composable () -> Unit,
) {
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
