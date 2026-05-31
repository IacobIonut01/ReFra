/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.settings.subsettings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Gif
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dot.gallery.R
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.LocalMediaDistributor
import com.dot.gallery.core.Position
import com.dot.gallery.core.Settings
import com.dot.gallery.core.Settings.Misc.rememberAllowGifAnimation
import com.dot.gallery.core.Settings.Misc.rememberCloudArchiveGroupByDate
import com.dot.gallery.core.Settings.Misc.rememberFavoriteIconPosition
import com.dot.gallery.core.Settings.Misc.rememberFavoritesGroupByDate
import com.dot.gallery.core.Settings.Misc.rememberLocationGroupByDate
import com.dot.gallery.core.Settings.Misc.rememberShowFilterButton
import com.dot.gallery.core.Settings.Misc.rememberShowSearchBarFavoriteButton
import com.dot.gallery.core.Settings.Misc.rememberAlbumsGroupMethod
import com.dot.gallery.core.Settings.Misc.rememberCloudArchiveGroupMethod
import com.dot.gallery.core.Settings.Misc.rememberFavoritesGroupMethod
import com.dot.gallery.core.Settings.Misc.rememberLocationGroupMethod
import com.dot.gallery.core.Settings.Misc.rememberTimelineGroupByDate
import com.dot.gallery.core.Settings.Misc.rememberTimelineGroupMethod
import com.dot.gallery.core.Settings.Misc.rememberVaultGroupByDate
import com.dot.gallery.core.Settings.Misc.rememberVaultGroupMethod
import com.dot.gallery.core.Settings.Misc.rememberGroupBurstSequences
import com.dot.gallery.core.Settings.Misc.rememberGroupCloudLocal
import com.dot.gallery.core.Settings.Misc.rememberGroupEditedCopies
import com.dot.gallery.core.Settings.Misc.rememberGroupRawJpg
import com.dot.gallery.core.Settings.Misc.rememberGroupSimilarMedia
import com.dot.gallery.core.Settings.Misc.rememberTimelineLayoutType
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.feature_node.presentation.settings.components.SettingsItem
import com.dot.gallery.core.navigate
import com.dot.gallery.core.util.SdkCompat
import com.dot.gallery.feature_node.presentation.settings.components.BaseSettingsScreen
import com.dot.gallery.feature_node.presentation.settings.components.ChooserPreferenceDetailScreen
import com.dot.gallery.feature_node.presentation.settings.components.PreferenceOption
import com.dot.gallery.feature_node.presentation.settings.components.SwitchPreferenceDetailScreen
import com.dot.gallery.feature_node.presentation.settings.components.rememberPreference
import com.dot.gallery.feature_node.presentation.settings.components.rememberSwitchPreference
import com.dot.gallery.feature_node.presentation.util.Screen
import com.dot.gallery.core.presentation.components.AlbumSectionNameSheet
import com.dot.gallery.core.presentation.components.DeleteSectionSheet
import com.dot.gallery.core.presentation.components.NavigationBackButton
import com.dot.gallery.core.presentation.components.SectionDialogMode
import com.dot.gallery.feature_node.domain.model.AlbumSection
import com.dot.gallery.feature_node.domain.model.AlbumSectionType
import com.dot.gallery.feature_node.presentation.albums.AlbumsViewModel
import com.dot.gallery.feature_node.presentation.common.components.OptionItem
import com.dot.gallery.feature_node.presentation.common.components.OptionSheet
import com.dot.gallery.feature_node.presentation.mediaview.rememberedDerivedState
import com.dot.gallery.feature_node.presentation.util.AppBottomSheetState
import com.dot.gallery.feature_node.presentation.util.rememberAppBottomSheetState
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

private const val DETAIL_TIMELINE_LAYOUT = "timeline_layout"
private const val DETAIL_GROUP_SIMILAR = "group_similar"
private const val DETAIL_GIF_ANIMATION = "gif_animation"
private const val DETAIL_FILTER_BUTTON = "filter_button"
private const val DETAIL_HIDE_TIMELINE = "hide_timeline"
private const val DETAIL_MERGE_ALBUMS = "merge_albums"
private const val DETAIL_ALBUM_SECTIONS = "album_sections"
private const val DETAIL_FAV_ICON = "fav_icon"
private const val DETAIL_SEARCHBAR_FAV_BUTTON = "searchbar_fav_button"
private const val DETAIL_DATE_HEADERS = "date_headers"
private const val DETAIL_GROUP_METHOD = "group_method"

