/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.docs

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.media.MediaMetadataRetriever
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dot.gallery.app.presentation.edit.EditScreen2
import com.dot.gallery.app.presentation.storycards.components.StoryCardsRow
import com.dot.gallery.app.presentation.trashed.components.TrashFooterContent
import com.dot.gallery.app.presentation.frameextract.FrameExportFormat
import com.dot.gallery.app.presentation.frameextract.FrameExportWorker
import com.dot.gallery.app.presentation.frameextract.FramePickerScreen
import com.dot.gallery.app.presentation.frameextract.FramePickerUiState
import com.dot.gallery.app.presentation.frameextract.FrameIdentity
import com.dot.gallery.app.presentation.frameextract.FramePreview
import com.dot.gallery.app.presentation.settings.subsettings.HandoverSettingsContent
import com.dot.gallery.app.presentation.settings.subsettings.HandoverSettingsState
import com.dot.gallery.app.presentation.frameextract.FrameSelectionReducer
import com.dot.gallery.app.presentation.frameextract.FrameVideoMetadata
import com.dot.gallery.app.presentation.frameextract.SelectionChange
import com.dot.gallery.app.presentation.frameextract.components.FrameExportFormatSheet
import com.dot.gallery.app.presentation.widget.WidgetStyleScreen
import com.dot.gallery.app.presentation.widget.data.WidgetDisplayStyle
import com.dot.gallery.app.presentation.widget.data.WidgetType
import com.dot.gallery.core.Constants
import com.dot.gallery.core.decoder.RawDevelopParams
import com.dot.gallery.core.ml.CutoutHelper
import com.dot.gallery.core.presentation.components.MainSearchBar
import com.dot.gallery.data.model.MediaMetadataState
import com.dot.gallery.docs.scenes.DocsSharedScopes
import com.dot.gallery.docs.showcase.generated.resources.Res
import com.dot.gallery.domain.model.MediaState
import com.dot.gallery.domain.model.UriMedia
import com.dot.gallery.domain.model.StoryCard
import com.dot.gallery.domain.model.StoryCardType
import com.dot.gallery.domain.uri.parseUri
import com.dot.gallery.domain.model.editor.DevelopCategory
import com.dot.gallery.domain.model.editor.DrawMode
import com.dot.gallery.domain.model.editor.DrawType
import com.dot.gallery.domain.model.editor.PathProperties
import com.dot.gallery.feature.editor.adjustments.filters.ImageFilterTypes
import com.dot.gallery.feature.editor.adjustments.varfilter.Brightness
import com.dot.gallery.feature.editor.adjustments.varfilter.Saturation
import com.dot.gallery.feature.editor.adjustments.varfilter.Vignette
import com.dot.gallery.feature.editor.components.develop.RawExportSheet
import com.dot.gallery.feature.editor.components.markup.TextMarkupOverlay
import com.dot.gallery.feature.editor.model.EditorDestination
import com.dot.gallery.feature.library.ui.TrashedContent
import com.dot.gallery.feature.viewer.components.media.CutoutController
import com.dot.gallery.feature.viewer.components.media.CutoutControlsBar
import com.dot.gallery.feature.viewer.components.media.CutoutState
import com.dot.gallery.feature.viewer.components.media.ZoomablePagerImagePointTool
import com.dot.gallery.handover.EngineInfo
import com.dot.gallery.handover.HandoverEnvelope
import com.dot.gallery.handover.HandoverInfo
import java.io.File
import kotlinx.coroutines.runBlocking

/**
 * Android-only scenes: real commonApp/feature components that exist only on
 * the platform (photo editor, frame picker, widget styler, cutout controls).
 * They render production UI over bundled docs assets — no app graph, no
 * database, no network. Registered through [platformScenes] so the wasmJs
 * live catalog never lists a scene it cannot host.
 */
