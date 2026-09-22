/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.storycards

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dot.gallery.R
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.Settings.Misc.rememberAllowBlur
import com.dot.gallery.core.Settings.Misc.rememberDarkMediaViewer
import com.dot.gallery.core.Settings.Misc.rememberSharedElements
import com.dot.gallery.core.Settings.Misc.rememberStoryViewerAutoAdvance
import com.dot.gallery.core.Settings.Misc.rememberStoryViewerDuration
import com.dot.gallery.core.setFollowTheme
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.MediaMetadata
import com.dot.gallery.feature_node.domain.model.StoryCard
import com.dot.gallery.feature_node.domain.util.isVideo
import com.dot.gallery.feature_node.domain.util.readUriOnly
import com.dot.gallery.feature_node.presentation.mediaview.LocalMediaViewerVisualPolicy
import com.dot.gallery.feature_node.presentation.mediaview.MediaViewerVisualPolicy
import com.dot.gallery.feature_node.presentation.mediaview.ViewerDismissBridge
import com.dot.gallery.feature_node.presentation.mediaview.ViewerDismissState
import com.dot.gallery.feature_node.presentation.mediaview.components.actionbuttons.FavoriteButton
import com.dot.gallery.feature_node.presentation.mediaview.components.actionbuttons.ShareButton
import com.dot.gallery.feature_node.presentation.mediaview.components.media.BlurredMediaBackground
import com.dot.gallery.feature_node.presentation.mediaview.components.media.MediaPreviewComponent
import com.dot.gallery.feature_node.presentation.mediaview.components.media.ViewerSharedElementThumbnail
import com.dot.gallery.feature_node.presentation.mediaview.rememberedDerivedState
import com.dot.gallery.feature_node.presentation.util.LocalHazeState
import com.dot.gallery.feature_node.presentation.util.MediaSharedElementKey
import com.dot.gallery.feature_node.presentation.util.rememberWindowInsetsController
import com.dot.gallery.feature_node.presentation.util.storyCardSharedElement
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

private const val DEFAULT_STORY_DURATION_SECONDS = 5
private const val MIN_STORY_DURATION_SECONDS = 3
private const val MAX_STORY_DURATION_SECONDS = 10

internal fun storyDurationMillis(rawSeconds: String): Long =
    (rawSeconds.toIntOrNull() ?: DEFAULT_STORY_DURATION_SECONDS)
        .coerceIn(MIN_STORY_DURATION_SECONDS, MAX_STORY_DURATION_SECONDS) * 1_000L