@Composable
fun SettingsTimelineAlbumsScreen() {
    var detailKey by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val eventHandler = LocalEventHandler.current

    var groupByMonth by Settings.Misc.rememberTimelineGroupByMonth()
    var groupByYear by Settings.Misc.rememberTimelineGroupByYear()
    var timelineLayoutType by rememberTimelineLayoutType()
    var groupSimilarMedia by rememberGroupSimilarMedia()
    var groupRawJpg by rememberGroupRawJpg()
    var groupEditedCopies by rememberGroupEditedCopies()
    var groupBurstSequences by rememberGroupBurstSequences()
    var groupCloudLocal by rememberGroupCloudLocal()
    var allowGifAnimation by rememberAllowGifAnimation()
    var showFilterButton by rememberShowFilterButton()
    var showSearchBarFavButton by rememberShowSearchBarFavoriteButton()
    var hideTimelineOnAlbum by Settings.Album.rememberHideTimelineOnAlbum()
    var mergeAlbumsByName by Settings.Album.rememberMergeAlbumsByName()
    var albumSectionsEnabled by Settings.Album.rememberAlbumSectionsEnabled()
    var favIconPosition by rememberFavoriteIconPosition()
    var dateHeaderTimeline by rememberTimelineGroupByDate()
    var dateHeaderFavorites by rememberFavoritesGroupByDate()
    var dateHeaderVault by rememberVaultGroupByDate()
    var dateHeaderCloudArchive by rememberCloudArchiveGroupByDate()
    var dateHeaderLocation by rememberLocationGroupByDate()
    var groupMethodTimeline by rememberTimelineGroupMethod()
    var groupMethodAlbums by rememberAlbumsGroupMethod()
    var groupMethodFavorites by rememberFavoritesGroupMethod()
    var groupMethodVault by rememberVaultGroupMethod()
    var groupMethodCloudArchive by rememberCloudArchiveGroupMethod()
    var groupMethodLocation by rememberLocationGroupMethod()

    when (detailKey) {
        DETAIL_TIMELINE_LAYOUT -> {
            BackHandler { detailKey = null }
            ChooserPreferenceDetailScreen(
                title = stringResource(R.string.timeline_layout_type),
                description = stringResource(R.string.timeline_layout_description),
                preview = { TimelineLayoutPreview(timelineLayoutType) },
                options = listOf(
                    PreferenceOption(Settings.Misc.LAYOUT_GRID, stringResource(R.string.timeline_layout_grid), timelineLayoutType == Settings.Misc.LAYOUT_GRID),
                    PreferenceOption(Settings.Misc.LAYOUT_MOSAIC, stringResource(R.string.timeline_layout_mosaic), timelineLayoutType == Settings.Misc.LAYOUT_MOSAIC),
                ),
                onOptionSelected = { timelineLayoutType = it },
            )
        }
        DETAIL_GROUP_SIMILAR -> {
            BackHandler { detailKey = null }
            SwitchPreferenceDetailScreen(
                title = stringResource(R.string.group_similar_media_title),
                isChecked = groupSimilarMedia,
                onCheckedChange = { groupSimilarMedia = it },
                description = stringResource(R.string.group_similar_media_description),
                preview = { checked -> GroupSimilarPreview(checked) },
                customContent = {
                    Column(modifier = Modifier.padding(top = 16.dp)) {
                        SettingsItem(
                            item = SettingsEntity.SwitchPreference(
                                title = stringResource(R.string.group_raw_jpg_title),
                                summary = stringResource(R.string.group_raw_jpg_summary),
                                isChecked = groupRawJpg,
                                onCheck = { groupRawJpg = it },
                                screenPosition = Position.Top
                            )
                        )
                        SettingsItem(
                            item = SettingsEntity.SwitchPreference(
                                title = stringResource(R.string.group_edited_copies_title),
                                summary = stringResource(R.string.group_edited_copies_summary),
                                isChecked = groupEditedCopies,
                                onCheck = { groupEditedCopies = it },
                                screenPosition = Position.Middle
                            )
                        )
                        SettingsItem(
                            item = SettingsEntity.SwitchPreference(
                                title = stringResource(R.string.group_burst_sequences_title),
                                summary = stringResource(R.string.group_burst_sequences_summary),
                                isChecked = groupBurstSequences,
                                onCheck = { groupBurstSequences = it },
                                screenPosition = Position.Middle
                            )
                        )
                        SettingsItem(
                            item = SettingsEntity.SwitchPreference(
                                title = stringResource(R.string.group_cloud_local_title),
                                summary = stringResource(R.string.group_cloud_local_summary),
                                isChecked = groupCloudLocal,
                                onCheck = { groupCloudLocal = it },
                                screenPosition = Position.Bottom
                            )
                        )
                    }
                },
            )
        }
        DETAIL_GIF_ANIMATION -> {
            BackHandler { detailKey = null }
            SwitchPreferenceDetailScreen(
                title = stringResource(R.string.allow_gif_animation_title),
                isChecked = allowGifAnimation,
                onCheckedChange = { allowGifAnimation = it },
                description = stringResource(R.string.allow_gif_animation_description),
                preview = { checked -> AnimateGifsPreview(checked) },
            )
        }
        DETAIL_FILTER_BUTTON -> {
            BackHandler { detailKey = null }
            SwitchPreferenceDetailScreen(
                title = stringResource(R.string.show_filter_button),
                isChecked = showFilterButton,
                onCheckedChange = { showFilterButton = it },
                description = stringResource(R.string.show_filter_button_description),
                useColumnLayout = true,
                preview = { checked -> FilterButtonPreview(checked) },
            )
        }
        DETAIL_SEARCHBAR_FAV_BUTTON -> {
            BackHandler { detailKey = null }
            SwitchPreferenceDetailScreen(
                title = stringResource(R.string.show_searchbar_favorite_button),
                isChecked = showSearchBarFavButton,
                onCheckedChange = { showSearchBarFavButton = it },
                description = stringResource(R.string.show_searchbar_favorite_button_description),
                useColumnLayout = true,
                preview = { checked -> SearchBarFavoriteButtonPreview(checked) },
            )
        }
        DETAIL_HIDE_TIMELINE -> {
            BackHandler { detailKey = null }
            SwitchPreferenceDetailScreen(
                title = stringResource(R.string.hide_timeline_for_albums),
                isChecked = hideTimelineOnAlbum,
                onCheckedChange = { hideTimelineOnAlbum = it },
                description = stringResource(R.string.hide_timeline_for_albums_description),
                preview = { checked -> HideTimelinePreview(checked) },
            )
        }
        DETAIL_MERGE_ALBUMS -> {
            BackHandler { detailKey = null }
            SwitchPreferenceDetailScreen(
                title = stringResource(R.string.merge_albums_by_name),
                isChecked = mergeAlbumsByName,
                onCheckedChange = { mergeAlbumsByName = it },
                description = stringResource(R.string.merge_albums_by_name_description),
                preview = { checked -> MergeAlbumsPreview(checked) },
            )
        }
        DETAIL_ALBUM_SECTIONS -> {
            BackHandler { detailKey = null }
            AlbumSectionsDetailScreen(
                isEnabled = albumSectionsEnabled,
                onEnabledChange = { albumSectionsEnabled = it },
            )
        }
        DETAIL_FAV_ICON -> {
            BackHandler { detailKey = null }
            ChooserPreferenceDetailScreen(
                title = stringResource(R.string.favorite_icon_on_thumbnails),
                description = stringResource(R.string.favorite_icon_description),
                preview = { FavoriteIconPreview(favIconPosition) },
                options = listOf(
                    PreferenceOption(Settings.Misc.FAV_ICON_DISABLED, stringResource(R.string.fav_position_disabled), favIconPosition == Settings.Misc.FAV_ICON_DISABLED),
                    PreferenceOption(Settings.Misc.FAV_ICON_BOTTOM_END, stringResource(R.string.fav_position_bottom_end), favIconPosition == Settings.Misc.FAV_ICON_BOTTOM_END),
                    PreferenceOption(Settings.Misc.FAV_ICON_BOTTOM_START, stringResource(R.string.fav_position_bottom_start), favIconPosition == Settings.Misc.FAV_ICON_BOTTOM_START),
                    PreferenceOption(Settings.Misc.FAV_ICON_TOP_END, stringResource(R.string.fav_position_top_end), favIconPosition == Settings.Misc.FAV_ICON_TOP_END),
                    PreferenceOption(Settings.Misc.FAV_ICON_TOP_START, stringResource(R.string.fav_position_top_start), favIconPosition == Settings.Misc.FAV_ICON_TOP_START),
                ),
                onOptionSelected = { favIconPosition = it },
            )
        }
        DETAIL_DATE_HEADERS -> {
            BackHandler { detailKey = null }
            ChooserPreferenceDetailScreen<Unit>(
                title = stringResource(R.string.date_headers_title),
                description = stringResource(R.string.date_headers_description),
                customContent = {
                    Column {
                        val sections = buildList {
                            add(Triple(R.string.date_headers_timeline, dateHeaderTimeline) { v: Boolean -> dateHeaderTimeline = v })
                            add(Triple(R.string.date_headers_albums, !hideTimelineOnAlbum) { v: Boolean -> hideTimelineOnAlbum = !v })
                            if (SdkCompat.supportsFavorites) {
                                add(Triple(R.string.date_headers_favorites, dateHeaderFavorites) { v: Boolean -> dateHeaderFavorites = v })
                            }
                            add(Triple(R.string.date_headers_vault, dateHeaderVault) { v: Boolean -> dateHeaderVault = v })
                            add(Triple(R.string.date_headers_cloud_archive, dateHeaderCloudArchive) { v: Boolean -> dateHeaderCloudArchive = v })
                            add(Triple(R.string.date_headers_location, dateHeaderLocation) { v: Boolean -> dateHeaderLocation = v })
                        }
                        sections.forEachIndexed { index, (titleRes, isChecked, onCheck) ->
                            val position = when {
                                sections.size == 1 -> Position.Alone
                                index == 0 -> Position.Top
                                index == sections.lastIndex -> Position.Bottom
                                else -> Position.Middle
                            }
                            SettingsItem(
                                item = SettingsEntity.SwitchPreference(
                                    title = stringResource(titleRes),
                                    isChecked = isChecked,
                                    onCheck = onCheck,
                                    screenPosition = position
                                )
                            )
                        }

                        Spacer(Modifier.height(16.dp))

                        SettingsItem(
                            item = SettingsEntity.Preference(
                                title = stringResource(R.string.customize_date_formats),
                                summary = stringResource(R.string.date_header_summary),
                                onClick = { eventHandler.navigate(Screen.DateFormatScreen()) },
                                screenPosition = Position.Alone
                            )
                        )
                    }
                },
            )
        }
        DETAIL_GROUP_METHOD -> {
            BackHandler { detailKey = null }
            val groupMethodOptions = listOf(
                Settings.Misc.GROUP_NORMAL to stringResource(R.string.group_method_normal),
                Settings.Misc.GROUP_MONTHLY to stringResource(R.string.group_method_monthly),
                Settings.Misc.GROUP_YEARLY to stringResource(R.string.group_method_yearly),
            )
            ChooserPreferenceDetailScreen<Unit>(
                title = stringResource(R.string.group_method_title),
                description = stringResource(R.string.group_method_description),
                customContent = {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        val sections = buildList {
                            add(Triple(R.string.group_method_timeline, groupMethodTimeline) { v: String ->
                                if (v != groupMethodTimeline) {
                                    groupMethodTimeline = v
                                    groupByMonth = v == Settings.Misc.GROUP_MONTHLY
                                    groupByYear = v == Settings.Misc.GROUP_YEARLY
                                }
                            })
                            add(Triple(R.string.group_method_albums, groupMethodAlbums) { v: String -> groupMethodAlbums = v })
                            if (SdkCompat.supportsFavorites) {
                                add(Triple(R.string.group_method_favorites, groupMethodFavorites) { v: String -> groupMethodFavorites = v })
                            }
                            add(Triple(R.string.group_method_vault, groupMethodVault) { v: String -> groupMethodVault = v })
                            add(Triple(R.string.group_method_cloud_archive, groupMethodCloudArchive) { v: String -> groupMethodCloudArchive = v })
                            add(Triple(R.string.group_method_location, groupMethodLocation) { v: String -> groupMethodLocation = v })
                        }
                        sections.forEachIndexed { index, (titleRes, currentValue, onValueChange) ->
                            val position = when {
                                sections.size == 1 -> Position.Alone
                                index == 0 -> Position.Top
                                index == sections.lastIndex -> Position.Bottom
                                else -> Position.Middle
                            }
                            GroupMethodSectionRow(
                                title = stringResource(titleRes),
                                currentValue = currentValue,
                                options = groupMethodOptions,
                                onValueChange = onValueChange,
                                position = position,
                                showRestartNote = false,
                            )
                        }
                    }
                },
            )
        }
        else -> {
            TimelineAlbumsListScreen(
                timelineLayoutType = timelineLayoutType,
                groupSimilarMedia = groupSimilarMedia,
                onGroupSimilarChange = { groupSimilarMedia = it },
                allowGifAnimation = allowGifAnimation,
                onGifAnimationChange = { allowGifAnimation = it },
                showFilterButton = showFilterButton,
                onShowFilterButtonChange = { showFilterButton = it },
                showSearchBarFavButton = showSearchBarFavButton,
                onShowSearchBarFavButtonChange = { showSearchBarFavButton = it },
                hideTimelineOnAlbum = hideTimelineOnAlbum,
                onHideTimelineChange = { hideTimelineOnAlbum = it },
                mergeAlbumsByName = mergeAlbumsByName,
                onMergeAlbumsChange = { mergeAlbumsByName = it },
                albumSectionsEnabled = albumSectionsEnabled,
                onAlbumSectionsChange = { albumSectionsEnabled = it },
                favIconPosition = favIconPosition,
                onDetailClick = { detailKey = it },
                onDateFormatClick = { eventHandler.navigate(Screen.DateFormatScreen()) },
                onStoryCardsClick = { eventHandler.navigate(Screen.StoryCardsSettingsScreen()) },
            )
        }
    }
}

