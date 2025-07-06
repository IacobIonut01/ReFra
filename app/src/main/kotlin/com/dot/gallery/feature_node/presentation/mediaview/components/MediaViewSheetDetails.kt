/*
 * SPDX-FileCopyrightText: 2023 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.mediaview.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GpsOff
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.toUpperCase
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.R
import com.dot.gallery.core.Constants.Animation.enterAnimation
import com.dot.gallery.core.Constants.Animation.exitAnimation
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.LocalMediaHandler
import com.dot.gallery.core.Settings.Misc.rememberAllowBlur
import com.dot.gallery.core.navigate
import com.dot.gallery.core.presentation.components.DragHandle
import com.dot.gallery.core.presentation.components.NavigationBarSpacer
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
import com.dot.gallery.feature_node.domain.util.isEncrypted
import com.dot.gallery.feature_node.domain.util.isRaw
import com.dot.gallery.feature_node.domain.util.isTrashed
import com.dot.gallery.feature_node.domain.util.readUriOnly
import com.dot.gallery.feature_node.presentation.exif.MetadataEditSheet
import com.dot.gallery.feature_node.presentation.mediaview.rememberedDerivedState
import com.dot.gallery.feature_node.presentation.util.LocalHazeState
import com.dot.gallery.feature_node.presentation.util.Screen
import com.dot.gallery.feature_node.presentation.util.printDebug
import com.dot.gallery.feature_node.presentation.util.rememberActivityResult
import com.dot.gallery.feature_node.presentation.util.rememberAppBottomSheetState
import com.dot.gallery.feature_node.presentation.util.rememberMediaInfo
import com.dot.gallery.feature_node.presentation.util.writeRequest
import com.github.panpf.sketch.rememberAsyncImagePainter
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import kotlinx.coroutines.launch

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun <T : Media> MediaViewSheetDetails(
    albumsState: State<AlbumState>,
    vaultState: State<VaultState>,
    metadataState: State<MediaMetadataState>,
    currentMedia: T?,
    addMediaToVault: (Vault, T) -> Unit,
    restoreMedia: ((Vault, T, () -> Unit) -> Unit)?,
    currentVault: Vault?
) {
    val handler = LocalMediaHandler.current
    val isBlurEnabled by rememberAllowBlur()
    val surfaceColor = MaterialTheme.colorScheme.surface
    val surfaceColorVariant = MaterialTheme.colorScheme.surfaceVariant
    val sheetBackgroundColor by animateColorAsState(
        if (isBlurEnabled) surfaceColor.copy(alpha = 0.6f) else surfaceColor
    )
    val sheetCardBackgroundColor by animateColorAsState(
        if (isBlurEnabled) surfaceColor.copy(alpha = 0.6f) else surfaceColorVariant
    )
    val sheetCardBackgroundModifier = remember(isBlurEnabled) {
        if (!isBlurEnabled) {
            Modifier.background(
                color = sheetCardBackgroundColor,
                shape = RoundedCornerShape(16.dp)
            )
        } else {
            Modifier
        }
    }
    val sheetHazeStyle = HazeMaterials.thin(
        containerColor = surfaceColor
    )
    val sheetCardHazeStyle = HazeMaterials.thick(
        containerColor = surfaceColor
    )

    val sheetCardButtonHazeStyle = HazeMaterials.thin(
        containerColor = surfaceColorVariant
    )
    val sheetBackgroundModifier = remember(isBlurEnabled) {
        if (!isBlurEnabled) {
            Modifier.background(
                color = sheetBackgroundColor,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            )
        } else {
            Modifier
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(
                RoundedCornerShape(
                    topStart = 24.dp,
                    topEnd = 24.dp
                )
            )
            .then(sheetBackgroundModifier)
            .hazeEffect(
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
                val metadata by rememberedDerivedState(metadataState.value, currentMedia) {
                    metadataState.value.metadata.firstOrNull { it.mediaId == currentMedia.id }
                }

                /**
                 * -1 - none
                 * 0 - delete all
                 * 1 - delete location
                 */
                var exifDeleteMode by rememberSaveable {
                    mutableIntStateOf(-1)
                }
                val exifAttributesEditResult = rememberActivityResult(
                    onResultOk = {
                        scope.launch {
                            handler.let {
                                when (exifDeleteMode) {
                                    0 -> {
                                        if (it.deleteMediaMetadata(currentMedia)) {
                                            printDebug("Exif Attributes Updated")
                                        } else {
                                            Toast.makeText(
                                                context,
                                                "Exif Update failed",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }

                                    1 -> {
                                        if (it.deleteMediaGPSMetadata(currentMedia)) {
                                            printDebug("Exif Attributes Updated")
                                        } else {
                                            Toast.makeText(
                                                context,
                                                "Exif Update failed",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }

                                    else -> {
                                        printDebug("No Exif Attributes Updated")
                                    }
                                }
                                exifDeleteMode = -1
                            }
                        }
                    }
                )

                val dateCaption = rememberMediaDateCaption(metadata, currentMedia)
                val metadataSheetState = rememberAppBottomSheetState()
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

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .then(sheetCardBackgroundModifier)
                                .hazeEffect(
                                    state = LocalHazeState.current,
                                    style = sheetCardHazeStyle
                                )
                                .padding(16.dp)
                        ) {
                            DateHeader(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        enabled = !currentMedia.readUriOnly,
                                        indication = null,
                                        interactionSource = remember {
                                            MutableInteractionSource()
                                        }
                                    ) {
                                        scope.launch {
                                            metadataSheetState.show()
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
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp)),
                                verticalArrangement = Arrangement.spacedBy(1.dp)
                            ) {
                                LocationItem(
                                    locationData = locationData
                                )
                                AnimatedVisibility(
                                    visible = currentMedia.canMakeActions
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(1.dp),
                                    ) {
                                        val sheetCardButtonBackgroundModifier =
                                            remember(isBlurEnabled) {
                                                if (!isBlurEnabled) {
                                                    Modifier.background(
                                                        color = surfaceColorVariant,
                                                        shape = RoundedCornerShape(2.dp)
                                                    )
                                                } else {
                                                    Modifier
                                                }
                                            }
                                        AnimatedVisibility(
                                            modifier = Modifier.weight(1f),
                                            visible = locationData != null
                                        ) {
                                            ListItem(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(2.dp))
                                                    .then(sheetCardButtonBackgroundModifier)
                                                    .hazeEffect(
                                                        state = LocalHazeState.current,
                                                        style = sheetCardButtonHazeStyle
                                                    )
                                                    .clickable {
                                                        scope.launch {
                                                            exifDeleteMode = 1
                                                            exifAttributesEditResult.launch(
                                                                currentMedia.writeRequest(
                                                                    context.contentResolver
                                                                )
                                                            )
                                                        }
                                                    },
                                                headlineContent = {
                                                    Text(
                                                        text = stringResource(R.string.delete_location),
                                                        fontSize = 12.sp
                                                    )
                                                },
                                                leadingContent = {
                                                    Icon(
                                                        imageVector = Icons.Outlined.GpsOff,
                                                        contentDescription = stringResource(R.string.delete_location)
                                                    )
                                                },
                                                colors = ListItemDefaults.colors(
                                                    containerColor = Color.Transparent,
                                                    headlineColor = MaterialTheme.colorScheme.onSurface,
                                                    leadingIconColor = MaterialTheme.colorScheme.onSurface
                                                )
                                            )
                                        }
                                        AnimatedVisibility(
                                            modifier = Modifier.weight(1f),
                                            visible = metadata?.lensDescription != null
                                        ) {
                                            ListItem(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(2.dp))
                                                    .then(sheetCardButtonBackgroundModifier)
                                                    .hazeEffect(
                                                        state = LocalHazeState.current,
                                                        style = sheetCardButtonHazeStyle
                                                    )
                                                    .clickable {
                                                        scope.launch {
                                                            exifDeleteMode = 0
                                                            exifAttributesEditResult.launch(
                                                                currentMedia.writeRequest(
                                                                    context.contentResolver
                                                                )
                                                            )
                                                        }
                                                    },
                                                headlineContent = {
                                                    Text(
                                                        text = stringResource(R.string.delete_metadata),
                                                        fontSize = 12.sp
                                                    )
                                                },
                                                leadingContent = {
                                                    Icon(
                                                        imageVector = Icons.Outlined.LocalFireDepartment,
                                                        contentDescription = stringResource(R.string.delete_metadata)
                                                    )
                                                },
                                                colors = ListItemDefaults.colors(
                                                    containerColor = Color.Transparent,
                                                    headlineColor = MaterialTheme.colorScheme.onSurface,
                                                    leadingIconColor = MaterialTheme.colorScheme.onSurface
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .then(sheetCardBackgroundModifier)
                                .hazeEffect(
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
                            if (category != null) {
                                val mediaCategoryCounter by handler.getClassifiedMediaCountAtCategory(
                                    category!!
                                ).collectAsStateWithLifecycle(0)
                                val mediaCategoryThumbnail by handler.getClassifiedMediaThumbnailByCategory(
                                    category!!
                                ).collectAsStateWithLifecycle(null)
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
                                    trailingContent = {
                                        AnimatedVisibility(
                                            visible = mediaCategoryThumbnail != null,
                                            enter = enterAnimation,
                                            exit = exitAnimation
                                        ) {
                                            Image(
                                                painter = rememberAsyncImagePainter(
                                                    mediaCategoryThumbnail!!.uri.toString()
                                                ),
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .clip(RoundedCornerShape(16.dp))
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
                    item {
                        MediaViewSheetActions(
                            media = currentMedia,
                            albumsState = albumsState,
                            vaults = vaultState,
                            addMedia = addMediaToVault,
                            restoreMedia = restoreMedia,
                            currentVault = currentVault
                        )
                    }
                    item {
                        NavigationBarSpacer()
                    }
                }

                if (metadataSheetState.isVisible) {
                    MetadataEditSheet(
                        state = metadataSheetState,
                        media = currentMedia,
                        metadata = metadata
                    )
                }
            }
        }
    }
}
