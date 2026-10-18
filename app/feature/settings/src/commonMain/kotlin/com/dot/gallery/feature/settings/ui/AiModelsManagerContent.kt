/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.Position
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.core.ml.ModelFileInfo
import com.dot.gallery.core.ml.ModelGroup
import com.dot.gallery.core.ml.ModelStatus
import com.dot.gallery.core.ml.SearchModel
import com.dot.gallery.core.presentation.components.ConfirmationSheet
import com.dot.gallery.core.presentation.components.ModalSheet
import com.dot.gallery.core.presentation.components.NavigationBackButton
import com.dot.gallery.core.presentation.components.SetupButton
import com.dot.gallery.core.ui.components.SettingsItem
import com.dot.gallery.core.ui.util.AppBottomSheetState
import com.dot.gallery.core.ui.util.rememberAppBottomSheetState
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.feature.settings.generated.resources.Res
import com.dot.gallery.feature.settings.generated.resources.ai_models_cancel_download
import com.dot.gallery.feature.settings.generated.resources.ai_models_cd_cancel
import com.dot.gallery.feature.settings.generated.resources.ai_models_cd_download
import com.dot.gallery.feature.settings.generated.resources.ai_models_cmp_dimensions
import com.dot.gallery.feature.settings.generated.resources.ai_models_cmp_index_speed
import com.dot.gallery.feature.settings.generated.resources.ai_models_cmp_languages
import com.dot.gallery.feature.settings.generated.resources.ai_models_cmp_languages_clip
import com.dot.gallery.feature.settings.generated.resources.ai_models_cmp_languages_eg2
import com.dot.gallery.feature.settings.generated.resources.ai_models_cmp_modal_clip
import com.dot.gallery.feature.settings.generated.resources.ai_models_cmp_modal_eg2
import com.dot.gallery.feature.settings.generated.resources.ai_models_cmp_modalities
import com.dot.gallery.feature.settings.generated.resources.ai_models_cmp_quality
import com.dot.gallery.feature.settings.generated.resources.ai_models_cmp_quality_clip
import com.dot.gallery.feature.settings.generated.resources.ai_models_cmp_quality_eg2
import com.dot.gallery.feature.settings.generated.resources.ai_models_cmp_size
import com.dot.gallery.feature.settings.generated.resources.ai_models_cmp_speed_clip
import com.dot.gallery.feature.settings.generated.resources.ai_models_cmp_speed_eg2
import com.dot.gallery.feature.settings.generated.resources.ai_models_compare_summary
import com.dot.gallery.feature.settings.generated.resources.ai_models_compare_title
import com.dot.gallery.feature.settings.generated.resources.ai_models_delete_confirm
import com.dot.gallery.feature.settings.generated.resources.ai_models_delete_confirm_active
import com.dot.gallery.feature.settings.generated.resources.ai_models_delete_confirm_bundled
import com.dot.gallery.feature.settings.generated.resources.ai_models_delete_model
import com.dot.gallery.feature.settings.generated.resources.ai_models_delete_title
import com.dot.gallery.feature.settings.generated.resources.ai_models_description
import com.dot.gallery.feature.settings.generated.resources.ai_models_download_size
import com.dot.gallery.feature.settings.generated.resources.ai_models_engine_active
import com.dot.gallery.feature.settings.generated.resources.ai_models_engine_clip_desc
import com.dot.gallery.feature.settings.generated.resources.ai_models_engine_clip_title
import com.dot.gallery.feature.settings.generated.resources.ai_models_engine_eg2_desc
import com.dot.gallery.feature.settings.generated.resources.ai_models_engine_eg2_title
import com.dot.gallery.feature.settings.generated.resources.ai_models_engine_use
import com.dot.gallery.feature.settings.generated.resources.ai_models_file_unverified
import com.dot.gallery.feature.settings.generated.resources.ai_models_file_verified
import com.dot.gallery.feature.settings.generated.resources.ai_models_files
import com.dot.gallery.feature.settings.generated.resources.ai_models_group_cutout_desc
import com.dot.gallery.feature.settings.generated.resources.ai_models_group_cutout_purpose
import com.dot.gallery.feature.settings.generated.resources.ai_models_group_cutout_title
import com.dot.gallery.feature.settings.generated.resources.ai_models_group_face_detect_desc
import com.dot.gallery.feature.settings.generated.resources.ai_models_group_face_detect_purpose
import com.dot.gallery.feature.settings.generated.resources.ai_models_group_face_detect_title
import com.dot.gallery.feature.settings.generated.resources.ai_models_group_face_recognition_desc
import com.dot.gallery.feature.settings.generated.resources.ai_models_group_face_recognition_purpose
import com.dot.gallery.feature.settings.generated.resources.ai_models_group_face_recognition_title
import com.dot.gallery.feature.settings.generated.resources.ai_models_install
import com.dot.gallery.feature.settings.generated.resources.ai_models_install_summary
import com.dot.gallery.feature.settings.generated.resources.ai_models_manager
import com.dot.gallery.feature.settings.generated.resources.ai_models_offline_installed_summary
import com.dot.gallery.feature.settings.generated.resources.ai_models_offline_unavailable_summary
import com.dot.gallery.feature.settings.generated.resources.ai_models_preparing_summary
import com.dot.gallery.feature.settings.generated.resources.ai_models_privacy_description
import com.dot.gallery.feature.settings.generated.resources.ai_models_section_other_title
import com.dot.gallery.feature.settings.generated.resources.ai_models_section_search_subtitle
import com.dot.gallery.feature.settings.generated.resources.ai_models_section_search_title
import com.dot.gallery.feature.settings.generated.resources.ai_models_source
import com.dot.gallery.feature.settings.generated.resources.ai_models_source_clip_url
import com.dot.gallery.feature.settings.generated.resources.ai_models_source_cutout_url
import com.dot.gallery.feature.settings.generated.resources.ai_models_source_eg2_url
import com.dot.gallery.feature.settings.generated.resources.ai_models_source_face_detect_url
import com.dot.gallery.feature.settings.generated.resources.ai_models_source_face_recognition_url
import com.dot.gallery.feature.settings.generated.resources.ai_models_status_in_use
import com.dot.gallery.feature.settings.generated.resources.ai_models_status_installed
import com.dot.gallery.feature.settings.generated.resources.ai_models_status_not_installed
import com.dot.gallery.feature.settings.generated.resources.ai_models_status_title
import com.dot.gallery.feature.settings.generated.resources.ai_models_switch_note

