/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.mediaview.components

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.core.work.workScheduler
import com.dot.gallery.cloud.core.CloudUri
import com.dot.gallery.cloud.core.PersonInfo
import com.dot.gallery.cloud.ui.backup.CloudBackupInfoSheet
import com.dot.gallery.cloud.ui.descriptor.ProviderBrandIcon
import com.dot.gallery.cloud.ui.people.PersonAvatar
import com.dot.gallery.core.LocalMediaHandler
import com.dot.gallery.core.Settings
import com.dot.gallery.core.metadata.MetadataCategory
import com.dot.gallery.core.metadata.MetadataSaveMode
import com.dot.gallery.core.metadata.SanitizationResult
import com.dot.gallery.core.presentation.components.ConfirmationSheet
import com.dot.gallery.core.util.SdkCompat
import com.dot.gallery.core.workers.enqueueRedactedLocationRepair
import com.dot.gallery.data.model.MediaMetadataState
import com.dot.gallery.domain.model.AlbumState
import com.dot.gallery.domain.model.Media
import com.dot.gallery.domain.model.MediaDateCaption
import com.dot.gallery.domain.model.UriMedia
import com.dot.gallery.domain.model.Vault
import com.dot.gallery.domain.model.VaultState
import com.dot.gallery.domain.util.getUri
import com.dot.gallery.feature.viewer.ViewerSheetGroup
import com.dot.gallery.feature.viewer.ViewerSheetRow
import com.dot.gallery.feature.viewer.ViewerSheetSectionLabel
import com.dot.gallery.feature.viewer.viewerSheetPosition
import com.dot.gallery.feature.viewer.MediaInfoSheetCard
import com.dot.gallery.feature.viewer.MediaViewSheetDetails as FeatureMediaViewSheetDetails
import com.dot.gallery.feature.viewer.MetadataSanitizationUiPhase
import com.dot.gallery.core.presentation.util.rememberLocationData
import com.dot.gallery.app.domain.model.rememberMediaDateCaption
import com.dot.gallery.app.presentation.exif.CaptureDateEditSheet
import com.dot.gallery.feature.viewer.MediaViewViewModel
import com.dot.gallery.feature.viewer.components.media.MotionPhotoShotsSection
import com.dot.gallery.feature.viewer.components.media.MotionPhotoState
import com.dot.gallery.feature.viewer.components.video.VideoHdrInfo
import com.dot.gallery.core.presentation.util.Screen
import com.dot.gallery.app.presentation.util.StaticMapPreview
import com.dot.gallery.core.ui.util.matchesSha256
import com.dot.gallery.core.ui.util.rememberActivityResult
import com.dot.gallery.core.ui.util.trashRequest
import com.dot.gallery.ui.theme.isDarkTheme
import kotlinx.coroutines.launch
import com.dot.gallery.domain.util.getCategory
import com.dot.gallery.domain.util.isVideo
import com.dot.gallery.app.presentation.exif.MetadataEditSheet
import com.dot.gallery.app.presentation.exif.MoveMediaSheet
import com.dot.gallery.app.presentation.util.rememberMediaInfo
import com.dot.gallery.app.presentation.exif.MoveMediaSheet
import com.dot.gallery.core.ui.util.rememberedDerivedState
import com.dot.gallery.domain.util.readUriOnly
import com.dot.gallery.core.ui.util.rememberAppBottomSheetState
import com.dot.gallery.core.logging.printDebug
import com.dot.gallery.core.logging.printError
import com.dot.gallery.core.presentation.util.effectiveCartoBasemapKey
import com.dot.gallery.app.presentation.util.launchMap
import com.dot.gallery.core.rememberMediaViewerNavigate
import com.dot.gallery.injection.injectViewModel
import com.dot.gallery.BuildConfig
import com.dot.gallery.feature.viewer.components.LocationAccessItem
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.cloud_backed_up_tap_hint
import com.dot.gallery.generated.resources.cloud_backed_up_title
import com.dot.gallery.generated.resources.cloud_people
import com.dot.gallery.generated.resources.cloud_people_unknown
import com.dot.gallery.generated.resources.cloud_person_photo_count
import com.dot.gallery.generated.resources.cloud_person_remove_media
import com.dot.gallery.generated.resources.cloud_person_remove_media_confirm
import com.dot.gallery.generated.resources.cloud_person_remove_media_summary
import com.dot.gallery.generated.resources.cloud_person_remove_media_title
import com.dot.gallery.generated.resources.location_map_cd
import com.dot.gallery.generated.resources.remove_metadata_copy_success
import com.dot.gallery.generated.resources.remove_metadata_failed
import com.dot.gallery.generated.resources.remove_metadata_rolled_back
import com.dot.gallery.generated.resources.remove_metadata_success

