/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.viewer.insight

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.presentation.util.LocalHazeState
import com.dot.gallery.core.presentation.util.hazeEffectScaled
import com.dot.gallery.feature.viewer.generated.resources.Res
import com.dot.gallery.feature.viewer.generated.resources.text_chip_kind
import com.dot.gallery.feature.viewer.generated.resources.text_chip_lines
import com.dot.gallery.feature.viewer.generated.resources.text_chip_one_line
import com.dot.gallery.feature.viewer.generated.resources.text_close_selection
import com.dot.gallery.feature.viewer.generated.resources.text_copy
import com.dot.gallery.feature.viewer.generated.resources.text_found_announcement
import com.dot.gallery.feature.viewer.generated.resources.text_read
import com.dot.gallery.feature.viewer.generated.resources.text_reading
import com.dot.gallery.feature.viewer.generated.resources.text_search
import com.dot.gallery.feature.viewer.generated.resources.text_search_in_refra
import com.dot.gallery.feature.viewer.generated.resources.text_select_all
import com.dot.gallery.feature.viewer.generated.resources.text_selected_count
import com.dot.gallery.feature.viewer.generated.resources.text_selected_one
import com.dot.gallery.feature.viewer.generated.resources.text_share
import com.dot.gallery.feature.viewer.generated.resources.text_tap_to_stop
import dev.chrisbanes.haze.blur.materials.HazeMaterials
import org.jetbrains.compose.resources.stringResource

/** What the Text chip shows: lines found, an offer to read (on request), or reading in progress. */
enum class TextChipMode { FOUND, READ, READING }

/**
 * The floating Text chip above the quick bar. Frosted like the rest of the viewer chrome:
 * primaryContainer at 80 % over a backdrop blur.
 */
@Composable
fun TextChip(
    mode: TextChipMode,
    lineCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kindOverride: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    val container = colors.primaryContainer
    val kind = kindOverride ?: when (mode) {
        TextChipMode.READING -> stringResource(Res.string.text_tap_to_stop)
        else -> stringResource(Res.string.text_chip_kind)
    }
    val value = when (mode) {
        TextChipMode.FOUND -> if (lineCount == 1) stringResource(Res.string.text_chip_one_line) else stringResource(Res.string.text_chip_lines, lineCount)
        TextChipMode.READ -> stringResource(Res.string.text_read)
        TextChipMode.READING -> stringResource(Res.string.text_reading)
    }
    val description = if (mode == TextChipMode.FOUND) stringResource(Res.string.text_found_announcement, lineCount) else value
    Row(
        modifier = modifier
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(container.copy(alpha = 0.8f))
            .hazeEffectScaled(
                state = LocalHazeState.current,
                style = HazeMaterials.thin(containerColor = container),
                glassContainerColor = container,
            )
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(colors.primary), contentAlignment = Alignment.Center) {
            if (mode == TextChipMode.READING) {
                CircularProgressIndicator(Modifier.size(20.dp), color = colors.onPrimary, strokeWidth = 2.dp)
            } else {
                Icon(if (mode == TextChipMode.READ) Icons.Outlined.DocumentScanner else Icons.Outlined.TextFields, null, tint = colors.onPrimary)
            }
        }
        Column(Modifier.widthIn(max = 220.dp)) {
            Text(kind, style = MaterialTheme.typography.labelSmall, color = colors.onPrimaryContainer.copy(alpha = 0.78f))
            Text(value, style = MaterialTheme.typography.titleSmall, color = colors.onPrimaryContainer)
        }
        if (mode != TextChipMode.READING) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = colors.onPrimaryContainer)
        }
    }
}

/** The header that replaces the top bar while selecting: Close and the selected-line count. */
@Composable
fun TextSelectionHeader(count: Int, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(colors.surfaceContainer.copy(alpha = 0.65f))
            .hazeEffectScaled(state = LocalHazeState.current, style = HazeMaterials.thin(containerColor = colors.surfaceContainer))
            .padding(end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClose),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.Close, stringResource(Res.string.text_close_selection), tint = colors.onSurface) }
        Text(
            if (count == 1) stringResource(Res.string.text_selected_one) else stringResource(Res.string.text_selected_count, count),
            style = MaterialTheme.typography.titleSmall,
            color = colors.onSurface,
        )
    }
}

/** The frosted bar that replaces the quick bar while selecting. Search is hidden for vault photos. */
@Composable
fun TextSelectionBar(
    showSearch: Boolean,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onSelectAll: () -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(34.dp))
            .background(colors.surfaceContainer.copy(alpha = 0.65f))
            .hazeEffectScaled(state = LocalHazeState.current, style = HazeMaterials.thin(containerColor = colors.surfaceContainer))
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BarAction(Icons.Outlined.ContentCopy, stringResource(Res.string.text_copy), null, primary = true, onClick = onCopy)
        BarAction(Icons.Outlined.Share, stringResource(Res.string.text_share), null, onClick = onShare)
        BarAction(Icons.Outlined.SelectAll, stringResource(Res.string.text_select_all), null, onClick = onSelectAll)
        if (showSearch) {
            BarAction(Icons.Outlined.Search, stringResource(Res.string.text_search), stringResource(Res.string.text_search_in_refra), onClick = onSearch)
        }
    }
}

@Composable
private fun BarAction(icon: ImageVector, label: String, description: String?, primary: Boolean = false, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val content = if (primary) colors.onSecondaryContainer else colors.onSurface
    Column(
        modifier = Modifier
            .widthIn(min = 76.dp)
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(28.dp))
            .then(if (primary) Modifier.background(colors.secondaryContainer) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = content)
        Text(label, style = MaterialTheme.typography.labelMedium, color = content)
    }
}
