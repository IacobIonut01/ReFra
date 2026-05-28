/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.help.data

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.ui.res.stringResource
import com.dot.gallery.R
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.ChecklistRtl
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Colorize
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material.icons.outlined.Swipe
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector

@Immutable
data class HelpTip(
    val id: String,
    @StringRes val title: Int,
    @StringRes val subtitle: Int,
    val icon: HelpIcon,
    val category: HelpCategory,
    val pages: List<TutorialPage>,
    val deepLink: String? = null,
    val sinceVersion: String? = null
)

@Immutable
data class TutorialPage(
    @StringRes val title: Int,
    @StringRes val description: Int,
    val steps: List<Int> = emptyList(),
    @StringRes val actionLabel: Int = 0,
    val actionRoute: String? = null,
    val previewType: PreviewType = PreviewType.NONE
)

@Immutable
data class HelpIcon(
    val vector: ImageVector? = null,
    @DrawableRes val drawableRes: Int? = null
) {
    companion object {
        fun ofVector(vector: ImageVector) = HelpIcon(vector = vector)
        fun ofDrawable(@DrawableRes resId: Int) = HelpIcon(drawableRes = resId)
    }
}

enum class PreviewType {
    AI_SEARCH,
    AI_CATEGORIES,
    TIMELINE_GRID,
    ALBUM_GRID,
    MEDIA_VIEWER,
    PHOTO_EDITOR_CROP,
    PHOTO_EDITOR_FILTERS,
    PHOTO_EDITOR_MARKUP,
    VAULT_LOCK,
    SEARCH_BAR,
    FAVORITES_GRID,
    TRASH_GRID,
    THEME_PICKER,
    COLOR_PALETTE,
    LOCATION_MAP,
    EXIF_VIEWER,
    PINCH_ZOOM_GRID,
    COLLECTION_VIEW,
    NAV_BAR_PREVIEW,
    SETTINGS_GENERAL,
    NONE
}

enum class HelpCategory {
    WHATS_NEW,
    GET_STARTED_BASICS,
    GET_STARTED_NAVIGATION,
    GET_STARTED_PERSONALIZATION,
    TIMELINE_ALBUMS,
    VIEWING,
    VIEWER_ACTIONS,
    VIEWER_SETTINGS,
    EDITING,
    SEARCH,
    AI_FEATURES,
    ALBUMS,
    VAULT,
    FAVORITES_TRASH,
    LOCATIONS,
    METADATA,
    SETTINGS_APPEARANCE,
    SETTINGS_GENERAL,
    SETTINGS_NAVIGATION,
    SETTINGS_SMART,
    SETTINGS_SECURITY,
    GESTURES,
    SELECTION_ACTIONS,
    ACCESSIBILITY
}

@Composable
fun HelpCategory.displayTitle(): String = when (this) {
    HelpCategory.WHATS_NEW -> stringResource(R.string.help_whats_new)
    HelpCategory.GET_STARTED_BASICS -> stringResource(R.string.help_cat_basics)
    HelpCategory.GET_STARTED_NAVIGATION -> stringResource(R.string.help_cat_navigation)
    HelpCategory.GET_STARTED_PERSONALIZATION -> stringResource(R.string.help_cat_personalization)
    HelpCategory.TIMELINE_ALBUMS -> stringResource(R.string.help_cat_timeline_albums)
    HelpCategory.VIEWING -> stringResource(R.string.help_cat_viewing)
    HelpCategory.VIEWER_ACTIONS -> stringResource(R.string.help_cat_viewer_actions)
    HelpCategory.VIEWER_SETTINGS -> stringResource(R.string.help_cat_viewer_settings)
    HelpCategory.EDITING -> stringResource(R.string.help_cat_editing)
    HelpCategory.SEARCH -> stringResource(R.string.help_cat_search)
    HelpCategory.AI_FEATURES -> stringResource(R.string.help_cat_ai)
    HelpCategory.ALBUMS -> stringResource(R.string.help_cat_albums)
    HelpCategory.VAULT -> stringResource(R.string.help_cat_vault)
    HelpCategory.FAVORITES_TRASH -> stringResource(R.string.help_cat_fav_trash)
    HelpCategory.LOCATIONS -> stringResource(R.string.help_cat_locations)
    HelpCategory.METADATA -> stringResource(R.string.help_cat_metadata)
    HelpCategory.SETTINGS_APPEARANCE -> stringResource(R.string.help_cat_settings_appearance)
    HelpCategory.SETTINGS_GENERAL -> stringResource(R.string.help_cat_settings_general)
    HelpCategory.SETTINGS_NAVIGATION -> stringResource(R.string.help_cat_settings_navigation)
    HelpCategory.SETTINGS_SMART -> stringResource(R.string.help_cat_settings_smart)
    HelpCategory.SETTINGS_SECURITY -> stringResource(R.string.help_cat_settings_security)
    HelpCategory.GESTURES -> stringResource(R.string.help_cat_gestures)
    HelpCategory.SELECTION_ACTIONS -> stringResource(R.string.help_cat_selection_actions)
    HelpCategory.ACCESSIBILITY -> stringResource(R.string.help_cat_accessibility)
}

fun HelpCategory.icon(): ImageVector = when (this) {
    HelpCategory.WHATS_NEW -> Icons.Outlined.NewReleases
    HelpCategory.GET_STARTED_BASICS -> Icons.AutoMirrored.Outlined.HelpOutline
    HelpCategory.GET_STARTED_NAVIGATION -> Icons.Outlined.Navigation
    HelpCategory.GET_STARTED_PERSONALIZATION -> Icons.Outlined.Palette
    HelpCategory.TIMELINE_ALBUMS -> Icons.Outlined.GridView
    HelpCategory.VIEWING -> Icons.Outlined.PlayCircleOutline
    HelpCategory.VIEWER_ACTIONS -> Icons.Outlined.SmartDisplay
    HelpCategory.VIEWER_SETTINGS -> Icons.Outlined.Tune
    HelpCategory.EDITING -> Icons.Outlined.Brush
    HelpCategory.SEARCH -> Icons.Outlined.Search
    HelpCategory.AI_FEATURES -> Icons.Outlined.AutoAwesome
    HelpCategory.ALBUMS -> Icons.Outlined.Collections
    HelpCategory.VAULT -> Icons.Outlined.Lock
    HelpCategory.FAVORITES_TRASH -> Icons.Outlined.FavoriteBorder
    HelpCategory.LOCATIONS -> Icons.Outlined.LocationOn
    HelpCategory.METADATA -> Icons.Outlined.Info
    HelpCategory.SETTINGS_APPEARANCE -> Icons.Outlined.Colorize
    HelpCategory.SETTINGS_GENERAL -> Icons.Outlined.Settings
    HelpCategory.SETTINGS_NAVIGATION -> Icons.Outlined.Explore
    HelpCategory.SETTINGS_SMART -> Icons.Outlined.AutoAwesome
    HelpCategory.SETTINGS_SECURITY -> Icons.Outlined.Shield
    HelpCategory.GESTURES -> Icons.Outlined.Swipe
    HelpCategory.SELECTION_ACTIONS -> Icons.Outlined.ChecklistRtl
    HelpCategory.ACCESSIBILITY -> Icons.Outlined.AccessibilityNew
}
