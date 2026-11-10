/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.dot.gallery.app.presentation.help.previews

import android.graphics.Color as AndroidColor
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.core.graphics.createBitmap
import androidx.media3.common.util.UnstableApi
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Panorama
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Slideshow
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.presentation.components.FilterKind
import com.dot.gallery.core.presentation.components.FilterOption
import com.dot.gallery.domain.model.MediaState
import com.dot.gallery.domain.model.Vault
import com.dot.gallery.domain.model.VaultState
import kotlinx.coroutines.flow.MutableStateFlow
import com.dot.gallery.app.presentation.albums.AlbumsScreen
import com.dot.gallery.app.presentation.classifier.CategoriesScreen
import com.dot.gallery.feature.exif.ui.MetadataViewContent
import com.dot.gallery.app.presentation.edit.EditScreen2
import com.dot.gallery.domain.model.editor.DrawMode
import com.dot.gallery.domain.model.editor.DrawType
import com.dot.gallery.domain.model.editor.PathProperties
import com.dot.gallery.app.presentation.vault.VaultDisplay
import com.dot.gallery.app.presentation.favorites.FavoriteScreen
import com.dot.gallery.feature.help.domain.PreviewType
import com.dot.gallery.feature.help.ui.AutoScrollBox
import com.dot.gallery.feature.help.ui.DoubleTapGestureOverlay
import com.dot.gallery.feature.help.ui.PinchGestureOverlay
import com.dot.gallery.feature.help.ui.PreviewBadge
import com.dot.gallery.feature.help.ui.PreviewFrame
import com.dot.gallery.feature.help.ui.SwipeDirection
import com.dot.gallery.feature.help.ui.SwipeGestureOverlay
import com.dot.gallery.feature.help.ui.TapGestureOverlay
import com.dot.gallery.app.presentation.help.data.HelpMockData
import com.dot.gallery.app.presentation.location.ListLocationsContent
import com.dot.gallery.app.presentation.settings.SettingsScreen
import com.dot.gallery.app.presentation.settings.subsettings.ColorPaletteScreen
import com.dot.gallery.app.presentation.timeline.TimelineScreen
import com.dot.gallery.core.presentation.components.shape
import com.dot.gallery.ui.core.Icons as GalleryIcons
import com.dot.gallery.ui.core.icons.Albums
import com.dot.gallery.ui.core.icons.Stylus
import com.dot.gallery.app.presentation.mediaview.MediaViewScreen
import com.dot.gallery.app.presentation.trashed.TrashedGridScreen
import com.dot.gallery.feature.help.ui.rememberPreviewAnimation
import androidx.compose.material.icons.outlined.Link
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import com.dot.gallery.feature.viewer.insight.CodeChip
import com.dot.gallery.feature.viewer.insight.TextChip
import com.dot.gallery.feature.viewer.insight.TextChipMode
import com.dot.gallery.feature.viewer.insight.TextSelectionBar
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.help_preview_code_kind
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.core.ui.generated.resources.Res as ResCoreUi
import com.dot.gallery.core.ui.generated.resources.filter_type_date
import com.dot.gallery.core.ui.generated.resources.filter_type_name


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
        PreviewType.SLIDESHOW -> SlideshowPreviewMini(modifier)
        PreviewType.MOTION_PHOTO -> MotionPhotoPreviewMini(modifier)
        PreviewType.VIDEO_CONTROLS -> VideoControlsPreviewMini(modifier)
        PreviewType.SUBJECT_CUTOUT -> SubjectCutoutPreviewMini(modifier)
        PreviewType.CASTING -> CastingPreviewMini(modifier)
        PreviewType.PANORAMA -> PanoramaPreviewMini(modifier)
        PreviewType.CODE_SCAN -> CodeScanPreviewMini(modifier)
        PreviewType.TEXT_RECOGNITION -> TextRecognitionPreviewMini(modifier)
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
                    paddingValues = PaddingValues(0.dp),
                    filterOptions = remember {
                        mutableStateListOf(
                            FilterOption(titleRes = ResCoreUi.string.filter_type_date, filterKind = FilterKind.DATE),
                            FilterOption(titleRes = ResCoreUi.string.filter_type_date, filterKind = FilterKind.DATE_MODIFIED),
                            FilterOption(titleRes = ResCoreUi.string.filter_type_name, filterKind = FilterKind.NAME),
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
                com.dot.gallery.feature.classifier.ui.CategoriesContent(
                    state = remember {
                        com.dot.gallery.feature.classifier.ui.CategoriesState(
                            categoriesWithCount = HelpMockData.MOCK_CATEGORIES_WITH_COUNT,
                            distinctMediaCount = HelpMockData.MOCK_CATEGORIES_WITH_COUNT.sumOf { it.mediaCount },
                            mediaState = MediaState(isLoading = false),
                        )
                    },
                    actions = remember {
                        com.dot.gallery.feature.classifier.ui.CategoriesActions(
                            onOpenCategory = {},
                            onEditCategory = {},
                            onCreateCategory = {},
                            onOpenSettings = {},
                        )
                    },
                    lastCellIndex = remember { androidx.compose.runtime.mutableIntStateOf(1) },
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

@UnstableApi
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun ViewerScreenMock() {
    PreviewScreenProvider { sharedScope, animScope ->
        MediaViewScreen(
            toggleRotate = {},
            paddingValues = PaddingValues(0.dp),
            isStandalone = true,
            mediaId = remember { HelpMockData.MOCK_PHOTOS.first().id },
            allowBlur = false,
            mediaState = remember { mutableStateOf(HelpMockData.MOCK_MEDIA_STATE) },
            metadataState = remember { mutableStateOf(HelpMockData.MOCK_METADATA_STATE) },
            albumsState = remember { mutableStateOf(HelpMockData.MOCK_ALBUM_STATE) },
            vaultState = remember { mutableStateOf(VaultState()) },
            sharedTransitionScope = sharedScope,
            animatedContentScope = animScope,
        )
    }
}

@Composable
private fun ViewerPreviewMini(modifier: Modifier = Modifier) {
    val animation = rememberPreviewAnimation(stepCount = 2)
    PreviewFrame(modifier, applyPadding = false) {
        AutoScrollBox(
            scrollProgress = animation.stepProgress,
            horizontal = true,
            modifier = Modifier.matchParentSize()
        ) {
            ViewerScreenMock()
        }
        SwipeGestureOverlay(
            modifier = Modifier.matchParentSize(),
            progress = animation.stepProgress,
            direction = SwipeDirection.LEFT
        )
    }
}

@Composable
private fun SlideshowPreviewMini(modifier: Modifier = Modifier) {
    val animation = rememberPreviewAnimation(stepCount = 3)
    PreviewFrame(modifier, applyPadding = false) {
        AutoScrollBox(
            scrollProgress = animation.stepProgress,
            horizontal = true,
            modifier = Modifier.matchParentSize()
        ) {
            ViewerScreenMock()
        }
        PreviewBadge(Icons.Outlined.Slideshow)
    }
}

@Composable
private fun PanoramaPreviewMini(modifier: Modifier = Modifier) {
    val animation = rememberPreviewAnimation(stepCount = 3)
    PreviewFrame(modifier, applyPadding = false) {
        AutoScrollBox(
            scrollProgress = animation.stepProgress,
            horizontal = true,
            modifier = Modifier.matchParentSize()
        ) {
            ViewerScreenMock()
        }
        PreviewBadge(Icons.Outlined.Panorama)
    }
}

@Composable
private fun MotionPhotoPreviewMini(modifier: Modifier = Modifier) {
    val animation = rememberPreviewAnimation(stepCount = 2)
    PreviewFrame(modifier, applyPadding = false) {
        ViewerScreenMock()
        DoubleTapGestureOverlay(
            modifier = Modifier.matchParentSize(),
            progress = animation.stepProgress
        )
        PreviewBadge(Icons.Outlined.PlayCircleOutline)
    }
}

@Composable
private fun CastingPreviewMini(modifier: Modifier = Modifier) {
    PreviewFrame(modifier, applyPadding = false) {
        ViewerScreenMock()
        PreviewBadge(Icons.Outlined.Cast)
    }
}

@Composable
private fun VideoControlsPreviewMini(modifier: Modifier = Modifier) {
    val animation = rememberPreviewAnimation(stepCount = 4)
    PreviewFrame(modifier, applyPadding = false) {
        ViewerScreenMock()
        PreviewBadge(Icons.Outlined.Movie)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.3f))
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(animation.stepProgress.coerceIn(0.06f, 1f))
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

@Composable
private fun SubjectCutoutPreviewMini(modifier: Modifier = Modifier) {
    val animation = rememberPreviewAnimation(stepCount = 2)
    PreviewFrame(modifier, applyPadding = false) {
        ViewerScreenMock()
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.5f)
                .aspectRatio(0.7f)
                .clip(RoundedCornerShape(16.dp))
                .border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f + 0.5f * animation.stepProgress),
                    shape = RoundedCornerShape(16.dp)
                )
        )
        PreviewBadge(Icons.Outlined.AutoFixHigh)
    }
}

