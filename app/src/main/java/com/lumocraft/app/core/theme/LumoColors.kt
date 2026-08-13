package com.lumocraft.app.core.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Brand-specific colors that are not part of the Material 3 palette,
 * e.g. the home screen brand gradient.
 */
@Immutable
data class LumoColors(
    val brandGradientStart: Color,
    val brandGradientEnd: Color,
) {
    companion object {
        val Light = LumoColors(
            brandGradientStart = Color(0xFF4B5BE0),
            brandGradientEnd = Color(0xFF8E6FE8),
        )
        val Dark = LumoColors(
            brandGradientStart = Color(0xFF3A45B8),
            brandGradientEnd = Color(0xFF6C5BC9),
        )
    }
}

val LocalLumoColors = staticCompositionLocalOf { LumoColors.Light }

/** Current brand colors, to be read inside a [LumoCraftTheme] scope. */
@Composable
fun lumoColors(): LumoColors = LocalLumoColors.current
