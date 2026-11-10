/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.Position
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.core.ml.ModelStatus
import com.dot.gallery.core.presentation.components.GalleryPageTopBar
import com.dot.gallery.core.ui.components.SettingsItem
import com.dot.gallery.feature.settings.generated.resources.Res
import com.dot.gallery.feature.settings.generated.resources.ai_models_manager
import com.dot.gallery.feature.settings.generated.resources.feature_cutout_summary
import com.dot.gallery.feature.settings.generated.resources.feature_cutout_title
import com.dot.gallery.feature.settings.generated.resources.feature_faces_summary
import com.dot.gallery.feature.settings.generated.resources.feature_faces_title
import com.dot.gallery.feature.settings.generated.resources.feature_search_summary
import com.dot.gallery.feature.settings.generated.resources.feature_search_title
import com.dot.gallery.feature.settings.generated.resources.feature_status_bundled
import com.dot.gallery.feature.settings.generated.resources.feature_status_downloading
import com.dot.gallery.feature.settings.generated.resources.feature_status_not_installed
import com.dot.gallery.feature.settings.generated.resources.feature_status_preparing
import com.dot.gallery.feature.settings.generated.resources.feature_status_ready
import com.dot.gallery.feature.settings.generated.resources.feature_status_ready_engine
import com.dot.gallery.feature.settings.generated.resources.feature_status_unavailable
import com.dot.gallery.feature.settings.generated.resources.hub_features
import com.dot.gallery.feature.settings.generated.resources.hub_models_ready
import com.dot.gallery.feature.settings.generated.resources.hub_models_ready_label
import com.dot.gallery.feature.settings.generated.resources.hub_privacy
import com.dot.gallery.feature.settings.generated.resources.hub_storage_label
import com.dot.gallery.feature.settings.generated.resources.hub_summary_body
import com.dot.gallery.feature.settings.generated.resources.hub_summary_title
import com.dot.gallery.feature.settings.generated.resources.ocr_hub_summary
import com.dot.gallery.feature.settings.generated.resources.ocr_settings_title
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.lazy.rememberLazyListState
import com.dot.gallery.core.presentation.components.rememberScrolledUnderTopBar
import com.dot.gallery.core.presentation.util.LocalHazeState
import dev.chrisbanes.haze.hazeSource

/**
 * The Intelligence Hub: a summary of what is installed, then one row per feature. Each row opens
 * that feature's preview page ([IntelligenceFeatureContent]), which owns the downloads, the engine
 * or quality choice, the options and the per-model details.
 */
@Composable
fun AiModelsManagerContent(
    state: AiModelsManagerState,
    actions: AiModelsManagerActions,
    /** Text recognition's state for its row; null leaves the row out (previews). */
    textRecognition: TextRecognitionState? = null,
) {
    val hazeState = LocalHazeState.current
    val listState = rememberLazyListState()
    Scaffold(
        topBar = {
            GalleryPageTopBar(
                title = stringResource(Res.string.ai_models_manager),
                hazeState = hazeState,
                scrolled = rememberScrolledUnderTopBar(listState),
            )
        }
    ) { padding ->
        val layoutDirection = LocalLayoutDirection.current
        // Builds that can neither download nor bundle models still read text: language packs can be
        // added from files. There the hub lists text recognition alone, without the model summary.
        val features = if (state.canInstallModels) IntelligenceFeature.entries else emptyList()
        val rowCount = features.size + if (textRecognition != null) 1 else 0
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().hazeSource(hazeState),
            contentPadding = PaddingValues(
                start = padding.calculateStartPadding(layoutDirection),
                end = padding.calculateEndPadding(layoutDirection),
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp
            )
        ) {
            if (state.canInstallModels) {
                item(key = "summary") {
                    // Same gap a settings group leaves above the next header.
                    HubSummaryCard(state = state, modifier = Modifier.padding(bottom = 16.dp))
                }
            }
            item(key = "features_label") {
                HubSectionLabel(title = stringResource(Res.string.hub_features))
            }
            features.forEachIndexed { index, feature ->
                item(key = "feature_${feature.name}") {
                    FeatureRow(
                        feature = feature,
                        state = state,
                        position = positionOf(index, rowCount),
                        onClick = { actions.onOpenFeature(feature) },
                    )
                }
            }
            if (textRecognition != null) {
                item(key = "feature_text_recognition") {
                    TextRecognitionRow(
                        state = textRecognition,
                        position = positionOf(rowCount - 1, rowCount),
                        onClick = actions.onOpenTextRecognition,
                    )
                }
            }
            item(key = "privacy") { HubPrivacyNote() }
        }
    }
}

/**
 * Lines a custom block up with the [SettingsItem] rows around it: centred in the page and capped
 * at the same 600 dp visible width (632 dp minus the rows' 16 dp side padding). Without it the
 * blocks hug the start edge on unfolded and tablet widths while the rows sit in the middle.
 */
internal fun Modifier.hubContentWidth(): Modifier = this
    .fillMaxWidth()
    .wrapContentWidth(Alignment.CenterHorizontally)
    .widthIn(max = 632.dp)
    .fillMaxWidth()
    .padding(horizontal = 16.dp)

