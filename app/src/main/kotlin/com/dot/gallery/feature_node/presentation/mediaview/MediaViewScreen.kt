/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.mediaview

import android.content.pm.ActivityInfo
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.view.PixelCopy
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisallowComposableCalls
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onVisibilityChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.graphics.createBitmap
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.composables.core.BottomSheet
import com.composables.core.SheetDetent.Companion.FullyExpanded
import com.composables.core.rememberBottomSheetState
import com.composeunstyled.LocalTextStyle
import com.dot.gallery.R
import com.dot.gallery.cloud.core.CloudRuntimeSettings
import com.dot.gallery.core.Constants.Animation.enterAnimation
import com.dot.gallery.core.Constants.Animation.exitAnimation
import com.dot.gallery.core.Constants.DEFAULT_TOP_BAR_ANIMATION_DURATION
import com.dot.gallery.core.Constants.Target.TARGET_TRASH
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.LocalMediaDistributor
import com.dot.gallery.core.Settings
import com.dot.gallery.core.Settings.Misc.rememberAutoContrast
import com.dot.gallery.core.Settings.Misc.rememberAutoHideOnVideoPlay
import com.dot.gallery.core.Settings.Misc.rememberDateHeaderFormat
import com.dot.gallery.core.Settings.Misc.rememberExtendedDateHeaderFormat
import com.dot.gallery.core.Settings.Misc.rememberShowMediaViewDateHeader
import com.dot.gallery.core.Settings.Misc.rememberVideoAutoplay
import com.dot.gallery.core.Settings.Misc.rememberVisualSearchAllowVault
import com.dot.gallery.core.Settings.Misc.rememberVisualSearchConvertFormat
import com.dot.gallery.core.Settings.Misc.rememberVisualSearchConvertMode
import com.dot.gallery.core.Settings.Misc.rememberVisualSearchEnabled
import com.dot.gallery.core.Settings.Misc.rememberVisualSearchPosition
import com.dot.gallery.core.Settings.Misc.rememberVisualSearchProvider
import com.dot.gallery.core.decoder.format.ImageReencoder
import com.dot.gallery.core.metadata.MetadataRemovalMode
import com.dot.gallery.core.metadata.MetadataSaveMode
import com.dot.gallery.core.navigate
import com.dot.gallery.core.navigateUp
import com.dot.gallery.core.presentation.components.DragHandle
import com.dot.gallery.core.presentation.components.OverwriteFallbackSheet
import com.dot.gallery.core.presentation.components.SetupButton
import com.dot.gallery.core.presentation.components.util.swipe
import com.dot.gallery.core.setFollowTheme
import com.dot.gallery.core.util.HdrCapabilities
import com.dot.gallery.feature_node.domain.model.AlbumState
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.MediaMetadata
import com.dot.gallery.feature_node.domain.model.MediaMetadataState
import com.dot.gallery.feature_node.domain.model.MediaState
import com.dot.gallery.feature_node.domain.model.SlideshowTransition
import com.dot.gallery.feature_node.domain.model.Vault
import com.dot.gallery.feature_node.domain.model.VaultState
import com.dot.gallery.feature_node.domain.util.getUri
import com.dot.gallery.feature_node.domain.util.isCloud
import com.dot.gallery.feature_node.domain.util.isEncrypted
import com.dot.gallery.feature_node.domain.util.isImage
import com.dot.gallery.feature_node.domain.util.isTrashed
import com.dot.gallery.feature_node.domain.util.isVideo
import com.dot.gallery.feature_node.domain.util.readUriOnly
import com.dot.gallery.feature_node.presentation.cast.FCastViewModel
import com.dot.gallery.feature_node.presentation.cast.components.CastButton
import com.dot.gallery.feature_node.presentation.cast.components.CastPermissionsDialog
import com.dot.gallery.feature_node.presentation.cast.components.CastStatusBanner
import com.dot.gallery.feature_node.presentation.cast.components.FCastDevicePickerDialog
import com.dot.gallery.feature_node.presentation.frameextract.FramePickerActivity
import com.dot.gallery.feature_node.presentation.frameextract.FrameSourceSpec
import com.dot.gallery.feature_node.presentation.mediaview.MediaViewViewModel.MediaViewEvent
import com.dot.gallery.feature_node.presentation.mediaview.components.GroupMemberSelectionBar
import com.dot.gallery.feature_node.presentation.mediaview.components.GroupMemberStrip
import com.dot.gallery.feature_node.presentation.mediaview.components.MediaViewAppBar
import com.dot.gallery.feature_node.presentation.mediaview.components.MediaViewQuickBottomBar
import com.dot.gallery.feature_node.presentation.mediaview.components.MediaViewSheetDetails
import com.dot.gallery.feature_node.presentation.mediaview.components.SlideshowControls
import com.dot.gallery.feature_node.presentation.mediaview.components.VisualSearchConvertSheet
import com.dot.gallery.feature_node.presentation.mediaview.components.actionbuttons.VisualSearchButton
import com.dot.gallery.feature_node.presentation.mediaview.components.media.CutoutController
import com.dot.gallery.feature_node.presentation.mediaview.components.media.CutoutControlsBar
import com.dot.gallery.feature_node.presentation.mediaview.components.media.MediaPreviewComponent
import com.dot.gallery.feature_node.presentation.mediaview.components.media.MotionPhotoFilmstrip
import com.dot.gallery.feature_node.presentation.mediaview.components.media.MotionPhotoState
import com.dot.gallery.feature_node.presentation.mediaview.components.media.ViewerSharedElementThumbnail
import com.dot.gallery.feature_node.presentation.mediaview.components.video.SubtitleBottomSheet
import com.dot.gallery.feature_node.presentation.mediaview.components.video.VideoPlayerController
import com.dot.gallery.feature_node.presentation.mediaview.slideshow.SlideshowAdvance
import com.dot.gallery.feature_node.presentation.mediaview.slideshow.buildSlideshowOrder
import com.dot.gallery.feature_node.presentation.mediaview.slideshow.resolveSlideshowAdvance
import com.dot.gallery.feature_node.presentation.mediaview.slideshow.slideshowDwellMillis
import com.dot.gallery.feature_node.presentation.util.AppBottomSheetState
import com.dot.gallery.feature_node.presentation.util.FullBrightnessWindow
import com.dot.gallery.feature_node.presentation.util.LocalHazeState
import com.dot.gallery.feature_node.presentation.util.MediaSharedElementKey
import com.dot.gallery.feature_node.presentation.util.ProvideInsets
import com.dot.gallery.feature_node.presentation.util.Screen
import com.dot.gallery.feature_node.presentation.util.ViewScreenConstants.BOTTOM_BAR_HEIGHT
import com.dot.gallery.feature_node.presentation.util.ViewScreenConstants.ImageOnly
import com.dot.gallery.feature_node.presentation.util.getMediaAppBarDate
import com.dot.gallery.feature_node.presentation.util.hazeEffectScaled
import com.dot.gallery.feature_node.presentation.util.mediaSharedElement
import com.dot.gallery.feature_node.presentation.util.printWarning
import com.dot.gallery.feature_node.presentation.util.rememberAppBottomSheetState
import com.dot.gallery.feature_node.presentation.util.rememberGestureNavigationEnabled
import com.dot.gallery.feature_node.presentation.util.rememberNavigationBarOnSides
import com.dot.gallery.feature_node.presentation.util.rememberWindowInsetsController
import com.dot.gallery.feature_node.presentation.util.setHdrMode
import com.dot.gallery.feature_node.presentation.util.shareMedia
import com.dot.gallery.feature_node.presentation.util.toggleSystemBars
import com.dot.gallery.ui.theme.isDarkTheme
import com.github.panpf.sketch.BitmapImage
import com.github.panpf.sketch.cache.CachePolicy
import com.github.panpf.sketch.request.ImageRequest
import com.github.panpf.sketch.sketch
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

internal enum class TapNavigationZone {
    Start,
    Center,
    End,
}

internal fun resolveTapNavigationZone(
    tapX: Float,
    viewportWidth: Int,
    isRtl: Boolean,
): TapNavigationZone {
    if (viewportWidth <= 0 || !tapX.isFinite()) return TapNavigationZone.Center
    val physicalZone = when {
        tapX < viewportWidth / 3f -> TapNavigationZone.Start
        tapX > viewportWidth * 2f / 3f -> TapNavigationZone.End
        else -> TapNavigationZone.Center
    }
    if (!isRtl || physicalZone == TapNavigationZone.Center) return physicalZone
    return if (physicalZone == TapNavigationZone.Start) TapNavigationZone.End
    else TapNavigationZone.Start
}

internal fun resolveTapNavigationTarget(
    zone: TapNavigationZone,
    currentPage: Int,
    pageCount: Int,
): Int? {
    if (currentPage !in 0 until pageCount) return null
    return when (zone) {
        TapNavigationZone.Start -> (currentPage - 1).takeIf { it >= 0 }
        TapNavigationZone.End -> (currentPage + 1).takeIf { it < pageCount }
        TapNavigationZone.Center -> null
    }
}

internal fun shouldHandleTapImmediately(
    tapNavigationEnabled: Boolean,
    zone: TapNavigationZone,
    canNavigate: Boolean,
): Boolean = tapNavigationEnabled && zone != TapNavigationZone.Center && canNavigate

internal fun isTapNavigationPromptEligible(
    tapNavigationEnabled: Boolean,
    isStandalone: Boolean,
    slideshowActive: Boolean,
    pageCount: Int,
    initialPageSetup: Boolean,
    isOrdinaryImage: Boolean,
    viewerSettled: Boolean,
): Boolean = !tapNavigationEnabled &&
        !isStandalone &&
        !slideshowActive &&
        pageCount > 1 &&
        initialPageSetup &&
        isOrdinaryImage &&
        viewerSettled

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TapNavigationPromptSheet(
    state: AppBottomSheetState,
    onEnable: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    if (state.isVisible) {
        ModalBottomSheet(
            sheetState = state.sheetState,
            onDismissRequest = { scope.launch { state.hide() } },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            tonalElevation = 0.dp,
            dragHandle = { DragHandle() },
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        ) {
            TapNavigationPromptContent(
                onEnable = {
                    onEnable()
                    scope.launch { state.hide() }
                },
                onNotNow = { scope.launch { state.hide() } },
            )
        }
    }
}

