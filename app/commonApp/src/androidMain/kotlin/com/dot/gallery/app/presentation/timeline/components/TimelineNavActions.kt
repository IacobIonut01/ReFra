/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.timeline.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dot.gallery.BuildConfig
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.Settings.Misc.rememberLastSeenVersion
import com.dot.gallery.core.navigate
import com.dot.gallery.core.presentation.components.DragHandle
import com.dot.gallery.core.presentation.components.OptionItem
import com.dot.gallery.core.presentation.components.OptionLayout
import com.dot.gallery.core.presentation.util.Screen
import com.dot.gallery.core.ui.util.rememberAppBottomSheetState
import com.dot.gallery.core.util.SdkCompat
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.cloud_backup_dashboard_title
import com.dot.gallery.generated.resources.cloud_memories
import com.dot.gallery.generated.resources.favorites
import com.dot.gallery.generated.resources.help_whats_new
import com.dot.gallery.generated.resources.more_options
import com.dot.gallery.generated.resources.settings_title
import com.dot.gallery.ui.theme.GalleryChrome
import com.dot.gallery.ui.theme.Spacing
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

const val TimelineMenuTag = "Timeline.Menu"
const val TimelineMenuSheetTag = "Timeline.MenuSheet"

/**
 * Overflow menu of the main tabs, presented as a bottom sheet so its rows keep the same
 * reachable, labelled shape as the rest of the gallery's sheets. [extraItems] adds
 * screen-specific entries at the top of the sheet; every entry dismisses the sheet first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineNavActions(extraItems: List<OptionItem> = emptyList()) {
    val eventHandler = LocalEventHandler.current
    var lastSeenVersion by rememberLastSeenVersion()
    val sheetState = rememberAppBottomSheetState()
    val scope = rememberCoroutineScope()
    // The dot on the button and the "What's new" row describe the same unread release.
    val hasUnreadRelease = lastSeenVersion != BuildConfig.VERSION_NAME

    val menuTitle = stringResource(Res.string.more_options)
    val openRoute: (String) -> Unit = { route ->
        scope.launch {
            sheetState.hide()
            eventHandler.navigate(route)
        }
    }
    val options = buildList {
        extraItems.forEach { item ->
            add(
                item.copy(
                    onClick = { summary ->
                        scope.launch {
                            sheetState.hide()
                            item.onClick(summary)
                        }
                    },
                ),
            )
        }
        if (SdkCompat.supportsFavorites) {
            add(
                OptionItem(
                    icon = Icons.Outlined.FavoriteBorder,
                    text = stringResource(Res.string.favorites),
                    onClick = { openRoute(Screen.FavoriteScreen()) },
                ),
            )
        }
        add(
            OptionItem(
                icon = Icons.Outlined.AutoAwesome,
                text = stringResource(Res.string.cloud_memories),
                onClick = { openRoute(Screen.MemoriesScreen()) },
            ),
        )
        add(
            OptionItem(
                icon = Icons.Outlined.CloudUpload,
                text = stringResource(Res.string.cloud_backup_dashboard_title),
                onClick = { openRoute(Screen.CloudBackupDashboardScreen()) },
            ),
        )
        if (hasUnreadRelease) {
            add(
                OptionItem(
                    icon = Icons.Outlined.NewReleases,
                    text = stringResource(Res.string.help_whats_new),
                    attention = true,
                    onClick = {
                        scope.launch {
                            sheetState.hide()
                            lastSeenVersion = BuildConfig.VERSION_NAME
                            eventHandler.navigate(Screen.WhatsNewScreen())
                        }
                    },
                ),
            )
        }
        add(
            OptionItem(
                icon = Icons.Outlined.Settings,
                text = stringResource(Res.string.settings_title),
                onClick = { openRoute(Screen.SettingsScreen()) },
            ),
        )
    }

    Box {
        IconButton(
            onClick = { scope.launch { sheetState.show() } },
            modifier = Modifier.size(GalleryChrome.ToolbarButtonSize).testTag(TimelineMenuTag),
        ) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = menuTitle,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (hasUnreadRelease) {
            Badge(
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                containerColor = MaterialTheme.colorScheme.primary,
            )
        }
    }
    if (sheetState.isVisible) {
        ModalBottomSheet(
            sheetState = sheetState.sheetState,
            onDismissRequest = { scope.launch { sheetState.hide() } },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            tonalElevation = 0.dp,
            dragHandle = { DragHandle() },
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
            modifier = Modifier.semantics { paneTitle = menuTitle },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.ExtraLarge)
                    .padding(top = Spacing.Small, bottom = Spacing.Large)
                    .navigationBarsPadding()
                    .testTag(TimelineMenuSheetTag),
                verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = menuTitle,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = Spacing.Small)
                        .semantics { heading() },
                )
                OptionLayout(
                    modifier = Modifier.fillMaxWidth(),
                    optionList = options,
                )
            }
        }
    }
}
