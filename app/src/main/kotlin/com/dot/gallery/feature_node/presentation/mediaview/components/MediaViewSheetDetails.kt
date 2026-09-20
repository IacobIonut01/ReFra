/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.mediaview.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.toUpperCase
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.dot.gallery.R
import com.dot.gallery.cloud.core.CloudUri
import com.dot.gallery.cloud.core.PersonInfo
import com.dot.gallery.cloud.ui.backup.CloudBackupInfoSheet
import com.dot.gallery.cloud.ui.descriptor.ProviderBrandIcon
import com.dot.gallery.cloud.ui.people.PersonAvatar
import com.dot.gallery.core.Constants.Animation.enterAnimation
import com.dot.gallery.core.Constants.Animation.exitAnimation
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.LocalMediaHandler
import com.dot.gallery.feature_node.presentation.mediaview.LocalMediaViewerVisualPolicy
import com.dot.gallery.core.navigate
import com.dot.gallery.core.metadata.MetadataRemovalMode
import com.dot.gallery.core.metadata.MetadataSaveMode
import com.dot.gallery.core.metadata.SanitizationCapability
import com.dot.gallery.core.metadata.SanitizationResult
import com.dot.gallery.core.presentation.components.DragHandle
import com.dot.gallery.core.presentation.components.NavigationBarSpacer
import com.dot.gallery.core.util.SdkCompat
import com.dot.gallery.feature_node.domain.model.AlbumState
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.MediaMetadataState
import com.dot.gallery.feature_node.domain.model.Vault
import com.dot.gallery.feature_node.domain.model.VaultState
import com.dot.gallery.feature_node.domain.model.rememberLocationData
import com.dot.gallery.feature_node.domain.model.rememberMediaDateCaption
import com.dot.gallery.feature_node.domain.util.canMakeActions
import com.dot.gallery.feature_node.domain.util.fileExtension
import com.dot.gallery.feature_node.domain.util.getCategory
import com.dot.gallery.feature_node.domain.util.getUri
import com.dot.gallery.feature_node.domain.util.isCloud
import com.dot.gallery.feature_node.domain.util.isEncrypted
import com.dot.gallery.feature_node.domain.util.isImage
import com.dot.gallery.feature_node.domain.util.isRaw
import com.dot.gallery.feature_node.domain.util.isTrashed
import com.dot.gallery.feature_node.domain.util.isVideo
import com.dot.gallery.feature_node.domain.util.readUriOnly
import com.dot.gallery.feature_node.presentation.exif.CaptureDateEditSheet
import com.dot.gallery.feature_node.presentation.exif.MetadataEditSheet
import com.dot.gallery.feature_node.presentation.exif.MoveMediaSheet
import com.dot.gallery.feature_node.presentation.mediaview.components.media.MotionPhotoShotsSection
import com.dot.gallery.feature_node.presentation.mediaview.components.media.MotionPhotoState
import com.dot.gallery.feature_node.presentation.mediaview.MediaViewViewModel
import com.dot.gallery.feature_node.presentation.mediaview.rememberedDerivedState
import com.dot.gallery.feature_node.presentation.util.GlideInvalidation
import com.dot.gallery.feature_node.presentation.util.LocalHazeState
import com.dot.gallery.feature_node.presentation.util.hazeEffectScaled
import com.dot.gallery.feature_node.presentation.util.matchesSha256
import com.dot.gallery.feature_node.presentation.util.Screen
import com.dot.gallery.feature_node.presentation.util.printDebug
import com.dot.gallery.feature_node.presentation.util.rememberActivityResult
import com.dot.gallery.feature_node.presentation.util.rememberAppBottomSheetState
import com.dot.gallery.feature_node.presentation.util.rememberMediaInfo
import com.dot.gallery.feature_node.presentation.util.trashRequest
import com.dot.gallery.feature_node.presentation.vault.components.ConfirmationSheet
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import kotlinx.coroutines.launch

