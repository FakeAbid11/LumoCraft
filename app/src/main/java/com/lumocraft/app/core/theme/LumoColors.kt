package com.lumocraft.app.core.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Brand-specific colors that are not part of the Material 3 palette:
 * the home brand gradient plus the tokens that render Minecraft-GUI
 * style beveled panels (a flat fill with a light top/left highlight and
 * a dark bottom/right shadow).
 */
@Immutable
data class LumoColors(
    val brandGradientStart: Color,
    val brandGradientEnd: Color,
    val panelFill: Color,
    val panelInset: Color,
    val panelBevelLight: Color,
    val panelBevelDark: Color,
    val panelBorder: Color,
    val accent: Color,
    val accentEdge: Color,
) {
    companion object {
        val Dark = LumoColors(
            brandGradientStart = GrassGreenDark,
            brandGradientEnd = DiamondTealDark,
            panelFill = StonePanel,
            panelInset = Bedrock,
            panelBevelLight = StoneEdgeLight,
            panelBevelDark = StoneEdgeDark,
            panelBorder = Color(0xFF0A0C0E),
            accent = GrassGreen,
            accentEdge = GrassGreenBright,
        )
        val Light = LumoColors(
            brandGradientStart = Color(0xFF9CCB7E),
            brandGradientEnd = Color(0xFF7FD0C4),
            panelFill = Color(0xFFECE9E1),
            panelInset = Color(0xFFFFFFFF),
            panelBevelLight = Color(0xFFFFFFFF),
            panelBevelDark = Color(0xFFBDB6A6),
            panelBorder = Color(0xFF8C8674),
            accent = GrassGreenDark,
            accentEdge = GrassGreenBright,
        )
    }
}

val LocalLumoColors = staticCompositionLocalOf { LumoColors.Light }

/** Current brand colors, to be read inside a [LumoCraftTheme] scope. */
@Composable
fun lumoColors(): LumoColors = LocalLumoColors.current
