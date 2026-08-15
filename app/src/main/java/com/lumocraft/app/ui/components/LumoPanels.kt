package com.lumocraft.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lumocraft.app.core.theme.ButtonShape
import com.lumocraft.app.core.theme.LumoDimens
import com.lumocraft.app.core.theme.PanelShape
import com.lumocraft.app.core.theme.lumoColors

/**
 * Shared blocky UI primitives styled after the Minecraft GUI: flat
 * panels with 2–3 dp beveled edges (light top/left, dark bottom/right),
 * chunky beveled buttons, and the settings rows that used to be
 * duplicated across screens. Depth comes from bevels, not Material
 * shadows, so every panel sets zero elevation.
 */

/**
 * Draws a Minecraft-style bevel behind content: a light highlight on the
 * top/left edges and a dark shadow on the bottom/right. When [inset] the
 * edges are reversed to look recessed (used for consoles/log areas).
 */
private fun Modifier.lumoBevel(
    light: Color,
    dark: Color,
    width: Float,
    inset: Boolean,
): Modifier = drawBehind {
    val top = if (inset) dark else light
    val left = if (inset) dark else light
    val bottom = if (inset) light else dark
    val right = if (inset) light else dark
    // top
    drawRect(color = top, topLeft = androidx.compose.ui.geometry.Offset(0f, 0f),
        size = androidx.compose.ui.geometry.Size(size.width, width))
    // left
    drawRect(color = left, topLeft = androidx.compose.ui.geometry.Offset(0f, 0f),
        size = androidx.compose.ui.geometry.Size(width, size.height))
    // bottom
    drawRect(color = bottom, topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - width),
        size = androidx.compose.ui.geometry.Size(size.width, width))
    // right
    drawRect(color = right, topLeft = androidx.compose.ui.geometry.Offset(size.width - width, 0f),
        size = androidx.compose.ui.geometry.Size(width, size.height))
}

// PLACEHOLDER_COMPONENTS

/**
 * A blocky Minecraft-GUI panel: flat stone fill, hard border, beveled
 * edges, no shadow. Replaces the ad-hoc `Card(surfaceContainerLow)`
 * surfaces used across the app.
 *
 * @param inset when true the bevel is recessed (for consoles/log areas).
 */
@Composable
fun LumoPanel(
    modifier: Modifier = Modifier,
    inset: Boolean = false,
    contentPadding: androidx.compose.foundation.layout.PaddingValues =
        androidx.compose.foundation.layout.PaddingValues(LumoDimens.panelPadding),
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val colors = lumoColors()
    val bevelPx = with(androidx.compose.ui.platform.LocalDensity.current) { LumoDimens.bevel.toPx() }
    Box(
        modifier = modifier
            .clip(PanelShape)
            .background(if (inset) colors.panelInset else colors.panelFill)
            .lumoBevel(colors.panelBevelLight, colors.panelBevelDark, bevelPx, inset)
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

/**
 * Section header (pixel title) followed by a [LumoPanel] holding [content].
 * Replaces the duplicated SettingsSection / PerformanceSection /
 * DiagnosticsSection wrappers.
 */
@Composable
fun LumoSectionPanel(
    title: String,
    modifier: Modifier = Modifier,
    contentPadding: androidx.compose.foundation.layout.PaddingValues =
        androidx.compose.foundation.layout.PaddingValues(LumoDimens.panelPadding),
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(LumoDimens.tightGap)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        LumoPanel(contentPadding = contentPadding, content = content)
    }
}

/** Label/value row used by Runtime, Diagnostics and Performance screens. */
@Composable
fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f).padding(start = LumoDimens.tightGap)
        )
    }
}
// PLACEHOLDER_ROWS

/** Labeled slider with a trailing value readout (accent-tinted track). */
@Composable
fun SliderRow(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    valueLabel: String? = null,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (valueLabel != null) {
                Text(
                    text = valueLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            onValueChangeFinished = onValueChangeFinished,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
            )
        )
    }
}

/** Labeled switch with optional supporting description. */
@Composable
fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = LumoDimens.itemGap)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
            )
        )
    }
}
// PLACEHOLDER_BUTTONS

/**
 * A chunky beveled Minecraft-style button. Flat [containerColor] fill,
 * bright top/left edge and dark bottom/right edge, pixel label. Disabled
 * state desaturates to a dim stone fill so "not ready" reads clearly.
 */
@Composable
fun LumoButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    prominent: Boolean = false,
    containerColor: Color = lumoColors().accent,
    edgeColor: Color = lumoColors().accentEdge,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
) {
    val colors = lumoColors()
    val bevelPx = with(androidx.compose.ui.platform.LocalDensity.current) { LumoDimens.bevel.toPx() }
    val fill = if (enabled) containerColor else colors.panelFill
    val light = if (enabled) edgeColor else colors.panelBevelLight
    val onColor = if (enabled) contentColor else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = modifier
            .height(if (prominent) LumoDimens.playButtonHeight else LumoDimens.buttonHeight)
            .clip(ButtonShape)
            .background(fill)
            .lumoBevel(light, colors.panelBevelDark, bevelPx, inset = false)
            .then(
                if (enabled) Modifier.androidxClickable(onClick) else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = LumoDimens.panelPadding)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = onColor,
                    modifier = Modifier.size(if (prominent) 26.dp else 20.dp)
                )
                Spacer(modifier = Modifier.width(LumoDimens.tightGap))
            }
            Text(
                text = text,
                style = if (prominent) MaterialTheme.typography.titleMedium
                else MaterialTheme.typography.labelLarge,
                color = onColor,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** The primary Play action: a prominent grass-green beveled button. */
@Composable
fun LumoPlayButton(
    text: String,
    onClick: () -> Unit,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    LumoButton(
        text = text,
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        icon = icon,
        enabled = enabled,
        prominent = true,
    )
}

/** Thin wrapper so callers don't need the foundation clickable import. */
private fun Modifier.androidxClickable(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)



