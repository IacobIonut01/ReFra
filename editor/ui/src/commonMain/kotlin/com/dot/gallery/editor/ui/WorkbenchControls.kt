package com.dot.gallery.editor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.dot.gallery.editor.ui.generated.resources.Res
import com.dot.gallery.editor.ui.generated.resources.editor_apply
import com.dot.gallery.editor.ui.generated.resources.editor_cancel
import com.dot.gallery.editor.ui.icons.EditorSymbols
import com.dot.gallery.editor.ui.icons.KeyboardArrowDown
import org.jetbrains.compose.resources.stringResource

/**
 * Shared workbench control anatomy from `editor/UI_REDESIGN.md`: the touch
 * region never dips under 48 dp while the *painted* control stays small —
 * a 20–22 dp icon inside a 32 dp field, a 3 dp slider track with a 12 dp
 * thumb, a compact 40 dp primary button. Visually small controls with large
 * hit regions are the whole point; do not shrink the regions to match.
 */

/**
 * A labelled icon control for dense chrome: icon + tooltip + TalkBack label
 * on one 48 dp node. `selected` paints the 32 dp field behind the icon and
 * exposes the `selected` semantics state — pass it only for real
 * toggle/selectable controls; plain commands leave it null so they never
 * claim a fake "not selected" state. Press, hover and keyboard focus all
 * paint visible feedback inside the same 32 dp field.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorkbenchIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val isSelected = selected == true
    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        isSelected -> MaterialTheme.colorScheme.primary
        else -> LocalContentColor.current
    }
    val button: @Composable () -> Unit = {
        // The caller's modifier (test tags, offsets) lands on the clickable
        // node itself: `clickable` is the semantics merge boundary, so the
        // click/disabled/role state and the icon's description must live on
        // the node the tag addresses — not on an outer wrapper that would
        // leave the tagged node with only the tooltip's long-click.
        Box(
            modifier = modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .then(
                    if (selected != null) {
                        Modifier.semantics { this.selected = selected }
                    } else {
                        Modifier
                    }
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .then(
                        if (focused && enabled) {
                            Modifier.border(
                                2.dp,
                                MaterialTheme.colorScheme.primary,
                                RoundedCornerShape(8.dp)
                            )
                        } else {
                            Modifier
                        }
                    )
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        when {
                            pressed && enabled -> MaterialTheme.colorScheme
                                .onSurface.copy(alpha = 0.14f)
                            hovered && enabled -> MaterialTheme.colorScheme
                                .onSurface.copy(alpha = 0.08f)
                            isSelected -> MaterialTheme.colorScheme
                                .surfaceContainerHigh
                            else -> Color.Transparent
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = contentColor,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
    WorkbenchTooltip(label) { button() }
}

/**
 * The V3 filled icon action — a 48 dp circular `primary` control with a
 * 24 dp `onPrimary` icon and full Button semantics. The tool header's
 * top Apply is its caller; it is the only primary-painted control in a
 * context header, so it never competes with the Save action on the home
 * header.
 */
@Composable
internal fun WorkbenchFilledIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.onSurface
                .copy(alpha = 0.12f),
            disabledContentColor = MaterialTheme.colorScheme.onSurface
                .copy(alpha = 0.38f)
        ),
        modifier = modifier.size(48.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(24.dp)
        )
    }
}

/**
 * A plain hover tooltip around any workbench control. Tooltip popups own
 * their own subscene; tests disable them via [LocalWorkbenchTooltipsEnabled]
 * so injected pointer moves can't race a tooltip subscene's dispose
 * ("RootNodeOwner is already disposed").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorkbenchTooltip(
    label: String,
    content: @Composable () -> Unit
) {
    if (LocalWorkbenchTooltipsEnabled.current) {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
            tooltip = { PlainTooltip { Text(label) } },
            state = rememberTooltipState(),
            content = content
        )
    } else {
        content()
    }
}

/**
 * Whether [WorkbenchIconButton] wraps its control in a hover tooltip. On by
 * default; UI tests turn it off — a tooltip popup is a second subscene whose
 * queued redraw can outlive its owner and crash the test render loop.
 */
internal val LocalWorkbenchTooltipsEnabled = compositionLocalOf { true }

/** Painted slider anatomy shared by the slot track and the M3 thumb. */
private val THUMB_SIZE = DpSize(12.dp, 12.dp)
private val THUMB_WIDTH = 12.dp

