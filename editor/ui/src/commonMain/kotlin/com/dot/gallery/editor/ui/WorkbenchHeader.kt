package com.dot.gallery.editor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dot.gallery.editor.ui.generated.resources.Res
import com.dot.gallery.editor.ui.generated.resources.editor_back
import com.dot.gallery.editor.ui.generated.resources.editor_close
import com.dot.gallery.editor.ui.generated.resources.editor_more_options
import com.dot.gallery.editor.ui.icons.ArrowBack
import com.dot.gallery.editor.ui.icons.Close
import com.dot.gallery.editor.ui.icons.EditorSymbols
import com.dot.gallery.editor.ui.icons.MoreVert
import org.jetbrains.compose.resources.stringResource

internal const val TAG_HEADER_MORE = "editor.header.more"

/**
 * The shared 64 dp command bar — the workbench's one header anatomy for both
 * editors: Back (48 dp), the source or context title, then the trailing
 * action cluster. Flat on the window surface with no divider, matching the
 * Figma V2 header.
 *
 * [stacked] (chosen by the caller — tiny widths or enlarged type) puts the
 * title block on row one and the actions on row two so enlarged text can
 * never push Save off the edge. Export is never hidden — it lives in
 * [actions].
 */
@Composable
internal fun WorkbenchHeader(
    title: String,
    onBack: () -> Unit,
    backTag: String,
    stacked: Boolean,
    modifier: Modifier = Modifier,
    titleStyle: TextStyle = MaterialTheme.typography.titleMedium,
    floatingControls: Boolean = false,
    actions: @Composable RowScope.() -> Unit
) {
    Surface(
        color = if (floatingControls) Color.Transparent else MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (stacked) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .padding(
                            start = if (floatingControls) 16.dp else 4.dp,
                            end = if (floatingControls) 16.dp else 8.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HeaderBack(onBack, backTag, floatingControls)
                    HeaderTitle(title, Modifier.weight(1f), titleStyle)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .padding(
                            start = if (floatingControls) 16.dp else 4.dp,
                            end = if (floatingControls) 16.dp else 8.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    content = actions
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .padding(
                            start = if (floatingControls) 16.dp else 4.dp,
                            end = if (floatingControls) 16.dp else 8.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(
                        if (floatingControls) 8.dp else 0.dp
                    )
                ) {
                    HeaderBack(onBack, backTag, floatingControls)
                    HeaderTitle(title, Modifier.weight(1f), titleStyle)
                    actions()
                }
            }
        }
    }
}

/**
 * The compact context header — Back, a context title (the open tool, the
 * Tools catalogue or a pane) and a trailing More button whose dropdown is
 * the overflow surface for secondary actions. Back is contextual: it
 * unwinds the open context (cancels a tool transaction, closes the
 * catalogue or pane) rather than leaving the editor — the caller wires it.
 * [menuContent] null leaves More present but disabled.
 *
 * The optional primary action ([primaryIcon]/[primaryLabel]/
 * [onPrimaryAction]) is the V3 header's single filled icon — the compact
 * tool surface's top Apply — rendered after More.
 */
