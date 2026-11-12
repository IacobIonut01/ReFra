/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.settings.ui

import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.outlined.BurstMode
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import com.dot.gallery.core.ui.components.PreviewFocusPhoto
import androidx.compose.material.icons.outlined.WebAsset
import com.dot.gallery.core.ui.components.PreviewPhoto
import com.dot.gallery.feature.settings.generated.resources.merge_albums_preview_album
import com.dot.gallery.feature.settings.generated.resources.merge_albums_preview_merged
import com.dot.gallery.feature.settings.generated.resources.merge_albums_preview_phone
import com.dot.gallery.feature.settings.generated.resources.merge_albums_preview_sd_card
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CalendarViewMonth
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.GifBox
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.MergeType
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PermMedia
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.Workspaces
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Gif
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dot.gallery.core.Position
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.core.presentation.components.AlbumSectionNameSheet
import com.dot.gallery.core.presentation.components.DeleteSectionSheet
import com.dot.gallery.core.presentation.components.FilterKind
import com.dot.gallery.core.presentation.components.OptionItem
import com.dot.gallery.core.presentation.components.OptionSheet
import com.dot.gallery.core.presentation.components.SectionDialogMode
import com.dot.gallery.core.ui.components.BaseSettingsScreen
import com.dot.gallery.core.ui.components.ChooserPreferenceDetailScreen
import com.dot.gallery.core.ui.components.PreferenceOption
import com.dot.gallery.core.ui.components.SettingsItem
import com.dot.gallery.core.ui.components.SwitchPreferenceDetailScreen
import com.dot.gallery.core.ui.components.rememberPreference
import com.dot.gallery.core.ui.components.rememberSwitchPreference
import com.dot.gallery.core.ui.settings.rememberPreference
import com.dot.gallery.core.ui.util.AppBottomSheetState
import com.dot.gallery.core.ui.util.rememberAppBottomSheetState
import com.dot.gallery.core.ui.util.rememberedDerivedState
import com.dot.gallery.domain.model.AlbumSection
import com.dot.gallery.domain.model.AlbumSectionType
import com.dot.gallery.domain.model.LastSort
import com.dot.gallery.domain.model.sectionType
import com.dot.gallery.domain.settings.FAV_ICON_BOTTOM_END
import com.dot.gallery.domain.settings.FAV_ICON_BOTTOM_START
import com.dot.gallery.domain.settings.FAV_ICON_DISABLED
import com.dot.gallery.domain.settings.FAV_ICON_TOP_END
import com.dot.gallery.domain.settings.FAV_ICON_TOP_START
import com.dot.gallery.domain.settings.GROUP_MONTHLY
import com.dot.gallery.domain.settings.GROUP_NORMAL
import com.dot.gallery.domain.settings.GROUP_YEARLY
import com.dot.gallery.domain.settings.LAYOUT_GRID
import com.dot.gallery.domain.settings.LAYOUT_MOSAIC
import com.dot.gallery.domain.settings.SECTION_CATEGORIES
import com.dot.gallery.domain.settings.SECTION_PEOPLE
import com.dot.gallery.domain.settings.SettingsKeys
import com.dot.gallery.domain.util.OrderType
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.feature.settings.generated.resources.Res
import com.dot.gallery.feature.settings.generated.resources.album_count
import com.dot.gallery.feature.settings.generated.resources.album_sections_detail_description
import com.dot.gallery.feature.settings.generated.resources.album_sections_manage_title
import com.dot.gallery.feature.settings.generated.resources.album_sections_summary
import com.dot.gallery.feature.settings.generated.resources.album_sections_title
import com.dot.gallery.feature.settings.generated.resources.albums
import com.dot.gallery.feature.settings.generated.resources.allow_gif_animation_description
import com.dot.gallery.feature.settings.generated.resources.allow_gif_animation_summary
import com.dot.gallery.feature.settings.generated.resources.allow_gif_animation_title
import com.dot.gallery.feature.settings.generated.resources.capture_time
import com.dot.gallery.feature.settings.generated.resources.capture_time_summary
import com.dot.gallery.feature.settings.generated.resources.create_section
import com.dot.gallery.feature.settings.generated.resources.customize_date_formats
import com.dot.gallery.feature.settings.generated.resources.date_header
import com.dot.gallery.feature.settings.generated.resources.date_header_summary
import com.dot.gallery.feature.settings.generated.resources.date_headers_albums
import com.dot.gallery.feature.settings.generated.resources.date_headers_cloud_archive
import com.dot.gallery.feature.settings.generated.resources.date_headers_description
import com.dot.gallery.feature.settings.generated.resources.date_headers_favorites
import com.dot.gallery.feature.settings.generated.resources.date_headers_summary
import com.dot.gallery.feature.settings.generated.resources.date_headers_timeline
import com.dot.gallery.feature.settings.generated.resources.date_headers_title
import com.dot.gallery.feature.settings.generated.resources.date_headers_vault
import com.dot.gallery.feature.settings.generated.resources.delete_section
import com.dot.gallery.feature.settings.generated.resources.fav_position_bottom_end
import com.dot.gallery.feature.settings.generated.resources.fav_position_bottom_start
import com.dot.gallery.feature.settings.generated.resources.fav_position_disabled
import com.dot.gallery.feature.settings.generated.resources.fav_position_top_end
import com.dot.gallery.feature.settings.generated.resources.fav_position_top_start
import com.dot.gallery.feature.settings.generated.resources.favorite_icon_description
import com.dot.gallery.feature.settings.generated.resources.favorite_icon_on_thumbnails
import com.dot.gallery.feature.settings.generated.resources.group_burst_sequences_summary
import com.dot.gallery.feature.settings.generated.resources.group_burst_sequences_title
import com.dot.gallery.feature.settings.generated.resources.group_cloud_local_summary
import com.dot.gallery.feature.settings.generated.resources.group_cloud_local_title
import com.dot.gallery.feature.settings.generated.resources.group_edited_copies_summary
import com.dot.gallery.feature.settings.generated.resources.group_edited_copies_title
import com.dot.gallery.feature.settings.generated.resources.group_method_albums
import com.dot.gallery.feature.settings.generated.resources.group_method_cloud_archive
import com.dot.gallery.feature.settings.generated.resources.group_method_description
import com.dot.gallery.feature.settings.generated.resources.group_method_favorites
import com.dot.gallery.feature.settings.generated.resources.group_method_monthly
import com.dot.gallery.feature.settings.generated.resources.group_method_normal
import com.dot.gallery.feature.settings.generated.resources.group_method_summary
import com.dot.gallery.feature.settings.generated.resources.group_method_timeline
import com.dot.gallery.feature.settings.generated.resources.group_method_title
import com.dot.gallery.feature.settings.generated.resources.group_method_vault
import com.dot.gallery.feature.settings.generated.resources.group_method_yearly
import com.dot.gallery.feature.settings.generated.resources.group_raw_jpg_summary
import com.dot.gallery.feature.settings.generated.resources.group_raw_jpg_title
import com.dot.gallery.feature.settings.generated.resources.group_similar_media_description
import com.dot.gallery.feature.settings.generated.resources.group_similar_media_summary
import com.dot.gallery.feature.settings.generated.resources.group_similar_media_title
import com.dot.gallery.feature.settings.generated.resources.library
import com.dot.gallery.feature.settings.generated.resources.library_categories_section_summary
import com.dot.gallery.feature.settings.generated.resources.library_categories_section_title
import com.dot.gallery.feature.settings.generated.resources.library_people_section_summary
import com.dot.gallery.feature.settings.generated.resources.library_people_section_title
import com.dot.gallery.feature.settings.generated.resources.location_categories_summary
import com.dot.gallery.feature.settings.generated.resources.location_categories_title
import com.dot.gallery.feature.settings.generated.resources.media_grouping_restart_note
import com.dot.gallery.feature.settings.generated.resources.media_type_albums_setting_summary
import com.dot.gallery.feature.settings.generated.resources.media_type_albums_setting_title
import com.dot.gallery.feature.settings.generated.resources.merge_albums_by_name
import com.dot.gallery.feature.settings.generated.resources.merge_albums_by_name_description
import com.dot.gallery.feature.settings.generated.resources.merge_albums_by_name_summary
import com.dot.gallery.feature.settings.generated.resources.modified_time_summary
import com.dot.gallery.feature.settings.generated.resources.recheck_capture_dates
import com.dot.gallery.feature.settings.generated.resources.recheck_capture_dates_summary
import com.dot.gallery.feature.settings.generated.resources.rename_section
import com.dot.gallery.feature.settings.generated.resources.search
import com.dot.gallery.feature.settings.generated.resources.section_type_apps
import com.dot.gallery.feature.settings.generated.resources.section_type_common
import com.dot.gallery.feature.settings.generated.resources.section_type_custom
import com.dot.gallery.feature.settings.generated.resources.section_type_uncategorized
import com.dot.gallery.feature.settings.generated.resources.settings_display_header
import com.dot.gallery.feature.settings.generated.resources.settings_timeline_albums
import com.dot.gallery.feature.settings.generated.resources.top_bar_filter
import com.dot.gallery.feature.settings.generated.resources.top_bar_select
import com.dot.gallery.feature.settings.generated.resources.top_bar_settings
import com.dot.gallery.feature.settings.generated.resources.top_bar_summary_none
import com.dot.gallery.feature.settings.generated.resources.sort_ascending
import com.dot.gallery.feature.settings.generated.resources.sort_by_date_modified
import com.dot.gallery.feature.settings.generated.resources.sort_descending
import com.dot.gallery.feature.settings.generated.resources.story_cards_settings_summary
import com.dot.gallery.feature.settings.generated.resources.story_cards_settings_title
import com.dot.gallery.feature.settings.generated.resources.timeline
import com.dot.gallery.feature.settings.generated.resources.month_highlights_title
import com.dot.gallery.feature.settings.generated.resources.month_highlights_summary
import com.dot.gallery.feature.settings.generated.resources.month_highlights_description
import com.dot.gallery.feature.settings.generated.resources.month_recaps_title
import com.dot.gallery.feature.settings.generated.resources.month_recaps_summary
import com.dot.gallery.feature.settings.generated.resources.header_places_title
import com.dot.gallery.feature.settings.generated.resources.header_places_summary
import com.dot.gallery.feature.settings.generated.resources.header_places_description
import com.dot.gallery.feature.settings.generated.resources.floating_date_label_title
import com.dot.gallery.feature.settings.generated.resources.floating_date_label_summary
import com.dot.gallery.feature.settings.generated.resources.floating_date_label_description
import com.dot.gallery.feature.settings.generated.resources.timeline_layout_description
import com.dot.gallery.feature.settings.generated.resources.timeline_layout_grid
import com.dot.gallery.feature.settings.generated.resources.timeline_layout_mosaic
import com.dot.gallery.feature.settings.generated.resources.timeline_layout_type
import com.dot.gallery.feature.settings.generated.resources.timeline_layout_type_summary
import com.dot.gallery.feature.settings.generated.resources.timeline_order
import com.dot.gallery.feature.settings.generated.resources.timeline_order_description
import com.dot.gallery.feature.settings.generated.resources.update_modified_date_summary
import com.dot.gallery.feature.settings.generated.resources.update_modified_date_title
import com.dot.gallery.core.presentation.components.GalleryPageTopBar
import com.dot.gallery.core.presentation.components.HomeShortcut
import com.dot.gallery.core.presentation.components.label
import com.dot.gallery.core.presentation.components.rememberHomeShortcut
import com.dot.gallery.core.presentation.components.rememberScrolledUnderTopBar
import com.dot.gallery.core.presentation.util.LocalHazeState
import dev.chrisbanes.haze.hazeSource