private val SEARCH_GROUPS = SearchModel.entries.map { it.group }.toSet()

private fun searchModelOf(group: ModelGroup): SearchModel? =
    SearchModel.entries.firstOrNull { it.group == group }

private fun positionOf(index: Int, count: Int): Position = when {
    count == 1 -> Position.Alone
    index == 0 -> Position.Top
    index == count - 1 -> Position.Bottom
    else -> Position.Middle
}

/**
 * AI models manager, built on the shared settings rows: one radio group choosing the
 * Smart Search engine (with a comparison sheet), one group for the face and cutout
 * models. Each row carries its status and a single trailing control; everything else
 * — source, file checksums, delete — lives in that model's detail sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiModelsManagerContent(
    state: AiModelsManagerState,
    actions: AiModelsManagerActions,
) {
    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val scope = rememberCoroutineScope()
    val detailSheet = rememberAppBottomSheetState()
    val compareSheet = rememberAppBottomSheetState()
    val deleteSheet = rememberAppBottomSheetState()
    var detailGroup by remember { mutableStateOf<ModelGroup?>(null) }
    var pendingDelete by remember { mutableStateOf<ModelGroup?>(null) }

    fun uiOf(group: ModelGroup) =
        state.groups.firstOrNull { it.group == group } ?: AiModelGroupUi(group = group)

    val openDetails: (ModelGroup) -> Unit = { group ->
        detailGroup = group
        scope.launch { detailSheet.show() }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(Res.string.ai_models_manager)) },
                navigationIcon = { NavigationBackButton() },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        val layoutDirection = LocalLayoutDirection.current
        val otherGroups = ModelGroup.entries.filter { it !in SEARCH_GROUPS }
        val compareTitle = stringResource(Res.string.ai_models_compare_title)
        val compareSummary = stringResource(Res.string.ai_models_compare_summary)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = padding.calculateStartPadding(layoutDirection),
                end = padding.calculateEndPadding(layoutDirection),
                top = 16.dp + padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 16.dp
            )
        ) {
            item(key = "intro") {
                SectionExplanation(
                    text = stringResource(Res.string.ai_models_description),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            item(key = "search_header") {
                SettingsItem(
                    item = SettingsEntity.Header(
                        title = stringResource(Res.string.ai_models_section_search_title)
                    )
                )
            }
            item(key = "search_explanation") {
                SectionExplanation(stringResource(Res.string.ai_models_section_search_subtitle))
            }
            // Engines + the comparison row form one group.
            val searchRowCount = SearchModel.entries.size + 1
            SearchModel.entries.forEachIndexed { index, model ->
                item(key = "engine_${model.name}") {
                    ModelRow(
                        ui = uiOf(model.group),
                        labels = modelLabels(model.group),
                        isActive = model == state.activeSearchModel,
                        isEngine = true,
                        isBundled = state.isBundled,
                        canInstallModels = state.canInstallModels,
                        position = positionOf(index, searchRowCount),
                        actions = actions,
                        onOpenDetails = { openDetails(model.group) },
                    )
                }
            }
            item(key = "engine_compare") {
                SettingsItem(
                    item = SettingsEntity.Preference(
                        title = compareTitle,
                        summary = compareSummary,
                        screenPosition = Position.Bottom,
                        onClick = { scope.launch { compareSheet.show() } }
                    )
                )
            }

            item(key = "other_header") {
                SettingsItem(
                    item = SettingsEntity.Header(
                        title = stringResource(Res.string.ai_models_section_other_title)
                    )
                )
            }
            otherGroups.forEachIndexed { index, group ->
                item(key = "group_${group.name}") {
                    ModelRow(
                        ui = uiOf(group),
                        labels = modelLabels(group),
                        isActive = false,
                        isEngine = false,
                        isBundled = state.isBundled,
                        canInstallModels = state.canInstallModels,
                        position = positionOf(index, otherGroups.size),
                        actions = actions,
                        onOpenDetails = { openDetails(group) },
                    )
                }
            }

            item(key = "privacy") {
                Text(
                    text = stringResource(Res.string.ai_models_privacy_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(top = 8.dp)
                )
            }
        }
    }

    detailGroup?.let { group ->
        val ui = uiOf(group)
        ModelDetailSheet(
            sheetState = detailSheet,
            ui = ui,
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
                pendingDelete = group
                scope.launch {
                    detailSheet.hide()
                    deleteSheet.show()
                }
            },
        )
    }

    CompareEnginesSheet(sheetState = compareSheet, activeModel = state.activeSearchModel)

    pendingDelete?.let { group ->
        ConfirmationSheet(
            state = deleteSheet,
            title = stringResource(Res.string.ai_models_delete_title, modelLabels(group).title),
            summary = stringResource(
                when {
                    searchModelOf(group) == state.activeSearchModel ->
                        Res.string.ai_models_delete_confirm_active
                    state.isBundled -> Res.string.ai_models_delete_confirm_bundled
                    else -> Res.string.ai_models_delete_confirm
                }
            ),
            confirmText = stringResource(Res.string.ai_models_delete_model),
            onConfirm = { actions.onDelete(group) }
        )
    }
}

// ==================== Rows ====================

/**
 * One model as a settings row: name, purpose and live status in the summary; a single
 * trailing control — the engine radio once installed, download, or cancel with progress.
 */
