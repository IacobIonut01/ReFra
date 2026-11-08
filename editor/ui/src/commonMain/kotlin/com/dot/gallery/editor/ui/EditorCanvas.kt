package com.dot.gallery.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.dot.gallery.editor.ui.icons.EditorSymbols
import com.dot.gallery.editor.ui.icons.ZoomIn
import dev.chrisbanes.haze.hazeSource
import com.dot.gallery.editor.ui.icons.Compare
import com.dot.gallery.editor.ui.icons.KeyboardArrowDown
import com.dot.gallery.editor.ui.icons.Redo
import com.dot.gallery.editor.ui.icons.Undo
import com.dot.gallery.editor.core.model.PhotoEdits
import com.dot.gallery.editor.core.model.PhotoDocument
import com.dot.gallery.editor.core.model.PixelSize
import com.dot.gallery.editor.render.photo.PhotoEditorState
import com.dot.gallery.editor.ui.generated.resources.Res
import com.dot.gallery.editor.ui.generated.resources.editor_compare_after
import com.dot.gallery.editor.ui.generated.resources.editor_compare_before
import com.dot.gallery.editor.ui.generated.resources.editor_compare_edited
import com.dot.gallery.editor.ui.generated.resources.editor_compare_original
import com.dot.gallery.editor.ui.generated.resources.editor_compare_divider
import com.dot.gallery.editor.ui.generated.resources.editor_opening
import com.dot.gallery.editor.ui.generated.resources.editor_redo
import com.dot.gallery.editor.ui.generated.resources.editor_undo
import com.dot.gallery.editor.ui.generated.resources.editor_zoom_2x
import com.dot.gallery.editor.ui.generated.resources.editor_zoom_fit
import com.dot.gallery.editor.ui.generated.resources.editor_zoom_menu
import org.jetbrains.compose.resources.stringResource
import com.github.panpf.zoomimage.compose.rememberZoomState
import com.github.panpf.zoomimage.ZoomImage
import com.github.panpf.zoomimage.subsampling.ImageInfo
import com.github.panpf.zoomimage.subsampling.SubsamplingImage
import com.github.panpf.zoomimage.compose.zoom.ZoomableState
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

internal const val TAG_CANVAS = "editor.canvas"
internal const val TAG_IMAGE = "editor.image"
internal const val TAG_COMPARE = "editor.compare"
internal const val TAG_ZOOM_FIT = "editor.zoomFit"
internal const val TAG_ZOOM_2X = "editor.zoom2x"
internal const val TAG_ZOOM_LABEL = "editor.zoomLabel"
internal const val TAG_ZOOM_HUD = "editor.zoomHud"
internal const val TAG_BEFORE_CHIP = "editor.beforeChip"

private const val WHEEL_STEP = 1.15f
private const val ZOOM_HUD_MS = 3_000L

// Approximate single-row width at fontScale 1.0; scaled by fontScale so the
// two-row fallback also fires when enlarged text would overflow mid sizes.
private const val MIN_FOOTER_ROW_DP = 300f

