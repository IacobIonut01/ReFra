/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.albumtimeline.components

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
import androidx.compose.ui.res.stringResource
import com.dot.gallery.R
import com.dot.gallery.core.Settings.Album.LastSort
import com.dot.gallery.core.presentation.components.FilterKind
import com.dot.gallery.feature_node.domain.util.OrderType

@Composable
fun AlbumSortDropdown(
    modifier: Modifier = Modifier,
    currentSort: LastSort,
    onSortChange: (LastSort) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.Sort,
                contentDescription = stringResource(R.string.sort)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            // Date taken option
            DropdownMenuItem(
                text = { Text(stringResource(R.string.sort_by_date_taken)) },
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
                text = { Text(stringResource(R.string.sort_by_date_modified)) },
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
                text = { Text(stringResource(R.string.sort_by_file_name)) },
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
                            stringResource(R.string.sort_descending)
                        else
                            stringResource(R.string.sort_ascending)
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
