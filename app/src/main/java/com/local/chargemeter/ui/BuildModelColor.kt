package com.local.chargemeter.ui

import androidx.compose.ui.graphics.Color

/** Model identity stays consistent across versions and both themes. */
internal fun buildModelColor(model: String, dark: Boolean): Color = when (model) {
    "GPT-6.1 Sol" -> if (dark) Color(0xFF67E8BD) else Color(0xFF087C5C)
    "GPT-5.6 Sol" -> Color(0xFFFFC857)
    "GPT-6 Astra" -> if (dark) Color(0xFFBEA2FF) else Color(0xFF7852BD)
    else -> if (dark) Color(0xFFB4BDC5) else Color(0xFF596672)
}
