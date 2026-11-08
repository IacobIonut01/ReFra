/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.ui.sharing

import com.dot.gallery.core.presentation.components.ConfirmationSheetContent
import com.dot.gallery.core.presentation.components.DragHandle
import com.dot.gallery.core.presentation.components.ExpressiveSheetColumn
import com.dot.gallery.core.presentation.components.ExpressiveSheetShape
import com.dot.gallery.core.presentation.components.SheetDismissConfirmActions
import com.dot.gallery.core.presentation.components.SheetHeader
import com.dot.gallery.core.presentation.components.SheetOptionRow
import com.dot.gallery.core.presentation.components.SheetRowTrailing
import com.dot.gallery.core.presentation.components.SheetSectionLabel
import com.dot.gallery.core.presentation.components.SheetSummaryTile
import com.dot.gallery.core.presentation.components.SheetTextField
import com.dot.gallery.core.presentation.components.SheetTone
import com.dot.gallery.core.presentation.components.sheetRowPosition
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import com.dot.gallery.generated.resources.cloud_shared_links_edit_allow_download_desc
import com.dot.gallery.generated.resources.cloud_shared_links_edit_allow_upload_desc
import com.dot.gallery.generated.resources.cloud_shared_links_edit_show_metadata_desc
import com.dot.gallery.generated.resources.cloud_shared_links_save

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dot.gallery.cloud.core.SharedLinkInfo
import com.dot.gallery.cloud.core.ThumbnailSize
import com.dot.gallery.core.presentation.components.LoadingMedia
import com.dot.gallery.core.presentation.util.LocalHazeState
import com.github.panpf.sketch.AsyncImage
import com.github.panpf.sketch.request.ComposableImageRequest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import com.dot.gallery.core.presentation.components.shape
import com.dot.gallery.feature.editor.adjustments.Crop
import com.dot.gallery.feature.help.domain.displayTitle
import com.dot.gallery.core.presentation.util.add
import com.dot.gallery.cloud.image.CloudMediaFetcher
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.action_cancel
import com.dot.gallery.generated.resources.action_delete
import com.dot.gallery.generated.resources.cancel
import com.dot.gallery.generated.resources.cloud_shared_links
import com.dot.gallery.generated.resources.cloud_shared_links_delete_message
import com.dot.gallery.generated.resources.cloud_shared_links_delete_title
import com.dot.gallery.generated.resources.cloud_shared_links_edit_allow_download
import com.dot.gallery.generated.resources.cloud_shared_links_edit_allow_download_badge
import com.dot.gallery.generated.resources.cloud_shared_links_edit_allow_upload
import com.dot.gallery.generated.resources.cloud_shared_links_edit_description
import com.dot.gallery.generated.resources.cloud_shared_links_edit_expire
import com.dot.gallery.generated.resources.cloud_shared_links_edit_expire_1day
import com.dot.gallery.generated.resources.cloud_shared_links_edit_expire_1year
import com.dot.gallery.generated.resources.cloud_shared_links_edit_expire_30days
import com.dot.gallery.generated.resources.cloud_shared_links_edit_expire_3months
import com.dot.gallery.generated.resources.cloud_shared_links_edit_expire_7days
import com.dot.gallery.generated.resources.cloud_shared_links_edit_expire_never
import com.dot.gallery.generated.resources.cloud_shared_links_edit_password
import com.dot.gallery.generated.resources.cloud_shared_links_edit_password_desc
import com.dot.gallery.generated.resources.cloud_shared_links_edit_show_metadata
import com.dot.gallery.generated.resources.cloud_shared_links_edit_show_metadata_badge
import com.dot.gallery.generated.resources.cloud_shared_links_edit_title
import com.dot.gallery.generated.resources.cloud_shared_links_empty
import com.dot.gallery.generated.resources.cloud_shared_links_expires_on
import com.dot.gallery.generated.resources.cloud_shared_links_filter_albums
import com.dot.gallery.generated.resources.cloud_shared_links_filter_all
import com.dot.gallery.generated.resources.cloud_shared_links_filter_individual
import com.dot.gallery.generated.resources.cloud_shared_links_link_copied
import com.dot.gallery.generated.resources.cloud_shared_links_no_expiry
import com.dot.gallery.generated.resources.cloud_shared_links_public_album
import com.dot.gallery.core.presentation.components.GalleryPageTopBar
import com.dot.gallery.core.presentation.components.rememberScrolledUnderTopBar
import dev.chrisbanes.haze.hazeSource
import androidx.compose.foundation.lazy.rememberLazyListState



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedLinksContent(
    state: SharedLinksUiState,
    actions: SharedLinksContentActions,
    initialEditLinkId: String? = null,
) {
    val listState = rememberLazyListState()

    var linkToDelete by remember { mutableStateOf<SharedLinkInfo?>(null) }
    var linkToEdit by remember {
        mutableStateOf(state.allLinks.firstOrNull { it.id == initialEditLinkId })
    }

    Scaffold(
        topBar = {
            GalleryPageTopBar(
                title = stringResource(Res.string.cloud_shared_links),
                hazeState = LocalHazeState.current,
                scrolled = rememberScrolledUnderTopBar(listState),
            )
        }
    ) { innerPadding ->
        val layoutDir = LocalLayoutDirection.current
        when {
            state.isLoading -> {
                LoadingMedia(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }
            state.allLinks.isEmpty() && state.error == null -> {
                EmptySharedLinks(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }
            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().hazeSource(LocalHazeState.current),
                    contentPadding = PaddingValues(
                        start = innerPadding.calculateStartPadding(layoutDir),
                        end = innerPadding.calculateEndPadding(layoutDir),
                        top = innerPadding.calculateTopPadding(),
                        bottom = innerPadding.calculateBottomPadding()
                    ),
                ) {
                    state.error?.let { error ->
                        item {
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                    item {
                        SharedLinksFilterRow(
                            currentFilter = state.filter,
                            onFilterSelected = actions.setFilter,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    items(state.filteredLinks, key = { it.accountKey }) { link ->
                        SharedLinkItem(
                            link = link,
                            accountLabel = if (state.hasMultipleProviders)
                                state.accountLabels[link.serverConfigId] ?: link.providerType.displayName else null,
                            onEdit = { linkToEdit = link },
                            onCopyLink = { actions.copyLink(link) },
                            onDelete = { linkToDelete = link }
                        )
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    linkToDelete?.let { link ->
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { linkToDelete = null },
            sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
            dragHandle = {
                com.dot.gallery.core.presentation.components.DragHandle(modifier = Modifier.padding(vertical = 5.dp))
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 0.dp,
            shape = com.dot.gallery.core.presentation.components.ExpressiveSheetShape,
        ) {
            com.dot.gallery.core.presentation.components.ExpressiveSheetColumn {
                run {
                    ConfirmationSheetContent(
                        title = stringResource(Res.string.cloud_shared_links_delete_title),
                        summary = stringResource(Res.string.cloud_shared_links_delete_message),
                        confirmText = stringResource(Res.string.action_delete),
                        dismissText = stringResource(Res.string.action_cancel),
                        icon = Icons.Outlined.Link,
                        tone = SheetTone.Error,
                        details = { SheetSummaryTile(title = link.displayTitle) },
                        onDismiss = { linkToDelete = null },
                        onConfirm = {
                            actions.deleteLink(link)
                            linkToDelete = null
                        },
                    )
                }
            }
        }
    }

    // Edit bottom sheet
    linkToEdit?.let { link ->
        EditSharedLinkSheet(
            link = link,
            isUpdating = state.isUpdating,
            onDismiss = { linkToEdit = null },
            onConfirm = { desc, pwd, expires, download, upload, metadata, changeExp ->
                actions.updateLink(
                    link,
                    desc,
                    pwd,
                    expires,
                    download,
                    upload,
                    metadata,
                    changeExp
                )
                linkToEdit = null
            }
        )
    }
}

@Composable
internal fun SharedLinksFilterRow(
    currentFilter: SharedLinksFilter,
    onFilterSelected: (SharedLinksFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SharedLinksFilter.entries.forEach { filter ->
            val label = when (filter) {
                SharedLinksFilter.ALL -> stringResource(Res.string.cloud_shared_links_filter_all)
                SharedLinksFilter.ALBUMS -> stringResource(Res.string.cloud_shared_links_filter_albums)
                SharedLinksFilter.INDIVIDUAL -> stringResource(Res.string.cloud_shared_links_filter_individual)
            }
            SharedLinksChip(
                label = label,
                selected = currentFilter == filter,
                onClick = { onFilterSelected(filter) },
            )
        }
    }
}

/** 40 dp choice chip of the shared links page and edit sheet: tonal when selected, grey pill otherwise. */
@Composable
private fun SharedLinksChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHighest,
        label = "sharedLinksChipBg"
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "sharedLinksChipText"
    )
    val shape = if (selected) RoundedCornerShape(12.dp) else CircleShape
    Box(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .clip(shape)
            .background(backgroundColor)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SharedLinkItem(
    link: SharedLinkInfo,
    accountLabel: String? = null,
    onEdit: () -> Unit,
    onCopyLink: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(start = 12.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            val thumbnailAssetId = link.thumbnailAssetId
            if (thumbnailAssetId != null) {
                val thumbnailUri = CloudMediaFetcher.buildUri(
                    providerType = link.providerType,
                    remoteId = thumbnailAssetId,
                    size = ThumbnailSize.THUMBNAIL,
                    configId = link.serverConfigId
                )
                AsyncImage(
                    request = ComposableImageRequest(thumbnailUri),
                    contentDescription = link.displayTitle,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    Icons.Outlined.Link,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Info column
        Column(modifier = Modifier.weight(1f)) {
            // Expiry badge
            val expiresAt = link.expiresAt
            val expiryText = if (expiresAt != null) {
                stringResource(Res.string.cloud_shared_links_expires_on, dateFormat.format(Date(expiresAt)))
            } else {
                stringResource(Res.string.cloud_shared_links_no_expiry)
            }
            Text(
                text = expiryText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // Title
            Text(
                text = link.displayTitle,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // Badges row
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (accountLabel != null) {
                    Text(
                        text = accountLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.primaryContainer,
                                RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                if (link.allowDownload) {
                    Text(
                        text = stringResource(Res.string.cloud_shared_links_edit_allow_download_badge),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.secondaryContainer,
                                RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                if (link.showMetadata) {
                    Text(
                        text = stringResource(Res.string.cloud_shared_links_edit_show_metadata_badge),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.tertiaryContainer,
                                RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }

        // Action buttons
        Row {
            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = stringResource(Res.string.cloud_shared_links_edit_title),
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = onCopyLink) {
                Icon(
                    Icons.Outlined.ContentCopy,
                    contentDescription = stringResource(Res.string.cloud_shared_links_link_copied),
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(
                onClick = onDelete,
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = stringResource(Res.string.action_delete),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptySharedLinks(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(top = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        Icon(
            modifier = Modifier.size(128.dp),
            imageVector = Icons.Outlined.LinkOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = stringResource(Res.string.cloud_shared_links_empty),
            style = MaterialTheme.typography.titleLarge
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun EditSharedLinkSheet(
    link: SharedLinkInfo,
    isUpdating: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (
        description: String?,
        password: String?,
        expiresAt: Long?,
        allowDownload: Boolean,
        allowUpload: Boolean,
        showMetadata: Boolean,
        changeExpiration: Boolean
    ) -> Unit
) {
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))

    var description by rememberSaveable { mutableStateOf(link.description ?: "") }
    var passwordEnabled by rememberSaveable { mutableStateOf(false) }
    var password by rememberSaveable { mutableStateOf("") }
    // Null keeps the link's current expiry; 0 is Never; otherwise days from now.
    var expiryDays by rememberSaveable { mutableStateOf(if (link.expiresAt == null) 0 else null) }
    var changeExpiration by rememberSaveable { mutableStateOf(false) }
    var allowDownload by rememberSaveable { mutableStateOf(link.allowDownload) }
    var allowUpload by rememberSaveable { mutableStateOf(link.allowUpload) }
    var showMetadata by rememberSaveable { mutableStateOf(link.showMetadata) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { DragHandle(modifier = Modifier.padding(vertical = 5.dp)) },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        shape = ExpressiveSheetShape,
    ) {
        ExpressiveSheetColumn {
            EditSharedLinkSheetContent(
                subtitle = link.albumName?.takeIf { link.isAlbumLink }
                    ?.let { "${stringResource(Res.string.cloud_shared_links_public_album)} · $it" },
                description = description,
                onDescriptionChange = { description = it },
                expiryDays = expiryDays,
                onExpiryDays = {
                    expiryDays = it
                    changeExpiration = true
                },
                passwordEnabled = passwordEnabled,
                onPasswordEnabled = { passwordEnabled = it },
                password = password,
                onPasswordChange = { password = it },
                showMetadata = showMetadata,
                onShowMetadata = { showMetadata = it },
                allowDownload = allowDownload,
                onAllowDownload = { allowDownload = it },
                allowUpload = allowUpload,
                onAllowUpload = { allowUpload = it },
                canSave = !isUpdating,
                onDismiss = onDismiss,
                onSave = {
                    val days = expiryDays
                    val expiresAt = when {
                        days == null -> link.expiresAt
                        days == 0 -> null
                        else -> Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, days) }.timeInMillis
                    }
                    onConfirm(
                        description.ifBlank { null },
                        password.takeIf { passwordEnabled }?.ifBlank { null },
                        expiresAt,
                        allowDownload,
                        allowUpload,
                        showMetadata,
                        changeExpiration
                    )
                },
            )
        }
    }
}

/**
 * Body of the shared link edit sheet: description, expiry chips and the grouped password,
 * metadata, download and upload switches. [expiryDays] is null when the link keeps its current
 * expiry, 0 for Never and otherwise the number of days from now.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ColumnScope.EditSharedLinkSheetContent(
    subtitle: String?,
    description: String,
    onDescriptionChange: (String) -> Unit,
    expiryDays: Int?,
    onExpiryDays: (Int) -> Unit,
    passwordEnabled: Boolean,
    onPasswordEnabled: (Boolean) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    showMetadata: Boolean,
    onShowMetadata: (Boolean) -> Unit,
    allowDownload: Boolean,
    onAllowDownload: (Boolean) -> Unit,
    allowUpload: Boolean,
    onAllowUpload: (Boolean) -> Unit,
    canSave: Boolean,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    SheetHeader(title = stringResource(Res.string.cloud_shared_links_edit_title), body = subtitle)
    SheetTextField(
        value = description,
        onValueChange = onDescriptionChange,
        label = stringResource(Res.string.cloud_shared_links_edit_description),
    )
    SheetSectionLabel(text = stringResource(Res.string.cloud_shared_links_edit_expire))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(
            0 to Res.string.cloud_shared_links_edit_expire_never,
            1 to Res.string.cloud_shared_links_edit_expire_1day,
            7 to Res.string.cloud_shared_links_edit_expire_7days,
            30 to Res.string.cloud_shared_links_edit_expire_30days,
            90 to Res.string.cloud_shared_links_edit_expire_3months,
            365 to Res.string.cloud_shared_links_edit_expire_1year,
        ).forEach { (days, label) ->
            SharedLinksChip(
                label = stringResource(label),
                selected = expiryDays == days,
                onClick = { onExpiryDays(days) },
            )
        }
    }
    Spacer(Modifier.height(16.dp))
    val rows = listOf(
        Triple(Icons.Outlined.Lock, Res.string.cloud_shared_links_edit_password, Res.string.cloud_shared_links_edit_password_desc) to
            (passwordEnabled to onPasswordEnabled),
        Triple(Icons.Outlined.Info, Res.string.cloud_shared_links_edit_show_metadata, Res.string.cloud_shared_links_edit_show_metadata_desc) to
            (showMetadata to onShowMetadata),
        Triple(Icons.Outlined.Download, Res.string.cloud_shared_links_edit_allow_download, Res.string.cloud_shared_links_edit_allow_download_desc) to
            (allowDownload to onAllowDownload),
        Triple(Icons.Outlined.CloudUpload, Res.string.cloud_shared_links_edit_allow_upload, Res.string.cloud_shared_links_edit_allow_upload_desc) to
            (allowUpload to onAllowUpload),
    )
    rows.forEachIndexed { index, (labels, toggle) ->
        val (icon, title, summary) = labels
        val (checked, onToggle) = toggle
        SheetOptionRow(
            title = stringResource(title),
            summary = stringResource(summary),
            icon = icon,
            position = sheetRowPosition(index, rows.size),
            trailing = SheetRowTrailing.Switch(checked),
            onClick = { onToggle(!checked) },
        )
        if (index == 0 && passwordEnabled) {
            SheetTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = stringResource(Res.string.cloud_shared_links_edit_password),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
    }
    SheetDismissConfirmActions(
        dismissText = stringResource(Res.string.action_cancel),
        onDismiss = onDismiss,
        confirmText = stringResource(Res.string.cloud_shared_links_save),
        onConfirm = onSave,
        confirmEnabled = canSave,
    )
}