@Composable
private fun TapNavigationPromptContent(
    onEnable: () -> Unit,
    onNotNow: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = 8.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.tap_sides_to_navigate_prompt_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.tap_sides_to_navigate_prompt_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        TapNavigationPreview(
            enabled = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SetupButton(
                onClick = onNotNow,
                modifier = Modifier.weight(1f),
                applyHorizontalPadding = false,
                applyBottomPadding = false,
                applyInsets = false,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                text = stringResource(R.string.tap_sides_to_navigate_not_now),
            )
            SetupButton(
                onClick = onEnable,
                modifier = Modifier.weight(1f),
                applyHorizontalPadding = false,
                applyBottomPadding = false,
                applyInsets = false,
                text = stringResource(R.string.tap_sides_to_navigate_enable),
            )
        }
        Text(
            text = stringResource(R.string.tap_sides_to_navigate_settings_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun TapNavigationPreview(
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(16f / 10f)
            .clip(RoundedCornerShape(20.dp)),
    ) {
        Image(
            painter = painterResource(R.drawable.image_sample_2),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.18f)),
        )
        if (enabled) {
            Row(modifier = Modifier.fillMaxSize()) {
                TapNavigationZonePreview(
                    icon = Icons.AutoMirrored.Outlined.ArrowBack,
                    label = stringResource(R.string.tap_sides_to_navigate_previous),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.32f)),
                )
                TapNavigationZonePreview(
                    icon = null,
                    label = stringResource(R.string.tap_sides_to_navigate_controls),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                )
                TapNavigationZonePreview(
                    icon = Icons.AutoMirrored.Outlined.ArrowForward,
                    label = stringResource(R.string.tap_sides_to_navigate_next),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.32f)),
                )
            }
        } else {
            TapNavigationZonePreview(
                icon = null,
                label = stringResource(R.string.tap_sides_to_navigate_controls),
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun TapNavigationZonePreview(
    icon: ImageVector?,
    label: String,
    modifier: Modifier,
) {
    Column(
        modifier = modifier.padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
    }
}

internal data class MediaViewerInitialSelection(
    val pageIndex: Int,
    val memberId: Long?,
    val found: Boolean,
)

internal fun shouldDismissMissingMediaTarget(
    isLoading: Boolean,
    isPartial: Boolean,
    targetFound: Boolean,
    hasMedia: Boolean,
    isStandalone: Boolean,
): Boolean = !isLoading && !isPartial && !targetFound && hasMedia && !isStandalone

internal fun shouldExitEmptySlideshow(
    isActive: Boolean,
    isLoading: Boolean,
    hasItems: Boolean,
): Boolean = isActive && !isLoading && !hasItems

internal fun isMediaViewerSharedElementPage(
    page: Int,
    currentPage: Int,
): Boolean = page == currentPage

private fun Modifier.mediaViewerBackGestureGuard(width: Dp): Modifier =
    fillMaxHeight()
        .width(width)
        .pointerInput(width) {
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            }
        }

internal fun resolveMediaViewerInitialSelection(
    mediaId: Long,
    pagerMediaIds: List<Long>,
    mediaGroupIds: Map<Long, List<Long>>,
): MediaViewerInitialSelection {
    val directIndex = pagerMediaIds.indexOf(mediaId)
    if (directIndex >= 0) {
        return MediaViewerInitialSelection(directIndex, null, true)
    }
    val groupEntry = mediaGroupIds.entries.firstOrNull { mediaId in it.value }
        ?: return MediaViewerInitialSelection(0, null, false)
    val groupIndex = pagerMediaIds.indexOf(groupEntry.key)
    return if (groupIndex >= 0) {
        MediaViewerInitialSelection(groupIndex, mediaId, true)
    } else {
        MediaViewerInitialSelection(0, null, false)
    }
}

internal fun isMediaViewerContentReady(
    selectionApplied: Boolean,
    isLoading: Boolean,
    targetFound: Boolean,
    currentPage: Int,
    initialPage: Int,
): Boolean = selectionApplied ||
        (!isLoading && targetFound && currentPage == initialPage)

/**
 * Whether the low-res surrogate thumbnail behind the media should draw. Its jobs are the
 * load placeholder and the transition/dismiss underlay; once the viewer is settled it must
 * stay hidden — parked under the media it leaks into view whenever the image leaves its
 * rest bounds (pinch zoom-out rubber-banding, rotation) (#1226).
 */
internal fun isViewerSurrogateVisible(
    contentReady: Boolean,
    transitionRunning: Boolean,
    dismissActive: Boolean,
    dismissedVisualHidden: Boolean,
): Boolean = !dismissedVisualHidden &&
        (!contentReady || transitionRunning || dismissActive)

/**
 * True when a pointer down at [downUptime] lands inside the double-tap window after a tap
 * released at [lastTapUpUptime]. That second press belongs to the image's one-finger zoom
 * (double-tap-hold and drag), so the viewer's swipe-to-dismiss must not arm on it.
 */
internal fun isSecondTapPress(
    downUptime: Long,
    lastTapUpUptime: Long,
    doubleTapMinMillis: Long,
    doubleTapTimeoutMillis: Long,
): Boolean {
    if (lastTapUpUptime < 0) return false
    val delta = downUptime - lastTapUpUptime
    return delta >= doubleTapMinMillis && delta <= doubleTapTimeoutMillis
}

/** True when a released gesture was a clean tap: released quickly and within touch slop. */
internal fun isCleanTap(
    upUptime: Long,
    downUptime: Long,
    dragDistance: Float,
    longPressTimeoutMillis: Long,
    touchSlop: Float,
): Boolean =
    upUptime - downUptime < longPressTimeoutMillis && dragDistance <= touchSlop

@Composable
fun <T> rememberedDerivedState(
    key: Any? = Unit,
    block: @DisallowComposableCalls () -> T
): State<T> {
    return remember(key) {
        derivedStateOf(block)
    }
}

@Composable
fun <T> rememberedDerivedState(
    vararg keys: Any? = arrayOf(Unit),
    block: @DisallowComposableCalls () -> T
): State<T> {
    return remember(*keys) {
        derivedStateOf(block)
    }
}

private class VideoFramePickerControllerRef {
    var player: ExoPlayer? = null
    var position: MutableLongState? = null
}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalHazeMaterialsApi::class)
@Composable
fun <T : Media> MediaViewScreenRoute(
    toggleRotate: () -> Unit,
    paddingValues: PaddingValues,
    isStandalone: Boolean = false,
    initialUiVisible: Boolean = true,
    mediaId: Long,
    target: String? = null,
    mediaState: State<MediaState<out T>>,
    metadataState: State<MediaMetadataState>,
    albumsState: State<AlbumState>,
    vaultState: State<VaultState>,
    restoreMedia: ((Vault, T, () -> Unit) -> Unit)? = null,
    deleteMedia: ((Vault, T, () -> Unit) -> Unit)? = null,
    currentVault: Vault? = null,
    slideshow: Boolean = false,
    allowBlur: Boolean,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedVisibilityScope,
    onDismissRequest: (() -> Unit)? = null,
    onCurrentMediaChange: (Long) -> Unit = {},
    viewerSessionKey: Int = 0,
    dismissBridge: ViewerDismissBridge? = null,
) {
    val viewModel = hiltViewModel<MediaViewViewModel>()
    MediaViewScreen(
        toggleRotate = toggleRotate,
        paddingValues = paddingValues,
        isStandalone = isStandalone,
        initialUiVisible = initialUiVisible,
        mediaId = mediaId,
        target = target,
        mediaState = mediaState,
        metadataState = metadataState,
        albumsState = albumsState,
        vaultState = vaultState,
        restoreMedia = restoreMedia,
        deleteMedia = deleteMedia,
        currentVault = currentVault,
        slideshow = slideshow,
        allowBlur = allowBlur,
        sharedTransitionScope = sharedTransitionScope,
        animatedContentScope = animatedContentScope,
        onDismissRequest = onDismissRequest,
        onCurrentMediaChange = onCurrentMediaChange,
        viewerSessionKey = viewerSessionKey,
        dismissBridge = dismissBridge,
        ensureMetadataAvailable = viewModel::ensureMetadataAvailable,
        rotateImage = viewModel::rotateImage,
        uiEvents = viewModel.uiEvents,
        rotationState = viewModel.rotationState,
        visualSearchState = viewModel.visualSearchState,
        launchVisualSearch = viewModel::launchVisualSearch,
        cancelVisualSearch = viewModel::cancelVisualSearch,
        metadataSanitizationState = viewModel.metadataSanitizationState,
        probeMetadataSanitization = viewModel::probeMetadataSanitization,
        sanitizeMetadata = viewModel::sanitizeMetadata,
        resetMetadataSanitization = viewModel::resetMetadataSanitization,
        motionPhotoStateFactory = { media ->
            com.dot.gallery.feature_node.presentation.mediaview.components.media.rememberMotionPhotoState(
                media = media,
                viewModel = viewModel
            )
        },
    )
}

