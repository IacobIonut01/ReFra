/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.Position
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.core.presentation.components.GalleryFilterChip
import com.dot.gallery.core.presentation.components.homeShortcutVisibility
import com.dot.gallery.core.ui.components.ChooserPreferenceDetailScreen
import com.dot.gallery.core.ui.components.PreferencePreviewStage
import com.dot.gallery.core.ui.components.PreviewHomeTopBar
import com.dot.gallery.core.ui.components.SettingsItem
import com.dot.gallery.core.ui.settings.rememberPreference
import com.dot.gallery.domain.settings.SettingsKeys
import com.dot.gallery.feature.settings.generated.resources.Res
import com.dot.gallery.feature.settings.generated.resources.search
import com.dot.gallery.feature.settings.generated.resources.top_bar_summary_none
import com.dot.gallery.feature.settings.generated.resources.top_bar_behavior_header
import com.dot.gallery.feature.settings.generated.resources.top_bar_buttons_header
import com.dot.gallery.feature.settings.generated.resources.top_bar_favorites
import com.dot.gallery.feature.settings.generated.resources.top_bar_favorites_summary
import com.dot.gallery.feature.settings.generated.resources.top_bar_filter
import com.dot.gallery.feature.settings.generated.resources.top_bar_filter_summary
import com.dot.gallery.feature.settings.generated.resources.top_bar_footnote
import com.dot.gallery.feature.settings.generated.resources.top_bar_footnote_no_favorites
import com.dot.gallery.feature.settings.generated.resources.top_bar_hide_on_scroll
import com.dot.gallery.feature.settings.generated.resources.top_bar_hide_on_scroll_summary
import com.dot.gallery.feature.settings.generated.resources.top_bar_preview_browsing
import com.dot.gallery.feature.settings.generated.resources.top_bar_preview_description
import com.dot.gallery.feature.settings.generated.resources.top_bar_preview_selecting
import com.dot.gallery.feature.settings.generated.resources.top_bar_select
import com.dot.gallery.feature.settings.generated.resources.top_bar_select_summary
import com.dot.gallery.feature.settings.generated.resources.top_bar_settings
import org.jetbrains.compose.resources.stringResource

const val TopBarPreviewTag = "Settings.TopBarPreview"

/**
 * Every setting of the home top bar on one page, under a live preview drawn by the real top
 * bar. The Browsing / Selecting chips switch only the preview, to show that Search replaces
 * Select and Favorites during a selection.
 */
@Composable
fun TopBarSettingsDetail(supportsFavorites: Boolean, initialPreviewSelecting: Boolean = false) {
    var showSelect by rememberPreference(SettingsKeys.showHomeSelectButton)
    var showFavorites by rememberPreference(SettingsKeys.showHomeFavoritesButton)
    var showFilter by rememberPreference(SettingsKeys.showFilterButton)
    var hideOnScroll by rememberPreference(SettingsKeys.autoHideSearchBar)
    var previewSelecting by rememberSaveable { mutableStateOf(initialPreviewSelecting) }

    ChooserPreferenceDetailScreen<Unit>(
        title = stringResource(Res.string.top_bar_settings),
        description = stringResource(
            if (supportsFavorites) Res.string.top_bar_footnote else Res.string.top_bar_footnote_no_favorites,
        ),
        customContent = {
            Column {
                PreferencePreviewStage(modifier = Modifier.padding(bottom = 16.dp)) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val shortcuts = homeShortcutVisibility(
                            timelineShortcuts = true,
                            selectionActive = previewSelecting,
                            supportsFavorites = supportsFavorites,
                            showFavorites = showFavorites,
                            showSelect = showSelect,
                        )
                        PreviewHomeTopBar(
                            shortcuts = shortcuts,
                            showFilter = showFilter,
                            description = stringResource(Res.string.top_bar_preview_description),
                            visibleButtons = topBarButtonsSummary(
                                listOfNotNull(
                                    stringResource(Res.string.top_bar_select).takeIf { shortcuts.select },
                                    stringResource(Res.string.top_bar_favorites).takeIf { shortcuts.favorites },
                                    stringResource(Res.string.search).takeIf { shortcuts.search },
                                    stringResource(Res.string.top_bar_filter).takeIf { showFilter },
                                ),
                                none = stringResource(Res.string.top_bar_summary_none),
                            ),
                            modifier = Modifier.testTag(TopBarPreviewTag),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GalleryFilterChip(
                                label = stringResource(Res.string.top_bar_preview_browsing),
                                selected = !previewSelecting,
                                onClick = { previewSelecting = false },
                            )
                            GalleryFilterChip(
                                label = stringResource(Res.string.top_bar_preview_selecting),
                                selected = previewSelecting,
                                onClick = { previewSelecting = true },
                            )
                        }
                    }
                }
                SettingsItem(item = SettingsEntity.Header(title = stringResource(Res.string.top_bar_buttons_header)))
                SettingsItem(
                    item = SettingsEntity.SwitchPreference(
                        title = stringResource(Res.string.top_bar_select),
                        summary = stringResource(Res.string.top_bar_select_summary),
                        icon = Icons.Outlined.CheckCircle,
                        isChecked = showSelect,
                        onCheck = { showSelect = it },
                        screenPosition = Position.Top,
                    ),
                )
                if (supportsFavorites) {
                    SettingsItem(
                        item = SettingsEntity.SwitchPreference(
                            title = stringResource(Res.string.top_bar_favorites),
                            summary = stringResource(Res.string.top_bar_favorites_summary),
                            icon = Icons.Outlined.FavoriteBorder,
                            isChecked = showFavorites,
                            onCheck = { showFavorites = it },
                            screenPosition = Position.Middle,
                        ),
                    )
                }
                SettingsItem(
                    item = SettingsEntity.SwitchPreference(
                        title = stringResource(Res.string.top_bar_filter),
                        summary = stringResource(Res.string.top_bar_filter_summary),
                        icon = Icons.Outlined.FilterList,
                        isChecked = showFilter,
                        onCheck = { showFilter = it },
                        screenPosition = Position.Bottom,
                    ),
                )
                SettingsItem(item = SettingsEntity.Header(title = stringResource(Res.string.top_bar_behavior_header)))
                SettingsItem(
                    item = SettingsEntity.SwitchPreference(
                        title = stringResource(Res.string.top_bar_hide_on_scroll),
                        summary = stringResource(Res.string.top_bar_hide_on_scroll_summary),
                        icon = Icons.Outlined.SearchOff,
                        isChecked = hideOnScroll,
                        onCheck = { hideOnScroll = it },
                        screenPosition = Position.Alone,
                    ),
                )
            }
        },
    )
}