@Composable
internal fun WorkbenchContextHeader(
    title: String,
    onBack: () -> Unit,
    backTag: String,
    stacked: Boolean,
    modifier: Modifier = Modifier,
    menuContent: (@Composable ColumnScope.() -> Unit)? = null,
    primaryIcon: ImageVector? = null,
    primaryLabel: String? = null,
    onPrimaryAction: (() -> Unit)? = null,
    primaryEnabled: Boolean = true,
    primaryTag: String? = null,
    floatingControls: Boolean = false,
    // The menu variant whose items can close it — taken over
    // [menuContent] when both are supplied.
    dismissibleMenuContent: (@Composable ColumnScope.(dismiss: () -> Unit) -> Unit)? = null
) {
    WorkbenchHeader(
        title = title,
        onBack = onBack,
        backTag = backTag,
        stacked = stacked,
        // Phone context titles match the 20 sp title the Layers header
        // and the p-tool frames use.
        titleStyle = MaterialTheme.typography.titleLarge,
        floatingControls = floatingControls,
        modifier = modifier
    ) {
        var menuOpen by remember { mutableStateOf(false) }
        Box {
            WorkbenchIconButton(
                icon = EditorSymbols.MoreVert,
                label = stringResource(Res.string.editor_more_options),
                onClick = { menuOpen = true },
                enabled = menuContent != null || dismissibleMenuContent != null,
                modifier = Modifier
                    .then(
                        if (floatingControls) {
                            Modifier.clip(CircleShape).chromeBlur(CircleShape)
                        } else Modifier
                    )
                    .testTag(TAG_HEADER_MORE)
            )
            if (dismissibleMenuContent != null) {
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false }
                ) {
                    dismissibleMenuContent { menuOpen = false }
                }
            } else if (menuContent != null) {
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false }
                ) {
                    menuContent()
                }
            }
        }
        if (primaryIcon != null && primaryLabel != null &&
            onPrimaryAction != null
        ) {
            WorkbenchFilledIconButton(
                icon = primaryIcon,
                label = primaryLabel,
                onClick = onPrimaryAction,
                enabled = primaryEnabled,
                modifier = Modifier.then(
                    if (primaryTag != null) Modifier.testTag(primaryTag)
                    else Modifier
                )
            )
        }
    }
}

@Composable
private fun HeaderBack(
    onBack: () -> Unit,
    tag: String,
    floating: Boolean = false
) {
    WorkbenchIconButton(
        icon = EditorSymbols.ArrowBack,
        label = stringResource(Res.string.editor_back),
        onClick = onBack,
        modifier = Modifier
            .then(
                if (floating) Modifier.clip(CircleShape).chromeBlur(CircleShape)
                else Modifier
            )
            .testTag(tag)
    )
}

@Composable
private fun HeaderTitle(title: String, modifier: Modifier, style: TextStyle) {
    Text(
        text = title,
        style = style,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(horizontal = 8.dp)
    )
}

/**
 * The compact context header as a floating glass capsule (Stitch): Close,
 * the centred context title, the More overflow and the one filled
 * action — the tool's Apply. Anatomy matches [WorkbenchContextHeader]
 * (same tags, same slots, same menu contract) on the shared capsule
 * material; tool surfaces never carry Save or the utility row.
 */
@Composable
internal fun WorkbenchFloatingContextHeader(
    title: String,
    onBack: () -> Unit,
    backTag: String,
    stacked: Boolean,
    modifier: Modifier = Modifier,
    menuContent: (@Composable ColumnScope.() -> Unit)? = null,
    primaryIcon: ImageVector? = null,
    primaryLabel: String? = null,
    onPrimaryAction: (() -> Unit)? = null,
    primaryEnabled: Boolean = true,
    primaryTag: String? = null
) {
    val hasMenu = menuContent != null
    val hasPrimary = primaryIcon != null && primaryLabel != null &&
        onPrimaryAction != null

    @Composable
    fun RowScope.MenuSlot() {
        var menuOpen by remember { mutableStateOf(false) }
        Box {
            WorkbenchIconButton(
                icon = EditorSymbols.MoreVert,
                label = stringResource(Res.string.editor_more_options),
                onClick = { menuOpen = true },
                enabled = hasMenu,
                modifier = Modifier.testTag(TAG_HEADER_MORE)
            )
            if (hasMenu) {
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false }
                ) {
                    menuContent!!()
                }
            }
        }
    }

    @Composable
    fun RowScope.PrimarySlot() {
        if (!hasPrimary) return
        WorkbenchFilledIconButton(
            icon = primaryIcon!!,
            label = primaryLabel!!,
            onClick = onPrimaryAction!!,
            enabled = primaryEnabled,
            modifier = Modifier.then(
                if (primaryTag != null) Modifier.testTag(primaryTag)
                else Modifier
            )
        )
    }

    EditorFloatingHeaderCapsule(modifier = modifier) {
        WorkbenchIconButton(
            icon = EditorSymbols.Close,
            label = stringResource(Res.string.editor_close),
            onClick = onBack,
            modifier = Modifier.testTag(backTag)
        )
        if (stacked) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                Row(horizontalArrangement = Arrangement.End) {
                    MenuSlot()
                    PrimarySlot()
                }
            }
        } else {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
            MenuSlot()
            PrimarySlot()
        }
    }
}
