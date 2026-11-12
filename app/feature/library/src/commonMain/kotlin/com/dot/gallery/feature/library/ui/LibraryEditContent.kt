/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.library.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.ui.settings.rememberPreference
import com.dot.gallery.domain.settings.SettingsKeys
import com.dot.gallery.feature.library.generated.resources.Res
import com.dot.gallery.feature.library.generated.resources.library_albums_order
import com.dot.gallery.feature.library.generated.resources.library_albums_order_summary
import com.dot.gallery.feature.library.generated.resources.library_albums_order_unavailable
import com.dot.gallery.feature.library.generated.resources.library_customize_hint
import com.dot.gallery.feature.library.generated.resources.library_customize_title
import com.dot.gallery.feature.library.generated.resources.library_edit_done
import com.dot.gallery.feature.library.ui.components.LibrarySectionsEditCard
import org.jetbrains.compose.resources.stringResource

/**
 * Customizing the Library as its own focused screen: the shortcut tiles, Locations, People and
 * Categories among them, the section switches, and an entry to the albums' own arrange screen.
 * Changes apply immediately, so Done only leaves.
 *
 * [onArrangeAlbums] opens the album arrange screen; null hides the entry. [canArrangeAlbums] is
 * false while the albums cannot be arranged, so the entry says why instead of opening.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LibraryEditContent(
    state: LibraryState,
    routes: LibraryRoutes,
    layout: LibraryHeaderLayout,
    statusBarInsets: WindowInsets,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    mapContent: (@Composable BoxScope.(latitude: Double, longitude: Double) -> Unit)? = null,
    onArrangeAlbums: (() -> Unit)? = null,
    canArrangeAlbums: Boolean = true,
) {
    BackHandler(onBack = onDone)
    var showPinned by rememberPreference(SettingsKeys.showPinnedAlbums)
    var showAllShortcuts by rememberPreference(SettingsKeys.showAllLibraryShortcuts)
    var showMediaTypes by rememberPreference(SettingsKeys.showMediaTypeAlbums)
    var groupAlbumsIntoSections by rememberPreference(SettingsKeys.albumSectionsEnabled)

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("library-edit")
            .windowInsetsPadding(statusBarInsets)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(top = 12.dp, bottom = 24.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = stringResource(Res.string.library_customize_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Button(onClick = onDone) {
                Text(stringResource(Res.string.library_edit_done))
            }
        }
        EditLabel(stringResource(Res.string.library_customize_hint))
        LibraryShortcuts(
            state = state,
            routes = routes,
            editMode = true,
            layout = layout,
            mapContent = mapContent,
            onNavigate = {},
        )
        LibrarySectionsEditCard(
            modifier = Modifier.padding(top = 20.dp),
            showPinned = showPinned,
            onPinnedChange = { showPinned = it },
            showMediaTypes = showMediaTypes,
            onMediaTypesChange = { showMediaTypes = it },
            groupAlbumsIntoSections = groupAlbumsIntoSections,
            onGroupAlbumsIntoSectionsChange = { groupAlbumsIntoSections = it },
            showAllShortcuts = showAllShortcuts,
            onShowAllShortcutsChange = { showAllShortcuts = it },
        )
        if (onArrangeAlbums != null) {
            AlbumsOrderEntry(
                modifier = Modifier.padding(top = 12.dp),
                enabled = canArrangeAlbums,
                onClick = onArrangeAlbums,
            )
        }
    }
}

/** A row that leaves for the album arrange screen, kept apart so this page stays short. */
@Composable
private fun AlbumsOrderEntry(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("library-albums-order")
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .alpha(if (enabled) 1f else 0.38f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.DragIndicator,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.library_albums_order),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(
                    if (enabled) Res.string.library_albums_order_summary
                    else Res.string.library_albums_order_unavailable
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EditLabel(text: String) {
    Text(
        modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 20.dp, bottom = 12.dp),
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