@Composable
private fun ModelRow(
    ui: AiModelGroupUi,
    labels: ModelLabels,
    isActive: Boolean,
    isEngine: Boolean,
    isBundled: Boolean,
    canInstallModels: Boolean,
    position: Position,
    actions: AiModelsManagerActions,
    onOpenDetails: () -> Unit,
) {
    val action = resolveModelManagementAction(ui.status, canInstallModels)
    val status = statusLine(ui, action, labels.approxBytes, isActive, isBundled)
    val model = searchModelOf(ui.group)
    // The status line carries the row's state, so it gets colour when it's news:
    // the engine in use, a running download, or a failure.
    val statusColor = when {
        ui.error != null && action == ModelManagementAction.DOWNLOAD -> MaterialTheme.colorScheme.error
        isActive || action == ModelManagementAction.CANCEL_DOWNLOAD -> MaterialTheme.colorScheme.primary
        else -> null
    }
    val summary = buildAnnotatedString {
        append(labels.purpose)
        append('\n')
        if (statusColor != null) {
            withStyle(SpanStyle(color = statusColor)) { append(status) }
        } else {
            append(status)
        }
    }

    SettingsItem(
        item = SettingsEntity.Preference(
            title = labels.title,
            summary = summary.text,
            summaryAnnotated = summary,
            screenPosition = position,
            onClick = onOpenDetails,
        ),
        customTrailingContent = when (action) {
            ModelManagementAction.DELETE, ModelManagementAction.INSTALLED_OFFLINE -> {
                if (isEngine && model != null) {
                    {
                        RadioButton(
                            selected = isActive,
                            onClick = { if (!isActive) actions.onSelectSearchModel(model) },
                        )
                    }
                } else {
                    {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
            ModelManagementAction.DOWNLOAD -> {
                {
                    FilledTonalIconButton(onClick = { actions.onDownload(ui.group) }) {
                        Icon(
                            imageVector = Icons.Outlined.Download,
                            contentDescription = stringResource(
                                Res.string.ai_models_cd_download, labels.title
                            )
                        )
                    }
                }
            }
            ModelManagementAction.CANCEL_DOWNLOAD -> {
                {
                    Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { (ui.progress / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier.size(44.dp),
                            strokeWidth = 3.dp,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        )
                        IconButton(onClick = { actions.onCancelDownload(ui.group) }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(
                                    Res.string.ai_models_cd_cancel, labels.title
                                )
                            )
                        }
                    }
                }
            }
            ModelManagementAction.COPYING -> {
                {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(12.dp).size(24.dp),
                        strokeWidth = 3.dp,
                    )
                }
            }
            ModelManagementAction.UNAVAILABLE_OFFLINE -> null
        },
    )
}

@Composable
private fun statusLine(
    ui: AiModelGroupUi,
    action: ModelManagementAction,
    approxBytes: Long,
    isActive: Boolean,
    isBundled: Boolean,
): String = when (action) {
    ModelManagementAction.DELETE, ModelManagementAction.INSTALLED_OFFLINE -> stringResource(
        if (isActive) Res.string.ai_models_status_in_use else Res.string.ai_models_status_installed,
        formatBytes(ui.installedSize)
    )
    ModelManagementAction.DOWNLOAD -> ui.error ?: if (isBundled) {
        stringResource(Res.string.ai_models_install_summary)
    } else {
        stringResource(Res.string.ai_models_status_not_installed, formatBytes(approxBytes))
    }
    ModelManagementAction.CANCEL_DOWNLOAD -> buildList {
        add("${ui.progress.toInt()}%")
        if (ui.info.totalBytes > 0) {
            add("${formatBytes(ui.info.downloadedBytes)} / ${formatBytes(ui.info.totalBytes)}")
        }
        if (ui.info.speed > 0) add("${formatBytes(ui.info.speed)}/s")
        if (ui.info.speed > 0 && ui.info.totalBytes > 0) {
            add(formatDuration((ui.info.totalBytes - ui.info.downloadedBytes) / ui.info.speed))
        }
    }.joinToString(" · ")
    ModelManagementAction.COPYING -> stringResource(Res.string.ai_models_preparing_summary)
    ModelManagementAction.UNAVAILABLE_OFFLINE -> listOfNotNull(
        ui.error,
        stringResource(Res.string.ai_models_offline_unavailable_summary)
    ).joinToString("\n")
}

@Composable
private fun SectionExplanation(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .widthIn(max = 600.dp)
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 8.dp),
    )
}

