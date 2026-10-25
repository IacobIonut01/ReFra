/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.timeline.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Badge
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.dot.gallery.BuildConfig
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.navigate
import com.dot.gallery.core.util.SdkCompat
import com.dot.gallery.core.presentation.util.Screen
import com.dot.gallery.core.Settings.Misc.rememberShowSearchBarFavoriteButton
import com.dot.gallery.core.Settings.Misc.rememberLastSeenVersion
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.cloud_backup_dashboard_title
import com.dot.gallery.generated.resources.favorites
import com.dot.gallery.generated.resources.help_whats_new
import com.dot.gallery.generated.resources.more_options
import com.dot.gallery.generated.resources.search_media
import com.dot.gallery.generated.resources.settings_title
import com.dot.gallery.ui.theme.GalleryChrome
import org.jetbrains.compose.resources.stringResource

const val TimelineMenuTag = "Timeline.Menu"

/** Overflow menu of the main tabs. */
@Composable
fun TimelineNavActions() {
    val eventHandler = LocalEventHandler.current
    val showFavoriteButton by rememberShowSearchBarFavoriteButton()
    var lastSeenVersion by rememberLastSeenVersion()
    var expanded by rememberSaveable { mutableStateOf(false) }
    val navigate: (String) -> Unit = { route ->
        expanded = false
        eventHandler.navigate(route)
    }
    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(GalleryChrome.ToolbarButtonSize).testTag(TimelineMenuTag),
        ) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = stringResource(Res.string.more_options),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (lastSeenVersion != BuildConfig.VERSION_NAME) {
            Badge(
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                containerColor = MaterialTheme.colorScheme.primary,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.search_media)) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                onClick = { navigate(Screen.SearchScreen()) },
            )
            if (showFavoriteButton && SdkCompat.supportsFavorites) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.favorites)) },
                    leadingIcon = { Icon(Icons.Outlined.FavoriteBorder, contentDescription = null) },
                    onClick = { navigate(Screen.FavoriteScreen()) },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.cloud_backup_dashboard_title)) },
                leadingIcon = { Icon(Icons.Outlined.CloudUpload, contentDescription = null) },
                onClick = { navigate(Screen.CloudBackupDashboardScreen()) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.help_whats_new)) },
                leadingIcon = { Icon(Icons.Outlined.NewReleases, contentDescription = null) },
                onClick = {
                    lastSeenVersion = BuildConfig.VERSION_NAME
                    navigate(Screen.WhatsNewScreen())
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.settings_title)) },
                leadingIcon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                onClick = { navigate(Screen.SettingsScreen()) },
            )
        }
    }
}
