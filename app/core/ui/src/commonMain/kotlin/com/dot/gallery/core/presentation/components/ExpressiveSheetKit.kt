/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.Position
import com.dot.gallery.core.ui.util.AppBottomSheetState
import kotlinx.coroutines.launch

/** Top corners of every redesigned bottom sheet. */
val ExpressiveSheetShape: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

/** Colour role of a sheet badge, summary or emphasised row. */
enum class SheetTone { Primary, Secondary, Tertiary, Error }

@Composable
fun SheetTone.containerColor(): Color = when (this) {
    SheetTone.Primary -> MaterialTheme.colorScheme.primaryContainer
    SheetTone.Secondary -> MaterialTheme.colorScheme.secondaryContainer
    SheetTone.Tertiary -> MaterialTheme.colorScheme.tertiaryContainer
    SheetTone.Error -> MaterialTheme.colorScheme.errorContainer
}

@Composable
fun SheetTone.contentColor(): Color = when (this) {
    SheetTone.Primary -> MaterialTheme.colorScheme.onPrimaryContainer
    SheetTone.Secondary -> MaterialTheme.colorScheme.onSecondaryContainer
    SheetTone.Tertiary -> MaterialTheme.colorScheme.onTertiaryContainer
    SheetTone.Error -> MaterialTheme.colorScheme.onErrorContainer
}

/** Where a row sits in its group, for the 24/8 dp grouped corners. */
fun sheetRowPosition(index: Int, count: Int): Position = when {
    count <= 1 -> Position.Alone
    index == 0 -> Position.Top
    index == count - 1 -> Position.Bottom
    else -> Position.Middle
}

fun sheetGroupedShape(position: Position): Shape = when (position) {
    Position.Alone -> RoundedCornerShape(24.dp)
    Position.Top -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
    Position.Middle -> RoundedCornerShape(8.dp)
    Position.Bottom -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
}

/**
 * Modal frame of the redesigned sheets: surfaceContainerLow, 28 dp top corners and the shared
 * drag handle, with [content] laid out by [ExpressiveSheetColumn].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpressiveSheet(
    sheetState: AppBottomSheetState,
    modifier: Modifier = Modifier,
    paneTitle: String? = null,
    onDismissRequest: suspend () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    if (!sheetState.isVisible) return
    ModalBottomSheet(
        modifier = modifier.semantics { paneTitle?.let { this.paneTitle = it } },
        sheetState = sheetState.sheetState,
        onDismissRequest = {
            scope.launch {
                sheetState.hide()
                onDismissRequest()
            }
        },
        dragHandle = { DragHandle(modifier = Modifier.padding(vertical = 5.dp)) },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        shape = ExpressiveSheetShape,
    ) {
        ExpressiveSheetColumn(content = content)
    }
}

/**
 * Body of a redesigned sheet, usable on its own (previews, tests and sheets that bring their own
 * container): 16 dp sides, 24 dp bottom above the navigation bar, scrollable.
 */
@Composable
fun ExpressiveSheetColumn(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        content = content,
    )
}

/**
 * Sheet heading. With an [icon] the header is centred under a round badge in [tone], with a
 * headlineSmall title (confirmations); without one it is a left-aligned titleLarge.
 */
@Composable
fun SheetHeader(
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    icon: ImageVector? = null,
    tone: SheetTone = SheetTone.Primary,
    centered: Boolean = icon != null,
) {
    val align = if (centered) TextAlign.Center else TextAlign.Start
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = if (centered) 20.dp else 16.dp),
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(if (centered) 12.dp else 8.dp),
    ) {
        if (icon != null) SheetIconBadge(icon = icon, tone = tone)
        Text(
            text = title,
            style = if (centered) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = align,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { heading() },
        )
        if (body != null) {
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = align,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Round 56 dp badge with a 24 dp icon. */
@Composable
fun SheetIconBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tone: SheetTone = SheetTone.Primary,
) {
    Box(
        modifier = modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(tone.containerColor()),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tone.contentColor())
    }
}

/** Small label above a group inside a sheet. */
@Composable
fun SheetSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 16.dp, bottom = 8.dp)
            .semantics { heading() },
    )
}