// ==================== Detail sheet ====================

/**
 * Everything about one model that isn't needed to pick or install it: live status, the
 * source link, per-file SHA-256 checks, and the destructive action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelDetailSheet(
    sheetState: AppBottomSheetState,
    ui: AiModelGroupUi,
    labels: ModelLabels,
    isActive: Boolean,
    isBundled: Boolean,
    canInstallModels: Boolean,
    actions: AiModelsManagerActions,
    onUseForSearch: (SearchModel) -> Unit,
    onDelete: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val openUrl: (String) -> Unit = actions.onOpenUrl ?: uriHandler::openUri
    val action = resolveModelManagementAction(ui.status, canInstallModels)
    val model = searchModelOf(ui.group)
    // SHA-256 over 100+ MB files is far too slow for the main thread — loadFileInfos runs
    // off-thread and the rows appear when it returns.
    val fileInfos by produceState(initialValue = emptyList<ModelFileInfo>(), ui.status, ui.group) {
        value = if (ui.status == ModelStatus.READY) actions.loadFileInfos(ui.group) else emptyList()
    }
    val statusSummary = buildString {
        append(statusLine(ui, action, labels.approxBytes, isActive, isBundled))
        if (action == ModelManagementAction.INSTALLED_OFFLINE) {
            append('\n')
            append(stringResource(Res.string.ai_models_offline_installed_summary))
        }
    }

    ModalSheet(
        sheetState = sheetState,
        title = labels.title,
        subtitle = labels.description,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        content = {
            Column(modifier = Modifier.fillMaxWidth()) {
                SettingsItem(
                    item = SettingsEntity.Preference(
                        title = stringResource(Res.string.ai_models_status_title),
                        summary = statusSummary,
                        screenPosition = Position.Top,
                    ),
                    applyPaddings = false,
                )
                SettingsItem(
                    item = SettingsEntity.Preference(
                        title = stringResource(Res.string.ai_models_source),
                        summary = labels.sourceLabel,
                        screenPosition = Position.Bottom,
                        onClick = { openUrl(labels.sourceUrl) },
                    ),
                    applyPaddings = false,
                )
                if (fileInfos.isNotEmpty()) {
                    SettingsItem(
                        item = SettingsEntity.Header(
                            title = stringResource(Res.string.ai_models_files)
                        ),
                        applyPaddings = false,
                    )
                    val verified = stringResource(Res.string.ai_models_file_verified)
                    val unverified = stringResource(Res.string.ai_models_file_unverified)
                    fileInfos.forEachIndexed { index, file ->
                        SettingsItem(
                            item = SettingsEntity.Preference(
                                title = file.name,
                                summary = "${formatBytes(file.size)} · " +
                                    "${if (file.verified) verified else unverified}\n${file.sha256}",
                                screenPosition = positionOf(index, fileInfos.size),
                            ),
                            applyPaddings = false,
                        )
                    }
                }
            }

            val buttonModifier = Modifier.fillMaxWidth()
            when (action) {
                ModelManagementAction.DELETE, ModelManagementAction.INSTALLED_OFFLINE -> {
                    if (model != null && !isActive) {
                        SheetButton(
                            text = stringResource(Res.string.ai_models_engine_use),
                            modifier = buttonModifier,
                            onClick = { onUseForSearch(model) },
                        )
                    }
                    if (action == ModelManagementAction.DELETE) {
                        SheetButton(
                            text = stringResource(Res.string.ai_models_delete_model),
                            modifier = buttonModifier,
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            onClick = onDelete,
                        )
                    }
                }
                ModelManagementAction.DOWNLOAD -> SheetButton(
                    text = if (isBundled) {
                        stringResource(Res.string.ai_models_install)
                    } else {
                        stringResource(
                            Res.string.ai_models_download_size,
                            formatBytes(labels.approxBytes)
                        )
                    },
                    modifier = buttonModifier,
                    onClick = { actions.onDownload(ui.group) },
                )
                ModelManagementAction.CANCEL_DOWNLOAD -> SheetButton(
                    text = stringResource(Res.string.ai_models_cancel_download),
                    modifier = buttonModifier,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    onClick = { actions.onCancelDownload(ui.group) },
                )
                ModelManagementAction.COPYING, ModelManagementAction.UNAVAILABLE_OFFLINE -> Unit
            }
        },
    )
}

@Composable
private fun SheetButton(
    text: String,
    modifier: Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    onClick: () -> Unit,
) {
    SetupButton(
        modifier = modifier,
        applyHorizontalPadding = false,
        applyBottomPadding = false,
        applyInsets = false,
        applyNavigationPadding = false,
        containerColor = containerColor,
        contentColor = contentColor,
        text = text,
        onClick = onClick,
    )
}

// ==================== Comparison sheet ====================

/** Side-by-side engine specs — capabilities and costs, not marketing. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompareEnginesSheet(
    sheetState: AppBottomSheetState,
    activeModel: SearchModel,
) {
    val rows = listOf(
        Triple(
            stringResource(Res.string.ai_models_cmp_modalities),
            stringResource(Res.string.ai_models_cmp_modal_clip),
            stringResource(Res.string.ai_models_cmp_modal_eg2),
        ),
        Triple(
            stringResource(Res.string.ai_models_cmp_quality),
            stringResource(Res.string.ai_models_cmp_quality_clip),
            stringResource(Res.string.ai_models_cmp_quality_eg2),
        ),
        Triple(
            stringResource(Res.string.ai_models_cmp_index_speed),
            stringResource(Res.string.ai_models_cmp_speed_clip),
            stringResource(Res.string.ai_models_cmp_speed_eg2),
        ),
        Triple(
            stringResource(Res.string.ai_models_cmp_languages),
            stringResource(Res.string.ai_models_cmp_languages_clip),
            stringResource(Res.string.ai_models_cmp_languages_eg2),
        ),
        Triple(
            stringResource(Res.string.ai_models_cmp_size),
            formatBytes(SearchModel.CLIP.approxSizeBytes),
            formatBytes(SearchModel.EMBEDDING_GEMMA_2.approxSizeBytes),
        ),
        Triple(
            stringResource(Res.string.ai_models_cmp_dimensions),
            "${SearchModel.CLIP.embeddingDimension}",
            "${SearchModel.EMBEDDING_GEMMA_2.embeddingDimension}",
        ),
    )
    val activeLabel = stringResource(Res.string.ai_models_engine_active)

    ModalSheet(
        sheetState = sheetState,
        title = stringResource(Res.string.ai_models_compare_title),
        subtitle = stringResource(Res.string.ai_models_switch_note),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                CompareRow(
                    label = "",
                    clip = stringResource(Res.string.ai_models_engine_clip_title),
                    eg2 = stringResource(Res.string.ai_models_engine_eg2_title),
                    clipNote = activeLabel.takeIf { activeModel == SearchModel.CLIP },
                    eg2Note = activeLabel.takeIf { activeModel == SearchModel.EMBEDDING_GEMMA_2 },
                    header = true,
                )
                rows.forEach { (label, clip, eg2) ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    CompareRow(label = label, clip = clip, eg2 = eg2)
                }
            }
        },
    )
}

@Composable
private fun CompareRow(
    label: String,
    clip: String,
    eg2: String,
    clipNote: String? = null,
    eg2Note: String? = null,
    header: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        listOf(clip to clipNote, eg2 to eg2Note).forEach { (value, note) ->
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = value,
                    style = if (header) MaterialTheme.typography.titleSmall
                    else MaterialTheme.typography.bodyMedium,
                    fontWeight = if (header) FontWeight.Bold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                note?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

// ==================== Labels ====================

private class ModelLabels(
    val title: String,
    /** One line saying what the model is for — shown on the row. */
    val purpose: String,
    /** The fuller description — shown at the top of the detail sheet. */
    val description: String,
    val sourceLabel: String,
    val sourceUrl: String,
    val approxBytes: Long,
)