@UnstableApi
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalHazeMaterialsApi::class)
@Composable
fun <T : Media> MediaViewScreen(
    toggleRotate: () -> Unit,
    paddingValues: PaddingValues,
    isStandalone: Boolean = false,
    initialUiVisible: Boolean = true,
    mediaId: Long,
    target: String? = null,
    mediaState: State<MediaState<out T>>,
    metadataState: State<MediaMetadataState>,
    albumsState: State<AlbumState>,
    vaultState: State<VaultState>,
    restoreMedia: ((Vault, T, () -> Unit) -> Unit)? = null,
    deleteMedia: ((Vault, T, () -> Unit) -> Unit)? = null,
    currentVault: Vault? = null,
    slideshow: Boolean = false,
    allowBlur: Boolean,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedVisibilityScope,
    onDismissRequest: (() -> Unit)? = null,
    onCurrentMediaChange: (Long) -> Unit = {},
    viewerSessionKey: Int = 0,
    dismissBridge: ViewerDismissBridge? = null,
    ensureMetadataAvailable: (Media?, MediaMetadataState) -> Unit = { _, _ -> },
    rotateImage: (Media, Int, Boolean) -> Unit = { _, _, _ -> },
    uiEvents: SharedFlow<MediaViewEvent> = MutableSharedFlow(),
    rotationState: StateFlow<MediaViewViewModel.RotationUiState?> = MutableStateFlow(null),
    visualSearchState: StateFlow<MediaViewViewModel.VisualSearchUiState> =
        MutableStateFlow(MediaViewViewModel.VisualSearchUiState.Idle),
    launchVisualSearch: (Media, MediaMetadata?, Vault?, VisualSearchTarget, Long?, ImageReencoder.ImageWriteFormat?) -> Unit =
        { _, _, _, _, _, _ -> },
    cancelVisualSearch: () -> Unit = {},
    metadataSanitizationState: StateFlow<MediaViewViewModel.MetadataSanitizationUiState> = MutableStateFlow(
        MediaViewViewModel.MetadataSanitizationUiState.Idle
    ),
    probeMetadataSanitization: (Media) -> Unit = {},
    sanitizeMetadata: (Media, MetadataRemovalMode, MetadataSaveMode) -> Unit = { _, _, _ -> },
    resetMetadataSanitization: () -> Unit = {},
    motionPhotoStateFactory: @Composable (Media?) -> MotionPhotoState = { remember { MotionPhotoState() } },
) = CompositionLocalProvider(
    LocalMediaViewerVisualPolicy provides MediaViewerVisualPolicy(allowBlur = allowBlur),
    LocalMediaViewerNavigate provides rememberViewerExitNavigate(onDismissRequest)
) {
    ProvideInsets {
        val eventHandler = LocalEventHandler.current
        val distributor = LocalMediaDistributor.current
        val dismissViewer = { onDismissRequest?.invoke() ?: eventHandler.navigateUp() }
        val navigateFromViewer = rememberMediaViewerNavigate()
        val context = LocalContext.current
        val rotateFailedText = stringResource(R.string.rotate_failed)
        val visualSearchFailedText = stringResource(R.string.visual_search_failed)
        val metadataSanitizationUiState by metadataSanitizationState.collectAsStateWithLifecycle()
        val scope = rememberCoroutineScope()
        val windowInsetsController = rememberWindowInsetsController()

        var initialPageSetup by rememberSaveable(mediaId) { mutableStateOf(false) }

        // Rotate on a format with no encoder (RAW/TIFF/PSD/…) can't overwrite in place; hold the
        // pending request so the fallback sheet can offer a copy instead.
        var rotateFallback by remember { mutableStateOf<Pair<Media, Int>?>(null) }
        rotateFallback?.let { (media, degrees) ->
            OverwriteFallbackSheet(
                onCreateCopy = {
                    rotateFallback = null
                    rotateImage(media, degrees, true)
                },
                onDismiss = { rotateFallback = null }
            )
        }

        // ── Slideshow mode ──
        val slideshowConfig = remember(slideshow) {
            if (slideshow) Settings.Slideshow.readConfig(context) else null
        }
        val slideshowSeed = rememberSaveable { System.currentTimeMillis() }
        var slideshowActive by rememberSaveable { mutableStateOf(slideshow) }
        var slideshowPaused by rememberSaveable { mutableStateOf(false) }
        // Whether the minimal slideshow transport bar is currently shown (toggled by tapping the
        // media). Kept separate from [showUI] so the normal viewer chrome stays hidden in slideshow.
        var slideshowControlsVisible by rememberSaveable { mutableStateOf(false) }
        // Signals emitted (with a media id) when a video finishes playing in slideshow mode.
        val videoEndedFlow = remember { MutableSharedFlow<Long>(extraBufferCapacity = 4) }
        var failedMediaIds by rememberSaveable { mutableStateOf(emptySet<Long>()) }

        // FCast
        val fcastVm: FCastViewModel = hiltViewModel()
        val fcastState by fcastVm.state.collectAsStateWithLifecycle()
        var showCastPicker by rememberSaveable { mutableStateOf(false) }
        var showCastPermissions by rememberSaveable { mutableStateOf(false) }

        // IDs of media confirmed for trash/delete but not yet removed from mediaState
        var pendingTrashIds by rememberSaveable { mutableStateOf(emptySet<Long>()) }

        // The media currently flying out after a confirmed trash/delete/restore. The page
        // swaps to the neighbor instantly once pending removal hides it; this thumbnail drops
        // down-and-out so the reveal reads as a deletion rather than a silent swap.
        var exitingMedia by remember { mutableStateOf<Media?>(null) }

        // Clean up pending IDs once the source has caught up
        LaunchedEffect(mediaState.value) {
            if (pendingTrashIds.isNotEmpty()) {
                val sourceIds = mediaState.value.media.map { it.id }.toSet()
                val confirmed = pendingTrashIds.filterNot { it in sourceIds }
                if (confirmed.isNotEmpty()) {
                    pendingTrashIds = pendingTrashIds - confirmed.toSet()
                }
            }
        }

        // Use pagerMedia for paging (only representatives when grouped, otherwise all media).
        // pagerMedia is already de-duplicated by id when built in mapMediaToItem (on Dispatchers.IO),
        // so only the raw `media` fallback needs distinctBy. Skipping it on the common path avoids an
        // O(n) list + HashSet allocation on the composition thread when opening large libraries.
        val pagerItems by rememberedDerivedState(
            mediaState.value,
            pendingTrashIds,
            slideshowActive
        ) {
            val pager = mediaState.value.pagerMedia
            val items =
                if (pager.isNotEmpty()) pager else mediaState.value.media.distinctBy { it.id }
            val filtered =
                if (pendingTrashIds.isEmpty()) items else items.filter { it.id !in pendingTrashIds }
            // While the slideshow is active, follow the computed playlist order (filtered/reversed/
            // randomized) so advancing is always the next page and looping wraps to index 0.
            if (slideshowActive && slideshowConfig != null) {
                buildSlideshowOrder(filtered, slideshowConfig, mediaId, slideshowSeed)
            } else filtered
        }

        val initialSelection by rememberedDerivedState(
            mediaId,
            pagerItems,
            mediaState.value.mediaGroups,
        ) {
            resolveMediaViewerInitialSelection(
                mediaId = mediaId,
                pagerMediaIds = pagerItems.map { it.id },
                mediaGroupIds = mediaState.value.mediaGroups.mapValues { (_, members) ->
                    members.map { it.id }
                },
            )
        }
        // Resolved entry target. Once the viewer settles on a page the resolution is frozen:
        // the entry media leaving the source afterwards (e.g. the user just deleted it) must
        // not re-resolve to "not found" — the missing-target dismiss would pop the viewer back
        // to the timeline instead of advancing to a neighbor page.
        var frozenEntryPage by rememberSaveable(mediaId) { mutableIntStateOf(-1) }
        var frozenEntryMemberId by rememberSaveable(mediaId) { mutableStateOf<Long?>(null) }
        val entrySelection = if (frozenEntryPage >= 0) {
            MediaViewerInitialSelection(frozenEntryPage, frozenEntryMemberId, true)
        } else initialSelection
        // Use only primitive ids/sizes as saveable keys (avoid passing full media list object)
        val initialPage = entrySelection.pageIndex
        var currentPage by rememberSaveable(initialPage) { mutableIntStateOf(initialPage) }
        var isVideoZoomed by rememberSaveable { mutableStateOf(false) }
        var isImageZoomed by rememberSaveable { mutableStateOf(false) }

        val pagerState = rememberPagerState(
            initialPage = initialPage,
            initialPageOffsetFraction = 0f,
            pageCount = { pagerItems.size }
        )
        val viewerContentReady = isMediaViewerContentReady(
            selectionApplied = initialPageSetup,
            isLoading = mediaState.value.isLoading,
            targetFound = entrySelection.found,
            currentPage = pagerState.currentPage,
            initialPage = initialPage,
        )

        // Group members for the current page's media
        val currentGroupMembers by rememberedDerivedState(
            mediaState.value,
            currentPage,
            pendingTrashIds
        ) {
            val currentId =
                pagerItems.getOrNull(currentPage)?.id ?: return@rememberedDerivedState emptyList()
            val members = mediaState.value.mediaGroups[currentId] ?: emptyList()
            if (pendingTrashIds.isEmpty()) members else members.filter { it.id !in pendingTrashIds }
        }

        // Track which group member is selected (null = show representative/pager item)
        var selectedMemberOverrideId by rememberSaveable(mediaId, entrySelection.memberId) {
            mutableStateOf(entrySelection.memberId)
        }
        var selectedMemberPage by rememberSaveable(mediaId, initialPage) {
            mutableIntStateOf(initialPage)
        }

        // Multi-select state for group members
        var groupMultiSelectMode by rememberSaveable { mutableStateOf(false) }
        var groupMultiSelectedIds by rememberSaveable { mutableStateOf(emptySet<Long>()) }

        // Select first group member when swiping to a different page
        LaunchedEffect(currentPage) {
            if (initialPageSetup && currentPage != selectedMemberPage) {
                selectedMemberOverrideId = null
                selectedMemberPage = currentPage
            }
            groupMultiSelectMode = false
            groupMultiSelectedIds = emptySet()
            isVideoZoomed = false
            isImageZoomed = false
        }

        // Reset selected member if it was deleted (no longer in group members)
        LaunchedEffect(currentGroupMembers, selectedMemberOverrideId) {
            val overrideId = selectedMemberOverrideId
            if (overrideId != null && currentGroupMembers.isNotEmpty() &&
                currentGroupMembers.none { it.id == overrideId }
            ) {
                selectedMemberOverrideId = currentGroupMembers.firstOrNull()?.id
            }
        }

        val currentMedia by rememberedDerivedState(
            mediaState.value,
            currentPage,
            selectedMemberOverrideId
        ) {
            val pagerItem = pagerItems.getOrNull(currentPage)
            if (selectedMemberOverrideId != null) {
                currentGroupMembers.find { it.id == selectedMemberOverrideId } ?: pagerItem
            } else {
                currentGroupMembers.firstOrNull() ?: pagerItem
            }
        }

        LaunchedEffect(currentMedia?.id) {
            currentMedia?.id?.let(onCurrentMediaChange)
            ensureMetadataAvailable(currentMedia, metadataState.value)
        }

        LaunchedEffect(mediaId, initialPage, entrySelection.found, mediaState.value.isLoading) {
            if (!mediaState.value.isLoading && entrySelection.found && !initialPageSetup) {
                frozenEntryPage = entrySelection.pageIndex
                frozenEntryMemberId = entrySelection.memberId
                if (pagerState.currentPage != initialPage) {
                    pagerState.scrollToPage(initialPage)
                }
                currentPage = initialPage
                selectedMemberPage = initialPage
                selectedMemberOverrideId = entrySelection.memberId
                initialPageSetup = true
            }
        }
        LaunchedEffect(
            mediaState.value.isLoading,
            mediaState.value.isPartial,
            entrySelection.found,
            pagerItems.isNotEmpty()
        ) {
            if (shouldDismissMissingMediaTarget(
                    isLoading = mediaState.value.isLoading,
                    isPartial = mediaState.value.isPartial,
                    targetFound = entrySelection.found,
                    hasMedia = pagerItems.isNotEmpty(),
                    isStandalone = isStandalone,
                )
            ) {
                dismissViewer()
            }
        }

        val currentDateFormat by rememberDateHeaderFormat()
        val currentExtendedDateFormat by rememberExtendedDateHeaderFormat()
        val textStyle = LocalTextStyle.current
        val currentDate by rememberedDerivedState(
            currentMedia,
            currentDateFormat,
            currentExtendedDateFormat
        ) {
            buildAnnotatedString {
                val date = currentMedia?.definedTimestamp?.getMediaAppBarDate(
                    currentDateFormat,
                    currentExtendedDateFormat
                ) ?: ""
                if (date.isNotEmpty()) {
                    val top = date.substringBefore("\n")
                    val bottom = date.substringAfter("\n")
                    withStyle(
                        style = textStyle.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        ).toSpanStyle()
                    ) {
                        appendLine(top)
                    }
                    withStyle(
                        style = textStyle.copy(
                            fontWeight = FontWeight.Normal,
                            fontSize = 14.sp
                        ).toSpanStyle()
                    ) {
                        append(bottom)
                    }
                }
            }
        }
        val canAutoPlay by rememberVideoAutoplay()
        var tapSidesToNavigate by Settings.Misc.rememberTapSidesToNavigate()
        val playWhenReady by rememberedDerivedState(
            currentMedia,
            canAutoPlay,
            slideshowActive
        ) { currentMedia?.isVideo == true && (canAutoPlay || slideshowActive) }
        val isReadOnly by rememberedDerivedState { currentMedia?.readUriOnly == true }
        val cloudSettingsByConfigId by CloudRuntimeSettings.settingsByConfigId.collectAsStateWithLifecycle()
        val currentCapabilities by rememberedDerivedState(currentMedia, cloudSettingsByConfigId) {
            currentMedia?.viewerActionCapabilities(
                settingsByConfigId = cloudSettingsByConfigId,
            )
        }
        // URI-only items still have immutable filename/type/size details even though metadata mutation
        // and all source writes are unavailable.
        val showInfo by rememberedDerivedState { currentMedia?.trashed == 0 }

        var showUI by rememberSaveable { mutableStateOf(initialUiVisible) }
        val viewerDismissState = remember(viewerSessionKey) { ViewerDismissState(dismissBridge) }
        val overlayMode = onDismissRequest != null
        val requestViewerDismiss = {
            // In overlay mode the AnimatedVisibility exit runs the sharedBounds return flight
            // from the media's current (dragged) position to the live source cell.
            dismissViewer()
        }
        val viewerInteractive = !overlayMode || !viewerDismissState.isActive
        val dismissAlpha = if (overlayMode) viewerDismissState.chromeAlpha else 1f
        // The pinned blurred backdrop dissolves quickly under the dragging card — faster than
        // the scrim/chrome fade — so the reveal behind the dropped card is the timeline, not a
        // lingering blur veil.
        val backdropAlpha = if (overlayMode) viewerDismissState.backdropAlpha else 1f
        val navigationChromeVisible = !animatedContentScope.transition.isRunning
        val showViewerChrome = showUI && navigationChromeVisible

        // Deterministic enter flight (overlay mode): the framework's shared-element bounds morph
        // snaps under the deferred transition — its bounds DeferredAnimation is recreated once the
        // transition's currentState already reads the target, so it initializes at the end bounds
        // and the media pops fullscreen instead of morphing from the cell. A root-level thumbnail
        // morphs cell→fullscreen instead, with both entries suppressed so no framework morph draws
        // on top; suppression holds until the container's enter transition settles.
        val sharedElementsEnabled by Settings.Misc.rememberSharedElements()
        LaunchedEffect(viewerSessionKey) {
            val b = dismissBridge
            if (!overlayMode || !sharedElementsEnabled || b == null) return@LaunchedEffect
            val enterMedia = withTimeoutOrNull(800) {
                snapshotFlow {
                    pagerItems.getOrNull(entrySelection.pageIndex)
                        ?.takeIf { entrySelection.found }
                }.first { it != null }
            } ?: return@LaunchedEffect
            val enterKey = MediaSharedElementKey.MediaKey(enterMedia.id)
            val cell = b.cellBounds[enterKey]?.takeUnless { it.isEmpty } ?: return@LaunchedEffect
            viewerDismissState.runEnterFlight(enterKey, enterMedia, cell)
            snapshotFlow { animatedContentScope.transition.isRunning }.first { !it }
            if (!viewerDismissState.isActive &&
                b.suppressedElementKey == enterKey &&
                b.flight == null
            ) {
                b.suppressedElementKey = null
            }
        }
        // True while the current cloud/remote page is downloading its full-size original for
        // subsampling; drives the subtle horizontal loading indicator under the top-center date.
        var subsamplingLoading by remember { mutableStateOf(false) }
        var isCutoutActive by rememberSaveable { mutableStateOf(false) }
        // Controller published by the current page while a cutout session is active; drives the bottom
        // cutout controls bar that replaces the quick-actions bar.
        var cutoutController by remember { mutableStateOf<CutoutController?>(null) }
        var isTopDark by remember { mutableStateOf(false) }
        var isBottomDark by remember { mutableStateOf(false) }
        val autoContrast by rememberAutoContrast()
        val motionPhotoState = motionPhotoStateFactory(currentMedia)
        val videoFramePickerController =
            remember(currentMedia?.id) { VideoFramePickerControllerRef() }
        val openFramePicker: () -> Unit =
            remember(currentMedia, motionPhotoState.motionInfo, currentVault) {
                {
                    currentMedia?.let { media ->
                        motionPhotoState.stopPlayback()
                        videoFramePickerController.player?.pause()
                        val metadata = metadataState.value.metadataMap[media.id]
                        FramePickerActivity.launch(
                            context,
                            FrameSourceSpec.from(
                                media = media,
                                metadata = metadata,
                                currentVault = currentVault,
                                motionPhotoHint = motionPhotoState.isDetected || metadata?.isMotionPhoto == true,
                                preferredPresentationTimeUs = motionPhotoState.motionInfo
                                    ?.presentationTimestampUs ?: -1L,
                                initialPositionMs = if (media.isVideo) {
                                    videoFramePickerController.position?.longValue
                                } else null,
                            ),
                        )
                    }
                }
            }
        // ── Visual search (#1155) ──
        // Provider-aware button: enabled by default, hidden when no app accepting
        // ACTION_SEND image/* is installed or when the media can't produce an image.
        var visualSearchEnabled by rememberVisualSearchEnabled()
        var visualSearchProviderPref by rememberVisualSearchProvider()
        val visualSearchPosition by rememberVisualSearchPosition()
        var visualSearchAllowVault by rememberVisualSearchAllowVault()
        var visualSearchConvertMode by rememberVisualSearchConvertMode()
        var visualSearchConvertFormat by rememberVisualSearchConvertFormat()
        val visualSearchTargets = remember(context) { context.discoverVisualSearchTargets() }
        val visualSearchTarget = remember(visualSearchTargets, visualSearchProviderPref) {
            resolveVisualSearchTarget(visualSearchTargets, visualSearchProviderPref)
        }
        val visualSearchAvailable by rememberedDerivedState(
            visualSearchEnabled,
            visualSearchTarget,
            currentCapabilities,
            currentMedia,
            visualSearchAllowVault,
        ) {
            visualSearchEnabled && visualSearchTarget != null &&
                    currentCapabilities?.visualSearch == true &&
                    currentMedia?.isTrashed == false &&
                    (visualSearchAllowVault || currentMedia?.isEncrypted != true)
        }
        val visualSearchTitle = visualSearchTarget?.let {
            stringResource(R.string.visual_search_with, it.displayName)
        } ?: stringResource(R.string.visual_search_generic_cd)
        // Set while the ask-mode convert sheet is up for an exotic-format image.
        var visualSearchConvertRequest by remember { mutableStateOf<Media?>(null) }
        val onVisualSearch: (Media) -> Unit = { media ->
            visualSearchTarget?.let { target ->
                motionPhotoState.stopPlayback()
                videoFramePickerController.player?.pause()
                val positionMs =
                    if (media.isVideo) videoFramePickerController.position?.longValue else null
                val metadata = metadataState.value.metadataMap[media.id]
                when {
                    media.needsVisualSearchConvert &&
                            visualSearchConvertMode == Settings.Misc.VISUAL_SEARCH_CONVERT_ASK ->
                        visualSearchConvertRequest = media

                    media.needsVisualSearchConvert &&
                            visualSearchConvertMode == Settings.Misc.VISUAL_SEARCH_CONVERT_ALWAYS ->
                        launchVisualSearch(
                            media, metadata, currentVault, target, positionMs,
                            visualSearchConvertFormat.toVisualSearchWriteFormat(),
                        )

                    else ->
                        launchVisualSearch(media, metadata, currentVault, target, positionMs, null)
                }
            }
        }
        val visualSearchUiState by visualSearchState.collectAsStateWithLifecycle()
        val visualSearchPreparing =
            visualSearchUiState as? MediaViewViewModel.VisualSearchUiState.Preparing
        val visualSearchStageLabel = when (visualSearchPreparing?.stage) {
            VisualSearchStage.DOWNLOADING ->
                stringResource(R.string.visual_search_stage_downloading)
            VisualSearchStage.DECRYPTING ->
                stringResource(R.string.visual_search_stage_decrypting)
            VisualSearchStage.PREPARING ->
                stringResource(R.string.visual_search_stage_preparing)
            VisualSearchStage.EXTRACTING ->
                stringResource(R.string.visual_search_stage_extracting)
            VisualSearchStage.CONVERTING ->
                stringResource(R.string.visual_search_stage_converting)
            null -> null
        }
        visualSearchConvertRequest?.let { convertMedia ->
            VisualSearchConvertSheet(
                mediaLabel = convertMedia.label,
                providerLabel = visualSearchTarget?.displayName.orEmpty(),
                convertFormat = visualSearchConvertFormat,
                onFormatChange = { visualSearchConvertFormat = it },
                onConvert = { rememberChoice ->
                    if (rememberChoice) {
                        visualSearchConvertMode = Settings.Misc.VISUAL_SEARCH_CONVERT_ALWAYS
                    }
                    visualSearchConvertRequest = null
                    visualSearchTarget?.let { target ->
                        launchVisualSearch(
                            convertMedia,
                            metadataState.value.metadataMap[convertMedia.id],
                            currentVault,
                            target,
                            null,
                            visualSearchConvertFormat.toVisualSearchWriteFormat(),
                        )
                    }
                },
                onSendOriginal = { rememberChoice ->
                    if (rememberChoice) {
                        visualSearchConvertMode = Settings.Misc.VISUAL_SEARCH_CONVERT_NEVER
                    }
                    visualSearchConvertRequest = null
                    visualSearchTarget?.let { target ->
                        launchVisualSearch(
                            convertMedia,
                            metadataState.value.metadataMap[convertMedia.id],
                            currentVault,
                            target,
                            null,
                            null,
                        )
                    }
                },
                onDismiss = { visualSearchConvertRequest = null },
            )
        }

        // Key rotation helpers by the *settled* pager media id, not currentMedia?.id.
        // During a cancelled swipe the pager's currentPage briefly flips to the neighbour
        // page and back; keying off it would reset this rememberSaveable state and make the
        // pending rotate button vanish (#962). settledPage only advances once a scroll fully
        // settles on a new page, so a cancelled swipe keeps the rotation state intact.
        // (Keying by media id also avoids a Serializable fallback of the whole Media object.)
        val settledRotationKey by rememberedDerivedState(pagerItems) {
            pagerItems.getOrNull(pagerState.settledPage)?.id ?: currentMedia?.id ?: -1L
        }
        val newRotationValue = rememberSaveable(settledRotationKey) { mutableIntStateOf(0) }
        val showRotationHelper = rememberSaveable(settledRotationKey) { mutableStateOf(false) }
        // The media the pending rotation was created on. During a cancelled swipe the
        // neighbour page is transiently composed and can emit rotation events, so the
        // apply path must resolve this id rather than trusting currentPage (#962).
        val rotationMediaId = rememberSaveable(settledRotationKey) { mutableLongStateOf(-1L) }

        // Drives the top-bar Rotate chip busy state and the seamless hold-until-reload behavior.
        val rotation by rotationState.collectAsStateWithLifecycle()
        val rotationInProgress = rotation != null
        val rotationStageLabel = when (rotation?.stage) {
            MediaViewViewModel.RotationStage.DECODING -> stringResource(R.string.rotate_stage_decoding)
            MediaViewViewModel.RotationStage.ROTATING -> stringResource(R.string.rotate_stage_rotating)
            MediaViewViewModel.RotationStage.SAVING -> stringResource(R.string.rotate_stage_saving)
            MediaViewViewModel.RotationStage.UPLOADING -> stringResource(R.string.rotate_stage_uploading)
            null -> null
        }

        LaunchedEffect(initialUiVisible, showUI) {
            if (!initialUiVisible && !showUI) windowInsetsController.toggleSystemBars(show = false)
        }

        BackHandler(!showUI && !slideshowActive) {
            windowInsetsController.toggleSystemBars(show = true)
            requestViewerDismiss()
        }
        // Exiting the slideshow returns to the normal viewer (chrome restored) rather than popping
        // the screen. A second back then leaves the viewer as usual.
        val exitSlideshow = {
            slideshowActive = false
            slideshowPaused = false
            slideshowControlsVisible = false
            showUI = true
            windowInsetsController.toggleSystemBars(show = true)
        }
        BackHandler(slideshowActive) { exitSlideshow() }

        LaunchedEffect(slideshowActive, mediaState.value.isLoading, pagerItems.isEmpty()) {
            if (shouldExitEmptySlideshow(
                    isActive = slideshowActive,
                    isLoading = mediaState.value.isLoading,
                    hasItems = pagerItems.isNotEmpty(),
                )
            ) {
                exitSlideshow()
            }
        }

        // Hide all chrome and keep the screen awake while the slideshow is running.
        val slideshowView = LocalView.current
        LaunchedEffect(slideshowActive) {
            if (slideshowActive) {
                showUI = false
                windowInsetsController.toggleSystemBars(show = false)
            }
        }
        DisposableEffect(slideshowActive, slideshowPaused) {
            slideshowView.keepScreenOn = slideshowActive && !slideshowPaused
            onDispose { slideshowView.keepScreenOn = false }
        }

        val activity = LocalActivity.current

        // Reset forced orientation when leaving the media view screen
        DisposableEffect(activity) {
            onDispose {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }

        val onSides = rememberNavigationBarOnSides()
        val isGestureEnabled = rememberGestureNavigationEnabled()
        // Extra padding for navigation bar with 3/2-buttons
        val extraPaddingWithNavButtons by remember(onSides, isGestureEnabled) {
            mutableStateOf(
                if (!isGestureEnabled) {
                    32.dp
                } else 0.dp
            )
        }
        val bottomBarHeightDefault by remember(onSides) {
            mutableStateOf(BOTTOM_BAR_HEIGHT)
        }

        // Read live: paddingValues is a stable, lazily-evaluated object, so caching its
        // calculateBottomPadding() in a remember keyed on the object would never update
        // on rotation. Reading it directly subscribes to the inset state (#929).
        val bottomPadding = paddingValues.calculateBottomPadding()

        val imageOnlyHeight =
            bottomBarHeightDefault + extraPaddingWithNavButtons + bottomPadding + 16.dp
        val imageOnlyDetent = remember(imageOnlyHeight) { ImageOnly { imageOnlyHeight } }

        val expandedDetent = remember { FullyExpanded }

        // Recreate the sheet state when the imageOnly detent height settles. The library's
        // rememberBottomSheetState captures detents once and updateAnchors does NOT move an
        // idle sheet when only the current detent's height changes — so after a rotation the
        // sheet stayed anchored at the portrait height. Rotation emits two frames (an
        // intermediate one with stale insets still at the old height, then the correct one),
        // and portrait + the intermediate frame share the same height, so keying on
        // imageOnlyHeight recreates the state exactly once, on the final correct value —
        // taking the same code path as opening fresh in landscape (which always worked). (#929)
        val sheetState = key(imageOnlyHeight) {
            rememberBottomSheetState(
                initialDetent = imageOnlyDetent,
                detents = listOf(imageOnlyDetent, expandedDetent),
                positionalThreshold = { it },
                velocityThreshold = { 1000.dp }
            )
        }

        val userScrollEnabled by rememberedDerivedState { sheetState.currentDetent != FullyExpanded }
        val tapNavigationPromptState = rememberAppBottomSheetState()

        // Tap on the media. In a slideshow we only toggle the minimal transport bar (leaving the
        // full viewer chrome hidden); otherwise we toggle the normal chrome as before.
        val onMediaClick = {
            if (slideshowActive) {
                slideshowControlsVisible = !slideshowControlsVisible
            } else if (sheetState.currentDetent == imageOnlyDetent) {
                showUI = !showUI
                windowInsetsController.toggleSystemBars(showUI)
            }
        }

        var isLocked by rememberSaveable { mutableStateOf(false) }
        var viewerWidth by remember { mutableIntStateOf(0) }
        val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        val tapZoneAt: (Offset) -> TapNavigationZone = { offset ->
            resolveTapNavigationZone(
                tapX = offset.x,
                viewportWidth = viewerWidth,
                isRtl = isRtl,
            )
        }
        val canNavigateByTap = !isStandalone &&
                !slideshowActive &&
                !isLocked &&
                !isImageZoomed &&
                !isVideoZoomed &&
                !isCutoutActive &&
                sheetState.currentDetent == imageOnlyDetent &&
                sheetState.progress(imageOnlyDetent, expandedDetent) == 0f &&
                !pagerState.isScrollInProgress
        val tapNavigationTarget: (TapNavigationZone) -> Int? = { zone ->
            if (canNavigateByTap) {
                resolveTapNavigationTarget(
                    zone = zone,
                    currentPage = pagerState.settledPage,
                    pageCount = pagerItems.size,
                )
            } else {
                null
            }
        }
        val onMediaTap: (TapNavigationZone) -> Unit = { zone ->
            if (!tapSidesToNavigate || zone == TapNavigationZone.Center || !canNavigateByTap) {
                onMediaClick()
            } else {
                tapNavigationTarget(zone)?.let { targetPage ->
                    scope.launch {
                        if (targetPage in pagerItems.indices && !pagerState.isScrollInProgress) {
                            pagerState.scrollToPage(targetPage)
                        }
                    }
                }
            }
        }
        val onImageImmediateTap: (Offset) -> Boolean = { offset ->
            val zone = tapZoneAt(offset)
            if (shouldHandleTapImmediately(
                    tapSidesToNavigate,
                    zone,
                    tapNavigationTarget(zone) != null
                )
            ) {
                onMediaTap(zone)
                true
            } else {
                false
            }
        }
        // Guard against a second swipe-down firing while the dismiss/pop transition is still in
        // flight. The viewer stays composed and gesture-active during the animation, so a second
        // swipe would trigger another navigateUp and pop past the gallery, exiting the app.
        var isDismissing by remember { mutableStateOf(false) }
        // Override back button/gesture when locked
        BackHandler(enabled = isLocked) { }
        // Overlay viewer: predictive back scrubs the return flight toward the source cell — the
        // same manual morph a committed swipe-dismiss runs — while scrim/chrome fade with gesture
        // progress. Without a mapped cell the gesture still resolves as a plain dismiss.
        if (overlayMode) {
            PredictiveBackHandler(
                enabled = showUI && !slideshowActive && !isLocked && !isDismissing
            ) { events ->
                viewerDismissState.onPredictiveBack(
                    events = events,
                    elementKey = pagerItems.getOrNull(currentPage)?.id?.let {
                        MediaSharedElementKey.MediaKey(it)
                    },
                    media = currentMedia,
                ) {
                    isDismissing = true
                    windowInsetsController.toggleSystemBars(show = true)
                    dismissViewer()
                }
            }
        } else {
            BackHandler(enabled = showUI && !slideshowActive && !isLocked) {
                windowInsetsController.toggleSystemBars(show = true)
                requestViewerDismiss()
            }
        }

        var tapNavigationPromptClaimed by rememberSaveable { mutableStateOf(false) }
        val currentTapNavigationMetadata =
            currentMedia?.id?.let(metadataState.value.metadataMap::get)
        val tapNavigationPromptEligible = isTapNavigationPromptEligible(
            tapNavigationEnabled = tapSidesToNavigate,
            isStandalone = isStandalone,
            slideshowActive = slideshowActive,
            pageCount = pagerItems.size,
            initialPageSetup = initialPageSetup,
            isOrdinaryImage = currentMedia?.isImage == true &&
                    currentTapNavigationMetadata != null &&
                    !currentTapNavigationMetadata.isPanorama &&
                    !currentTapNavigationMetadata.isPhotosphere,
            viewerSettled = navigationChromeVisible &&
                    viewerInteractive &&
                    !isLocked &&
                    !isDismissing &&
                    !isImageZoomed &&
                    !isCutoutActive &&
                    !pagerState.isScrollInProgress &&
                    sheetState.currentDetent == imageOnlyDetent &&
                    sheetState.progress(imageOnlyDetent, expandedDetent) == 0f,
        )
        LaunchedEffect(tapNavigationPromptEligible) {
            if (tapNavigationPromptEligible && !tapNavigationPromptClaimed) {
                tapNavigationPromptClaimed = true
                if (Settings.Misc.claimTapSidesToNavigatePrompt(context)) {
                    try {
                        tapNavigationPromptState.show()
                    } catch (error: Throwable) {
                        withContext(NonCancellable) {
                            Settings.Misc.releaseTapSidesToNavigatePrompt(context)
                        }
                        throw error
                    }
                }
            }
        }
        TapNavigationPromptSheet(
            state = tapNavigationPromptState,
            onEnable = { tapSidesToNavigate = true },
        )


        LaunchedEffect(mediaState.value) {
            snapshotFlow { pagerState.currentPage }.collectLatest { page ->
                if (!mediaState.value.isLoading && pagerItems.isEmpty() && !isStandalone) {
                    windowInsetsController.toggleSystemBars(show = true)
                    dismissViewer()
                }
                if (!mediaState.value.isLoading) {
                    currentPage = page
                }
            }
        }

        // set HDR Gain map (only on displays that can actually render HDR — skips the probe decode on
        // SDR-only devices, where the window would never enter COLOR_MODE_HDR anyway)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            HdrCapabilities.isHdrDisplay(context)
        ) {
            val hdrCache = remember { HashMap<Long, Boolean>() }
            LaunchedEffect(mediaState.value) {
                withContext(Dispatchers.IO) {
                    snapshotFlow { pagerState.currentPage }.collectLatest {
                        printWarning("Trying to set HDR mode for page $it")
                        val media = currentMedia
                        if (media?.isImage == true) {
                            val cached = hdrCache[media.id]
                            if (cached != null) {
                                withContext(Dispatchers.Main.immediate) {
                                    context.setHdrMode(cached)
                                }
                                printWarning("Setting HDR Mode to $cached (cached)")
                            } else {
                                val request = ImageRequest(context, media.getUri().toString()) {
                                    setExtra(
                                        key = "mediaKey",
                                        value = media.idLessKey,
                                    )
                                    setExtra(
                                        key = "realMimeType",
                                        value = media.mimeType,
                                    )
                                    // Always decode from the source for the gain-map probe.
                                    // Sketch's result cache re-encodes bitmaps with
                                    // Bitmap.compress, which strips the Ultra HDR gain map, so a
                                    // cached result would report hasGainmap()=false and leave the
                                    // window in SDR mode after the app restarts (#998).
                                    resultCachePolicy(CachePolicy.DISABLED)
                                    memoryCachePolicy(CachePolicy.DISABLED)
                                }
                                val result = context.sketch.execute(request)
                                (result.image as? BitmapImage)?.bitmap?.let { bitmap ->
                                    val hasGainmap = bitmap.hasGainmap()
                                    hdrCache[media.id] = hasGainmap
                                    withContext(Dispatchers.Main.immediate) {
                                        context.setHdrMode(hasGainmap)
                                    }
                                    printWarning("Setting HDR Mode to $hasGainmap")
                                } ?: printWarning("Resulting image null")
                            }
                        } else {
                            withContext(Dispatchers.Main.immediate) {
                                context.setHdrMode(false)
                            }
                            printWarning("Not an image, skipping")
                        }
                    }
                }
            }

            DisposableEffect(Unit) {
                onDispose {
                    printWarning("Disposing HDR Mode")
                    context.setHdrMode(false)
                }
            }
        }

        // ── PixelCopy-based real-time luminance detection ──
        val pixelCopyThread = remember {
            HandlerThread("AutoContrastThread").apply { start() }
        }
        val pixelCopyHandler = remember(pixelCopyThread) {
            Handler(pixelCopyThread.looper)
        }
        DisposableEffect(Unit) {
            onDispose { pixelCopyThread.quitSafely() }
        }

        LaunchedEffect(autoContrast, activity, currentMedia?.id) {
            isTopDark = false
            isBottomDark = false
            if (!autoContrast || activity == null) {
                return@LaunchedEffect
            }

            // Wait for the image to render before capturing
            delay(350.milliseconds)

            val window = activity.window
            val captureW = 32

            val decorView = window.decorView
            val screenW = decorView.width
            val screenH = decorView.height
            if (screenW > 0 && screenH > 0) {
                val captureH = (captureW * screenH.toFloat() / screenW)
                    .toInt().coerceAtLeast(1)
                val dest = createBitmap(captureW, captureH)
                try {
                    suspendCancellableCoroutine { cont ->
                        PixelCopy.request(
                            window,
                            Rect(0, 0, screenW, screenH),
                            dest,
                            { result ->
                                if (result == PixelCopy.SUCCESS) {
                                    val w = dest.width
                                    val h = dest.height
                                    val pixels = IntArray(w * h)
                                    dest.getPixels(pixels, 0, w, 0, 0, w, h)

                                    val topRows = (h * 0.15f).toInt().coerceAtLeast(1)
                                    val bottomStart = h - (h * 0.15f).toInt().coerceAtLeast(1)

                                    var topLum = 0.0
                                    var topCnt = 0
                                    for (y in 0 until topRows) {
                                        for (x in 0 until w) {
                                            val p = pixels[y * w + x]
                                            topLum += 0.299 * ((p shr 16) and 0xFF) +
                                                    0.587 * ((p shr 8) and 0xFF) +
                                                    0.114 * (p and 0xFF)
                                            topCnt++
                                        }
                                    }

                                    var btmLum = 0.0
                                    var btmCnt = 0
                                    for (y in bottomStart until h) {
                                        for (x in 0 until w) {
                                            val p = pixels[y * w + x]
                                            btmLum += 0.299 * ((p shr 16) and 0xFF) +
                                                    0.587 * ((p shr 8) and 0xFF) +
                                                    0.114 * (p and 0xFF)
                                            btmCnt++
                                        }
                                    }

                                    isTopDark = topCnt > 0 &&
                                            (topLum / topCnt / 255.0) < 0.4
                                    isBottomDark = btmCnt > 0 &&
                                            (btmLum / btmCnt / 255.0) < 0.4
                                }
                                dest.recycle()
                                cont.resumeWith(Result.success(Unit))
                            },
                            pixelCopyHandler
                        )
                    }
                } catch (_: Exception) {
                    dest.recycle()
                }
            }
        }

        LaunchedEffect(uiEvents, rotateFailedText, visualSearchFailedText) {
            uiEvents.collect { event ->
                when (event) {
                    MediaViewEvent.ScrollToFirstPage -> pagerState.animateScrollToPage(0)

                    is MediaViewEvent.NavigateToRotatedCopy -> {
                        // Wait (briefly) for the new copy to be indexed into the pager, then jump to it.
                        val targetIndex = withTimeoutOrNull(5.seconds) {
                            snapshotFlow {
                                pagerItems.indexOfFirst {
                                    it.getUri().toString() == event.uri
                                }
                            }.first { it >= 0 }
                        }
                        pagerState.animateScrollToPage(targetIndex ?: 0)
                    }

                    is MediaViewEvent.OverwriteApplied -> {
                        // The rotation is now persisted into the file; drop the pending-confirm chip.
                        // The page holds its visual rotation and drops it once the baked-in image
                        // reloads (handled in ZoomablePagerImage), so we stay on the same item.
                        if (rotationMediaId.longValue == event.mediaId ||
                            currentMedia?.id == event.mediaId
                        ) {
                            showRotationHelper.value = false
                            newRotationValue.intValue = 0
                            rotationMediaId.longValue = -1L
                        }
                    }

                    is MediaViewEvent.RotationFailed -> {
                        Toast.makeText(
                            context,
                            event.message ?: rotateFailedText,
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    is MediaViewEvent.LaunchVisualSearch -> {
                        // Fired with the Activity context so the provider opens on the same task.
                        runCatching {
                            context.launchVisualSearch(event.target, event.uri, event.mimeType)
                        }.onFailure {
                            Toast.makeText(
                                context,
                                visualSearchFailedText,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    is MediaViewEvent.VisualSearchFailed -> {
                        Toast.makeText(
                            context,
                            event.message ?: visualSearchFailedText,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }

        // Slideshow auto-advance: images dwell for the configured interval, videos play through once,
        // and failed media remains readable briefly before being skipped. The pure edge policy below
        // prevents invalid page requests for empty/single-item playlists.
        val currentMediaFailed = currentMedia?.id in failedMediaIds
        LaunchedEffect(
            slideshowActive,
            slideshowPaused,
            currentPage,
            currentMedia?.id,
            currentMediaFailed,
            initialPageSetup,
        ) {
            val cfg = slideshowConfig
            if (!slideshowActive || slideshowPaused || !initialPageSetup || cfg == null) {
                return@LaunchedEffect
            }
            val media = currentMedia ?: return@LaunchedEffect
            val dwellMillis = slideshowDwellMillis(cfg, media.isVideo, currentMediaFailed)
            if (dwellMillis != null) {
                delay(dwellMillis)
            } else {
                videoEndedFlow.first { it == media.id }
            }
            if (!slideshowActive || slideshowPaused) return@LaunchedEffect

            // The scroll MUST run in an external scope, not this effect: pagerState.currentPage flips
            // to the target at the half-way point of the animation, which mutates this effect's
            // `currentPage` key and would cancel animateScrollToPage mid-flight.
            when (val advance = resolveSlideshowAdvance(pagerItems.size, currentPage, cfg.loop)) {
                is SlideshowAdvance.Page -> scope.launch {
                    pagerState.animateScrollToPage(advance.index)
                }

                SlideshowAdvance.Exit -> exitSlideshow()
                SlideshowAdvance.Hold -> Unit
            }
        }

        FullBrightnessWindow {
            val isDarkTheme = isDarkTheme()
            val visualPolicy = LocalMediaViewerVisualPolicy.current
            val backgroundColor =
                if (visualPolicy.usesDarkBackground(isDarkTheme)) Color.Black else Color.White
            val dismissGestureEnabled = overlayMode &&
                    !isLocked &&
                    !isImageZoomed &&
                    !isVideoZoomed &&
                    !isCutoutActive &&
                    !slideshowActive &&
                    !pagerState.isScrollInProgress &&
                    sheetState.progress(imageOnlyDetent, expandedDetent) == 0f
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // The scrim is this box's own background — pinned to the screen while it fades
                    // with gesture progress (the media slides away on an inner offset).
                    .background(backgroundColor.copy(alpha = dismissAlpha))
                    .onSizeChanged { viewerDismissState.updateSize(it) }
                    // The dismiss gesture lives on the outermost viewer box, which is never
                    // transformed: the drag offset lands on an inner "card" (the shared-element
                    // box), so pointer positions here always arrive in untranslated screen space.
                    // A handler inside the offset card would see each applied offset subtracted
                    // from the next delta — halved tracking speed and a fast up/down oscillation.
                    .pointerInput(dismissGestureEnabled) {
                        if (dismissGestureEnabled) {
                            // Uptime of the last clean tap. A down arriving inside the
                            // double-tap window is the second press of the image's
                            // one-finger zoom (double-tap-hold and drag) and must reach
                            // ZoomImage instead of arming swipe-to-dismiss — this detector
                            // reads the Initial pass, so a downward drag would otherwise be
                            // consumed before zoomable ever sees it (#1226).
                            var lastTapUpUptime = Long.MIN_VALUE
                            awaitEachGesture {
                                val down = awaitFirstDown(
                                    requireUnconsumed = false,
                                    pass = PointerEventPass.Initial,
                                )
                                val doubleTapHold = isSecondTapPress(
                                    downUptime = down.uptimeMillis,
                                    lastTapUpUptime = lastTapUpUptime,
                                    doubleTapMinMillis = viewConfiguration.doubleTapMinTimeMillis,
                                    doubleTapTimeoutMillis = viewConfiguration.doubleTapTimeoutMillis,
                                )
                                var lastPosition = down.position
                                var totalDrag = Offset.Zero
                                var draggingToDismiss = false
                                var cancelled = false
                                var upChange: PointerInputChange? = null
                                try {
                                    do {
                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                        if (event.changes.count { it.pressed } > 1) {
                                            cancelled = true
                                            break
                                        }
                                        val change = event.changes.firstOrNull { it.id == down.id }
                                        if (change == null) {
                                            cancelled = true
                                            break
                                        }
                                        if (!change.pressed) upChange = change
                                        val delta = change.position - lastPosition
                                        totalDrag += delta
                                        if (!doubleTapHold &&
                                            !draggingToDismiss &&
                                            totalDrag.y > viewConfiguration.touchSlop &&
                                            abs(totalDrag.y) > abs(totalDrag.x)
                                        ) {
                                            viewerDismissState.start(
                                                scope,
                                                // The shared element keys off the raw pager item,
                                                // not currentMedia (which may resolve to a group
                                                // member) — arm that same key or the suppression
                                                // misses the element.
                                                pagerItems.getOrNull(currentPage)?.id?.let {
                                                    MediaSharedElementKey.MediaKey(it)
                                                },
                                                currentMedia,
                                            )
                                            draggingToDismiss = true
                                        }
                                        if (draggingToDismiss) {
                                            viewerDismissState.dragBy(delta.y)
                                            change.consume()
                                        }
                                        lastPosition = change.position
                                    } while (change.pressed)
                                    // Remember a clean tap so the next gesture inside the
                                    // double-tap window is left to the image's one-finger
                                    // zoom; anything else resets the window.
                                    val up = upChange
                                    lastTapUpUptime = if (!cancelled && !draggingToDismiss &&
                                        up != null && isCleanTap(
                                            upUptime = up.uptimeMillis,
                                            downUptime = down.uptimeMillis,
                                            dragDistance = totalDrag.getDistance(),
                                            longPressTimeoutMillis =
                                                viewConfiguration.longPressTimeoutMillis,
                                            touchSlop = viewConfiguration.touchSlop,
                                        )
                                    ) {
                                        up.uptimeMillis
                                    } else {
                                        Long.MIN_VALUE
                                    }
                                    if (draggingToDismiss) {
                                        if (cancelled) {
                                            viewerDismissState.cancel(scope)
                                        } else {
                                            viewerDismissState.finish(scope) {
                                                isDismissing = true
                                                windowInsetsController.toggleSystemBars(show = true)
                                                dismissViewer()
                                            }
                                        }
                                    }
                                } catch (e: CancellationException) {
                                    // The pointerInput key (media id / gesture eligibility) can
                                    // flip mid-drag and cancel this block; never leave the
                                    // dismiss state stuck in gestureActive.
                                    if (draggingToDismiss) viewerDismissState.cancel(scope)
                                    throw e
                                }
                            }
                        }
                    }
            ) {
                HorizontalPager(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { viewerWidth = it.width },
                    userScrollEnabled = userScrollEnabled &&
                            viewerInteractive &&
                            !isLocked &&
                            !isVideoZoomed &&
                            !isCutoutActive &&
                            (!slideshowActive || slideshowPaused),
                    state = pagerState,
                    flingBehavior = PagerDefaults.flingBehavior(
                        state = pagerState,
                        snapAnimationSpec = spring(
                            stiffness = Spring.StiffnessMedium
                        ),
                        snapPositionalThreshold = 0.3f
                    ),
                    key = { index ->
                        pagerItems.getOrNull(index)?.id ?: "empty_$index"
                    },
                    pageSpacing = 16.dp,
                    beyondViewportPageCount = 0
                ) { index ->
                    val pagerMedia by rememberedDerivedState(pagerItems, index) {
                        pagerItems.getOrNull(index)
                    }
                    // Show the selected group member if on current page, otherwise the pager item
                    val media by rememberedDerivedState(
                        pagerMedia,
                        selectedMemberOverrideId,
                        currentPage,
                        index
                    ) {
                        if (index == currentPage) {
                            val groupMembers =
                                pagerMedia?.let { mediaState.value.mediaGroups[it.id] }
                            if (selectedMemberOverrideId != null) {
                                groupMembers?.find { it.id == selectedMemberOverrideId }
                                    ?: pagerMedia
                            } else {
                                groupMembers?.firstOrNull() ?: pagerMedia
                            }
                        } else {
                            pagerMedia
                        }
                    }
                    val mediaMetadata by rememberedDerivedState(metadataState.value, media) {
                        media?.id?.let { metadataState.value.metadataMap[it] }
                    }
                    val canPlay = rememberSaveable(media) { mutableStateOf(false) }
                    var canAnimateContent by rememberSaveable(media) { mutableStateOf(true) }
                    LaunchedEffect(
                        viewerInteractive,
                        index,
                        currentPage,
                        playWhenReady,
                        media?.isVideo
                    ) {
                        canPlay.value = media?.isVideo == true &&
                                index == currentPage &&
                                playWhenReady &&
                                viewerInteractive
                    }

                    // ── Slideshow transitions ──
                    val transition = slideshowConfig?.transition
                    val fadeEnabled = slideshowActive &&
                            (transition == SlideshowTransition.FADE || transition == SlideshowTransition.KEN_BURNS)
                    val kenBurnsEnabled = slideshowActive && media?.isVideo != true &&
                            slideshowConfig != null &&
                            (transition == SlideshowTransition.KEN_BURNS || slideshowConfig.kenBurns)
                    val kenBurnsScale = remember(media?.id) { Animatable(1f) }
                    LaunchedEffect(
                        media?.id,
                        index,
                        currentPage,
                        slideshowActive,
                        slideshowPaused,
                        kenBurnsEnabled
                    ) {
                        if (kenBurnsEnabled && index == currentPage && !slideshowPaused) {
                            kenBurnsScale.snapTo(1f)
                            kenBurnsScale.animateTo(
                                targetValue = 1.12f,
                                animationSpec = tween(
                                    durationMillis = (slideshowConfig?.intervalMillis
                                        ?: 5000L).toInt(),
                                    easing = LinearEasing
                                )
                            )
                        } else {
                            kenBurnsScale.snapTo(1f)
                        }
                    }
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Shared-element box: composed from the first transition frame and wraps the real
                        // media (over the grid-prefetched thumbnail), so sharedBounds morphs cell↔viewer
                        // bounds. It doubles as the dismiss "card": the drag offset lands here, upstream
                        // of sharedBounds, and the committed return flight morphs from the release bounds.
                        val sharedElementMedia = pagerMedia ?: media
                        val sharedElementActive =
                            sharedElementMedia != null && isMediaViewerSharedElementPage(
                                page = index,
                                currentPage = pagerState.currentPage,
                            )
                        // The low-res surrogate rides the drag offset *behind* the pinned
                        // BlurredMediaBackground (drawn first, MPC's backdrop inside the
                        // shared-element box second) — stacking: thumbnail → blur → media.
                        // Fit so it underlays exactly the media's letterboxed bounds and the
                        // backdrop owns the margins; route mode keeps Crop because the
                        // framework's sharedBounds morph animates the box from the
                        // Crop-rendered cell.
                        if (sharedElementActive) {
                            ViewerSharedElementThumbnail(
                                media = sharedElementMedia,
                                contentScale = if (viewerDismissState.usesOverlayTransform) {
                                    ContentScale.Fit
                                } else {
                                    ContentScale.Crop
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .offset {
                                        IntOffset(0, viewerDismissState.offsetY.roundToInt())
                                    }
                                    // This sibling is outside the shared-element box's alpha
                                    // gate, so it needs its own — a committed/back/enter
                                    // flight owns the media's visual and the root layer
                                    // draws it.
                                    .graphicsLayer {
                                        alpha = if (isViewerSurrogateVisible(
                                                contentReady = viewerContentReady,
                                                transitionRunning =
                                                    animatedContentScope.transition.isRunning,
                                                dismissActive = viewerDismissState.isActive,
                                                dismissedVisualHidden =
                                                    viewerDismissState.isDismissedVisualHidden(
                                                        MediaSharedElementKey.MediaKey(
                                                            sharedElementMedia.id
                                                        )
                                                    ),
                                            )
                                        ) 1f else 0f
                                    },
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                // The dismiss "card" itself is never translated: the drag offset lands
                                // on the thumbnail below and on MediaPreviewComponent's translating
                                // content box, so the blurred backdrop — a sibling of that box — stays
                                // pinned to the screen (pre-card parity). Translating this outer box
                                // instead would drag the gesture-tracking bounds and clip a pinned
                                // blur at its own top edge.
                                .then(
                                    if (sharedElementActive) {
                                        with(sharedTransitionScope) {
                                            Modifier.mediaSharedElement(
                                                media = sharedElementMedia,
                                                animatedVisibilityScope = animatedContentScope,
                                                permitTransformDuringDeferredTransition = true,
                                            )
                                        }
                                    } else {
                                        Modifier
                                    }
                                )
                                .clipToBounds()
                                .graphicsLayer {
                                    // After a committed dismiss this media's in-place copy stays hidden:
                                    // the root flight layer morphs it to the cell during the flight, and
                                    // through the retained exit the real cell is already back on screen.
                                    alpha = if (sharedElementMedia != null &&
                                        viewerDismissState.isDismissedVisualHidden(
                                            MediaSharedElementKey.MediaKey(sharedElementMedia.id)
                                        )
                                    ) 0f else 1f
                                },
                        ) {
                            AnimatedVisibility(
                                modifier = Modifier
                                    .onVisibilityChanged { isVisible ->
                                        canPlay.value =
                                            (if (media?.isVideo == true) isVisible && playWhenReady else false)
                                        canAnimateContent = isVisible
                                    }
                                    .graphicsLayer {
                                        if (fadeEnabled) {
                                            // Cancel the pager's horizontal translation so pages cross-fade
                                            // in place instead of sliding.
                                            val off = (((pagerState.currentPage - index) +
                                                    pagerState.currentPageOffsetFraction)).coerceIn(
                                                -1f,
                                                1f
                                            )
                                            translationX = size.width * off
                                            alpha = (1f - abs(off)).coerceIn(0f, 1f)
                                        }
                                        if (kenBurnsEnabled) {
                                            scaleX = kenBurnsScale.value
                                            scaleY = kenBurnsScale.value
                                        }
                                    },
                                // In overlay mode the media rides the shared element through the dismiss —
                                // keep it composed so the flying overlay copy draws it. In route mode the
                                // committed dismiss hands the visual to the thumbnail element layer.
                                visible = media != null && viewerContentReady &&
                                        (viewerDismissState.usesOverlayTransform || !viewerDismissState.isCommitted),
                                enter = enterAnimation,
                                exit = if (viewerDismissState.isCommitted) ExitTransition.None else exitAnimation
                            ) {
                                var offset by remember {
                                    mutableStateOf(IntOffset(0, 0))
                                }
                                val displayMedia = media ?: return@AnimatedVisibility
                                MediaPreviewComponent(
                                    isSelected = index == currentPage,
                                    // Skip the fullscreen `.blur(100.dp)` backdrop while the
                                    // shared-element open/close transition is animating — it's
                                    // invisible during the animation but a heavy per-frame render pass
                                    // that stutters the transition. It fades in once the page settles.
                                    // Also skip it during a slideshow cross-fade: two stacked blurred
                                    // backdrops would blend into a "phantom" ghost image behind the
                                    // current page.
                                    renderBackground = !animatedContentScope.transition.isRunning &&
                                            !fadeEnabled,
                                    backdropGestureAlpha = backdropAlpha,
                                    swipeToDismissEnabled = !overlayMode,
                                    modifier = Modifier.fillMaxSize(),
                                    containerModifier = Modifier,
                                    media = media,
                                    uiEnabled = showUI,
                                    playWhenReady = canPlay,
                                    slideshowActive = slideshowActive,
                                    cutoutEnabled = currentCapabilities?.cutout == true,
                                    onLoadFailed = {
                                        media?.id?.let { failedMediaIds = failedMediaIds + it }
                                    },
                                    onVideoEnded = {
                                        media?.id?.let { videoEndedFlow.tryEmit(it) }
                                    },
                                    onSwipeDown = {
                                        if (!overlayMode && !isLocked && !isDismissing) {
                                            isDismissing = true
                                            windowInsetsController.toggleSystemBars(show = true)
                                            dismissViewer()
                                        }
                                    },
                                    offset = if (overlayMode) {
                                        // Overlay mode drives the drag offset through the state's
                                        // tracked value (the outer box's pointerInput writes it).
                                        IntOffset(0, viewerDismissState.offsetY.roundToInt())
                                    } else {
                                        offset
                                    },
                                    isPanorama = mediaMetadata?.isPanorama == true,
                                    isPhotosphere = mediaMetadata?.isPhotosphere == true,
                                    isMotionPhoto = mediaMetadata?.isMotionPhoto == true,
                                    motionPhotoState = motionPhotoState,
                                    currentVault = currentVault,
                                    rotationDisabled = isLocked || currentCapabilities?.rotate != true,
                                    onImageRotated = { newRotation ->
                                        // Reduce the accumulated rotation to the 0..359 range so that
                                        // every full turn (360, 720, ...) is treated as "no rotation".
                                        val normalizedRotation =
                                            ((newRotation % 360) + 360) % 360
                                        showRotationHelper.value =
                                            media?.isImage == true && normalizedRotation != 0
                                        newRotationValue.intValue =
                                            (if (showRotationHelper.value) normalizedRotation else 0)
                                        if (showRotationHelper.value) {
                                            rotationMediaId.longValue = media?.id ?: -1L
                                        }
                                    },
                                    onItemClick = { if (viewerInteractive) onMediaClick() },
                                    onImageTap = { offset ->
                                        if (viewerInteractive) onMediaTap(tapZoneAt(offset))
                                    },
                                    onImageImmediateTap = if (viewerInteractive) {
                                        onImageImmediateTap
                                    } else {
                                        { false }
                                    },
                                    onZoomChange = { zoomed -> isVideoZoomed = zoomed },
                                    onImageZoomChange = { zoomed -> isImageZoomed = zoomed },
                                    // Only the settled/current page drives the top-bar loading
                                    // indicator; neighbour pages that transiently compose during a
                                    // fling must not toggle it.
                                    onSubsamplingLoadingChange = if (index == currentPage) {
                                        { loading -> subsamplingLoading = loading }
                                    } else {
                                        {}
                                    },
                                    onCutoutStateChanged = { active ->
                                        // Only react to a genuine cutout transition. The selected page
                                        // re-emits `false` on every swipe (its cutout is inactive), so
                                        // without this guard each page change would force `showUI = true`
                                        // and bring the controls back after the user hid them (#1033).
                                        if (active != isCutoutActive) {
                                            isCutoutActive = active
                                            if (active) {
                                                showUI = false
                                                windowInsetsController.toggleSystemBars(show = false)
                                            } else {
                                                showUI = true
                                                windowInsetsController.toggleSystemBars(show = true)
                                            }
                                        }
                                    },
                                    onCutoutController = if (index == currentPage) {
                                        { controller -> cutoutController = controller }
                                    } else {
                                        {}
                                    }
                                ) { player, isPlaying, currentTime, totalTime, buffer, frameRate, subtitleState ->
                                    if (index == currentPage) {
                                        SideEffect {
                                            videoFramePickerController.position = currentTime
                                            videoFramePickerController.player = player
                                        }
                                    }
                                    val subtitleTracks = subtitleState.subtitleTracks
                                    val onSelectSubtitle = subtitleState.onSelectSubtitle
                                    val onDisableSubtitles = subtitleState.onDisableSubtitles
                                    val addExternalSubtitle =
                                        subtitleState.onAddExternalSubtitle
                                    Box(
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        val hideUiOnPlay by rememberAutoHideOnVideoPlay()
                                        var uiInteracted by remember { mutableStateOf(false) }
                                        LaunchedEffect(isPlaying.value, hideUiOnPlay, showUI) {
                                            if (isPlaying.value && showUI && hideUiOnPlay && !uiInteracted) {
                                                // Wait up to 2s, but abort the auto-hide the moment the
                                                // user starts dragging the info sheet. During an active
                                                // swipe currentDetent still reads imageOnly, so watch the
                                                // sheet's drag progress instead: any non-zero progress
                                                // means a gesture is in flight and hiding the UI now would
                                                // make it vanish mid-swipe (#964).
                                                val sheetMoved = withTimeoutOrNull(2.seconds) {
                                                    snapshotFlow {
                                                        sheetState.progress(
                                                            imageOnlyDetent,
                                                            expandedDetent
                                                        )
                                                    }.first { it > 0f }
                                                }
                                                if (sheetMoved == null &&
                                                    sheetState.currentDetent == imageOnlyDetent &&
                                                    sheetState.progress(
                                                        imageOnlyDetent,
                                                        expandedDetent
                                                    ) == 0f
                                                ) {
                                                    showUI = false
                                                    windowInsetsController.toggleSystemBars(
                                                        false
                                                    )
                                                }
                                            }
                                        }
                                        // Mute local player while casting (avoid double audio) or
                                        // during a slideshow (videos play muted).
                                        val isCasting = fcastState.connectedDevice != null
                                        LaunchedEffect(isCasting, slideshowActive) {
                                            if (isCasting || slideshowActive) {
                                                player.volume = 0f
                                            } else {
                                                player.volume = 1f
                                            }
                                        }
                                        val resources = LocalResources.current
                                        val videoConfiguration = LocalConfiguration.current
                                        val width =
                                            remember(videoConfiguration) { resources.displayMetrics.widthPixels }
                                        val navigateEndImmediately = shouldHandleTapImmediately(
                                            tapNavigationEnabled = tapSidesToNavigate,
                                            zone = TapNavigationZone.End,
                                            canNavigate = tapNavigationTarget(TapNavigationZone.End) != null,
                                        )
                                        val navigateStartImmediately =
                                            shouldHandleTapImmediately(
                                                tapNavigationEnabled = tapSidesToNavigate,
                                                zone = TapNavigationZone.Start,
                                                canNavigate = tapNavigationTarget(
                                                    TapNavigationZone.Start
                                                ) != null,
                                            )
                                        if (!isVideoZoomed && viewerInteractive) {
                                            Spacer(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .graphicsLayer {
                                                        translationX = width / 1.5f
                                                    }
                                                    .align(Alignment.TopEnd)
                                                    .clip(CircleShape)
                                                    .then(
                                                        if (navigateEndImmediately) {
                                                            Modifier.clickable(
                                                                interactionSource = remember { MutableInteractionSource() },
                                                                indication = null,
                                                                onClick = {
                                                                    onMediaTap(
                                                                        TapNavigationZone.End
                                                                    )
                                                                },
                                                            )
                                                        } else {
                                                            Modifier.combinedClickable(
                                                                interactionSource = remember { MutableInteractionSource() },
                                                                indication = null,
                                                                onDoubleClick = {
                                                                    scope.launch {
                                                                        currentTime.longValue += 10 * 1000
                                                                        player.seekTo(
                                                                            currentTime.longValue
                                                                        )
                                                                        delay(100.milliseconds)
                                                                        player.play()
                                                                    }
                                                                },
                                                                onClick = { onMediaClick() },
                                                            )
                                                        }
                                                    )
                                                    .swipe(
                                                        enabled = !overlayMode,
                                                        onOffset = { offset = it }) {
                                                        if (!isDismissing) {
                                                            isDismissing = true
                                                            windowInsetsController.toggleSystemBars(
                                                                show = true
                                                            )
                                                            dismissViewer()
                                                        }
                                                    }
                                            )

                                            Spacer(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .graphicsLayer {
                                                        translationX = -width / 1.5f
                                                    }
                                                    .align(Alignment.TopStart)
                                                    .clip(CircleShape)
                                                    .then(
                                                        if (navigateStartImmediately) {
                                                            Modifier.clickable(
                                                                interactionSource = remember { MutableInteractionSource() },
                                                                indication = null,
                                                                onClick = {
                                                                    onMediaTap(
                                                                        TapNavigationZone.Start
                                                                    )
                                                                },
                                                            )
                                                        } else {
                                                            Modifier.combinedClickable(
                                                                interactionSource = remember { MutableInteractionSource() },
                                                                indication = null,
                                                                onDoubleClick = {
                                                                    scope.launch {
                                                                        currentTime.longValue -= 10 * 1000
                                                                        player.seekTo(
                                                                            currentTime.longValue
                                                                        )
                                                                        delay(100.milliseconds)
                                                                        player.play()
                                                                    }
                                                                },
                                                                onClick = { onMediaClick() },
                                                            )
                                                        }
                                                    )
                                                    .swipe(
                                                        enabled = !overlayMode,
                                                        onOffset = { offset = it }) {
                                                        if (!isDismissing) {
                                                            isDismissing = true
                                                            windowInsetsController.toggleSystemBars(
                                                                show = true
                                                            )
                                                            dismissViewer()
                                                        }
                                                    }
                                            )
                                        }

                                        val onRemoveSubtitle = subtitleState.onRemoveSubtitle
                                        val subtitleSheetState = rememberAppBottomSheetState()

                                        val subtitleFilePicker =
                                            rememberLauncherForActivityResult(
                                                contract = ActivityResultContracts.OpenDocument()
                                            ) { uri: Uri? ->
                                                uri?.let { addExternalSubtitle(it) }
                                            }

                                        SubtitleBottomSheet(
                                            state = subtitleSheetState,
                                            subtitleTracks = subtitleTracks,
                                            onSelectSubtitle = onSelectSubtitle,
                                            onDisableSubtitles = onDisableSubtitles,
                                            onAddSubtitle = {
                                                subtitleFilePicker.launch(
                                                    arrayOf(
                                                        "application/x-subrip",
                                                        "application/ttml+xml",
                                                        "text/vtt",
                                                        "text/x-ssa",
                                                        "text/plain"
                                                    )
                                                )
                                            },
                                            onRemoveSubtitle = onRemoveSubtitle
                                        )

                                        AnimatedVisibility(
                                            visible = viewerInteractive && showViewerChrome,
                                            enter = enterAnimation(
                                                DEFAULT_TOP_BAR_ANIMATION_DURATION
                                            ),
                                            exit = exitAnimation(
                                                DEFAULT_TOP_BAR_ANIMATION_DURATION
                                            ),
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            VideoPlayerController(
                                                paddingValues = paddingValues,
                                                player = player,
                                                isPlaying = isPlaying,
                                                currentTime = currentTime,
                                                totalTime = totalTime,
                                                buffer = buffer,
                                                toggleRotate = toggleRotate,
                                                frameRate = frameRate,
                                                onCastSeek = if (fcastState.connectedDevice != null) {
                                                    { seconds -> fcastVm.seek(seconds) }
                                                } else null,
                                                onCastPlayPause = if (fcastState.connectedDevice != null) {
                                                    { playing ->
                                                        if (playing) fcastVm.resume() else fcastVm.pause()
                                                    }
                                                } else null,
                                                onCastVolume = if (fcastState.connectedDevice != null) {
                                                    { vol -> fcastVm.setVolume(vol) }
                                                } else null,
                                                onCastSpeed = if (fcastState.connectedDevice != null) {
                                                    { spd -> fcastVm.setSpeed(spd) }
                                                } else null,
                                                anySubtitleSelected = subtitleTracks.any { it.isSelected },
                                                onSubtitleClick = {
                                                    scope.launch { subtitleSheetState.show() }
                                                },
                                                onInteraction = { uiInteracted = true },
                                                isBottomDark = isBottomDark,
                                                autoContrast = autoContrast
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                // Deleted-media fly-away: the page already swapped to the neighbor underneath;
                // this overlay drops the captured media's thumbnail down-and-out so the reveal
                // reads as a removal rather than a silent swap.
                exitingMedia?.let { exiting ->
                    val exitProgress = remember(exiting.id) { Animatable(0f) }
                    LaunchedEffect(exiting.id) {
                        exitProgress.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(320, easing = FastOutSlowInEasing)
                        )
                        if (exitingMedia?.id == exiting.id) exitingMedia = null
                    }
                    ViewerSharedElementThumbnail(
                        media = exiting,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val p = exitProgress.value
                                alpha = 1f - p
                                translationY = size.height * 0.45f * p
                                val s = 1f - 0.15f * p
                                scaleX = s
                                scaleY = s
                            },
                    )
                }
                if (currentMedia?.isImage == true && isGestureEnabled) {
                    val gesturePadding = WindowInsets.systemGestures.asPaddingValues()
                    Spacer(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .mediaViewerBackGestureGuard(
                                gesturePadding.calculateStartPadding(LocalLayoutDirection.current)
                            )
                    )
                    Spacer(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .mediaViewerBackGestureGuard(
                                gesturePadding.calculateEndPadding(LocalLayoutDirection.current)
                            )
                    )
                }
                // Sync status bar icon color with the top image luminance
                val isCurrentVideo by rememberedDerivedState(currentMedia) {
                    currentMedia?.isVideo == true
                }
                val configuration = LocalConfiguration.current
                LaunchedEffect(
                    isTopDark,
                    autoContrast,
                    isDarkTheme,
                    allowBlur,
                    isCurrentVideo,
                    configuration
                ) {
                    val followTheme = if (autoContrast) !isTopDark
                    else !allowBlur && !isCurrentVideo
                    windowInsetsController.isAppearanceLightStatusBars =
                        if (followTheme) !isDarkTheme
                        else if (autoContrast) isTopDark
                        else false
                    eventHandler.setFollowTheme(followTheme)
                }
                DisposableEffect(Unit) {
                    onDispose {
                        eventHandler.setFollowTheme(true)
                    }
                }

                val allowShowingDate by rememberShowMediaViewDateHeader()
                // Keep the top app bar above the BottomSheet so its back/info buttons stay tappable
                // when the info panel is expanded (the sheet is drawn after the app bar and would
                // otherwise intercept taps in the overlapping top region). Empty app bar areas have no
                // pointer input, so taps/drags there still fall through to the sheet.
                Box(
                    modifier = Modifier
                        .zIndex(1f)
                        .graphicsLayer { alpha = dismissAlpha }
                        .then(
                            if (viewerInteractive) Modifier
                            else Modifier.clearAndSetSemantics { }
                        )
                ) {
                    MediaViewAppBar(
                        showUI = showViewerChrome && viewerInteractive,
                        showInfo = showInfo,
                        showDate = remember(currentMedia, allowShowingDate) {
                            currentMedia?.timestamp != 0L && allowShowingDate
                        },
                        isLocked = isLocked,
                        currentDate = currentDate,
                        showLoadingIndicator = subsamplingLoading,
                        paddingValues = paddingValues,
                        currentMedia = currentMedia,
                        // Hide the pending-rotation chip while the info panel is expanded so it
                        // doesn't float on top of / overlap the metadata sheet (#963).
                        showRotationHelper = rememberedDerivedState(
                            showRotationHelper.value,
                            sheetState.currentDetent,
                            currentCapabilities,
                        ) {
                            showRotationHelper.value &&
                                    currentCapabilities?.rotate == true &&
                                    sheetState.currentDetent == imageOnlyDetent
                        },
                        // Fade the top-bar extras out as the info sheet is dragged up, fully hidden at
                        // full expand. Read inside a lambda so it re-evaluates per frame without
                        // recomposing the whole app bar (#963).
                        topExtrasAlpha = {
                            1f - sheetState.progress(imageOnlyDetent, expandedDetent)
                                .coerceIn(0f, 1f)
                        },
                        isImageDark = isTopDark,
                        autoContrast = autoContrast,
                        isMotionPhoto = motionPhotoState.isDetected,
                        isMotionPlaying = motionPhotoState.isPlaying,
                        onToggleMotionPhoto = { motionPhotoState.togglePlayback() },
                        rotationInProgress = rotationInProgress,
                        rotationStageLabel = rotationStageLabel,
                        rotateImage = {
                            // Apply to the media the pending rotation was created on,
                            // not whatever currentPage points at mid-swipe (#962).
                            val media = pagerItems.firstOrNull { it.id == rotationMediaId.longValue }
                                ?: pagerItems.firstOrNull { it.id == settledRotationKey }
                                ?: currentMedia!!
                            if (ImageReencoder.isReencodable(media.mimeType, media.label)) {
                                rotateImage(media, newRotationValue.intValue, false)
                            } else {
                                rotateFallback = media to newRotationValue.intValue
                            }
                        },
                        onShowInfo = {
                            scope.launch {
                                if (showUI) {
                                    if (sheetState.currentDetent == imageOnlyDetent) {
                                        sheetState.animateTo(FullyExpanded)
                                    } else {
                                        sheetState.animateTo(imageOnlyDetent)
                                    }
                                }
                            }
                        },
                        onGoBack = {
                            scope.launch {
                                if (sheetState.currentDetent == FullyExpanded) {
                                    sheetState.animateTo(imageOnlyDetent)
                                } else {
                                    requestViewerDismiss()
                                }
                            }
                        },
                        onLock = {
                            isLocked = !isLocked
                        },
                        visualSearchButton = visualSearchTarget?.takeIf {
                            visualSearchAvailable &&
                                    visualSearchPosition == Settings.Misc.VISUAL_SEARCH_POSITION_TOP
                        }?.let { target ->
                            { followTheme: Boolean ->
                                currentMedia?.let { media ->
                                    VisualSearchButton(
                                        media = media,
                                        enabled = true,
                                        followTheme = followTheme,
                                        icon = target.icon,
                                        tintIcon = target.tintIcon,
                                        title = visualSearchTitle,
                                        onItemClick = onVisualSearch,
                                    )
                                }
                            }
                        },
                        visualSearchProgress = visualSearchPreparing?.progress,
                        visualSearchStageLabel = visualSearchStageLabel,
                        onCancelVisualSearch = cancelVisualSearch,
                        castButton = if (fcastVm.isCastAvailable()) {
                            { followTheme ->
                                CastButton(
                                    isConnected = fcastState.connectedDevice != null,
                                    isConnecting = fcastState.isConnecting,
                                    followTheme = followTheme,
                                    onClick = {
                                        if (fcastState.connectedDevice != null) {
                                            showCastPicker = true
                                        } else if (!fcastVm.hasAllPermissions()) {
                                            showCastPermissions = true
                                        } else {
                                            fcastVm.startDiscovery()
                                            showCastPicker = true
                                        }
                                    }
                                )
                            }
                        } else null,
                        castBanner = if (fcastVm.isCastAvailable() && fcastState.connectedDevice != null) {
                            {
                                CastStatusBanner(
                                    deviceName = fcastState.connectedDevice?.name ?: "",
                                    onStop = { fcastVm.stopCasting() },
                                    onClick = { showCastPicker = true }
                                )
                            }
                        } else null
                    )
                }

                // Auto-cast current media when device connects
                LaunchedEffect(fcastState.connectedDevice?.host) {
                    val device = fcastState.connectedDevice
                    val media = currentMedia
                    if (device != null && media != null && fcastState.castingMediaId == null) {
                        fcastVm.castMedia(media)
                    }
                }

                // FCast device picker dialog
                if (showCastPicker) {
                    FCastDevicePickerDialog(
                        state = fcastState,
                        onDeviceSelected = { device ->
                            fcastVm.connect(device)
                            showCastPicker = false
                        },
                        onCastMedia = {
                            currentMedia?.let { fcastVm.castMedia(it) }
                        },
                        onStopCasting = {
                            fcastVm.stopCasting()
                        },
                        onDisconnect = {
                            fcastVm.disconnect()
                            showCastPicker = false
                        },
                        onDismiss = {
                            fcastVm.stopDiscovery()
                            showCastPicker = false
                        }
                    )
                }

                // Cast permissions checklist dialog
                if (showCastPermissions) {
                    CastPermissionsDialog(
                        permissions = fcastVm.checkPermissions(),
                        onDismiss = { showCastPermissions = false }
                    )
                }

                // Floating filmstrip overlay (positioned like video seekbar)
                AnimatedVisibility(
                    visible = viewerInteractive && showViewerChrome && motionPhotoState.isDetected && motionPhotoState.compositeFilmstrip != null,
                    enter = enterAnimation(DEFAULT_TOP_BAR_ANIMATION_DURATION),
                    exit = exitAnimation(DEFAULT_TOP_BAR_ANIMATION_DURATION),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp)
                        .padding(
                            bottom = bottomPadding + extraPaddingWithNavButtons +
                                    bottomBarHeightDefault + 32.dp
                        )
                ) {
                    MotionPhotoFilmstrip(
                        state = motionPhotoState,
                        onTap = openFramePicker
                    )
                }
                // Group member thumbnail strip (for grouped RAW+JPG, bursts, edits)
                val showMotionFilmstrip =
                    motionPhotoState.isDetected && motionPhotoState.compositeFilmstrip != null
                AnimatedVisibility(
                    visible = viewerInteractive && showViewerChrome && !showMotionFilmstrip && currentGroupMembers.size > 1,
                    enter = enterAnimation(DEFAULT_TOP_BAR_ANIMATION_DURATION),
                    exit = exitAnimation(DEFAULT_TOP_BAR_ANIMATION_DURATION),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .graphicsLayer {
                            translationY =
                                bottomBarHeightDefault.toPx() * sheetState.progress(
                                    imageOnlyDetent,
                                    expandedDetent
                                )
                        }
                        .padding(horizontal = 16.dp)
                        .padding(
                            bottom = bottomPadding + extraPaddingWithNavButtons +
                                    bottomBarHeightDefault + 32.dp +
                                    // Lift the member carousel above the video transport controls
                                    // (slider + time) so they don't overlap for grouped videos.
                                    (if (isCurrentVideo) 96.dp else 0.dp)
                        )
                ) {
                    val currentPagerItemId = pagerItems.getOrNull(currentPage)?.id ?: -1L
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Floating action bar for group multi-select
                        AnimatedVisibility(visible = groupMultiSelectMode) {
                            GroupMemberSelectionBar(
                                selectedCount = groupMultiSelectedIds.size,
                                totalCount = currentGroupMembers.size,
                                onClose = {
                                    groupMultiSelectMode = false
                                    groupMultiSelectedIds = emptySet()
                                },
                                onSelectAll = {
                                    groupMultiSelectedIds =
                                        currentGroupMembers.map { it.id }.toSet()
                                },
                                onShare = {
                                    val selected = currentGroupMembers.filter {
                                        it.id in groupMultiSelectedIds
                                    }
                                    if (selected.isNotEmpty()) {
                                        scope.launch {
                                            context.shareMedia(selected)
                                        }
                                    }
                                }
                            )
                        }
                        key(currentPagerItemId) {
                            val hasCloudAndLocal = remember(currentGroupMembers) {
                                currentGroupMembers.any { it.isCloud } && currentGroupMembers.any { !it.isCloud }
                            }
                            GroupMemberStrip(
                                members = currentGroupMembers,
                                selectedId = selectedMemberOverrideId
                                    ?: currentGroupMembers.firstOrNull()?.id
                                    ?: currentPagerItemId,
                                onSelect = { id ->
                                    selectedMemberOverrideId = id
                                },
                                showCloudLabels = hasCloudAndLocal,
                                multiSelectMode = groupMultiSelectMode,
                                multiSelectedIds = groupMultiSelectedIds,
                                onEnterMultiSelect = { id ->
                                    groupMultiSelectMode = true
                                    groupMultiSelectedIds = setOf(id)
                                },
                                onToggleMultiSelect = { id ->
                                    val newSet = if (id in groupMultiSelectedIds) {
                                        groupMultiSelectedIds - id
                                    } else {
                                        groupMultiSelectedIds + id
                                    }
                                    groupMultiSelectedIds = newSet
                                    if (newSet.isEmpty()) {
                                        groupMultiSelectMode = false
                                    }
                                }
                            )
                        }
                    }
                }
                // Back handler for group multi-select mode
                BackHandler(groupMultiSelectMode) {
                    groupMultiSelectMode = false
                    groupMultiSelectedIds = emptySet()
                }
                // When the UI is hidden (e.g. tapping the image), always settle the info sheet back
                // to the image-only detent. A partial drag interrupted by hiding the UI would
                // otherwise freeze the sheet at a mid-offset and, since its alpha is tied to showUI,
                // leave it invisible-but-still-interactive instead of dismissed (#964).
                LaunchedEffect(showUI) {
                    if (!showUI && sheetState.progress(imageOnlyDetent, expandedDetent) > 0f) {
                        sheetState.animateTo(imageOnlyDetent)
                    }
                }
                BackHandler(sheetState.currentDetent == FullyExpanded) {
                    scope.launch {
                        sheetState.animateTo(imageOnlyDetent)
                    }
                }
                val bottomSheetAlpha by animateFloatAsState(
                    targetValue = if (showViewerChrome) 1f else 0f,
                    animationSpec = tween(DEFAULT_TOP_BAR_ANIMATION_DURATION),
                    label = "MediaViewActionsAlpha"
                )
                if (!isCutoutActive) {
                    BottomSheet(
                        state = sheetState,
                        enabled = viewerInteractive && showViewerChrome && target != TARGET_TRASH && showInfo,
                        modifier = Modifier
                            .graphicsLayer {
                                alpha = bottomSheetAlpha * dismissAlpha
                            }
                            .then(
                                if (viewerInteractive) Modifier
                                else Modifier.clearAndSetSemantics { }
                            )
                            .fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            AnimatedVisibility(
                                visible = currentMedia != null,
                                enter = enterAnimation,
                                exit = exitAnimation
                            ) {
                                val bottomBarFollowTheme = if (autoContrast) {
                                    !isBottomDark
                                } else {
                                    !allowBlur
                                }
                                val surfaceContainer by animateColorAsState(
                                    targetValue = when {
                                        autoContrast && !isBottomDark -> Color.White.copy(0.5f)
                                        autoContrast -> Color.Black.copy(0.5f)
                                        bottomBarFollowTheme -> MaterialTheme.colorScheme.surfaceContainer.copy(
                                            if (isDarkTheme) 0.5f else 0.8f
                                        )

                                        else -> Color.Black.copy(0.5f)
                                    },
                                    label = "BottomBarSurfaceContainer"
                                )
                                val backgroundModifier = if (!allowBlur) {
                                    Modifier.background(
                                        color = surfaceContainer,
                                        shape = RoundedCornerShape(100)
                                    )
                                } else Modifier
                                Box(
                                    modifier = Modifier
                                        .graphicsLayer {
                                            val progress =
                                                sheetState.progress(imageOnlyDetent, expandedDetent)
                                            alpha = 1f - progress
                                            translationY =
                                                bottomBarHeightDefault.toPx() * progress
                                        }
                                        .padding(
                                            bottom = bottomPadding + extraPaddingWithNavButtons + 16.dp
                                        )
                                        .fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(100))
                                            .then(backgroundModifier)
                                            .hazeEffectScaled(
                                                state = LocalHazeState.current,
                                                style = HazeMaterials.ultraThin(
                                                    containerColor = surfaceContainer
                                                )
                                            )
                                            .padding(horizontal = 8.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        MediaViewQuickBottomBar(
                                            currentMedia = currentMedia,
                                            showDeleteButton = !isReadOnly,
                                            // Only interactive while the action bar is actually visible
                                            // (collapsed sheet). When the info panel is expanded the bar is
                                            // faded out (alpha = 0) but would otherwise still be tappable,
                                            // letting taps near the drag handle trigger hidden buttons.
                                            enabled = viewerInteractive && showViewerChrome &&
                                                    sheetState.currentDetent == imageOnlyDetent,
                                            deleteMedia = deleteMedia,
                                            restoreMedia = restoreMedia,
                                            currentVault = currentVault,
                                            isImageDark = isBottomDark,
                                            autoContrast = autoContrast,
                                            visualSearchTarget = if (
                                                visualSearchAvailable &&
                                                visualSearchPosition == Settings.Misc.VISUAL_SEARCH_POSITION_BOTTOM
                                            ) visualSearchTarget else null,
                                            visualSearchTitle = visualSearchTitle,
                                            onVisualSearch = onVisualSearch,
                                            onTrashConfirmed = {
                                                val removedMedia = currentMedia
                                                val trashedId = removedMedia?.id
                                                if (trashedId != null) {
                                                    val newPending = pendingTrashIds + trashedId
                                                    pendingTrashIds = newPending
                                                    // If all items are now filtered out, navigate up
                                                    val state = mediaState.value
                                                    val allItems =
                                                        state.pagerMedia.ifEmpty { state.media }
                                                    val remaining =
                                                        allItems.count { it.id !in newPending }
                                                    if (remaining <= 0 && !isStandalone) {
                                                        windowInsetsController.toggleSystemBars(show = true)
                                                        dismissViewer()
                                                    } else {
                                                        exitingMedia = removedMedia
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                            }

                            val currentCloudBackups by rememberedDerivedState(
                                mediaState.value,
                                currentMedia
                            ) {
                                currentMedia?.let { mediaState.value.cloudBackups[it.id] }
                                    ?: emptyList()
                            }
                            MediaViewSheetDetails(
                                albumsState = albumsState,
                                vaultState = vaultState,
                                metadataState = metadataState,
                                currentMedia = currentMedia,
                                restoreMedia = restoreMedia,
                                currentVault = currentVault,
                                motionPhotoState = motionPhotoState,
                                onOpenFramePicker = openFramePicker,
                                cloudBackups = currentCloudBackups,
                                onOpenPersonTimeline = { person ->
                                    navigateFromViewer(
                                        Screen.PersonDetailScreen.personId(
                                            configId = person.serverConfigId,
                                            id = person.id
                                        )
                                    )
                                },
                                metadataSanitizationState = metadataSanitizationUiState,
                                probeMetadataSanitization = probeMetadataSanitization,
                                sanitizeMetadata = sanitizeMetadata,
                                resetMetadataSanitization = resetMetadataSanitization,
                            )
                        }
                    }
                }

                // Cutout controls: replaces the quick-actions bar in the same bottom slot while a
                // subject-cutout session is active on the current page.
                AnimatedVisibility(
                    visible = viewerInteractive && navigationChromeVisible && isCutoutActive && cutoutController != null,
                    enter = enterAnimation(DEFAULT_TOP_BAR_ANIMATION_DURATION),
                    exit = exitAnimation(DEFAULT_TOP_BAR_ANIMATION_DURATION),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = bottomPadding + extraPaddingWithNavButtons + 16.dp)
                        .padding(horizontal = 16.dp)
                ) {
                    cutoutController?.let { controller ->
                        CutoutControlsBar(controller = controller)
                    }
                }

                // Slideshow controls: minimal transport bar shown when the user taps during a
                // slideshow. Tapping the media toggles [slideshowControlsVisible] via onMediaClick,
                // keeping the normal viewer chrome hidden.
                AnimatedVisibility(
                    visible = viewerInteractive && navigationChromeVisible && slideshowActive && slideshowControlsVisible,
                    enter = enterAnimation(DEFAULT_TOP_BAR_ANIMATION_DURATION),
                    exit = exitAnimation(DEFAULT_TOP_BAR_ANIMATION_DURATION),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = bottomPadding + extraPaddingWithNavButtons + 16.dp)
                        .padding(horizontal = 16.dp)
                ) {
                    SlideshowControls(
                        isPaused = slideshowPaused,
                        onPlayPause = { slideshowPaused = !slideshowPaused },
                        onPrevious = {
                            scope.launch {
                                val size = pagerItems.size
                                if (size > 0) {
                                    val prev =
                                        if (currentPage - 1 < 0) size - 1 else currentPage - 1
                                    pagerState.animateScrollToPage(prev)
                                }
                            }
                        },
                        onNext = {
                            scope.launch {
                                val size = pagerItems.size
                                if (size > 0) {
                                    val next = if (currentPage + 1 >= size) 0 else currentPage + 1
                                    pagerState.animateScrollToPage(next)
                                }
                            }
                        },
                        onExit = { exitSlideshow() }
                    )
                }
            }
        }
    }
}