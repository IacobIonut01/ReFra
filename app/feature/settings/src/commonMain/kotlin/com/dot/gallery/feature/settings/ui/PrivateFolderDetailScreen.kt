/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.FolderSpecial
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhotoAlbum
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.Position
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.core.presentation.components.ExpressiveSheet
import com.dot.gallery.core.presentation.components.SheetDismissConfirmActions
import com.dot.gallery.core.presentation.components.SheetButtonStyle
import com.dot.gallery.core.presentation.components.SheetHeader
import com.dot.gallery.core.presentation.components.SheetOptionRow
import com.dot.gallery.core.presentation.components.SheetRowTrailing
import com.dot.gallery.core.presentation.components.SheetTone
import com.dot.gallery.core.ui.components.BaseSettingsScreen
import com.dot.gallery.core.ui.components.DestructivePill
import com.dot.gallery.core.ui.components.Footnote
import com.dot.gallery.core.ui.components.StatTile
import com.dot.gallery.core.ui.components.SummaryCard
import com.dot.gallery.core.ui.components.SummaryCardButton
import com.dot.gallery.core.ui.util.rememberAppBottomSheetState
import com.dot.gallery.domain.uri.decodeUri
import com.dot.gallery.feature.settings.generated.resources.Res
import com.dot.gallery.feature.settings.generated.resources.action_cancel
import com.dot.gallery.feature.settings.generated.resources.private_folder_body_configured
import com.dot.gallery.feature.settings.generated.resources.private_folder_body_none
import com.dot.gallery.feature.settings.generated.resources.private_folder_change
import com.dot.gallery.feature.settings.generated.resources.private_folder_footnote_configured
import com.dot.gallery.feature.settings.generated.resources.private_folder_footnote_none
import com.dot.gallery.feature.settings.generated.resources.private_folder_header_help
import com.dot.gallery.feature.settings.generated.resources.private_folder_header_private
import com.dot.gallery.feature.settings.generated.resources.private_folder_header_what_you_get
import com.dot.gallery.feature.settings.generated.resources.private_folder_how_choosing
import com.dot.gallery.feature.settings.generated.resources.private_folder_how_choosing_body
import com.dot.gallery.feature.settings.generated.resources.private_folder_how_find
import com.dot.gallery.feature.settings.generated.resources.private_folder_how_find_body
import com.dot.gallery.feature.settings.generated.resources.private_folder_how_it_works
import com.dot.gallery.feature.settings.generated.resources.private_folder_how_it_works_summary
import com.dot.gallery.feature.settings.generated.resources.private_folder_how_nomedia
import com.dot.gallery.feature.settings.generated.resources.private_folder_how_nomedia_body
import com.dot.gallery.feature.settings.generated.resources.private_folder_how_outside
import com.dot.gallery.feature.settings.generated.resources.private_folder_how_outside_body
import com.dot.gallery.feature.settings.generated.resources.private_folder_how_what
import com.dot.gallery.feature.settings.generated.resources.private_folder_how_what_body
import com.dot.gallery.feature.settings.generated.resources.private_folder_open
import com.dot.gallery.feature.settings.generated.resources.private_folder_remove
import com.dot.gallery.feature.settings.generated.resources.private_folder_remove_action
import com.dot.gallery.feature.settings.generated.resources.private_folder_remove_body
import com.dot.gallery.feature.settings.generated.resources.private_folder_remove_keep_hidden
import com.dot.gallery.feature.settings.generated.resources.private_folder_remove_keep_hidden_summary
import com.dot.gallery.feature.settings.generated.resources.private_folder_remove_make_visible
import com.dot.gallery.feature.settings.generated.resources.private_folder_remove_make_visible_summary
import com.dot.gallery.feature.settings.generated.resources.private_folder_remove_title
import com.dot.gallery.feature.settings.generated.resources.private_folder_row_albums
import com.dot.gallery.feature.settings.generated.resources.private_folder_row_albums_summary
import com.dot.gallery.feature.settings.generated.resources.private_folder_row_any_storage
import com.dot.gallery.feature.settings.generated.resources.private_folder_row_any_storage_summary
import com.dot.gallery.feature.settings.generated.resources.private_folder_row_hidden
import com.dot.gallery.feature.settings.generated.resources.private_folder_row_hidden_summary
import com.dot.gallery.feature.settings.generated.resources.private_folder_row_library
import com.dot.gallery.feature.settings.generated.resources.private_folder_row_library_summary
import com.dot.gallery.feature.settings.generated.resources.private_folder_row_locked
import com.dot.gallery.feature.settings.generated.resources.private_folder_row_locked_summary
import com.dot.gallery.feature.settings.generated.resources.private_folder_row_stay_put
import com.dot.gallery.feature.settings.generated.resources.private_folder_row_stay_put_summary
import com.dot.gallery.feature.settings.generated.resources.private_folder_stat_external
import com.dot.gallery.feature.settings.generated.resources.private_folder_stat_internal
import com.dot.gallery.feature.settings.generated.resources.private_folder_stat_items
import com.dot.gallery.feature.settings.generated.resources.security_private_folder
import com.dot.gallery.feature.settings.generated.resources.security_private_folder_pick
import com.dot.gallery.feature.settings.generated.resources.security_private_folder_status_none
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** What the private folder holds, as far as the platform shell has scanned it. */
@Immutable
data class PrivateFolderStats(
    val itemCount: Int,
    val totalBytes: Long,
    val scanning: Boolean = false,
)