@Composable
private fun modelLabels(group: ModelGroup): ModelLabels = when (group) {
    ModelGroup.SEARCH_CLIP -> {
        val tagline = stringResource(Res.string.ai_models_engine_clip_desc)
        ModelLabels(
            title = stringResource(Res.string.ai_models_engine_clip_title),
            purpose = tagline,
            description = tagline,
            sourceLabel = stringResource(Res.string.ai_models_source_clip_url),
            sourceUrl = "https://github.com/IacobIonut01/ReFra/tree/main/ml-models/src/main/assets",
            approxBytes = SearchModel.CLIP.approxSizeBytes,
        )
    }
    ModelGroup.SEARCH_EG2 -> {
        val tagline = stringResource(Res.string.ai_models_engine_eg2_desc)
        ModelLabels(
            title = stringResource(Res.string.ai_models_engine_eg2_title),
            purpose = tagline,
            description = tagline,
            sourceLabel = stringResource(Res.string.ai_models_source_eg2_url),
            sourceUrl = "https://huggingface.co/onnx-community/embeddinggemma-2-ONNX",
            approxBytes = SearchModel.EMBEDDING_GEMMA_2.approxSizeBytes,
        )
    }
    ModelGroup.CUTOUT -> ModelLabels(
        title = stringResource(Res.string.ai_models_group_cutout_title),
        purpose = stringResource(Res.string.ai_models_group_cutout_purpose),
        description = stringResource(Res.string.ai_models_group_cutout_desc),
        sourceLabel = stringResource(Res.string.ai_models_source_cutout_url),
        sourceUrl = "https://huggingface.co/Acly/MobileSAM",
        approxBytes = 44L * 1_048_576L,
    )
    ModelGroup.FACE_DETECT -> ModelLabels(
        title = stringResource(Res.string.ai_models_group_face_detect_title),
        purpose = stringResource(Res.string.ai_models_group_face_detect_purpose),
        description = stringResource(Res.string.ai_models_group_face_detect_desc),
        sourceLabel = stringResource(Res.string.ai_models_source_face_detect_url),
        sourceUrl = "https://github.com/onnx/models/tree/main/validated/vision/body_analysis/ultraface",
        approxBytes = 1_363_149L,
    )
    ModelGroup.FACE_RECOGNITION -> ModelLabels(
        title = stringResource(Res.string.ai_models_group_face_recognition_title),
        purpose = stringResource(Res.string.ai_models_group_face_recognition_purpose),
        description = stringResource(Res.string.ai_models_group_face_recognition_desc),
        sourceLabel = stringResource(Res.string.ai_models_source_face_recognition_url),
        sourceUrl = "https://huggingface.co/garavv/arcface-onnx",
        approxBytes = 130L * 1_048_576L,
    )
}

private fun formatDuration(seconds: Long): String = when {
    seconds < 60 -> "${seconds}s"
    seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
    else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
}