@Composable
private fun TimelineAlbumsListScreen(
    timelineLayoutType: String,
    groupSimilarMedia: Boolean,
    onGroupSimilarChange: (Boolean) -> Unit,
    allowGifAnimation: Boolean,
    onGifAnimationChange: (Boolean) -> Unit,
    showFilterButton: Boolean,
    onShowFilterButtonChange: (Boolean) -> Unit,
    showSearchBarFavButton: Boolean,
    onShowSearchBarFavButtonChange: (Boolean) -> Unit,
    hideTimelineOnAlbum: Boolean,
    onHideTimelineChange: (Boolean) -> Unit,
    mergeAlbumsByName: Boolean,
    onMergeAlbumsChange: (Boolean) -> Unit,
    albumSectionsEnabled: Boolean = false,
    onAlbumSectionsChange: (Boolean) -> Unit = {},
    favIconPosition: String,
    onDetailClick: (String) -> Unit,
    onDateFormatClick: () -> Unit,
    onStoryCardsClick: () -> Unit = {},
) {
    @Composable
    fun settings(): SnapshotStateList<SettingsEntity> {
        val context = LocalContext.current

        val timelineHeader = remember(context) {
            SettingsEntity.Header(title = context.getString(R.string.timeline))
        }

        val layoutLabel = remember(timelineLayoutType) {
            when (timelineLayoutType) {
                Settings.Misc.LAYOUT_MOSAIC -> context.getString(R.string.timeline_layout_mosaic)
                else -> context.getString(R.string.timeline_layout_grid)
            }
        }
        val timelineLayoutPref = rememberPreference(
            timelineLayoutType,
            title = stringResource(R.string.timeline_layout_type),
            summary = stringResource(R.string.timeline_layout_type_summary) + " ($layoutLabel)",
            onClick = { onDetailClick(DETAIL_TIMELINE_LAYOUT) },
            screenPosition = Position.Top
        )

        val groupSimilarMediaPref = rememberSwitchPreference(
            groupSimilarMedia,
            title = stringResource(R.string.group_similar_media_title),
            summary = stringResource(R.string.group_similar_media_summary),
            isChecked = groupSimilarMedia,
            onCheck = onGroupSimilarChange,
            onClick = { onDetailClick(DETAIL_GROUP_SIMILAR) },
            screenPosition = Position.Middle
        )

        val allowGifAnimationPref = rememberSwitchPreference(
            allowGifAnimation,
            title = stringResource(R.string.allow_gif_animation_title),
            summary = stringResource(R.string.allow_gif_animation_summary),
            isChecked = allowGifAnimation,
            onCheck = onGifAnimationChange,
            onClick = { onDetailClick(DETAIL_GIF_ANIMATION) },
            screenPosition = Position.Middle
        )

        val dateHeaderPref = rememberPreference(
            title = stringResource(R.string.date_header),
            summary = stringResource(R.string.date_header_summary),
            onClick = onDateFormatClick,
            screenPosition = Position.Middle
        )

        val showFilterButtonPref = rememberSwitchPreference(
            showFilterButton,
            title = stringResource(R.string.show_filter_button),
            summary = stringResource(R.string.show_filter_button_summary),
            isChecked = showFilterButton,
            onCheck = onShowFilterButtonChange,
            onClick = { onDetailClick(DETAIL_FILTER_BUTTON) },
            screenPosition = Position.Middle
        )

        val showSearchBarFavButtonPref = rememberSwitchPreference(
            showSearchBarFavButton,
            title = stringResource(R.string.show_searchbar_favorite_button),
            summary = stringResource(R.string.show_searchbar_favorite_button_summary),
            isChecked = showSearchBarFavButton,
            onCheck = onShowSearchBarFavButtonChange,
            onClick = { onDetailClick(DETAIL_SEARCHBAR_FAV_BUTTON) },
            screenPosition = Position.Middle
        )

        val storyCardsPref = rememberPreference(
            title = stringResource(R.string.story_cards_settings_title),
            summary = stringResource(R.string.story_cards_settings_summary),
            onClick = onStoryCardsClick,
            screenPosition = Position.Bottom
        )

        val albumsHeader = remember(context) {
            SettingsEntity.Header(title = context.getString(R.string.albums))
        }

        val hideTimelineOnAlbumPref = rememberSwitchPreference(
            hideTimelineOnAlbum,
            title = stringResource(R.string.hide_timeline_for_albums),
            summary = stringResource(R.string.hide_timeline_for_album_summary),
            isChecked = hideTimelineOnAlbum,
            onCheck = onHideTimelineChange,
            onClick = { onDetailClick(DETAIL_HIDE_TIMELINE) },
            screenPosition = Position.Top
        )

        val mergeAlbumsByNamePref = rememberSwitchPreference(
            mergeAlbumsByName,
            title = stringResource(R.string.merge_albums_by_name),
            summary = stringResource(R.string.merge_albums_by_name_summary),
            isChecked = mergeAlbumsByName,
            onCheck = onMergeAlbumsChange,
            onClick = { onDetailClick(DETAIL_MERGE_ALBUMS) },
            screenPosition = Position.Middle
        )

        val albumSectionsPref = rememberSwitchPreference(
            albumSectionsEnabled,
            title = stringResource(R.string.album_sections_title),
            summary = stringResource(R.string.album_sections_summary),
            isChecked = albumSectionsEnabled,
            onCheck = onAlbumSectionsChange,
            onClick = { onDetailClick(DETAIL_ALBUM_SECTIONS) },
            screenPosition = Position.Bottom
        )

        val favIconPositionLabel = remember(favIconPosition) {
            when (favIconPosition) {
                Settings.Misc.FAV_ICON_DISABLED -> context.getString(R.string.fav_position_disabled)
                Settings.Misc.FAV_ICON_BOTTOM_START -> context.getString(R.string.fav_position_bottom_start)
                Settings.Misc.FAV_ICON_TOP_END -> context.getString(R.string.fav_position_top_end)
                Settings.Misc.FAV_ICON_TOP_START -> context.getString(R.string.fav_position_top_start)
                else -> context.getString(R.string.fav_position_bottom_end)
            }
        }
        val hasFavorites = SdkCompat.supportsFavorites
        val favIconPositionPref = rememberPreference(
            favIconPosition,
            title = stringResource(R.string.favorite_icon_on_thumbnails),
            summary = favIconPositionLabel,
            onClick = { onDetailClick(DETAIL_FAV_ICON) },
            screenPosition = Position.Bottom
        )

        val displayHeader = remember(context) {
            SettingsEntity.Header(title = context.getString(R.string.settings_display_header))
        }

        val dateHeadersPref = rememberPreference(
            title = stringResource(R.string.date_headers_title),
            summary = stringResource(R.string.date_headers_summary),
            onClick = { onDetailClick(DETAIL_DATE_HEADERS) },
            screenPosition = Position.Top
        )

        val groupMethodPref = rememberPreference(
            title = stringResource(R.string.group_method_title),
            summary = stringResource(R.string.group_method_summary),
            onClick = { onDetailClick(DETAIL_GROUP_METHOD) },
            screenPosition = if (hasFavorites) Position.Middle else Position.Bottom
        )

        return remember(
            timelineLayoutPref, groupSimilarMediaPref,
            allowGifAnimationPref, dateHeaderPref, showFilterButtonPref,
            showSearchBarFavButtonPref, storyCardsPref,
            hideTimelineOnAlbumPref, mergeAlbumsByNamePref, albumSectionsPref, favIconPositionPref,
            dateHeadersPref, groupMethodPref
        ) {
            mutableStateListOf<SettingsEntity>().apply {
                add(timelineHeader)
                add(timelineLayoutPref)
                add(groupSimilarMediaPref)
                add(allowGifAnimationPref)
                add(dateHeaderPref)
                add(showFilterButtonPref)
                if (SdkCompat.supportsFavorites) {
                    add(showSearchBarFavButtonPref)
                }
                add(storyCardsPref)

                add(albumsHeader)
                add(hideTimelineOnAlbumPref)
                add(mergeAlbumsByNamePref)
                add(albumSectionsPref)

                add(displayHeader)
                add(dateHeadersPref)
                add(groupMethodPref)
                if (SdkCompat.supportsFavorites) {
                    add(favIconPositionPref)
                }
            }
        }
    }

    BaseSettingsScreen(
        title = stringResource(R.string.settings_timeline_albums),
        settingsList = settings(),
    )
}

