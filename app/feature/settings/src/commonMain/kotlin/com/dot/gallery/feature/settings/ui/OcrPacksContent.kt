/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dot.gallery.core.Position
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.core.presentation.components.GalleryPageTopBarAction
import com.dot.gallery.core.presentation.components.ModalSheet
import com.dot.gallery.core.ui.components.BaseSettingsScreen
import com.dot.gallery.core.ui.components.SettingsItem
import com.dot.gallery.core.ui.util.rememberAppBottomSheetState
import com.dot.gallery.feature.settings.generated.resources.Res
import com.dot.gallery.feature.settings.generated.resources.ocr_add_model_files
import com.dot.gallery.feature.settings.generated.resources.ocr_cancel_download_cd
import com.dot.gallery.feature.settings.generated.resources.ocr_delete_pack_cd
import com.dot.gallery.feature.settings.generated.resources.ocr_done
import com.dot.gallery.feature.settings.generated.resources.ocr_download_cd
import com.dot.gallery.feature.settings.generated.resources.ocr_more_options
import com.dot.gallery.feature.settings.generated.resources.ocr_pack_downloading
import com.dot.gallery.feature.settings.generated.resources.ocr_pack_failed
import com.dot.gallery.feature.settings.generated.resources.ocr_pack_installed
import com.dot.gallery.feature.settings.generated.resources.ocr_pack_installed_with_finder
import com.dot.gallery.feature.settings.generated.resources.ocr_pack_linked
import com.dot.gallery.feature.settings.generated.resources.ocr_pack_list
import com.dot.gallery.feature.settings.generated.resources.ocr_pack_list_caption
import com.dot.gallery.feature.settings.generated.resources.ocr_pack_list_links
import com.dot.gallery.feature.settings.generated.resources.ocr_pack_needs_readding
import com.dot.gallery.feature.settings.generated.resources.ocr_pack_not_added
import com.dot.gallery.feature.settings.generated.resources.ocr_packs_intro
import com.dot.gallery.feature.settings.generated.resources.ocr_packs_intro_offline
import com.dot.gallery.feature.settings.generated.resources.ocr_packs_title
import com.dot.gallery.feature.settings.generated.resources.ocr_readd_cd
import com.dot.gallery.feature.settings.generated.resources.ocr_retry_cd
import com.dot.gallery.feature.settings.generated.resources.ocr_section_more
import com.dot.gallery.feature.settings.generated.resources.ocr_section_on_device
import com.dot.gallery.feature.settings.generated.resources.ocr_section_suggested
import com.dot.gallery.feature.settings.generated.resources.ocr_section_unavailable
import com.dot.gallery.feature.settings.generated.resources.ocr_suggested
import com.dot.gallery.feature.settings.generated.resources.ocr_text_finder
import com.dot.gallery.feature.settings.generated.resources.ocr_unavailable_summary
import com.dot.gallery.feature.settings.generated.resources.ocr_verify_added
import com.dot.gallery.feature.settings.generated.resources.ocr_verify_altered
import com.dot.gallery.feature.settings.generated.resources.ocr_verify_ready
import com.dot.gallery.feature.settings.generated.resources.ocr_verify_summary
import com.dot.gallery.feature.settings.generated.resources.ocr_verify_title
import com.dot.gallery.feature.settings.generated.resources.ocr_verify_unknown
import com.dot.gallery.ui.theme.Spacing
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private const val PACK_TAG = "ocr-pack:"

/**
 * Settings › Text recognition › Language packs. Packs are grouped into On this device, Suggested
 * for your languages and More languages; "Suggested" is only a hint. Online builds download from the
 * row and keep Add model files and Pack list in the overflow menu; builds without internet access
 * show those two as buttons instead. Picking files ends in the verification sheet.
 */