/**
 * The single filled action of a workbench header (Save copy / Save video).
 * Compact 40 dp visible height with a 48 dp touch floor; only this button
 * carries the primary fill.
 */
@Composable
internal fun WorkbenchPrimaryAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** Optional leading 18 dp glyph — the crop reference's check mark. */
    icon: ImageVector? = null,
    /** A 40 dp pill with a smaller label — the secondary action row. */
    compact: Boolean = false
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        // The approved primary action is a full pill — the Material3
        // default button shape, not a rounded rectangle.
        shape = ButtonDefaults.shape,
        contentPadding = if (compact) {
            PaddingValues(horizontal = 16.dp, vertical = 0.dp)
        } else {
            ButtonDefaults.ContentPadding
        },
        modifier = modifier
            .heightIn(min = if (compact) 40.dp else 48.dp)
            .widthIn(min = if (compact) 48.dp else 64.dp)
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(if (compact) 16.dp else 18.dp)
            )
            Spacer(Modifier.width(if (compact) 4.dp else 6.dp))
        }
        Text(
            label,
            maxLines = 1,
            style = if (compact) {
                MaterialTheme.typography.labelMedium
            } else {
                MaterialTheme.typography.labelLarge
            }
        )
    }
}

/**
 * The two-part primary action of the Figma V2 video header: a 124 x 48 dp
 * outer field holding two visible 40 dp parts with a 2 dp gap — the
 * leading label pill runs [onClick], the trailing 48 x 40 dp chevron
 * opens a real dropdown supplied by the caller. Touch regions keep the
 * full 48 dp height while the painted buttons sit at 40.
 */
@Composable
internal fun WorkbenchSplitAction(
    label: String,
    onClick: () -> Unit,
    menuLabel: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingTag: String? = null,
    trailingTag: String? = null,
    menuContent: @Composable ColumnScope.() -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val container = if (enabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    }
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }
    Row(
        modifier = modifier.size(width = 124.dp, height = 48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .then(if (leadingTag != null) Modifier.testTag(leadingTag) else Modifier)
                .clickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClickLabel = label,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 20.dp, bottomStart = 20.dp,
                            topEnd = 4.dp, bottomEnd = 4.dp
                        )
                    )
                    .background(container),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor,
                    maxLines = 1
                )
            }
        }
        Spacer(Modifier.width(2.dp))
        Box(
            modifier = Modifier
                .width(48.dp)
                .fillMaxHeight()
                .then(if (trailingTag != null) Modifier.testTag(trailingTag) else Modifier)
                .clickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClickLabel = menuLabel,
                    onClick = { menuOpen = true }
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 4.dp, bottomStart = 4.dp,
                            topEnd = 20.dp, bottomEnd = 20.dp
                        )
                    )
                    .background(container),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = EditorSymbols.KeyboardArrowDown,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false }
            ) {
                menuContent()
            }
        }
    }
}

/**
 * The thin adjustment slider — a 3 dp track with a 12 dp thumb, no end dots,
 * no thumb/track gap, on a 48 dp hit lane. [valueRange] defaults to the
 * normalized `0..1`; callers may pass their real domain (e.g. EV stops) so
 * TalkBack's setProgress speaks user units. Takes the caller's
 * [interactionSource] so drag outcome (commit vs cancel) can be observed
 * where the draft lives.
 */