actual fun platformScenes(): List<DocsScene> = listOf(
    DocsScene(
        id = "stories-carousel",
        title = "Story highlights carousel",
        pathTrace = listOf("Timeline", "Story cards"),
        states = listOf("one", "two", "many", "loading"),
        presentation = "component",
        widthDp = 412,
        heightDp = 236,
    ) { state -> DocsStoryCardsScene(state) },
    DocsScene(
        id = "editor-photo",
        title = "Photo editor",
        pathTrace = listOf("Photo", "Edit"),
        states = listOf("lighting", "colour", "effects", "filters", "markup", "crop", "save-menu"),
        captureWindow = true,
    ) { state -> DocsPhotoEditorScene(state) },
    DocsScene(
        id = "editor-raw",
        title = "RAW develop editor",
        pathTrace = listOf("RAW photo", "Edit", "Develop"),
        states = listOf("white-balance", "tone", "detail", "colour", "output"),
    ) { state -> DocsRawEditorScene(state) },
    DocsScene(
        id = "raw-export",
        title = "RAW export format",
        pathTrace = listOf("RAW photo", "Edit", "Save"),
        states = listOf("all-formats", "edits-applied"),
        captureWindow = true,
    ) { state -> DocsRawExportScene(state) },
    DocsScene(
        id = "markup-text",
        title = "Markup text input",
        pathTrace = listOf("Photo", "Edit", "Markup", "Text"),
    ) { state -> DocsMarkupTextScene(state) },
    DocsScene(
        id = "cutout-tools",
        title = "Subject cutout controls",
        pathTrace = listOf("Photo", "Cutout"),
        presentation = "component",
        widthDp = 412,
        heightDp = 170,
    ) { state -> DocsCutoutToolsScene(state) },
    DocsScene(
        id = "frame-picker",
        title = "Frame picker",
        pathTrace = listOf("Video", "Extract frames"),
        states = listOf("choose", "selected", "preparing", "exporting"),
    ) { state -> DocsFramePickerScene(state) },
    DocsScene(
        id = "frame-format",
        title = "Frame export format",
        pathTrace = listOf("Video", "Extract frames", "Save"),
        states = listOf("jpeg", "png"),
        captureWindow = true,
    ) { state -> DocsFrameFormatScene(state) },
    DocsScene(
        id = "widget-style",
        title = "Widget style",
        pathTrace = listOf("Widgets", "Configure", "Style"),
        states = listOf("photo", "grayscale", "icon", "grid"),
    ) { state -> DocsWidgetStyleScene(state) },
    DocsScene(
        id = "browse-trash",
        title = "Trash",
        pathTrace = listOf("Library", "Trash"),
        states = listOf("filled", "empty"),
    ) { state -> DocsTrashScene(state) },
    DocsScene(
        id = "settings-remote-inference",
        title = "Remote inference",
        pathTrace = listOf("Settings", "Smart Features", "Remote inference"),
        states = listOf("configured", "blank"),
    ) { state -> DocsRemoteInferenceScene(state) },
) + cloudSetupScenes + cloudSettingsScenes + cloudUseScenes + viewerScenes + exploreScenes

// ── Bundled-asset fixtures ────────────────────────────────────────────────

/**
 * Decode a bundled drawable to a platform [Bitmap]. Synchronous by design:
 * the bytes ride the same classpath the wasm bundle serves, and the capture
 * harness must never photograph a "loading" frame.
 */
private fun docsBitmap(path: String): Bitmap {
    val bytes = runBlocking { Res.readBytes(path) }
    return requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size)) {
        "docs fixture bitmap failed to decode: $path"
    }
}

/** Materialise a bundled `files/` asset into the private cache once per composition. */
@Composable
private fun rememberDocsAssetFile(path: String, fileName: String): File {
    val context = LocalContext.current
    return remember(path) {
        File(context.cacheDir, fileName).also { file ->
            if (!file.exists() || file.length() == 0L) {
                file.writeBytes(runBlocking { Res.readBytes(path) })
            }
        }
    }
}