/**
 * The photo surface: a fit-centred image over the neutral surround inside a
 * clipped, pannable viewport, with a real footer strip below it — the footer
 * is layout, not an overlay, so its controls never draw on the photo.
 *
 * Zoom/pan/compare live in the hoisted [viewport] (owned by
 * [PhotoEditorContent], keyed on the source) so a layout flip between the
 * compact and sidebar compositions keeps the view. [bitmap] is the already-
 * converted image, prepared by the owner off the UI thread; null keeps the
 * neutral surround and the opening indicator.
 *
 * When [gpuRenderer] is supplied with [gpuEdits] and [gpuImageSize], the
 * runtime shader fills the image box instead of [bitmap]: [gpuImageSize] is
 * the post-geometry output frame the fit maths run against. A draw that
 * returns false reports through [onGpuFrameFailed] and the owner falls back
 * to the CPU [bitmap].
 *
 * [showFooter] hides the utility strip entirely (focus mode replaces it
 * with floating chrome). [overlay] draws inside the canvas bounds — the
 * owner uses it for floating focus chrome; it shares the box's geometry
 * and does not intercept the image's gestures. The navigator overlays the
 * viewport while zoomed past fit and shares the same draw inputs.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun EditorCanvas(
    state: PhotoEditorState,
    actions: PhotoEditorActions,
    viewport: EditorViewportState,
    bitmap: ImageBitmap?,
    imageDescription: String,
    compactUtility: Boolean,
    modifier: Modifier = Modifier,
    gpuRenderer: PhotoShaderRenderer? = null,
    gpuEdits: PhotoEdits? = null,
    gpuImageSize: PixelSize? = null,
    onGpuFrameFailed: (PhotoShaderRenderer) -> Unit = {},
    showFooter: Boolean = true,
    // Focus mode floats a HUD bar over the top of the canvas — the
    // navigator inset drops below it so they never overlap.
    focusMode: Boolean = false,
    // How much of the canvas bottom floating chrome covers; the split
    // stage labels stay above it.
    splitLabelBottomInset: Dp = 0.dp,
    // Figma V2 phone anatomy: the media field is inset inside the canvas
    // region before fit maths run — fit, pan and the navigator all work on
    // the inner field, and the drawn image is clipped to the corner radius.
    mediaPadding: PaddingValues = PaddingValues.Zero,
    mediaCornerRadius: Dp = 0.dp,
    // The labelled phone utility row (Undo/Redo/zoom chips) replaces the
    // compact icon row — only meaningful with [compactUtility].
    compactUtilityLabels: Boolean = false,
    // The v4 field anatomy: the fit card is inset inside the canvas
    // region by the chrome lanes it must clear (the floating utility row
    // above, the floating toolbar below). The card is what ZoomImage
    // uses for the resting fit geometry; the viewing container extends
    // behind the floating chrome so deep zoom is never card-clipped.
    fitFieldInsets: PaddingValues = PaddingValues.Zero,
    // The rectangle tool chrome frames its own surface against (the crop
    // frame's fixed, handle-safe region). It defaults to the media field;
    // the crop family keeps the media full bleed while the frame still
    // clears the action row and the card.
    frameFieldInsets: PaddingValues = fitFieldInsets,
    // With chrome floating over the region, the card's clip and backdrop
    // interpolate out to the region edges as the zoom passes fit — the
    // zoomed media always lands on the card's dark ground, never the
    // bare window.
    expandFieldOnZoom: Boolean = false,
    // The card's corner radius at fit — interpolated to zero by the same
    // zoom factor that expands it.
    fieldCornerRadius: Dp = 0.dp,
    // The surround behind the card — unset keeps the neutral canvas
    // tone; the v4 home passes the window's black so the card edge reads.
    surroundColor: Color? = null,
    // The standalone 75×48 Before chip overlaid at start 24 / bottom 20 of
    // the canvas region — the workspace's compare peek in addition to the
    // toolbar's Before destination.
    showBeforeChip: Boolean = false,
    // Hoisted so the owner can hand keyboard focus back to the viewport
    // after a control that stole it (a removed catalogue cell, the
    // Apply/Cancel bar) is gone — workspace shortcuts must never go dead.
    focusRequester: FocusRequester = remember { FocusRequester() },
    // Draws exactly over the fit image box, in canvas-region
    // coordinates — computed from the padded inner field and the fit
    // size *before* zoom (tool canvases run at zoom 1). Above the
    // media, below the navigator and [overlay] chrome.
    imageOverlay: @Composable BoxScope.(EditorImageLayout) -> Unit = {},
    overlay: @Composable BoxScope.() -> Unit = {}
) {
    val editorColors = LocalEditorColors.current
    val cropView = LocalPhotoToolUiState.current?.cropView
    val gpuDrawing =
        gpuRenderer != null && gpuEdits != null && gpuImageSize != null
    val imageReady = gpuDrawing || bitmap != null
    // Zoom/pan live in a ZoomImage state shared by the media field's
    // gesture modifier and the footer's preset chips.
    val zoomState = rememberZoomState()
    val zoomable = zoomState.zoomable
    val subsampling = zoomState.subsampling
    val scope = rememberCoroutineScope()
    val latestState = rememberUpdatedState(state)
    val latestActions = rememberUpdatedState(actions)
    val displayedPixels = if (gpuDrawing || viewport.showsBefore) state.originalPreview else state.preview
    val previewPainter = rememberEditorPixelPainter(displayedPixels, bitmap)
    val readerFactory = LocalPhotoImageReaderFactory.current
    val document = state.document?.let {
        if (viewport.showsBefore) PhotoDocument(it.source) else it
    }
    // The crop tool's preview mode swaps the recipe for its crop-dropped
    // display document: preview pixels, fit geometry and deep-zoom tiles
    // all describe the full uncropped source behind the crop window.
    val displayDocument = if (viewport.showsBefore) document
        else state.displayDocument ?: document
    val tilePreview = if (viewport.showsBefore) state.originalPreview
        else state.preview.takeIf { state.previewDocument == displayDocument }
    val tilesCoherent = viewport.showsBefore || gpuDrawing ||
        state.previewDocument == displayDocument
    val pixelImage = remember(
        displayDocument, tilePreview, readerFactory, tilesCoherent
    ) {
        if (displayDocument != null && readerFactory != null && tilesCoherent) {
            EditorPixelImage(
                document = displayDocument,
                preview = tilePreview,
                openReader = { readerFactory(displayDocument) },
                onError = { error ->
                    scope.launch {
                        if (latestState.value.document?.source ==
                            displayDocument.source
                        ) {
                            latestActions.value.reportPreviewFallback(error)
                        }
                    }
                }
            )
        } else null
    }
    remember(subsampling) {
        subsampling.setRegionDecoders(listOf(EditorPixelRegionDecoder.Factory()))
    }
    // Switch generations synchronously, before ZoomImage can draw stale tiles.
    subsampling.setImage(
        pixelImage?.let {
            SubsamplingImage(it, ImageInfo(it.size.width, it.size.height, EditorPixelRegionDecoder.PIXEL_MIME))
        }
    )
    Column(
        modifier = modifier
            // No root clipToBounds: the media box and the rounded
            // backdrop clip themselves inside, and a root clip would
            // shave the hit region off interactive overlay chrome
            // (the layer transform's 48 dp corner handles) wherever a
            // handle rides a card edge.
            .background(surroundColor ?: editorColors.canvas)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            val layoutDirection = LocalLayoutDirection.current
            val density = LocalDensity.current
            // Fit against the inset card, but view against a container
            // covering the whole field once the clip expands.
            val containerWidth = constraints.maxWidth.toFloat()
            val containerHeight = constraints.maxHeight.toFloat()
            val cardLeft = with(density) {
                fitFieldInsets.calculateLeftPadding(layoutDirection).toPx()
            }
            val cardRight = with(density) {
                fitFieldInsets.calculateRightPadding(layoutDirection).toPx()
            }
            val cardTop = with(density) {
                fitFieldInsets.calculateTopPadding().toPx()
            }
            val cardBottom = with(density) {
                fitFieldInsets.calculateBottomPadding().toPx()
            }
            val cardWidth = (containerWidth - cardLeft - cardRight)
                .coerceAtLeast(0f)
            val cardHeight = (containerHeight - cardTop - cardBottom)
                .coerceAtLeast(0f)
            // The rectangle tool chrome's field — the same card unless the
            // caller keeps the media full bleed behind it.
            val frameLeft = with(density) {
                frameFieldInsets.calculateLeftPadding(layoutDirection).toPx()
            }
            val frameRight = with(density) {
                frameFieldInsets.calculateRightPadding(layoutDirection).toPx()
            }
            val frameTop = with(density) {
                frameFieldInsets.calculateTopPadding().toPx()
            }
            val frameBottom = with(density) {
                frameFieldInsets.calculateBottomPadding().toPx()
            }
            val frameWidth = (containerWidth - frameLeft - frameRight)
                .coerceAtLeast(0f)
            val frameHeight = (containerHeight - frameTop - frameBottom)
                .coerceAtLeast(0f)
            // Extend toward the heavier inset to preserve the card's
            // center using ZoomImage's supported Center alignment.
            val viewingLeft = min(0f, cardLeft - cardRight)
            val viewingTop = min(0f, cardTop - cardBottom)
            val viewingWidth = containerWidth + abs(cardLeft - cardRight)
            val viewingHeight = containerHeight + abs(cardTop - cardBottom)
            val padLeft = with(density) {
                mediaPadding.calculateLeftPadding(layoutDirection).toPx()
            }
            val padRight = with(density) {
                mediaPadding.calculateRightPadding(layoutDirection).toPx()
            }
            val padTop = with(density) { mediaPadding.calculateTopPadding().toPx() }
            val padBottom = with(density) {
                mediaPadding.calculateBottomPadding().toPx()
            }
            // Fit and navigator maths run against the padded card field.
            val canvasWidth = (cardWidth - padLeft - padRight)
                .coerceAtLeast(0f)
            val canvasHeight = (cardHeight - padTop - padBottom)
                .coerceAtLeast(0f)
            val currentBitmap = bitmap
            // The frame's pixel dimensions — the shader output size when the
            // runtime shader draws, otherwise the bitmap's own.
            val imageSize: Size? = when {
                gpuDrawing -> Size(
                    gpuImageSize!!.width.toFloat(),
                    gpuImageSize!!.height.toFloat()
                )
                currentBitmap != null -> Size(
                    currentBitmap.width.toFloat(),
                    currentBitmap.height.toFloat()
                )
                else -> null
            }
            val fitSize = if (imageSize != null) {
                val scale = min(
                    canvasWidth / imageSize.width,
                    canvasHeight / imageSize.height
                )
                Size(imageSize.width * scale, imageSize.height * scale)
            } else {
                Size.Zero
            }

            // The content node's pixel size — rounded once so the drawn
            // box, the ZoomImage content size and the overlay seam all
            // describe the same rect to the pixel.
            val fitSizeInt = if (fitSize.width > 0f && fitSize.height > 0f) {
                IntSize(
                    fitSize.width.roundToInt(),
                    fitSize.height.roundToInt()
                )
            } else {
                IntSize.Zero
            }
            // The split compare runs across the image as it sits on screen —
            // the fit rect under the zoom transform, clipped to the card — so
            // the divider, its knob and the stage labels stay on the photo.
            val splitRect = if (viewport.showsSplit && fitSizeInt != IntSize.Zero) {
                val transform = zoomable.transform
                val originX = transform.scaleOrigin.pivotFractionX * viewingWidth
                val originY = transform.scaleOrigin.pivotFractionY * viewingHeight
                val left = viewingLeft + originX +
                    (0f - originX) * transform.scaleX + transform.offsetX
                val top = viewingTop + originY +
                    (0f - originY) * transform.scaleY + transform.offsetY
                Rect(
                    left = left,
                    top = top,
                    right = left + fitSizeInt.width * transform.scaleX,
                    bottom = top + fitSizeInt.height * transform.scaleY
                ).intersect(
                    Rect(cardLeft, cardTop, cardLeft + cardWidth, cardTop + cardHeight)
                ).takeIf { it.width > 0f && it.height > 0f }
            } else {
                null
            }

            // The ZoomImage content node is the fit rect itself: base
            // transform must be identity — `None`, not `Fit`, because the
            // content already IS the fit result and `Fit` would re-scale
            // it into the container. User zoom scales it around the pinch
            // centroid and past-fit the media covers the region edges.
            // Setters run inline in composition exactly like ZoomImage —
            // a LaunchedEffect would leave one frame un-transformed and
            // the image at the field's top corner.
            LaunchedEffect(zoomable, layoutDirection) {
                zoomable.setContentScale(ContentScale.None)
                zoomable.setLayoutDirection(layoutDirection)
            }
            // ZoomImage reports its own viewing-container measurement.
            if (fitSizeInt != IntSize.Zero) {
                zoomable.setContentSize(fitSizeInt)
            }
            // The hoisted viewport survives layout flips; a fresh canvas
            // restores the saved transform once geometry lands, then every
            // user/programmatic transform mirrors back into it.
            var transformRestored by remember(viewport, zoomable) {
                mutableStateOf(false)
            }
            LaunchedEffect(zoomable, viewport, fitSizeInt, cardWidth,
                cardHeight
            ) {
                if (!transformRestored && fitSizeInt != IntSize.Zero &&
                    cardWidth > 0f && cardHeight > 0f
                ) {
                    transformRestored = true
                    // Always pull the stored triple — a fresh viewport
                    // at fit must drag the live zoomable off the old
                    // document's zoom, not just skip a no-op write.
                    zoomable.scale(
                        viewport.zoom * zoomable.baseTransform.scaleX
                    )
                    zoomable.offset(
                        Offset(viewport.panX, viewport.panY)
                    )
                }
            }
            LaunchedEffect(zoomable, viewport) {
                snapshotFlow { zoomable.userTransform }.collect { t ->
                    // A swapped-in viewport owns the stored transform —
                    // until the restore lands, the live zoomable still
                    // carries the OLD document's gesture state and must
                    // not write it into the fresh viewport.
                    if (!transformRestored) return@collect
                    if (viewport.zoom != t.scaleX) viewport.zoom = t.scaleX
                    if (viewport.panX != t.offsetX) viewport.panX = t.offsetX
                    if (viewport.panY != t.offsetY) viewport.panY = t.offsetY
                }
            }
            // The reverse edge: the hoisted viewport is also the public
            // control surface — the utility row's Fit/2x chips and the
            // desktop fit action write it directly. Those writes pull the
            // zoomable to the stored transform; gesture-driven mirrors
            // above land as equal values, so the pair converges with no
            // write-back loop. `continuousTransformType` guards the
            // mirror-lag race: while a gesture is mid-flight the stored
            // triple trails the live transform and must never write back.
            LaunchedEffect(zoomable, viewport) {
                snapshotFlow {
                    Triple(viewport.zoom, viewport.panX, viewport.panY)
                }.collect { (zoom, panX, panY) ->
                    if (!transformRestored ||
                        zoomable.contentSize == IntSize.Zero ||
                        zoomable.containerSize == IntSize.Zero ||
                        zoomable.continuousTransformType != 0
                    ) {
                        return@collect
                    }
                    val t = zoomable.userTransform
                    if (t.scaleX != zoom || t.offsetX != panX ||
                        t.offsetY != panY
                    ) {
                        zoomable.scale(zoom * zoomable.baseTransform.scaleX)
                        zoomable.offset(Offset(panX, panY))
                    }
                }
            }

            // Hold-to-compare: ZoomImage fires onLongPress once at the hold
            // threshold, and the release watcher restores the after frame —
            // finger up means after.
            var holdComparing by remember { mutableStateOf(false) }
            val holdReady = rememberUpdatedState(imageReady)
            DisposableEffect(viewport) {
                onDispose {
                    if (holdComparing) viewport.compareBefore = false
                    viewport.zoomReadoutVisible = false
                }
            }

            // The 100% readout rides every scale change for three seconds
            // (collectLatest restarts the countdown on each new value) and
            // never shows for the fit the canvas opens on.
            val hudReady = rememberUpdatedState(imageReady)
            LaunchedEffect(zoomable, viewport) {
                snapshotFlow { viewport.zoom }.drop(1).collectLatest {
                    if (hudReady.value) {
                        viewport.zoomReadoutVisible = true
                        delay(ZOOM_HUD_MS)
                        viewport.zoomReadoutVisible = false
                    }
                }
            }

            // Canvas-first keyboard control: once a live image exists the
            // viewport holds focus so arrows/zoom keys work without a click.
            LaunchedEffect(imageReady) {
                if (imageReady) focusRequester.requestFocus()
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = imageDescription }
                    .testTag(TAG_CANVAS)
                    // The media layer is the Haze source — floating chrome
                    // (navigator, focus bars) lives outside it, so sampling
                    // never reads the consumers back into the backdrop.
                    .hazeSource(LocalEditorHazeState.current)
                    .focusRequester(focusRequester)
                    .pointerInput(viewport) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.none { it.pressed } &&
                                    holdComparing
                                ) {
                                    holdComparing = false
                                    viewport.compareBefore = false
                                }
                            }
                        }
                    }
                    .onKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown || !imageReady) {
                            return@onKeyEvent false
                        }
                        // Arrow keys nudge the pan by a tenth of the canvas;
                        // 0 fits. Zoomable re-clamps every request itself.
                        when (event.key) {
                            Key.Equals, Key.NumPadAdd -> {
                                scope.launch {
                                    zoomable.scaleBy(WHEEL_STEP)
                                }
                                true
                            }
                            Key.Minus, Key.NumPadSubtract -> {
                                scope.launch {
                                    zoomable.scaleBy(1f / WHEEL_STEP)
                                }
                                true
                            }
                            Key.DirectionLeft -> {
                                scope.launch {
                                    zoomable.offsetBy(
                                        Offset(cardWidth * 0.1f, 0f)
                                    )
                                }
                                true
                            }
                            Key.DirectionRight -> {
                                scope.launch {
                                    zoomable.offsetBy(
                                        Offset(-cardWidth * 0.1f, 0f)
                                    )
                                }
                                true
                            }
                            Key.DirectionUp -> {
                                scope.launch {
                                    zoomable.offsetBy(
                                        Offset(0f, cardHeight * 0.1f)
                                    )
                                }
                                true
                            }
                            Key.DirectionDown -> {
                                scope.launch {
                                    zoomable.offsetBy(
                                        Offset(0f, -cardHeight * 0.1f)
                                    )
                                }
                                true
                            }
                            Key.Zero, Key.NumPad0 -> {
                                scope.launch {
                                    zoomable.scale(zoomable.minScale)
                                    zoomable.offset(Offset.Zero)
                                }
                                true
                            }
                            else -> false
                        }
                    }
                    .focusable()
            ) {
                if (imageReady && fitSizeInt != IntSize.Zero) {
                    // The card corners only read as chrome at fit — they
                    // flatten out as the zoom takes the media over the
                    // region edges so the full-bleed frame is square.
                    val cornerZoom =
                        ((viewport.zoom - 1f) / 0.12f).coerceIn(0f, 1f)
                    // The card rect the media clips to: the fit insets
                    // while at fit, expanding out to the region edge as
                    // the zoom passes 1x so zoomed media always lands on
                    // the card's dark ground, never the bare window.
                    val expand =
                        if (expandFieldOnZoom) cornerZoom else 0f
                    val animL = cardLeft * (1f - expand)
                    val animT = cardTop * (1f - expand)
                    val animW = containerWidth - animL -
                        cardRight * (1f - expand)
                    val animH = containerHeight - animT -
                        cardBottom * (1f - expand)
                    // The card backdrop — the always-dark field behind
                    // the media, sharing the animated clip rect.
                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    animL.roundToInt(),
                                    animT.roundToInt()
                                )
                            }
                            .size(
                                with(density) {
                                    DpSize(animW.toDp(), animH.toDp())
                                }
                            )
                            .clip(
                                RoundedCornerShape(
                                    fieldCornerRadius * (1f - expand)
                                )
                            )
                            .background(editorColors.canvas)
                    )
                    // The media clip box sits on the same animated rect.
                    // ZoomImage owns a field-sized container inside the
                    // expanding clip. Alignment preserves the inset fit
                    // position without clipping zoomed pixels at the card.
                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    animL.roundToInt(),
                                    animT.roundToInt()
                                )
                            }
                            .size(
                                with(density) {
                                    DpSize(animW.toDp(), animH.toDp())
                                }
                            )
                            .clipToBounds()
                    ) {
                        Box(
                            modifier = Modifier
                                .offset {
                                    IntOffset(
                                        (viewingLeft - animL).roundToInt(),
                                        (viewingTop - animT).roundToInt()
                                    )
                                }
                                .wrapContentSize(Alignment.TopStart, unbounded = true)
                                .size(
                                    with(density) {
                                        DpSize(
                                            viewingWidth.toDp(),
                                            viewingHeight.toDp()
                                        )
                                    }
                                )
                                // A tool-owned view of the fit image (the
                                // crop tool's fixed frame) scales/moves
                                // the media in region coordinates; read
                                // in the draw phase only.
                                .graphicsLayer {
                                    val view = cropView
                                    if (view != null && view.active) {
                                        val s = view.scale
                                        transformOrigin = TransformOrigin(0f, 0f)
                                        scaleX = s
                                        scaleY = s
                                        translationX = (s - 1f) * viewingLeft + view.translationX
                                        translationY = (s - 1f) * viewingTop + view.translationY
                                    }
                                }
                        ) {
                            val cellPx = with(density) { 12.dp.toPx() }
                            val cornerPx = with(density) {
                                (mediaCornerRadius * (1f - cornerZoom)).toPx()
                            }
                            val photoPainter = remember(
                                fitSize, previewPainter, gpuRenderer, gpuEdits,
                                cellPx, cornerPx, editorColors
                            ) {
                                EditorPhotoPainter(
                                    intrinsicSize = Size(
                                        fitSizeInt.width.toFloat(), fitSizeInt.height.toFloat()
                                    ),
                                    preview = previewPainter,
                                    renderer = if (gpuDrawing) gpuRenderer else null,
                                    edits = if (gpuDrawing) gpuEdits else null,
                                    onFrameFailed = onGpuFrameFailed,
                                    checkerA = editorColors.checkerA,
                                    checkerB = editorColors.checkerB,
                                    cellSize = cellPx,
                                    corner = cornerPx
                                )
                            }
                            ZoomImage(
                                painter = photoPainter,
                                contentDescription = imageDescription,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.None,
                                zoomState = zoomState,
                                scrollBar = null,
                                onLongPress = {
                                    if (holdReady.value) {
                                        holdComparing = true
                                        viewport.compareBefore = true
                                    }
                                }
                            )
                            Box(Modifier.matchParentSize().zoomTransform(zoomable)) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .size(
                                            with(density) {
                                                DpSize(
                                                    fitSizeInt.width.toDp(),
                                                    fitSizeInt.height.toDp()
                                                )
                                            }
                                        )
                                        .testTag(TAG_IMAGE)
                                )
                            }
                            // The split compare's original half: the
                            // upright source under the same zoom transform
                            // as the edit, clipped in container space at
                            // the divider so both halves pan together.
                            if (splitRect != null) {
                                val originalPainter = rememberEditorPixelPainter(
                                    state.originalPreview, null
                                )
                                val dividerX = splitRect.left +
                                    viewport.splitFraction * splitRect.width - viewingLeft
                                if (originalPainter != null) {
                                    Box(
                                        Modifier
                                            .matchParentSize()
                                            .drawWithContent {
                                                clipRect(right = dividerX) {
                                                    this@drawWithContent.drawContent()
                                                }
                                            }
                                            .testTag(TAG_SPLIT_ORIGINAL)
                                    ) {
                                        Box(
                                            Modifier
                                                .matchParentSize()
                                                .zoomTransform(zoomable)
                                        ) {
                                            Image(
                                                painter = originalPainter,
                                                contentDescription = null,
                                                contentScale = ContentScale.FillBounds,
                                                modifier = Modifier
                                                    .align(Alignment.TopStart)
                                                    .size(
                                                        with(density) {
                                                            DpSize(
                                                                fitSizeInt.width.toDp(),
                                                                fitSizeInt.height.toDp()
                                                            )
                                                        }
                                                    )
                                                    .clip(
                                                        RoundedCornerShape(
                                                            with(density) { cornerPx.toDp() }
                                                        )
                                                    )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else if (state.opening) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(40.dp))
                        Text(
                            text = stringResource(Res.string.editor_opening),
                            style = MaterialTheme.typography.labelLarge,
                            color = editorColors.onCanvas,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                }
            }

            // The image-bound overlay seam — the same fit box the media
            // draws into, handed out so crop/perspective chrome sits on
            // the image without re-deriving the fit maths. This scope is
            // the outer canvas region — the fit rect is centred in it by
            // the zoomable base transform (at 1x, tools run unzoomed). The
            // int-rounded size keeps the seam identical to the drawn box.
            if (imageReady && fitSizeInt != IntSize.Zero) {
                val overlayTransform = zoomable.transform
                val overlayOriginX =
                    overlayTransform.scaleOrigin.pivotFractionX * viewingWidth
                val overlayOriginY =
                    overlayTransform.scaleOrigin.pivotFractionY * viewingHeight
                imageOverlay(
                    EditorImageLayout(
                        left = with(density) {
                            (cardLeft + (cardWidth - fitSizeInt.width) / 2f)
                                .toDp()
                        },
                        top = with(density) {
                            (cardTop + (cardHeight - fitSizeInt.height) / 2f)
                                .toDp()
                        },
                        width = with(density) { fitSizeInt.width.toDp() },
                        height = with(density) { fitSizeInt.height.toDp() },
                        fieldLeft = with(density) { frameLeft.toDp() },
                        fieldTop = with(density) { frameTop.toDp() },
                        fieldWidth = with(density) { frameWidth.toDp() },
                        fieldHeight = with(density) { frameHeight.toDp() },
                        zoomedLeft = with(density) {
                            (viewingLeft + overlayOriginX -
                                overlayOriginX * overlayTransform.scaleX +
                                overlayTransform.offsetX).toDp()
                        },
                        zoomedTop = with(density) {
                            (viewingTop + overlayOriginY -
                                overlayOriginY * overlayTransform.scaleY +
                                overlayTransform.offsetY).toDp()
                        },
                        zoomedWidth = with(density) {
                            (fitSizeInt.width * overlayTransform.scaleX).toDp()
                        },
                        zoomedHeight = with(density) {
                            (fitSizeInt.height * overlayTransform.scaleY).toDp()
                        }
                    )
                )
            }

            // The interactive minimap — same bitmap/shader draw path as the
            // image box, the same fit maths for the visible rectangle. In
            // focus mode the top bar floats 16dp in with a ≥56dp row, so the
            // navigator drops below it instead of sliding under the HUD.
            if (imageReady && imageSize != null && viewport.zoom > 1.001f &&
                !expandFieldOnZoom
            ) {
                EditorNavigator(
                    canvasSizePx = Size(canvasWidth, canvasHeight),
                    imageSizePx = imageSize,
                    viewport = viewport,
                    bitmap = currentBitmap,
                    gpuRenderer = if (gpuDrawing) gpuRenderer else null,
                    gpuEdits = if (gpuDrawing) gpuEdits else null,
                    onGpuFrameFailed = onGpuFrameFailed,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(
                            top = if (focusMode) 88.dp else 12.dp,
                            end = 12.dp
                        )
                )
            }
            // The standalone Before peek: a 75×48 chip parked 24/20 dp in
            // from the canvas region's start/bottom corner — toggles the
            // same compare state the toolbar's Before destination drives.
            if (showBeforeChip && imageReady) {
                BeforeOverlayChip(
                    comparing = viewport.compareSplit,
                    onToggle = {
                        viewport.compareSplit = !viewport.compareSplit
                    },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 24.dp, bottom = 20.dp)
                )
            }
            // Hold-compare reads its stage on the image itself — the
            // word rides the top of the media for as long as the finger
            // stays down; lifting restores the after frame and the
            // label with it.
            if (holdComparing) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = with(density) { cardTop.toDp() } + 12.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .chromeBlur(RoundedCornerShape(16.dp))
                        .testTag(TAG_COMPARE)
                ) {
                    Text(
                        text = stringResource(
                            Res.string.editor_compare_before
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(
                            horizontal = 14.dp, vertical = 8.dp
                        )
                    )
                }
            }
            if (imageReady && splitRect != null) {
                SplitCompareDivider(
                    viewport = viewport,
                    rect = splitRect,
                    containerWidth = containerWidth,
                    containerHeight = containerHeight,
                    labelBottomInset = splitLabelBottomInset
                )
            }
            // Zoom readout: surfaces for three seconds after each scale
            // change, then drops away until the next gesture.
            if (imageReady && viewport.zoomReadoutVisible && !expandFieldOnZoom) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                        .copy(alpha = 0.9f),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(
                            top = if (focusMode) 88.dp else 12.dp
                        )
                        .testTag(TAG_ZOOM_HUD)
                ) {
                    Text(
                        text = formatZoomPercent(viewport.zoom),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(
                            horizontal = 12.dp, vertical = 6.dp
                        )
                    )
                }
            }
            overlay()
        }

        if (showFooter) {
            // The utility row sits on the window surface, not on the canvas
            // surround — the canvas keeps its always-dark field.
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.testTag(TAG_PHOTO_UTILITY)
            ) {
                CanvasFooter(
                    state = state,
                    actions = actions,
                    viewport = viewport,
                    imageReady = imageReady,
                    compactUtility = compactUtility,
                    compactUtilityLabels = compactUtilityLabels,
                    onFit = {
                        scope.launch {
                            zoomable.scale(
                                zoomable.minScale, animated = true
                            )
                            zoomable.offset(Offset.Zero)
                        }
                    },
                    onZoom2x = {
                        scope.launch {
                            zoomable.scale(
                                zoomable.minScale * 2f, animated = true
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = EditorV2Layout.PhoneUtility)
                )
            }
        }
    }
}

/**
 * Small neutral checkerboard behind translucent image pixels — the grid is
 * built once per measured size into a single path, then drawn as two fills.
 */