private const val DETAIL_TIMELINE_LAYOUT = "timeline_layout"
private const val DETAIL_TIMELINE_ORDER = "timeline_order"
private const val DETAIL_GROUP_SIMILAR = "group_similar"
private const val DETAIL_GIF_ANIMATION = "gif_animation"
private const val DETAIL_TOP_BAR = "top_bar"

/** The Top bar row's summary: the extra buttons that are on, or [none] when there are none. */
internal fun topBarButtonsSummary(enabled: List<String>, none: String): String =
    enabled.joinToString(", ").ifEmpty { none }
private const val DETAIL_MERGE_ALBUMS = "merge_albums"
private const val DETAIL_ALBUM_SECTIONS = "album_sections"
private const val DETAIL_FAV_ICON = "fav_icon"
private const val DETAIL_DATE_HEADERS = "date_headers"
private const val DETAIL_GROUP_METHOD = "group_method"
private const val DETAIL_MONTH_HIGHLIGHTS = "month_highlights"
private const val DETAIL_HEADER_PLACES = "header_places"
private const val DETAIL_FLOATING_DATE = "floating_date"

@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SettingsTimelineAlbumsContent(
    state: SettingsTimelineAlbumsState = SettingsTimelineAlbumsState(),
    actions: SettingsTimelineAlbumsActions = SettingsTimelineAlbumsActions(),
) {
    var detailKey by rememberSaveable { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    var groupByMonth by rememberPreference(SettingsKeys.timelineGroupByMonth)
    var groupByYear by rememberPreference(SettingsKeys.timelineGroupByYear)
    var timelineLayoutType by rememberPreference(SettingsKeys.timelineLayoutType)
    var timelineSort by rememberPreference(SettingsKeys.timelineSort)
    var groupSimilarMedia by rememberPreference(SettingsKeys.groupSimilarMedia)
    var groupRawJpg by rememberPreference(SettingsKeys.groupRawJpg)
    var groupEditedCopies by rememberPreference(SettingsKeys.groupEditedCopies)
    var groupBurstSequences by rememberPreference(SettingsKeys.groupBurstSequences)
    var groupCloudLocal by rememberPreference(SettingsKeys.groupCloudLocal)
    var allowGifAnimation by rememberPreference(SettingsKeys.allowGifAnimation)
    val showFilterButton by rememberPreference(SettingsKeys.showFilterButton)
    val homeShortcut = rememberHomeShortcut()
    val showSelectButton by rememberPreference(SettingsKeys.showHomeSelectButton)
    var dateHeaderAlbums by rememberPreference(SettingsKeys.albumGroupByDate)
    var mergeAlbumsByName by rememberPreference(SettingsKeys.mergeAlbumsByName)
    var albumSectionsEnabled by rememberPreference(SettingsKeys.albumSectionsEnabled)
    var showMediaTypeAlbums by rememberPreference(SettingsKeys.showMediaTypeAlbums)
    var updateModifiedDate by rememberPreference(SettingsKeys.updateModifiedDate)
    var favIconPosition by rememberPreference(SettingsKeys.favoriteIconPosition)
    var dateHeaderTimeline by rememberPreference(SettingsKeys.timelineGroupByDate)
    var dateHeaderFavorites by rememberPreference(SettingsKeys.favoritesGroupByDate)
    var dateHeaderVault by rememberPreference(SettingsKeys.vaultGroupByDate)
    var dateHeaderCloudArchive by rememberPreference(SettingsKeys.cloudArchiveGroupByDate)
    var groupMethodTimeline by rememberPreference(SettingsKeys.timelineGroupMethod)
    var groupMethodAlbums by rememberPreference(SettingsKeys.albumsGroupMethod)
    var groupMethodFavorites by rememberPreference(SettingsKeys.favoritesGroupMethod)
    var groupMethodVault by rememberPreference(SettingsKeys.vaultGroupMethod)
    var groupMethodCloudArchive by rememberPreference(SettingsKeys.cloudArchiveGroupMethod)
    var showLocationCategories by rememberPreference(SettingsKeys.showLocationCategories)
    var monthHighlights by rememberPreference(SettingsKeys.timelineMonthHighlights)
    var monthRecaps by rememberPreference(SettingsKeys.timelineMonthRecaps)
    var headerPlaces by rememberPreference(SettingsKeys.timelineHeaderPlaces)
    var floatingDateLabel by rememberPreference(SettingsKeys.timelineFloatingDateLabel)
    var hiddenSections by rememberPreference(SettingsKeys.hiddenSections)

    when (detailKey) {
        DETAIL_TIMELINE_LAYOUT -> {
            BackHandler { detailKey = null }
            ChooserPreferenceDetailScreen(
                title = stringResource(Res.string.timeline_layout_type),
                description = stringResource(Res.string.timeline_layout_description),
                optionPreview = { layout -> TimelineLayoutTilePreview(layout) },
                options = listOf(
                    PreferenceOption(LAYOUT_GRID, stringResource(Res.string.timeline_layout_grid), timelineLayoutType == LAYOUT_GRID),
                    PreferenceOption(LAYOUT_MOSAIC, stringResource(Res.string.timeline_layout_mosaic), timelineLayoutType == LAYOUT_MOSAIC),
                ),
                onOptionSelected = { timelineLayoutType = it },
            )
        }
        DETAIL_TIMELINE_ORDER -> {
            BackHandler { detailKey = null }
            val captureSummary = stringResource(Res.string.capture_time_summary)
            val modifiedSummary = stringResource(Res.string.modified_time_summary)
            val newest = stringResource(Res.string.sort_descending)
            val oldest = stringResource(Res.string.sort_ascending)
            ChooserPreferenceDetailScreen(
                title = stringResource(Res.string.timeline_order),
                description = stringResource(Res.string.timeline_order_description),
                options = listOf(
                    LastSort(OrderType.Descending, FilterKind.DATE),
                    LastSort(OrderType.Ascending, FilterKind.DATE),
                    LastSort(OrderType.Descending, FilterKind.DATE_MODIFIED),
                    LastSort(OrderType.Ascending, FilterKind.DATE_MODIFIED),
                ).map { option ->
                    val capture = option.kind == FilterKind.DATE
                    PreferenceOption(
                        value = option,
                        label = "${if (capture) stringResource(Res.string.capture_time) else stringResource(Res.string.sort_by_date_modified)} · ${if (option.orderType == OrderType.Descending) newest else oldest}",
                        isSelected = timelineSort == option,
                        description = if (capture) captureSummary else modifiedSummary,
                    )
                },
                onOptionSelected = { timelineSort = it },
            )
        }
        DETAIL_GROUP_SIMILAR -> {
            BackHandler { detailKey = null }
            SwitchPreferenceDetailScreen(
                title = stringResource(Res.string.group_similar_media_title),
                isChecked = groupSimilarMedia,
                onCheckedChange = { groupSimilarMedia = it },
                description = stringResource(Res.string.group_similar_media_description),
                preview = { checked -> GroupSimilarPreview(checked) },
                customContent = {
                    Column(modifier = Modifier.padding(top = 16.dp)) {
                        SettingsItem(
                            item = SettingsEntity.SwitchPreference(
                                title = stringResource(Res.string.group_raw_jpg_title),
                                summary = stringResource(Res.string.group_raw_jpg_summary),
                                isChecked = groupRawJpg,
                                onCheck = { groupRawJpg = it },
                                screenPosition = Position.Top
                            )
                        )
                        SettingsItem(
                            item = SettingsEntity.SwitchPreference(
                                title = stringResource(Res.string.group_edited_copies_title),
                                summary = stringResource(Res.string.group_edited_copies_summary),
                                isChecked = groupEditedCopies,
                                onCheck = { groupEditedCopies = it },
                                screenPosition = Position.Middle
                            )
                        )
                        SettingsItem(
                            item = SettingsEntity.SwitchPreference(
                                title = stringResource(Res.string.group_burst_sequences_title),
                                summary = stringResource(Res.string.group_burst_sequences_summary),
                                isChecked = groupBurstSequences,
                                onCheck = { groupBurstSequences = it },
                                screenPosition = Position.Middle
                            )
                        )
                        SettingsItem(
                            item = SettingsEntity.SwitchPreference(
                                title = stringResource(Res.string.group_cloud_local_title),
                                summary = stringResource(Res.string.group_cloud_local_summary),
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
                title = stringResource(Res.string.allow_gif_animation_title),
                isChecked = allowGifAnimation,
                onCheckedChange = { allowGifAnimation = it },
                description = stringResource(Res.string.allow_gif_animation_description),
                preview = { checked -> AnimateGifsPreview(checked) },
            )
        }
        DETAIL_TOP_BAR -> {
            BackHandler { detailKey = null }
            TopBarSettingsDetail(support = state.homeShortcutSupport)
        }
        DETAIL_MERGE_ALBUMS -> {
            BackHandler { detailKey = null }
            SwitchPreferenceDetailScreen(
                title = stringResource(Res.string.merge_albums_by_name),
                isChecked = mergeAlbumsByName,
                onCheckedChange = { mergeAlbumsByName = it },
                description = stringResource(Res.string.merge_albums_by_name_description),
                preview = { checked -> MergeAlbumsPreview(checked) },
            )
        }
        DETAIL_ALBUM_SECTIONS -> {
            BackHandler { detailKey = null }
            AlbumSectionsDetailContent(
                isEnabled = albumSectionsEnabled,
                onEnabledChange = {
                    albumSectionsEnabled = it
                    if (it) actions.onEnsureDefaultSections()
                },
                sections = state.albumSections,
                albumCountBySection = state.albumCountBySection,
                actions = actions,
            )
        }
        DETAIL_FAV_ICON -> {
            BackHandler { detailKey = null }
            ChooserPreferenceDetailScreen(
                title = stringResource(Res.string.favorite_icon_on_thumbnails),
                description = stringResource(Res.string.favorite_icon_description),
                preview = { FavoriteIconPreview(favIconPosition) },
                options = listOf(
                    PreferenceOption(FAV_ICON_DISABLED, stringResource(Res.string.fav_position_disabled), favIconPosition == FAV_ICON_DISABLED),
                    PreferenceOption(FAV_ICON_BOTTOM_END, stringResource(Res.string.fav_position_bottom_end), favIconPosition == FAV_ICON_BOTTOM_END),
                    PreferenceOption(FAV_ICON_BOTTOM_START, stringResource(Res.string.fav_position_bottom_start), favIconPosition == FAV_ICON_BOTTOM_START),
                    PreferenceOption(FAV_ICON_TOP_END, stringResource(Res.string.fav_position_top_end), favIconPosition == FAV_ICON_TOP_END),
                    PreferenceOption(FAV_ICON_TOP_START, stringResource(Res.string.fav_position_top_start), favIconPosition == FAV_ICON_TOP_START),
                ),
                onOptionSelected = { favIconPosition = it },
            )
        }
        DETAIL_DATE_HEADERS -> {
            BackHandler { detailKey = null }
            ChooserPreferenceDetailScreen<Unit>(
                title = stringResource(Res.string.date_headers_title),
                description = stringResource(Res.string.date_headers_description),
                customContent = {
                    Column {
                        val sections = buildList {
                            add(Triple(Res.string.date_headers_timeline, dateHeaderTimeline) { v: Boolean -> dateHeaderTimeline = v })
                            add(Triple(Res.string.date_headers_albums, dateHeaderAlbums) { v: Boolean -> dateHeaderAlbums = v })
                            if (state.supportsFavorites) {
                                add(Triple(Res.string.date_headers_favorites, dateHeaderFavorites) { v: Boolean -> dateHeaderFavorites = v })
                            }
                            add(Triple(Res.string.date_headers_vault, dateHeaderVault) { v: Boolean -> dateHeaderVault = v })
                            add(Triple(Res.string.date_headers_cloud_archive, dateHeaderCloudArchive) { v: Boolean -> dateHeaderCloudArchive = v })
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
                                title = stringResource(Res.string.customize_date_formats),
                                summary = stringResource(Res.string.date_header_summary),
                                onClick = { actions.onDateFormatClick() },
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
                GROUP_NORMAL to stringResource(Res.string.group_method_normal),
                GROUP_MONTHLY to stringResource(Res.string.group_method_monthly),
                GROUP_YEARLY to stringResource(Res.string.group_method_yearly),
            )
            ChooserPreferenceDetailScreen<Unit>(
                title = stringResource(Res.string.group_method_title),
                description = stringResource(Res.string.group_method_description),
                customContent = {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        val sections = buildList {
                            add(Triple(Res.string.group_method_timeline, groupMethodTimeline) { v: String ->
                                if (v != groupMethodTimeline) {
                                    groupMethodTimeline = v
                                    groupByMonth = v == GROUP_MONTHLY
                                    groupByYear = v == GROUP_YEARLY
                                }
                            })
                            add(Triple(Res.string.group_method_albums, groupMethodAlbums) { v: String -> groupMethodAlbums = v })
                            if (state.supportsFavorites) {
                                add(Triple(Res.string.group_method_favorites, groupMethodFavorites) { v: String -> groupMethodFavorites = v })
                            }
                            add(Triple(Res.string.group_method_vault, groupMethodVault) { v: String -> groupMethodVault = v })
                            add(Triple(Res.string.group_method_cloud_archive, groupMethodCloudArchive) { v: String -> groupMethodCloudArchive = v })
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
        DETAIL_MONTH_HIGHLIGHTS -> {
            BackHandler { detailKey = null }
            SwitchPreferenceDetailScreen(
                title = stringResource(Res.string.month_highlights_title),
                isChecked = monthHighlights,
                onCheckedChange = { monthHighlights = it },
                description = stringResource(Res.string.month_highlights_description),
                preview = { checked -> MonthHighlightsPreview(checked) },
                customContent = {
                    Column(modifier = Modifier.padding(top = 16.dp)) {
                        SettingsItem(
                            item = SettingsEntity.SwitchPreference(
                                title = stringResource(Res.string.month_recaps_title),
                                summary = stringResource(Res.string.month_recaps_summary),
                                isChecked = monthRecaps,
                                onCheck = { monthRecaps = it },
                                screenPosition = Position.Alone
                            )
                        )
                    }
                },
            )
        }
        DETAIL_HEADER_PLACES -> {
            BackHandler { detailKey = null }
            SwitchPreferenceDetailScreen(
                title = stringResource(Res.string.header_places_title),
                isChecked = headerPlaces,
                onCheckedChange = { headerPlaces = it },
                description = stringResource(Res.string.header_places_description),
                useColumnLayout = true,
                preview = { checked -> HeaderPlacesPreview(checked) },
            )
        }
        DETAIL_FLOATING_DATE -> {
            BackHandler { detailKey = null }
            SwitchPreferenceDetailScreen(
                title = stringResource(Res.string.floating_date_label_title),
                isChecked = floatingDateLabel,
                onCheckedChange = { floatingDateLabel = it },
                description = stringResource(Res.string.floating_date_label_description),
                useColumnLayout = true,
                preview = { checked -> FloatingDatePreview(checked) },
            )
        }
        else -> {
            TimelineAlbumsListContent(
                state = state,
                timelineLayoutType = timelineLayoutType,
                timelineSort = timelineSort,
                groupSimilarMedia = groupSimilarMedia,
                onGroupSimilarChange = { groupSimilarMedia = it },
                allowGifAnimation = allowGifAnimation,
                onGifAnimationChange = { allowGifAnimation = it },
                showFilterButton = showFilterButton,
                homeShortcut = homeShortcut,
                showSelectButton = showSelectButton,
                mergeAlbumsByName = mergeAlbumsByName,
                onMergeAlbumsChange = { mergeAlbumsByName = it },
                albumSectionsEnabled = albumSectionsEnabled,
                onAlbumSectionsChange = { albumSectionsEnabled = it },
                showMediaTypeAlbums = showMediaTypeAlbums,
                onShowMediaTypeAlbumsChange = { showMediaTypeAlbums = it },
                updateModifiedDate = updateModifiedDate,
                onUpdateModifiedDateChange = { updateModifiedDate = it },
                favIconPosition = favIconPosition,
                showLocationCategories = showLocationCategories,
                onShowLocationCategoriesChange = { showLocationCategories = it },
                hiddenSections = hiddenSections,
                onHiddenSectionsChange = { hiddenSections = it },
                monthHighlights = monthHighlights,
                onMonthHighlightsChange = { monthHighlights = it },
                headerPlaces = headerPlaces,
                onHeaderPlacesChange = { headerPlaces = it },
                floatingDateLabel = floatingDateLabel,
                onFloatingDateLabelChange = { floatingDateLabel = it },
                onDetailClick = { detailKey = it },
                onDateFormatClick = { actions.onDateFormatClick() },
                onStoryCardsClick = actions.onStoryCardsClick,
                onRecheckCaptureDates = actions.onRecheckCaptureDates,
                searchSink = actions.searchSink,
                searchRoute = actions.searchRoute,
                listState = listState,
            )
        }
    }
}
@Composable
private fun TimelineAlbumsListContent(
    state: SettingsTimelineAlbumsState,
    timelineLayoutType: String,
    timelineSort: LastSort,
    groupSimilarMedia: Boolean,
    onGroupSimilarChange: (Boolean) -> Unit,
    allowGifAnimation: Boolean,
    onGifAnimationChange: (Boolean) -> Unit,
    showFilterButton: Boolean,
    homeShortcut: HomeShortcut,
    showSelectButton: Boolean,
    mergeAlbumsByName: Boolean,
    onMergeAlbumsChange: (Boolean) -> Unit,
    albumSectionsEnabled: Boolean = false,
    onAlbumSectionsChange: (Boolean) -> Unit = {},
    showMediaTypeAlbums: Boolean = true,
    onShowMediaTypeAlbumsChange: (Boolean) -> Unit = {},
    updateModifiedDate: Boolean,
    onUpdateModifiedDateChange: (Boolean) -> Unit,
    favIconPosition: String,
    showLocationCategories: Boolean = true,
    onShowLocationCategoriesChange: (Boolean) -> Unit = {},
    hiddenSections: Set<String> = emptySet(),
    onHiddenSectionsChange: (Set<String>) -> Unit = {},
    monthHighlights: Boolean = true,
    onMonthHighlightsChange: (Boolean) -> Unit = {},
    headerPlaces: Boolean = true,
    onHeaderPlacesChange: (Boolean) -> Unit = {},
    floatingDateLabel: Boolean = true,
    onFloatingDateLabelChange: (Boolean) -> Unit = {},
    onDetailClick: (String) -> Unit,
    onDateFormatClick: () -> Unit,
    onStoryCardsClick: () -> Unit = {},
    onRecheckCaptureDates: () -> Unit = {},
    searchSink: ((String, List<String>) -> Unit)?,
    searchRoute: String?,
    listState: LazyListState,
) {
    @Composable
    fun settings(): SnapshotStateList<SettingsEntity> {
        val timelineHeaderTitle = stringResource(Res.string.timeline)
        val timelineHeader = remember(timelineHeaderTitle) {
            SettingsEntity.Header(title = timelineHeaderTitle)
        }

        val mosaicLayoutLabel = stringResource(Res.string.timeline_layout_mosaic)
        val gridLayoutLabel = stringResource(Res.string.timeline_layout_grid)
        val layoutLabel = remember(timelineLayoutType, mosaicLayoutLabel, gridLayoutLabel) {
            when (timelineLayoutType) {
                LAYOUT_MOSAIC -> mosaicLayoutLabel
                else -> gridLayoutLabel
            }
        }
        val timelineLayoutPref = rememberPreference(
            timelineLayoutType,
            title = stringResource(Res.string.timeline_layout_type),
            icon = Icons.Outlined.GridView,
            summary = stringResource(Res.string.timeline_layout_type_summary) + " ($layoutLabel)",
            onClick = { onDetailClick(DETAIL_TIMELINE_LAYOUT) },
            screenPosition = Position.Top
        )

        val timelineSortLabel = if (timelineSort.kind == FilterKind.DATE) {
            stringResource(Res.string.capture_time)
        } else {
            stringResource(Res.string.sort_by_date_modified)
        }
        val timelineDirectionLabel = if (timelineSort.orderType == OrderType.Descending) {
            stringResource(Res.string.sort_descending)
        } else {
            stringResource(Res.string.sort_ascending)
        }
        val timelineOrderPref = rememberPreference(
            timelineSort,
            title = stringResource(Res.string.timeline_order),
            icon = Icons.Outlined.Sort,
            summary = "$timelineSortLabel · $timelineDirectionLabel",
            onClick = { onDetailClick(DETAIL_TIMELINE_ORDER) },
            screenPosition = Position.Middle
        )

        val groupSimilarMediaPref = rememberSwitchPreference(
            groupSimilarMedia,
            title = stringResource(Res.string.group_similar_media_title),
            icon = Icons.Outlined.Layers,
            summary = stringResource(Res.string.group_similar_media_summary),
            isChecked = groupSimilarMedia,
            onCheck = onGroupSimilarChange,
            onClick = { onDetailClick(DETAIL_GROUP_SIMILAR) },
            screenPosition = Position.Middle
        )

        val allowGifAnimationPref = rememberSwitchPreference(
            allowGifAnimation,
            title = stringResource(Res.string.allow_gif_animation_title),
            icon = Icons.Outlined.GifBox,
            summary = stringResource(Res.string.allow_gif_animation_summary),
            isChecked = allowGifAnimation,
            onCheck = onGifAnimationChange,
            onClick = { onDetailClick(DETAIL_GIF_ANIMATION) },
            screenPosition = Position.Middle
        )

        val monthHighlightsPref = rememberSwitchPreference(
            monthHighlights,
            title = stringResource(Res.string.month_highlights_title),
            icon = Icons.Outlined.AutoAwesome,
            summary = stringResource(Res.string.month_highlights_summary),
            isChecked = monthHighlights,
            onCheck = onMonthHighlightsChange,
            onClick = { onDetailClick(DETAIL_MONTH_HIGHLIGHTS) },
            screenPosition = Position.Middle
        )

        val headerPlacesPref = rememberSwitchPreference(
            headerPlaces,
            title = stringResource(Res.string.header_places_title),
            icon = Icons.Outlined.Place,
            summary = stringResource(Res.string.header_places_summary),
            isChecked = headerPlaces,
            onCheck = onHeaderPlacesChange,
            onClick = { onDetailClick(DETAIL_HEADER_PLACES) },
            screenPosition = Position.Middle
        )

        val floatingDateLabelPref = rememberSwitchPreference(
            floatingDateLabel,
            title = stringResource(Res.string.floating_date_label_title),
            icon = Icons.Outlined.CalendarViewMonth,
            summary = stringResource(Res.string.floating_date_label_summary),
            isChecked = floatingDateLabel,
            onCheck = onFloatingDateLabelChange,
            onClick = { onDetailClick(DETAIL_FLOATING_DATE) },
            screenPosition = Position.Middle
        )

        val dateHeaderPref = rememberPreference(
            title = stringResource(Res.string.date_header),
            icon = Icons.Outlined.CalendarMonth,
            summary = stringResource(Res.string.date_header_summary),
            onClick = onDateFormatClick,
            screenPosition = Position.Middle
        )

        val recheckCaptureDatesPref = rememberPreference(
            title = stringResource(Res.string.recheck_capture_dates),
            icon = Icons.Outlined.EventRepeat,
            summary = stringResource(Res.string.recheck_capture_dates_summary),
            onClick = onRecheckCaptureDates,
            screenPosition = Position.Middle
        )

        val topBarSummary = topBarButtonsSummary(
            listOfNotNull(
                stringResource(Res.string.top_bar_select).takeIf { showSelectButton },
                homeShortcut.takeIf { it != HomeShortcut.NONE && state.homeShortcutSupport.supports(it) }?.label(),
                stringResource(Res.string.top_bar_filter).takeIf { showFilterButton },
            ),
            none = stringResource(Res.string.top_bar_summary_none),
        )
        val topBarPref = rememberPreference(
            topBarSummary,
            title = stringResource(Res.string.top_bar_settings),
            icon = Icons.Outlined.WebAsset,
            summary = topBarSummary,
            onClick = { onDetailClick(DETAIL_TOP_BAR) },
            screenPosition = Position.Middle
        )

        val storyCardsPref = rememberPreference(
            title = stringResource(Res.string.story_cards_settings_title),
            icon = Icons.Outlined.AutoStories,
            summary = stringResource(Res.string.story_cards_settings_summary),
            onClick = onStoryCardsClick,
            screenPosition = Position.Bottom
        )

        val albumsHeaderTitle = stringResource(Res.string.albums)
        val albumsHeader = remember(albumsHeaderTitle) {
            SettingsEntity.Header(title = albumsHeaderTitle)
        }

        val mergeAlbumsByNamePref = rememberSwitchPreference(
            mergeAlbumsByName,
            title = stringResource(Res.string.merge_albums_by_name),
            icon = Icons.Outlined.MergeType,
            summary = stringResource(Res.string.merge_albums_by_name_summary),
            isChecked = mergeAlbumsByName,
            onCheck = onMergeAlbumsChange,
            onClick = { onDetailClick(DETAIL_MERGE_ALBUMS) },
            screenPosition = Position.Top
        )

        val updateModifiedDatePref = rememberSwitchPreference(
            updateModifiedDate,
            title = stringResource(Res.string.update_modified_date_title),
            icon = Icons.Outlined.EditCalendar,
            summary = stringResource(Res.string.update_modified_date_summary),
            isChecked = updateModifiedDate,
            onCheck = onUpdateModifiedDateChange,
            screenPosition = Position.Middle
        )

        val albumSectionsPref = rememberSwitchPreference(
            albumSectionsEnabled,
            title = stringResource(Res.string.album_sections_title),
            icon = Icons.Outlined.ViewAgenda,
            summary = stringResource(Res.string.album_sections_summary),
            isChecked = albumSectionsEnabled,
            onCheck = onAlbumSectionsChange,
            onClick = { onDetailClick(DETAIL_ALBUM_SECTIONS) },
            screenPosition = Position.Middle
        )

        val showMediaTypeAlbumsPref = rememberSwitchPreference(
            showMediaTypeAlbums,
            title = stringResource(Res.string.media_type_albums_setting_title),
            icon = Icons.Outlined.PermMedia,
            summary = stringResource(Res.string.media_type_albums_setting_summary),
            isChecked = showMediaTypeAlbums,
            onCheck = onShowMediaTypeAlbumsChange,
            screenPosition = Position.Bottom
        )

        val favDisabledLabel = stringResource(Res.string.fav_position_disabled)
        val favBottomStartLabel = stringResource(Res.string.fav_position_bottom_start)
        val favTopEndLabel = stringResource(Res.string.fav_position_top_end)
        val favTopStartLabel = stringResource(Res.string.fav_position_top_start)
        val favBottomEndLabel = stringResource(Res.string.fav_position_bottom_end)
        val favIconPositionLabel = remember(
            favIconPosition,
            favDisabledLabel,
            favBottomStartLabel,
            favTopEndLabel,
            favTopStartLabel,
            favBottomEndLabel,
        ) {
            when (favIconPosition) {
                FAV_ICON_DISABLED -> favDisabledLabel
                FAV_ICON_BOTTOM_START -> favBottomStartLabel
                FAV_ICON_TOP_END -> favTopEndLabel
                FAV_ICON_TOP_START -> favTopStartLabel
                else -> favBottomEndLabel
            }
        }
        val hasFavorites = state.supportsFavorites
        val favIconPositionPref = rememberPreference(
            favIconPosition,
            title = stringResource(Res.string.favorite_icon_on_thumbnails),
            icon = Icons.Outlined.Favorite,
            summary = favIconPositionLabel,
            onClick = { onDetailClick(DETAIL_FAV_ICON) },
            screenPosition = Position.Bottom
        )

        val displayHeaderTitle = stringResource(Res.string.settings_display_header)
        val displayHeader = remember(displayHeaderTitle) {
            SettingsEntity.Header(title = displayHeaderTitle)
        }

        val dateHeadersPref = rememberPreference(
            title = stringResource(Res.string.date_headers_title),
            icon = Icons.Outlined.DateRange,
            summary = stringResource(Res.string.date_headers_summary),
            onClick = { onDetailClick(DETAIL_DATE_HEADERS) },
            screenPosition = Position.Top
        )

        val groupMethodPref = rememberPreference(
            title = stringResource(Res.string.group_method_title),
            icon = Icons.Outlined.Workspaces,
            summary = stringResource(Res.string.group_method_summary),
            onClick = { onDetailClick(DETAIL_GROUP_METHOD) },
            screenPosition = if (hasFavorites) Position.Middle else Position.Bottom
        )

        val libraryHeaderTitle = stringResource(Res.string.library)
        val libraryHeader = remember(libraryHeaderTitle) {
            SettingsEntity.Header(title = libraryHeaderTitle)
        }

        val locationCategoriesPref = rememberSwitchPreference(
            showLocationCategories,
            title = stringResource(Res.string.location_categories_title),
            icon = Icons.Outlined.Place,
            summary = stringResource(Res.string.location_categories_summary),
            isChecked = showLocationCategories,
            onCheck = onShowLocationCategoriesChange,
            screenPosition = Position.Top
        )

        // Section visibility parity with the Library edit-mode Sections card.
        val peopleSectionPref = rememberSwitchPreference(
            hiddenSections,
            title = stringResource(Res.string.library_people_section_title),
            icon = Icons.Outlined.People,
            summary = stringResource(Res.string.library_people_section_summary),
            isChecked = SECTION_PEOPLE !in hiddenSections,
            onCheck = { checked ->
                onHiddenSectionsChange(
                    if (checked) hiddenSections - SECTION_PEOPLE
                    else hiddenSections + SECTION_PEOPLE
                )
            },
            screenPosition = Position.Middle
        )
        val categoriesSectionPref = rememberSwitchPreference(
            hiddenSections,
            title = stringResource(Res.string.library_categories_section_title),
            icon = Icons.Outlined.Category,
            summary = stringResource(Res.string.library_categories_section_summary),
            isChecked = SECTION_CATEGORIES !in hiddenSections,
            onCheck = { checked ->
                onHiddenSectionsChange(
                    if (checked) hiddenSections - SECTION_CATEGORIES
                    else hiddenSections + SECTION_CATEGORIES
                )
            },
            screenPosition = Position.Bottom
        )

        return remember(
            timelineHeader, timelineLayoutPref, timelineOrderPref, groupSimilarMediaPref,
            allowGifAnimationPref, monthHighlightsPref, headerPlacesPref, floatingDateLabelPref,
            dateHeaderPref, recheckCaptureDatesPref, topBarPref, storyCardsPref,
            albumsHeader, mergeAlbumsByNamePref, updateModifiedDatePref, albumSectionsPref,
            showMediaTypeAlbumsPref, displayHeader, favIconPositionPref,
            dateHeadersPref, groupMethodPref, libraryHeader, locationCategoriesPref,
            peopleSectionPref, categoriesSectionPref
        ) {
            mutableStateListOf<SettingsEntity>().apply {
                add(timelineHeader)
                add(timelineLayoutPref)
                add(timelineOrderPref)
                add(groupSimilarMediaPref)
                add(allowGifAnimationPref)
                add(monthHighlightsPref)
                add(headerPlacesPref)
                add(floatingDateLabelPref)
                add(dateHeaderPref)
                add(recheckCaptureDatesPref)
                add(topBarPref)
                add(storyCardsPref)

                add(albumsHeader)
                add(mergeAlbumsByNamePref)
                add(updateModifiedDatePref)
                add(albumSectionsPref)
                add(showMediaTypeAlbumsPref)

                add(displayHeader)
                add(dateHeadersPref)
                add(groupMethodPref)
                if (state.supportsFavorites) {
                    add(favIconPositionPref)
                }

                add(libraryHeader)
                add(locationCategoriesPref)
                add(peopleSectionPref)
                add(categoriesSectionPref)
            }
        }
    }

    BaseSettingsScreen(
        title = stringResource(Res.string.settings_timeline_albums),
        settingsList = settings(),
        listState = listState,
        searchRoute = searchRoute,
        searchSink = searchSink,
    )
}
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlbumSectionsDetailContent(
    isEnabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    sections: List<AlbumSection>,
    albumCountBySection: Map<Long, Int>,
    actions: SettingsTimelineAlbumsActions,
) {
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

    val listState = rememberLazyListState()

    AlbumSectionNameSheet(
        sheetState = sectionNameSheetState,
        mode = sectionDialogMode,
        initialName = sectionDialogInitialName,
        onCreateSection = { name ->
            actions.onCreateSection(name)
        },
        onRenameSection = { newName ->
            pendingSection?.let { actions.onRenameSection(it.id, newName) }
            pendingSection = null
        }
    )

    DeleteSectionSheet(
        sheetState = deleteSectionSheetState,
        onConfirmDelete = {
            pendingSection?.let { actions.onDeleteSection(it.id) }
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
        topBar = {
            GalleryPageTopBar(
                title = stringResource(Res.string.album_sections_title),
                hazeState = LocalHazeState.current,
                scrolled = rememberScrolledUnderTopBar(listState),
            )
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().hazeSource(LocalHazeState.current),
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
                        title = stringResource(Res.string.album_sections_title),
                        icon = Icons.Outlined.ViewAgenda,
                        summary = stringResource(Res.string.album_sections_summary),
                        isChecked = isEnabled,
                        onCheck = {
                            onEnabledChange(it)
                            if (it) actions.onEnsureDefaultSections()
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
                    text = stringResource(Res.string.album_sections_detail_description),
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
                        text = stringResource(Res.string.album_sections_manage_title),
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
                                actions.onReorderSections(reordered)
                                draggingIndex++
                                dragOffsetY -= itemHeightPx
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            } else if (dragOffsetY < -swapThreshold && draggingIndex > 0) {
                                val reordered = sections.map { it.id }.toMutableList()
                                val item = reordered.removeAt(draggingIndex)
                                reordered.add(draggingIndex - 1, item)
                                actions.onReorderSections(reordered)
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
                            actions.onToggleSectionVisibility(section.id, visible)
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
                        Text(stringResource(Res.string.create_section))
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
        AlbumSectionType.COMMON -> stringResource(Res.string.section_type_common)
        AlbumSectionType.APPS -> stringResource(Res.string.section_type_apps)
        AlbumSectionType.UNCATEGORIZED -> stringResource(Res.string.section_type_uncategorized)
        AlbumSectionType.CUSTOM -> stringResource(Res.string.section_type_custom)
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
                                "$sectionTypeLabel · ${pluralStringResource(Res.plurals.album_count, albumCount, albumCount)}"
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
    val renameLabel = stringResource(Res.string.rename_section)
    val deleteLabel = stringResource(Res.string.delete_section)
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
                text = stringResource(Res.string.media_grouping_restart_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/** Grid against Mosaic: the arrangement itself, in stand-in photos. */
@Composable
private fun TimelineLayoutTilePreview(layout: String) {
    val cell = 30.dp
    val gap = 2.dp
    if (layout == LAYOUT_MOSAIC) {
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                PreviewPhoto(3, Modifier.size(cell * 2 + gap).clip(RoundedCornerShape(4.dp)))
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    PreviewPhoto(1, Modifier.size(cell).clip(RoundedCornerShape(4.dp)))
                    PreviewPhoto(2, Modifier.size(cell).clip(RoundedCornerShape(4.dp)))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                PreviewPhoto(0, Modifier.size(cell * 1.5f + gap / 2, cell).clip(RoundedCornerShape(4.dp)))
                PreviewPhoto(4, Modifier.size(cell * 1.5f + gap / 2, cell).clip(RoundedCornerShape(4.dp)))
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            repeat(3) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    repeat(3) { col ->
                        PreviewPhoto(row * 3 + col, Modifier.size(cell).clip(RoundedCornerShape(4.dp)))
                    }
                }
            }
        }
    }
}

/** One photo; grouped, two similar shots stack behind it and a badge counts them. */
@Composable
private fun GroupSimilarPreview(isChecked: Boolean) {
    Box(modifier = Modifier.size(width = 132.dp, height = 140.dp)) {
        if (isChecked) {
            PreviewPhoto(
                index = 2,
                modifier = Modifier.offset(24.dp, 0.dp).size(84.dp, 100.dp).alpha(0.6f)
                    .clip(RoundedCornerShape(12.dp))
            )
            PreviewPhoto(
                index = 1,
                modifier = Modifier.offset(14.dp, 10.dp).size(104.dp, 110.dp).alpha(0.85f)
                    .clip(RoundedCornerShape(14.dp))
            )
        }
        PreviewFocusPhoto(index = 0, modifier = Modifier.offset(6.dp, 20.dp)) {
            if (isChecked) {
                Row(
                    modifier = Modifier
                        .padding(8.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(start = 5.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(Icons.Outlined.BurstMode, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Text("3", color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/** One GIF; animated, it gains a primary outline, a primary badge and playback progress. */
@Composable
private fun AnimateGifsPreview(isChecked: Boolean) {
    PreviewFocusPhoto(
        index = 1,
        modifier = if (isChecked) {
            Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
        } else Modifier,
    ) {
        Box(
            modifier = Modifier
                .padding(8.dp)
                .clip(CircleShape)
                .background(
                    if (isChecked) MaterialTheme.colorScheme.primary
                    else Color.Black.copy(alpha = 0.7f)
                )
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = "GIF",
                style = MaterialTheme.typography.labelMedium,
                color = if (isChecked) MaterialTheme.colorScheme.onPrimary else Color.White,
            )
        }
        if (isChecked) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.5f))
            ) {
                Box(
                    Modifier.fillMaxWidth(0.6f).fillMaxHeight().clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

/** Two albums with the same name from different places, or the single merged one. */
@Composable
private fun MergeAlbumsPreview(isChecked: Boolean) {
    val album = stringResource(Res.string.merge_albums_preview_album)
    Column(
        modifier = Modifier.width(144.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (isChecked) {
            MergeAlbumRow(album, stringResource(Res.string.merge_albums_preview_merged), photo = 0, merged = true)
        } else {
            MergeAlbumRow(album, stringResource(Res.string.merge_albums_preview_phone), photo = 0, merged = false)
            MergeAlbumRow(album, stringResource(Res.string.merge_albums_preview_sd_card), photo = 3, merged = false)
        }
    }
}

@Composable
private fun MergeAlbumRow(name: String, detail: String, photo: Int, merged: Boolean) {
    val container = if (merged) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceContainerLowest
    val content = if (merged) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(container)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PreviewPhoto(photo, Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)))
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1)
            Text(
                detail,
                style = MaterialTheme.typography.labelSmall,
                color = content.copy(alpha = 0.8f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (merged) {
            Icon(Icons.Outlined.Link, null, tint = content, modifier = Modifier.size(18.dp))
        }
    }
}

/** One thumbnail with the heart in the chosen corner, or none when hidden. */
@Composable
private fun FavoriteIconPreview(currentPosition: String) {
    val alignment = when (currentPosition) {
        FAV_ICON_BOTTOM_START -> Alignment.BottomStart
        FAV_ICON_TOP_END -> Alignment.TopEnd
        FAV_ICON_TOP_START -> Alignment.TopStart
        else -> Alignment.BottomEnd
    }
    PreviewFocusPhoto(index = 4, modifier = Modifier.padding(vertical = 16.dp)) {
        if (currentPosition != FAV_ICON_DISABLED) {
            Box(
                modifier = Modifier
                    .align(alignment)
                    .padding(8.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/** A month's plain bold header and grid, against its highlight card. */
@Composable
private fun MonthHighlightsPreview(isChecked: Boolean) {
    val c = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.width(140.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (isChecked) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(92.dp)
                    .clip(RoundedCornerShape(16.dp))
            ) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    PreviewPhoto(0, Modifier.weight(2f).fillMaxHeight())
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        PreviewPhoto(1, Modifier.fillMaxWidth().weight(1f))
                        PreviewPhoto(2, Modifier.fillMaxWidth().weight(1f))
                    }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
                Column(Modifier.align(Alignment.BottomStart).padding(8.dp)) {
                    Box(Modifier.size(56.dp, 8.dp).clip(RoundedCornerShape(4.dp)).background(Color.White))
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.size(40.dp, 5.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.8f)))
                }
            }
        } else {
            Box(Modifier.size(72.dp, 10.dp).clip(RoundedCornerShape(5.dp)).background(c.onSurface.copy(alpha = 0.6f)))
            Spacer(Modifier.height(2.dp))
        }
        Box(Modifier.size(48.dp, 6.dp).clip(RoundedCornerShape(3.dp)).background(c.onSurface.copy(alpha = 0.4f)))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(3) { PreviewPhoto(it + 3, Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(4.dp))) }
        }
    }
}

/** A day header with and without the city after the date. */
@Composable
private fun HeaderPlacesPreview(isChecked: Boolean) {
    val c = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(72.dp, 10.dp).clip(RoundedCornerShape(5.dp)).background(c.onSurface.copy(alpha = 0.7f)))
            if (isChecked) {
                Icon(Icons.Outlined.LocationOn, null, tint = c.primary, modifier = Modifier.size(14.dp))
                Box(Modifier.size(48.dp, 10.dp).clip(RoundedCornerShape(5.dp)).background(c.primary.copy(alpha = 0.6f)))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(4) { PreviewPhoto(it, Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(4.dp))) }
        }
    }
}

/** The plain sticky date header, against the floating pill that opens the months. */
@Composable
private fun FloatingDatePreview(isChecked: Boolean) {
    val c = MaterialTheme.colorScheme
    Box(modifier = Modifier.fillMaxWidth().height(88.dp)) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            repeat(4) { PreviewPhoto(it + 1, Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(4.dp))) }
        }
        if (isChecked) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 14.dp)
                    .clip(CircleShape)
                    .background(c.surfaceContainerHigh)
                    .padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(Modifier.size(56.dp, 8.dp).clip(RoundedCornerShape(4.dp)).background(c.onSurface.copy(alpha = 0.7f)))
                Icon(Icons.Outlined.ExpandMore, null, tint = c.onSurface, modifier = Modifier.size(16.dp))
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(c.surface, Color.Transparent)))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Box(Modifier.size(64.dp, 8.dp).clip(RoundedCornerShape(4.dp)).background(c.onSurface.copy(alpha = 0.7f)))
            }
        }
    }
}