@Composable
private fun DocsStoryCardsScene(state: String?) {
    if (state == "loading") {
        StoryCardsRow(cards = null, onCardClick = {}, modifier = Modifier.padding(vertical = 8.dp))
        return
    }
    val lake = rememberDocsAssetFile("drawable/docs_lake.jpg", "stories-lake.jpg")
    val earth = rememberDocsAssetFile("drawable/docs_earth.jpg", "stories-earth.jpg")
    val flower = rememberDocsAssetFile("drawable/docs_flower.jpg", "stories-flower.jpg")
    val detail = rememberDocsAssetFile("drawable/docs_flower_detail.jpg", "stories-detail.jpg")
    val cards = remember(lake, earth, flower, detail) {
        val files = listOf(lake, earth, flower, detail)
        val media = DocsGalleryData.mediaItems().take(4).mapIndexed { index, item ->
            item.copy(uri = parseUri("file://${files[index].absolutePath}"), path = files[index].absolutePath)
        }
        val types = listOf(StoryCardType.MEMORIES, StoryCardType.LOCATIONS, StoryCardType.FAVORITES, StoryCardType.ALBUMS)
        val titles = listOf("On this day", "At the coast", "Favorites", "Camera")
        (0..7).map { index ->
            StoryCard(
                id = index.toLong(),
                type = types[index % types.size],
                title = titles[index % titles.size],
                subtitle = "4 highlights",
                thumbnailMedia = media[index % media.size],
                mediaList = media,
            )
        }
    }
    StoryCardsRow(
        cards = when (state) {
            "one" -> cards.take(1)
            "two" -> cards.take(2)
            else -> cards
        },
        onCardClick = {},
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

/** Keeps the tagged root dark (editor chrome is dark) while a scene has no bitmap. */
@Composable
private fun EditorLoadingGate() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(modifier = Modifier.padding(16.dp), color = Color.White)
    }
}

// ── Editor scenes ─────────────────────────────────────────────────────────

/**
 * The real [EditScreen2] over the bundled lake photo. Docs callbacks stay in
 * memory — nothing is written, overridden or reverted. [initialDestination]
 * poses each tool tab through the production navigator rather than drawing a
 * fake panel.
 */
@Composable
fun DocsPhotoEditorScene(state: String?) {
    val bitmap = remember { docsBitmap("drawable/docs_lake.jpg") }
    var rawParams by remember { mutableStateOf(RawDevelopParams.AUTO) }
    val destination = when (state) {
        "colour" -> EditorDestination.Colour
        "effects" -> EditorDestination.Effects
        "filters" -> EditorDestination.Filters
        "markup" -> EditorDestination.Markup
        // "crop" and "save-menu" use the default Lighting surface — the crop
        // affordances live on the editor canvas, not in a separate panel.
        else -> EditorDestination.Lighting
    }
    val playa = remember { ImageFilterTypes.Playa.createImageFilter() }
    DocsEditScreen2(
        bitmap = bitmap,
        isChanged = true,
        canOverride = state == "save-menu",
        hasOriginalBackup = state == "save-menu",
        appliedAdjustments = when (state) {
            "colour" -> listOf(Saturation(1.4f))
            "effects" -> listOf(Vignette(0.35f))
            "filters" -> listOf(playa)
            else -> listOf(Brightness(0.15f))
        },
        previewMatrix = if (state == "filters") playa.colorMatrix() else null,
        activeFilterName = if (state == "filters") playa.name else null,
        vignetteIntensity = if (state == "effects") 0.35f else 0f,
        initialDestination = destination,
        initialMenuExpanded = state == "save-menu",
        rawParams = rawParams,
        onRawParams = { rawParams = it },
    )
}

/**
 * The real [EditScreen2] RAW develop path over the bundled photo as an
 * example render — this captures the develop controls, it does not benchmark
 * the RAW decoder against a genuine DNG.
 */
@Composable
fun DocsRawEditorScene(state: String?) {
    val bitmap = remember { docsBitmap("drawable/docs_lake.jpg") }
    var rawParams by remember { mutableStateOf(RawDevelopParams.AUTO) }
    DocsEditScreen2(
        bitmap = bitmap,
        isRawEdit = true,
        rawParams = rawParams,
        onRawParams = { rawParams = it },
        initialDestination = EditorDestination.Develop(
            when (state) {
                "tone" -> DevelopCategory.Tone
                "detail" -> DevelopCategory.Detail
                "colour" -> DevelopCategory.Colour
                "output" -> DevelopCategory.Output
                else -> DevelopCategory.WhiteBalance
            }
        ),
    )
}

/**
 * The real [EditScreen2] with every callback routed to in-memory docs state.
 * All heavy deps (ViewModel, ML, MediaStore writes) stay out — the screen is
 * fully parameterised so none are needed for a static pose.
 */
