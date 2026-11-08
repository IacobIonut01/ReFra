/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.albums.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.dot.gallery.core.presentation.components.FilterKind
import com.dot.gallery.core.presentation.components.GalleryDetailAction
import com.dot.gallery.core.presentation.components.GalleryPageTopBarAction
import com.dot.gallery.domain.model.LastSort
import com.dot.gallery.domain.util.OrderType
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.feature.albums.generated.resources.Res
import com.dot.gallery.feature.albums.generated.resources.sort
import com.dot.gallery.feature.albums.generated.resources.sort_ascending
import com.dot.gallery.feature.albums.generated.resources.sort_by_date_modified
import com.dot.gallery.feature.albums.generated.resources.sort_by_date_taken
import com.dot.gallery.feature.albums.generated.resources.sort_by_file_name
import com.dot.gallery.feature.albums.generated.resources.sort_descending

/**
 * Sort menu for an album's media. [anchor] draws what opens it (the detail header's sort pill);
 * by default it is a sort icon button.
 */
@Composable
fun AlbumSortDropdown(
    modifier: Modifier = Modifier,
    currentSort: LastSort,
    onSortChange: (LastSort) -> Unit,
    anchor: (@Composable (open: () -> Unit) -> Unit)? = null,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Box(modifier = modifier) {
        if (anchor != null) {
            anchor { expanded = true }
        } else {
            IconButton(onClick = { expanded = true }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Sort,
                    contentDescription = stringResource(Res.string.sort)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            // Date taken option
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.sort_by_date_taken)) },
                onClick = {
                    onSortChange(currentSort.copy(kind = FilterKind.DATE))
                    expanded = false
                },
                trailingIcon = {
                    if (currentSort.kind == FilterKind.DATE) {
                        Icon(Icons.Outlined.Check, contentDescription = null)
                    }
                }
            )

            // Date modified option
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.sort_by_date_modified)) },
                onClick = {
                    onSortChange(currentSort.copy(kind = FilterKind.DATE_MODIFIED))
                    expanded = false
                },
                trailingIcon = {
                    if (currentSort.kind == FilterKind.DATE_MODIFIED) {
                        Icon(Icons.Outlined.Check, contentDescription = null)
                    }
                }
            )

            // File name option
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.sort_by_file_name)) },
                onClick = {
                    onSortChange(currentSort.copy(kind = FilterKind.NAME))
                    expanded = false
                },
                trailingIcon = {
                    if (currentSort.kind == FilterKind.NAME) {
                        Icon(Icons.Outlined.Check, contentDescription = null)
                    }
                }
            )

            HorizontalDivider()

            // Ascending/Descending toggle
            DropdownMenuItem(
                text = {
                    Text(
                        if (currentSort.orderType == OrderType.Descending)
                            stringResource(Res.string.sort_descending)
                        else
                            stringResource(Res.string.sort_ascending)
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = if (currentSort.orderType == OrderType.Descending)
                            Icons.Outlined.KeyboardArrowDown
                        else
                            Icons.Outlined.KeyboardArrowUp,
                        contentDescription = null
                    )
                },
                onClick = {
                    val newOrder = if (currentSort.orderType == OrderType.Ascending)
                        OrderType.Descending
                    else
                        OrderType.Ascending
                    onSortChange(currentSort.copy(orderType = newOrder))
                    expanded = false
                }
            )
        }
    }
}

/** Label of the sort pill: the date order when sorting by date, otherwise plain "Sort". */
@Composable
fun albumSortLabel(sort: LastSort): String = when {
    sort.kind != FilterKind.DATE && sort.kind != FilterKind.DATE_MODIFIED -> stringResource(Res.string.sort)
    sort.orderType == OrderType.Descending -> stringResource(Res.string.sort_descending)
    else -> stringResource(Res.string.sort_ascending)
}

/** The sort button in the detail top bar's action group, wired to [AlbumSortDropdown]. */
@Composable
fun AlbumSortTopBarAction(currentSort: LastSort, onSortChange: (LastSort) -> Unit) {
    AlbumSortDropdown(currentSort = currentSort, onSortChange = onSortChange) { open ->
        GalleryPageTopBarAction(
            icon = Icons.AutoMirrored.Outlined.Sort,
            contentDescription = albumSortLabel(currentSort),
            onClick = open,
        )
    }
}

/** The detail header's sort pill wired to [AlbumSortDropdown]. */
@Composable
fun AlbumSortPill(currentSort: LastSort, onSortChange: (LastSort) -> Unit) {
    AlbumSortDropdown(currentSort = currentSort, onSortChange = onSortChange) { open ->
        GalleryDetailAction(
            label = albumSortLabel(currentSort),
            icon = Icons.AutoMirrored.Outlined.Sort,
            onClick = open,
        )
    }
}
