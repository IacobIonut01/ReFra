/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.settings.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Balance
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.CompareArrows
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.Position
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.core.ml.FaceModel
import com.dot.gallery.core.ml.ModelGroup
import com.dot.gallery.core.ml.SearchModel
import com.dot.gallery.core.presentation.components.ConfirmationSheet
import com.dot.gallery.core.presentation.components.GalleryPageTopBar
import com.dot.gallery.core.ui.components.SettingsItem
import com.dot.gallery.core.ui.util.rememberAppBottomSheetState
import com.dot.gallery.feature.settings.generated.resources.Res
import com.dot.gallery.feature.settings.generated.resources.ai_models_cancel_download
import com.dot.gallery.feature.settings.generated.resources.ai_models_compare_summary
import com.dot.gallery.feature.settings.generated.resources.ai_models_compare_title
import com.dot.gallery.feature.settings.generated.resources.ai_models_delete_confirm
import com.dot.gallery.feature.settings.generated.resources.ai_models_delete_confirm_active
import com.dot.gallery.feature.settings.generated.resources.ai_models_delete_confirm_bundled
import com.dot.gallery.feature.settings.generated.resources.ai_models_delete_model
import com.dot.gallery.feature.settings.generated.resources.ai_models_delete_title
import com.dot.gallery.feature.settings.generated.resources.ai_models_offline_unavailable_summary
import com.dot.gallery.feature.settings.generated.resources.ai_models_preparing_summary
import com.dot.gallery.feature.settings.generated.resources.ai_models_status_in_use
import com.dot.gallery.feature.settings.generated.resources.cta_cutout
import com.dot.gallery.feature.settings.generated.resources.cta_faces
import com.dot.gallery.feature.settings.generated.resources.cta_install
import com.dot.gallery.feature.settings.generated.resources.cta_not_now
import com.dot.gallery.feature.settings.generated.resources.cta_remove
import com.dot.gallery.feature.settings.generated.resources.cta_remove_title
import com.dot.gallery.feature.settings.generated.resources.cta_scan_people
import com.dot.gallery.feature.settings.generated.resources.cta_search
import com.dot.gallery.feature.settings.generated.resources.face_tier_accurate_desc
import com.dot.gallery.feature.settings.generated.resources.face_tier_accurate_title
import com.dot.gallery.feature.settings.generated.resources.face_tier_download
import com.dot.gallery.feature.settings.generated.resources.face_tier_fast_desc
import com.dot.gallery.feature.settings.generated.resources.face_tier_fast_title
import com.dot.gallery.feature.settings.generated.resources.face_tier_selected_download
import com.dot.gallery.feature.settings.generated.resources.face_tier_standard_desc
import com.dot.gallery.feature.settings.generated.resources.face_tier_standard_title
import com.dot.gallery.feature.settings.generated.resources.feature_cutout_body
import com.dot.gallery.feature.settings.generated.resources.feature_cutout_headline
import com.dot.gallery.feature.settings.generated.resources.feature_faces_body
import com.dot.gallery.feature.settings.generated.resources.feature_faces_headline
import com.dot.gallery.feature.settings.generated.resources.feature_search_body
import com.dot.gallery.feature.settings.generated.resources.feature_search_headline
import com.dot.gallery.feature.settings.generated.resources.feature_status_coming_soon
import com.dot.gallery.feature.settings.generated.resources.feature_status_downloading
import com.dot.gallery.feature.settings.generated.resources.option_charging_summary
import com.dot.gallery.feature.settings.generated.resources.option_charging_title
import com.dot.gallery.feature.settings.generated.resources.option_hidden_summary
import com.dot.gallery.feature.settings.generated.resources.option_hidden_title
import com.dot.gallery.feature.settings.generated.resources.preview_components
import com.dot.gallery.feature.settings.generated.resources.preview_engine
import com.dot.gallery.feature.settings.generated.resources.preview_engine_desc
import com.dot.gallery.feature.settings.generated.resources.preview_offline
import com.dot.gallery.feature.settings.generated.resources.preview_on_device
import com.dot.gallery.feature.settings.generated.resources.preview_options
import com.dot.gallery.feature.settings.generated.resources.preview_quality
import com.dot.gallery.feature.settings.generated.resources.preview_quality_desc
import com.dot.gallery.feature.settings.generated.resources.preview_wifi_note
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.lazy.rememberLazyListState
import com.dot.gallery.core.presentation.components.rememberScrolledUnderTopBar
import com.dot.gallery.core.presentation.util.LocalHazeState
import dev.chrisbanes.haze.hazeSource