@Composable
internal fun WorkbenchSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f
) {
    val colors = SliderDefaults.colors(
        thumbColor = MaterialTheme.colorScheme.primary,
        activeTrackColor = MaterialTheme.colorScheme.primary,
        inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        disabledThumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        disabledActiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        disabledInactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh
    )
    Slider(
        value = value.coerceIn(valueRange.start, valueRange.endInclusive),
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = valueRange,
        interactionSource = interactionSource,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        thumb = {
            SliderDefaults.Thumb(
                interactionSource = interactionSource,
                thumbSize = THUMB_SIZE,
                colors = colors,
                enabled = enabled
            )
        },
        track = { sliderState ->
            // M3's slider layout forces the track child to at least its
            // 16 dp token height — shrinking the modifier cannot win. So
            // the slot fills its lane and *paints* a centred 3 dp line;
            // the painted track is thin while the hit region stays ≥48 dp.
            // The active half ends under the thumb centre, matching the
            // Slider's own thumb travel (thumb inset half-width each side).
            val span = sliderState.valueRange.endInclusive -
                sliderState.valueRange.start
            val fraction = if (span > 0f) {
                ((sliderState.value - sliderState.valueRange.start) / span)
                    .coerceIn(0f, 1f)
            } else {
                0f
            }
            val activeColor = if (enabled) {
                colors.activeTrackColor
            } else {
                colors.disabledActiveTrackColor
            }
            val inactiveColor = if (enabled) {
                colors.inactiveTrackColor
            } else {
                colors.disabledInactiveTrackColor
            }
            Canvas(modifier = Modifier.fillMaxWidth().heightIn(min = 3.dp)) {
                val lineHeight = 3.dp.toPx()
                val top = (size.height - lineHeight) / 2f
                val corner = CornerRadius(lineHeight / 2f)
                drawRoundRect(
                    color = inactiveColor,
                    topLeft = Offset(0f, top),
                    size = Size(size.width, lineHeight),
                    cornerRadius = corner
                )
                val thumbPx = THUMB_WIDTH.toPx()
                val activeEnd =
                    thumbPx / 2f + fraction * (size.width - thumbPx)
                if (activeEnd > 0f) {
                    drawRoundRect(
                        color = activeColor,
                        topLeft = Offset(0f, top),
                        size = Size(
                            activeEnd.coerceAtMost(size.width), lineHeight
                        ),
                        cornerRadius = corner
                    )
                }
            }
        }
    )
}

internal const val TAG_TOOL_CANCEL = "editor.tool.cancel"
internal const val TAG_TOOL_APPLY = "editor.tool.apply"

/**
 * The fixed Cancel/Apply bar shown while a tool owns the session draft —
 * in place of the compact navigation pill or at the foot of the wide
 * inspector. Cancel is a text action, Apply the single filled pill,
 * matching the approved `p-light` actions row. Apply is gated separately:
 * the session refuses to apply while an interaction is in flight, so the
 * button mirrors that precondition.
 */
@Composable
internal fun EditorToolActionsBar(
    onCancel: () -> Unit,
    onApply: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    applyEnabled: Boolean = enabled
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(
            onClick = onCancel,
            enabled = enabled,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag(TAG_TOOL_CANCEL)
        ) {
            Text(
                stringResource(Res.string.editor_cancel),
                maxLines = 1,
                style = MaterialTheme.typography.titleMedium
            )
        }
        Spacer(Modifier.weight(1f))
        WorkbenchPrimaryAction(
            label = stringResource(Res.string.editor_apply),
            onClick = onApply,
            enabled = applyEnabled,
            modifier = Modifier.testTag(TAG_TOOL_APPLY)
        )
    }
}

/**
 * A labelled destination in a bottom dock or side rail — icon (22 dp by
 * default) over a label on one merged ≥48 dp node. `selected` paints the
 * field behind the icon and exposes the `selected` semantics property;
 * destinations only — commands are [WorkbenchIconButton]s.
 */
@Composable
internal fun WorkbenchDockItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    iconSize: Dp = 22.dp,
    labelStyle: TextStyle? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    Column(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Tab,
                onClickLabel = label,
                onClick = onClick
            )
            .semantics { this.selected = selected }
            .padding(vertical = 4.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .then(
                    if (focused && enabled) {
                        Modifier.border(
                            2.dp,
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(8.dp)
                        )
                    } else {
                        Modifier
                    }
                )
                .clip(RoundedCornerShape(8.dp))
                .background(
                    when {
                        pressed && enabled -> MaterialTheme.colorScheme
                            .onSurface.copy(alpha = 0.14f)
                        hovered && enabled -> MaterialTheme.colorScheme
                            .onSurface.copy(alpha = 0.08f)
                        selected -> MaterialTheme.colorScheme.surfaceVariant
                        else -> Color.Transparent
                    }
                )
                // 4 dp inner padding: 8 dp left a 64 dp dock cell only
                // ~40 dp for the label — enough to clip "Colour".
                .padding(horizontal = 4.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = when {
                        !enabled -> MaterialTheme.colorScheme.onSurface
                            .copy(alpha = 0.38f)
                        selected -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(iconSize)
                )
                Text(
                    text = label,
                    style = labelStyle ?: MaterialTheme.typography.labelMedium,
                    color = when {
                        !enabled -> MaterialTheme.colorScheme.onSurface
                            .copy(alpha = 0.38f)
                        selected -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
            }
        }
    }
}