internal fun advanceStoryElapsed(
    elapsedMillis: Long,
    frameDeltaMillis: Long,
    durationMillis: Long,
    blocked: Boolean,
): Long = if (blocked) {
    elapsedMillis
} else {
    (elapsedMillis + frameDeltaMillis.coerceAtLeast(0L)).coerceAtMost(durationMillis)
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun StoryViewerScreen(
    cards: List<StoryCard>?,
    initialCardId: Long = -1L,
    metadataMap: Map<Long, MediaMetadata> = emptyMap(),
    onEnsureMetadata: (Media?) -> Unit = {},
    onDismiss: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    sessionKey: Int = 0,
    dismissBridge: ViewerDismissBridge? = null,
) {
    val allowBlur by rememberAllowBlur()
    val darkMediaViewer by rememberDarkMediaViewer()
    CompositionLocalProvider(
        LocalMediaViewerVisualPolicy provides MediaViewerVisualPolicy(
            allowBlur = allowBlur,
            forceDarkBackground = darkMediaViewer
        )
    ) {
        StoryViewerContent(
            cards = cards,
            initialCardId = initialCardId,
            metadataMap = metadataMap,
            onEnsureMetadata = onEnsureMetadata,
            onDismiss = onDismiss,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
            sessionKey = sessionKey,
            dismissBridge = dismissBridge,
        )
    }
}

@SuppressLint("NoCollectCallFound")
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
private fun StoryViewerContent(
    cards: List<StoryCard>?,
    initialCardId: Long,
    metadataMap: Map<Long, MediaMetadata>,
    onEnsureMetadata: (Media?) -> Unit,
    onDismiss: () -> Unit,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    sessionKey: Int,
    dismissBridge: ViewerDismissBridge?,
) {
    // Force light status bar icons (white) on dark background, restore on exit
    val windowInsetsController = rememberWindowInsetsController()
    val eventHandler = LocalEventHandler.current
    DisposableEffect(Unit) {
        val previousLight = windowInsetsController.isAppearanceLightStatusBars
        windowInsetsController.isAppearanceLightStatusBars = false
        eventHandler.setFollowTheme(false)
        onDispose {
            windowInsetsController.isAppearanceLightStatusBars = previousLight
            eventHandler.setFollowTheme(true)
        }
    }
    val scope = rememberCoroutineScope()
    val dismissState = remember(initialCardId, sessionKey) { ViewerDismissState(dismissBridge) }

    // null = still loading, show spinner
    if (cards == null) {
        BackHandler { onDismiss() }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color.White)
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(12.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back_cd),
                    tint = Color.White,
                )
            }
        }
        return
    }

    // Loaded but empty — keep recovery and navigation available
    if (cards.isEmpty()) {
        BackHandler { onDismiss() }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.story_no_media),
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(12.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back_cd),
                    tint = Color.White,
                )
            }
        }
        return
    }

    // Resolve initial page; -1 means the target card hasn't loaded yet
    val targetIndex by rememberedDerivedState(cards, initialCardId) {
        if (initialCardId == -1L) 0
        else cards.indexOfFirst { it.id == initialCardId }
    }

    val pagerState = rememberPagerState(
        initialPage = targetIndex.coerceAtLeast(0),
        pageCount = { cards.size }
    )
    // Predictive back scrubs the return flight toward the source card — the same manual morph
    // a committed swipe-dismiss runs — while scrim/chrome fade with gesture progress. Without
    // a mapped cell the gesture still resolves as a plain dismiss.
    PredictiveBackHandler { events ->
        val card = cards.getOrNull(pagerState.currentPage)
        dismissState.onPredictiveBack(
            events = events,
            elementKey = card?.id?.let { MediaSharedElementKey.StoryCardKey(it) },
            media = card?.mediaList?.firstOrNull(),
        ) {
            onDismiss()
        }
    }

    // When the target card appears after initial load, scroll to it
    LaunchedEffect(targetIndex) {
        if (targetIndex > 0 && pagerState.currentPage != targetIndex) {
            pagerState.scrollToPage(targetIndex)
        }
    }

    // Deterministic enter flight (overlay mode): the framework's shared-element bounds morph
    // snaps under the deferred transition — its bounds DeferredAnimation is recreated once the
    // transition's currentState already reads the target, so it initializes at the end bounds
    // and the media pops fullscreen instead of morphing from the card. A root-level thumbnail
    // morphs card→fullscreen instead, with both entries suppressed so no framework morph draws
    // on top; suppression holds until the container's enter transition settles.
    val sharedElementsEnabled by rememberSharedElements()
    LaunchedEffect(sessionKey) {
        if (!sharedElementsEnabled || dismissBridge == null || animatedVisibilityScope == null) return@LaunchedEffect
        val card = withTimeoutOrNull(800.milliseconds) {
            snapshotFlow { cards.getOrNull(targetIndex) }.first { it != null }
        } ?: return@LaunchedEffect
        val enterKey = MediaSharedElementKey.StoryCardKey(card.id)
        val cell =
            dismissBridge.cellBounds[enterKey]?.takeUnless { it.isEmpty } ?: return@LaunchedEffect
        val enterMedia = card.mediaList.firstOrNull() ?: return@LaunchedEffect
        dismissState.runEnterFlight(enterKey, enterMedia, cell)
        snapshotFlow { animatedVisibilityScope.transition.isRunning }.first { !it }
        if (!dismissState.isActive &&
            dismissBridge.suppressedElementKey == enterKey &&
            dismissBridge.flight == null
        ) {
            dismissBridge.suppressedElementKey = null
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Backdrop scrim: pinned to the screen (the drag offset lands on an inner card) and
        // fades with gesture progress so the timeline reveals under the dropping card.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = dismissState.chromeAlpha))
        )
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = !dismissState.isActive,
            key = { index -> cards[index].id },
            beyondViewportPageCount = 0,
        ) { page ->
            val card by rememberedDerivedState(cards, page) {
                cards[page]
            }
            val isCurrentPage by rememberedDerivedState(pagerState.currentPage) {
                pagerState.currentPage == page
            }
            StoryCardViewer(
                card = card,
                isCurrentPage = isCurrentPage,
                metadataMap = metadataMap,
                onEnsureMetadata = onEnsureMetadata,
                onDismiss = onDismiss,
                dismissState = dismissState,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                isPagerScrollInProgress = pagerState.isScrollInProgress,
                onCardFinished = {
                    scope.launch {
                        val page = pagerState.targetPage
                        if (page < cards.lastIndex) {
                            pagerState.animateScrollToPage(page + 1)
                        } else {
                            onDismiss()
                        }
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalHazeMaterialsApi::class, ExperimentalSharedTransitionApi::class)
@Composable
private fun StoryCardViewer(
    card: StoryCard,
    isCurrentPage: Boolean,
    metadataMap: Map<Long, MediaMetadata> = emptyMap(),
    onEnsureMetadata: (Media?) -> Unit = {},
    onDismiss: () -> Unit,
    dismissState: ViewerDismissState,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    isPagerScrollInProgress: Boolean,
    onCardFinished: () -> Unit
) {
    val mediaList = card.mediaList
    if (mediaList.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text(stringResource(R.string.story_no_media), color = Color.White)
        }
        return
    }

    var currentMediaIndex by rememberSaveable(card.id) { mutableIntStateOf(0) }
    val currentMedia by rememberedDerivedState(mediaList, currentMediaIndex) {
        mediaList[currentMediaIndex.coerceIn(0, mediaList.lastIndex)]
    }
    val autoAdvance by rememberStoryViewerAutoAdvance()
    val durationStr by rememberStoryViewerDuration()
    val durationMs = remember(durationStr) { storyDurationMillis(durationStr) }
    var isPaused by rememberSaveable(card.id) { mutableStateOf(false) }
    var isPressed by remember { mutableStateOf(false) }
    var elapsedMs by remember(currentMedia.id) { mutableLongStateOf(0L) }
    var progress by remember(currentMedia.id) { mutableFloatStateOf(0f) }
    val dismissScope = rememberCoroutineScope()
    val chromeAlpha = dismissState.chromeAlpha
    val lifecycleOwner = LocalLifecycleOwner.current
    var isResumed by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
            isResumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val timerBlocked = isPaused || isPressed || isPagerScrollInProgress || !isResumed ||
            dismissState.isActive
    val playWhenReady = rememberUpdatedState(isCurrentPage && !timerBlocked)
    val allowBlur = LocalMediaViewerVisualPolicy.current.allowBlur
    val viewerInteractive = !dismissState.isActive
    val chromeInteractionModifier = if (viewerInteractive) {
        Modifier
    } else {
        Modifier.clearAndSetSemantics { }
    }
    val hazeState = LocalHazeState.current

    fun showPreviousMedia() {
        if (currentMediaIndex > 0) currentMediaIndex--
    }

    fun showNextMedia() {
        if (currentMediaIndex < mediaList.lastIndex) currentMediaIndex++ else onCardFinished()
    }

    // Auto-advance timer
    LaunchedEffect(currentMedia.id, isCurrentPage, autoAdvance, durationMs, timerBlocked) {
        if (!isCurrentPage || !autoAdvance || currentMedia.isVideo || timerBlocked) return@LaunchedEffect
        var lastFrameMillis = 0L
        while (elapsedMs < durationMs) {
            withFrameMillis { frameMillis ->
                val delta = if (lastFrameMillis == 0L) 0L else frameMillis - lastFrameMillis
                lastFrameMillis = frameMillis
                elapsedMs = advanceStoryElapsed(elapsedMs, delta, durationMs, timerBlocked)
            }
            progress = (elapsedMs.toFloat() / durationMs).coerceIn(0f, 1f)
        }
        showNextMedia()
    }

    val blurContainerColor = remember {
        Color.Black.copy(alpha = 0.5f)
    }
    val fallbackContainerColor = remember {
        Color.Black.copy(alpha = 0.4f)
    }
    val previousActionLabel = stringResource(R.string.story_previous_photo)
    val nextActionLabel = stringResource(R.string.story_next_photo)
    val positionDescription = stringResource(
        R.string.story_position,
        currentMediaIndex + 1,
        mediaList.size,
    )
    val navigationActions = buildList {
        if (currentMediaIndex > 0) {
            add(CustomAccessibilityAction(previousActionLabel) {
                showPreviousMedia()
                true
            })
        }
        add(CustomAccessibilityAction(nextActionLabel) {
            showNextMedia()
            true
        })
    }

    // Look up metadata for the current media and trigger collection if needed
    val mediaMetadata by rememberedDerivedState(metadataMap, currentMedia) {
        metadataMap[currentMedia.id]
    }
    LaunchedEffect(currentMedia.id) {
        onEnsureMetadata(currentMedia)
    }
    // Shared-element box: composed of the first transition frame so sharedBounds can morph
    // card↔viewer bounds, wrapping the real media over the prefetched thumbnail. It is never
    // translated itself — the drag offset lands on MediaPreviewComponent's translating content
    // box inside, so the frozen deferred-transition bounds can't lock the media at rest while
    // the gesture handler's coordinate space stays untransformed.
    var sharedElementModifier = Modifier
        .fillMaxSize()
    if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        sharedElementModifier = with(sharedTransitionScope) {
            sharedElementModifier.storyCardSharedElement(
                allowAnimation = isCurrentPage,
                cardId = card.id,
                animatedVisibilityScope = animatedVisibilityScope,
                permitTransformDuringDeferredTransition = true,
            )
        }
    }
    sharedElementModifier = sharedElementModifier
        .clipToBounds()
        .graphicsLayer {
            // After a committed dismiss this card's in-place copy stays hidden: the root flight
            // layer morphs it to the source card, and through the retained exit the real card
            // is already back on screen.
            alpha = if (dismissState.isDismissedVisualHidden(
                    MediaSharedElementKey.StoryCardKey(card.id)
                )
            ) {
                0f
            } else 1f
        }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { dismissState.updateSize(it) }
            // The dismiss gesture lives on this outermost, never-transformed box: the drag
            // offset lands on the shared-element card inside, so pointer positions here always
            // arrive in untranslated screen space. A handler inside the offset card would see
            // each applied offset subtracted from the next delta — halved tracking speed and
            // a fast up/down oscillation.
            .pointerInput(card.id) {
                detectVerticalDragGestures(
                    onDragStart = {
                        dismissState.start(
                            dismissScope,
                            MediaSharedElementKey.StoryCardKey(card.id),
                            currentMedia,
                        )
                    },
                    onVerticalDrag = { change, dragAmount ->
                        dismissState.dragBy(dragAmount)
                        change.consume()
                    },
                    onDragEnd = {
                        dismissState.finish(dismissScope, onDismiss)
                    },
                    onDragCancel = { dismissState.cancel(dismissScope) },
                )
            }
    ) {
        // The low-res surrogate rides the drag offset *behind* the pinned blurred backdrop —
        // same stacking as the media viewer: thumbnail → blur → media content. Hidden while
        // a flight owns the visual (committed dismiss / predictive back / enter flight).
        ViewerSharedElementThumbnail(
            media = currentMedia,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(0, dismissState.offsetY.roundToInt()) }
                .graphicsLayer {
                    alpha = if (dismissState.isDismissedVisualHidden(
                            MediaSharedElementKey.StoryCardKey(card.id)
                        )
                    ) 0f else 1f
                },
        )
        // Pinned blurred backdrop — stays put while the media card slides, dissolving
        // quickly under it via the gesture alpha.
        Box(modifier = Modifier.fillMaxSize()) {
            BlurredMediaBackground(
                media = currentMedia,
                uiEnabled = true,
                gestureAlpha = dismissState.backdropAlpha,
            )
        }
        // Media display using the same component as the media view screen
        // key() forces full tear-down/rebuild when media changes, ensuring
        // the VideoPlayer's SurfaceView and ExoPlayer are properly recycled
        Box(modifier = sharedElementModifier) {
            key(currentMedia.id) {
                MediaPreviewComponent(
                    media = currentMedia,
                    uiEnabled = true,
                    playWhenReady = playWhenReady,
                    onItemClick = { /* handled by gesture overlay */ },
                    onSwipeDown = {},
                    rotationDisabled = true,
                    onImageRotated = {},
                    offset = IntOffset(0, dismissState.offsetY.roundToInt()),
                    isPanorama = mediaMetadata?.isPanorama == true,
                    isPhotosphere = mediaMetadata?.isPhotosphere == true,
                    isMotionPhoto = mediaMetadata?.isMotionPhoto == true,
                    storyActive = true,
                    renderBackground = false,
                    onVideoEnded = { if (autoAdvance) showNextMedia() },
                    videoController = { _, _, currentTime, duration, _, _, _ ->
                        val videoPosition = currentTime.longValue
                        LaunchedEffect(videoPosition, duration) {
                            if (duration > 0L) {
                                progress = (videoPosition.toFloat() / duration).coerceIn(0f, 1f)
                            }
                        }
                    }
                )
            }
        }

        // Gesture overlay for story navigation (left/right tap, long-press to pause)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (viewerInteractive) {
                        Modifier.semantics {
                            stateDescription = positionDescription
                            customActions = navigationActions
                        }
                    } else {
                        Modifier.clearAndSetSemantics { }
                    }
                )
                .pointerInput(card.id, viewerInteractive) {
                    if (viewerInteractive) {
                        detectTapGestures(
                            onTap = { offset ->
                                when {
                                    offset.x < size.width / 3f -> showPreviousMedia()
                                    offset.x > size.width * 2f / 3f -> showNextMedia()
                                }
                            },
                            onLongPress = { },
                            onPress = {
                                isPressed = true
                                try {
                                    awaitRelease()
                                } finally {
                                    isPressed = false
                                }
                            }
                        )
                    }
                }
        )

        // Top gradient overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .align(Alignment.TopCenter)
                .graphicsLayer { alpha = chromeAlpha }
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.6f),
                            Color.Transparent
                        )
                    )
                )
        )

        // ── Top: Progress segments + back button + title + pause ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .graphicsLayer { alpha = chromeAlpha }
                .then(chromeInteractionModifier)
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // Segmented progress bar (no end tip — just rounded segments)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        stateDescription = positionDescription
                        progressBarRangeInfo = ProgressBarRangeInfo(
                            current = currentMediaIndex + progress,
                            range = 0f..mediaList.size.toFloat(),
                            steps = mediaList.size - 1,
                        )
                    },
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                mediaList.forEachIndexed { index, _ ->
                    val segmentProgress = when {
                        index < currentMediaIndex -> 1f
                        index == currentMediaIndex -> if (autoAdvance || currentMedia.isVideo) progress else 1f
                        else -> 0f
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.3f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(segmentProgress)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.White)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Back button + centered title + pause button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back button — circular with blur, white tint
                val backBgModifier = if (allowBlur) {
                    Modifier
                        .clip(CircleShape)
                        .hazeEffect(
                            state = hazeState,
                            style = HazeMaterials.ultraThin(containerColor = blurContainerColor)
                        )
                } else {
                    Modifier.background(fallbackContainerColor, CircleShape)
                }
                IconButton(
                    onClick = onDismiss,
                    enabled = viewerInteractive,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .then(backBgModifier)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back_cd),
                        tint = Color.White
                    )
                }

                // Centered title + subtitle
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = card.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                    if (card.subtitle != null) {
                        Text(
                            text = card.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }
                }

                // Pause button — rounded with blur
                if (autoAdvance || currentMedia.isVideo) {
                    val pauseBgModifier = if (allowBlur) {
                        Modifier
                            .clip(CircleShape)
                            .hazeEffect(
                                state = hazeState,
                                style = HazeMaterials.ultraThin(containerColor = blurContainerColor)
                            )
                    } else {
                        Modifier.background(fallbackContainerColor, CircleShape)
                    }
                    IconButton(
                        onClick = { isPaused = !isPaused },
                        enabled = viewerInteractive,
                        modifier = Modifier
                            .then(pauseBgModifier)
                    ) {
                        Icon(
                            imageVector = if (isPaused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                            contentDescription = stringResource(
                                if (isPaused) R.string.story_resume else R.string.story_pause
                            ),
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                } else {
                    // Spacer matching back button width for centering
                    Spacer(Modifier.size(48.dp))
                }
            }
        }

        // ── Bottom: Action buttons + counter chip ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .graphicsLayer { alpha = chromeAlpha }
                .then(chromeInteractionModifier)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.5f)
                        )
                    )
                )
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Action buttons row (share, favorite, etc.)
            if (!currentMedia.readUriOnly) {
                val actionBgModifier = if (allowBlur) {
                    Modifier
                        .clip(RoundedCornerShape(100))
                        .hazeEffect(
                            state = hazeState,
                            style = HazeMaterials.ultraThin(containerColor = blurContainerColor)
                        )
                } else {
                    Modifier.background(fallbackContainerColor, RoundedCornerShape(100))
                }
                Row(
                    modifier = Modifier
                        .then(actionBgModifier)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ShareButton(
                        media = currentMedia,
                        enabled = viewerInteractive
                    )
                    FavoriteButton(
                        media = currentMedia,
                        enabled = viewerInteractive
                    )
                }
            }

            // Item counter chip with blur
            val chipBgModifier = if (allowBlur) {
                Modifier
                    .clip(RoundedCornerShape(100))
                    .hazeEffect(
                        state = hazeState,
                        style = HazeMaterials.ultraThin(containerColor = blurContainerColor)
                    )
            } else {
                Modifier.background(fallbackContainerColor, RoundedCornerShape(100))
            }
            Text(
                text = stringResource(
                    R.string.story_counter,
                    currentMediaIndex + 1,
                    mediaList.size
                ),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier
                    .then(chipBgModifier)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}
