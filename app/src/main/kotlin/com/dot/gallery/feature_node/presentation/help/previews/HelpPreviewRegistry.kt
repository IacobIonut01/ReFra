/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.help.previews

import android.graphics.Color as AndroidColor
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.core.graphics.createBitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dot.gallery.R
import com.dot.gallery.core.presentation.components.FilterKind
import com.dot.gallery.core.presentation.components.FilterOption
import com.dot.gallery.feature_node.domain.model.MediaState
import com.dot.gallery.feature_node.domain.model.Vault
import com.dot.gallery.feature_node.domain.model.VaultState
import kotlinx.coroutines.flow.MutableStateFlow
import com.dot.gallery.feature_node.presentation.albums.AlbumsScreen
import com.dot.gallery.feature_node.presentation.classifier.CategoriesScreen
import com.dot.gallery.feature_node.presentation.exif.MetadataViewScreen
import com.dot.gallery.feature_node.presentation.edit.EditScreen2
import com.dot.gallery.feature_node.domain.model.editor.DrawMode
import com.dot.gallery.feature_node.domain.model.editor.DrawType
import com.dot.gallery.feature_node.domain.model.editor.PathProperties
import com.dot.gallery.feature_node.presentation.mediaview.MediaViewScreen
import com.dot.gallery.feature_node.presentation.vault.VaultDisplay
import com.dot.gallery.feature_node.presentation.favorites.FavoriteScreen
import com.dot.gallery.feature_node.presentation.help.data.HelpMockData
import com.dot.gallery.feature_node.presentation.help.data.PreviewType
import com.dot.gallery.feature_node.presentation.location.ListLocationsContent
import com.dot.gallery.feature_node.presentation.settings.SettingsScreen
import com.dot.gallery.feature_node.presentation.settings.subsettings.ColorPaletteScreen
import com.dot.gallery.feature_node.presentation.timeline.TimelineScreen
import com.dot.gallery.feature_node.presentation.trashed.TrashedGridScreen
import com.dot.gallery.ui.core.Icons as GalleryIcons
import com.dot.gallery.ui.core.icons.Albums

/**
 * Central dispatcher that maps a [PreviewType] to the corresponding
 * mini preview composable. All previews render inside a clipped box.
 *
 * Tier 1 previews use real screen composables ([TimelineScreen],
 * [AlbumsScreen], [FavoriteScreen], [TrashedGridScreen], [SettingsScreen])
 * wrapped in [PreviewScreenProvider] which provides mocked data and
 * [SharedTransitionScope] + [AnimatedContentScope].
 *
 * Tier 2/3 previews use simplified mockups for components that are
 * too heavy to render in a help screen context (editor, vault, etc.).
 */
@Composable
fun HelpPreview(
    type: PreviewType,
    modifier: Modifier = Modifier
) {
    when (type) {
        PreviewType.TIMELINE_GRID -> TimelinePreviewMini(modifier)
        PreviewType.ALBUM_GRID -> AlbumGridPreviewMini(modifier)
        PreviewType.FAVORITES_GRID -> FavoritesPreviewMini(modifier)
        PreviewType.TRASH_GRID -> TrashPreviewMini(modifier)
        PreviewType.SEARCH_BAR -> SearchBarPreviewMini(modifier)
        PreviewType.AI_SEARCH -> SearchBarPreviewMini(modifier)
        PreviewType.AI_CATEGORIES -> CategoriesPreviewMini(modifier)
        PreviewType.MEDIA_VIEWER -> ViewerPreviewMini(modifier)
        PreviewType.EXIF_VIEWER -> ExifPreviewMini(modifier)
        PreviewType.PHOTO_EDITOR_CROP -> EditorCropPreviewMini(modifier)
        PreviewType.PHOTO_EDITOR_FILTERS -> EditorFiltersPreviewMini(modifier)
        PreviewType.PHOTO_EDITOR_MARKUP -> EditorMarkupPreviewMini(modifier)
        PreviewType.VAULT_LOCK -> VaultPreviewMini(modifier)
        PreviewType.THEME_PICKER -> ThemePickerPreviewMini(modifier)
        PreviewType.COLOR_PALETTE -> ColorPalettePreviewMini(modifier)
        PreviewType.PINCH_ZOOM_GRID -> PinchZoomPreviewMini(modifier)
        PreviewType.LOCATION_MAP -> LocationMapPreviewMini(modifier)
        PreviewType.NAV_BAR_PREVIEW -> NavBarPreviewMini(modifier)
        PreviewType.SETTINGS_GENERAL -> SettingsPreviewMini(modifier)
        PreviewType.COLLECTION_VIEW -> AlbumGridPreviewMini(modifier)
        PreviewType.NONE -> {}
    }
}

