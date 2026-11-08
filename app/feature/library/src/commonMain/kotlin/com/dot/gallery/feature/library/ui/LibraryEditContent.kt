/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.library.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.ui.settings.rememberPreference
import com.dot.gallery.domain.settings.SECTION_CATEGORIES
import com.dot.gallery.domain.settings.SECTION_PEOPLE
import com.dot.gallery.domain.settings.SettingsKeys
import com.dot.gallery.feature.library.generated.resources.Res
import com.dot.gallery.feature.library.generated.resources.library_customize_hint
import com.dot.gallery.feature.library.generated.resources.library_customize_title
import com.dot.gallery.feature.library.generated.resources.library_edit_done
import com.dot.gallery.feature.library.generated.resources.library_preview
import com.dot.gallery.feature.library.ui.components.LibrarySectionsEditCard
import org.jetbrains.compose.resources.stringResource

/**
 * Customizing the Library as its own focused screen: shortcuts, section switches, and a live
 * preview of the block those switches reflow. Changes apply immediately, so Done only leaves.
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
) {
    BackHandler(onBack = onDone)
    val snapshot = state.snapshot
    val sections = rememberLibrarySections(state)
    var showLocations by rememberPreference(SettingsKeys.showLocationCategories)
    var hiddenSections by rememberPreference(SettingsKeys.hiddenSections)
    val noClassification by rememberPreference(SettingsKeys.noClassification)
    var showPinned by rememberPreference(SettingsKeys.showPinnedAlbums)
    var showMediaTypes by rememberPreference(SettingsKeys.showMediaTypeAlbums)
    var groupAlbumsIntoSections by rememberPreference(SettingsKeys.albumSectionsEnabled)

    fun setSectionVisible(id: String, visible: Boolean) {
        hiddenSections = if (visible) hiddenSections - id else hiddenSections + id
    }

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
            onNavigate = {},
            onEnterEditMode = {},
        )
        LibrarySectionsEditCard(
            modifier = Modifier.padding(top = 20.dp),
            showLocations = showLocations,
            onLocationsChange = { showLocations = it },
            hasLocations = !snapshot.locations.isNullOrEmpty(),
            peopleAvailable = snapshot.cloud.hasPeople,
            showPeople = SECTION_PEOPLE !in hiddenSections,
            onPeopleChange = { setSectionVisible(SECTION_PEOPLE, it) },
            categoriesAvailable = state.aiAvailable && !noClassification,
            showCategories = SECTION_CATEGORIES !in hiddenSections,
            onCategoriesChange = { setSectionVisible(SECTION_CATEGORIES, it) },
            showPinned = showPinned,
            onPinnedChange = { showPinned = it },
            showMediaTypes = showMediaTypes,
            onMediaTypesChange = { showMediaTypes = it },
            groupAlbumsIntoSections = groupAlbumsIntoSections,
            onGroupAlbumsIntoSectionsChange = { groupAlbumsIntoSections = it },
        )
        if (sections.count > 0) {
            EditLabel(stringResource(Res.string.library_preview))
            LibraryExploreCards(
                state = state,
                routes = routes,
                sections = sections,
                layout = layout,
                mapContent = mapContent,
                onNavigate = {},
                onLongPress = {},
            )
        }
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