@Composable
private fun DocsEditScreen2(
    bitmap: Bitmap,
    isChanged: Boolean = false,
    canOverride: Boolean = false,
    hasOriginalBackup: Boolean = false,
    isRawEdit: Boolean = false,
    rawParams: RawDevelopParams = RawDevelopParams.AUTO,
    onRawParams: (RawDevelopParams) -> Unit = {},
    appliedAdjustments: List<com.dot.gallery.feature.editor.model.Adjustment> = emptyList(),
    previewMatrix: androidx.compose.ui.graphics.ColorMatrix? = null,
    activeFilterName: String? = null,
    vignetteIntensity: Float = 0f,
    drawMode: DrawMode = DrawMode.Draw,
    initialDestination: EditorDestination? = null,
    initialMenuExpanded: Boolean = false,
) {
    var changed by remember { mutableStateOf(isChanged) }
    var adjustments by remember { mutableStateOf(appliedAdjustments) }
    EditScreen2(
        hasOriginalBackup = hasOriginalBackup,
        canOverride = canOverride,
        isChanged = changed,
        currentImage = bitmap,
        targetImage = bitmap,
        targetUri = null,
        previewMatrix = previewMatrix,
        appliedAdjustments = adjustments,
        activeFilterName = activeFilterName,
        vignetteIntensity = vignetteIntensity,
        currentPosition = Offset.Unspecified,
        paths = emptyList(),
        pathsUndone = emptyList(),
        previousPosition = Offset.Unspecified,
        drawMode = drawMode,
        drawType = DrawType.Stylus,
        currentPathProperty = PathProperties(),
        currentPath = Path(),
        onClose = {},
        onOverride = {},
        onSaveCopy = {},
        onAdjustItemLongClick = {},
        onAdjustmentChange = { adjustments = adjustments + it; changed = true },
        onAdjustmentPreview = {},
        onToggleFilter = { adjustments = adjustments + it; changed = true },
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
        applyDrawing = { _, done -> done(true) },
        undoLastPath = {},
        redoLastPath = {},
        clearDrawing = {},
        onRevertToOriginal = { changed = false; adjustments = emptyList() },
        isRawEdit = isRawEdit,
        rawDevelopParams = if (isRawEdit) rawParams else null,
        onRawDevelopChange = onRawParams,
        initialDestination = initialDestination,
        initialMenuExpanded = initialMenuExpanded,
    )
}

// ── RAW export sheet ──────────────────────────────────────────────────────

/** The real [RawExportSheet] over the native editor. Dark — the editor forces it. */
@Composable
fun DocsRawExportScene(state: String?) {
    val bitmap = remember { docsBitmap("drawable/docs_lake.jpg") }
    var rawParams by remember { mutableStateOf(RawDevelopParams.AUTO) }
    Box(Modifier.fillMaxSize()) {
        DocsEditScreen2(
            bitmap = bitmap,
            isRawEdit = true,
            rawParams = rawParams,
            onRawParams = { rawParams = it },
        )
        var dismissed by remember { mutableStateOf(false) }
        if (!dismissed) {
            RawExportSheet(
                allowTiff = state != "edits-applied",
                onFormatSelected = {},
                onDismiss = { dismissed = true },
            )
        }
    }
}

// ── Markup text ───────────────────────────────────────────────────────────

/**
 * The real [EditScreen2] posed in markup draw/text mode with the actual
 * [TextMarkupOverlay] open — the same overlay the "Add text" button raises.
 */
@Composable
fun DocsMarkupTextScene(state: String?) {
    val bitmap = remember { docsBitmap("drawable/docs_lake.jpg") }
    Box(Modifier.fillMaxSize()) {
        DocsEditScreen2(
            bitmap = bitmap,
            drawMode = DrawMode.Text,
            initialDestination = EditorDestination.MarkupDraw,
        )
        TextMarkupOverlay(
            initialText = "Holiday notes",
            initialColor = Color(0xFF2962FF),
            onDone = { _, _ -> },
            onRemove = {},
        )
    }
}

// ── Cutout controls ───────────────────────────────────────────────────────