/**
 * Preview and management page of one Intelligence Hub feature: what it does and that it stays on
 * the device, the engine or quality choice, the models it downloads, its options, and one primary
 * action that follows the feature's state (download, cancel, scan, remove).
 */
@Composable
fun IntelligenceFeatureContent(
    feature: IntelligenceFeature,
    state: AiModelsManagerState,
    actions: AiModelsManagerActions,
) {
    val scope = rememberCoroutineScope()
    val detailSheet = rememberAppBottomSheetState()
    val compareSheet = rememberAppBottomSheetState()
    val removeSheet = rememberAppBottomSheetState()
    var detailGroup by remember { mutableStateOf<ModelGroup?>(null) }
    var pendingDelete by remember { mutableStateOf<List<ModelGroup>>(emptyList()) }
    val groups = state.groupsOf(feature)
    val summary = state.summaryOf(feature)

    fun uiOf(group: ModelGroup) = state.groups.firstOrNull { it.group == group } ?: AiModelGroupUi(group = group)
    val openDetails: (ModelGroup) -> Unit = { group ->
        detailGroup = group
        scope.launch { detailSheet.show() }
    }
    val askRemove: (List<ModelGroup>) -> Unit = { toRemove ->
        pendingDelete = toRemove
        scope.launch { removeSheet.show() }
    }

    val hazeState = LocalHazeState.current
    val listState = rememberLazyListState()
    Scaffold(
        topBar = {
            GalleryPageTopBar(
                title = feature.title(),
                hazeState = hazeState,
                scrolled = rememberScrolledUnderTopBar(listState),
            )
        }
    ) { padding ->
        val layoutDirection = LocalLayoutDirection.current
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
            item(key = "hero") { FeatureHero(feature) }
            item(key = "intro") { FeatureIntro(feature) }

            when (feature) {
                IntelligenceFeature.FACE_GROUPING -> {
                    item(key = "quality_label") {
                        HubSectionLabel(
                            title = stringResource(Res.string.preview_quality),
                            description = stringResource(Res.string.preview_quality_desc)
                        )
                    }
                    FaceModel.entries.forEachIndexed { index, model ->
                        item(key = "tier_${model.name}") {
                            FaceTierRow(
                                model = model,
                                state = state,
                                position = positionOf(index, FaceModel.entries.size),
                                onSelect = { actions.onSelectFaceModel(model) },
                            )
                        }
                    }
                }
                IntelligenceFeature.SMART_SEARCH -> {
                    item(key = "engine_label") {
                        HubSectionLabel(
                            title = stringResource(Res.string.preview_engine),
                            description = stringResource(Res.string.preview_engine_desc)
                        )
                    }
                    val rows = SearchModel.entries.size + 1
                    SearchModel.entries.forEachIndexed { index, model ->
                        item(key = "engine_${model.name}") {
                            ModelRow(
                                ui = uiOf(model.group),
                                labels = modelLabels(model.group),
                                isActive = model == state.activeSearchModel,
                                isEngine = true,
                                isBundled = state.isBundled,
                                canInstallModels = state.canInstallModels,
                                position = positionOf(index, rows),
                                actions = actions,
                                onOpenDetails = { openDetails(model.group) },
                            )
                        }
                    }
                    item(key = "engine_compare") {
                        SettingsItem(
                            item = SettingsEntity.Preference(
                                icon = Icons.Outlined.CompareArrows,
                                title = stringResource(Res.string.ai_models_compare_title),
                                summary = stringResource(Res.string.ai_models_compare_summary),
                                screenPosition = Position.Bottom,
                                onClick = { scope.launch { compareSheet.show() } }
                            ),
                            customTrailingContent = { Chevron() },
                        )
                    }
                }
                IntelligenceFeature.SUBJECT_CUTOUT -> Unit
            }

            if (feature != IntelligenceFeature.SMART_SEARCH) {
                item(key = "components_label") {
                    HubSectionLabel(
                        title = stringResource(
                            Res.string.preview_components,
                            feature.title(),
                            formatBytes(groups.sumOf(::approxBytesOf))
                        )
                    )
                }
                val showNote = summary.status == FeatureStatus.NOT_INSTALLED && !state.isBundled
                val rows = groups.size + if (showNote) 1 else 0
                groups.forEachIndexed { index, group ->
                    item(key = "component_${group.name}") {
                        ModelRow(
                            ui = uiOf(group),
                            labels = modelLabels(group),
                            isActive = false,
                            isEngine = false,
                            isBundled = state.isBundled,
                            canInstallModels = state.canInstallModels,
                            position = positionOf(index, rows),
                            actions = actions,
                            onOpenDetails = { openDetails(group) },
                        )
                    }
                }
                if (showNote) item(key = "wifi_note") { WifiNote() }
            }

            if (feature != IntelligenceFeature.SUBJECT_CUTOUT) {
                item(key = "options_label") { HubSectionLabel(title = stringResource(Res.string.preview_options)) }
                item(key = "option_charging") {
                    SettingsItem(
                        item = SettingsEntity.SwitchPreference(
                            icon = Icons.Outlined.BatteryChargingFull,
                            title = stringResource(Res.string.option_charging_title),
                            summary = stringResource(Res.string.option_charging_summary),
                            isChecked = state.scanOnlyWhileCharging,
                            onCheck = actions.onScanOnlyWhileChargingChange,
                            screenPosition = Position.Top,
                        )
                    )
                }
                item(key = "option_hidden") {
                    SettingsItem(
                        item = SettingsEntity.SwitchPreference(
                            icon = Icons.Outlined.VisibilityOff,
                            title = stringResource(Res.string.option_hidden_title),
                            summary = stringResource(Res.string.option_hidden_summary),
                            isChecked = state.skipHiddenAlbums,
                            onCheck = actions.onSkipHiddenAlbumsChange,
                            screenPosition = Position.Bottom,
                        )
                    )
                }
            }

            item(key = "actions") {
                FeatureActions(
                    feature = feature,
                    state = state,
                    summary = summary,
                    actions = actions,
                    onRemove = {
                        askRemove(
                            if (feature == IntelligenceFeature.SMART_SEARCH) listOf(state.activeSearchModel.group)
                            else groups
                        )
                    },
                )
            }
            item(key = "privacy") { HubPrivacyNote() }
        }
    }

    detailGroup?.let { group ->
        ModelDetailSheet(
            sheetState = detailSheet,
            ui = uiOf(group),
            labels = modelLabels(group),
            isActive = searchModelOf(group) == state.activeSearchModel,
            isBundled = state.isBundled,
            canInstallModels = state.canInstallModels,
            actions = actions,
            onUseForSearch = { model ->
                actions.onSelectSearchModel(model)
                scope.launch { detailSheet.hide() }
            },
            onDelete = {
                scope.launch { detailSheet.hide() }
                askRemove(listOf(group))
            },
        )
    }

    if (feature == IntelligenceFeature.SMART_SEARCH) {
        CompareEnginesSheet(sheetState = compareSheet, activeModel = state.activeSearchModel)
    }

    if (pendingDelete.isNotEmpty()) {
        val single = pendingDelete.singleOrNull()
        ConfirmationSheet(
            state = removeSheet,
            title = if (single != null) {
                stringResource(Res.string.ai_models_delete_title, modelLabels(single).title)
            } else {
                stringResource(Res.string.cta_remove_title, feature.title())
            },
            summary = stringResource(
                when {
                    pendingDelete.any { searchModelOf(it) == state.activeSearchModel } ->
                        Res.string.ai_models_delete_confirm_active
                    state.isBundled -> Res.string.ai_models_delete_confirm_bundled
                    else -> Res.string.ai_models_delete_confirm
                }
            ),
            confirmText = stringResource(Res.string.ai_models_delete_model),
            onConfirm = { pendingDelete.forEach(actions.onDelete) }
        )
    }
}

