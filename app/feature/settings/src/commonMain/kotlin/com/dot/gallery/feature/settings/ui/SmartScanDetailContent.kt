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
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.Position
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.core.logging.formatLogClock
import com.dot.gallery.core.smart.SmartScanPlan
import com.dot.gallery.core.ui.components.BaseSettingsScreen
import com.dot.gallery.core.ui.components.Footnote
import com.dot.gallery.core.ui.components.SettingsItem
import com.dot.gallery.core.ui.components.StatTile
import com.dot.gallery.core.ui.components.SummaryCard
import com.dot.gallery.core.ui.components.SummaryCardButton
import com.dot.gallery.core.ui.components.settingsContentWidth
import com.dot.gallery.data.entity.SmartScanPhase
import com.dot.gallery.data.entity.SmartScanPhaseEntity
import com.dot.gallery.data.entity.SmartScanRunEntity
import com.dot.gallery.data.entity.SmartScanStatus
import com.dot.gallery.domain.date.formatEpochSec
import com.dot.gallery.feature.settings.generated.resources.Res
import com.dot.gallery.feature.settings.generated.resources.ai_models_unavailable
import com.dot.gallery.feature.settings.generated.resources.smart_scan_card_body_complete
import com.dot.gallery.feature.settings.generated.resources.smart_scan_card_complete
import com.dot.gallery.feature.settings.generated.resources.smart_scan_card_none
import com.dot.gallery.feature.settings.generated.resources.smart_scan_card_none_body
import com.dot.gallery.feature.settings.generated.resources.smart_scan_card_status
import com.dot.gallery.feature.settings.generated.resources.smart_scan_cancel_scan
import com.dot.gallery.feature.settings.generated.resources.smart_scan_face_models_unavailable
import com.dot.gallery.feature.settings.generated.resources.smart_scan_footnote_idle
import com.dot.gallery.feature.settings.generated.resources.smart_scan_footnote_running
import com.dot.gallery.feature.settings.generated.resources.smart_scan_full_refresh
import com.dot.gallery.feature.settings.generated.resources.smart_scan_full_refresh_summary
import com.dot.gallery.feature.settings.generated.resources.smart_scan_manager_title
import com.dot.gallery.feature.settings.generated.resources.smart_scan_preparing_stage
import com.dot.gallery.feature.settings.generated.resources.smart_scan_actions_header
import com.dot.gallery.feature.settings.generated.resources.smart_scan_rebuild_header
import com.dot.gallery.feature.settings.generated.resources.smart_scan_refresh_all
import com.dot.gallery.feature.settings.generated.resources.smart_scan_refresh_description
import com.dot.gallery.feature.settings.generated.resources.smart_scan_retry_failed
import com.dot.gallery.feature.settings.generated.resources.smart_scan_row_categories
import com.dot.gallery.feature.settings.generated.resources.smart_scan_row_categories_summary
import com.dot.gallery.feature.settings.generated.resources.smart_scan_row_embeddings
import com.dot.gallery.feature.settings.generated.resources.smart_scan_row_embeddings_summary
import com.dot.gallery.feature.settings.generated.resources.smart_scan_row_metadata
import com.dot.gallery.feature.settings.generated.resources.smart_scan_row_metadata_summary
import com.dot.gallery.feature.settings.generated.resources.smart_scan_row_people
import com.dot.gallery.feature.settings.generated.resources.smart_scan_row_people_summary
import com.dot.gallery.feature.settings.generated.resources.smart_scan_scanning_title
import com.dot.gallery.feature.settings.generated.resources.smart_scan_stage_running
import com.dot.gallery.feature.settings.generated.resources.smart_scan_stages_header
import com.dot.gallery.feature.settings.generated.resources.smart_scan_stat_done
import com.dot.gallery.feature.settings.generated.resources.smart_scan_stat_failed
import com.dot.gallery.feature.settings.generated.resources.smart_scan_stat_processed
import com.dot.gallery.feature.settings.generated.resources.smart_scan_stat_skipped
import com.dot.gallery.feature.settings.generated.resources.smart_scan_when_today
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** Statuses after which the latest scan can be retried. */
private val RetryableStatuses = setOf(
    SmartScanStatus.PARTIAL,
    SmartScanStatus.BLOCKED,
    SmartScanStatus.FAILED,
    SmartScanStatus.INTERRUPTED,
)

