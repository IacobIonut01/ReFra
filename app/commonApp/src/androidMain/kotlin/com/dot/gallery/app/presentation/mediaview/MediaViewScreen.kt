/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.mediaview

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
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
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
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.pager.PagerState
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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
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
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.composables.core.BottomSheet
import com.composables.core.BottomSheetState
import com.composables.core.SheetDetent
import com.composables.core.SheetDetent.Companion.FullyExpanded
import com.composables.core.rememberBottomSheetState
import com.composeunstyled.LocalTextStyle
import com.dot.gallery.cloud.core.CloudRuntimeSettings
import com.dot.gallery.cloud.ui.CloudSelectionViewModel
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.LocalMediaDistributor
import com.dot.gallery.core.LocalMediaHandler
import com.dot.gallery.core.PendingRemovalScope
import com.dot.gallery.core.Settings
import com.dot.gallery.core.Settings.Misc.rememberSwipeUpToTrash
import com.dot.gallery.core.Settings.Misc.rememberTrashEnabled
import com.dot.gallery.core.decoder.format.ImageReencoder
import com.dot.gallery.core.metadata.MetadataRemovalMode
import com.dot.gallery.core.metadata.MetadataSaveMode
import com.dot.gallery.core.navigate
import com.dot.gallery.core.navigateUp
import com.dot.gallery.core.presentation.components.DragHandle
import com.dot.gallery.core.presentation.components.OverwriteFallbackSheet
import com.dot.gallery.core.presentation.components.SetupButton
import com.dot.gallery.core.setFollowTheme
import com.dot.gallery.core.util.HdrCapabilities
import com.dot.gallery.domain.model.AlbumState
import com.dot.gallery.domain.model.Media
import com.dot.gallery.data.entity.MediaMetadata
import com.dot.gallery.data.model.MediaMetadataState
import com.dot.gallery.domain.model.MediaState
import com.dot.gallery.domain.model.SlideshowTransition
import com.dot.gallery.domain.model.Vault
import com.dot.gallery.domain.model.VaultState
import com.dot.gallery.domain.util.getUri
import com.dot.gallery.app.presentation.cast.components.CastButton
import com.dot.gallery.app.presentation.cast.components.CastPermissionsDialog
import com.dot.gallery.app.presentation.cast.components.CastStatusBanner
import com.dot.gallery.app.presentation.cast.components.FCastDevicePickerDialog
import com.dot.gallery.app.presentation.frameextract.FramePickerActivity
import com.dot.gallery.app.presentation.frameextract.FrameSourceSpec
import com.dot.gallery.feature.viewer.GroupMemberSelectionBar
import com.dot.gallery.feature.viewer.MediaViewAppBar
import com.dot.gallery.feature.viewer.SlideshowControls
import com.dot.gallery.feature.viewer.VisualSearchConvertSheet
import com.dot.gallery.feature.viewer.components.media.CutoutController
import com.dot.gallery.feature.viewer.components.media.CutoutControlsBar
import com.dot.gallery.feature.viewer.components.media.MotionPhotoFilmstrip
import com.dot.gallery.feature.viewer.components.media.MotionPhotoState
import com.dot.gallery.feature.viewer.SubtitleBottomSheet
import com.dot.gallery.feature.viewer.components.video.VideoHdrInfo
import com.dot.gallery.feature.viewer.components.video.VideoPlayerController
import com.dot.gallery.feature.viewer.SlideshowAdvance
import com.dot.gallery.core.ui.util.AppBottomSheetState
import com.dot.gallery.app.presentation.util.FullBrightnessWindow
import com.dot.gallery.core.presentation.util.LocalHazeState
import com.dot.gallery.core.presentation.util.MediaSharedElementKey
import com.dot.gallery.core.presentation.util.ProvideInsets
import com.dot.gallery.core.presentation.util.Screen
import com.dot.gallery.core.presentation.util.rememberGestureNavigationEnabled
import com.dot.gallery.core.presentation.util.rememberNavigationBarOnSides
import com.dot.gallery.app.presentation.util.rememberWindowInsetsController
import com.dot.gallery.app.presentation.util.setHdrMode
import com.dot.gallery.core.presentation.util.shareMedia
import com.dot.gallery.core.presentation.util.toggleSystemBars
import com.dot.gallery.ui.theme.isDarkTheme
import com.github.panpf.sketch.BitmapImage
import com.github.panpf.sketch.cache.CachePolicy
import com.github.panpf.sketch.request.ImageRequest
import com.github.panpf.sketch.sketch
import dev.chrisbanes.haze.blur.materials.HazeMaterials
import dev.chrisbanes.haze.rememberHazeState
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
import com.dot.gallery.core.util.or
import com.dot.gallery.core.workers.rotateImage
import com.dot.gallery.feature.editor.adjustments.Cutout
import com.dot.gallery.feature.editor.adjustments.varfilter.Rotate
import com.dot.gallery.cloud.ui.people.PersonDetailScreen
import com.dot.gallery.core.presentation.components.shape
import com.dot.gallery.feature.editor.adjustments.Crop
import com.dot.gallery.feature.editor.adjustments.filters.None
import com.dot.gallery.feature.editor.util.rotate
import com.dot.gallery.core.presentation.util.update
import com.dot.gallery.feature.viewer.LocalMediaViewerVisualPolicy
import com.dot.gallery.feature.viewer.MediaViewerVisualPolicy
import com.dot.gallery.app.presentation.cast.FCastViewModel
import com.dot.gallery.app.presentation.cast.MediaViewerCastController
import com.dot.gallery.core.Constants.Animation.enterAnimation
import com.dot.gallery.core.Constants.Animation.exitAnimation
import com.dot.gallery.core.Constants.DEFAULT_TOP_BAR_ANIMATION_DURATION
import com.dot.gallery.core.Constants.Target.TARGET_TRASH
import com.dot.gallery.core.Settings.Misc.rememberAutoContrast
import com.dot.gallery.core.Settings.Misc.rememberAutoHideOnVideoPlay
import com.dot.gallery.core.Settings.Misc.rememberDarkMediaViewer
import com.dot.gallery.core.Settings.Misc.rememberDateHeaderFormat
import com.dot.gallery.core.Settings.Misc.rememberExtendedDateHeaderFormat
import com.dot.gallery.core.Settings.Misc.rememberShowMediaViewDateHeader
import com.dot.gallery.core.Settings.Misc.rememberVideoAutoplay
import com.dot.gallery.core.Settings.Misc.rememberVideoHdrPlayback
import com.dot.gallery.core.Settings.Misc.rememberVisualSearchAllowVault
import com.dot.gallery.core.Settings.Misc.rememberVisualSearchConvertFormat
import com.dot.gallery.core.Settings.Misc.rememberVisualSearchConvertMode
import com.dot.gallery.core.Settings.Misc.rememberVisualSearchEnabled
import com.dot.gallery.core.Settings.Misc.rememberVisualSearchPosition
import com.dot.gallery.core.Settings.Misc.rememberVisualSearchProvider
import com.dot.gallery.feature.viewer.MediaViewViewModel
import com.dot.gallery.feature.viewer.MediaViewViewModel.MediaViewEvent
import com.dot.gallery.app.presentation.util.ViewScreenConstants.BOTTOM_BAR_HEIGHT
import com.dot.gallery.app.presentation.util.ViewScreenConstants.ImageOnly
import com.dot.gallery.core.ui.mediaview.VisualSearchTarget
import com.dot.gallery.core.presentation.components.util.swipe
import com.dot.gallery.core.ViewerExitVisual
import com.dot.gallery.core.ViewerDismissBridge
import com.dot.gallery.core.LocalMediaViewerNavigate
import com.dot.gallery.domain.util.isCloud
import com.dot.gallery.domain.util.isEncrypted
import com.dot.gallery.domain.util.isImage
import com.dot.gallery.domain.util.isTrashed
import com.dot.gallery.domain.util.isVideo
import com.dot.gallery.domain.uri.host
import com.dot.gallery.domain.util.volume
import com.dot.gallery.core.ui.mediaview.icon
import com.dot.gallery.app.presentation.mediaview.components.MediaViewQuickBottomBar
import com.dot.gallery.app.presentation.mediaview.components.MediaViewSheetDetails
import com.dot.gallery.feature.viewer.components.media.MediaPreviewComponent
import com.dot.gallery.feature.viewer.components.media.ViewerSharedElementThumbnail
import com.dot.gallery.feature.viewer.GroupMemberStrip
import com.dot.gallery.feature.viewer.resolveSlideshowAdvance
import com.dot.gallery.domain.util.readUriOnly
import com.dot.gallery.core.ui.util.rememberAppBottomSheetState
import com.dot.gallery.core.logging.printWarning
import com.dot.gallery.feature.viewer.buildSlideshowOrder
import com.dot.gallery.core.presentation.util.mediaSharedElement
import com.dot.gallery.domain.model.idLessKey
import com.dot.gallery.core.logging.printError
import com.dot.gallery.core.rememberViewerExitNavigate
import com.dot.gallery.core.ui.util.rememberedDerivedState
import com.dot.gallery.core.ui.mediaview.tintIcon
import com.dot.gallery.core.rememberMediaViewerNavigate
import com.dot.gallery.injection.injectViewModel
import com.dot.gallery.feature.viewer.slideshowDwellMillis
import com.dot.gallery.core.ui.mediaview.resolveVisualSearchTarget
import com.dot.gallery.core.ui.mediaview.needsVisualSearchConvert
import com.dot.gallery.feature.viewer.VisualSearchButton
import com.dot.gallery.core.presentation.util.hazeEffectScaled
import com.dot.gallery.domain.model.definedTimestamp
import com.dot.gallery.domain.date.getMediaAppBarDate
import com.dot.gallery.feature.viewer.ViewerDismissState
import com.dot.gallery.feature.viewer.VisualSearchStage
import com.dot.gallery.feature.viewer.launchVisualSearch
import com.dot.gallery.feature.viewer.toVisualSearchWriteFormat
import com.dot.gallery.feature.viewer.viewerActionCapabilities
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.image_sample_2
import com.dot.gallery.generated.resources.rotate_failed
import com.dot.gallery.generated.resources.rotate_stage_decoding
import com.dot.gallery.generated.resources.rotate_stage_rotating
import com.dot.gallery.generated.resources.rotate_stage_saving
import com.dot.gallery.generated.resources.rotate_stage_uploading
import com.dot.gallery.generated.resources.tap_sides_to_navigate_controls
import com.dot.gallery.generated.resources.tap_sides_to_navigate_enable
import com.dot.gallery.generated.resources.tap_sides_to_navigate_next
import com.dot.gallery.generated.resources.tap_sides_to_navigate_not_now
import com.dot.gallery.generated.resources.tap_sides_to_navigate_previous
import com.dot.gallery.generated.resources.tap_sides_to_navigate_prompt_description
import com.dot.gallery.generated.resources.tap_sides_to_navigate_prompt_title
import com.dot.gallery.generated.resources.tap_sides_to_navigate_settings_hint
import com.dot.gallery.generated.resources.visual_search_failed
import com.dot.gallery.generated.resources.visual_search_generic_cd
import com.dot.gallery.generated.resources.visual_search_stage_converting
import com.dot.gallery.generated.resources.visual_search_stage_decrypting
import com.dot.gallery.generated.resources.visual_search_stage_downloading
import com.dot.gallery.generated.resources.visual_search_stage_extracting
import com.dot.gallery.generated.resources.visual_search_stage_preparing
import com.dot.gallery.generated.resources.visual_search_with
import com.dot.gallery.generated.resources.action_undo
import com.dot.gallery.generated.resources.error_toast
import com.dot.gallery.generated.resources.swipe_up_to_trash_message
import com.dot.gallery.data.repository.MediaMutationResult
import com.dot.gallery.domain.trash.canMoveToTrashRecoverably
import com.dot.gallery.app.presentation.mediaview.components.TrashSwipeIndicator
import com.dot.gallery.app.presentation.mediaview.components.swipeUpTrashProgress
import com.dot.gallery.core.logging.printDebug
import com.dot.gallery.core.ui.util.rememberActivityResult
import com.dot.gallery.core.util.SdkCompat
import com.dot.gallery.core.util.rememberFeedbackManager
import com.dot.gallery.app.presentation.util.toastError


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
            text = stringResource(Res.string.tap_sides_to_navigate_prompt_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(Res.string.tap_sides_to_navigate_prompt_description),
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
                text = stringResource(Res.string.tap_sides_to_navigate_not_now),
            )
            SetupButton(
                onClick = onEnable,
                modifier = Modifier.weight(1f),
                applyHorizontalPadding = false,
                applyBottomPadding = false,
                applyInsets = false,
                text = stringResource(Res.string.tap_sides_to_navigate_enable),
            )
        }
        Text(
            text = stringResource(Res.string.tap_sides_to_navigate_settings_hint),
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
            painter = painterResource(Res.drawable.image_sample_2),
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
                    label = stringResource(Res.string.tap_sides_to_navigate_previous),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.32f)),
                )
                TapNavigationZonePreview(
                    icon = null,
                    label = stringResource(Res.string.tap_sides_to_navigate_controls),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                )
                TapNavigationZonePreview(
                    icon = Icons.AutoMirrored.Outlined.ArrowForward,
                    label = stringResource(Res.string.tap_sides_to_navigate_next),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.32f)),
                )
            }
        } else {
            TapNavigationZonePreview(
                icon = null,
                label = stringResource(Res.string.tap_sides_to_navigate_controls),
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

/**
 * Whether the viewer should close once the viewed item was trashed or deleted.
 * Applies to the standalone viewer too: a camera hand-off usually holds a single
 * item, and keeping the viewer open would leave an empty black pager.
 */
internal fun shouldDismissViewerAfterRemoval(remainingCount: Int): Boolean = remainingCount <= 0

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
 * Whether the viewer's navigation chrome (top bar, quick actions, info sheet) may draw.
 *
 * The chrome is withheld for the whole entry transition, so the media arrives on a bare backdrop
 * and the bars fade in once behind it. [transitionSettled] is the latch that keeps it withheld
 * until that transition has run: the entry registers its animations only *after* the viewer has
 * composed, so on the viewer's first frame `isRunning` still reads false while the entry is
 * already on its way. Gating on [transitionRunning] alone renders the chrome fully visible for
 * that frame, and the starting transition then drives it through a complete exit before
 * re-entering it — the chrome fades out and fades back in on every open.
 */
internal fun isViewerNavigationChromeVisible(
    transitionSettled: Boolean,
    transitionRunning: Boolean,
): Boolean = transitionSettled && !transitionRunning

/**
 * Frames spent watching for an entry transition that has not registered its animations yet.
 * Covers the first composed frame of a viewer, where `isRunning` is still false (see
 * [isViewerNavigationChromeVisible]); reaching the end means no transition is coming.
 */
private const val ENTRY_TRANSITION_START_GRACE_FRAMES = 4

/** Upper bound for the reveal wait, so a transition that never settles cannot hide the chrome. */
private const val ENTRY_TRANSITION_SETTLE_FRAMES = 180

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

/**
 * Programmatic page change honoring "Animate media items": animates when enabled, jumps
 * instantly when disabled so scripted navigation (slideshow advance, rotate-to-copy, viewer
 * events) never lingers on a scroll animation.
 */
internal suspend fun PagerState.scrollToPageOrAnimate(page: Int, animate: Boolean) {
    if (animate) animateScrollToPage(page) else scrollToPage(page)
}

/**
 * Bottom-sheet detent change honoring "Animate media items": animates when enabled, jumps
 * instantly when disabled so the info sheet opens/collapses without a slide.
 */
internal suspend fun BottomSheetState.animateToOrJump(detent: SheetDetent, animate: Boolean) {
    if (animate) animateTo(detent) else jumpTo(detent)
}


private class VideoFramePickerControllerRef {
    var player: ExoPlayer? = null
    var position: MutableLongState? = null
}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalSharedTransitionApi::class)
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
    val viewModel = injectViewModel<MediaViewViewModel> {mediaViewViewModel}
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
            com.dot.gallery.feature.viewer.components.media.rememberMotionPhotoState(
                media = media,
                viewModel = viewModel
            )
        },
    )
}