/**
 * The real [CutoutControlsBar] posed post-selection: include tool active and
 * a result present so both tiers (tools + export row) show. The result bitmap
 * is the bundled photo — the bar never draws it, only gates the export tier.
 */
@Composable
fun DocsCutoutToolsScene(state: String?) {
    val bitmap = remember { docsBitmap("drawable/docs_lake.jpg") }
    val cutoutState = remember {
        CutoutState().apply {
            activeTool = ZoomablePagerImagePointTool.ADD
            result = CutoutHelper.CutoutResult(bitmap, Rect(0, 0, bitmap.width, bitmap.height))
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CutoutControlsBar(
            controller = CutoutController(
                state = cutoutState,
                onToolChange = { cutoutState.activeTool = it },
                onReset = { cutoutState.updateResult(null, null) },
                onUndo = {},
                onRedo = {},
                onCopy = {},
                onShare = {},
                onSave = {},
                onClose = {},
            ),
        )
    }
}

// ── Frame picker ──────────────────────────────────────────────────────────

private data class DocsVideoFixture(
    val metadata: FrameVideoMetadata,
    val frames: List<FramePreview>,
)

/** Extract three real frames from the bundled CC0 flower clip. */
@Composable
private fun rememberDocsVideoFixture(): DocsVideoFixture {
    val videoFile = rememberDocsAssetFile("files/docs_flower.mp4", "docs_flower.mp4")
    return remember(videoFile) {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(videoFile.absolutePath)
        try {
            val metadata = FrameVideoMetadata(
                durationUs = (retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION
                )?.toLongOrNull() ?: 0L) * 1_000L,
                frameCount = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT
                )?.toIntOrNull(),
                // Verified ffprobe values for this exact bundled clip
                // (150 frames at 30000/1001) when the tag is absent.
                frameRate = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE
                )?.toFloatOrNull() ?: 30_000f / 1_001f,
                width = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
                )?.toIntOrNull() ?: 0,
                height = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
                )?.toIntOrNull() ?: 0,
                rotationDegrees = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION
                )?.toIntOrNull() ?: 0,
                isConstantFrameRate = true,
                isHdr = false,
            )
            // Exact frame indices + PTS verified with ffprobe on this clip;
            // getFrameAtIndex returns the real frame, never a nearby keyframe.
            val samples = listOf(15 to 500_500L, 21 to 700_700L, 36 to 1_201_200L)
            val frames = samples.mapNotNull { (index, ptsUs) ->
                retriever.getFrameAtIndex(index)?.let { bmp ->
                    FramePreview(FrameIdentity(frameIndex = index, presentationTimeUs = ptsUs), bmp)
                }
            }
            DocsVideoFixture(metadata, frames)
        } finally {
            retriever.release()
        }
    }
}

/**
 * The real [FramePickerScreen] fed with frames actually extracted from the
 * bundled clip. Selection/format callbacks run the production
 * [FrameSelectionReducer] in memory; export callbacks only move the demo
 * state machine — nothing is written to MediaStore.
 */
