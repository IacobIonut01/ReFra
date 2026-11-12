/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemColors
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.dot.gallery.core.presentation.util.NavigationItem

/**
 * The classic navigation rail used on wide windows. Pure rendering extracted
 * unchanged from the app's private `ClassicNavigationRail`.
 *
 * A non-null [onSearchClick] adds a never-selected Search item in the middle of the
 * destinations, matching [ClassicNavigationBarContent].
 */
@Composable
fun ClassicNavigationRailContent(
    selectedRoute: String?,
    navigationItems: List<NavigationItem>,
    onClick: (route: String) -> Unit,
    onSearchClick: (() -> Unit)? = null,
) {
    val colors = NavigationRailItemDefaults.colors(
        indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
        selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
    )
    val searchSlot = classicSearchSlot(navigationItems.size, showSearch = onSearchClick != null)
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Spacer(Modifier.weight(1f))
        navigationItems.forEachIndexed { index, item ->
            if (index == searchSlot && onSearchClick != null) {
                ClassicSearchRailItem(onSearchClick, colors)
            }
            val selected = item.route == selectedRoute
            NavigationRailItem(
                selected = selected,
                colors = colors,
                onClick = { onClick(item.route) },
                label = { NavigationItemLabel(item) },
                icon = { NavigationItemIcon(item) }
            )
        }
        if (searchSlot == navigationItems.size && onSearchClick != null) {
            ClassicSearchRailItem(onSearchClick, colors)
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun ClassicSearchRailItem(
    onSearchClick: () -> Unit,
    colors: NavigationRailItemColors,
) {
    NavigationRailItem(
        selected = false,
        colors = colors,
        onClick = onSearchClick,
        modifier = Modifier.testTag(ClassicNavigationSearchTag),
        label = { ClassicSearchLabel() },
        icon = { ClassicSearchIcon() },
    )
}