/** Rounded tile that shows what an action affects: a leading picture, a title and a summary. */
@Composable
fun SheetSummaryTile(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(start = 12.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** What a [SheetOptionRow] shows at its end. */
sealed interface SheetRowTrailing {
    data object None : SheetRowTrailing
    data object Chevron : SheetRowTrailing
    data class Radio(val selected: Boolean) : SheetRowTrailing
    data class Switch(val checked: Boolean) : SheetRowTrailing
    data class Checkbox(val checked: Boolean) : SheetRowTrailing
    class Custom(val content: @Composable () -> Unit) : SheetRowTrailing
}

/**
 * One row of a grouped list inside a sheet: icon, title, optional summary, grouped corners from
 * [position] and a radio, switch, checkbox, chevron or custom trailing element.
 */
@Composable
fun SheetOptionRow(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    summary: String? = null,
    position: Position = Position.Alone,
    trailing: SheetRowTrailing = SheetRowTrailing.None,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    leading: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val gap = when (position) {
        Position.Alone -> PaddingValues(0.dp)
        Position.Top -> PaddingValues(bottom = 1.dp)
        Position.Middle -> PaddingValues(vertical = 1.dp)
        Position.Bottom -> PaddingValues(top = 1.dp)
    }
    val interaction = when {
        onClick == null -> Modifier
        trailing is SheetRowTrailing.Radio ->
            Modifier.selectable(selected = trailing.selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
        trailing is SheetRowTrailing.Switch ->
            Modifier.toggleable(value = trailing.checked, enabled = enabled, role = Role.Switch) { onClick() }
        trailing is SheetRowTrailing.Checkbox ->
            Modifier.toggleable(value = trailing.checked, enabled = enabled, role = Role.Checkbox) { onClick() }
        else -> Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
    }
    val alpha = if (enabled) 1f else 0.38f
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(gap)
            .clip(sheetGroupedShape(position))
            .background(containerColor)
            .then(interaction)
            .heightIn(min = 72.dp)
            .padding(
                start = 16.dp,
                end = if (trailing is SheetRowTrailing.Switch || trailing == SheetRowTrailing.None) 16.dp else 8.dp,
                top = 12.dp,
                bottom = 12.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when {
            leading != null -> Box(Modifier.alpha(alpha)) { leading() }
            icon != null -> Icon(
                imageVector = icon,
                contentDescription = null,
                tint = (if (contentColor == MaterialTheme.colorScheme.onSurface) MaterialTheme.colorScheme.onSurfaceVariant
                else contentColor).copy(alpha = alpha),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = contentColor.copy(alpha = alpha),
            )
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = (if (contentColor == MaterialTheme.colorScheme.onSurface) MaterialTheme.colorScheme.onSurfaceVariant
                    else contentColor).copy(alpha = alpha),
                )
            }
        }
        when (trailing) {
            SheetRowTrailing.None -> Unit
            SheetRowTrailing.Chevron -> Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                )
            }
            is SheetRowTrailing.Radio -> Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                RadioButton(selected = trailing.selected, onClick = null, enabled = enabled)
            }
            is SheetRowTrailing.Checkbox -> Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Checkbox(checked = trailing.checked, onCheckedChange = null, enabled = enabled)
            }
            is SheetRowTrailing.Switch -> Switch(checked = trailing.checked, onCheckedChange = null, enabled = enabled)
            is SheetRowTrailing.Custom -> Box(Modifier.defaultMinSize(48.dp, 48.dp), contentAlignment = Alignment.Center) {
                trailing.content()
            }
        }
    }
}

/** Square-ish action tile of the three-column action grids (album options). */
@Composable
fun SheetActionTile(
    text: String,
    icon: ImageVector?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    summary: String? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(
        modifier = modifier
            .defaultMinSize(minHeight = 88.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(containerColor)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else 0.38f)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        if (icon != null) Icon(imageVector = icon, contentDescription = null, tint = contentColor)
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (summary != null) {
            Text(
                text = summary,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

enum class SheetButtonStyle { Filled, Tonal, Text, Error }

/** 48 dp pill button in the redesigned sheet styles, with an optional leading icon. */
@Composable
fun SheetButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: SheetButtonStyle = SheetButtonStyle.Filled,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val height = Modifier.heightIn(min = 48.dp)
    val padding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
    val label: @Composable RowScope.() -> Unit = {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
    when (style) {
        SheetButtonStyle.Filled -> Button(onClick, modifier.then(height), enabled, contentPadding = padding, content = label)
        SheetButtonStyle.Tonal -> FilledTonalButton(onClick, modifier.then(height), enabled, contentPadding = padding, content = label)
        SheetButtonStyle.Text -> TextButton(onClick, modifier.then(height), enabled, contentPadding = PaddingValues(horizontal = 16.dp), content = label)
        SheetButtonStyle.Error -> FilledTonalButton(
            onClick = onClick,
            modifier = modifier.then(height),
            enabled = enabled,
            contentPadding = padding,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
            content = label,
        )
    }
}

/** Bottom action row of a sheet: buttons share the width, 24 dp above them. */
@Composable
fun SheetActions(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/** The common Cancel + confirm pair. */
@Composable
fun SheetDismissConfirmActions(
    dismissText: String,
    onDismiss: () -> Unit,
    confirmText: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    confirmStyle: SheetButtonStyle = SheetButtonStyle.Filled,
    confirmIcon: ImageVector? = null,
    confirmEnabled: Boolean = true,
) {
    SheetActions(modifier) {
        SheetButton(text = dismissText, onClick = onDismiss, style = SheetButtonStyle.Tonal, modifier = Modifier.weight(1f))
        SheetButton(
            text = confirmText,
            onClick = onConfirm,
            style = confirmStyle,
            icon = confirmIcon,
            enabled = confirmEnabled,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Filled text field of the redesigned sheets: 16 dp corners, no underline. */
@Composable
fun SheetTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = singleLine,
        shape = RoundedCornerShape(16.dp),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        trailingIcon = trailingIcon,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            errorIndicatorColor = Color.Transparent,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Empty or permission state of the redesigned screens: a 96 dp tonal badge, title and body. */
@Composable
fun ExpressiveEmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(40.dp),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (body != null) {
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        action?.invoke()
    }
}