@Composable
private fun Checkerboard(modifier: Modifier = Modifier) {
    val a = LocalEditorColors.current.checkerA
    val b = LocalEditorColors.current.checkerB
    Spacer(
        modifier = modifier.drawWithCache {
            val cell = 8.dp.toPx()
            val path = Path()
            var row = 0
            var y = 0f
            while (y < size.height) {
                var col = 0
                var x = 0f
                while (x < size.width) {
                    if ((row + col) % 2 == 1) {
                        path.addRect(
                            Rect(
                                x, y,
                                min(x + cell, size.width),
                                min(y + cell, size.height)
                            )
                        )
                    }
                    x += cell
                    col++
                }
                y += cell
                row++
            }
            onDrawBehind {
                drawRect(a)
                drawPath(path, b)
            }
        }
    )
}

/**
 * The strip below the viewport: Before on the left, the zoom dropdown on the
 * right. In compact mode the header's edit commands (Undo/Redo) live here
 * instead — the compact header only carries Back, the source name and Save.
 * At large fonts or narrow windows the row splits in two rather than letting
 * labels wrap or clip.
 *
 * The footer sits on the window surface — a plain scheme row, not a
 * re-themed canvas band.
 */
@Composable
private fun CanvasFooter(
    state: PhotoEditorState,
    actions: PhotoEditorActions,
    viewport: EditorViewportState,
    imageReady: Boolean,
    compactUtility: Boolean,
    compactUtilityLabels: Boolean,
    onFit: () -> Unit,
    onZoom2x: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (compactUtility && compactUtilityLabels) {
        PhoneUtilityRow(
            state = state,
            actions = actions,
            viewport = viewport,
            imageReady = imageReady,
            onFit = onFit,
            onZoom2x = onZoom2x,
            modifier = modifier
        )
        return
    }
    BoxWithConstraints(
        modifier = modifier.padding(horizontal = 8.dp)
    ) {
        val fontScale = LocalDensity.current.fontScale
        val twoRows = fontScale >= 1.5f ||
            maxWidth < (MIN_FOOTER_ROW_DP * fontScale).dp
        if (twoRows) {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (compactUtility) {
                        FooterUndoRedo(state, actions)
                    } else {
                        CompareChip(
                            selected = viewport.compareSplit,
                            enabled = imageReady,
                            onToggle = {
                                viewport.compareSplit =
                                    !viewport.compareSplit
                            }
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    ZoomMenu(viewport.zoom, imageReady, onFit, onZoom2x)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Spacer(Modifier.weight(1f))
                    RenderIndicator(state.rendering)
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (compactUtility) {
                    FooterUndoRedo(state, actions)
                } else {
                    CompareChip(
                        selected = viewport.compareSplit,
                        enabled = imageReady,
                        onToggle = {
                            viewport.compareSplit = !viewport.compareSplit
                        }
                    )
                }
                Spacer(Modifier.weight(1f))
                RenderIndicator(state.rendering)
                ZoomMenu(viewport.zoom, imageReady, onFit, onZoom2x)
            }
        }
    }
}

/**
 * The V3 phone utility row — exactly 48 dp: three labelled chips spanning
 * the full width — Undo, the wider zoom chip and Redo — on 16 dp
 * horizontal insets with 12 dp gaps. Chips paint 16 dp corners on
 * `surfaceContainerHigh` with 18 dp icons; there is no overflow button —
 * Reset lives on the header's More menu and the Edits pane. Enlarged type
 * falls back to the icon row: fixed-width chips cannot hold scaled
 * labels.
 */
@Composable
internal fun PhoneUtilityRow(
    state: PhotoEditorState,
    actions: PhotoEditorActions,
    viewport: EditorViewportState,
    imageReady: Boolean,
    onFit: () -> Unit,
    onZoom2x: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fontScale = LocalDensity.current.fontScale
    Row(
        modifier = modifier
            .height(EditorV2Layout.PhoneUtility)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (fontScale >= 1.5f) {
            FooterUndoRedo(state, actions)
            Spacer(Modifier.weight(1f))
            ZoomMenu(viewport.zoom, imageReady, onFit, onZoom2x)
            return@Row
        }
        UtilityChip(
            label = stringResource(Res.string.editor_undo),
            icon = { Icon(EditorSymbols.Undo, null, Modifier.size(18.dp)) },
            enabled = state.canUndo,
            onClick = actions.undo,
            modifier = Modifier
                .weight(1f)
                .testTag(TAG_UNDO)
        )
        // Content-sized chip, centred inside its flex slot so callers that
        // use the whole row keep the previous full-width look.
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            ZoomChip(
                zoom = viewport.zoom,
                showReadout = viewport.zoomReadoutVisible,
                imageReady = imageReady,
                onFit = onFit,
                onZoom2x = onZoom2x
            )
        }
        UtilityChip(
            label = stringResource(Res.string.editor_redo),
            icon = { Icon(EditorSymbols.Redo, null, Modifier.size(18.dp)) },
            enabled = state.canRedo,
            onClick = actions.redo,
            modifier = Modifier
                .weight(1f)
                .testTag(TAG_REDO)
        )
    }
}

