package com.lumocraft.app.core.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Brand palette
val LumoIndigo = Color(0xFF4B5BE0)
val LumoIndigoDark = Color(0xFF3A45B8)
val LumoTeal = Color(0xFF00B8A9)
val LumoAmber = Color(0xFFFFB74D)

val LightColorScheme = lightColorScheme(
    primary = LumoIndigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE1E2FF),
    onPrimaryContainer = Color(0xFF10166B),
    secondary = LumoTeal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB8F5EC),
    onSecondaryContainer = Color(0xFF00352F),
    tertiary = LumoAmber,
    background = Color(0xFFFBFBFF),
    onBackground = Color(0xFF1A1B21),
    surface = Color(0xFFFBFBFF),
    onSurface = Color(0xFF1A1B21),
    surfaceVariant = Color(0xFFE7E8F0),
    onSurfaceVariant = Color(0xFF45464F),
    outline = Color(0xFF767680),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F5FA),
    surfaceContainer = Color(0xFFEFEFF6),
    surfaceContainerHigh = Color(0xFFE9E9F0),
    surfaceContainerHighest = Color(0xFFE3E4EB)
)

val DarkColorScheme = darkColorScheme(
    primary = LumoIndigoDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3A45B8),
    onPrimaryContainer = Color(0xFFE1E2FF),
    secondary = LumoTeal,
    onSecondary = Color(0xFF00352F),
    secondaryContainer = Color(0xFF005048),
    onSecondaryContainer = Color(0xFFB8F5EC),
    tertiary = LumoAmber,
    background = Color(0xFF121318),
    onBackground = Color(0xFFE4E2E9),
    surface = Color(0xFF121318),
    onSurface = Color(0xFFE4E2E9),
    surfaceVariant = Color(0xFF45464F),
    onSurfaceVariant = Color(0xFFC6C6D0),
    outline = Color(0xFF90919A),
    surfaceContainerLowest = Color(0xFF0D0E12),
    surfaceContainerLow = Color(0xFF1A1B21),
    surfaceContainer = Color(0xFF1E1F26),
    surfaceContainerHigh = Color(0xFF292A31),
    surfaceContainerHighest = Color(0xFF34353C)
)