/**
 * A photo with a QR code: the brackets draw in and the production [CodeChip] fades up under it,
 * as in the viewer. Sample state only; no scanner is created.
 */
@Composable
private fun CodeScanPreviewMini(modifier: Modifier = Modifier) {
    val animation = rememberPreviewAnimation(stepCount = 2)
    val progress = animation.stepProgress
    val primary = MaterialTheme.colorScheme.primary
    PreviewFrame(modifier, applyPadding = false) {
        ViewerScreenMock()
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White)
                    .drawBehind {
                        val cell = size.width / 21f
                        val ink = Color(0xFF16161A)
                        fun finder(cx: Int, cy: Int) {
                            drawRect(ink, Offset((cx + 2) * cell, (cy + 2) * cell), Size(cell * 7, cell * 7))
                            drawRect(Color.White, Offset((cx + 3) * cell, (cy + 3) * cell), Size(cell * 5, cell * 5))
                            drawRect(ink, Offset((cx + 4) * cell, (cy + 4) * cell), Size(cell * 3, cell * 3))
                        }
                        finder(0, 0); finder(10, 0); finder(0, 10)
                        for (i in 0 until 9) for (j in 0 until 9) {
                            if ((i * 7 + j * 3) % 5 < 2) {
                                drawRect(ink, Offset((10 + i) * cell, (10 + j) * cell), Size(cell, cell))
                            }
                        }
                    }
                    .drawWithContent {
                        drawContent()
                        val arm = size.width * 0.24f * progress
                        val stroke = 3.dp.toPx()
                        val w = size.width
                        val h = size.height
                        val c = primary.copy(alpha = progress)
                        listOf(Offset(0f, 0f) to Offset(1f, 1f), Offset(w, 0f) to Offset(-1f, 1f),
                            Offset(w, h) to Offset(-1f, -1f), Offset(0f, h) to Offset(1f, -1f)).forEach { (p, d) ->
                            drawLine(c, p, p + Offset(d.x * arm, 0f), stroke, StrokeCap.Round)
                            drawLine(c, p, p + Offset(0f, d.y * arm), stroke, StrokeCap.Round)
                        }
                    }
            )
            Box(modifier = Modifier.graphicsLayer { alpha = progress; translationY = (1f - progress) * 24f }) {
                CodeChip(
                    icon = Icons.Outlined.Link,
                    kind = stringResource(Res.string.help_preview_code_kind),
                    value = "canalnights.example",
                    contentDescription = "",
                    enabled = false,
                    onClick = {},
                )
            }
        }
    }
}