/** One labelled utility chip — fixed width on the exact 48 dp row. */
@Composable
internal fun UtilityChip(
    label: String,
    icon: @Composable () -> Unit,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .chromeBlur(RoundedCornerShape(16.dp))
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = label,
                onClick = onClick
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        CompositionLocalProvider(
            LocalContentColor provides
                if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                }
        ) {
            icon()
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

/**
 * The floating chrome material — a backdrop blur over the canvas
 * (haze, same 12 dp recipe as [EditorFloatingPanel]) under a tinted
 * fill, so chips legibly ride whatever the media does behind them.
 * With the host's blur disabled the same shape stays opaque.
 */
@Composable
internal fun Modifier.chromeBlur(
    shape: Shape,
    baseColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh
): Modifier {
    val prefs = LocalEditorUiPreferences.current
    val haze = LocalEditorHazeState.current
    val style = remember(baseColor) {
        HazeBlurStyle {
            blurRadius(12.dp)
            noiseFactor(0f)
            colorEffects(
                listOf(HazeColorEffect.tint(baseColor.copy(alpha = 0.6f)))
            )
            fallbackColorEffect(
                HazeColorEffect.tint(baseColor)
            )
        }
    }
    return this
        .clip(shape)
        .background(baseColor, shape)
        .then(
            if (prefs.blurEnabled) {
                Modifier.hazeBlur(
                    input = HazeInput.Backdrop(haze),
                    style = style
                )
            } else {
                Modifier
            }
        )
}

/**
 * The 75×48 Before chip floating inside the workspace canvas — toggles the
 * same compare state as the toolbar's Before destination. The label names
 * the picture a tap shows next: "Before" while the edit is up, "After"
 * while comparing.
 */
@Composable
internal fun BeforeOverlayChip(
    comparing: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(width = 75.dp, height = 48.dp)
            .clip(RoundedCornerShape(24.dp))
            .chromeBlur(RoundedCornerShape(24.dp))
            .clickable(
                role = Role.Switch,
                onClickLabel = stringResource(Res.string.editor_compare_before),
                onClick = onToggle
            )
            .semantics { this.selected = comparing }
            .testTag(TAG_BEFORE_CHIP),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(
                if (comparing) {
                    Res.string.editor_compare_after
                } else {
                    Res.string.editor_compare_before
                }
            ),
            style = MaterialTheme.typography.labelLarge,
            color = if (comparing) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1
        )
    }
}

