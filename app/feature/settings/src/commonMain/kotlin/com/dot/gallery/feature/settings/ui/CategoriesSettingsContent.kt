/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.VideocamOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.Position
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.core.ui.components.BaseSettingsScreen
import com.dot.gallery.core.ui.components.DestructivePill
import com.dot.gallery.core.ui.components.Footnote
import com.dot.gallery.core.ui.components.StatTile
import com.dot.gallery.core.ui.components.SummaryCard
import com.dot.gallery.core.ui.components.SummaryCardButton
import com.dot.gallery.core.ui.settings.rememberPreference
import com.dot.gallery.data.entity.SmartScanStatus
import com.dot.gallery.domain.date.formatEpochSec
import com.dot.gallery.core.logging.formatLogClock
import com.dot.gallery.domain.settings.SettingsKeys
import com.dot.gallery.feature.settings.generated.resources.Res
import com.dot.gallery.feature.settings.generated.resources.action_cancel
import com.dot.gallery.feature.settings.generated.resources.ai_models_not_available
import com.dot.gallery.feature.settings.generated.resources.categories
import com.dot.gallery.feature.settings.generated.resources.categories_count
import com.dot.gallery.feature.settings.generated.resources.categories_exclude_videos
import com.dot.gallery.feature.settings.generated.resources.categories_exclude_videos_purge_message
import com.dot.gallery.feature.settings.generated.resources.categories_exclude_videos_purge_title
import com.dot.gallery.feature.settings.generated.resources.categories_exclude_videos_summary
import com.dot.gallery.feature.settings.generated.resources.categories_footnote
import com.dot.gallery.feature.settings.generated.resources.categories_last_scan_on
import com.dot.gallery.feature.settings.generated.resources.categories_last_scan_today
import com.dot.gallery.feature.settings.generated.resources.categories_options_header
import com.dot.gallery.feature.settings.generated.resources.categories_stat_matched
import com.dot.gallery.feature.settings.generated.resources.categories_stat_unmatched
import com.dot.gallery.feature.settings.generated.resources.categories_stop_scan
import com.dot.gallery.feature.settings.generated.resources.categorise_your_media
import com.dot.gallery.feature.settings.generated.resources.categorise_your_media_summary
import com.dot.gallery.feature.settings.generated.resources.editor_remove
import com.dot.gallery.feature.settings.generated.resources.reset_categories
import com.dot.gallery.feature.settings.generated.resources.scan_for_new_categories
import com.dot.gallery.feature.settings.generated.resources.scanning_media
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Categories settings: a summary card (category count, last scan, photos matched / not matched
 * yet, Scan for new categories or Stop scan), the two options, the Reset categories pill and the
 * on-device classification note.
 */
