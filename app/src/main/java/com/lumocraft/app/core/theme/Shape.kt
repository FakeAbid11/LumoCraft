package com.lumocraft.app.core.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Blocky shape scale. Minecraft's GUI is built from hard rectangles, so
 * every radius is intentionally tiny (2–4 dp): enough to avoid a razor
 * edge, small enough to read as pixel/blocky rather than rounded.
 */
val LumoShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(2.dp),
    medium = RoundedCornerShape(3.dp),
    large = RoundedCornerShape(4.dp),
    extraLarge = RoundedCornerShape(4.dp),
)

/** Shared shapes used directly by the blocky components. */
val PanelShape = RoundedCornerShape(3.dp)
val ButtonShape = RoundedCornerShape(2.dp)
val ChipShape = RoundedCornerShape(2.dp)