/** The compact-mode Undo/Redo pair that the header no longer carries. */
@Composable
private fun FooterUndoRedo(
    state: PhotoEditorState,
    actions: PhotoEditorActions
) {
    WorkbenchIconButton(
        icon = EditorSymbols.Undo,
        label = stringResource(Res.string.editor_undo),
        onClick = actions.undo,
        modifier = Modifier.testTag(TAG_UNDO),
        enabled = state.canUndo
    )
    WorkbenchIconButton(
        icon = EditorSymbols.Redo,
        label = stringResource(Res.string.editor_redo),
        onClick = actions.redo,
        modifier = Modifier.testTag(TAG_REDO),
        enabled = state.canRedo
    )
}

@Composable
private fun CompareChip(
    selected: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit
) {
    // A compact labelled toggle — the compare icon + "Before" text on a
    // 48 dp lane, not a fat chip. `selected` stays a real semantics state.
    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                enabled = enabled,
                role = Role.Switch,
                onClickLabel = stringResource(Res.string.editor_compare_before),
                onClick = onToggle
            )
            .semantics { this.selected = selected }
            .then(
                if (selected) {
                    Modifier.background(
                        MaterialTheme.colorScheme.secondaryContainer
                    )
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag(TAG_COMPARE)
    ) {
        Icon(
            imageVector = EditorSymbols.Compare,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = stringResource(Res.string.editor_compare_before),
            color = contentColor,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}

/**
 * The zoom preset control: a field showing "Fit" at 1× or the current
 * N× zoom, opening a menu with the working Fit and 2× presets. The trigger
 * keeps [TAG_ZOOM_LABEL] so zoom state stays assertable on one node.
 */
@Composable
internal fun ZoomMenu(
    zoom: Float,
    imageReady: Boolean,
    onFit: () -> Unit,
    onZoom2x: () -> Unit
) {
    var open by remember { mutableStateOf(false) }
    val label =
        if (zoom <= 1.001f) stringResource(Res.string.editor_zoom_fit)
        else formatZoom(zoom)
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag(TAG_ZOOM_LABEL)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                )
                .clickable(
                    enabled = imageReady,
                    role = Role.Button,
                    onClickLabel = stringResource(
                        Res.string.editor_zoom_menu, label
                    )
                ) { open = true }
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (imageReady) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                maxLines = 1,
                softWrap = false
            )
            Icon(
                imageVector = EditorSymbols.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(
                    alpha = if (imageReady) 0.85f else 0.45f
                ),
                modifier = Modifier.size(16.dp)
            )
        }
        ZoomDropdownMenu(open, { open = false }, zoom, imageReady, onFit, onZoom2x)
    }
}