/** Installed model count and their size on disk, over the primary container. */
@Composable
private fun HubSummaryCard(state: AiModelsManagerState, modifier: Modifier = Modifier) {
    val ready = state.groups.filter { it.status == ModelStatus.READY }
    Column(
        modifier = modifier
            .hubContentWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(Res.string.hub_summary_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = stringResource(Res.string.hub_summary_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HubStat(
                value = stringResource(Res.string.hub_models_ready, ready.size, state.groups.size),
                label = stringResource(Res.string.hub_models_ready_label),
                modifier = Modifier.weight(1f)
            )
            HubStat(
                value = formatBytes(ready.sumOf { it.installedSize }),
                label = stringResource(Res.string.hub_storage_label),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
internal fun HubStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(text = value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Category title between settings groups: the shared [SettingsItem] header, with an optional
 * explanation lined up under the header text.
 */
@Composable
internal fun HubSectionLabel(title: String, modifier: Modifier = Modifier, description: String? = null) {
    Column(modifier = modifier.fillMaxWidth()) {
        SettingsItem(item = SettingsEntity.Header(title = title))
        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                // The header text sits 16 dp row padding + 16 dp inset in from the content edge.
                modifier = Modifier
                    .hubContentWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 12.dp)
            )
        }
    }
}

@Composable
internal fun HubPrivacyNote(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .hubContentWidth()
            .padding(start = 8.dp, end = 8.dp, top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.VerifiedUser,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(Res.string.hub_privacy),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

internal fun IntelligenceFeature.icon(): ImageVector = when (this) {
    IntelligenceFeature.FACE_GROUPING -> Icons.Outlined.Groups
    IntelligenceFeature.SMART_SEARCH -> Icons.Outlined.ImageSearch
    IntelligenceFeature.SUBJECT_CUTOUT -> Icons.Outlined.ContentCut
}

@Composable
internal fun IntelligenceFeature.title(): String = stringResource(
    when (this) {
        IntelligenceFeature.FACE_GROUPING -> Res.string.feature_faces_title
        IntelligenceFeature.SMART_SEARCH -> Res.string.feature_search_title
        IntelligenceFeature.SUBJECT_CUTOUT -> Res.string.feature_cutout_title
    }
)

/** One-line state of a feature: ready with its size, downloading, or what it costs to add. */
@Composable
internal fun featureStatusLine(feature: IntelligenceFeature, state: AiModelsManagerState): String {
    val summary = state.summaryOf(feature)
    return when (summary.status) {
        FeatureStatus.READY -> if (feature == IntelligenceFeature.SMART_SEARCH) {
            stringResource(
                Res.string.feature_status_ready_engine,
                modelLabels(state.activeSearchModel.group).title,
                formatBytes(summary.installedBytes)
            )
        } else {
            stringResource(Res.string.feature_status_ready, formatBytes(summary.installedBytes))
        }
        FeatureStatus.DOWNLOADING ->
            stringResource(Res.string.feature_status_downloading, (summary.progress * 100).toInt())
        FeatureStatus.PREPARING -> stringResource(Res.string.feature_status_preparing)
        FeatureStatus.NOT_INSTALLED -> if (state.isBundled) {
            stringResource(Res.string.feature_status_bundled)
        } else {
            stringResource(Res.string.feature_status_not_installed, formatBytes(summary.missingBytes))
        }
        FeatureStatus.UNAVAILABLE -> stringResource(Res.string.feature_status_unavailable)
    }
}

@Composable
private fun FeatureRow(
    feature: IntelligenceFeature,
    state: AiModelsManagerState,
    position: Position,
    onClick: () -> Unit,
) {
    val purpose = stringResource(
        when (feature) {
            IntelligenceFeature.FACE_GROUPING -> Res.string.feature_faces_summary
            IntelligenceFeature.SMART_SEARCH -> Res.string.feature_search_summary
            IntelligenceFeature.SUBJECT_CUTOUT -> Res.string.feature_cutout_summary
        }
    )
    val status = featureStatusLine(feature, state)
    val statusColor = when (state.summaryOf(feature).status) {
        FeatureStatus.DOWNLOADING, FeatureStatus.PREPARING -> MaterialTheme.colorScheme.primary
        else -> null
    }
    val summary = buildAnnotatedString {
        append(purpose)
        append('\n')
        if (statusColor != null) withStyle(SpanStyle(color = statusColor)) { append(status) } else append(status)
    }
    SettingsItem(
        item = SettingsEntity.Preference(
            icon = feature.icon(),
            title = feature.title(),
            summary = summary.text,
            summaryAnnotated = summary,
            screenPosition = position,
            onClick = onClick,
        ),
        customTrailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp)
            )
        },
    )
}

/** Text recognition's row: it runs on language packs rather than the hub's model groups. */
@Composable
private fun TextRecognitionRow(state: TextRecognitionState, position: Position, onClick: () -> Unit) {
    val summary = buildAnnotatedString {
        append(stringResource(Res.string.ocr_hub_summary))
        append('\n')
        append(textRecognitionStatusLine(state))
    }
    SettingsItem(
        item = SettingsEntity.Preference(
            icon = Icons.Outlined.TextFields,
            title = stringResource(Res.string.ocr_settings_title),
            summary = summary.text,
            summaryAnnotated = summary,
            screenPosition = position,
            onClick = onClick,
        ),
        customTrailingContent = { Chevron() },
    )
}