@Composable
fun OcrPacksContent(state: OcrPacksState, actions: OcrPacksActions) {
    val onDevice = state.packs.filter { it.status.onDevice }
    val suggested = state.packs.filter { !it.status.onDevice && it.suggested }
    val more = state.packs.filter { !it.status.onDevice && !it.suggested }
    val titles = state.packs.associate { it.id to ocrPackTitle(it.id, it.languages) }
    val languageLines = state.packs.associate { it.id to ocrPackLanguages(it) }
    val statusLines = state.packs.associate { it.id to statusLine(it.status) }
    val headers = listOf(
        stringResource(Res.string.ocr_section_on_device),
        stringResource(Res.string.ocr_section_suggested),
        stringResource(Res.string.ocr_section_more),
        stringResource(Res.string.ocr_section_unavailable),
    )
    val unavailableSummary = stringResource(Res.string.ocr_unavailable_summary)
    val errorColor = MaterialTheme.colorScheme.error

    val rows = remember(state, titles, languageLines, statusLines, headers, errorColor) {
        mutableStateListOf<SettingsEntity>().apply {
            listOf(onDevice, suggested, more).forEachIndexed { group, packs ->
                if (packs.isEmpty()) return@forEachIndexed
                add(SettingsEntity.Header(headers[group]))
                packs.forEachIndexed { index, pack ->
                    val status = statusLines.getValue(pack.id)
                    add(
                        SettingsEntity.Preference(
                            title = titles.getValue(pack.id),
                            summaryAnnotated = buildAnnotatedString {
                                append(languageLines.getValue(pack.id))
                                if (status.isNotEmpty()) {
                                    append('\n')
                                    val color = if (pack.status == OcrPackStatus.NeedsReadding) errorColor else Color.Unspecified
                                    withStyle(SpanStyle(fontSize = 12.sp, color = color)) { append(status) }
                                }
                            },
                            screenPosition = positionIn(index, packs.size),
                            tag = PACK_TAG + pack.id,
                        )
                    )
                }
            }
            if (state.unavailableLanguages.isNotEmpty()) {
                add(SettingsEntity.Header(headers[3]))
                add(
                    SettingsEntity.Preference(
                        icon = Icons.Outlined.Info,
                        title = state.unavailableLanguages.joinToString(", "),
                        summary = unavailableSummary,
                        screenPosition = Position.Alone,
                    )
                )
            }
        }
    }
    val byTag = state.packs.associateBy { PACK_TAG + it.id }

    BaseSettingsScreen(
        title = stringResource(Res.string.ocr_packs_title),
        settingsList = rows,
        topContent = { PacksIntro(state.downloadsAllowed, actions) },
        actions = if (state.downloadsAllowed) {
            { PacksOverflow(actions) }
        } else null,
        settingsBuilder = { item, _, modifier ->
            val pack = byTag[item.tag]
            if (pack == null) {
                SettingsItem(item = item, modifier = modifier)
            } else {
                SettingsItem(
                    item = item,
                    modifier = modifier,
                    titleBadge = if (pack.suggested && !pack.status.onDevice) {
                        { SuggestedBadge() }
                    } else null,
                    customTrailingContent = { PackControl(pack, item.title, state.downloadsAllowed, actions) },
                )
            }
        },
    )

    VerificationSheet(state.verification, titles, actions.onDismissVerification)
}

@Composable
private fun statusLine(status: OcrPackStatus): String = when (status) {
    is OcrPackStatus.NotAdded -> status.downloadBytes?.let(::ocrSizeLabel) ?: stringResource(Res.string.ocr_pack_not_added)
    is OcrPackStatus.Downloading -> stringResource(
        Res.string.ocr_pack_downloading,
        ocrSizeLabel(status.doneBytes),
        ocrSizeLabel(status.totalBytes),
    )
    is OcrPackStatus.Installed -> stringResource(
        if (status.includesFinder) Res.string.ocr_pack_installed_with_finder else Res.string.ocr_pack_installed,
        ocrSizeLabel(status.bytes),
    )
    OcrPackStatus.Linked -> stringResource(Res.string.ocr_pack_linked)
    OcrPackStatus.NeedsReadding -> stringResource(Res.string.ocr_pack_needs_readding)
    OcrPackStatus.Failed -> stringResource(Res.string.ocr_pack_failed)
}

@Composable
private fun PacksIntro(downloadsAllowed: Boolean, actions: OcrPacksActions) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        Text(
            text = stringResource(if (downloadsAllowed) Res.string.ocr_packs_intro else Res.string.ocr_packs_intro_offline),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!downloadsAllowed) {
            Spacer(Modifier.size(4.dp))
            Button(onClick = actions.onAddFiles, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Icon(Icons.Outlined.UploadFile, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(Res.string.ocr_add_model_files))
            }
            FilledTonalButton(onClick = actions.onOpenPackList, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(Res.string.ocr_pack_list_links))
            }
            Text(
                text = stringResource(Res.string.ocr_pack_list_caption),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PacksOverflow(actions: OcrPacksActions) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        GalleryPageTopBarAction(
            icon = Icons.Outlined.MoreVert,
            contentDescription = stringResource(Res.string.ocr_more_options),
            onClick = { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.ocr_add_model_files)) },
                leadingIcon = { Icon(Icons.Outlined.UploadFile, contentDescription = null) },
                onClick = { expanded = false; actions.onAddFiles() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.ocr_pack_list)) },
                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null) },
                onClick = { expanded = false; actions.onOpenPackList() },
            )
        }
    }
}

