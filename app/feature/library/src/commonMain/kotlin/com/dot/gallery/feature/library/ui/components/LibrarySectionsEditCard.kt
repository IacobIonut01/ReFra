/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.library.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PermMedia
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.feature.library.generated.resources.Res
import com.dot.gallery.feature.library.generated.resources.library_section_album_sections
import com.dot.gallery.feature.library.generated.resources.library_section_all_shortcuts
import com.dot.gallery.feature.library.generated.resources.library_section_all_shortcuts_summary
import com.dot.gallery.feature.library.generated.resources.library_section_album_sections_summary
import com.dot.gallery.feature.library.generated.resources.library_section_media_types
import com.dot.gallery.feature.library.generated.resources.library_section_media_types_summary
import com.dot.gallery.feature.library.generated.resources.library_section_pinned
import com.dot.gallery.feature.library.generated.resources.library_section_pinned_summary
import com.dot.gallery.feature.library.generated.resources.library_sections_title


/** Outer corners round the group, inner corners stay tight, as in the settings lists. */
private fun connectedRowShape(index: Int, count: Int): RoundedCornerShape {
    val outer = 24.dp
    val inner = 8.dp
    return RoundedCornerShape(
        topStart = if (index == 0) outer else inner,
        topEnd = if (index == 0) outer else inner,
        bottomStart = if (index == count - 1) outer else inner,
        bottomEnd = if (index == count - 1) outer else inner,
    )
}

/** One section toggle row inside [LibrarySectionsEditCard]. */
private data class SectionEditRow(
    val id: String,
    val titleRes: StringResource,
    val icon: ImageVector,
    val checked: Boolean,
    val enabled: Boolean = true,
    /** Explains the current state under the title — e.g. an empty or unavailable section. */
    val hintRes: StringResource? = null,
    val onChange: (Boolean) -> Unit
)

/**
 * "Sections" list shown under the shortcuts grid while edit mode is on: the switches whose
 * callbacks are set, for every shortcut, pinned albums, album sections and media type albums.
 * Locations, People and Categories are shortcut tiles, so they are hidden and resized in the
 * grid above instead. Toggles apply immediately.
 */
@Composable
fun LibrarySectionsEditCard(
    modifier: Modifier = Modifier,
    showPinned: Boolean = true,
    onPinnedChange: ((Boolean) -> Unit)? = null,
    showMediaTypes: Boolean = true,
    onMediaTypesChange: ((Boolean) -> Unit)? = null,
    groupAlbumsIntoSections: Boolean = false,
    onGroupAlbumsIntoSectionsChange: ((Boolean) -> Unit)? = null,
    showAllShortcuts: Boolean = false,
    onShowAllShortcutsChange: ((Boolean) -> Unit)? = null,
) {
    val rows = listOfNotNull(
        onShowAllShortcutsChange?.let {
            SectionEditRow(
                id = "all_shortcuts",
                titleRes = Res.string.library_section_all_shortcuts,
                icon = Icons.Outlined.UnfoldMore,
                checked = showAllShortcuts,
                hintRes = Res.string.library_section_all_shortcuts_summary,
                onChange = it
            )
        },
        onPinnedChange?.let {
            SectionEditRow(
                id = "pinned",
                titleRes = Res.string.library_section_pinned,
                icon = Icons.Outlined.PushPin,
                checked = showPinned,
                hintRes = Res.string.library_section_pinned_summary,
                onChange = it
            )
        },
        onGroupAlbumsIntoSectionsChange?.let {
            SectionEditRow(
                id = "album_sections",
                titleRes = Res.string.library_section_album_sections,
                icon = Icons.Outlined.ViewAgenda,
                checked = groupAlbumsIntoSections,
                hintRes = Res.string.library_section_album_sections_summary,
                onChange = it
            )
        },
        onMediaTypesChange?.let {
            SectionEditRow(
                id = "media_types",
                titleRes = Res.string.library_section_media_types,
                icon = Icons.Outlined.PermMedia,
                checked = showMediaTypes,
                hintRes = Res.string.library_section_media_types_summary,
                onChange = it
            )
        },
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(Res.string.library_sections_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 12.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            rows.forEachIndexed { index, row ->
                val enabled = row.enabled
                val contentAlpha = if (enabled) 1f else 0.38f
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(connectedRowShape(index, rows.size))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = row.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                            .copy(alpha = contentAlpha),
                        modifier = Modifier.size(22.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(row.titleRes),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                                .copy(alpha = contentAlpha)
                        )
                        if (row.hintRes != null) {
                            Text(
                                text = stringResource(row.hintRes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                    .copy(alpha = contentAlpha)
                            )
                        }
                    }
                    Switch(
                        checked = row.checked,
                        onCheckedChange = row.onChange,
                        enabled = enabled
                    )
                }
            }
        }
    }
}