/**
 * The phone utility row's zoom chip — same Fit/N× readout and preset menu
 * as [ZoomMenu] on the 16 dp-corner chip anatomy the Figma V2 utility row
 * paints. Keeps [TAG_ZOOM_LABEL] on the trigger.
 */
@Composable
internal fun ZoomChip(
    zoom: Float,
    showReadout: Boolean,
    imageReady: Boolean,
    onFit: () -> Unit,
    onZoom2x: () -> Unit,
    modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }
    // The Figma chip always reads the percentage — "100%" at fit, never
    // the word "Fit" (that stays the dropdown's action).
    val label = formatZoomPercent(zoom)
    Box(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .height(48.dp)
                .testTag(TAG_ZOOM_LABEL)
                .clip(RoundedCornerShape(16.dp))
                .chromeBlur(RoundedCornerShape(16.dp))
                .clickable(
                    enabled = imageReady,
                    role = Role.Button,
                    onClickLabel = stringResource(
                        Res.string.editor_zoom_menu, label
                    )
                ) { open = true }
        ) {
            Icon(
                imageVector = EditorSymbols.ZoomIn,
                contentDescription = stringResource(Res.string.editor_zoom_menu, label),
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp)
            )
            if (showReadout) Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (imageReady) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .padding(start = 6.dp)
                    .testTag(TAG_ZOOM_HUD)
            )
        }
        ZoomDropdownMenu(open, { open = false }, zoom, imageReady, onFit, onZoom2x)
    }
}