/**
 * A poster whose lines light up, then the production [TextChip] and, in the second step, the
 * selection with the production [TextSelectionBar]. Sample state only; no model is loaded.
 */
@Composable
private fun TextRecognitionPreviewMini(modifier: Modifier = Modifier) {
    val animation = rememberPreviewAnimation(stepCount = 2)
    val progress = animation.stepProgress
    val selecting = animation.currentStep == 1
    val primary = MaterialTheme.colorScheme.primary
    val lines = listOf("Canal Nights", "Open-air cinema · Aug 14", "Screenings start at 21:30", "Pier 4, Harbour Road")
    PreviewFrame(modifier, applyPadding = false) {
        ViewerScreenMock()
        if (selecting) Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.45f * progress)))
        Column(
            modifier = Modifier.align(Alignment.Center).clip(RoundedCornerShape(6.dp)).background(Color(0xFFF6F1E7)).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            lines.forEachIndexed { index, line ->
                Text(
                    text = line,
                    style = if (index == 0) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodySmall,
                    fontWeight = if (index == 0) FontWeight.Bold else null,
                    color = Color(0xFF16161A),
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(primary.copy(alpha = (if (selecting) 0.45f else 0.26f) * progress))
                        .padding(horizontal = 2.dp),
                )
            }
        }
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp).graphicsLayer { alpha = progress }) {
            if (selecting) {
                TextSelectionBar(showSearch = true, onCopy = {}, onShare = {}, onSelectAll = {}, onSearch = {})
            } else {
                TextChip(mode = TextChipMode.FOUND, lineCount = lines.size, onClick = {})
            }
        }
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
                MetadataViewContent(
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
// PreviewFrame / PreviewBadge / gesture overlays / rememberPreviewAnimation /
// AutoScrollBox live in :feature:help (com.dot.gallery.feature.help.ui).