// ===== Preview Composables for Detail Screens =====

@Composable
private fun GroupByMonthPreview(isChecked: Boolean) {
    val headerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    val cellColor = MaterialTheme.colorScheme.surfaceContainerHighest
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        if (isChecked) {
            Box(Modifier.size(72.dp, 8.dp).clip(RoundedCornerShape(4.dp)).background(headerColor))
            Spacer(Modifier.height(2.dp))
            repeat(3) {
                Row(Modifier.fillMaxWidth().height(48.dp), Arrangement.spacedBy(3.dp)) {
                    repeat(4) {
                        Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(cellColor))
                    }
                }
            }
        } else {
            repeat(2) { group ->
                Box(Modifier.size(56.dp, 8.dp).clip(RoundedCornerShape(4.dp)).background(headerColor))
                Spacer(Modifier.height(2.dp))
                Row(Modifier.fillMaxWidth().height(48.dp), Arrangement.spacedBy(3.dp)) {
                    repeat(4) {
                        Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(cellColor))
                    }
                }
                if (group == 0) Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun HideTimelinePreview(isChecked: Boolean) {
    val headerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    val cellColor = MaterialTheme.colorScheme.surfaceContainerHighest
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        repeat(3) { rowIndex ->
            if (!isChecked && rowIndex > 0 && rowIndex % 2 == 0) {
                Spacer(Modifier.height(4.dp))
                Box(Modifier.size(48.dp, 7.dp).clip(RoundedCornerShape(3.dp)).background(headerColor))
                Spacer(Modifier.height(2.dp))
            }
            Row(Modifier.fillMaxWidth().height(48.dp), Arrangement.spacedBy(3.dp)) {
                repeat(4) {
                    Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(cellColor))
                }
            }
        }
    }
}

