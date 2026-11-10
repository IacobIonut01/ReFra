/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ManageSearch
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.Position
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.core.presentation.components.ConfirmationSheet
import com.dot.gallery.core.presentation.components.GalleryPageTopBar
import com.dot.gallery.core.presentation.components.ModalSheet
import com.dot.gallery.core.presentation.components.rememberScrolledUnderTopBar
import com.dot.gallery.core.presentation.util.LocalHazeState
import com.dot.gallery.core.ui.components.SettingsItem
import com.dot.gallery.core.ui.util.rememberAppBottomSheetState
import com.dot.gallery.domain.settings.OCR_MODE_AUTO
import com.dot.gallery.domain.settings.OCR_MODE_ON_REQUEST
import com.dot.gallery.feature.settings.generated.resources.Res
import com.dot.gallery.feature.settings.generated.resources.ocr_body
import com.dot.gallery.feature.settings.generated.resources.ocr_cta_choose_languages
import com.dot.gallery.feature.settings.generated.resources.ocr_cta_turn_off
import com.dot.gallery.feature.settings.generated.resources.ocr_cta_turn_on
import com.dot.gallery.feature.settings.generated.resources.ocr_delete
import com.dot.gallery.feature.settings.generated.resources.ocr_delete_index_confirm_body
import com.dot.gallery.feature.settings.generated.resources.ocr_delete_index_confirm_title
import com.dot.gallery.feature.settings.generated.resources.ocr_delete_index_summary
import com.dot.gallery.feature.settings.generated.resources.ocr_delete_index_title
import com.dot.gallery.feature.settings.generated.resources.ocr_headline
import com.dot.gallery.feature.settings.generated.resources.ocr_hub_status_needs_pack
import com.dot.gallery.feature.settings.generated.resources.ocr_hub_status_off
import com.dot.gallery.feature.settings.generated.resources.ocr_hub_status_on
import com.dot.gallery.feature.settings.generated.resources.ocr_index_summary
import com.dot.gallery.feature.settings.generated.resources.ocr_index_title
import com.dot.gallery.feature.settings.generated.resources.ocr_master_summary_no_pack
import com.dot.gallery.feature.settings.generated.resources.ocr_packs_summary_installed
import com.dot.gallery.feature.settings.generated.resources.ocr_packs_summary_none
import com.dot.gallery.feature.settings.generated.resources.ocr_packs_title
import com.dot.gallery.feature.settings.generated.resources.ocr_privacy_entry_summary
import com.dot.gallery.feature.settings.generated.resources.ocr_section_privacy
import com.dot.gallery.feature.settings.generated.resources.ocr_settings_title
import com.dot.gallery.feature.settings.generated.resources.ocr_stat_index
import com.dot.gallery.feature.settings.generated.resources.ocr_stat_packs
import com.dot.gallery.feature.settings.generated.resources.ocr_vault_summary
import com.dot.gallery.feature.settings.generated.resources.ocr_vault_title
import com.dot.gallery.feature.settings.generated.resources.ocr_when_auto
import com.dot.gallery.feature.settings.generated.resources.ocr_when_auto_short
import com.dot.gallery.feature.settings.generated.resources.ocr_when_auto_summary
import com.dot.gallery.feature.settings.generated.resources.ocr_when_on_request
import com.dot.gallery.feature.settings.generated.resources.ocr_when_on_request_short
import com.dot.gallery.feature.settings.generated.resources.ocr_when_on_request_summary
import com.dot.gallery.feature.settings.generated.resources.ocr_when_title
import com.dot.gallery.feature.settings.generated.resources.preview_options
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** Where text recognition stands, which decides the page's primary action and the hub row's status. */
enum class TextRecognitionStatus {
    /** No language pack is ready, so it cannot be turned on. */
    NEEDS_PACK,
    OFF,
    ON;

    companion object {
        fun of(state: TextRecognitionState): TextRecognitionStatus = when {
            !state.canEnable -> NEEDS_PACK
            state.enabled -> ON
            else -> OFF
        }
    }
}

/**
 * Intelligence Hub › Text recognition, laid out like the hub's other feature pages: what it does,
 * Options (language packs, when to read, finding photos by their text, and the Privacy and storage
 * sheet), then one primary action that follows the state: choose languages, turn on or turn off.
 */