private fun IntelligenceFeature.heroIcon(): ImageVector = when (this) {
    IntelligenceFeature.FACE_GROUPING -> Icons.Outlined.Face
    IntelligenceFeature.SMART_SEARCH -> Icons.Outlined.ImageSearch
    IntelligenceFeature.SUBJECT_CUTOUT -> Icons.Outlined.ContentCut
}

@Composable
private fun FeatureHero(feature: IntelligenceFeature, modifier: Modifier = Modifier) =
    HubFeatureHero(icon = feature.heroIcon(), modifier = modifier)

/** A hub feature's glyph on an orbit, with the offline badge and the on-device note. */
@Composable
internal fun HubFeatureHero(icon: ImageVector, modifier: Modifier = Modifier) {
    val orbitColor = MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier = modifier
            .hubContentWidth()
            .height(224.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Canvas(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 10.dp)
                .size(190.dp)
        ) {
            drawCircle(
                color = orbitColor,
                radius = size.minDimension / 2f - 1.dp.toPx(),
                style = Stroke(
                    width = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 6.dp.toPx()))
                )
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 49.dp)
                .size(112.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .size(44.dp)
            )
        }
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 140.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = stringResource(Res.string.preview_offline),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
            Text(
                text = stringResource(Res.string.preview_on_device),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FeatureIntro(feature: IntelligenceFeature) {
    val (headline, body) = when (feature) {
        IntelligenceFeature.FACE_GROUPING -> Res.string.feature_faces_headline to Res.string.feature_faces_body
        IntelligenceFeature.SMART_SEARCH -> Res.string.feature_search_headline to Res.string.feature_search_body
        IntelligenceFeature.SUBJECT_CUTOUT -> Res.string.feature_cutout_headline to Res.string.feature_cutout_body
    }
    HubFeatureIntro(headline = stringResource(headline), body = stringResource(body))
}

/** Headline and explanation under a hub feature's hero. */
@Composable
internal fun HubFeatureIntro(headline: String, body: String) {
    Column(
        modifier = Modifier
            .hubContentWidth()
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = headline,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private class FaceTierLabels(val icon: ImageVector, val title: StringResource, val description: StringResource)

private fun FaceModel.labels(): FaceTierLabels = when (this) {
    FaceModel.STANDARD -> FaceTierLabels(
        Icons.Outlined.Balance, Res.string.face_tier_standard_title, Res.string.face_tier_standard_desc
    )
    FaceModel.FAST -> FaceTierLabels(
        Icons.Outlined.Speed, Res.string.face_tier_fast_title, Res.string.face_tier_fast_desc
    )
    FaceModel.ACCURATE -> FaceTierLabels(
        Icons.Outlined.HighQuality, Res.string.face_tier_accurate_title, Res.string.face_tier_accurate_desc
    )
}

@Composable
private fun FaceTierRow(
    model: FaceModel,
    state: AiModelsManagerState,
    position: Position,
    onSelect: () -> Unit,
) {
    val labels = model.labels()
    val selected = model == state.activeFaceModel
    val tierSummary = summarizeFeature(model.groups, state.groups, state.canInstallModels)
    val status = when {
        !model.isAvailable -> stringResource(Res.string.feature_status_coming_soon)
        tierSummary.status == FeatureStatus.READY && selected ->
            stringResource(Res.string.ai_models_status_in_use, formatBytes(tierSummary.installedBytes))
        tierSummary.status == FeatureStatus.DOWNLOADING ->
            stringResource(Res.string.feature_status_downloading, (tierSummary.progress * 100).toInt())
        selected -> stringResource(Res.string.face_tier_selected_download, formatBytes(tierSummary.missingBytes))
        else -> stringResource(Res.string.face_tier_download, formatBytes(model.approxSizeBytes))
    }
    val summary = buildAnnotatedString {
        append(stringResource(labels.description))
        append('\n')
        if (selected) withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append(status) }
        else append(status)
    }
    SettingsItem(
        item = SettingsEntity.Preference(
            icon = labels.icon,
            title = stringResource(labels.title),
            summary = summary.text,
            summaryAnnotated = summary,
            enabled = model.isAvailable,
            screenPosition = position,
            onClick = if (model.isAvailable) onSelect else null,
        ),
        backgroundColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainer,
        customTrailingContent = {
            RadioButton(
                selected = selected,
                enabled = model.isAvailable,
                onClick = if (model.isAvailable) onSelect else null,
            )
        },
    )
}

@Composable
private fun WifiNote() {
    Row(
        modifier = Modifier
            .hubContentWidth()
            .padding(top = 1.dp)
            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.Wifi,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = stringResource(Res.string.preview_wifi_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun Chevron() {
    Icon(
        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(12.dp)
    )
}

/** The one primary action for the feature's state, plus "Not now" when the page was offered. */
@Composable
private fun FeatureActions(
    feature: IntelligenceFeature,
    state: AiModelsManagerState,
    summary: FeatureSummary,
    actions: AiModelsManagerActions,
    onRemove: () -> Unit,
) {
    Column(
        modifier = Modifier
            .hubContentWidth()
            .padding(top = 28.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val wide = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
        when (summary.status) {
            FeatureStatus.NOT_INSTALLED -> Button(
                modifier = wide,
                onClick = { summary.missingGroups.forEach(actions.onDownload) },
            ) {
                Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    modifier = Modifier.padding(start = 8.dp),
                    text = if (state.isBundled) stringResource(Res.string.cta_install) else stringResource(
                        when (feature) {
                            IntelligenceFeature.FACE_GROUPING -> Res.string.cta_faces
                            IntelligenceFeature.SMART_SEARCH -> Res.string.cta_search
                            IntelligenceFeature.SUBJECT_CUTOUT -> Res.string.cta_cutout
                        },
                        formatBytes(summary.missingBytes)
                    ),
                    style = MaterialTheme.typography.labelLarge
                )
            }
            FeatureStatus.DOWNLOADING -> {
                Text(
                    text = stringResource(Res.string.feature_status_downloading, (summary.progress * 100).toInt()),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                LinearProgressIndicator(
                    progress = { summary.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(CircleShape),
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
                Button(
                    modifier = wide,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    ),
                    onClick = { state.groupsOf(feature).forEach(actions.onCancelDownload) },
                ) { Text(stringResource(Res.string.ai_models_cancel_download), style = MaterialTheme.typography.labelLarge) }
            }
            FeatureStatus.PREPARING -> Text(
                text = stringResource(Res.string.ai_models_preparing_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FeatureStatus.READY -> {
                if (feature == IntelligenceFeature.FACE_GROUPING) {
                    Button(modifier = wide, onClick = actions.onScanForPeople) {
                        Icon(Icons.Outlined.PersonSearch, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(
                            modifier = Modifier.padding(start = 8.dp),
                            text = stringResource(Res.string.cta_scan_people),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
                if (state.canInstallModels) {
                    Button(
                        modifier = wide,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                        onClick = onRemove,
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(
                            modifier = Modifier.padding(start = 8.dp),
                            text = stringResource(Res.string.cta_remove, formatBytes(summary.installedBytes)),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
            FeatureStatus.UNAVAILABLE -> Text(
                text = stringResource(Res.string.ai_models_offline_unavailable_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        actions.onNotNow?.let { notNow ->
            TextButton(modifier = Modifier.heightIn(min = 48.dp), onClick = notNow) {
                Text(stringResource(Res.string.cta_not_now), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