@UnstableApi
@OptIn(ExperimentalSharedTransitionApi::class)
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
    // Optional host seams — docs captures inject a local cast surface and swap the
    // quick-actions/details rows; null keeps the production wiring untouched.
    castController: MediaViewerCastController? = null,
    quickActionsContent: (@Composable RowScope.(T?, Boolean, Boolean, Boolean) -> Unit)? = null,
    detailsContent: (@Composable (T?) -> Unit)? = null,
    initialInfoExpanded: Boolean = false,
) {
    // "View all metadata" used to leave the viewer, so closing that screen landed on the
    // grid. Keep the photo composed and draw the metadata list over it instead.
    var metadataCover by remember { mutableStateOf<MetadataCoverRequest?>(null) }
    val exitNavigate = rememberViewerExitNavigate(onDismissRequest)
    val viewerNavigate = remember(exitNavigate, isStandalone) {
        { route: String ->
            val request = metadataCoverOverMedia(route, isStandalone)
            if (request != null) metadataCover = request else exitNavigate(route)
        }
    }
    CompositionLocalProvider(
    // Viewer-owned backdrop. The activity haze state also carries gallery sources for as
    // long as the overlay is up; remembering this state here (not on chrome or page keys)
    // keeps media sources and every viewer consumer on one backdrop across hide/show.
    LocalHazeState provides rememberHazeState(),
    LocalMediaViewerVisualPolicy provides MediaViewerVisualPolicy(
        allowBlur = allowBlur,
        forceDarkBackground = rememberDarkMediaViewer().value,
        // "Animate media items" off — nested viewer components (chrome, media fades,
        // blur, zoom/rotation tweens) resolve instantly instead of animating.
        animationsEnabled = Settings.Misc.rememberSharedElements().value
    ),
    LocalMediaViewerNavigate provides viewerNavigate
) {
    ProvideInsets {
        val eventHandler = LocalEventHandler.current
        val distributor = LocalMediaDistributor.current
        val dismissViewer = { onDismissRequest?.invoke() ?: eventHandler.navigateUp() }
        val navigateFromViewer = rememberMediaViewerNavigate()
        val context = LocalContext.current
        val rotateFailedText = stringResource(Res.string.rotate_failed)
        val visualSearchFailedText = stringResource(Res.string.visual_search_failed)
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
        val fcastVm: MediaViewerCastController =
            castController ?: injectViewModel<FCastViewModel> { fCastViewModel }
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
        val cloudSelectionViewModel = injectViewModel<CloudSelectionViewModel> {cloudSelectionViewModel}
        val cloudSettingsByConfigId by CloudRuntimeSettings.settingsByConfigId.collectAsStateWithLifecycle()
        val currentCapabilities by rememberedDerivedState(currentMedia, cloudSettingsByConfigId) {
            val media = currentMedia
            // A cloud item's trash is the *provider's* bin, which only the cloud registry knows:
            // without it the capability would read as "cannot trash" for every remote item.
            media?.viewerActionCapabilities(
                settingsByConfigId = cloudSettingsByConfigId,
                providerSupportsTrash = media.isCloud && cloudSelectionViewModel.supportsTrash(media),
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

        // Deterministic enter flight (overlay mode): the framework's shared-element bounds morph
        // snaps under the deferred transition — its bounds DeferredAnimation is recreated once the
        // transition's currentState already reads the target, so it initializes at the end bounds
        // and the media pops fullscreen instead of morphing from the cell. A root-level thumbnail
        // morphs cell→fullscreen instead, with both entries suppressed so no framework morph draws
        // on top; suppression holds until the container's enter transition settles.
        val sharedElementsEnabled by Settings.Misc.rememberSharedElements()
        // Chrome is withheld until the entry transition has settled (see
        // [isViewerNavigationChromeVisible]): the media flies in on a bare backdrop and the bars
        // fade in once behind it, instead of being torn down and rebuilt mid-flight.
        var navigationTransitionSettled by remember(viewerSessionKey) { mutableStateOf(false) }
        LaunchedEffect(viewerSessionKey, sharedElementsEnabled) {
            if (!sharedElementsEnabled) {
                // Media animations off: nothing flies, the viewer and its chrome appear at once.
                navigationTransitionSettled = true
                return@LaunchedEffect
            }
            val transition = animatedContentScope.transition
            var started = false
            for (frame in 0 until ENTRY_TRANSITION_SETTLE_FRAMES) {
                val running = transition.isRunning
                started = started || running
                if (started && !running) break
                // Nothing has started yet — watch a few frames, then conclude none is coming.
                if (!started && frame >= ENTRY_TRANSITION_START_GRACE_FRAMES) break
                withFrameNanos { }
            }
            navigationTransitionSettled = true
        }
        val navigationChromeVisible = isViewerNavigationChromeVisible(
            transitionSettled = navigationTransitionSettled,
            transitionRunning = animatedContentScope.transition.isRunning,
        )
        val showViewerChrome = showUI && navigationChromeVisible
        // With animations off the dismiss gesture still tracks the finger but commits,
        // cancels and predictive back all resolve instantly — no flights or springs.
        viewerDismissState.animationsEnabled = sharedElementsEnabled
        // With media animations disabled every chrome layer appears/disappears instantly
        // and programmatic page scrolls jump instead of animating.
        val chromeEnter =
            if (sharedElementsEnabled) enterAnimation(DEFAULT_TOP_BAR_ANIMATION_DURATION) else EnterTransition.None
        val chromeExit =
            if (sharedElementsEnabled) exitAnimation(DEFAULT_TOP_BAR_ANIMATION_DURATION) else ExitTransition.None
        val contentEnter = if (sharedElementsEnabled) enterAnimation else EnterTransition.None
        val contentExit = if (sharedElementsEnabled) exitAnimation else ExitTransition.None
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
        // HDR classification of the current page's video track, published from the
        // videoController slot under the `index == currentPage` guard and consumed by the
        // HDR window-mode effect below (#1274). Stays NONE for images/SDR videos.
        val videoHdrInfo = remember { mutableStateOf(VideoHdrInfo.NONE) }
        val hdrVideoEnabled by rememberVideoHdrPlayback()
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
            stringResource(Res.string.visual_search_with, it.displayName)
        } ?: stringResource(Res.string.visual_search_generic_cd)
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
                stringResource(Res.string.visual_search_stage_downloading)
            VisualSearchStage.DECRYPTING ->
                stringResource(Res.string.visual_search_stage_decrypting)
            VisualSearchStage.PREPARING ->
                stringResource(Res.string.visual_search_stage_preparing)
            VisualSearchStage.EXTRACTING ->
                stringResource(Res.string.visual_search_stage_extracting)
            VisualSearchStage.CONVERTING ->
                stringResource(Res.string.visual_search_stage_converting)
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
            MediaViewViewModel.RotationStage.DECODING -> stringResource(Res.string.rotate_stage_decoding)
            MediaViewViewModel.RotationStage.ROTATING -> stringResource(Res.string.rotate_stage_rotating)
            MediaViewViewModel.RotationStage.SAVING -> stringResource(Res.string.rotate_stage_saving)
            MediaViewViewModel.RotationStage.UPLOADING -> stringResource(Res.string.rotate_stage_uploading)
            null -> null
        }

        LaunchedEffect(initialUiVisible, showUI) {
            if (!initialUiVisible && !showUI) windowInsetsController.toggleSystemBars(show = false)
        }

        BackHandler(!showUI && !slideshowActive && metadataCover == null) {
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
        BackHandler(slideshowActive && metadataCover == null) { exitSlideshow() }

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
                initialDetent = if (initialInfoExpanded) expandedDetent else imageOnlyDetent,
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
        BackHandler(enabled = isLocked && metadataCover == null) { }
        // Overlay viewer: predictive back scrubs the return flight toward the source cell — the
        // same manual morph a committed swipe-dismiss runs — while scrim/chrome fade with gesture
        // progress. Without a mapped cell the gesture still resolves as a plain dismiss.
        if (overlayMode) {
            PredictiveBackHandler(
                enabled = showUI && !slideshowActive && !isLocked && !isDismissing &&
                    metadataCover == null
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
            BackHandler(enabled = showUI && !slideshowActive && !isLocked && metadataCover == null) {
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
                        } else if (media?.isVideo == true) {
                            // Video HDR (#1274): driven by the playing track's published HDR
                            // classification — the window enters COLOR_MODE_HDR once the tracks
                            // resolve to Dolby Vision/HDR10/HLG and leaves it when playback ends,
                            // the page changes, or the user disables HDR video playback.
                            snapshotFlow { videoHdrInfo.value.isHdr && hdrVideoEnabled }
                                .collectLatest { hdr ->
                                    withContext(Dispatchers.Main.immediate) {
                                        context.setHdrMode(hdr)
                                    }
                                    printWarning(
                                        "Setting HDR Mode to $hdr (video=${videoHdrInfo.value.type})"
                                    )
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
                } catch (e: Exception) {
                    printError("ui.mediaview", "window pixel copy for scrim sampling failed", e)
                    dest.recycle()
                }
            }
        }

        LaunchedEffect(uiEvents, rotateFailedText, visualSearchFailedText) {
            uiEvents.collect { event ->
                when (event) {
                    MediaViewEvent.ScrollToFirstPage ->
                        pagerState.scrollToPageOrAnimate(0, sharedElementsEnabled)

                    is MediaViewEvent.NavigateToRotatedCopy -> {
                        // Wait (briefly) for the new copy to be indexed into the pager, then jump to it.
                        val targetIndex = withTimeoutOrNull(5.seconds) {
                            snapshotFlow {
                                pagerItems.indexOfFirst {
                                    it.getUri().toString() == event.uri
                                }
                            }.first { it >= 0 }
                        }
                        pagerState.scrollToPageOrAnimate(targetIndex ?: 0, sharedElementsEnabled)
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
                    pagerState.scrollToPageOrAnimate(advance.index, sharedElementsEnabled)
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
            // Stamp the non-interactive stand-in the overlay host draws while exiting: the
            // viewer leaves composition with the exit so its gesture surfaces can't eat a
            // fast re-tap on the grid underneath. Media owned by a return flight is left out
            // — the root flight layer draws it.
            SideEffect {
                dismissBridge?.exitVisual = ViewerExitVisual(
                    scrim = backgroundColor.copy(alpha = dismissAlpha),
                    media = currentMedia?.takeUnless {
                        viewerDismissState.isDismissedVisualHidden(
                            MediaSharedElementKey.MediaKey(it.id)
                        )
                    },
                )
            }
            // ── Swipe up to trash ──
            // Opt-in gesture (Settings → Media Viewer): drag the current media upwards to move it
            // to the trash. Only a recoverable trash move is ever performed — an item with no bin
            // (a cloud provider without one, encrypted media, a source that cannot be trashed)
            // leaves the gesture inert instead of falling back to a permanent delete.
            val mediaHandler = LocalMediaHandler.current
            val swipeUpToTrashEnabled by rememberSwipeUpToTrash()
            val trashEnabledSetting by rememberTrashEnabled()
            // Per-item trash policy inputs: a cloud item's bin is the provider's, a local item's is
            // the platform's plus the Trash setting.
            val trashEnabledFor: (Media) -> Boolean = { media ->
                if (media.isCloud) {
                    cloudSelectionViewModel.supportsTrash(media)
                } else {
                    trashEnabledSetting
                }
            }
            val trashSupportedFor: (Media) -> Boolean = { media ->
                if (media.isCloud) {
                    cloudSelectionViewModel.supportsTrash(media)
                } else {
                    SdkCompat.supportsTrash
                }
            }
            val trashGestureAllowed by rememberedDerivedState(
                currentMedia,
                currentCapabilities,
                swipeUpToTrashEnabled,
                trashEnabledSetting,
                target,
            ) {
                val media = currentMedia
                media != null &&
                        swipeUpToTrashEnabled &&
                        target != TARGET_TRASH &&
                        currentCapabilities?.trash == true &&
                        media.canMoveToTrashRecoverably(
                            trashEnabled = trashEnabledFor(media),
                            trashSupported = trashSupportedFor(media),
                        )
            }
            // The gesture handler outlives a page change — its pointerInput block is only rebuilt
            // when a key changes — so the page it acts on is read live at release time instead of
            // being captured together with the lambda.
            val latestMedia by rememberUpdatedState(currentMedia)
            val latestTrashGestureAllowed by rememberUpdatedState(trashGestureAllowed)
            // 0..1 for the in-flight upward drag; drives the trash indicator.
            var trashSwipeProgress by remember { mutableFloatStateOf(0f) }
            val trashSwipeFeedback = rememberFeedbackManager()
            val snackbarHostState = remember { SnackbarHostState() }
            val movedToTrashMessage = stringResource(Res.string.swipe_up_to_trash_message)
            val undoLabel = stringResource(Res.string.action_undo)
            val trashSwipeError = toastError(stringResource(Res.string.error_toast))
            // Hides a media the viewer just lost and lets its thumbnail drop out of the page.
            val hideTrashedMedia: (Media) -> Unit = { removed ->
                pendingTrashIds = pendingTrashIds + removed.id
                exitingMedia = removed.takeIf { sharedElementsEnabled }
            }
            // A MediaStore trash/untrash request is answered asynchronously (Android 11+ asks the
            // user to confirm), so the items in flight are parked until that result arrives.
            var pendingSwipeTrash by remember { mutableStateOf<List<Media>>(emptyList()) }
            // Undo restores the item AND brings the viewer back to it: the page it was on has
            // already advanced to a neighbour, so a rescued photo would otherwise sit off-screen
            // until the user swiped back to find it. Requests travel through a flow rather than a
            // state key — clearing a key from inside its own LaunchedEffect cancels that effect
            // before it ever scrolls.
            val revealRestoredRequests = remember { MutableSharedFlow<Media>(extraBufferCapacity = 4) }
            var pendingUndoRestore by remember { mutableStateOf<Media?>(null) }
            val swipeRestoreResult = rememberActivityResult(
                onResultOk = {
                    pendingUndoRestore?.let { revealRestoredRequests.tryEmit(it) }
                    pendingUndoRestore = null
                },
                onResultCanceled = { pendingUndoRestore = null },
            )
            // Waits until the restored item is back in the pager, then scrolls onto it. The pager
            // needs a frame or two to pick the updated list up, and a scroll issued against the
            // stale one lands on the neighbour that had taken the item's place — so the target is
            // resolved from the pager's own list and the scroll is retried until the page really
            // shows the restored media.
            val revealRestoredPage: suspend (Media) -> Unit = { media ->
                val isRestored: (Media?) -> Boolean = { item ->
                    item != null && (item.id == media.id ||
                            mediaState.value.mediaGroups[item.id]?.any { it.id == media.id } == true)
                }
                val deadline = System.currentTimeMillis() + 10_000
                var shown = false
                while (!shown && System.currentTimeMillis() < deadline) {
                    val index = pagerItems.indexOfFirst { isRestored(it) }
                    if (index in 0 until pagerState.pageCount) {
                        pagerState.scrollToPageOrAnimate(index, sharedElementsEnabled)
                        shown = isRestored(pagerItems.getOrNull(pagerState.settledPage))
                        printDebug(
                            "mediaview.trash",
                            "Undo reveal ${media.id}: index=$index settled=${pagerState.settledPage} " +
                                    "pageCount=${pagerState.pageCount} shown=$shown"
                        )
                    }
                    if (!shown) delay(150)
                }
            }
            LaunchedEffect(Unit) {
                revealRestoredRequests.collect { media -> revealRestoredPage(media) }
            }
            // Puts a trashed item back and returns the viewer to it — the Undo action.
            val undoSwipeTrash: suspend (Media) -> Unit = { media ->
                distributor.unmarkPendingRemoval(setOf(media.id))
                pendingTrashIds = pendingTrashIds - media.id
                when (mediaHandler.trashMedia(swipeRestoreResult, listOf(media), trash = false)) {
                    MediaMutationResult.COMPLETED -> revealRestoredRequests.tryEmit(media)
                    MediaMutationResult.FAILED -> trashSwipeError.show()
                    MediaMutationResult.REQUEST_LAUNCHED -> pendingUndoRestore = media
                }
            }
            // Offers Undo for a trashed item; true when the user took it.
            val offerUndoSwipeTrash: suspend (Media) -> Boolean = { media ->
                val outcome = snackbarHostState.showSnackbar(
                    message = movedToTrashMessage,
                    actionLabel = undoLabel,
                    withDismissAction = false,
                    duration = SnackbarDuration.Short,
                )
                val undone = outcome == SnackbarResult.ActionPerformed
                if (undone) undoSwipeTrash(media)
                undone
            }
            // Bookkeeping once the trash itself went through: hide the page, then offer Undo. The
            // viewer only closes after the Undo window has passed, so even the last item — the one
            // case with no neighbour page left to reveal — stays recoverable in place.
            val onSwipeUpTrashCommitted: suspend (List<Media>) -> Unit = { items ->
                val removed = items.firstOrNull()
                if (removed != null) {
                    distributor.markPendingRemoval(
                        items.mapTo(HashSet()) { it.id },
                        PendingRemovalScope.NON_TRASH,
                    )
                    hideTrashedMedia(removed)
                    if (!offerUndoSwipeTrash(removed)) {
                        val state = mediaState.value
                        val allItems = state.pagerMedia.ifEmpty { state.media }
                        if (allItems.none { it.id !in pendingTrashIds } && !isStandalone) {
                            windowInsetsController.toggleSystemBars(show = true)
                            dismissViewer()
                        }
                    }
                }
            }
            val swipeTrashResult = rememberActivityResult(
                onResultCanceled = { pendingSwipeTrash = emptyList() },
                onResultOk = {
                    val items = pendingSwipeTrash
                    pendingSwipeTrash = emptyList()
                    scope.launch { onSwipeUpTrashCommitted(items) }
                },
            )
            // Moves the media the finger was released on to the trash.
            val performSwipeUpTrash: suspend (Media) -> Unit = { media ->
                // A second gate on the exact item, so a stale closure can never reach a permanent
                // delete where the user asked for a trash move.
                if (media.canMoveToTrashRecoverably(trashEnabledFor(media), trashSupportedFor(media))) {
                    when (mediaHandler.trashMedia(swipeTrashResult, listOf(media), trash = true)) {
                        MediaMutationResult.COMPLETED -> onSwipeUpTrashCommitted(listOf(media))
                        MediaMutationResult.FAILED -> trashSwipeError.show()
                        MediaMutationResult.REQUEST_LAUNCHED -> pendingSwipeTrash = listOf(media)
                    }
                }
            }

            val dismissGestureEnabled = overlayMode &&
                    metadataCover == null &&
                    !isLocked &&
                    !isImageZoomed &&
                    !isVideoZoomed &&
                    !isCutoutActive &&
                    !slideshowActive &&
                    !pagerState.isScrollInProgress &&
                    sheetState.progress(imageOnlyDetent, expandedDetent) == 0f
            // Reuses every dismiss guard, so a zoomed, cut-out, locked or slideshow page never
            // arms the trash gesture either.
            val trashGestureEnabled = trashGestureAllowed &&
                    metadataCover == null &&
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
                    .pointerInput(dismissGestureEnabled, trashGestureEnabled) {
                        if (dismissGestureEnabled || trashGestureEnabled) {
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
                                var draggingToTrash = false
                                var trashVibrated = false
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
                                            !draggingToTrash &&
                                            dismissGestureEnabled &&
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
                                        // The mirror gesture, armed only when a recoverable trash
                                        // move is available for this page.
                                        if (!doubleTapHold &&
                                            !draggingToDismiss &&
                                            !draggingToTrash &&
                                            trashGestureEnabled &&
                                            -totalDrag.y > viewConfiguration.touchSlop &&
                                            abs(totalDrag.y) > abs(totalDrag.x)
                                        ) {
                                            draggingToTrash = true
                                        }
                                        if (draggingToDismiss) {
                                            viewerDismissState.dragBy(delta.y)
                                            change.consume()
                                        }
                                        if (draggingToTrash) {
                                            // Progress follows the finger, so a release below the
                                            // threshold simply springs the indicator away.
                                            trashSwipeProgress = swipeUpTrashProgress(-totalDrag.y)
                                            if (!trashVibrated && trashSwipeProgress >= 1f) {
                                                trashSwipeFeedback.vibrateStrong()
                                                trashVibrated = true
                                            }
                                            change.consume()
                                        }
                                        lastPosition = change.position
                                    } while (change.pressed)
                                    // Remember a clean tap so the next gesture inside the
                                    // double-tap window is left to the image's one-finger
                                    // zoom; anything else resets the window.
                                    val up = upChange
                                    lastTapUpUptime = if (!cancelled && !draggingToDismiss &&
                                        !draggingToTrash &&
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
                                    if (draggingToTrash) {
                                        // A release past the threshold trashes; anything else
                                        // (short drag, cancelled gesture) only retracts the
                                        // indicator and leaves the media alone.
                                        val committed = !cancelled && trashSwipeProgress >= 1f
                                        val media = latestMedia
                                        trashSwipeProgress = 0f
                                        if (committed && media != null &&
                                            latestTrashGestureAllowed
                                        ) {
                                            scope.launch { performSwipeUpTrash(media) }
                                        }
                                    }
                                } catch (e: CancellationException) {
                                    // The pointerInput key (media id / gesture eligibility) can
                                    // flip mid-drag and cancel this block; never leave the
                                    // dismiss state stuck in gestureActive.
                                    if (draggingToDismiss) viewerDismissState.cancel(scope)
                                    if (draggingToTrash) trashSwipeProgress = 0f
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
                    val fadeEnabled = sharedElementsEnabled && slideshowActive &&
                            (transition == SlideshowTransition.FADE || transition == SlideshowTransition.KEN_BURNS)
                    val kenBurnsEnabled = sharedElementsEnabled && slideshowActive && media?.isVideo != true &&
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
                                enter = contentEnter,
                                exit = if (viewerDismissState.isCommitted) ExitTransition.None else contentExit
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
                                    // Only the current page's HDR state is allowed to drive window
                                    // HDR mode and the details-sheet row (#1274).
                                    onVideoHdrChanged = if (index == currentPage) {
                                        { videoHdrInfo.value = it }
                                    } else {
                                        {}
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
                                            enter = chromeEnter,
                                            exit = chromeExit,
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
                                        sheetState.animateToOrJump(FullyExpanded, sharedElementsEnabled)
                                    } else {
                                        sheetState.animateToOrJump(imageOnlyDetent, sharedElementsEnabled)
                                    }
                                }
                            }
                        },
                        onGoBack = {
                            scope.launch {
                                if (sheetState.currentDetent == FullyExpanded) {
                                    sheetState.animateToOrJump(imageOnlyDetent, sharedElementsEnabled)
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
                    enter = chromeEnter,
                    exit = chromeExit,
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
                    enter = chromeEnter,
                    exit = chromeExit,
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
                        AnimatedVisibility(
                            visible = groupMultiSelectMode,
                            enter = contentEnter,
                            exit = contentExit
                        ) {
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
                BackHandler(groupMultiSelectMode && metadataCover == null) {
                    groupMultiSelectMode = false
                    groupMultiSelectedIds = emptySet()
                }
                // When the UI is hidden (e.g. tapping the image), always settle the info sheet back
                // to the image-only detent. A partial drag interrupted by hiding the UI would
                // otherwise freeze the sheet at a mid-offset and, since its alpha is tied to showUI,
                // leave it invisible-but-still-interactive instead of dismissed (#964).
                LaunchedEffect(showUI) {
                    if (!showUI && sheetState.progress(imageOnlyDetent, expandedDetent) > 0f) {
                        sheetState.animateToOrJump(imageOnlyDetent, sharedElementsEnabled)
                    }
                }
                BackHandler(sheetState.currentDetent == FullyExpanded && metadataCover == null) {
                    scope.launch {
                        sheetState.animateToOrJump(imageOnlyDetent, sharedElementsEnabled)
                    }
                }
                val bottomSheetAlpha by animateFloatAsState(
                    targetValue = if (showViewerChrome) 1f else 0f,
                    animationSpec = if (sharedElementsEnabled) {
                        tween(DEFAULT_TOP_BAR_ANIMATION_DURATION)
                    } else {
                        snap()
                    },
                    label = "MediaViewActionsAlpha"
                )
                if (!isCutoutActive) {
                    BottomSheet(
                        state = sheetState,
                        enabled = viewerInteractive && showViewerChrome && target != TARGET_TRASH && showInfo,
                        modifier = Modifier
                            .graphicsLayer {
                                alpha = bottomSheetAlpha * dismissAlpha
                                // Auto compositing promotes alpha < 1 into an offscreen buffer.
                                // Haze keeps that buffer's clipped capture after the fade returns
                                // to 1, which chops the quick-action capsule. Modulate the alpha
                                // instead so the sampling layer is never flattened mid-transition.
                                compositingStrategy = CompositingStrategy.ModulateAlpha
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
                                enter = contentEnter,
                                exit = contentExit
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
                                    animationSpec = if (sharedElementsEnabled) spring() else snap(),
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
                                            compositingStrategy = CompositingStrategy.ModulateAlpha
                                        }
                                        .padding(
                                            bottom = bottomPadding + extraPaddingWithNavButtons + 16.dp
                                        )
                                        .fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    // The capsule's haze layer is a sibling of the padded actions
                                    // and is measured to their full bounds. Clip sits on that
                                    // final boundary so the blur sampling rect cannot shrink to
                                    // the glyphs when the sheet alpha animates.
                                    val capsuleShape = RoundedCornerShape(100)
                                    Box(contentAlignment = Alignment.Center) {
                                        Box(
                                            modifier = Modifier
                                                .matchParentSize()
                                                .clip(capsuleShape)
                                                .then(backgroundModifier)
                                                .hazeEffectScaled(
                                                    state = LocalHazeState.current,
                                                    style = HazeMaterials.ultraThin(
                                                        containerColor = surfaceContainer
                                                    )
                                                )
                                        )
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            if (quickActionsContent != null) {
                                                quickActionsContent(
                                                    currentMedia,
                                                    viewerInteractive && showViewerChrome &&
                                                            sheetState.currentDetent == imageOnlyDetent,
                                                    isBottomDark,
                                                    autoContrast,
                                                )
                                            } else MediaViewQuickBottomBar(
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
                                                        if (shouldDismissViewerAfterRemoval(remaining)) {
                                                            windowInsetsController.toggleSystemBars(show = true)
                                                            dismissViewer()
                                                        } else {
                                                            // The fly-away reveal is skipped entirely
                                                            // when media animations are disabled.
                                                            exitingMedia =
                                                                removedMedia.takeIf { sharedElementsEnabled }
                                                        }
                                                    }
                                                }
                                            )
                                        }
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
                            if (detailsContent != null) {
                                detailsContent(currentMedia)
                            } else MediaViewSheetDetails(
                                albumsState = albumsState,
                                vaultState = vaultState,
                                metadataState = metadataState,
                                currentMedia = currentMedia,
                                restoreMedia = restoreMedia,
                                currentVault = currentVault,
                                motionPhotoState = motionPhotoState,
                                videoHdr = videoHdrInfo.value,
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

                // Trash target for the swipe-up gesture: only drawn while the drag is in flight,
                // so the viewer shows nothing extra at rest.
                TrashSwipeIndicator(
                    progress = trashSwipeProgress,
                    modifier = Modifier.align(Alignment.Center),
                )

                // Undo for a swipe-up trash. It lives above the quick-actions bar so the action
                // stays reachable while the item that was trashed leaves the page.
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = bottomPadding + extraPaddingWithNavButtons + 96.dp)
                        .padding(horizontal = 16.dp),
                )

                // Cutout controls: replaces the quick-actions bar in the same bottom slot while a
                // subject-cutout session is active on the current page.
                AnimatedVisibility(
                    visible = viewerInteractive && navigationChromeVisible && isCutoutActive && cutoutController != null,
                    enter = chromeEnter,
                    exit = chromeExit,
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
                    enter = chromeEnter,
                    exit = chromeExit,
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
                                    pagerState.scrollToPageOrAnimate(prev, sharedElementsEnabled)
                                }
                            }
                        },
                        onNext = {
                            scope.launch {
                                val size = pagerItems.size
                                if (size > 0) {
                                    val next = if (currentPage + 1 >= size) 0 else currentPage + 1
                                    pagerState.scrollToPageOrAnimate(next, sharedElementsEnabled)
                                }
                            }
                        },
                        onExit = { exitSlideshow() }
                    )
                }
                metadataCover?.let { request ->
                    BackHandler { metadataCover = null }
                    MetadataCoverLayer(
                        request = request,
                        onBack = { metadataCover = null },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}
}