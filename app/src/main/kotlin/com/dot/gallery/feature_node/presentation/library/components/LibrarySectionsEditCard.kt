/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.library.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dot.gallery.R

/** One section toggle row inside [LibrarySectionsEditCard]. */
private data class SectionEditRow(
    val id: String,
    val titleRes: Int,
    val icon: ImageVector,
    val checked: Boolean,
    val enabled: Boolean = true,
    /** Explains the current state under the title — e.g. an empty or unavailable section. */
    val hintRes: Int? = null,
    val onChange: (Boolean) -> Unit
)

/**
 * "Sections" card shown under the shortcuts grid while edit mode is on (#1262):
 * manual visibility toggles for the Library's Locations / People / Categories
 * rows, replacing the old auto-hide-when-empty behavior. Toggles apply
 * immediately so the page live-previews the change.
 */
@Composable
fun LibrarySectionsEditCard(
    showLocations: Boolean,
    onLocationsChange: (Boolean) -> Unit,
    hasLocations: Boolean,
    peopleAvailable: Boolean,
    showPeople: Boolean,
    onPeopleChange: (Boolean) -> Unit,
    categoriesAvailable: Boolean,
    showCategories: Boolean,
    onCategoriesChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = listOf(
        SectionEditRow(
            id = "locations",
            titleRes = R.string.locations,
            icon = Icons.Outlined.LocationOn,
            checked = showLocations,
            // The switch stays enabled even when empty — it also gates location
            // collection, not only display.
            hintRes = if (!hasLocations) {
                R.string.library_section_locations_empty
            } else null,
            onChange = onLocationsChange
        ),
        SectionEditRow(
            id = "people",
            titleRes = R.string.cloud_people,
            icon = Icons.Outlined.People,
            checked = showPeople,
            enabled = peopleAvailable,
            hintRes = if (!peopleAvailable) {
                R.string.library_section_people_unavailable
            } else null,
            onChange = onPeopleChange
        ),
        SectionEditRow(
            id = "categories",
            titleRes = R.string.categories,
            icon = Icons.Outlined.AutoAwesome,
            checked = showCategories,
            enabled = categoriesAvailable,
            hintRes = if (!categoriesAvailable) {
                R.string.library_section_categories_unavailable
            } else null,
            onChange = onCategoriesChange
        )
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.library_sections_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    start = 20.dp, end = 20.dp, top = 8.dp, bottom = 4.dp
                )
            )
            rows.forEach { row ->
                val enabled = row.enabled
                val contentAlpha = if (enabled) 1f else 0.38f
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
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
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                                .copy(alpha = contentAlpha)
                        )
                        if (row.hintRes != null) {
                            Text(
                                text = stringResource(row.hintRes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                    .copy(alpha = 0.7f)
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
