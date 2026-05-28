/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.mediaview

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.graphics.createBitmap
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisallowComposableCalls
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.media3.common.util.UnstableApi
import com.dot.gallery.feature_node.presentation.mediaview.components.media.MotionPhotoState
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
import com.dot.gallery.core.Settings.Misc.rememberAutoContrast
import com.dot.gallery.core.Settings.Misc.rememberAutoHideOnVideoPlay
import com.dot.gallery.core.Settings.Misc.rememberDateHeaderFormat
import com.dot.gallery.core.Settings.Misc.rememberExtendedDateHeaderFormat
import com.dot.gallery.core.Settings.Misc.rememberShowMediaViewDateHeader
import com.dot.gallery.core.Settings.Misc.rememberVideoAutoplay
import com.dot.gallery.core.navigateUp
import com.dot.gallery.core.setFollowTheme
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
import com.dot.gallery.feature_node.presentation.mediaview.components.GroupMemberSelectionBar
import com.dot.gallery.feature_node.presentation.mediaview.components.GroupMemberStrip
import com.dot.gallery.feature_node.presentation.mediaview.components.MediaViewAppBar
import com.dot.gallery.feature_node.presentation.mediaview.components.MediaViewQuickBottomBar
import com.dot.gallery.feature_node.presentation.mediaview.components.MediaViewSheetDetails
import com.dot.gallery.feature_node.presentation.mediaview.components.media.MediaPreviewComponent
import com.dot.gallery.feature_node.presentation.mediaview.components.media.MotionPhotoFilmstrip
import com.dot.gallery.feature_node.presentation.mediaview.components.video.SubtitleBottomSheet
import com.dot.gallery.feature_node.presentation.mediaview.components.video.VideoPlayerController
import com.dot.gallery.feature_node.presentation.util.rememberAppBottomSheetState
import com.dot.gallery.feature_node.presentation.cast.FCastViewModel
import com.dot.gallery.feature_node.presentation.cast.components.CastButton
import com.dot.gallery.feature_node.presentation.cast.components.FCastDevicePickerDialog
import com.dot.gallery.feature_node.presentation.cast.components.CastPermissionsDialog
import com.dot.gallery.feature_node.presentation.cast.components.CastStatusBanner
import com.dot.gallery.feature_node.presentation.util.shareMedia
import com.dot.gallery.feature_node.presentation.util.FullBrightnessWindow
import com.dot.gallery.feature_node.presentation.util.LocalHazeState
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
import com.dot.gallery.ui.theme.isDarkTheme
import com.github.panpf.sketch.BitmapImage
import com.github.panpf.sketch.request.ImageRequest
import com.github.panpf.sketch.sketch
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.suspendCoroutine
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
    return remember(*keys) {
        derivedStateOf(block)
    }
}

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalHazeMaterialsApi::class)
@Composable
fun <T : Media> MediaViewScreenRoute(
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
) {
    val viewModel = hiltViewModel<MediaViewViewModel>()
    MediaViewScreen(
        toggleRotate = toggleRotate,
        paddingValues = paddingValues,
        isStandalone = isStandalone,
        mediaId = mediaId,
        target = target,
        mediaState = mediaState,
        metadataState = metadataState,
        albumsState = albumsState,
        vaultState = vaultState,
        restoreMedia = restoreMedia,
        deleteMedia = deleteMedia,
        currentVault = currentVault,
        sharedTransitionScope = sharedTransitionScope,
        animatedContentScope = animatedContentScope,
        ensureMetadataAvailable = viewModel::ensureMetadataAvailable,
        rotateImage = viewModel::rotateImage,
        uiEvents = viewModel.uiEvents,
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
    ensureMetadataAvailable: (Media?, MediaMetadataState) -> Unit = { _, _ -> },
    rotateImage: (Media, Int) -> Unit = { _, _ -> },
    uiEvents: SharedFlow<MediaViewEvent> = MutableSharedFlow(),
    motionPhotoStateFactory: @Composable (Media?) -> MotionPhotoState = { remember { MotionPhotoState() } },
) = ProvideInsets {
    val eventHandler = LocalEventHandler.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val windowInsetsController = rememberWindowInsetsController()

    var initialPageSetup by rememberSaveable(mediaId) { mutableStateOf(false) }

    // FCast
    val fcastVm: FCastViewModel = hiltViewModel()
    val fcastState by fcastVm.state.collectAsStateWithLifecycle()
    var showCastPicker by rememberSaveable { mutableStateOf(false) }
    var showCastPermissions by rememberSaveable { mutableStateOf(false) }

    // IDs of media confirmed for trash/delete but not yet removed from mediaState
    var pendingTrashIds by rememberSaveable { mutableStateOf(emptySet<Long>()) }

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

    // Use pagerMedia for paging (only representatives when grouped, otherwise all media)
    val pagerItems by rememberedDerivedState(mediaState.value, pendingTrashIds) {
        val pager = mediaState.value.pagerMedia
        val items = pager.ifEmpty { mediaState.value.media }
        if (pendingTrashIds.isEmpty()) items else items.filter { it.id !in pendingTrashIds }
    }

    // Use only primitive ids/sizes as saveable keys (avoid passing full media list object)
    val initialPage = rememberSaveable(mediaId, pagerItems.size) {
        pagerItems.indexOfFirst { it.id == mediaId }.coerceAtLeast(0)
    }
    var currentPage by rememberSaveable(initialPage) { mutableIntStateOf(initialPage) }
    var isVideoZoomed by rememberSaveable { mutableStateOf(false) }

    val pagerState = rememberPagerState(
        initialPage = initialPage,
        initialPageOffsetFraction = 0f,
        pageCount = { pagerItems.size }
    )

    // Group members for the current page's media
    val currentGroupMembers by rememberedDerivedState(mediaState.value, currentPage, pendingTrashIds) {
        val currentId =
            pagerItems.getOrNull(currentPage)?.id ?: return@rememberedDerivedState emptyList()
        val members = mediaState.value.mediaGroups[currentId] ?: emptyList()
        if (pendingTrashIds.isEmpty()) members else members.filter { it.id !in pendingTrashIds }
    }

    // Track which group member is selected (null = show representative/pager item)
    var selectedMemberOverrideId by rememberSaveable { mutableStateOf<Long?>(null) }

    // Multi-select state for group members
    var groupMultiSelectMode by rememberSaveable { mutableStateOf(false) }
    var groupMultiSelectedIds by rememberSaveable { mutableStateOf(emptySet<Long>()) }

    // Select first group member when swiping to a different page
    LaunchedEffect(currentPage) {
        selectedMemberOverrideId = null
        groupMultiSelectMode = false
        groupMultiSelectedIds = emptySet()
        isVideoZoomed = false
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
        ensureMetadataAvailable(currentMedia, metadataState.value)
    }

    LaunchedEffect(initialPage, mediaState.value.isLoading) {
        if (!mediaState.value.isLoading && !initialPageSetup) {
            if (pagerState.currentPage != initialPage) {
                pagerState.scrollToPage(initialPage)
            }
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
    var isTopDark by remember { mutableStateOf(false) }
    var isBottomDark by remember { mutableStateOf(false) }
    val autoContrast by rememberAutoContrast()
    val motionPhotoState = motionPhotoStateFactory(currentMedia)
    // Key rotation helpers by media id, not whole media object (prevents Serializable fallback of Media inside internal Pair)
    val newRotationValue = rememberSaveable(currentMedia?.id ?: -1L) { mutableIntStateOf(0) }
    val showRotationHelper = rememberSaveable(currentMedia?.id ?: -1L) { mutableStateOf(false) }

    BackHandler(!showUI) {
        windowInsetsController.toggleSystemBars(show = true)
        eventHandler.navigateUp()
    }
    val activity = LocalActivity.current
    val window = LocalWindowInfo.current
    val density = LocalDensity.current

    // Reset forced orientation when leaving the media view screen
    DisposableEffect(activity) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

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

    val bottomPadding = remember(paddingValues) {
        paddingValues.calculateBottomPadding()
    }

    val imageOnlyDetent =
        remember(bottomBarHeightDefault, extraPaddingWithNavButtons, bottomPadding) {
            ImageOnly { bottomBarHeightDefault + extraPaddingWithNavButtons + bottomPadding + 16.dp }
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


    LaunchedEffect(mediaState.value) {
        snapshotFlow { pagerState.currentPage }.collectLatest { page ->
            if (!mediaState.value.isLoading && pagerItems.isEmpty() && !isStandalone) {
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
        delay(350L)

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
                suspendCoroutine { cont ->
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

    LaunchedEffect(Unit) {
        uiEvents.collect { event ->
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
                userScrollEnabled = if (isLocked || isVideoZoomed) false else userScrollEnabled,
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
                        val groupMembers = pagerMedia?.let { mediaState.value.mediaGroups[it.id] }
                        if (selectedMemberOverrideId != null) {
                            groupMembers?.find { it.id == selectedMemberOverrideId } ?: pagerMedia
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
                    val displayMedia = media ?: return@AnimatedVisibility
                    val sharedElementMedia = pagerMedia ?: displayMedia
                    with(sharedTransitionScope) {
                            MediaPreviewComponent(
                                modifier = Modifier
                                    .mediaSharedElement(
                                        allowAnimation = canAnimateContent,
                                        media = sharedElementMedia,
                                        animatedVisibilityScope = animatedContentScope
                                    ),
                                containerModifier = Modifier,
                                media = media,
                                uiEnabled = showUI,
                                playWhenReady = canPlay,
                                onSwipeDown = {
                                    if (!isLocked) {
                                        windowInsetsController.toggleSystemBars(show = true)
                                        runCatching {
                                            (activity as ComponentActivity).onBackPressedDispatcher.onBackPressed()
                                        }.getOrElse {
                                            eventHandler.navigateUp()
                                        }
                                    }
                                },
                                offset = offset,
                                isPanorama = mediaMetadata?.isPanorama == true,
                                isPhotosphere = mediaMetadata?.isPhotosphere == true,
                                isMotionPhoto = mediaMetadata?.isMotionPhoto == true,
                                motionPhotoState = motionPhotoState,
                                currentVault = currentVault,
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
                                },
                                onZoomChange = { zoomed -> isVideoZoomed = zoomed }
                            ) { player, isPlaying, currentTime, totalTime, buffer, frameRate, subtitleState ->
                                val subtitleTracks = subtitleState.subtitleTracks
                                val onSelectSubtitle = subtitleState.onSelectSubtitle
                                val onDisableSubtitles = subtitleState.onDisableSubtitles
                                val addExternalSubtitle = subtitleState.onAddExternalSubtitle
                                Box(
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    val hideUiOnPlay by rememberAutoHideOnVideoPlay()
                                    var uiInteracted by remember { mutableStateOf(false) }
                                    LaunchedEffect(isPlaying.value, hideUiOnPlay) {
                                        if (isPlaying.value && showUI && hideUiOnPlay && !uiInteracted) {
                                            delay(2.seconds)
                                            showUI = false
                                            windowInsetsController.toggleSystemBars(false)
                                        }
                                    }
                                    // Mute local player while casting to avoid double audio
                                    val isCasting = fcastState.connectedDevice != null
                                    LaunchedEffect(isCasting) {
                                        if (isCasting) {
                                            player.volume = 0f
                                        } else {
                                            player.volume = 1f
                                        }
                                    }
                                    val resources = LocalResources.current
                                    val width =
                                        remember(context) { resources.displayMetrics.widthPixels }
                                    if (!isVideoZoomed) {
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
                                    }

                                    val onRemoveSubtitle = subtitleState.onRemoveSubtitle
                                    val subtitleSheetState = rememberAppBottomSheetState()

                                    val subtitleFilePicker = rememberLauncherForActivityResult(
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
            // Sync status bar icon color with the top image luminance
            val isCurrentVideo by rememberedDerivedState(currentMedia) {
                currentMedia?.isVideo == true
            }
            LaunchedEffect(isTopDark, autoContrast, isDarkTheme, allowBlur, isCurrentVideo) {
                val followTheme = if (autoContrast) !isTopDark
                    else !allowBlur && !isCurrentVideo
                windowInsetsController.isAppearanceLightStatusBars =
                    if (followTheme) !isDarkTheme else false
                eventHandler.setFollowTheme(followTheme)
            }
            DisposableEffect(Unit) {
                onDispose {
                    eventHandler.setFollowTheme(true)
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
                isImageDark = isTopDark,
                autoContrast = autoContrast,
                isMotionPhoto = motionPhotoState.isDetected,
                isMotionPlaying = motionPhotoState.isPlaying,
                onToggleMotionPhoto = { motionPhotoState.togglePlayback() },
                rotateImage = {
                    rotateImage(currentMedia!!, newRotationValue.intValue)
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
                },
                castButton = if (fcastVm.isCastAvailable()) { { followTheme ->
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
                } } else null,
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
                visible = showUI && motionPhotoState.isDetected && motionPhotoState.compositeFilmstrip != null,
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
                    onTap = {
                        if (sheetState.currentDetent == imageOnlyDetent) {
                            showUI = !showUI
                            windowInsetsController.toggleSystemBars(showUI)
                        }
                    }
                )
            }
            // Group member thumbnail strip (for grouped RAW+JPG, bursts, edits)
            val showMotionFilmstrip =
                motionPhotoState.isDetected && motionPhotoState.compositeFilmstrip != null
            AnimatedVisibility(
                visible = showUI && !showMotionFilmstrip && currentGroupMembers.size > 1,
                enter = enterAnimation(DEFAULT_TOP_BAR_ANIMATION_DURATION),
                exit = exitAnimation(DEFAULT_TOP_BAR_ANIMATION_DURATION),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .graphicsLayer {
                        translationY =
                            bottomBarHeightDefault.toPx() * sheetState.progress(imageOnlyDetent, expandedDetent)
                    }
                    .padding(horizontal = 16.dp)
                    .padding(
                        bottom = bottomPadding + extraPaddingWithNavButtons +
                                bottomBarHeightDefault + 32.dp
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
                                groupMultiSelectedIds = currentGroupMembers.map { it.id }.toSet()
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
                        GroupMemberStrip(
                            members = currentGroupMembers,
                            selectedId = selectedMemberOverrideId
                                ?: currentGroupMembers.firstOrNull()?.id
                                ?: currentPagerItemId,
                            onSelect = { id ->
                                selectedMemberOverrideId = id
                            },
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
                    .graphicsLayer {
                        alpha = bottomSheetAlpha
                    }
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
                                    val progress = sheetState.progress(imageOnlyDetent, expandedDetent)
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
                                    .hazeEffect(
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
                                    enabled = showUI,
                                    deleteMedia = deleteMedia,
                                    restoreMedia = restoreMedia,
                                    currentVault = currentVault,
                                    isImageDark = isBottomDark,
                                    autoContrast = autoContrast,
                                    onTrashConfirmed = {
                                        val trashedId = currentMedia?.id
                                        if (trashedId != null) {
                                            val newPending = pendingTrashIds + trashedId
                                            pendingTrashIds = newPending
                                            // If all items are now filtered out, navigate up
                                            val state = mediaState.value
                                            val allItems = state.pagerMedia.ifEmpty { state.media }
                                            val remaining = allItems.count { it.id !in newPending }
                                            if (remaining <= 0 && !isStandalone) {
                                                windowInsetsController.toggleSystemBars(show = true)
                                                eventHandler.navigateUp()
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }

                    MediaViewSheetDetails(
                        albumsState = albumsState,
                        vaultState = vaultState,
                        metadataState = metadataState,
                        currentMedia = currentMedia,
                        restoreMedia = restoreMedia,
                        currentVault = currentVault,
                        motionPhotoState = motionPhotoState,
                    )
                }
            }
        }
    }

}