@Composable
private fun SuggestedBadge() {
    Text(
        text = stringResource(Res.string.ocr_suggested),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** The row's trailing control: delete, a round download or retry button, or the download ring. */
@Composable
private fun PackControl(pack: OcrPackRow, title: String, downloadsAllowed: Boolean, actions: OcrPacksActions) {
    when (val status = pack.status) {
        is OcrPackStatus.Installed, OcrPackStatus.Linked -> IconButton(onClick = { actions.onDelete(pack.id) }) {
            Icon(Icons.Outlined.Delete, contentDescription = stringResource(Res.string.ocr_delete_pack_cd, title))
        }
        OcrPackStatus.NeedsReadding -> RoundAction(Icons.Outlined.Refresh, stringResource(Res.string.ocr_readd_cd, title), actions.onAddFiles)
        OcrPackStatus.Failed -> if (downloadsAllowed) {
            RoundAction(Icons.Outlined.Refresh, stringResource(Res.string.ocr_retry_cd, title)) { actions.onDownload(pack.id) }
        }
        is OcrPackStatus.NotAdded -> if (downloadsAllowed) {
            RoundAction(Icons.Outlined.Download, stringResource(Res.string.ocr_download_cd, title)) { actions.onDownload(pack.id) }
        }
        is OcrPackStatus.Downloading -> {
            val description = stringResource(Res.string.ocr_cancel_download_cd, title)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button) { actions.onCancelDownload(pack.id) }
                    .semantics { contentDescription = description },
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    progress = { (status.doneBytes.toFloat() / status.totalBytes.coerceAtLeast(1)).coerceIn(0f, 1f) },
                    modifier = Modifier.size(36.dp),
                    strokeWidth = 4.dp,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
                Icon(Icons.Outlined.Stop, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun RoundAction(icon: ImageVector, description: String, onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Icon(icon, contentDescription = description)
    }
}

/** After Add model files: what each picked file turned out to be. */
@Composable
private fun VerificationSheet(verification: OcrVerification?, titles: Map<String, String>, onDismiss: () -> Unit) {
    val sheet = rememberAppBottomSheetState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(verification) { if (verification != null) sheet.show() else sheet.hide() }
    verification ?: return
    val added = verification.files.count { it.result == OcrVerifiedResult.ADDED }
    val ready = verification.readyPackIds.mapNotNull(titles::get)
    ModalSheet(
        sheetState = sheet,
        onDismissRequest = onDismiss,
        content = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
                Text(
                    pluralStringResource(Res.plurals.ocr_verify_title, verification.files.size, verification.files.size),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    listOfNotNull(
                        stringResource(Res.string.ocr_verify_summary, added, verification.files.size - added),
                        ready.takeIf { it.isNotEmpty() }?.let { stringResource(Res.string.ocr_verify_ready, it.joinToString(", ")) },
                    ).joinToString(" "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                verification.files.forEach { file ->
                    val packTitle = file.packId?.let(titles::get)
                    val detail = when (file.result) {
                        OcrVerifiedResult.ADDED -> stringResource(
                            Res.string.ocr_verify_added,
                            packTitle ?: stringResource(Res.string.ocr_text_finder),
                        )
                        OcrVerifiedResult.UNKNOWN -> stringResource(Res.string.ocr_verify_unknown)
                        OcrVerifiedResult.ALTERED -> stringResource(
                            Res.string.ocr_verify_altered,
                            packTitle ?: stringResource(Res.string.ocr_text_finder),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
                        val ok = file.result == OcrVerifiedResult.ADDED
                        Icon(
                            if (ok) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                            contentDescription = null,
                            tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                        Column {
                            Text(file.name, style = MaterialTheme.typography.titleMedium)
                            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Button(
                    onClick = { scope.launch { sheet.hide(); onDismiss() } },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) { Text(stringResource(Res.string.ocr_done)) }
            }
        },
    )
}

private fun positionIn(index: Int, count: Int): Position = when {
    count == 1 -> Position.Alone
    index == 0 -> Position.Top
    index == count - 1 -> Position.Bottom
    else -> Position.Middle
}
