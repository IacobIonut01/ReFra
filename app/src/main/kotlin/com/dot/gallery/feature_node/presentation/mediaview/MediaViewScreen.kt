/*
 * SPDX-FileCopyrightText: 2023 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.mediaview

import android.content.res.Configuration
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisallowComposableCalls
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onVisibilityChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.composables.core.BottomSheet
import com.composables.core.SheetDetent.Companion.FullyExpanded
import com.composables.core.rememberBottomSheetState
import com.composeunstyled.LocalTextStyle
import com.dot.gallery.core.Constants.Animation.enterAnimation
import com.dot.gallery.core.Constants.Animation.exitAnimation
import com.dot.gallery.core.Constants.DEFAULT_TOP_BAR_ANIMATION_DURATION
import com.dot.gallery.core.Constants.Target.TARGET_TRASH
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.Settings.Misc.rememberAllowBlur
import com.dot.gallery.core.Settings.Misc.rememberAutoHideOnVideoPlay
import com.dot.gallery.core.Settings.Misc.rememberDateHeaderFormat
import com.dot.gallery.core.Settings.Misc.rememberExtendedDateHeaderFormat
import com.dot.gallery.core.Settings.Misc.rememberShowMediaViewDateHeader
import com.dot.gallery.core.Settings.Misc.rememberVideoAutoplay
import com.dot.gallery.core.navigateUp
import com.dot.gallery.core.presentation.components.util.swipe
import com.dot.gallery.feature_node.domain.model.AlbumState
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.MediaMetadataState
import com.dot.gallery.feature_node.domain.model.MediaState
import com.dot.gallery.feature_node.domain.model.Vault
import com.dot.gallery.feature_node.domain.model.VaultState
import com.dot.gallery.feature_node.domain.util.getUri
import com.dot.gallery.feature_node.domain.util.isImage
import com.dot.gallery.feature_node.domain.util.isVideo
import com.dot.gallery.feature_node.domain.util.readUriOnly
import com.dot.gallery.feature_node.presentation.mediaview.MediaViewViewModel.MediaViewEvent
import com.dot.gallery.feature_node.presentation.mediaview.components.MediaViewAppBar
import com.dot.gallery.feature_node.presentation.mediaview.components.MediaViewQuickBottomBar
import com.dot.gallery.feature_node.presentation.mediaview.components.MediaViewSheetDetails
import com.dot.gallery.feature_node.presentation.mediaview.components.media.MediaPreviewComponent
import com.dot.gallery.feature_node.presentation.mediaview.components.video.VideoPlayerController
import com.dot.gallery.feature_node.presentation.util.FullBrightnessWindow
import com.dot.gallery.feature_node.presentation.util.ProvideInsets
import com.dot.gallery.feature_node.presentation.util.ViewScreenConstants.BOTTOM_BAR_HEIGHT
import com.dot.gallery.feature_node.presentation.util.ViewScreenConstants.ImageOnly
import com.dot.gallery.feature_node.presentation.util.getMediaAppBarDate
import com.dot.gallery.feature_node.presentation.util.mediaSharedElement
import com.dot.gallery.feature_node.presentation.util.printWarning
import com.dot.gallery.feature_node.presentation.util.rememberGestureNavigationEnabled
import com.dot.gallery.feature_node.presentation.util.rememberNavigationBarHeight
import com.dot.gallery.feature_node.presentation.util.rememberWindowInsetsController
import com.dot.gallery.feature_node.presentation.util.setHdrMode
import com.dot.gallery.feature_node.presentation.util.toggleSystemBars
import com.dot.gallery.ui.theme.BlackScrim
import com.dot.gallery.ui.theme.WhiterBlackScrim
import com.dot.gallery.ui.theme.isDarkTheme
import com.github.panpf.sketch.BitmapImage
import com.github.panpf.sketch.request.ImageRequest
import com.github.panpf.sketch.sketch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds

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
    return remember(keys) {
        derivedStateOf(block)
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun <T : Media> MediaViewScreen(
    toggleRotate: () -> Unit,
    paddingValues: PaddingValues,
    isStandalone: Boolean = false,
    mediaId: Long,
    target: String? = null,
    mediaState: State<MediaState<out T>>,
    metadataState: State<MediaMetadataState>,
    albumsState: State<AlbumState>,
    vaultState: State<VaultState>,
    restoreMedia: ((Vault, T, () -> Unit) -> Unit)? = null,
    deleteMedia: ((Vault, T, () -> Unit) -> Unit)? = null,
    currentVault: Vault? = null,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
) = ProvideInsets {
    val viewModel = hiltViewModel<MediaViewViewModel>()
    val eventHandler = LocalEventHandler.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val windowInsetsController = rememberWindowInsetsController()

    var initialPageSetup by rememberSaveable { mutableStateOf(false) }

    // Use only primitive ids/sizes as saveable keys (avoid passing full media list object)
    val initialPage = rememberSaveable(mediaId, mediaState.value.media.size) {
        mediaState.value.media.indexOfFirst { it.id == mediaId }.coerceAtLeast(0)
    }
    var currentPage by rememberSaveable(initialPage) { mutableIntStateOf(initialPage) }

    val pagerState = rememberPagerState(
        initialPage = initialPage,
        initialPageOffsetFraction = 0f,
        pageCount = mediaState.value.media::size
    )

    val currentMedia by rememberedDerivedState(mediaState.value) {
        mediaState.value.media.getOrNull(currentPage)
    }

    var shouldForcePage by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(initialPage, currentPage, mediaState.value) {
        if (currentPage == 0) {
            if (mediaState.value.isLoading) {
                shouldForcePage = true
            } else {
                if (initialPage != 0 && currentPage != initialPage && shouldForcePage) {
                    pagerState.scrollToPage(initialPage)
                }
                shouldForcePage = false
                initialPageSetup = true
            }
        } else {
            initialPageSetup = true
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
    val playWhenReady by rememberedDerivedState(
        currentMedia,
        canAutoPlay
    ) { currentMedia?.isVideo == true && canAutoPlay }
    val isReadOnly by rememberedDerivedState { currentMedia?.readUriOnly == true }
    val showInfo by rememberedDerivedState { currentMedia?.trashed == 0 && !isReadOnly }

    var showUI by rememberSaveable { mutableStateOf(true) }
    // Key rotation helpers by media id, not whole media object (prevents Serializable fallback of Media inside internal Pair)
    val newRotationValue = rememberSaveable(currentMedia?.id ?: -1L) { mutableIntStateOf(0) }
    val showRotationHelper = rememberSaveable(currentMedia?.id ?: -1L) { mutableStateOf(false) }

    BackHandler(!showUI) {
        windowInsetsController.toggleSystemBars(show = true)
        eventHandler.navigateUp()
    }
    val window = LocalWindowInfo.current
    val density = LocalDensity.current
    val halfScreenHeight by remember(
        window,
        density
    ) { mutableStateOf(Dp(window.containerSize.height / density.density / 4)) }

    val configuration = LocalConfiguration.current
    val isLandscape = remember(configuration) {
        configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    }
    val isGestureEnabled = rememberGestureNavigationEnabled()
    // Extra padding for navigation bar with 3/2-buttons
    val extraPaddingWithNavButtons by remember(isLandscape, isGestureEnabled) {
        mutableStateOf(
            if (!isGestureEnabled && !isLandscape) {
                32.dp
            } else 0.dp
        )
    }
    val navigationBarHeight = rememberNavigationBarHeight()
    val bottomBarHeightDefault by remember(isGestureEnabled, isLandscape) {
        mutableStateOf(
            if (!isGestureEnabled && isLandscape) 84.dp
            else BOTTOM_BAR_HEIGHT
        )
    }

    val imageOnlyDetent = remember(bottomBarHeightDefault, extraPaddingWithNavButtons) {
        ImageOnly { bottomBarHeightDefault + extraPaddingWithNavButtons }
    }
    val expandedDetent = remember { FullyExpanded }

    val sheetState = rememberBottomSheetState(
        initialDetent = imageOnlyDetent,
        detents = listOf(imageOnlyDetent, expandedDetent),
        positionalThreshold = { it },
        velocityThreshold = { 1000.dp }
    )

    val userScrollEnabled by rememberedDerivedState { sheetState.currentDetent != FullyExpanded }
    var isLocked by rememberSaveable { mutableStateOf(false) }
    // Override back button/gesture when locked
    BackHandler(enabled = isLocked) { }

    val sheetProgress by rememberedDerivedState {
        sheetState.progress(
            imageOnlyDetent,
            expandedDetent
        )
    }

    LaunchedEffect(mediaState.value) {
        snapshotFlow { pagerState.currentPage }.collectLatest { page ->
            if (!mediaState.value.isLoading && mediaState.value.media.isEmpty() && !isStandalone) {
                windowInsetsController.toggleSystemBars(show = true)
                eventHandler.navigateUp()
            }
            if (!mediaState.value.isLoading) {
                currentPage = page
            }
        }
    }

    // set HDR Gain map
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        LaunchedEffect(mediaState.value) {
            withContext(Dispatchers.IO) {
                snapshotFlow { pagerState.currentPage }.collectLatest {
                    printWarning("Trying to set HDR mode for page $it")
                    if (currentMedia?.isImage == true) {
                        val request = ImageRequest(context, currentMedia?.getUri().toString()) {
                            currentMedia?.let { media ->
                                setExtra(
                                    key = "mediaKey",
                                    value = media.idLessKey,
                                )
                            }
                            setExtra(
                                key = "realMimeType",
                                value = currentMedia?.mimeType,
                            )
                        }
                        val result = context.sketch.execute(request)
                        (result.image as? BitmapImage)?.bitmap?.let { bitmap ->
                            val hasGainmap = bitmap.hasGainmap()
                            withContext(Dispatchers.Main.immediate) {
                                context.setHdrMode(hasGainmap)
                            }
                            printWarning("Setting HDR Mode to $hasGainmap")
                        } ?: printWarning("Resulting image null")
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

    val bottomPadding =
        remember(configuration.orientation) {
            //if (!isGestureEnabled && isLandscape) 0.dp
            //else
            paddingValues.calculateBottomPadding()
        }

    LaunchedEffect(Unit) {
        viewModel.uiEvents.collect { event ->
            when (event) {
                MediaViewEvent.ScrollToFirstPage -> pagerState.animateScrollToPage(0)
            }
        }
    }

    FullBrightnessWindow {
        val isDarkTheme = isDarkTheme()
        val allowBlur by rememberAllowBlur()
        val backgroundColor by animateColorAsState(
            if (allowBlur) Color.Black else {
                if (isDarkTheme) Color.Black else Color.White
            }
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
        ) {
            HorizontalPager(
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = if (isLocked) false else userScrollEnabled,
                state = pagerState,
                flingBehavior = PagerDefaults.flingBehavior(
                    state = pagerState,
                    snapAnimationSpec = spring(
                        stiffness = Spring.StiffnessMedium
                    ),
                    snapPositionalThreshold = 0.3f
                ),
                key = { index ->
                    mediaState.value.media.getOrNull(index) ?: "empty"
                },
                pageSpacing = 16.dp,
                beyondViewportPageCount = 0
            ) { index ->
                val media by rememberedDerivedState(mediaState.value) {
                    mediaState.value.media.getOrNull(
                        index
                    )
                }
                val canPlay = rememberSaveable(media) { mutableStateOf(false) }
                var canAnimateContent by rememberSaveable(media) { mutableStateOf(true) }
                AnimatedVisibility(
                    modifier = Modifier
                        .onVisibilityChanged { isVisible ->
                            canPlay.value =
                                (if (media?.isVideo == true) isVisible && playWhenReady else false)
                            canAnimateContent = isVisible
                        },
                    visible = media != null && initialPageSetup,
                    enter = enterAnimation,
                    exit = exitAnimation
                ) {
                    var offset by remember {
                        mutableStateOf(IntOffset(0, 0))
                    }
                    with(sharedTransitionScope) {
                        MediaPreviewComponent(
                            modifier = Modifier
                                .mediaSharedElement(
                                    allowAnimation = canAnimateContent,
                                    media = media!!,
                                    animatedVisibilityScope = animatedContentScope
                                )
                                .graphicsLayer {
                                    translationY =
                                        -((halfScreenHeight -
                                                bottomBarHeightDefault -
                                                bottomPadding -
                                                extraPaddingWithNavButtons -
                                                if (!isGestureEnabled && isLandscape) navigationBarHeight else 0.dp
                                                ).toPx() * sheetProgress)
                                },
                            media = media,
                            uiEnabled = showUI,
                            playWhenReady = canPlay,
                            onSwipeDown = {
                                if (!isLocked) {
                                    windowInsetsController.toggleSystemBars(show = true)
                                    eventHandler.navigateUp()
                                }
                            },
                            offset = offset,
                            rotationDisabled = isLocked,
                            onImageRotated = { newRotation ->
                                showRotationHelper.value =
                                    media?.isImage == true && newRotation != 0 && newRotation != 360
                                newRotationValue.intValue =
                                    (if (showRotationHelper.value) newRotation else 0)
                            },
                            onItemClick = {
                                if (sheetState.currentDetent == imageOnlyDetent) {
                                    showUI = !showUI
                                    windowInsetsController.toggleSystemBars(showUI)
                                }
                            }
                        ) { player, isPlaying, currentTime, totalTime, buffer, frameRate ->
                            Box(
                                modifier = Modifier.fillMaxSize()
                            ) {
                                val hideUiOnPlay by rememberAutoHideOnVideoPlay()
                                LaunchedEffect(isPlaying.value, hideUiOnPlay) {
                                    if (isPlaying.value && showUI && hideUiOnPlay) {
                                        delay(2.seconds)
                                        showUI = false
                                        windowInsetsController.toggleSystemBars(false)
                                    }
                                }
                                val resources = LocalResources.current
                                val width =
                                    remember(context) { resources.displayMetrics.widthPixels }
                                Spacer(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            translationX = width / 1.5f
                                        }
                                        .align(Alignment.TopEnd)
                                        .clip(CircleShape)
                                        .combinedClickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onDoubleClick = {
                                                scope.launch {
                                                    currentTime.longValue += 10 * 1000
                                                    player.seekTo(currentTime.longValue)
                                                    delay(100)
                                                    player.play()
                                                }
                                            },
                                            onClick = {
                                                if (sheetState.currentDetent == imageOnlyDetent) {
                                                    showUI = !showUI
                                                    windowInsetsController.toggleSystemBars(showUI)
                                                }
                                            }
                                        )
                                        .swipe(onOffset = { offset = it }) {
                                            windowInsetsController.toggleSystemBars(show = true)
                                            eventHandler.navigateUp()
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
                                        .combinedClickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onDoubleClick = {
                                                scope.launch {
                                                    currentTime.longValue -= 10 * 1000
                                                    player.seekTo(currentTime.longValue)
                                                    delay(100)
                                                    player.play()
                                                }
                                            },
                                            onClick = {
                                                if (sheetState.currentDetent == imageOnlyDetent) {
                                                    showUI = !showUI
                                                    windowInsetsController.toggleSystemBars(
                                                        showUI
                                                    )
                                                }
                                            }
                                        )
                                        .swipe(onOffset = { offset = it }) {
                                            windowInsetsController.toggleSystemBars(show = true)
                                            eventHandler.navigateUp()
                                        }
                                )

                                AnimatedVisibility(
                                    visible = showUI,
                                    enter = enterAnimation(DEFAULT_TOP_BAR_ANIMATION_DURATION),
                                    exit = exitAnimation(DEFAULT_TOP_BAR_ANIMATION_DURATION),
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
                                        frameRate = frameRate
                                    )
                                }
                            }
                        }
                    }
                }
            }
            val allowShowingDate by rememberShowMediaViewDateHeader()
            MediaViewAppBar(
                showUI = showUI,
                showInfo = showInfo,
                showDate = remember(currentMedia, allowShowingDate) {
                    currentMedia?.timestamp != 0L && allowShowingDate
                },
                isLocked = isLocked,
                currentDate = currentDate,
                paddingValues = paddingValues,
                currentMedia = currentMedia,
                showRotationHelper = showRotationHelper,
                rotateImage = {
                    viewModel.rotateImage(currentMedia!!, newRotationValue.intValue)
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
                            eventHandler.navigateUp()
                        }
                    }
                },
                onLock = {
                    isLocked = !isLocked
                }
            )
            LaunchedEffect(showUI) {
                if (!showUI && (sheetState.currentDetent == FullyExpanded || sheetState.targetDetent == FullyExpanded)) {
                    sheetState.animateTo(imageOnlyDetent)
                }
            }
            BackHandler(sheetState.currentDetent == FullyExpanded) {
                scope.launch {
                    sheetState.animateTo(imageOnlyDetent)
                }
            }
            val bottomSheetAlpha by animateFloatAsState(
                targetValue = if (showUI) 1f else 0f,
                animationSpec = tween(DEFAULT_TOP_BAR_ANIMATION_DURATION),
                label = "MediaViewActionsAlpha"
            )
            BottomSheet(
                state = sheetState,
                enabled = showUI && target != TARGET_TRASH && showInfo,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .graphicsLayer {
                        alpha = bottomSheetAlpha
                    }
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val actionsAlpha by animateFloatAsState(
                        targetValue = 1f - sheetProgress,
                        label = "MediaViewActions2Alpha"
                    )
                    AnimatedVisibility(
                        visible = currentMedia != null,
                        enter = enterAnimation,
                        exit = exitAnimation
                    ) {
                        val followTheme = remember(allowBlur) { !allowBlur }
                        val gradientColor by animateColorAsState(
                            if (followTheme) {
                                if (isDarkTheme) BlackScrim else WhiterBlackScrim
                            } else BlackScrim,
                        )
                        Row(
                            modifier = Modifier
                                .graphicsLayer {
                                    alpha = actionsAlpha
                                    translationY =
                                        bottomBarHeightDefault.toPx() * sheetProgress
                                }
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, gradientColor)
                                    )
                                )
                                .padding(
                                    bottom = bottomPadding + extraPaddingWithNavButtons
                                )
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            MediaViewQuickBottomBar(
                                currentMedia = currentMedia,
                                showDeleteButton = !isReadOnly,
                                enabled = showUI,
                                deleteMedia = deleteMedia,
                                restoreMedia = restoreMedia,
                                currentVault = currentVault
                            )
                        }
                    }

                    MediaViewSheetDetails(
                        albumsState = albumsState,
                        vaultState = vaultState,
                        metadataState = metadataState,
                        currentMedia = currentMedia,
                        restoreMedia = restoreMedia,
                        currentVault = currentVault,
                    )
                }
            }
        }
    }

}