@Composable
fun DocsFramePickerScene(state: String?) {
    val fixture = rememberDocsVideoFixture()
    if (fixture.frames.isEmpty()) {
        EditorLoadingGate()
        return
    }
    var uiState by remember {
        mutableStateOf<FramePickerUiState>(framePickerStateFor(state, fixture))
    }
    FramePickerScreen(
        state = uiState,
        onBack = {},
        onRetry = { uiState = framePickerStateFor("choose", fixture) },
        onStep = { delta ->
            (uiState as? FramePickerUiState.Ready)?.let { ready ->
                val index = fixture.frames.indexOfFirst {
                    it.identity == ready.currentFrame?.identity
                }.coerceAtLeast(0)
                val next = fixture.frames[(index + delta).coerceIn(0, fixture.frames.lastIndex)]
                uiState = ready.copy(currentFrame = next)
            }
        },
        onTogglePlayback = {},
        onToggleSelection = {
            (uiState as? FramePickerUiState.Ready)?.let { ready ->
                val current = ready.currentFrame?.identity ?: return@let
                when (val change = FrameSelectionReducer.toggle(ready.selection, current)) {
                    is SelectionChange.Updated -> uiState = ready.copy(
                        selection = change.frames,
                        selectedThumbnails = fixture.frames.filter { it.identity in change.frames },
                    )
                    SelectionChange.LimitReached -> Unit
                }
            }
        },
        onJump = { identity ->
            (uiState as? FramePickerUiState.Ready)?.let { ready ->
                fixture.frames.firstOrNull { it.identity == identity }?.let { preview ->
                    uiState = ready.copy(currentFrame = preview)
                }
            }
        },
        onRemove = { identity ->
            (uiState as? FramePickerUiState.Ready)?.let { ready ->
                val selection = ready.selection.filterNot { it == identity }
                uiState = ready.copy(
                    selection = selection,
                    selectedThumbnails = fixture.frames.filter { it.identity in selection },
                )
            }
        },
        onClear = {
            (uiState as? FramePickerUiState.Ready)?.let { ready ->
                uiState = ready.copy(selection = emptyList(), selectedThumbnails = emptyList())
            }
        },
        onFormat = { format ->
            (uiState as? FramePickerUiState.Ready)?.let { ready ->
                uiState = ready.copy(format = format)
            }
        },
        onExport = { saveCurrent ->
            uiState = FramePickerUiState.Exporting(
                done = 0,
                total = if (saveCurrent) 1 else 2,
                phase = FrameExportWorker.PHASE_DECODING,
            )
        },
        onCancelExport = { uiState = framePickerStateFor("selected", fixture) },
    )
}

private fun framePickerStateFor(
    state: String?,
    fixture: DocsVideoFixture,
): FramePickerUiState = when (state) {
    "preparing" -> FramePickerUiState.PreparingSource(progress = 40)
    "exporting" -> FramePickerUiState.Exporting(
        done = 1,
        total = 2,
        phase = FrameExportWorker.PHASE_ENCODING,
    )
    else -> {
        // "choose" keeps an empty selection so the real Save-current-frame
        // label is shown; "selected" marks the first two sampled frames.
        val selection = when (state) {
            "selected" -> fixture.frames.take(2).map { it.identity }
            else -> emptyList()
        }
        FramePickerUiState.Ready(
            metadata = fixture.metadata,
            currentFrame = fixture.frames[1],
            selection = selection,
            selectedThumbnails = fixture.frames.filter { it.identity in selection },
            filmstrip = fixture.frames,
            isPreviewLoading = false,
            isPlaying = false,
            format = FrameExportFormat.JPEG,
            preferredTimeUs = 700_700L,
        )
    }
}

// ── Frame export format sheet ─────────────────────────────────────────────

/** The real [FrameExportFormatSheet] dialog over the live picker surface. */
@Composable
fun DocsFrameFormatScene(state: String?) {
    val fixture = rememberDocsVideoFixture()
    var selected by remember {
        mutableStateOf(if (state == "png") FrameExportFormat.PNG else FrameExportFormat.JPEG)
    }
    var dismissed by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        if (fixture.frames.isNotEmpty()) {
            FramePickerScreen(
                state = framePickerStateFor("selected", fixture),
                onBack = {}, onRetry = {}, onStep = {}, onTogglePlayback = {},
                onToggleSelection = {}, onJump = {}, onRemove = {}, onClear = {},
                onFormat = {}, onExport = {}, onCancelExport = {},
            )
        } else {
            EditorLoadingGate()
        }
        if (!dismissed) {
            FrameExportFormatSheet(
                selected = selected,
                onSelect = { selected = it },
                onSave = {},
                onDismiss = { dismissed = true },
            )
        }
    }
}

// ── Widget style ──────────────────────────────────────────────────────────

/**
 * The real [WidgetStyleScreen] with the bundled photos staged as the pick —
 * the same live widget faces the configurator draws. `onDone` only updates
 * the demo pose; no launcher widget is installed.
 */
