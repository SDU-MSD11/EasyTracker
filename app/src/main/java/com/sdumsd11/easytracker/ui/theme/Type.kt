package com.sdumsd11.easytracker.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Material3 hierarchy used across screens: headline for app bars, title for
// card/section headings, body for content, label for captions-only badges.
//
// Legibility (ISO 9241-303 §5.5.2/5.5.3, 9241-125): running text never goes below
// 16sp / 24sp line-height — bodyMedium was raised 14sp/20sp → 16sp/24sp, and
// labelMedium 12sp/16sp → 14sp/20sp for captions-only use (badges, table captions).
// FontFamily.SansSerif is explicit (no Caveat/serif anywhere — structured Latin only).
//
// Polarity: light scheme renders near-black (Neutral900) on near-white (Neutral50)
// — positive polarity; dark scheme renders near-white (Neutral50) on near-black
// (Neutral950) — negative polarity (see Theme.kt). Dynamic color is never applied:
// EasyTrackerTheme always uses the brand palette (and these contrast pairs).
//
// Touch legibility is unchanged: 48dp min targets (NavButton/ScreenScaffold).
private val family = FontFamily.SansSerif
val Typography = Typography(
    headlineSmall = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.25.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.5.sp,
    ),
)
