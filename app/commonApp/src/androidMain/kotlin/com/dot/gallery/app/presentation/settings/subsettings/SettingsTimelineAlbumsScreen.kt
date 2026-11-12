/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.settings.subsettings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.rememberCoroutineScope
import com.dot.gallery.core.work.workScheduler
import kotlinx.coroutines.launch
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.LocalMediaDistributor
import com.dot.gallery.core.navigate
import com.dot.gallery.core.presentation.components.HomeShortcutSupport
import com.dot.gallery.core.util.SdkCompat
import com.dot.gallery.core.workers.enqueueCaptureTimeIndex
import com.dot.gallery.feature.settings.ui.SettingsTimelineAlbumsActions
import com.dot.gallery.feature.settings.ui.SettingsTimelineAlbumsContent
import com.dot.gallery.feature.settings.ui.SettingsTimelineAlbumsState
import com.dot.gallery.feature.albums.AlbumsViewModel
import com.dot.gallery.feature.help.domain.SettingsSearchRegistry
import com.dot.gallery.core.presentation.util.Screen
import com.dot.gallery.injection.injectViewModel

/**
 * App-side shell for the timeline & albums settings: owns the platform seams
 * (`AlbumsViewModel` section ops, `LocalMediaDistributor` album counts,
 * WorkManager capture-time reindex) and delegates the UI to
 * [SettingsTimelineAlbumsContent] in `:feature:settings`.
 */
@Composable
fun SettingsTimelineAlbumsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val eventHandler = LocalEventHandler.current

    val albumsViewModel = injectViewModel<AlbumsViewModel> {albumsViewModel}
    val sections by albumsViewModel.sectionsFlow.collectAsStateWithLifecycle()
    val mediaDistributor = LocalMediaDistributor.current
    val albumState by mediaDistributor.albumsFlow.collectAsStateWithLifecycle()
    val albumCountBySection = remember(albumState.albumSections) {
        albumState.albumSections.associate { it.section.id to it.albums.size }
    }

    SettingsTimelineAlbumsContent(
        state = SettingsTimelineAlbumsState(
            supportsFavorites = SdkCompat.supportsFavorites,
            homeShortcutSupport = HomeShortcutSupport(
                favorites = SdkCompat.supportsFavorites,
                trash = SdkCompat.supportsTrash,
            ),
            albumSections = sections,
            albumCountBySection = albumCountBySection,
        ),
        actions = SettingsTimelineAlbumsActions(
            onDateFormatClick = { eventHandler.navigate(Screen.DateFormatScreen()) },
            onStoryCardsClick = { eventHandler.navigate(Screen.StoryCardsSettingsScreen()) },
            onRecheckCaptureDates = {
                scope.launch { workScheduler().enqueueCaptureTimeIndex(force = true) }
            },
            onCreateSection = albumsViewModel::createSection,
            onRenameSection = albumsViewModel::renameSection,
            onDeleteSection = albumsViewModel::deleteSection,
            onReorderSections = albumsViewModel::reorderSections,
            onToggleSectionVisibility = albumsViewModel::toggleSectionVisibility,
            onEnsureDefaultSections = albumsViewModel::ensureDefaultSections,
            searchSink = SettingsSearchRegistry::register,
            searchRoute = Screen.SettingsTimelineAlbumsScreen(),
        ),
    )
}