@OptIn(ExperimentalHazeMaterialsApi::class, ExperimentalGlideComposeApi::class)
@Composable
fun <T : Media> MediaViewSheetDetails(
    albumsState: State<AlbumState>,
    vaultState: State<VaultState>,
    metadataState: State<MediaMetadataState>,
    currentMedia: T?,
    restoreMedia: ((Vault, T, () -> Unit) -> Unit)?,
    currentVault: Vault?,
    motionPhotoState: MotionPhotoState? = null,
    onOpenFramePicker: () -> Unit = {},
    cloudBackups: List<Media.UriMedia> = emptyList(),
    onOpenPersonTimeline: (PersonInfo) -> Unit = {},
    metadataSanitizationState: MediaViewViewModel.MetadataSanitizationUiState =
        MediaViewViewModel.MetadataSanitizationUiState.Idle,
    probeMetadataSanitization: (Media) -> Unit = {},
    sanitizeMetadata: (Media, MetadataRemovalMode, MetadataSaveMode) -> Unit = { _, _, _ -> },
    resetMetadataSanitization: () -> Unit = {},
) {
    val metadata by rememberedDerivedState(metadataState.value, currentMedia) {
        currentMedia?.id?.let { metadataState.value.metadataMap[it] }
    }
    LaunchedEffect(metadata) {
        printDebug("Available metadata for ${currentMedia?.id}:\n${metadata.toString()}")
    }
    val handler = LocalMediaHandler.current
    // Hoisted above the media-gated content below: a move that removes the current
    // item (e.g. the last photo of the viewed album) would otherwise dispose the
    // sheet mid-operation together with its completion state. MoveMediaSheet itself
    // freezes mediaList while visible.
    val moveSheetState = rememberAppBottomSheetState()
    val isBlurEnabled = LocalMediaViewerVisualPolicy.current.allowBlur
    val surfaceColor = MaterialTheme.colorScheme.surface
    val surfaceColorVariant = MaterialTheme.colorScheme.surfaceContainer
    val surfaceContainerHigh = MaterialTheme.colorScheme.surfaceContainerHigh
    val sheetCardBackgroundModifier = remember(isBlurEnabled) {
        if (!isBlurEnabled) {
            Modifier.background(
                color = surfaceColorVariant,
                shape = RoundedCornerShape(16.dp)
            )
        } else {
            Modifier
        }
    }
    val sheetHazeStyle = HazeMaterials.thin(
        containerColor = surfaceColor
    )
    val sheetCardHazeStyle = HazeMaterials.regular(
        containerColor = surfaceColor
    )

    val sheetCardButtonHazeStyle = HazeMaterials.thick(
        containerColor = surfaceColorVariant
    )
    val iconBackgroundHazeStyle = HazeMaterials.thick(
        containerColor = surfaceContainerHigh
    )
    val iconBackgroundModifier = remember(isBlurEnabled) {
        if (!isBlurEnabled) {
            Modifier.background(
                color = surfaceContainerHigh,
                shape = RoundedCornerShape(10.dp)
            )
        } else {
            Modifier
        }
    }
    val buttonBackgroundModifier = remember(isBlurEnabled) {
        if (!isBlurEnabled) {
            Modifier.background(
                color = surfaceContainerHigh,
                shape = RoundedCornerShape(12.dp)
            )
        } else {
            Modifier
        }
    }
    val sheetBackgroundModifier = remember(isBlurEnabled) {
        if (!isBlurEnabled) {
            Modifier.background(
                color = surfaceColor,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            )
        } else {
            Modifier
        }
    }
    Column(
        modifier = Modifier
            .widthIn(max = 600.dp)
            .fillMaxWidth()
            .clip(
                RoundedCornerShape(
                    topStart = 24.dp,
                    topEnd = 24.dp
                )
            )
            .then(sheetBackgroundModifier)
            .hazeEffectScaled(
                state = LocalHazeState.current,
                style = sheetHazeStyle
            )
            .graphicsLayer {
                translationY = -1f
            }
    ) {

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            DragHandle()
        }

        AnimatedVisibility(
            modifier = Modifier.fillMaxWidth(),
            visible = currentMedia != null && !currentMedia.isTrashed,
            enter = enterAnimation,
            exit = exitAnimation
        ) {
            if (currentMedia != null) {
                val context = LocalContext.current
                val scope = rememberCoroutineScope()

                val metadataRemovalSheetState = rememberAppBottomSheetState()
                val metadataRemovalSuccessText = stringResource(R.string.remove_metadata_success)
                val metadataRemovalCopySuccessText = stringResource(R.string.remove_metadata_copy_success)
                val metadataRemovalRolledBackText = stringResource(R.string.remove_metadata_rolled_back)
                val metadataRemovalFailedText = stringResource(R.string.remove_metadata_failed)
                val canReplaceOriginal = SdkCompat.supportsTrash
                var rememberedSanitizationCapability by remember(currentMedia.id) {
                    mutableStateOf<SanitizationCapability?>(null)
                }
                LaunchedEffect(metadataSanitizationState, currentMedia.id) {
                    if (metadataSanitizationState is MediaViewViewModel.MetadataSanitizationUiState.Ready &&
                        metadataSanitizationState.mediaId == currentMedia.id
                    ) {
                        rememberedSanitizationCapability = metadataSanitizationState.capability
                    }
                }
                val sanitizationCapability = when (metadataSanitizationState) {
                    is MediaViewViewModel.MetadataSanitizationUiState.Ready ->
                        metadataSanitizationState.takeIf { it.mediaId == currentMedia.id }?.capability
                            ?: rememberedSanitizationCapability
                    else -> rememberedSanitizationCapability
                }
                val metadataRemovalBusy =
                    metadataSanitizationState is MediaViewViewModel.MetadataSanitizationUiState.Probing ||
                        metadataSanitizationState is MediaViewViewModel.MetadataSanitizationUiState.Running
                val metadataRemovalPermissionResult = rememberActivityResult(
                    onResultCanceled = {
                        Toast.makeText(context, metadataRemovalCopySuccessText, Toast.LENGTH_SHORT).show()
                        scope.launch { metadataRemovalSheetState.hide() }
                        resetMetadataSanitization()
                    },
                    onResultOk = {
                        Toast.makeText(context, metadataRemovalSuccessText, Toast.LENGTH_SHORT).show()
                        scope.launch { metadataRemovalSheetState.hide() }
                        resetMetadataSanitization()
                    }
                )
                LaunchedEffect(
                    metadataSanitizationState,
                    currentMedia.id,
                    metadataRemovalSuccessText,
                    metadataRemovalCopySuccessText,
                    metadataRemovalRolledBackText,
                    metadataRemovalFailedText,
                ) {
                    val staleTerminalState = when (metadataSanitizationState) {
                        is MediaViewViewModel.MetadataSanitizationUiState.Ready ->
                            metadataSanitizationState.mediaId != currentMedia.id
                        is MediaViewViewModel.MetadataSanitizationUiState.Complete ->
                            metadataSanitizationState.mediaId != currentMedia.id
                        else -> false
                    }
                    if (staleTerminalState) {
                        resetMetadataSanitization()
                        return@LaunchedEffect
                    }
                    when (metadataSanitizationState) {
                        is MediaViewViewModel.MetadataSanitizationUiState.Ready -> {
                            if (metadataSanitizationState.mediaId == currentMedia.id) {
                                metadataRemovalSheetState.show()
                            }
                        }
                        is MediaViewViewModel.MetadataSanitizationUiState.Complete -> {
                            if (metadataSanitizationState.mediaId == currentMedia.id) {
                                val result = metadataSanitizationState.result
                                if (result is SanitizationResult.Success &&
                                    result.saveMode == MetadataSaveMode.REPLACE_ORIGINAL
                                ) {
                                    val sourceUnchanged = result.sourceUri.matchesSha256(
                                        context.contentResolver,
                                        result.sourceSha256
                                    )
                                    val trashRequest = if (sourceUnchanged) {
                                        result.sourceUri.trashRequest(context.contentResolver)
                                    } else {
                                        null
                                    }
                                    if (trashRequest != null) {
                                        metadataRemovalPermissionResult.launch(trashRequest)
                                    } else {
                                        Toast.makeText(
                                            context,
                                            metadataRemovalCopySuccessText,
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        metadataRemovalSheetState.hide()
                                        resetMetadataSanitization()
                                    }
                                } else {
                                    val message = when (result) {
                                        is SanitizationResult.Success -> metadataRemovalCopySuccessText
                                        is SanitizationResult.CommitFailed -> if (result.rolledBack) {
                                            metadataRemovalRolledBackText
                                        } else {
                                            metadataRemovalFailedText
                                        }
                                        else -> metadataRemovalFailedText
                                    }
                                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                    if (result is SanitizationResult.Success) metadataRemovalSheetState.hide()
                                    resetMetadataSanitization()
                                }
                            }
                        }
                        else -> Unit
                    }
                }

                val dateCaption = rememberMediaDateCaption(metadata, currentMedia)
                val metadataSheetState = rememberAppBottomSheetState()
                val captureDateSheetState = rememberAppBottomSheetState()
                val backupSheetState = rememberAppBottomSheetState()
                val removePersonConfirmState = rememberAppBottomSheetState()
                var pendingPerson by remember { mutableStateOf<PersonInfo?>(null) }

                // On-device persons whose faces were detected in this media — emitted
                // live so the people card and its remove flow stay in sync with removals.
                val mediaViewViewModel = hiltViewModel<MediaViewViewModel>()
                val mediaPeople by produceState<List<PersonInfo>>(emptyList(), currentMedia.id) {
                    mediaViewViewModel.peopleForMedia(currentMedia.id).collect { value = it }
                }
                val unknownPersonText = stringResource(R.string.cloud_people_unknown)
                val personPhotoCountFormat = stringResource(R.string.cloud_person_photo_count)
                val allMetadataEventHandler = LocalEventHandler.current
                val mediaInfoList = rememberMediaInfo(
                    media = currentMedia,
                    exifMetadata = metadata,
                    onLabelClick = {
                        if (!currentMedia.readUriOnly) {
                            scope.launch {
                                metadataSheetState.show()
                            }
                        }
                    }
                )

                val locationData = rememberLocationData(metadata)
                var category by remember(currentMedia) {
                    mutableStateOf(currentMedia.getCategory)
                }
                LaunchedEffect(currentMedia, category, handler) {
                    if (category == null) {
                        category = handler.getCategoryForMediaId(currentMedia.id)
                    }
                }
                val mediaCategoryCounter by if (category != null) {
                    handler.getClassifiedMediaCountAtCategory(category!!)
                        .collectAsStateWithLifecycle(0)
                } else {
                    remember { mutableStateOf(0) }
                }
                val mediaCategoryThumbnail by if (category != null) {
                    handler.getClassifiedMediaThumbnailByCategory(category!!)
                        .collectAsStateWithLifecycle(null)
                } else {
                    remember { mutableStateOf(null) }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    item(key = "date_location") {
                        Column(
                            modifier = Modifier
                                .widthIn(max = 600.dp)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .then(sheetCardBackgroundModifier)
                                .hazeEffectScaled(
                                    state = LocalHazeState.current,
                                    style = sheetCardHazeStyle
                                )
                                .padding(16.dp)
                        ) {
                            DateHeader(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        enabled = currentMedia.canMakeActions && currentMedia.isImage,
                                        indication = null,
                                        interactionSource = remember {
                                            MutableInteractionSource()
                                        }
                                    ) {
                                        scope.launch {
                                            captureDateSheetState.show()
                                        }
                                    },
                                mediaDateCaption = dateCaption
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(state = rememberScrollState())
                                    .padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (currentMedia.isRaw) {
                                    MediaInfoChip(
                                        text = currentMedia.fileExtension.toUpperCase(Locale.current),
                                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                                if (currentMedia.isEncrypted) {
                                    MediaInfoChip(
                                        text = stringResource(R.string.encrypted),
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    )
                                }
                                if (currentMedia.isCloud) {
                                    MediaInfoChip(
                                        text = stringResource(R.string.cloud_media),
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            LocationItem(
                                iconBackgroundModifier = Modifier
                                    .then(iconBackgroundModifier)
                                    .hazeEffectScaled(
                                        state = LocalHazeState.current,
                                        style = iconBackgroundHazeStyle
                                    ),
                                locationData = locationData,
                                mediaUri = currentMedia.getUri(),
                                onShowInApp = {
                                    allMetadataEventHandler.navigate(Screen.LocationsScreen())
                                }
                            )
                            AnimatedVisibility(visible = currentMedia.canMakeActions) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .then(buttonBackgroundModifier)
                                        .hazeEffectScaled(
                                            state = LocalHazeState.current,
                                            style = sheetCardButtonHazeStyle
                                        )
                                        .clickable(enabled = !metadataRemovalBusy) {
                                            probeMetadataSanitization(currentMedia)
                                        }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.LocalFireDepartment,
                                        contentDescription = stringResource(R.string.remove_metadata),
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = stringResource(R.string.remove_metadata),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                    if (motionPhotoState != null) {
                        item(key = "motion_photo") {
                            MotionPhotoShotsSection(
                                state = motionPhotoState,
                                onOpenFramePicker = onOpenFramePicker,
                                modifier = Modifier
                                    .widthIn(max = 600.dp)
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .then(sheetCardBackgroundModifier)
                                    .hazeEffectScaled(
                                        state = LocalHazeState.current,
                                        style = sheetCardHazeStyle
                                    )
                                    .padding(16.dp)
                            )
                        }
                    }
                    if (cloudBackups.isNotEmpty()) {
                        item(key = "cloud_backup") {
                            Column(
                                modifier = Modifier
                                    .widthIn(max = 600.dp)
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .then(sheetCardBackgroundModifier)
                                    .hazeEffectScaled(
                                        state = LocalHazeState.current,
                                        style = sheetCardHazeStyle
                                    )
                                    .padding(vertical = 16.dp)
                            ) {
                                val backupProviders = remember(cloudBackups) {
                                    cloudBackups
                                        .mapNotNull { CloudUri.parse(it.uri.toString())?.providerType }
                                        .distinct()
                                }
                                MediaInfoRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                    label = stringResource(R.string.cloud_backed_up_title),
                                    content = stringResource(R.string.cloud_backed_up_tap_hint),
                                    icon = Icons.Outlined.CloudDone,
                                    iconBackgroundModifier = Modifier
                                        .then(iconBackgroundModifier)
                                        .hazeEffectScaled(
                                            state = LocalHazeState.current,
                                            style = iconBackgroundHazeStyle
                                        ),
                                    trailingContent = {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            backupProviders.forEach { providerType ->
                                                ProviderBrandIcon(
                                                    providerType = providerType,
                                                    modifier = Modifier.size(22.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        scope.launch { backupSheetState.show() }
                                    }
                                )
                            }
                        }
                    }
                    if (mediaPeople.isNotEmpty()) {
                        item(key = "people") {
                            Column(
                                modifier = Modifier
                                    .widthIn(max = 600.dp)
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .then(sheetCardBackgroundModifier)
                                    .hazeEffectScaled(
                                        state = LocalHazeState.current,
                                        style = sheetCardHazeStyle
                                    )
                                    .padding(vertical = 16.dp)
                            ) {
                                mediaPeople.forEach { person ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { onOpenPersonTimeline(person) }
                                            .padding(horizontal = 8.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        PersonAvatar(person = person, size = 40.dp)
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Text(
                                                text = person.name.ifBlank { unknownPersonText },
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = personPhotoCountFormat.format(person.assetCount),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Outlined.PersonRemove,
                                            contentDescription = stringResource(R.string.cloud_person_remove_media),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .clickable {
                                                    pendingPerson = person
                                                    scope.launch { removePersonConfirmState.show() }
                                                }
                                                .padding(8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    item(key = "media_info") {
                        Column(
                            modifier = Modifier
                                .widthIn(max = 600.dp)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .then(sheetCardBackgroundModifier)
                                .hazeEffectScaled(
                                    state = LocalHazeState.current,
                                    style = sheetCardHazeStyle
                                )
                                .padding(vertical = 16.dp)
                        ) {
                            mediaInfoList.forEach {
                                MediaInfoRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                    label = it.label,
                                    content = it.content,
                                    icon = it.icon,
                                    iconBackgroundModifier = Modifier
                                        .then(iconBackgroundModifier)
                                        .hazeEffectScaled(
                                            state = LocalHazeState.current,
                                            style = iconBackgroundHazeStyle
                                        ),
                                    trailingContent = {
                                        if (it.trailingIcon != null && currentMedia.canMakeActions) {
                                            MediaInfoChip(
                                                text = stringResource(R.string.edit),
                                                contentColor = MaterialTheme.colorScheme.secondary,
                                                containerColor = MaterialTheme.colorScheme.secondary.copy(
                                                    alpha = 0.1f
                                                ),
                                                onClick = {
                                                    scope.launch {
                                                        metadataSheetState.show()
                                                    }
                                                }
                                            )
                                        }
                                    },
                                    onClick = it.onClick
                                )
                            }
                            // Only offer "view all metadata" when there is metadata to show.
                            // metadata is null when nothing could be parsed for this item, so
                            // hiding the row avoids opening an empty metadata screen.
                            if (!currentMedia.isEncrypted && metadata != null) {
                                MediaInfoRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                    label = stringResource(R.string.view_all_metadata),
                                    content = stringResource(R.string.metadata),
                                    icon = Icons.Outlined.Info,
                                    iconBackgroundModifier = Modifier
                                        .then(iconBackgroundModifier)
                                        .hazeEffectScaled(
                                            state = LocalHazeState.current,
                                            style = iconBackgroundHazeStyle
                                        ),
                                    onClick = {
                                        allMetadataEventHandler.navigate(
                                            Screen.MetadataViewScreen.uriAndType(
                                                mediaUri = currentMedia.getUri().toString(),
                                                isVideo = currentMedia.isVideo
                                            )
                                        )
                                    }
                                )
                            }
                            if (category != null) {
                                val eventHandler = LocalEventHandler.current
                                MediaInfoRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                    label = category!!,
                                    content = stringResource(
                                        R.string.s_items,
                                        mediaCategoryCounter
                                    ),
                                    iconBackgroundModifier = Modifier
                                        .then(iconBackgroundModifier)
                                        .hazeEffectScaled(
                                            state = LocalHazeState.current,
                                            style = iconBackgroundHazeStyle
                                        ),
                                    trailingContent = {
                                        AnimatedVisibility(
                                            visible = mediaCategoryThumbnail != null,
                                            enter = enterAnimation,
                                            exit = exitAnimation
                                        ) {
                                            GlideImage(
                                                model = mediaCategoryThumbnail!!.uri,
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .clip(RoundedCornerShape(16.dp)),
                                                requestBuilderTransform = {
                                                    it.signature(GlideInvalidation.signature(mediaCategoryThumbnail!!))
                                                }
                                            )
                                        }
                                    },
                                    onClick = {
                                        eventHandler.navigate(
                                            Screen.CategoryViewScreen.category(
                                                category!!
                                            )
                                        )
                                    }
                                )
                            }
                        }
                    }
                    item(key = "actions") {
                        MediaViewSheetActions(
                            media = currentMedia,
                            albumsState = albumsState,
                            vaults = vaultState,
                            restoreMedia = restoreMedia,
                            currentVault = currentVault,
                            moveSheetState = moveSheetState,
                            isMotionPhoto = motionPhotoState?.isDetected == true || metadata?.isMotionPhoto == true,
                            onOpenFramePicker = onOpenFramePicker,
                        )
                    }
                    item(key = "spacer") {
                        NavigationBarSpacer()
                    }
                }

                if (metadataRemovalSheetState.isVisible) {
                    MetadataRemovalSheet(
                        state = metadataRemovalSheetState,
                        capability = sanitizationCapability,
                        isBusy = metadataRemovalBusy,
                        canReplaceOriginal = canReplaceOriginal,
                        onConfirm = { mode, saveMode ->
                            sanitizeMetadata(currentMedia, mode, saveMode)
                        }
                    )
                }

                if (metadataSheetState.isVisible) {
                    MetadataEditSheet(
                        state = metadataSheetState,
                        media = currentMedia,
                        metadata = metadata
                    )
                }

                if (captureDateSheetState.isVisible) {
                    CaptureDateEditSheet(
                        state = captureDateSheetState,
                        media = currentMedia
                    )
                }

                if (cloudBackups.isNotEmpty()) {
                    CloudBackupInfoSheet(
                        sheetState = backupSheetState,
                        backups = cloudBackups
                    )
                }

                pendingPerson?.let { person ->
                    ConfirmationSheet(
                        state = removePersonConfirmState,
                        title = pluralStringResource(
                            R.plurals.cloud_person_remove_media_title,
                            1,
                            1,
                            person.name.ifBlank { unknownPersonText }
                        ),
                        summary = stringResource(R.string.cloud_person_remove_media_summary),
                        confirmText = stringResource(R.string.cloud_person_remove_media_confirm),
                        onConfirm = {
                            mediaViewViewModel.removeMediaFromPerson(person.id, currentMedia.id)
                            pendingPerson = null
                            scope.launch { removePersonConfirmState.hide() }
                        }
                    )
                }

            }
        }

        if (albumsState.value.albums.isNotEmpty()) {
            MoveMediaSheet(
                sheetState = moveSheetState,
                mediaList = listOfNotNull(currentMedia),
                albumState = albumsState,
                onFinish = { }
            )
        }
    }
}
