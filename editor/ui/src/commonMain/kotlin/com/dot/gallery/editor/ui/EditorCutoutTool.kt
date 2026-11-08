package com.dot.gallery.editor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.dot.gallery.editor.core.model.PhotoCutoutMatte
import com.dot.gallery.editor.core.model.PixelSize
import com.dot.gallery.editor.core.model.toEdits
import com.dot.gallery.editor.render.SrgbPixels
import com.dot.gallery.editor.render.photo.PhotoEditorState
import com.dot.gallery.editor.ui.generated.resources.Res
import com.dot.gallery.editor.ui.generated.resources.editor_retouch_copied
import com.dot.gallery.editor.ui.generated.resources.editor_retouch_copy
import com.dot.gallery.editor.ui.generated.resources.editor_retouch_cutout_failed
import com.dot.gallery.editor.ui.generated.resources.editor_retouch_cutout_hint
import com.dot.gallery.editor.ui.generated.resources.editor_retouch_cutout_preparing
import com.dot.gallery.editor.ui.generated.resources.editor_retouch_cutout_surface
import com.dot.gallery.editor.ui.generated.resources.editor_retouch_exclude
import com.dot.gallery.editor.ui.generated.resources.editor_retouch_include
import com.dot.gallery.editor.ui.generated.resources.editor_retouch_no_subject
import com.dot.gallery.editor.ui.generated.resources.editor_retouch_remove_bg_hint
import com.dot.gallery.editor.ui.generated.resources.editor_retouch_reset_selection
import com.dot.gallery.editor.ui.generated.resources.editor_retouch_share
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

internal const val TAG_CATEGORY_CUTOUT = "editor.category.cutout"
internal const val TAG_CATEGORY_REMOVE_BACKGROUND = "editor.category.removeBackground"
internal const val TAG_CUTOUT_SURFACE = "editor.cutout.surface"

/** Below this share of the frame a seed selection is a speck, not a subject. */
private const val MIN_SUBJECT_COVERAGE = 0.005f

/** Above this share a seed selection is the whole frame, not a subject. */
private const val MAX_SUBJECT_COVERAGE = 0.92f

/** The dim painted outside a Cutout selection. */
private const val OUTSIDE_DIM = 0.62f

/** The fraction of [matte] that is more than half opaque. */
internal fun matteCoverage(matte: PhotoCutoutMatte): Float {
    var covered = 0L
    for (y in 0 until matte.height) {
        for (x in 0 until matte.width) {
            if (matte.alphaAt(x, y) > 127) covered++
        }
    }
    return covered.toFloat() / (matte.width.toLong() * matte.height)
}

/** The first automatic selection is kept only when it looks like a subject. */
internal fun isPlausibleSubject(matte: PhotoCutoutMatte): Boolean =
    matteCoverage(matte) in MIN_SUBJECT_COVERAGE..MAX_SUBJECT_COVERAGE

/** [pixels] with each pixel's alpha multiplied by [matte], straight RGB kept. */
internal fun applyMatteToPixels(pixels: SrgbPixels, matte: PhotoCutoutMatte): SrgbPixels {
    val width = pixels.size.width
    val height = pixels.size.height
    val out = IntArray(width * height)
    for (y in 0 until height) {
        val my = (y.toLong() * matte.height / height).toInt()
        for (x in 0 until width) {
            val mx = (x.toLong() * matte.width / width).toInt()
            val argb = pixels.argbAt(x, y)
            val alpha = (argb ushr 24) * matte.alphaAt(mx, my) / 255
            out[y * width + x] = (alpha shl 24) or (argb and 0x00FFFFFF)
        }
    }
    return SrgbPixels(pixels.size, out)
}

/** [pixels] made fully opaque, undoing an earlier matte for the segmenter. */
private fun opaquePixels(pixels: SrgbPixels): SrgbPixels {
    val out = pixels.copyPixels()
    for (index in out.indices) out[index] = out[index] or 0xFF000000.toInt()
    return SrgbPixels(pixels.size, out)
}

/**
 * The interactive selection behind Cutout and Remove background: owns the
 * host's encoded session, the prompts and the current mask. The panel creates
 * it and the canvas overlay reads it through [PhotoToolUiState].
 */
@Stable
internal class CutoutController(val removal: Boolean) {
    val pins = mutableStateListOf<EditorSegmentPoint>()
    var include by mutableStateOf(true)
    var matte by mutableStateOf<PhotoCutoutMatte?>(null)
    var busy by mutableStateOf(true)
    var failed by mutableStateOf(false)
    var noSubject by mutableStateOf(false)
    var notice by mutableStateOf<String?>(null)
    var session: EditorSegmenterSession? = null