@Composable
private fun TimelineLayoutPreview(currentLayout: String) {
    val cellColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val bigCellColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        listOf(Settings.Misc.LAYOUT_GRID, Settings.Misc.LAYOUT_MOSAIC).forEach { layoutType ->
            val selected = currentLayout == layoutType
            val label = if (layoutType == Settings.Misc.LAYOUT_MOSAIC) stringResource(R.string.timeline_layout_mosaic)
            else stringResource(R.string.timeline_layout_grid)
            val borderColor = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant
            val containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else Color.Transparent

            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { }
                    .border(width = 2.dp, color = borderColor, shape = RoundedCornerShape(16.dp))
                    .background(containerColor)
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(4.dp)
                ) {
                    if (layoutType == Settings.Misc.LAYOUT_GRID) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            repeat(4) {
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    repeat(4) {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(1f)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(cellColor)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(
                                modifier = Modifier.weight(2f),
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Box(
                                    modifier = Modifier.weight(2f).fillMaxSize()
                                        .clip(RoundedCornerShape(2.dp)).background(bigCellColor)
                                )
                                Column(
                                    modifier = Modifier.weight(2f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    repeat(2) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            repeat(2) {
                                                Box(
                                                    modifier = Modifier.weight(1f).fillMaxSize()
                                                        .clip(RoundedCornerShape(2.dp)).background(cellColor)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                repeat(4) {
                                    Box(
                                        modifier = Modifier.weight(1f).fillMaxSize()
                                            .clip(RoundedCornerShape(2.dp)).background(cellColor)
                                    )
                                }
                            }
                        }
                    }
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun GroupSimilarPreview(isChecked: Boolean) {
    val cellColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val groupColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(2.dp)) {
            if (isChecked) {
                // Grouped: 4 items, first has stack indicator
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(4.dp))
                        .background(groupColor)
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(3.dp)
                            .size(12.dp, 8.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
                repeat(3) {
                    Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(4.dp)).background(cellColor))
                }
            } else {
                repeat(4) {
                    Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(4.dp)).background(
                        if (it < 2) groupColor.copy(alpha = 0.25f) else cellColor
                    ))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(2.dp)) {
            repeat(4) {
                Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(4.dp)).background(cellColor))
            }
        }
    }
}