// ── Tier 1: Real screen composables via PreviewScreenProvider ──

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun TimelinePreviewMini(modifier: Modifier = Modifier) {
    val animation = rememberPreviewAnimation(stepCount = 2)
    PreviewFrame(modifier, applyPadding = false) {
        AutoScrollBox(
            scrollProgress = animation.stepProgress,
            modifier = Modifier.matchParentSize()
        ) {
            PreviewScreenProvider { sharedScope, animScope ->
                TimelineScreen(
                    paddingValues = PaddingValues(0.dp),
                    isScrolling = remember { mutableStateOf(false) },
                    mediaState = remember { mutableStateOf(HelpMockData.MOCK_MEDIA_STATE) },
                    metadataState = remember { mutableStateOf(HelpMockData.MOCK_METADATA_STATE) },
                    sharedTransitionScope = sharedScope,
                    animatedContentScope = animScope,
                )
            }
        }
        SwipeGestureOverlay(
            modifier = Modifier.matchParentSize(),
            progress = animation.stepProgress,
            direction = SwipeDirection.UP
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun AlbumGridPreviewMini(modifier: Modifier = Modifier) {
    val animation = rememberPreviewAnimation(stepCount = 2)
    PreviewFrame(modifier, applyPadding = false) {
        AutoScrollBox(
            scrollProgress = animation.stepProgress,
            modifier = Modifier.matchParentSize()
        ) {
            PreviewScreenProvider { sharedScope, animScope ->
                AlbumsScreen(
                    filterOptions = remember {
                        mutableStateListOf(
                            FilterOption(titleRes = R.string.filter_type_date, filterKind = FilterKind.DATE),
                            FilterOption(titleRes = R.string.filter_type_date, filterKind = FilterKind.DATE_MODIFIED),
                            FilterOption(titleRes = R.string.filter_type_name, filterKind = FilterKind.NAME),
                        )
                    },
                    isScrolling = remember { mutableStateOf(false) },
                    onAlbumClick = {},
                    onAlbumLongClick = {},
                    onMoveAlbumToTrash = { _, _ -> },
                    onIgnoreAlbum = {},
                    onLockAlbum = {},
                    sharedTransitionScope = sharedScope,
                    animatedContentScope = animScope,
                )
            }
        }
        SwipeGestureOverlay(
            modifier = Modifier.matchParentSize(),
            progress = animation.stepProgress,
            direction = SwipeDirection.UP
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun FavoritesPreviewMini(modifier: Modifier = Modifier) {
    val animation = rememberPreviewAnimation(stepCount = 2)
    PreviewFrame(modifier, applyPadding = false) {
        AutoScrollBox(
            scrollProgress = animation.stepProgress,
            modifier = Modifier.matchParentSize()
        ) {
            PreviewScreenProvider { sharedScope, animScope ->
                FavoriteScreen(
                    paddingValues = PaddingValues(0.dp),
                    mediaState = remember { mutableStateOf(HelpMockData.MOCK_FAVORITES_STATE) },
                    metadataState = remember { mutableStateOf(HelpMockData.MOCK_METADATA_STATE) },
                    clearSelection = {},
                    sharedTransitionScope = sharedScope,
                    animatedContentScope = animScope,
                )
            }
        }
        SwipeGestureOverlay(
            modifier = Modifier.matchParentSize(),
            progress = animation.stepProgress,
            direction = SwipeDirection.UP
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun TrashPreviewMini(modifier: Modifier = Modifier) {
    val animation = rememberPreviewAnimation(stepCount = 2)
    PreviewFrame(modifier, applyPadding = false) {
        AutoScrollBox(
            scrollProgress = animation.stepProgress,
            modifier = Modifier.matchParentSize()
        ) {
            PreviewScreenProvider { sharedScope, animScope ->
                TrashedGridScreen(
                    paddingValues = PaddingValues(0.dp),
                    mediaState = remember { mutableStateOf(HelpMockData.MOCK_TRASH_STATE) },
                    metadataState = remember { mutableStateOf(HelpMockData.MOCK_METADATA_STATE) },
                    clearSelection = {},
                    sharedTransitionScope = sharedScope,
                    animatedContentScope = animScope,
                )
            }
        }
        SwipeGestureOverlay(
            modifier = Modifier.matchParentSize(),
            progress = animation.stepProgress,
            direction = SwipeDirection.UP
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun PinchZoomPreviewMini(modifier: Modifier = Modifier) {
    val animation = rememberPreviewAnimation(stepCount = 2)
    PreviewFrame(modifier, applyPadding = false) {
        PreviewScreenProvider { sharedScope, animScope ->
            TimelineScreen(
                paddingValues = PaddingValues(0.dp),
                isScrolling = remember { mutableStateOf(false) },
                mediaState = remember { mutableStateOf(HelpMockData.MOCK_MEDIA_STATE) },
                metadataState = remember { mutableStateOf(HelpMockData.MOCK_METADATA_STATE) },
                sharedTransitionScope = sharedScope,
                animatedContentScope = animScope,
            )
        }
        PinchGestureOverlay(
            modifier = Modifier.matchParentSize(),
            progress = animation.stepProgress
        )
    }
}

// ── Tier 2: Real renderer + simplified sub-components ──

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SearchBarPreviewMini(modifier: Modifier = Modifier) {
    val animation = rememberPreviewAnimation(stepCount = 2)
    PreviewFrame(modifier, applyPadding = false) {
        AutoScrollBox(
            scrollProgress = animation.stepProgress,
            modifier = Modifier.matchParentSize()
        ) {
            PreviewScreenProvider { sharedScope, animScope ->
                TimelineScreen(
                    paddingValues = PaddingValues(0.dp),
                    isScrolling = remember { mutableStateOf(false) },
                    mediaState = remember { mutableStateOf(HelpMockData.MOCK_MEDIA_STATE) },
                    metadataState = remember { mutableStateOf(HelpMockData.MOCK_METADATA_STATE) },
                    sharedTransitionScope = sharedScope,
                    animatedContentScope = animScope,
                )
            }
        }
        SwipeGestureOverlay(
            modifier = Modifier.matchParentSize(),
            progress = animation.stepProgress,
            direction = SwipeDirection.UP
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun CategoriesPreviewMini(modifier: Modifier = Modifier) {
    val animation = rememberPreviewAnimation(stepCount = 2)
    PreviewFrame(modifier, applyPadding = false) {
        AutoScrollBox(
            scrollProgress = animation.stepProgress,
            modifier = Modifier.matchParentSize()
        ) {
            PreviewScreenProvider { sharedScope, animScope ->
                CategoriesScreen(
                    categoriesWithCount = remember { HelpMockData.MOCK_CATEGORIES_WITH_COUNT },
                    mediaState = remember { MediaState() },
                    sharedTransitionScope = sharedScope,
                    animatedContentScope = animScope,
                )
            }
        }
        SwipeGestureOverlay(
            modifier = Modifier.matchParentSize(),
            progress = animation.stepProgress,
            direction = SwipeDirection.UP
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun ViewerPreviewMini(modifier: Modifier = Modifier) {
    val animation = rememberPreviewAnimation(stepCount = 2)
    PreviewFrame(modifier, applyPadding = false) {
        AutoScrollBox(
            scrollProgress = animation.stepProgress,
            horizontal = true,
            modifier = Modifier.matchParentSize()
        ) {
            PreviewScreenProvider { sharedScope, animScope ->
                MediaViewScreen(
                    toggleRotate = {},
                    paddingValues = PaddingValues(0.dp),
                    isStandalone = true,
                    mediaId = remember { HelpMockData.MOCK_PHOTOS.first().id },
                    mediaState = remember { mutableStateOf(HelpMockData.MOCK_MEDIA_STATE) },
                    metadataState = remember { mutableStateOf(HelpMockData.MOCK_METADATA_STATE) },
                    albumsState = remember { mutableStateOf(HelpMockData.MOCK_ALBUM_STATE) },
                    vaultState = remember { mutableStateOf(VaultState()) },
                    sharedTransitionScope = sharedScope,
                    animatedContentScope = animScope,
                )
            }
        }
        SwipeGestureOverlay(
            modifier = Modifier.matchParentSize(),
            progress = animation.stepProgress,
            direction = SwipeDirection.LEFT
        )
    }
}

@Composable
private fun ExifPreviewMini(modifier: Modifier = Modifier) {
    val animation = rememberPreviewAnimation(stepCount = 2)
    PreviewFrame(modifier, applyPadding = false) {
        AutoScrollBox(
            scrollProgress = animation.stepProgress,
            modifier = Modifier.matchParentSize()
        ) {
            PreviewScreenProvider { _, _ ->
                MetadataViewScreen(
                    state = remember { HelpMockData.MOCK_METADATA_VIEW_STATE },
                )
            }
        }
        SwipeGestureOverlay(
            modifier = Modifier.matchParentSize(),
            progress = animation.stepProgress,
            direction = SwipeDirection.UP
        )
    }
}

// ── Tier 3: Editor screens (EditScreen2 is fully parameterized) ──

@Composable
private fun EditorPreviewMini(modifier: Modifier = Modifier) {
    val mockBitmap = remember {
        createBitmap(400, 300).apply {
            eraseColor(AndroidColor.DKGRAY)
        }
    }
    val animation = rememberPreviewAnimation(stepCount = 3)
    PreviewFrame(modifier, applyPadding = false) {
        EditScreen2(
            currentImage = mockBitmap,
            targetImage = mockBitmap,
            targetUri = null,
            currentPosition = Offset.Unspecified,
            paths = emptyList(),
            pathsUndone = emptyList(),
            previousPosition = Offset.Unspecified,
            drawMode = DrawMode.Draw,
            drawType = DrawType.Stylus,
            currentPathProperty = PathProperties(),
            currentPath = Path(),
            onClose = {},
            onOverride = {},
            onSaveCopy = {},
            onAdjustItemLongClick = {},
            onAdjustmentChange = {},
            onAdjustmentPreview = {},
            onToggleFilter = {},
            removeLast = {},
            onCropRect = {},
            addPath = { _, _ -> },
            clearPathsUndone = {},
            setCurrentPosition = {},
            setPreviousPosition = {},
            setDrawMode = {},
            setDrawType = {},
            setCurrentPath = {},
            setCurrentPathProperty = {},
            applyDrawing = { _, _ -> },
            undoLastPath = {},
            redoLastPath = {},
        )
        val tapPositions = remember {
            listOf(
                0.2f to 0.95f,
                0.5f to 0.95f,
                0.8f to 0.95f,
            )
        }
        val (tapX, tapY) = tapPositions[animation.currentStep]
        TapGestureOverlay(
            modifier = Modifier.matchParentSize(),
            progress = animation.stepProgress,
            tapX = tapX, tapY = tapY
        )
    }
}

@Composable
private fun EditorCropPreviewMini(modifier: Modifier = Modifier) {
    EditorPreviewMini(modifier)
}

@Composable
private fun EditorFiltersPreviewMini(modifier: Modifier = Modifier) {
    EditorPreviewMini(modifier)
}

@Composable
private fun EditorMarkupPreviewMini(modifier: Modifier = Modifier) {
    EditorPreviewMini(modifier)
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun VaultPreviewMini(modifier: Modifier = Modifier) {
    val mockVault = remember { Vault(name = "My Vault") }
    val mockVaultState = remember {
        mutableStateOf(VaultState(vaults = listOf(mockVault), isLoading = false))
    }
    val mockCurrentVault = remember { MutableStateFlow<Vault?>(mockVault) }
    val mockMediaStateFlow = remember { MutableStateFlow(HelpMockData.MOCK_MEDIA_STATE) }
    val animation = rememberPreviewAnimation(stepCount = 2)
    PreviewFrame(modifier, applyPadding = false) {
        AutoScrollBox(
            scrollProgress = animation.stepProgress,
            modifier = Modifier.matchParentSize()
        ) {
            PreviewScreenProvider { sharedScope, animScope ->
                VaultDisplay(
                    globalNavigateUp = {},
                    vaultState = mockVaultState,
                    currentVault = mockCurrentVault,
                    createMediaState = { mockMediaStateFlow },
                    onCreateVaultClick = {},
                    deleteLeftovers = { _, _ -> },
                    setVault = {},
                    deleteVault = {},
                    restoreVault = {},
                    workerProgress = remember { MutableStateFlow(0f) },
                    workerIsRunning = remember { MutableStateFlow(false) },
                    sharedTransitionScope = sharedScope,
                    animatedContentScope = animScope,
                    metadataState = remember { mutableStateOf(HelpMockData.MOCK_METADATA_STATE) },
                )
            }
        }
        SwipeGestureOverlay(
            modifier = Modifier.matchParentSize(),
            progress = animation.stepProgress,
            direction = SwipeDirection.UP
        )
    }
}

@Composable
private fun ThemePickerPreviewMini(modifier: Modifier = Modifier) {
    val animation = rememberPreviewAnimation(stepCount = 2)
    PreviewFrame(modifier, applyPadding = false) {
        AutoScrollBox(
            scrollProgress = animation.stepProgress,
            modifier = Modifier.matchParentSize()
        ) {
            PreviewScreenProvider { _, _ ->
                ColorPaletteScreen()
            }
        }
        SwipeGestureOverlay(
            modifier = Modifier.matchParentSize(),
            progress = animation.stepProgress,
            direction = SwipeDirection.UP
        )
    }
}

@Composable
private fun ColorPalettePreviewMini(modifier: Modifier = Modifier) {
    val animation = rememberPreviewAnimation(stepCount = 2)
    PreviewFrame(modifier, applyPadding = false) {
        AutoScrollBox(
            scrollProgress = animation.stepProgress,
            modifier = Modifier.matchParentSize()
        ) {
            PreviewScreenProvider { _, _ ->
                ColorPaletteScreen()
            }
        }
        SwipeGestureOverlay(
            modifier = Modifier.matchParentSize(),
            progress = animation.stepProgress,
            direction = SwipeDirection.UP
        )
    }
}

@Composable
private fun LocationMapPreviewMini(modifier: Modifier = Modifier) {
    val animation = rememberPreviewAnimation(stepCount = 2)
    PreviewFrame(modifier, applyPadding = false) {
        AutoScrollBox(
            scrollProgress = animation.stepProgress,
            modifier = Modifier.matchParentSize()
        ) {
            PreviewScreenProvider { _, _ ->
                ListLocationsContent(
                    metadataState = remember { mutableStateOf(HelpMockData.MOCK_METADATA_STATE) },
                    locations = remember { HelpMockData.MOCK_LOCATIONS },
                )
            }
        }
        SwipeGestureOverlay(
            modifier = Modifier.matchParentSize(),
            progress = animation.stepProgress,
            direction = SwipeDirection.UP
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun NavBarPreviewMini(modifier: Modifier = Modifier) {
    val navIcons = remember {
        listOf(
            Icons.Outlined.Photo to "Timeline",
            GalleryIcons.Albums to "Albums",
            Icons.Outlined.PhotoLibrary to "Library",
        )
    }
    val animation = rememberPreviewAnimation(stepCount = 3)
    val selectedIndex = animation.currentStep
    val tapXPositions = remember { listOf(0.17f, 0.5f, 0.83f) }
    PreviewFrame(modifier, applyPadding = false) {
        PreviewScreenProvider { sharedScope, animScope ->
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f)) {
                    TimelineScreen(
                        paddingValues = PaddingValues(0.dp),
                        isScrolling = remember { mutableStateOf(false) },
                        mediaState = remember { mutableStateOf(HelpMockData.MOCK_MEDIA_STATE) },
                        metadataState = remember { mutableStateOf(HelpMockData.MOCK_METADATA_STATE) },
                        sharedTransitionScope = sharedScope,
                        animatedContentScope = animScope,
                    )
                }
                NavigationBar(
                    modifier = Modifier
                        .fillMaxWidth()
                        .scale(0.75f),
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
                ) {
                    navIcons.forEachIndexed { index, (icon, label) ->
                        NavigationBarItem(
                            selected = index == selectedIndex,
                            onClick = {},
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                            label = {
                                Text(
                                    text = label,
                                    fontWeight = FontWeight.Medium,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            },
                            icon = {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = label,
                                )
                            }
                        )
                    }
                }
            }
        }
        TapGestureOverlay(
            modifier = Modifier.matchParentSize(),
            progress = animation.stepProgress,
            tapX = tapXPositions[selectedIndex],
            tapY = 0.93f
        )
    }
}

@Composable
private fun SettingsPreviewMini(modifier: Modifier = Modifier) {
    val animation = rememberPreviewAnimation(stepCount = 2)
    PreviewFrame(modifier, applyPadding = false) {
        AutoScrollBox(
            scrollProgress = animation.stepProgress,
            modifier = Modifier.matchParentSize()
        ) {
            PreviewImageProvider {
                SettingsScreen()
            }
        }
        SwipeGestureOverlay(
            modifier = Modifier.matchParentSize(),
            progress = animation.stepProgress,
            direction = SwipeDirection.UP
        )
    }
}

// ── Shared helpers ──

@Composable
private fun PreviewFrame(
    modifier: Modifier = Modifier,
    applyPadding: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(3f / 4f)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clipToBounds()
            .then(if (applyPadding) Modifier.padding(8.dp) else Modifier)
    ) {
        content()
    }
}