/** The folder path a tree URI points at, such as `Pictures/Private`. */
fun privateFolderDisplayPath(treeUri: String): String = runCatching {
    val decoded = decodeUri(treeUri)
    decoded.substringAfterLast("/tree/", decoded).substringAfter(":", decoded)
}.getOrDefault(treeUri)

/** True when the tree URI lives on the device's primary (internal) storage volume. */
fun isPrivateFolderOnInternalStorage(treeUri: String): Boolean = runCatching {
    val decoded = decodeUri(treeUri)
    decoded.substringAfterLast("/tree/", "").substringBefore(":") == "primary"
}.getOrDefault(true)

/**
 * Private folder settings: a summary card (folder, stats, Open / Change, or Pick folder when
 * none is set), short info rows, a "How it works" page, and removal through a sheet that keeps
 * the files hidden or makes them visible.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PrivateFolderDetailScreen(
    currentUri: String,
    onPickFolder: () -> Unit,
    onClearFolder: () -> Unit,
    onClearFolderAndReveal: () -> Unit,
    stats: PrivateFolderStats? = null,
    onOpenFolder: (() -> Unit)? = null,
    initialPage: PrivateFolderPage = PrivateFolderPage.Main,
) {
    var page by rememberSaveable { mutableStateOf(initialPage) }
    when (page) {
        PrivateFolderPage.HowItWorks -> {
            BackHandler { page = PrivateFolderPage.Main }
            PrivateFolderHowItWorks(onBack = { page = PrivateFolderPage.Main })
        }
        PrivateFolderPage.Main -> PrivateFolderMain(
            currentUri = currentUri,
            stats = stats,
            onPickFolder = onPickFolder,
            onOpenFolder = onOpenFolder,
            onClearFolder = onClearFolder,
            onClearFolderAndReveal = onClearFolderAndReveal,
            onHowItWorks = { page = PrivateFolderPage.HowItWorks },
        )
    }
}

enum class PrivateFolderPage { Main, HowItWorks }

@Composable
private fun PrivateFolderMain(
    currentUri: String,
    stats: PrivateFolderStats?,
    onPickFolder: () -> Unit,
    onOpenFolder: (() -> Unit)?,
    onClearFolder: () -> Unit,
    onClearFolderAndReveal: () -> Unit,
    onHowItWorks: () -> Unit,
) {
    val isConfigured = currentUri.isNotEmpty()
    val displayPath = remember(currentUri) { privateFolderDisplayPath(currentUri) }
    val internal = remember(currentUri) { isPrivateFolderOnInternalStorage(currentUri) }
    val removeSheet = rememberAppBottomSheetState()
    val scope = rememberCoroutineScope()

    BaseSettingsScreen(
        title = stringResource(Res.string.security_private_folder),
        topContent = {
            SummaryCard(
                icon = if (isConfigured) Icons.Outlined.FolderSpecial else Icons.Outlined.FolderOpen,
                title = if (isConfigured) displayPath else stringResource(Res.string.security_private_folder_status_none),
                body = stringResource(
                    if (isConfigured) Res.string.private_folder_body_configured else Res.string.private_folder_body_none
                ),
                // About 24 dp to the first section label, together with its own top padding.
                modifier = Modifier.padding(bottom = 20.dp),
            ) {
                if (isConfigured) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile(
                            value = stats?.takeUnless { it.scanning && it.itemCount == 0 }
                                ?.itemCount?.toString() ?: "…",
                            label = stringResource(Res.string.private_folder_stat_items),
                            modifier = Modifier.weight(1f),
                        )
                        StatTile(
                            value = stats?.takeUnless { it.scanning && it.itemCount == 0 }
                                ?.let { formatBytes(it.totalBytes) } ?: "…",
                            label = stringResource(
                                if (internal) Res.string.private_folder_stat_internal
                                else Res.string.private_folder_stat_external
                            ),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (onOpenFolder != null) {
                            SummaryCardButton(
                                label = stringResource(Res.string.private_folder_open),
                                icon = Icons.Outlined.FolderOpen,
                                filled = true,
                                onClick = onOpenFolder,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        SummaryCardButton(
                            label = stringResource(Res.string.private_folder_change),
                            icon = Icons.Outlined.Edit,
                            onClick = onPickFolder,
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else {
                    SummaryCardButton(
                        label = stringResource(Res.string.security_private_folder_pick),
                        icon = Icons.Outlined.FolderOpen,
                        filled = true,
                        onClick = onPickFolder,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        settingsList = rememberPrivateFolderRows(isConfigured, onHowItWorks),
        bottomContent = {
            Column {
                if (isConfigured) {
                    DestructivePill(
                        label = stringResource(Res.string.private_folder_remove),
                        icon = Icons.Outlined.Delete,
                        onClick = { scope.launch { removeSheet.show() } },
                    )
                }
                Footnote(
                    icon = Icons.Outlined.Info,
                    text = stringResource(
                        if (isConfigured) Res.string.private_folder_footnote_configured
                        else Res.string.private_folder_footnote_none
                    ),
                )
            }
        },
    )

    if (removeSheet.isVisible) {
        var keepHidden by remember { mutableStateOf(true) }
        ExpressiveSheet(
            sheetState = removeSheet,
            paneTitle = stringResource(Res.string.private_folder_remove_title),
        ) {
            SheetHeader(
                title = stringResource(Res.string.private_folder_remove_title),
                body = stringResource(Res.string.private_folder_remove_body, displayPath),
                icon = Icons.Outlined.Delete,
                tone = SheetTone.Error,
            )
            Column(modifier = Modifier.padding(top = 16.dp)) {
                SheetOptionRow(
                    title = stringResource(Res.string.private_folder_remove_keep_hidden),
                    summary = stringResource(Res.string.private_folder_remove_keep_hidden_summary),
                    icon = Icons.Outlined.VisibilityOff,
                    position = Position.Top,
                    trailing = SheetRowTrailing.Radio(selected = keepHidden),
                    onClick = { keepHidden = true },
                )
                SheetOptionRow(
                    title = stringResource(Res.string.private_folder_remove_make_visible),
                    summary = stringResource(Res.string.private_folder_remove_make_visible_summary),
                    icon = Icons.Outlined.Visibility,
                    position = Position.Bottom,
                    trailing = SheetRowTrailing.Radio(selected = !keepHidden),
                    onClick = { keepHidden = false },
                )
            }
            SheetDismissConfirmActions(
                dismissText = stringResource(Res.string.action_cancel),
                onDismiss = { scope.launch { removeSheet.hide() } },
                confirmText = stringResource(Res.string.private_folder_remove_action),
                confirmStyle = SheetButtonStyle.Error,
                confirmIcon = Icons.Outlined.Delete,
                onConfirm = {
                    scope.launch {
                        removeSheet.hide()
                        if (keepHidden) onClearFolder() else onClearFolderAndReveal()
                    }
                },
            )
        }
    }
}

@Composable
private fun rememberPrivateFolderRows(
    isConfigured: Boolean,
    onHowItWorks: () -> Unit,
): SnapshotStateList<SettingsEntity> {
    val infoHeader = stringResource(
        if (isConfigured) Res.string.private_folder_header_private else Res.string.private_folder_header_what_you_get
    )
    val info = if (isConfigured) {
        listOf(
            Triple(Icons.Outlined.VisibilityOff, Res.string.private_folder_row_library, Res.string.private_folder_row_library_summary),
            Triple(Icons.Outlined.PhotoAlbum, Res.string.private_folder_row_albums, Res.string.private_folder_row_albums_summary),
            Triple(Icons.Outlined.Lock, Res.string.private_folder_row_locked, Res.string.private_folder_row_locked_summary),
        )
    } else {
        listOf(
            Triple(Icons.Outlined.VisibilityOff, Res.string.private_folder_row_hidden, Res.string.private_folder_row_hidden_summary),
            Triple(Icons.Outlined.SdStorage, Res.string.private_folder_row_any_storage, Res.string.private_folder_row_any_storage_summary),
            Triple(Icons.Outlined.Folder, Res.string.private_folder_row_stay_put, Res.string.private_folder_row_stay_put_summary),
        )
    }.mapIndexed { index, (icon, title, summary) ->
        SettingsEntity.Preference(
            icon = icon,
            title = stringResource(title),
            summary = stringResource(summary),
            screenPosition = positionIn(index, 3),
        )
    }
    val helpHeader = stringResource(Res.string.private_folder_header_help)
    val howItWorks = SettingsEntity.Preference(
        icon = Icons.AutoMirrored.Outlined.HelpOutline,
        title = stringResource(Res.string.private_folder_how_it_works),
        summary = stringResource(Res.string.private_folder_how_it_works_summary),
        onClick = onHowItWorks,
    )
    return remember(infoHeader, info, helpHeader, howItWorks) {
        mutableStateListOf<SettingsEntity>().apply {
            add(SettingsEntity.Header(infoHeader))
            addAll(info)
            add(SettingsEntity.Header(helpHeader))
            add(howItWorks)
        }
    }
}

/** The long private folder explanation, split into five topics. */
@Composable
private fun PrivateFolderHowItWorks(onBack: () -> Unit) {
    val topics = listOf(
        Triple(Icons.Outlined.VisibilityOff, Res.string.private_folder_how_outside, Res.string.private_folder_how_outside_body),
        Triple(Icons.Outlined.Shield, Res.string.private_folder_how_nomedia, Res.string.private_folder_how_nomedia_body),
        Triple(Icons.Outlined.PhotoAlbum, Res.string.private_folder_how_find, Res.string.private_folder_how_find_body),
        Triple(Icons.Outlined.Image, Res.string.private_folder_how_what, Res.string.private_folder_how_what_body),
        Triple(Icons.Outlined.SdStorage, Res.string.private_folder_how_choosing, Res.string.private_folder_how_choosing_body),
    ).mapIndexed { index, (icon, title, body) ->
        SettingsEntity.Preference(
            icon = icon,
            title = stringResource(title),
            summary = stringResource(body),
            screenPosition = positionIn(index, 5),
        )
    }
    BaseSettingsScreen(
        title = stringResource(Res.string.private_folder_how_it_works),
        settingsList = remember(topics) { mutableStateListOf<SettingsEntity>().apply { addAll(topics) } },
        onBack = onBack,
    )
}

private fun positionIn(index: Int, count: Int): Position = when {
    count == 1 -> Position.Alone
    index == 0 -> Position.Top
    index == count - 1 -> Position.Bottom
    else -> Position.Middle
}