@Composable
private fun AnimateGifsPreview(isChecked: Boolean) {
    val cellColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val gifColor = MaterialTheme.colorScheme.tertiaryContainer
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(2.dp)) {
            repeat(4) { index ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (index == 1) gifColor else cellColor)
                ) {
                    if (index == 1) {
                        Icon(
                            imageVector = Icons.Outlined.Gif,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.align(Alignment.Center).size(20.dp)
                        )
                        if (isChecked) {
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 3.dp),
                                horizontalArrangement = Arrangement.spacedBy(1.dp)
                            ) {
                                repeat(3) {
                                    Box(
                                        modifier = Modifier
                                            .size(width = 4.dp, height = (3 + it * 2).dp)
                                            .clip(RoundedCornerShape(1.dp))
                                            .background(MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.6f))
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(2.dp)) {
            repeat(4) {
                Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(4.dp)).background(cellColor))
            }
        }
    }
}

@Composable
private fun MergeAlbumsPreview(isChecked: Boolean) {
    val cellColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val folderColor = MaterialTheme.colorScheme.primaryContainer
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (isChecked) {
            // Merged: single album folder with link icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(folderColor),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                    Icon(
                        imageVector = Icons.Outlined.Link,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Text(
                text = "Camera",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        } else {
            // Separate: two album folders
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                listOf("Camera", "Camera").forEachIndexed { index, name ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(cellColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteIconPreview(currentPosition: String) {
    val isHidden = currentPosition == Settings.Misc.FAV_ICON_DISABLED
    val heartAlpha by animateFloatAsState(
        targetValue = if (isHidden) 0f else 1f,
        label = "heartAlpha"
    )
    val favAlignment = when (currentPosition) {
        Settings.Misc.FAV_ICON_BOTTOM_START -> Alignment.BottomStart
        Settings.Misc.FAV_ICON_TOP_END -> Alignment.TopEnd
        Settings.Misc.FAV_ICON_TOP_START -> Alignment.TopStart
        else -> Alignment.BottomEnd
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)
    ) {
        repeat(3) { index ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                if (index == 1) {
                    Icon(
                        modifier = Modifier
                            .align(favAlignment)
                            .padding(6.dp)
                            .size(14.dp),
                        imageVector = Icons.Filled.Favorite,
                        tint = Color.Red.copy(alpha = heartAlpha),
                        contentDescription = null
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterButtonPreview(isChecked: Boolean) {
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .clip(RoundedCornerShape(100))
            .background(surfaceColor)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = null,
            tint = onSurfaceColor,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = stringResource(R.string.search),
            style = MaterialTheme.typography.bodyLarge,
            color = onSurfaceColor,
            modifier = Modifier.weight(1f)
        )
        if (isChecked) {
            Icon(
                imageVector = Icons.Outlined.FilterList,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun SearchBarFavoriteButtonPreview(isChecked: Boolean) {
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    val favColor = MaterialTheme.colorScheme.primaryFixed
    val onFavColor = MaterialTheme.colorScheme.onPrimaryFixed
    val settingsColor = MaterialTheme.colorScheme.tertiaryFixed
    val onSettingsColor = MaterialTheme.colorScheme.onTertiaryFixed
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(100))
                .background(surfaceColor)
                .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                tint = onSurfaceColor,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = stringResource(R.string.search),
                style = MaterialTheme.typography.bodyLarge,
                color = onSurfaceColor,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Outlined.FilterList,
                contentDescription = null,
                tint = onSurfaceColor,
                modifier = Modifier.size(22.dp)
            )
        }
        if (isChecked) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(favColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Favorite,
                    contentDescription = null,
                    tint = onFavColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(settingsColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = null,
                tint = onSettingsColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlbumSectionsDetailScreen(
    isEnabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    val albumsViewModel = hiltViewModel<AlbumsViewModel>()
    val sections by albumsViewModel.sectionsFlow.collectAsStateWithLifecycle()
    val mediaDistributor = LocalMediaDistributor.current
    val albumState by mediaDistributor.albumsFlow.collectAsStateWithLifecycle()
    val albumCountBySection = remember(albumState.albumSections) {
        albumState.albumSections.associate { it.section.id to it.albums.size }
    }
    val scope = rememberCoroutineScope()

    val sectionNameSheetState = rememberAppBottomSheetState()
    val deleteSectionSheetState = rememberAppBottomSheetState()
    val sectionActionsSheetState = rememberAppBottomSheetState()
    var sectionDialogMode by remember { mutableStateOf(SectionDialogMode.Create) }
    var sectionDialogInitialName by remember { mutableStateOf("") }
    var pendingSection by remember { mutableStateOf<AlbumSection?>(null) }

    // Drag-to-reorder state
    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val itemHeightPx = remember(density) { with(density) { 72.dp.toPx() } }

    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    AlbumSectionNameSheet(
        sheetState = sectionNameSheetState,
        mode = sectionDialogMode,
        initialName = sectionDialogInitialName,
        onCreateSection = { name ->
            albumsViewModel.createSection(name)
        },
        onRenameSection = { newName ->
            pendingSection?.let { albumsViewModel.renameSection(it.id, newName) }
            pendingSection = null
        }
    )

    DeleteSectionSheet(
        sheetState = deleteSectionSheetState,
        onConfirmDelete = {
            pendingSection?.let { albumsViewModel.deleteSection(it.id) }
            pendingSection = null
        }
    )

    SectionActionsSheet(
        sheetState = sectionActionsSheetState,
        sectionName = pendingSection?.label ?: "",
        onRename = {
            pendingSection?.let { section ->
                sectionDialogMode = SectionDialogMode.Rename
                sectionDialogInitialName = section.label
                scope.launch { sectionNameSheetState.show() }
            }
        },
        onDelete = {
            scope.launch { deleteSectionSheetState.show() }
        }
    )

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.album_sections_title)) },
                navigationIcon = { NavigationBackButton() },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = draggingIndex == -1,
            contentPadding = PaddingValues(
                start = padding.calculateStartPadding(LocalLayoutDirection.current),
                end = padding.calculateEndPadding(LocalLayoutDirection.current),
                top = 16.dp + padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 16.dp
            ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Master toggle
            item(key = "master_toggle") {
                SettingsItem(
                    item = SettingsEntity.SwitchPreference(
                        title = stringResource(R.string.album_sections_title),
                        summary = stringResource(R.string.album_sections_summary),
                        isChecked = isEnabled,
                        onCheck = {
                            onEnabledChange(it)
                            if (it) albumsViewModel.ensureDefaultSections()
                        },
                        screenPosition = Position.Alone
                    ),
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                )
            }

            // Description
            item(key = "description") {
                Text(
                    text = stringResource(R.string.album_sections_detail_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 16.dp)
                )
            }

            // Section management header
            if (isEnabled) {
                item(key = "sections_header") {
                    Text(
                        text = stringResource(R.string.album_sections_manage_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .widthIn(max = 600.dp)
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 8.dp)
                    )
                }

                // Section items with drag-to-reorder
                itemsIndexed(
                    items = sections,
                    key = { _, section -> "section_${section.id}" }
                ) { index, section ->
                    val isCustom = section.sectionType == AlbumSectionType.CUSTOM
                    val position = sectionItemPosition(index, sections.size)
                    val isDragged = draggingIndex == index

                    AlbumSectionListItem(
                        section = section,
                        albumCount = albumCountBySection[section.id] ?: 0,
                        isGlobalEnabled = isEnabled,
                        position = position,
                        isDragging = isDragged,
                        dragOffset = if (isDragged) dragOffsetY else 0f,
                        onDragStart = {
                            draggingIndex = index
                            dragOffsetY = 0f
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDrag = { delta ->
                            dragOffsetY += delta
                            val swapThreshold = itemHeightPx * 0.5f
                            if (dragOffsetY > swapThreshold && draggingIndex < sections.lastIndex) {
                                val reordered = sections.map { it.id }.toMutableList()
                                val item = reordered.removeAt(draggingIndex)
                                reordered.add(draggingIndex + 1, item)
                                albumsViewModel.reorderSections(reordered)
                                draggingIndex++
                                dragOffsetY -= itemHeightPx
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            } else if (dragOffsetY < -swapThreshold && draggingIndex > 0) {
                                val reordered = sections.map { it.id }.toMutableList()
                                val item = reordered.removeAt(draggingIndex)
                                reordered.add(draggingIndex - 1, item)
                                albumsViewModel.reorderSections(reordered)
                                draggingIndex--
                                dragOffsetY += itemHeightPx
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        },
                        onDragEnd = {
                            draggingIndex = -1
                            dragOffsetY = 0f
                        },
                        onToggleVisibility = { visible ->
                            albumsViewModel.toggleSectionVisibility(section.id, visible)
                        },
                        onClick = if (isCustom) {
                            {
                                pendingSection = section
                                scope.launch { sectionActionsSheetState.show() }
                            }
                        } else null,
                        modifier = Modifier
                            .widthIn(max = 600.dp)
                            .fillMaxWidth()
                    )
                }

                // Create section button
                item(key = "create_section") {
                    OutlinedButton(
                        onClick = {
                            sectionDialogMode = SectionDialogMode.Create
                            sectionDialogInitialName = ""
                            scope.launch { sectionNameSheetState.show() }
                        },
                        modifier = Modifier
                            .widthIn(max = 600.dp)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(top = 16.dp),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.create_section))
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumSectionListItem(
    section: AlbumSection,
    albumCount: Int = 0,
    isGlobalEnabled: Boolean,
    position: Position,
    isDragging: Boolean = false,
    dragOffset: Float = 0f,
    onDragStart: () -> Unit = {},
    onDrag: (Float) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onToggleVisibility: (Boolean) -> Unit,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = MaterialTheme.colorScheme.surfaceContainer
    val isCustom = section.sectionType == AlbumSectionType.CUSTOM
    val isVisible = section.isVisible

    val fullCornerRadius by animateDpAsState(
        targetValue = 24.dp,
        label = "fullCornerRadius"
    )
    val normalCornerRadius by animateDpAsState(
        targetValue = 8.dp,
        label = "normalCornerRadius"
    )

    val shape by rememberedDerivedState(position, fullCornerRadius, normalCornerRadius) {
        when (position) {
            Position.Alone -> RoundedCornerShape(fullCornerRadius)
            Position.Top -> RoundedCornerShape(
                topStart = fullCornerRadius, topEnd = fullCornerRadius,
                bottomStart = normalCornerRadius, bottomEnd = normalCornerRadius
            )
            Position.Middle -> RoundedCornerShape(normalCornerRadius)
            Position.Bottom -> RoundedCornerShape(
                topStart = normalCornerRadius, topEnd = normalCornerRadius,
                bottomStart = fullCornerRadius, bottomEnd = fullCornerRadius
            )
        }
    }

    val paddingModifier = when (position) {
        Position.Alone -> Modifier.padding(bottom = 16.dp)
        Position.Bottom -> Modifier.padding(top = 1.dp, bottom = 16.dp)
        Position.Middle -> Modifier.padding(vertical = 1.dp)
        Position.Top -> Modifier.padding(bottom = 1.dp)
    }

    val elevation by animateFloatAsState(
        targetValue = if (isDragging) 8f else 0f,
        label = "dragElevation"
    )

    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)

    val sectionTypeLabel = when (section.sectionType) {
        AlbumSectionType.COMMON -> stringResource(R.string.section_type_common)
        AlbumSectionType.APPS -> stringResource(R.string.section_type_apps)
        AlbumSectionType.UNCATEGORIZED -> stringResource(R.string.section_type_uncategorized)
        AlbumSectionType.CUSTOM -> stringResource(R.string.section_type_custom)
    }

    Box(
        modifier = Modifier
            .then(paddingModifier)
            .graphicsLayer {
                translationY = dragOffset
                shadowElevation = elevation
                scaleX = if (isDragging) 1.02f else 1f
                scaleY = if (isDragging) 1.02f else 1f
            }
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { currentOnDragStart() },
                    onDrag = { change, offset ->
                        change.consume()
                        currentOnDrag(offset.y)
                    },
                    onDragEnd = { currentOnDragEnd() },
                    onDragCancel = { currentOnDragEnd() }
                )
            }
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = modifier
                    .padding(horizontal = 16.dp)
                    .clip(shape)
                    .background(color = backgroundColor)
                    .then(
                        if (onClick != null) Modifier.clickable(onClick = onClick)
                        else Modifier
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .padding(8.dp)
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Drag handle
                    Icon(
                        Icons.Outlined.DragHandle, null,
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(22.dp),
                        tint = if (isDragging) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )

                    // Label + type
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = section.label,
                            style = MaterialTheme.typography.titleMedium,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isVisible) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                        Text(
                            text = if (albumCount > 0) {
                                "$sectionTypeLabel · ${pluralStringResource(R.plurals.album_count, albumCount, albumCount)}"
                            } else sectionTypeLabel,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                alpha = if (isVisible) 1f else 0.38f
                            ),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    // Visibility switch
                    Switch(
                        checked = isVisible,
                        onCheckedChange = onToggleVisibility,
                        enabled = isGlobalEnabled
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionActionsSheet(
    sheetState: AppBottomSheetState,
    sectionName: String,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val renameLabel = stringResource(R.string.rename_section)
    val deleteLabel = stringResource(R.string.delete_section)
    val errorContainerColor = MaterialTheme.colorScheme.errorContainer
    val onErrorContainerColor = MaterialTheme.colorScheme.onErrorContainer

    val options = remember(renameLabel, deleteLabel, errorContainerColor, onErrorContainerColor) {
        listOf(
            OptionItem(
                icon = Icons.Outlined.Edit,
                text = renameLabel,
                onClick = { _ ->
                    scope.launch { sheetState.hide() }
                    onRename()
                }
            ),
            OptionItem(
                icon = Icons.Outlined.Delete,
                text = deleteLabel,
                containerColor = errorContainerColor,
                contentColor = onErrorContainerColor,
                onClick = { _ ->
                    scope.launch { sheetState.hide() }
                    onDelete()
                }
            ),
        ).toMutableStateList()
    }

    OptionSheet(
        state = sheetState,
        headerContent = {
            Text(
                text = sectionName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
        },
        optionList = arrayOf(options)
    )
}

private fun sectionItemPosition(index: Int, size: Int): Position {
    return when {
        size == 1 -> Position.Alone
        index == 0 -> Position.Top
        index == size - 1 -> Position.Bottom
        else -> Position.Middle
    }
}

@Composable
private fun GroupMethodSectionRow(
    title: String,
    currentValue: String,
    options: List<Pair<String, String>>,
    onValueChange: (String) -> Unit,
    position: Position,
    showRestartNote: Boolean = false,
) {
    val fullCornerRadius = 24.dp
    val normalCornerRadius = 8.dp
    val shape = when (position) {
        Position.Alone -> RoundedCornerShape(fullCornerRadius)
        Position.Top -> RoundedCornerShape(
            topStart = fullCornerRadius, topEnd = fullCornerRadius,
            bottomStart = normalCornerRadius, bottomEnd = normalCornerRadius
        )
        Position.Middle -> RoundedCornerShape(normalCornerRadius)
        Position.Bottom -> RoundedCornerShape(
            topStart = normalCornerRadius, topEnd = normalCornerRadius,
            bottomStart = fullCornerRadius, bottomEnd = fullCornerRadius
        )
    }
    val paddingModifier = when (position) {
        Position.Alone -> Modifier.padding(bottom = 16.dp)
        Position.Bottom -> Modifier.padding(top = 1.dp, bottom = 16.dp)
        Position.Middle -> Modifier.padding(vertical = 1.dp)
        Position.Top -> Modifier.padding(bottom = 1.dp)
    }

    Column(
        modifier = Modifier
            .then(paddingModifier)
            .padding(horizontal = 16.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(10.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { (value, label) ->
                val isSelected = currentValue == value
                val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHigh
                val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(100))
                        .background(backgroundColor)
                        .clickable { onValueChange(value) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = contentColor,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
        if (showRestartNote) {
            Text(
                text = stringResource(R.string.media_grouping_restart_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
