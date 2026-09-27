/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.ui.sharing

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.R
import com.dot.gallery.cloud.core.SharedLinkInfo
import com.dot.gallery.cloud.core.ThumbnailSize
import com.dot.gallery.cloud.image.CloudMediaFetcher
import com.dot.gallery.core.presentation.components.LoadingMedia
import com.dot.gallery.core.presentation.components.NavigationBackButton
import com.dot.gallery.feature_node.presentation.util.LocalHazeState
import com.github.panpf.sketch.AsyncImage
import com.github.panpf.sketch.request.ComposableImageRequest
import dev.chrisbanes.haze.blur.LocalHazeBlurStyle
import com.dot.gallery.feature_node.presentation.util.hazeEffectScaled
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedLinksScreen() {
    val viewModel = hiltViewModel<SharedLinksViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        state = rememberTopAppBarState()
    )
    val context = LocalContext.current
    val linkCopiedText = stringResource(R.string.cloud_shared_links_link_copied)

    var linkToDelete by remember { mutableStateOf<SharedLinkInfo?>(null) }
    var linkToEdit by remember { mutableStateOf<SharedLinkInfo?>(null) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                modifier = Modifier.hazeEffectScaled(
                    state = LocalHazeState.current,
                    style = LocalHazeBlurStyle.current
                ),
                title = { Text(stringResource(R.string.cloud_shared_links)) },
                navigationIcon = { NavigationBackButton() },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
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
                    modifier = Modifier.fillMaxSize(),
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
                            onFilterSelected = { viewModel.setFilter(it) },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    items(state.filteredLinks, key = { it.accountKey }) { link ->
                        SharedLinkItem(
                            link = link,
                            accountLabel = if (state.hasMultipleProviders)
                                viewModel.accountLabelFor(link) else null,
                            onEdit = { linkToEdit = link },
                            onCopyLink = {
                                val url = viewModel.getShareUrl(link)
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Shared Link", url))
                                Toast.makeText(
                                    context,
                                    linkCopiedText,
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            onDelete = { linkToDelete = link }
                        )
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    linkToDelete?.let { link ->
        AlertDialog(
            onDismissRequest = { linkToDelete = null },
            icon = {
                Icon(
                    Icons.Outlined.Link,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text(stringResource(R.string.cloud_shared_links_delete_title)) },
            text = { Text(stringResource(R.string.cloud_shared_links_delete_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteLink(link)
                        linkToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                OutlinedButton(onClick = { linkToDelete = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    // Edit bottom sheet
    linkToEdit?.let { link ->
        EditSharedLinkSheet(
            link = link,
            isUpdating = state.isUpdating,
            onDismiss = { linkToEdit = null },
            onConfirm = { desc, pwd, expires, download, upload, metadata, changeExp ->
                viewModel.updateLink(
                    link = link,
                    description = desc,
                    password = pwd,
                    expiresAt = expires,
                    allowDownload = download,
                    allowUpload = upload,
                    showMetadata = metadata,
                    changeExpiration = changeExp
                )
                linkToEdit = null
            }
        )
    }
}

@Composable
private fun SharedLinksFilterRow(
    currentFilter: SharedLinksFilter,
    onFilterSelected: (SharedLinksFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SharedLinksFilter.entries.forEach { filter ->
                val label = when (filter) {
                    SharedLinksFilter.ALL -> stringResource(R.string.cloud_shared_links_filter_all)
                    SharedLinksFilter.ALBUMS -> stringResource(R.string.cloud_shared_links_filter_albums)
                    SharedLinksFilter.INDIVIDUAL -> stringResource(R.string.cloud_shared_links_filter_individual)
                }
                val selected = currentFilter == filter
                val backgroundColor by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                    label = "pillTabBg"
                )
                val textColor by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "pillTabText"
                )
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .clickable { onFilterSelected(filter) },
                    shape = CircleShape,
                    color = backgroundColor
                ) {
                    Text(
                        text = label,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = textColor,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
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
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (link.thumbnailAssetId != null) {
                val thumbnailUri = CloudMediaFetcher.buildUri(
                    providerType = link.providerType,
                    remoteId = link.thumbnailAssetId,
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
            val expiryText = if (link.expiresAt != null) {
                stringResource(R.string.cloud_shared_links_expires_on, dateFormat.format(Date(link.expiresAt)))
            } else {
                stringResource(R.string.cloud_shared_links_no_expiry)
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
                        text = stringResource(R.string.cloud_shared_links_edit_allow_download_badge),
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
                        text = stringResource(R.string.cloud_shared_links_edit_show_metadata_badge),
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
                    contentDescription = stringResource(R.string.cloud_shared_links_edit_title),
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = onCopyLink) {
                Icon(
                    Icons.Outlined.ContentCopy,
                    contentDescription = stringResource(R.string.cloud_shared_links_link_copied),
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
                    contentDescription = stringResource(R.string.action_delete),
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
            text = stringResource(R.string.cloud_shared_links_empty),
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
    var password by rememberSaveable { mutableStateOf("") }
    var expiresAt by rememberSaveable { mutableLongStateOf(link.expiresAt ?: 0L) }
    var hasExpiry by rememberSaveable { mutableStateOf(link.expiresAt != null) }
    var allowDownload by rememberSaveable { mutableStateOf(link.allowDownload) }
    var allowUpload by rememberSaveable { mutableStateOf(link.allowUpload) }
    var showMetadata by rememberSaveable { mutableStateOf(link.showMetadata) }
    var changeExpiration by rememberSaveable { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.Link,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.cloud_shared_links_edit_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Link type label
            if (link.isAlbumLink && link.albumName != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${stringResource(R.string.cloud_shared_links_public_album)} | ${link.albumName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Description
            Text(
                text = stringResource(R.string.cloud_shared_links_edit_description),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Password
            Text(
                text = stringResource(R.string.cloud_shared_links_edit_password),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.cloud_shared_links_edit_password_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Expire after
            Text(
                text = stringResource(R.string.cloud_shared_links_edit_expire),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                data class ExpiryChip(val label: String, val daysFromNow: Int?)

                val chips = listOf(
                    ExpiryChip(stringResource(R.string.cloud_shared_links_edit_expire_never), null),
                    ExpiryChip(stringResource(R.string.cloud_shared_links_edit_expire_1day), 1),
                    ExpiryChip(stringResource(R.string.cloud_shared_links_edit_expire_7days), 7),
                    ExpiryChip(stringResource(R.string.cloud_shared_links_edit_expire_30days), 30),
                    ExpiryChip(stringResource(R.string.cloud_shared_links_edit_expire_3months), 90),
                    ExpiryChip(stringResource(R.string.cloud_shared_links_edit_expire_1year), 365)
                )

                chips.forEach { chip ->
                    val isSelected = if (chip.daysFromNow == null) !hasExpiry else false
                    val chipBg by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                        label = "expiryChipBg"
                    )
                    val chipText by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        label = "expiryChipText"
                    )
                    Surface(
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable {
                                changeExpiration = true
                                if (chip.daysFromNow == null) {
                                    hasExpiry = false
                                    expiresAt = 0L
                                } else {
                                    hasExpiry = true
                                    val cal = Calendar.getInstance()
                                    cal.add(Calendar.DAY_OF_YEAR, chip.daysFromNow)
                                    expiresAt = cal.timeInMillis
                                }
                            },
                        shape = CircleShape,
                        color = chipBg
                    ) {
                        Text(
                            text = chip.label,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = chipText
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Show metadata toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.cloud_shared_links_edit_show_metadata),
                    style = MaterialTheme.typography.bodyLarge
                )
                Switch(
                    checked = showMetadata,
                    onCheckedChange = { showMetadata = it }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Allow download toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.cloud_shared_links_edit_allow_download),
                    style = MaterialTheme.typography.bodyLarge
                )
                Switch(
                    checked = allowDownload,
                    onCheckedChange = { allowDownload = it }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Allow upload toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.cloud_shared_links_edit_allow_upload),
                    style = MaterialTheme.typography.bodyLarge
                )
                Switch(
                    checked = allowUpload,
                    onCheckedChange = { allowUpload = it }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
                Button(
                    onClick = {
                        onConfirm(
                            description.ifBlank { null },
                            password.ifBlank { null },
                            if (hasExpiry && expiresAt > 0L) expiresAt else null,
                            allowDownload,
                            allowUpload,
                            showMetadata,
                            changeExpiration
                        )
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isUpdating
                ) {
                    Text(stringResource(R.string.cloud_shared_links_confirm))
                }
            }
        }
    }
}
