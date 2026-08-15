package com.lumocraft.app.core.theme

import androidx.compose.ui.unit.dp

/**
 * Centralized spacing, sizing and bevel tokens for the Minecraft-themed
 * UI. Screens should read these instead of hardcoding `.dp` literals so
 * the blocky look stays consistent everywhere.
 */
object LumoDimens {
    /** Outer padding around a screen's scrolling content. */
    val screenPadding = 16.dp

    /** Vertical gap between top-level cards/sections on a screen. */
    val sectionGap = 16.dp

    /** Gap between rows inside a panel. */
    val itemGap = 12.dp

    /** Tight gap (chips, inline icon+label). */
    val tightGap = 8.dp

    /** Inner padding of a [LumoPanel]. */
    val panelPadding = 16.dp

    /** Width of the raised/inset bevel drawn around blocky panels. */
    val bevel = 3.dp

    /** Height of the primary Play button. */
    val playButtonHeight = 60.dp

    /** Height of standard chunky buttons. */
    val buttonHeight = 52.dp

    /** Corner radii — deliberately tiny so surfaces read "blocky". */
    val cornerSmall = 2.dp
    val cornerMedium = 3.dp
    val cornerLarge = 4.dp
}
