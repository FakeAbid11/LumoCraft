package com.lumocraft.app.core.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// --- Minecraft-flavored brand palette ---
// Grass (primary), emerald/diamond (secondary), gold/XP (tertiary),
// redstone (error), and a stone/slate neutral ladder for panels.
val GrassGreen = Color(0xFF6AB04C)
val GrassGreenDark = Color(0xFF4E8C36)
val GrassGreenBright = Color(0xFF8BD46A)
val DiamondTeal = Color(0xFF3AD6C6)
val DiamondTealDark = Color(0xFF14867B)
val GoldOre = Color(0xFFF2C14E)
val Redstone = Color(0xFFD64541)

// Stone/slate neutrals — the dark backdrop ladder (dark mode is the star).
val Bedrock = Color(0xFF0E1013)
val StoneDeep = Color(0xFF16191D)
val StonePanel = Color(0xFF1E2227)
val StonePanelHigh = Color(0xFF262B31)
val StonePanelHighest = Color(0xFF313840)
val StoneEdgeLight = Color(0xFF3D454E)
val StoneEdgeDark = Color(0xFF0A0C0E)
val StoneText = Color(0xFFE7E9EC)
val StoneTextDim = Color(0xFFA5ADB6)

val LightColorScheme = lightColorScheme(
    primary = GrassGreenDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDE8BC),
    onPrimaryContainer = Color(0xFF12300A),
    secondary = DiamondTealDark,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB8F0E8),
    onSecondaryContainer = Color(0xFF00352F),
    tertiary = Color(0xFFB8860B),
    background = Color(0xFFF3F1EC),
    onBackground = Color(0xFF1B1C18),
    surface = Color(0xFFF3F1EC),
    onSurface = Color(0xFF1B1C18),
    surfaceVariant = Color(0xFFDED8CC),
    onSurfaceVariant = Color(0xFF4B4A42),
    outline = Color(0xFF7C7A70),
    outlineVariant = Color(0xFFC4BDAE),
    error = Color(0xFFB3261E),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFECE9E1),
    surfaceContainer = Color(0xFFE6E2D8),
    surfaceContainerHigh = Color(0xFFDFDBD0),
    surfaceContainerHighest = Color(0xFFD8D3C7)
)

val DarkColorScheme = darkColorScheme(
    primary = GrassGreen,
    onPrimary = Color(0xFF0C1A06),
    primaryContainer = GrassGreenDark,
    onPrimaryContainer = Color(0xFFE8F6DE),
    secondary = DiamondTeal,
    onSecondary = Color(0xFF00201C),
    secondaryContainer = DiamondTealDark,
    onSecondaryContainer = Color(0xFFB8F0E8),
    tertiary = GoldOre,
    onTertiary = Color(0xFF2A1E00),
    error = Redstone,
    onError = Color(0xFF2A0000),
    background = StoneDeep,
    onBackground = StoneText,
    surface = StoneDeep,
    onSurface = StoneText,
    surfaceVariant = StonePanelHigh,
    onSurfaceVariant = StoneTextDim,
    outline = StoneEdgeLight,
    outlineVariant = StoneEdgeDark,
    surfaceContainerLowest = Bedrock,
    surfaceContainerLow = StonePanel,
    surfaceContainer = StonePanelHigh,
    surfaceContainerHigh = StonePanelHigh,
    surfaceContainerHighest = StonePanelHighest
)