/** Tag for refresh rows, which end in a refresh action icon instead of nothing. */
private const val RefreshRowTag = "smart-scan-refresh"

/**
 * Smart Features data hub. Idle: a summary card for the latest scan (processed / failed /
 * skipped, Refresh all, Retry failed), the per-feature refresh rows and Full refresh. While a
 * scan runs: a progress card with Cancel scan and the list of stages with their status.
 */
@Composable
fun SmartScanDetailContent(
    state: SmartScanDetailState,
    actions: SmartScanDetailActions = SmartScanDetailActions(),
    listState: LazyListState = rememberLazyListState(),
) {
    val running = state.activeScan != null
    val refreshHeader = stringResource(Res.string.smart_scan_actions_header)
    val refreshDescription = stringResource(Res.string.smart_scan_refresh_description)
    val rows = if (running) {
        stageRows(state.activePhases)
    } else {
        refreshRows(state, actions, refreshHeader)
    }
    BaseSettingsScreen(
        title = stringResource(Res.string.smart_scan_manager_title),
        listState = listState,
        topContent = {
            val active = state.activeScan
            if (active != null) {
                ScanProgressCard(active, state.activePhases, actions)
            } else {
                LatestScanCard(state.latestScan, actions)
            }
        },
        settingsList = remember(rows) { mutableStateListOf<SettingsEntity>().apply { addAll(rows) } },
        settingsBuilder = { item, _, modifier ->
            when {
                item.isHeader && item.title == refreshHeader -> Column(modifier) {
                    SettingsItem(item = item)
                    Text(
                        text = refreshDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .settingsContentWidth()
                            .padding(start = 8.dp, end = 8.dp, bottom = 12.dp),
                    )
                }
                item.tag == RefreshRowTag -> SettingsItem(
                    item = item,
                    modifier = modifier,
                    customTrailingContent = {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
                else -> SettingsItem(item = item, modifier = modifier)
            }
        },
        bottomContent = {
            Footnote(
                icon = Icons.Outlined.Info,
                text = stringResource(
                    if (running) Res.string.smart_scan_footnote_running else Res.string.smart_scan_footnote_idle
                ),
            )
        },
    )
}

@Composable
private fun refreshRows(
    state: SmartScanDetailState,
    actions: SmartScanDetailActions,
    refreshHeader: String,
): List<SettingsEntity> {
    fun row(
        icon: ImageVector,
        title: String,
        summary: String,
        position: Position,
        enabled: Boolean,
        onClick: () -> Unit,
    ) = SettingsEntity.Preference(
        icon = icon,
        title = title,
        summary = summary,
        enabled = enabled,
        onClick = onClick,
        screenPosition = position,
        tag = RefreshRowTag,
    )
    val modelsMissing = stringResource(Res.string.ai_models_unavailable)
    return listOf(
        SettingsEntity.Header(refreshHeader),
        row(
            Icons.Outlined.Info,
            stringResource(Res.string.smart_scan_row_metadata),
            stringResource(Res.string.smart_scan_row_metadata_summary),
            Position.Top, true, actions.onMetadata,
        ),
        row(
            Icons.Outlined.ImageSearch,
            stringResource(Res.string.smart_scan_row_embeddings),
            if (state.searchReady) stringResource(Res.string.smart_scan_row_embeddings_summary) else modelsMissing,
            Position.Middle, state.searchReady, actions.onEmbeddings,
        ),
        row(
            Icons.Outlined.Category,
            stringResource(Res.string.smart_scan_row_categories),
            if (state.searchReady) stringResource(Res.string.smart_scan_row_categories_summary) else modelsMissing,
            Position.Middle, state.searchReady, actions.onCategories,
        ),
        row(
            Icons.Outlined.Face,
            stringResource(Res.string.smart_scan_row_people),
            if (state.personsReady) stringResource(Res.string.smart_scan_row_people_summary)
            else stringResource(Res.string.smart_scan_face_models_unavailable),
            Position.Bottom, state.personsReady, actions.onPersons,
        ),
        SettingsEntity.Header(stringResource(Res.string.smart_scan_rebuild_header)),
        row(
            Icons.Outlined.RestartAlt,
            stringResource(Res.string.smart_scan_full_refresh),
            stringResource(Res.string.smart_scan_full_refresh_summary),
            Position.Alone, true, actions.onFullRefresh,
        ),
    )
}

@Composable
private fun stageRows(phases: List<SmartScanPhaseEntity>): List<SettingsEntity> {
    val primary = MaterialTheme.colorScheme.primary
    val rows = phases.mapIndexed { index, phase ->
        val status = when (phase.status) {
            SmartScanStatus.RUNNING -> stringResource(
                Res.string.smart_scan_stage_running,
                phase.progressPercent(),
            )
            else -> phase.status.label()
        }
        val emphasised = phase.status == SmartScanStatus.RUNNING || phase.status == SmartScanStatus.SUCCEEDED
        SettingsEntity.Preference(
            icon = phase.stageIcon(),
            title = phase.phase.label(),
            summary = status,
            summaryAnnotated = if (emphasised) {
                buildAnnotatedString { withStyle(SpanStyle(color = primary)) { append(status) } }
            } else null,
            screenPosition = when {
                phases.size == 1 -> Position.Alone
                index == 0 -> Position.Top
                index == phases.lastIndex -> Position.Bottom
                else -> Position.Middle
            },
        )
    }
    return listOf(SettingsEntity.Header(stringResource(Res.string.smart_scan_stages_header))) + rows
}

private fun SmartScanPhaseEntity.progressPercent(): Int =
    if (totalMedia > 0) (processedMedia * 100 / totalMedia).coerceIn(0, 100) else 0

private fun SmartScanPhaseEntity.stageIcon(): ImageVector = when (status) {
    SmartScanStatus.SUCCEEDED -> Icons.Outlined.CheckCircle
    SmartScanStatus.RUNNING -> Icons.Outlined.Autorenew
    SmartScanStatus.FAILED, SmartScanStatus.BLOCKED -> Icons.Outlined.ErrorOutline
    else -> phase.icon()
}

private fun SmartScanPhase.icon(): ImageVector = when (this) {
    SmartScanPhase.SOURCE_SYNC -> Icons.Outlined.Sync
    SmartScanPhase.METADATA -> Icons.Outlined.Info
    SmartScanPhase.SEARCH_INDEX -> Icons.Outlined.ImageSearch
    SmartScanPhase.CATEGORY_CLASSIFICATION -> Icons.Outlined.Category
    SmartScanPhase.FACE_INDEX -> Icons.Outlined.Face
    SmartScanPhase.FACE_CLUSTER -> Icons.Outlined.Groups
}

@Composable
private fun LatestScanCard(run: SmartScanRunEntity?, actions: SmartScanDetailActions) {
    if (run == null) {
        SummaryCard(
            icon = Icons.Outlined.Autorenew,
            title = stringResource(Res.string.smart_scan_card_none),
            body = stringResource(Res.string.smart_scan_card_none_body),
            modifier = Modifier.padding(bottom = 20.dp),
        ) {
            SummaryCardButton(
                label = stringResource(Res.string.smart_scan_refresh_all),
                icon = Icons.Outlined.Refresh,
                filled = true,
                onClick = actions.onAll,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        return
    }
    val succeeded = run.status == SmartScanStatus.SUCCEEDED
    val whenLabel = (run.finishedAt ?: run.updatedAt).let { scanWhenLabel(it) }
    SummaryCard(
        icon = if (succeeded) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
        title = if (succeeded) stringResource(Res.string.smart_scan_card_complete)
        else stringResource(Res.string.smart_scan_card_status, run.status.label()),
        body = if (succeeded) stringResource(Res.string.smart_scan_card_body_complete, whenLabel) else whenLabel,
        modifier = Modifier.padding(bottom = 20.dp),
    ) {
        ScanStats(
            first = run.processedMedia.toString() to stringResource(Res.string.smart_scan_stat_processed),
            failed = run.failedMedia,
            skipped = run.skippedMedia,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryCardButton(
                label = stringResource(Res.string.smart_scan_refresh_all),
                icon = Icons.Outlined.Refresh,
                filled = true,
                onClick = actions.onAll,
                modifier = Modifier.weight(1f),
            )
            if (run.status in RetryableStatuses) {
                SummaryCardButton(
                    label = stringResource(Res.string.smart_scan_retry_failed),
                    icon = Icons.Outlined.RestartAlt,
                    onClick = actions.onRetry,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ScanProgressCard(
    run: SmartScanRunEntity,
    phases: List<SmartScanPhaseEntity>,
    actions: SmartScanDetailActions,
) {
    val current = phases.firstOrNull { it.status == SmartScanStatus.RUNNING }
        ?: phases.firstOrNull { it.status == SmartScanStatus.QUEUED }
    val overallProgress = SmartScanPlan.overallProgress(phases)
    val stageNumber = phases.indexOf(current).takeIf { it >= 0 }?.plus(1) ?: 0
    val eta = current
        ?.takeIf { it.status == SmartScanStatus.RUNNING }
        ?.let { SmartScanPlan.estimatedRemainingMillis(it.totalMedia, it.processedMedia, it.startedAt) }
        ?.let { smartScanEtaLabel(it) }
    val body = listOfNotNull(
        current?.phase?.label() ?: stringResource(Res.string.smart_scan_preparing_stage),
        eta,
    ).joinToString(" · ")
    val total = run.totalMedia.takeIf { it > 0 } ?: current?.totalMedia ?: 0
    val processed = if (run.totalMedia > 0) run.processedMedia else current?.processedMedia ?: 0

    SummaryCard(
        icon = Icons.Outlined.Autorenew,
        title = stringResource(Res.string.smart_scan_scanning_title, stageNumber, phases.size),
        body = body,
        modifier = Modifier.padding(bottom = 20.dp),
    ) {
        ScanStats(
            first = processed.toString() to stringResource(Res.string.smart_scan_stat_done, total),
            failed = run.failedMedia,
            skipped = run.skippedMedia,
        )
        LinearProgressIndicator(
            progress = { overallProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
        SummaryCardButton(
            label = stringResource(Res.string.smart_scan_cancel_scan),
            icon = Icons.Outlined.StopCircle,
            onClick = actions.onCancel,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ScanStats(first: Pair<String, String>, failed: Int, skipped: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile(value = first.first, label = first.second, modifier = Modifier.weight(1.3f))
        StatTile(
            value = failed.toString(),
            label = stringResource(Res.string.smart_scan_stat_failed),
            modifier = Modifier.weight(1f),
        )
        StatTile(
            value = skipped.toString(),
            label = stringResource(Res.string.smart_scan_stat_skipped),
            modifier = Modifier.weight(1f),
        )
    }
}

/** "Today, 09:42" for today, otherwise "Nov 7, 09:42". */
@Composable
internal fun scanWhenLabel(epochMillis: Long): String {
    return if (isToday(epochMillis)) {
        stringResource(Res.string.smart_scan_when_today, formatLogClock(epochMillis))
    } else {
        (epochMillis / 1000).formatEpochSec("MMM d, HH:mm")
    }
}

/** True when [epochMillis] falls on the device's current calendar day. */
@OptIn(ExperimentalTime::class)
internal fun isToday(epochMillis: Long): Boolean {
    val day = "yyyyMMdd"
    return (epochMillis / 1000).formatEpochSec(day) ==
        Clock.System.now().epochSeconds.formatEpochSec(day)
}
