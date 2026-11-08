/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Deselect
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.core.LocalMediaSelector
import com.dot.gallery.core.ui.util.rememberedDerivedState
import com.dot.gallery.domain.model.Media
import com.dot.gallery.domain.model.MediaState
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.clear_selection
import com.dot.gallery.generated.resources.media_details
import com.dot.gallery.generated.resources.select_all
import com.dot.gallery.generated.resources.selection_dialog_close_cd
import com.dot.gallery.ui.theme.GalleryChrome
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** True inside the rail panel: selection actions render as icon-only rail segments. */
val LocalSelectionBarCompact = staticCompositionLocalOf { false }

/**
 * The selection bar as it appears inside the wide-window rail: close (with the selection count),
 * select all and details on top, then the same configured actions the bottom bar offers, stacked
 * one per row so `RowScope` actions keep working.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <T : Media> RailSelectionPanel(
    allMedia: MediaState<T>,
    selectedCount: Int,
    showSelectAll: Boolean,
    showInfo: Boolean,
    onClose: () -> Unit,
    onInfo: () -> Unit,
    actions: @Composable RowScope.() -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        RailSelectionButton(
            imageVector = Icons.Outlined.Close,
            contentDescription = stringResource(Res.string.selection_dialog_close_cd),
            label = selectedCount.toString(),
            onClick = onClose,
        )
        if (showSelectAll) RailSelectAllButton(allMedia)
        AnimatedVisibility(visible = showInfo) {
            RailSelectionButton(
                imageVector = Icons.Outlined.Info,
                contentDescription = stringResource(Res.string.media_details),
                onClick = onInfo,
            )
        }
        HorizontalDivider(
            modifier = Modifier.width(32.dp).padding(vertical = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        CompositionLocalProvider(LocalSelectionBarCompact provides true) {
            FlowRow(
                maxItemsInEachRow = 1,
                horizontalArrangement = Arrangement.Center,
            ) {
                actions()
            }
        }
    }
}

@Composable
private fun <T : Media> RailSelectAllButton(allMedia: MediaState<T>) {
    val scope = rememberCoroutineScope()
    val selector = LocalMediaSelector.current
    val selectedMedia by selector.selectedMedia.collectAsStateWithLifecycle()
    val selectedAll by rememberedDerivedState(selectedMedia, allMedia) {
        selectedMedia.size == allMedia.media.size && allMedia.media.isNotEmpty()
    }
    val container by animateColorAsState(
        if (selectedAll) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        label = "railSelectAllContainer",
    )
    RailSelectionButton(
        imageVector = if (selectedAll) Icons.Outlined.Deselect else Icons.Outlined.SelectAll,
        contentDescription = stringResource(
            if (selectedAll) Res.string.clear_selection else Res.string.select_all,
        ),
        containerColor = container,
        contentColor = if (selectedAll) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurface,
        onClick = {
            scope.launch {
                if (selectedAll) selector.clearSelection()
                else selector.addToSelection(allMedia.media.map { it.id })
            }
        },
    )
}

@Composable
private fun RailSelectionButton(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    label: String? = null,
    containerColor: Color = Color.Transparent,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Box(
        modifier = Modifier
            .size(GalleryChrome.RailItemWidth)
            .padding(4.dp)
            .clip(RoundedCornerShape(GalleryChrome.IndicatorCornerRadius))
            .background(containerColor)
            .clickable(role = Role.Button, onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                tint = contentColor,
                modifier = Modifier.size(24.dp),
            )
            if (label != null) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor,
                    maxLines = 1,
                )
            }
        }
    }
}
