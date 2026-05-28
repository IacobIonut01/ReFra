/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.settings.subsettings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Gif
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dot.gallery.R
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.Position
import com.dot.gallery.core.Settings
import com.dot.gallery.core.Settings.Misc.rememberAllowGifAnimation
import com.dot.gallery.core.Settings.Misc.rememberFavoriteIconPosition
import com.dot.gallery.core.Settings.Misc.rememberShowFilterButton
import com.dot.gallery.core.Settings.Misc.rememberGroupBurstSequences
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
import com.dot.gallery.feature_node.presentation.util.restartApplication
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val DETAIL_GROUP_BY_MONTH = "group_by_month"
private const val DETAIL_TIMELINE_LAYOUT = "timeline_layout"
private const val DETAIL_GROUP_SIMILAR = "group_similar"
private const val DETAIL_GIF_ANIMATION = "gif_animation"
private const val DETAIL_FILTER_BUTTON = "filter_button"
private const val DETAIL_HIDE_TIMELINE = "hide_timeline"
private const val DETAIL_MERGE_ALBUMS = "merge_albums"
private const val DETAIL_FAV_ICON = "fav_icon"

@Composable
fun SettingsTimelineAlbumsScreen() {
    var detailKey by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val eventHandler = LocalEventHandler.current

    var groupByMonth by Settings.Misc.rememberTimelineGroupByMonth()
    var timelineLayoutType by rememberTimelineLayoutType()
    var groupSimilarMedia by rememberGroupSimilarMedia()
    var groupRawJpg by rememberGroupRawJpg()
    var groupEditedCopies by rememberGroupEditedCopies()
    var groupBurstSequences by rememberGroupBurstSequences()
    var allowGifAnimation by rememberAllowGifAnimation()
    var showFilterButton by rememberShowFilterButton()
    var hideTimelineOnAlbum by Settings.Album.rememberHideTimelineOnAlbum()
    var mergeAlbumsByName by Settings.Album.rememberMergeAlbumsByName()
    var favIconPosition by rememberFavoriteIconPosition()

    when (detailKey) {
        DETAIL_GROUP_BY_MONTH -> {
            BackHandler { detailKey = null }
            SwitchPreferenceDetailScreen(
                title = stringResource(R.string.monthly_timeline_title),
                isChecked = groupByMonth,
                onCheckedChange = {
                    scope.launch {
                        scope.async { groupByMonth = it }.await()
                        delay(50)
                        context.restartApplication()
                    }
                },
                description = stringResource(R.string.monthly_timeline_description),
                preview = { checked -> GroupByMonthPreview(checked) },
            )
        }
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
        else -> {
            TimelineAlbumsListScreen(
                groupByMonth = groupByMonth,
                onGroupByMonthChange = {
                    scope.launch {
                        scope.async { groupByMonth = it }.await()
                        delay(50)
                        context.restartApplication()
                    }
                },
                timelineLayoutType = timelineLayoutType,
                groupSimilarMedia = groupSimilarMedia,
                onGroupSimilarChange = { groupSimilarMedia = it },
                allowGifAnimation = allowGifAnimation,
                onGifAnimationChange = { allowGifAnimation = it },
                showFilterButton = showFilterButton,
                onShowFilterButtonChange = { showFilterButton = it },
                hideTimelineOnAlbum = hideTimelineOnAlbum,
                onHideTimelineChange = { hideTimelineOnAlbum = it },
                mergeAlbumsByName = mergeAlbumsByName,
                onMergeAlbumsChange = { mergeAlbumsByName = it },
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
    groupByMonth: Boolean,
    onGroupByMonthChange: (Boolean) -> Unit,
    timelineLayoutType: String,
    groupSimilarMedia: Boolean,
    onGroupSimilarChange: (Boolean) -> Unit,
    allowGifAnimation: Boolean,
    onGifAnimationChange: (Boolean) -> Unit,
    showFilterButton: Boolean,
    onShowFilterButtonChange: (Boolean) -> Unit,
    hideTimelineOnAlbum: Boolean,
    onHideTimelineChange: (Boolean) -> Unit,
    mergeAlbumsByName: Boolean,
    onMergeAlbumsChange: (Boolean) -> Unit,
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

        val groupByMonthPref = rememberSwitchPreference(
            groupByMonth,
            title = stringResource(R.string.monthly_timeline_title),
            summary = stringResource(R.string.monthly_timeline_summary),
            isChecked = groupByMonth,
            onCheck = onGroupByMonthChange,
            onClick = { onDetailClick(DETAIL_GROUP_BY_MONTH) },
            screenPosition = Position.Top
        )

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
            screenPosition = Position.Middle
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
            screenPosition = if (SdkCompat.supportsFavorites) Position.Middle else Position.Bottom
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
        val favIconPositionPref = rememberPreference(
            favIconPosition,
            title = stringResource(R.string.favorite_icon_on_thumbnails),
            summary = favIconPositionLabel,
            onClick = { onDetailClick(DETAIL_FAV_ICON) },
            screenPosition = Position.Bottom
        )

        return remember(
            groupByMonthPref, timelineLayoutPref, groupSimilarMediaPref,
            allowGifAnimationPref, dateHeaderPref, showFilterButtonPref,
            storyCardsPref,
            hideTimelineOnAlbumPref, mergeAlbumsByNamePref, favIconPositionPref
        ) {
            mutableStateListOf<SettingsEntity>().apply {
                add(timelineHeader)
                add(groupByMonthPref)
                add(timelineLayoutPref)
                add(groupSimilarMediaPref)
                add(allowGifAnimationPref)
                add(dateHeaderPref)
                add(showFilterButtonPref)
                add(storyCardsPref)

                add(albumsHeader)
                add(hideTimelineOnAlbumPref)
                add(mergeAlbumsByNamePref)
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