    suspend fun segmentAndPublish(onMatte: (PhotoCutoutMatte?) -> Unit) {
        val active = session ?: return
        busy = true
        try {
            val result = if (pins.isEmpty()) null else active.segment(pins.toList())
            noSubject = result == null && pins.isNotEmpty()
            matte = result
            onMatte(result)
        } finally {
            busy = false
        }
    }

    fun close() {
        session?.close()
        session = null
    }
}

/**
 * Cutout and Remove background share one surface: Include and Exclude pick what a
 * tap on the photo does. Cutout leaves the recipe alone and offers Copy and
 * Share of the subject; Remove background lands the mask in the recipe, so the
 * saved copy keeps a transparent background.
 */
@Composable
internal fun CutoutToolContent(
    state: PhotoEditorState,
    actions: PhotoEditorActions,
    removal: Boolean,
    modifier: Modifier = Modifier
) {
    val toolUi = LocalPhotoToolUiState.current ?: remember { PhotoToolUiState() }
    val host = LocalEditorHostTools.current
    val latestState by rememberUpdatedState(state)
    val latestActions by rememberUpdatedState(actions)
    val scope = rememberCoroutineScope()
    val controller = remember(removal) { CutoutController(removal) }
    val tool = if (removal) EditorToolIds.PhotoRemoveBackground else EditorToolIds.PhotoCutout
    val toolTitle = photoToolTitle(tool)
    val resetLabel = stringResource(Res.string.editor_retouch_reset_selection)
    val copied = stringResource(Res.string.editor_retouch_copied)
    val enabled = state.document != null && !state.opening

    fun publish(matte: PhotoCutoutMatte?) {
        if (!controller.removal) return
        val edits = latestState.document?.toEdits() ?: return
        if (edits.retouch.matte == matte) return
        latestActions.beginToolEdit(toolTitle)
        latestActions.applyEdits(toolTitle, edits.copy(retouch = edits.retouch.copy(matte = matte)))
    }
    toolUi.cutoutPublish = ::publish

    DisposableEffect(controller) {
        toolUi.cutout = controller
        onDispose {
            if (toolUi.cutout === controller) {
                toolUi.cutout = null
                toolUi.cutoutPublish = null
            }
            controller.close()
        }
    }

    // Encode once the first preview exists; an earlier matte only hides pixels.
    val hasPreview = state.preview != null
    LaunchedEffect(controller, hasPreview) {
        val open = host.openSegmenter
        val pixels = latestState.preview
        if (open == null || pixels == null || controller.session != null) return@LaunchedEffect
        controller.busy = true
        val session = open(opaquePixels(pixels))
        if (session == null) {
            controller.failed = true
            controller.busy = false
            return@LaunchedEffect
        }
        controller.session = session
        // A centred seed selects the usual subject; a weak guess stays out.
        val seed = EditorSegmentPoint(0.5f, 0.5f, include = true)
        val guess = session.segment(listOf(seed))
        if (guess != null && isPlausibleSubject(guess)) {
            controller.pins += seed
            controller.matte = guess
            publish(guess)
        }
        controller.busy = false
    }

    val toolMenu = LocalPhotoToolMenu.current
    DisposableEffect(toolMenu) {
        onDispose {
            toolMenu?.resetParameterLabel = null
            toolMenu?.onResetParameter = null
        }
    }
    toolMenu?.apply {
        resetParameterLabel = resetLabel
        resetParameterTag = "editor.cutout.reset"
        resetParameterEnabled = enabled && controller.pins.isNotEmpty()
        onResetParameter = {
            controller.pins.clear()
            controller.matte = null
            controller.noSubject = false
            publish(null)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(RetouchCardPadding)
            .testTag("editor.cutout"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ConnectedButtonGroup(
            itemCount = 2,
            selectedIndex = if (controller.include) 0 else 1,
            enabled = enabled,
            scrolling = false,
            label = {
                stringResource(
                    if (it == 0) Res.string.editor_retouch_include
                    else Res.string.editor_retouch_exclude
                )
            },
            tag = { if (it == 0) "editor.cutout.include" else "editor.cutout.exclude" },
            onSelect = { controller.include = it == 0 },
            leading = { index, active ->
                Icon(
                    imageVector = if (index == 0) RetouchSymbols.Add else RetouchSymbols.Remove,
                    contentDescription = null,
                    tint = if (active) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
        )
        RetouchHint(
            stringResource(
                when {
                    controller.failed -> Res.string.editor_retouch_cutout_failed
                    controller.busy && controller.session == null ->
                        Res.string.editor_retouch_cutout_preparing
                    controller.noSubject -> Res.string.editor_retouch_no_subject
                    removal -> Res.string.editor_retouch_remove_bg_hint
                    else -> Res.string.editor_retouch_cutout_hint
                }
            )
        )
        controller.notice?.let { RetouchHint(it) }
        val copy = host.copySubject
        val share = host.shareSubject
        if (copy != null || share != null) {
            val matte = controller.matte
            val ready = enabled && matte != null && !controller.busy && state.preview != null
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (copy != null) {
                    RetouchActionButton(
                        icon = RetouchSymbols.Copy,
                        label = stringResource(Res.string.editor_retouch_copy),
                        enabled = ready,
                        tag = "editor.cutout.copy",
                        onClick = {
                            val pixels = latestState.preview ?: return@RetouchActionButton
                            val mask = controller.matte ?: return@RetouchActionButton
                            scope.launch {
                                if (copy(applyMatteToPixels(opaquePixels(pixels), mask))) {
                                    controller.notice = copied
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (share != null) {
                    RetouchActionButton(
                        icon = RetouchSymbols.Share,
                        label = stringResource(Res.string.editor_retouch_share),
                        enabled = ready,
                        tag = "editor.cutout.share",
                        onClick = {
                            val pixels = latestState.preview ?: return@RetouchActionButton
                            val mask = controller.matte ?: return@RetouchActionButton
                            scope.launch {
                                share(applyMatteToPixels(opaquePixels(pixels), mask))
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * The selection canvas: tapping adds a prompt where the photo is, pins mark
 * every prompt, and Cutout dims what falls outside the mask. Remove background
 * needs no dim because the rendered preview is already transparent there.
 */
@Composable
internal fun CutoutCanvasOverlay(
    layout: EditorImageLayout,
    toolUi: PhotoToolUiState,
    modifier: Modifier = Modifier
) {
    val controller = toolUi.cutout ?: return
    val density = LocalDensity.current
    val boxW = with(density) { layout.zoomedWidth.toPx() }
    val boxH = with(density) { layout.zoomedHeight.toPx() }
    val scope = rememberCoroutineScope()
    val label = stringResource(Res.string.editor_retouch_cutout_surface)
    val primary = MaterialTheme.colorScheme.primary
    val error = MaterialTheme.colorScheme.error
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val onError = MaterialTheme.colorScheme.onError
    val dimImage: ImageBitmap? = if (controller.removal) {
        null
    } else {
        val matte = controller.matte
        remember(matte) { matte?.let { outsideDimPixels(it).toEditorImageBitmap() } }
    }
    Box(
        modifier = modifier
            .offset(x = layout.zoomedLeft, y = layout.zoomedTop)
            .size(width = layout.zoomedWidth, height = layout.zoomedHeight)
            .testTag(TAG_CUTOUT_SURFACE)
            .semantics { contentDescription = label }
            .pointerInput(boxW, boxH) {
                detectTapGestures { position ->
                    val active = toolUi.cutout ?: return@detectTapGestures
                    if (active.busy || active.session == null) return@detectTapGestures
                    active.pins += EditorSegmentPoint(
                        (position.x / boxW).coerceIn(0f, 1f),
                        (position.y / boxH).coerceIn(0f, 1f),
                        active.include
                    )
                    scope.launch {
                        active.segmentAndPublish { toolUi.cutoutPublish?.invoke(it) }
                    }
                }
            }
    ) {
        if (dimImage != null) {
            Canvas(Modifier.fillMaxSize()) {
                drawImage(
                    image = dimImage,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(dimImage.width, dimImage.height),
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(size.width.toInt(), size.height.toInt())
                )
            }
        }
        controller.pins.forEach { pin ->
            val pinSize = 24.dp
            val half = with(density) { (pinSize / 2).toPx() }
            with(density) {
                Box(
                    modifier = Modifier
                        .offset(
                            x = (pin.x * boxW - half).toDp(),
                            y = (pin.y * boxH - half).toDp()
                        )
                        .size(pinSize)
                        .clip(CircleShape)
                        .background(if (pin.include) primary else error)
                        .border(2.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (pin.include) RetouchSymbols.Add else RetouchSymbols.Remove,
                        contentDescription = null,
                        tint = if (pin.include) onPrimary else onError,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

private fun outsideDimPixels(matte: PhotoCutoutMatte): SrgbPixels {
    val out = IntArray(matte.width * matte.height)
    for (y in 0 until matte.height) {
        for (x in 0 until matte.width) {
            val dim = ((255 - matte.alphaAt(x, y)) * OUTSIDE_DIM).toInt()
            out[y * matte.width + x] = dim shl 24
        }
    }
    return SrgbPixels(PixelSize(matte.width, matte.height), out)
}

