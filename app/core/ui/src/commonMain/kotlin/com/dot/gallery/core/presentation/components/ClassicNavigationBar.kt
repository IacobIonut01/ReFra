/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemColors
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.presentation.util.NavigationItem
import com.dot.gallery.core.ui.generated.resources.Res
import com.dot.gallery.core.ui.generated.resources.search
import org.jetbrains.compose.resources.stringResource

const val ClassicNavigationSearchTag = "Classic.NavigationSearch"

/**
 * Position of the Search item among [itemCount] classic destinations: the middle, so two
 * destinations get it between them. Returns -1 when [showSearch] is false.
 */
fun classicSearchSlot(itemCount: Int, showSearch: Boolean): Int =
    if (showSearch) itemCount.coerceAtLeast(0) / 2 else -1

/**
 * The classic Material bottom bar. Pure rendering extracted unchanged from the
 * app's `ClassicNavBar` so docs previews render the same bar without a
 * NavController.
 *
 * A non-null [onSearchClick] adds a Search item in the middle of the destinations. It is an
 * action rather than a destination, so it is never shown as selected; the host passes null on
 * the search screen to hide it.
 */
@Composable
fun ClassicNavigationBarContent(
    selectedRoute: String?,
    navigationItems: List<NavigationItem>,
    onClick: (route: String) -> Unit,
    onSearchClick: (() -> Unit)? = null,
) {
    val colors = NavigationBarItemDefaults.colors(
        indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
        selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
        selectedTextColor = MaterialTheme.colorScheme.onSurface,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val searchSlot = classicSearchSlot(navigationItems.size, showSearch = onSearchClick != null)
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
    ) {
        navigationItems.forEachIndexed { index, item ->
            if (index == searchSlot && onSearchClick != null) {
                ClassicSearchBarItem(onSearchClick, colors)
            }
            val selected = item.route == selectedRoute
            NavigationBarItem(
                selected = selected,
                colors = colors,
                onClick = { onClick(item.route) },
                label = { NavigationItemLabel(item) },
                icon = { NavigationItemIcon(item) }
            )
        }
        if (searchSlot == navigationItems.size && onSearchClick != null) {
            ClassicSearchBarItem(onSearchClick, colors)
        }
    }
}

@Composable
private fun RowScope.ClassicSearchBarItem(
    onSearchClick: () -> Unit,
    colors: NavigationBarItemColors,
) {
    NavigationBarItem(
        selected = false,
        colors = colors,
        onClick = onSearchClick,
        modifier = Modifier.testTag(ClassicNavigationSearchTag),
        label = { ClassicSearchLabel() },
        icon = { ClassicSearchIcon() },
    )
}

@Composable
internal fun ClassicSearchLabel() = Text(
    text = stringResource(Res.string.search),
    fontWeight = FontWeight.Medium,
    style = MaterialTheme.typography.bodyMedium,
)

@Composable
internal fun ClassicSearchIcon() = Icon(
    imageVector = Icons.Outlined.Search,
    // The label already names the item, so the icon stays silent to avoid a double read.
    contentDescription = null,
)