@Composable
fun CategoriesSettingsContent(
    state: CategoriesSettingsState,
    actions: CategoriesSettingsActions = CategoriesSettingsActions(),
    listState: LazyListState = rememberLazyListState(),
) {
    var noClassification by rememberPreference(SettingsKeys.noClassification)
    var excludeVideos by rememberPreference(SettingsKeys.categoriesExcludeVideos)
    val scanning = state.categoryScan != null

    val optionsHeader = stringResource(Res.string.categories_options_header)
    val categoriseTitle = stringResource(Res.string.categorise_your_media)
    val categoriseSummary = stringResource(Res.string.categorise_your_media_summary)
    val excludeTitle = stringResource(Res.string.categories_exclude_videos)
    val excludeSummary = stringResource(Res.string.categories_exclude_videos_summary)
    val rows = remember(
        optionsHeader, categoriseTitle, categoriseSummary, excludeTitle, excludeSummary,
        noClassification, excludeVideos,
    ) {
        mutableStateListOf<SettingsEntity>(
            SettingsEntity.Header(optionsHeader),
            SettingsEntity.SwitchPreference(
                icon = Icons.Outlined.AutoAwesome,
                title = categoriseTitle,
                summary = categoriseSummary,
                isChecked = !noClassification,
                onCheck = { noClassification = !it },
                screenPosition = Position.Top,
            ),
            SettingsEntity.SwitchPreference(
                icon = Icons.Outlined.VideocamOff,
                title = excludeTitle,
                summary = excludeSummary,
                isChecked = excludeVideos,
                // Turning it on may first ask to remove existing video matches.
                onCheck = { checked ->
                    if (!checked) excludeVideos = false else actions.onRequestExcludeVideos()
                },
                screenPosition = Position.Bottom,
            ),
        )
    }

    BaseSettingsScreen(
        title = stringResource(Res.string.categories),
        listState = listState,
        topContent = { CategoriesSummaryCard(state, actions, scanning) },
        settingsList = rows,
        bottomContent = {
            Column {
                if (state.hasCategories && !scanning) {
                    DestructivePill(
                        label = stringResource(Res.string.reset_categories),
                        icon = Icons.Outlined.RestartAlt,
                        onClick = actions.onResetCategories,
                    )
                }
                Footnote(icon = Icons.Outlined.Info, text = stringResource(Res.string.categories_footnote))
            }
        },
    )

    state.videoPurgeCount?.let { count ->
        AlertDialog(
            onDismissRequest = actions.onDismissVideoPurge,
            title = { Text(stringResource(Res.string.categories_exclude_videos_purge_title)) },
            text = {
                Text(pluralStringResource(Res.plurals.categories_exclude_videos_purge_message, count, count))
            },
            confirmButton = {
                TextButton(
                    onClick = actions.onConfirmVideoPurge,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(Res.string.editor_remove))
                }
            },
            dismissButton = {
                TextButton(onClick = actions.onDismissVideoPurge) {
                    Text(stringResource(Res.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun CategoriesSummaryCard(
    state: CategoriesSettingsState,
    actions: CategoriesSettingsActions,
    scanning: Boolean,
) {
    val body = when {
        !state.modelsReady -> stringResource(Res.string.ai_models_not_available)
        scanning -> state.categoryScan?.let { run ->
            if (run.status == SmartScanStatus.RUNNING) run.currentPhase?.label() ?: run.status.label()
            else run.status.label()
        } ?: stringResource(Res.string.scanning_media)
        state.lastScanAt != null -> lastScanLabel(state.lastScanAt)
        else -> null
    }
    SummaryCard(
        icon = Icons.Outlined.Category,
        title = pluralStringResource(Res.plurals.categories_count, state.categoryCount, state.categoryCount),
        body = body,
        modifier = Modifier.padding(bottom = 20.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                value = state.matchedCount.toString(),
                label = stringResource(Res.string.categories_stat_matched),
                modifier = Modifier.weight(1f),
            )
            StatTile(
                value = state.unmatchedCount.toString(),
                label = stringResource(Res.string.categories_stat_unmatched),
                modifier = Modifier.weight(1f),
            )
        }
        if (scanning) {
            val progress = state.workerProgress
            val barModifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
            if (progress in 0f..<100f) {
                LinearProgressIndicator(
                    progress = { (progress / 100f).coerceIn(0f, 1f) },
                    modifier = barModifier,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
            } else {
                LinearProgressIndicator(modifier = barModifier, gapSize = 0.dp)
            }
            SummaryCardButton(
                label = stringResource(Res.string.categories_stop_scan),
                icon = Icons.Outlined.StopCircle,
                onClick = actions.onStopScan,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            SummaryCardButton(
                label = stringResource(Res.string.scan_for_new_categories),
                icon = Icons.Outlined.Refresh,
                filled = true,
                enabled = state.modelsReady,
                onClick = actions.onStartScan,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** "Last scan today, 09:42" or "Last scan Nov 7, 09:42". */
@Composable
private fun lastScanLabel(epochMillis: Long): String =
    if (isToday(epochMillis)) {
        stringResource(Res.string.categories_last_scan_today, formatLogClock(epochMillis))
    } else {
        stringResource(Res.string.categories_last_scan_on, (epochMillis / 1000).formatEpochSec("MMM d, HH:mm"))
    }