@Composable
fun DocsWidgetStyleScene(state: String?) {
    val photoFile = rememberDocsAssetFile("drawable/docs_lake.jpg", "docs_lake.jpg")
    val detailFile = rememberDocsAssetFile("drawable/docs_flower.jpg", "docs_flower.jpg")
    val uris = remember(photoFile, detailFile) {
        listOf(android.net.Uri.fromFile(photoFile), android.net.Uri.fromFile(detailFile))
    }
    var style by remember {
        mutableStateOf(
            when (state) {
                "grayscale" -> WidgetDisplayStyle.GRAYSCALE
                "icon" -> WidgetDisplayStyle.ICON
                else -> WidgetDisplayStyle.IMAGE
            }
        )
    }
    WidgetStyleScreen(
        previewUris = if (state == "grid") uris else uris.take(1),
        widgetType = if (state == "grid") WidgetType.GRID else WidgetType.SINGLE,
        initialStyle = style,
        initialIcon = if (state == "icon") "✈" else null,
        isReconfigure = false,
        onBack = {},
        onDone = { newStyle, _ -> style = newStyle },
    )
}

// ── Trash ────────────────────────────────────────────────────────────────

/**
 * The real [TrashedContent] over two demo items copied into the trash
 * (`trashed = 1` + the 30-day auto-delete expiry) — so the grid, the
 * extracted [TrashFooterContent] (Restore all / Delete permanently + the
 * warning card) and the real `EmptyTrash` for `empty` all render exactly as
 * the app composes them. Footer callbacks are inert: docs never issues a
 * real restore/delete.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun DocsTrashScene(state: String?) {
    val session = LocalDocsGallerySession.current!!
    val trashedMedia = remember {
        DocsGalleryData.mediaItems().take(2).mapIndexed { index, media ->
            media.copy(
                id = 90L + index,
                trashed = 1,
                expiryTimestamp = DOCS_DEMO_EPOCH_SECONDS + 30L * 86_400L,
            )
        }
    }
    val mediaState = remember(state) {
        mutableStateOf(
            if (state == "empty") MediaState<UriMedia>(isLoading = false)
            else DocsGalleryData.mediaStateFor(trashedMedia)
        )
    }
    val metadataState = session.distributor.metadataFlow.collectAsState(
        initial = MediaMetadataState()
    )
    DocsSharedScopes { sts, acs ->
        TrashedContent(
            paddingValues = WindowInsets.systemBars.asPaddingValues(),
            mediaState = mediaState,
            metadataState = metadataState,
            lastCellIndex = rememberSaveable {
                mutableIntStateOf(Constants.cellsList.indexOf(GridCells.Fixed(3)))
            },
            lastMosaicCellIndex = rememberSaveable {
                mutableIntStateOf(Constants.mosaicColumnsList.indexOf(4).coerceAtLeast(0))
            },
            mediaViewerRoute = "media_screen",
            searchBar = { scrolling, menuItems ->
                MainSearchBar(
                    isScrolling = scrolling,
                    sharedTransitionScope = sts,
                    animatedContentScope = acs,
                    onSearchClick = {},
                    menuItems = menuItems,
                )
            },
            // No selection active in the captured pose.
            selectionSheetContent = { _, _ -> },
            aboveGridContent = {
                TrashFooterContent(
                    hasMedia = mediaState.value.media.isNotEmpty(),
                    onRestoreAll = {},
                    onDeleteAll = {},
                )
            },
            sharedTransitionScope = sts,
            animatedContentScope = acs,
        )
    }
}

/** Remote inference settings over the real [HandoverSettingsContent]. */
@Composable
private fun DocsRemoteInferenceScene(state: String?) {
    val configured = state != "blank"
    HandoverSettingsContent(
        state = HandoverSettingsState(
            enabled = configured,
            remoteUrl = if (configured) "https://handover.example.com" else "",
            localUrl = if (configured) "http://192.168.1.10:8610" else "",
            wifiSsid = if (configured) "HomeNet" else "",
            autoSwitch = configured,
            engine = "",
            sink = HandoverEnvelope.SINK_BOTH,
            preferRemoteQueries = true,
            secretSet = configured,
            status = if (configured) HandoverInfo(
                server = "refra-handover",
                version = "0.1.0",
                protocol = 1,
                principal = "alice",
                engines = listOf(
                    EngineInfo(
                        id = "eg2-q4",
                        displayName = "EmbeddingGemma 2 (q4)",
                        engine = "eg2",
                        appEngine = "EMBEDDING_GEMMA_2",
                        modalities = listOf("text", "image", "video"),
                        embeddingDim = 768,
                        loaded = true,
                    ),
                ),
            ) else null,
        ),
    )
}