/**
 * App-side host for the viewer's info/details sheet: keeps the legacy call signature
 * and feeds the shared `feature.viewer` implementation with app-resolved inputs
 * (metadata, people, cloud backups, map tiles, edit sheets, capability gates).
 */
@Composable
fun <T : Media> MediaViewSheetDetails(
    albumsState: State<AlbumState>,
    vaultState: State<VaultState>,
    metadataState: State<MediaMetadataState>,
    currentMedia: T?,
    restoreMedia: ((Vault, T, () -> Unit) -> Unit)?,
    currentVault: Vault?,
    motionPhotoState: MotionPhotoState? = null,
    videoHdr: VideoHdrInfo = VideoHdrInfo.NONE,
    onOpenFramePicker: () -> Unit = {},
    cloudBackups: List<UriMedia> = emptyList(),
    onOpenPersonTimeline: (PersonInfo) -> Unit = {},
    metadataSanitizationState: MediaViewViewModel.MetadataSanitizationUiState =
        MediaViewViewModel.MetadataSanitizationUiState.Idle,
    probeMetadataSanitization: (Media) -> Unit = {},
    sanitizeMetadata: (Media, Set<MetadataCategory>, MetadataSaveMode) -> Unit = { _, _, _ -> },
    resetMetadataSanitization: () -> Unit = {},
) {
    val metadata by rememberedDerivedState(metadataState.value, currentMedia) {
        currentMedia?.id?.let { metadataState.value.metadataMap[it] }
    }
    val handler = LocalMediaHandler.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val navigateFromViewer = rememberMediaViewerNavigate()

    // Hoisted above the media-gated content below: a move that removes the current
    // item (e.g. the last photo of the viewed album) would otherwise dispose the
    // sheet mid-operation together with its completion state. MoveMediaSheet itself
    // freezes mediaList while visible.
    val moveSheetState = rememberAppBottomSheetState()
    val metadataSheetState = rememberAppBottomSheetState()
    val captureDateSheetState = rememberAppBottomSheetState()
    val backupSheetState = rememberAppBottomSheetState()
    val removePersonConfirmState = rememberAppBottomSheetState()
    var pendingPerson by remember { mutableStateOf<PersonInfo?>(null) }

    // On-device persons whose faces were detected in this media — emitted
    // live so the people card and its remove flow stay in sync with removals.
    val mediaViewViewModel = injectViewModel<MediaViewViewModel> {mediaViewViewModel}
    val mediaPeople by produceState<List<PersonInfo>>(emptyList(), currentMedia?.id) {
        val mediaId = currentMedia?.id
        if (mediaId != null) {
            mediaViewViewModel.peopleForMedia(mediaId).collect { value = it }
        }
    }
    val unknownPersonText = stringResource(Res.string.cloud_people_unknown)
    val personPhotoCountFormat = stringResource(Res.string.cloud_person_photo_count)

    val locationData = rememberLocationData(metadata)
    var category by remember(currentMedia) {
        mutableStateOf(currentMedia?.getCategory)
    }
    LaunchedEffect(currentMedia, category, handler) {
        if (category == null && currentMedia != null) {
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

    // Map preview slot — StaticMapPreview stays app-side (Glide tiles + CARTO key).
    val mapsEnabled = remember { BuildConfig.MAPS_ENABLED }
    val mapAppearance by Settings.Misc.rememberMapAppearance()
    val userCartoKey by Settings.Misc.rememberCartoBasemapKey()
    val effectiveAppIsDark = isDarkTheme()
    val cartoKey = remember(userCartoKey) {
        effectiveCartoBasemapKey(BuildConfig.CARTO_BASEMAP_KEY, userCartoKey)
    }
    val mapContentDescription = stringResource(Res.string.location_map_cd)
    val mapPreview: (@Composable (Modifier) -> Unit)? =
        if (mapsEnabled && locationData != null) {
            { previewModifier ->
                StaticMapPreview(
                    latitude = locationData.latitude,
                    longitude = locationData.longitude,
                    appearance = mapAppearance,
                    effectiveAppIsDark = effectiveAppIsDark,
                    zoom = 12,
                    apiKey = cartoKey,
                    contentDescription = mapContentDescription,
                    modifier = previewModifier,
                )
            }
        } else {
            null
        }

    // Metadata sanitization — the shared phase mirrors the ViewModel state while the
    // completion side-effects (toasts, REPLACE_ORIGINAL trash request) stay app-side.
    val sanitizationPhase = when (val state = metadataSanitizationState) {
        is MediaViewViewModel.MetadataSanitizationUiState.Idle ->
            MetadataSanitizationUiPhase.Idle
        is MediaViewViewModel.MetadataSanitizationUiState.Probing ->
            MetadataSanitizationUiPhase.Probing
        is MediaViewViewModel.MetadataSanitizationUiState.Running ->
            MetadataSanitizationUiPhase.Running
        is MediaViewViewModel.MetadataSanitizationUiState.Ready ->
            MetadataSanitizationUiPhase.Ready(state.mediaId, state.capability)
        is MediaViewViewModel.MetadataSanitizationUiState.Complete ->
            MetadataSanitizationUiPhase.Complete(state.mediaId)
    }
    val metadataRemovalSuccessText = stringResource(Res.string.remove_metadata_success)
    val metadataRemovalCopySuccessText = stringResource(Res.string.remove_metadata_copy_success)
    val metadataRemovalRolledBackText = stringResource(Res.string.remove_metadata_rolled_back)
    val metadataRemovalFailedText = stringResource(Res.string.remove_metadata_failed)
    val metadataRemovalPermissionResult = rememberActivityResult(
        onResultCanceled = {
            Toast.makeText(context, metadataRemovalCopySuccessText, Toast.LENGTH_SHORT).show()
            resetMetadataSanitization()
        },
        onResultOk = {
            Toast.makeText(context, metadataRemovalSuccessText, Toast.LENGTH_SHORT).show()
            resetMetadataSanitization()
        }
    )

    FeatureMediaViewSheetDetails(
        metadata = metadata,
        currentMedia = currentMedia,
        dateCaption = if (currentMedia != null) {
            rememberMediaDateCaption(metadata, currentMedia)
        } else {
            remember { MediaDateCaption(date = "", description = "") }
        },
        mediaInfoList = if (currentMedia != null) {
            rememberMediaInfo(
                media = currentMedia,
                exifMetadata = metadata,
                videoHdr = videoHdr,
                onLabelClick = {
                    if (!currentMedia.readUriOnly) {
                        scope.launch {
                            metadataSheetState.show()
                        }
                    }
                }
            )
        } else {
            emptyList()
        },
        locationData = locationData,
        hasAlbums = albumsState.value.albums.isNotEmpty(),
        canReplaceOriginal = SdkCompat.supportsTrash,
        metadataSheetState = metadataSheetState,
        captureDateSheetState = captureDateSheetState,
        moveSheetState = moveSheetState,
        sanitizationPhase = sanitizationPhase,
        probeMetadataSanitization = { media -> probeMetadataSanitization(media) },
        sanitizeMetadata = { media, categories, saveMode -> sanitizeMetadata(media, categories, saveMode) },
        onSanitizationComplete = { _ ->
            val result = (metadataSanitizationState as?
                MediaViewViewModel.MetadataSanitizationUiState.Complete)?.result
            scope.launch {
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
                    resetMetadataSanitization()
                }
            }
        },
        onResetSanitization = resetMetadataSanitization,
        onShowLocationInApp = { media ->
            navigateFromViewer(Screen.LocationsScreen.withMediaId(media.id))
        },
        onViewAllMetadata = { media ->
            navigateFromViewer(
                Screen.MetadataViewScreen.uriAndType(
                    mediaUri = media.getUri().toString(),
                    isVideo = media.isVideo
                )
            )
        },
        onOpenCategory = { name ->
            navigateFromViewer(Screen.CategoryViewScreen.category(name))
        },
        category = category,
        categoryItemCount = mediaCategoryCounter,
        categoryThumbnail = mediaCategoryThumbnail?.uri,
        mapPreview = mapPreview,
        onOpenMap = { lat, lng -> context.launchMap(lat, lng) },
        locationAccessPrompt = { rowModifier ->
            LocationAccessItem(
                modifier = rowModifier,
                onGranted = {
                    scope.launch {
                        runCatching { currentMedia?.let { handler.collectMetadataFor(it) } }
                            .onFailure {
                                printError("ui.mediaview", "post-grant metadata re-parse failed", it)
                            }
                        runCatching { workScheduler().enqueueRedactedLocationRepair() }
                            .onFailure {
                                printError("ui.mediaview", "redacted location repair enqueue failed", it)
                            }
                    }
                }
            )
        },
        // The section only draws once the filmstrip decodes; emitting the card slot
        // unconditionally leaves an empty card on every non-motion photo.
        motionPhotoContent = if (motionPhotoState?.compositeFilmstrip != null) {
            { cardModifier ->
                MediaInfoSheetCard(
                    modifier = cardModifier,
                    innerPadding = Modifier.padding(16.dp)
                ) {
                    MotionPhotoShotsSection(
                        state = motionPhotoState,
                        onOpenFramePicker = onOpenFramePicker,
                    )
                }
            }
        } else {
            null
        },
        preInfoItems = {
            if (cloudBackups.isNotEmpty()) {
                item(key = "cloud_backup") {
                    val backupProviders = remember(cloudBackups) {
                        cloudBackups
                            .mapNotNull {
                                CloudUri.parse(it.uri.toString())?.providerType
                            }
                            .distinct()
                    }
                    ViewerSheetRow(
                        title = stringResource(Res.string.cloud_backed_up_title),
                        summary = stringResource(Res.string.cloud_backed_up_tap_hint),
                        icon = Icons.Outlined.CloudDone,
                        onClick = {
                            scope.launch { backupSheetState.show() }
                        },
                        trailing = {
                            Row(
                                modifier = Modifier.padding(end = 8.dp),
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
                        }
                    )
                }
            }
            if (mediaPeople.isNotEmpty()) {
                item(key = "people") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        ViewerSheetSectionLabel(
                            text = stringResource(Res.string.cloud_people),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        ViewerSheetGroup {
                            mediaPeople.forEachIndexed { index, person ->
                                ViewerSheetRow(
                                    title = person.name.ifBlank { unknownPersonText },
                                    summary = personPhotoCountFormat.format(person.assetCount),
                                    position = viewerSheetPosition(index, mediaPeople.size),
                                    summaryMaxLines = 1,
                                    onClick = { onOpenPersonTimeline(person) },
                                    leading = {
                                        PersonAvatar(person = person, size = 40.dp)
                                    },
                                    trailing = {
                                        IconButton(
                                            onClick = {
                                                pendingPerson = person
                                                scope.launch {
                                                    removePersonConfirmState.show()
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.PersonRemove,
                                                contentDescription = stringResource(
                                                    Res.string.cloud_person_remove_media
                                                ),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        metadataEditSheet = { state ->
            currentMedia?.let {
                MetadataEditSheet(
                    state = state,
                    media = it,
                    metadata = metadata
                )
            }
        },
        captureDateSheet = { state ->
            currentMedia?.let {
                CaptureDateEditSheet(
                    state = state,
                    media = it
                )
            }
        },
        moveSheet = { state ->
            MoveMediaSheet(
                sheetState = state,
                mediaList = listOfNotNull(currentMedia),
                albumState = albumsState,
                onFinish = { }
            )
        },
        actionsContent = { media, sheet ->
            MediaViewSheetActions(
                media = media,
                albumsState = albumsState,
                vaults = vaultState,
                restoreMedia = restoreMedia,
                currentVault = currentVault,
                moveSheetState = sheet,
                isMotionPhoto = motionPhotoState?.isDetected == true ||
                    metadata?.isMotionPhoto == true,
                onOpenFramePicker = onOpenFramePicker,
            )
        },
        logDebug = ::printDebug,
    )

    // App-owned sheets attached to this sheet's state: cloud backup detail + the
    // remove-person confirmation.
    if (cloudBackups.isNotEmpty()) {
        CloudBackupInfoSheet(
            sheetState = backupSheetState,
            backups = cloudBackups
        )
    }
    pendingPerson?.let { person ->
        val personMediaId = currentMedia?.id
        ConfirmationSheet(
            state = removePersonConfirmState,
            title = pluralStringResource(
                Res.plurals.cloud_person_remove_media_title,
                1,
                1,
                person.name.ifBlank { unknownPersonText }
            ),
            summary = stringResource(Res.string.cloud_person_remove_media_summary),
            confirmText = stringResource(Res.string.cloud_person_remove_media_confirm),
            onConfirm = {
                if (personMediaId != null) {
                    mediaViewViewModel.removeMediaFromPerson(person.id, personMediaId)
                }
                pendingPerson = null
                scope.launch { removePersonConfirmState.hide() }
            }
        )
    }
}