@Composable
fun TextRecognitionContent(
    state: TextRecognitionState,
    actions: TextRecognitionActions,
    searchRoute: String? = null,
    searchSink: ((String, List<String>) -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val whenSheet = rememberAppBottomSheetState()
    val privacySheet = rememberAppBottomSheetState()
    val deleteSheet = rememberAppBottomSheetState()
    val status = TextRecognitionStatus.of(state)
    val on = status == TextRecognitionStatus.ON

    val title = stringResource(Res.string.ocr_settings_title)
    val packsTitle = stringResource(Res.string.ocr_packs_title)
    val whenTitle = stringResource(Res.string.ocr_when_title)
    val indexTitle = stringResource(Res.string.ocr_index_title)
    val privacyTitle = stringResource(Res.string.ocr_section_privacy)
    val deleteTitle = stringResource(Res.string.ocr_delete_index_title)
    val vaultTitle = stringResource(Res.string.ocr_vault_title)
    if (searchRoute != null && searchSink != null) {
        LaunchedEffect(searchRoute, title, packsTitle, whenTitle, indexTitle, privacyTitle, deleteTitle, vaultTitle) {
            searchSink(searchRoute, listOf(title, packsTitle, whenTitle, indexTitle, privacyTitle, vaultTitle, deleteTitle))
        }
    }

    val packNames = state.installedPackIds.map { ocrPackTitle(it) }
    val packsSummary = if (packNames.isEmpty()) stringResource(Res.string.ocr_packs_summary_none)
    else stringResource(Res.string.ocr_packs_summary_installed, packNames.size, packNames.joinToString(" · "))

    val hazeState = LocalHazeState.current
    val listState = rememberLazyListState()
    Scaffold(
        topBar = {
            GalleryPageTopBar(
                title = title,
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
            item(key = "hero") { HubFeatureHero(icon = Icons.Outlined.DocumentScanner) }
            item(key = "intro") {
                HubFeatureIntro(
                    headline = stringResource(Res.string.ocr_headline),
                    body = stringResource(Res.string.ocr_body),
                )
            }
            item(key = "options_label") { HubSectionLabel(title = stringResource(Res.string.preview_options)) }
            item(key = "packs") {
                SettingsItem(
                    item = SettingsEntity.Preference(
                        icon = Icons.Outlined.Translate,
                        title = packsTitle,
                        summary = packsSummary,
                        onClick = actions.onOpenPacks,
                        screenPosition = Position.Top,
                    ),
                    customTrailingContent = { Chevron() },
                )
            }
            item(key = "when") {
                SettingsItem(
                    item = SettingsEntity.Preference(
                        icon = Icons.Outlined.Schedule,
                        title = whenTitle,
                        summary = stringResource(
                            if (state.onRequest) Res.string.ocr_when_on_request_short else Res.string.ocr_when_auto_short
                        ),
                        enabled = on,
                        onClick = { scope.launch { whenSheet.show() } },
                        screenPosition = Position.Middle,
                    ),
                    customTrailingContent = { Chevron() },
                )
            }
            item(key = "index") {
                SettingsItem(
                    item = SettingsEntity.SwitchPreference(
                        icon = Icons.AutoMirrored.Outlined.ManageSearch,
                        title = indexTitle,
                        summary = stringResource(Res.string.ocr_index_summary),
                        enabled = on,
                        isChecked = state.indexLibrary,
                        onCheck = actions.onIndexLibraryChange,
                        screenPosition = Position.Middle,
                    )
                )
            }
            item(key = "privacy_storage") {
                SettingsItem(
                    item = SettingsEntity.Preference(
                        icon = Icons.Outlined.VerifiedUser,
                        title = privacyTitle,
                        summary = stringResource(Res.string.ocr_privacy_entry_summary),
                        onClick = { scope.launch { privacySheet.show() } },
                        screenPosition = Position.Bottom,
                    ),
                    customTrailingContent = { Chevron() },
                )
            }
            item(key = "actions") { TextRecognitionActionsBlock(status = status, actions = actions) }
            item(key = "privacy") { HubPrivacyNote() }
        }
    }

    ModalSheet(
        sheetState = whenSheet,
        title = whenTitle,
        content = {
            Column(Modifier.fillMaxWidth()) {
                listOf(
                    Triple(OCR_MODE_AUTO, Res.string.ocr_when_auto, Res.string.ocr_when_auto_summary),
                    Triple(OCR_MODE_ON_REQUEST, Res.string.ocr_when_on_request, Res.string.ocr_when_on_request_summary),
                ).forEachIndexed { index, (mode, rowTitle, summary) ->
                    val selected = (mode == OCR_MODE_ON_REQUEST) == state.onRequest
                    SettingsItem(
                        item = SettingsEntity.Preference(
                            title = stringResource(rowTitle),
                            summary = stringResource(summary),
                            onClick = {
                                actions.onModeChange(mode)
                                scope.launch { whenSheet.hide() }
                            },
                            screenPosition = if (index == 0) Position.Top else Position.Bottom,
                        ),
                        modifier = Modifier.semantics { this.selected = selected },
                        applyPaddings = false,
                        backgroundColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                    )
                }
            }
        },
    )

    ModalSheet(
        sheetState = privacySheet,
        title = privacyTitle,
        content = {
            PrivacyAndStorage(
                state = state,
                vaultTitle = vaultTitle,
                deleteTitle = deleteTitle,
                onDeleteIndex = {
                    scope.launch {
                        privacySheet.hide()
                        deleteSheet.show()
                    }
                },
            )
        },
    )

    ConfirmationSheet(
        state = deleteSheet,
        title = stringResource(Res.string.ocr_delete_index_confirm_title),
        summary = stringResource(Res.string.ocr_delete_index_confirm_body),
        confirmText = stringResource(Res.string.ocr_delete),
        onConfirm = actions.onDeleteIndex,
    )
}

/** Body of the Privacy and storage sheet: how vault photos are handled, what is stored, and deleting the index. */
@Composable
private fun PrivacyAndStorage(
    state: TextRecognitionState,
    vaultTitle: String,
    deleteTitle: String,
    onDeleteIndex: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SettingsItem(
            item = SettingsEntity.Preference(
                icon = Icons.Outlined.Lock,
                title = vaultTitle,
                summary = stringResource(Res.string.ocr_vault_summary),
                screenPosition = Position.Alone,
            ),
            applyPaddings = false,
            backgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HubStat(
                value = ocrSizeLabel(state.modelBytes),
                label = stringResource(Res.string.ocr_stat_packs),
                modifier = Modifier.weight(1f),
            )
            HubStat(
                value = ocrSizeLabel(state.indexBytes),
                label = stringResource(Res.string.ocr_stat_index, state.indexedPhotos),
                modifier = Modifier.weight(1f),
            )
        }
        Button(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
            enabled = state.indexedPhotos > 0,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
            onClick = onDeleteIndex,
        ) {
            Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                modifier = Modifier.padding(start = 8.dp),
                text = deleteTitle,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Text(
            text = stringResource(Res.string.ocr_delete_index_summary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

/** The page's one primary action, as the hub's other pages have: choose languages, turn on or turn off. */
@Composable
private fun TextRecognitionActionsBlock(status: TextRecognitionStatus, actions: TextRecognitionActions) {
    Column(
        modifier = Modifier
            .hubContentWidth()
            .padding(top = 28.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val wide = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
        when (status) {
            TextRecognitionStatus.NEEDS_PACK -> {
                Button(modifier = wide, onClick = actions.onOpenPacks) {
                    Icon(Icons.Outlined.Translate, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        modifier = Modifier.padding(start = 8.dp),
                        text = stringResource(Res.string.ocr_cta_choose_languages),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Text(
                    text = stringResource(Res.string.ocr_master_summary_no_pack),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextRecognitionStatus.OFF -> Button(modifier = wide, onClick = { actions.onEnabledChange(true) }) {
                Icon(Icons.Outlined.TextFields, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    modifier = Modifier.padding(start = 8.dp),
                    text = stringResource(Res.string.ocr_cta_turn_on),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            TextRecognitionStatus.ON -> Button(
                modifier = wide,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                ),
                onClick = { actions.onEnabledChange(false) },
            ) {
                Text(stringResource(Res.string.ocr_cta_turn_off), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** One-line state of text recognition for its Intelligence Hub row. */
@Composable
internal fun textRecognitionStatusLine(state: TextRecognitionState): String {
    val packs = state.installedPackIds.map { ocrPackTitle(it) }.joinToString(", ")
    return when (TextRecognitionStatus.of(state)) {
        TextRecognitionStatus.NEEDS_PACK -> stringResource(Res.string.ocr_hub_status_needs_pack)
        TextRecognitionStatus.OFF -> stringResource(Res.string.ocr_hub_status_off, packs)
        TextRecognitionStatus.ON -> stringResource(Res.string.ocr_hub_status_on, packs, ocrSizeLabel(state.modelBytes))
    }
}