/** The Fit/2× preset dropdown shared by both zoom triggers. */
@Composable
private fun ZoomDropdownMenu(
    open: Boolean,
    onDismiss: () -> Unit,
    zoom: Float,
    imageReady: Boolean,
    onFit: () -> Unit,
    onZoom2x: () -> Unit
) {
    DropdownMenu(expanded = open, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = {
                Text(
                    stringResource(Res.string.editor_zoom_fit),
                    maxLines = 1,
                    softWrap = false
                )
            },
            enabled = imageReady && zoom > 1.001f,
            onClick = {
                onDismiss()
                onFit()
            },
            modifier = Modifier.testTag(TAG_ZOOM_FIT)
        )
        DropdownMenuItem(
            text = {
                Text(
                    stringResource(Res.string.editor_zoom_2x),
                    maxLines = 1,
                    softWrap = false
                )
            },
            enabled = imageReady && abs(zoom - 2f) > 0.001f,
            onClick = {
                onDismiss()
                onZoom2x()
            },
            modifier = Modifier.testTag(TAG_ZOOM_2X)
        )
    }
}

@Composable
private fun RenderIndicator(visible: Boolean) {
    if (visible) {
        CircularProgressIndicator(
            modifier = Modifier
                .size(18.dp)
                .padding(start = 4.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * ZoomImage's `zooming` without its `clipToBounds` — the same scale,
 * offset and rotation layers, but the clip is owned by the animated
 * card rect outside so the media never outruns the card's expansion.
 */
private fun Modifier.zoomTransform(zoomable: ZoomableState): Modifier =
    this
        .graphicsLayer {
            val transform = zoomable.transform
            scaleX = transform.scaleX
            scaleY = transform.scaleY
            translationX = transform.offsetX
            translationY = transform.offsetY
            transformOrigin = transform.scaleOrigin
        }
        .graphicsLayer {
            val transform = zoomable.transform
            rotationZ = transform.rotation
            transformOrigin = transform.rotationOrigin
        }

internal const val TAG_SPLIT_ORIGINAL = "editor.split.original"
internal const val TAG_SPLIT_HANDLE = "editor.split.handle"
internal const val TAG_SPLIT_BEFORE = "editor.split.before"
internal const val TAG_SPLIT_AFTER = "editor.split.after"

// A side label steps aside once its half is too narrow to read as one.
private const val SPLIT_LABEL_MIN_FRACTION = 0.15f
private const val SPLIT_KEY_STEP = 0.05f

/**
 * The split compare's chrome in canvas-region coordinates: a hairline
 * divider over the on-screen image [rect] at
 * [EditorViewportState.splitFraction], the 48 dp drag knob centred on it,
 * and the Before / After stage labels riding the bottom of each half. Only the knob takes pointer input, so pan and zoom keep
 * working on both halves; it is an adjustable slider for accessibility
 * (set-progress plus arrow keys) and its label names it.
 */
@Composable
private fun BoxScope.SplitCompareDivider(
    viewport: EditorViewportState,
    rect: Rect,
    containerWidth: Float,
    containerHeight: Float,
    labelBottomInset: Dp
) {
    val density = LocalDensity.current
    val editorColors = LocalEditorColors.current
    val fraction = viewport.splitFraction
    val dividerX = rect.left + fraction * rect.width
    val latestWidth = rememberUpdatedState(rect.width)
    val label = stringResource(Res.string.editor_compare_divider)
    val knob = 48.dp
    val knobPx = with(density) { knob.toPx() }
    Box(
        Modifier
            .matchParentSize()
            .drawBehind {
                val stroke = 2.dp.toPx()
                drawRect(
                    color = editorColors.onCanvas,
                    topLeft = Offset(dividerX - stroke / 2f, rect.top),
                    size = Size(stroke, rect.height)
                )
            }
    )
    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (dividerX - knobPx / 2f).roundToInt(),
                    (rect.center.y - knobPx / 2f).roundToInt()
                )
            }
            .size(knob)
            .clip(CircleShape)
            .chromeBlur(CircleShape)
            .pointerInput(viewport) {
                detectHorizontalDragGestures { change, dragAmount ->
                    change.consume()
                    val width = latestWidth.value
                    if (width > 0f) {
                        viewport.splitFraction =
                            (viewport.splitFraction + dragAmount / width)
                                .coerceIn(0f, 1f)
                    }
                }
            }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val delta = when (event.key) {
                    Key.DirectionLeft -> -SPLIT_KEY_STEP
                    Key.DirectionRight -> SPLIT_KEY_STEP
                    else -> return@onKeyEvent false
                }
                viewport.splitFraction =
                    (viewport.splitFraction + delta).coerceIn(0f, 1f)
                true
            }
            .focusable()
            .semantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                setProgress { value ->
                    viewport.splitFraction = value.coerceIn(0f, 1f)
                    true
                }
            }
            .testTag(TAG_SPLIT_HANDLE),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = EditorSymbols.Compare,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp)
        )
    }
    // The labels ride the image's bottom edge: its top corners belong to
    // the histogram pill.
    val labelBottom = maxOf(
        with(density) { (containerHeight - rect.bottom).toDp() },
        labelBottomInset
    ) + 12.dp
    if (fraction >= SPLIT_LABEL_MIN_FRACTION) {
        SplitStageLabel(
            text = stringResource(Res.string.editor_compare_original),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(
                    start = with(density) { rect.left.toDp() } + 12.dp,
                    bottom = labelBottom
                )
                .testTag(TAG_SPLIT_BEFORE)
        )
    }
    if (fraction <= 1f - SPLIT_LABEL_MIN_FRACTION) {
        SplitStageLabel(
            text = stringResource(Res.string.editor_compare_edited),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = with(density) { (containerWidth - rect.right).toDp() } + 12.dp,
                    bottom = labelBottom
                )
                .testTag(TAG_SPLIT_AFTER)
        )
    }
}

@Composable
private fun SplitStageLabel(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .chromeBlur(RoundedCornerShape(16.dp))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}
