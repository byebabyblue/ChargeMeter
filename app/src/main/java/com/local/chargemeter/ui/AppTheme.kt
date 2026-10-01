package com.local.chargemeter.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

enum class ThemeMode(val label: String) {
    System("跟随系统"), Light("浅色"), Dark("深色"),
}

internal data class AppPalette(
    val PageBackground: Color,
    val CardBackground: Color,
    val TextPrimary: Color,
    val TextSecondary: Color,
    val TrackColor: Color,
    val ChargeGreenDark: Color,
    val DischargeRedDark: Color,
    val isDark: Boolean,
)

internal val LightPalette = AppPalette(
    Color(0xFFF5F8F3), Color.White, Color(0xFF101411), Color(0xFF727873),
    Color(0xFFE4EAE4), Color(0xFF00A943), Color(0xFFD92D38), false,
)
internal val DarkPalette = AppPalette(
    Color(0xFF0D1210), Color(0xFF19211D), Color(0xFFEAF3ED), Color(0xFF9BAFA1),
    Color(0xFF2D3B32), Color(0xFF51DE8D), Color(0xFFFF7B81), true,
)
internal val LocalAppPalette = staticCompositionLocalOf { LightPalette }
