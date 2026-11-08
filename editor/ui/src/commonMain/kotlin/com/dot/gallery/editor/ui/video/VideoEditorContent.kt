package com.dot.gallery.editor.ui.video

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationEventHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.dot.gallery.editor.core.model.VideoTrackKind
import com.dot.gallery.editor.core.time.EditorTime
import com.dot.gallery.editor.render.video.VideoEditorState
import com.dot.gallery.editor.render.video.VideoInsertion
import com.dot.gallery.editor.render.video.VideoErrorKind
import com.dot.gallery.editor.render.video.VideoExportResult
import com.dot.gallery.editor.ui.EditorLayoutMode
import com.dot.gallery.editor.ui.rememberEditorSettingsStore
import com.dot.gallery.editor.ui.EditorRecoverySurface
import com.dot.gallery.editor.ui.EditorToolCatalogueSheet
import com.dot.gallery.editor.ui.EditorToolGroup
import com.dot.gallery.editor.ui.EditorToolId
import com.dot.gallery.editor.ui.EditorToolIds
import com.dot.gallery.editor.ui.EditorV2Layout
import com.dot.gallery.editor.ui.LocalEditorColors
import com.dot.gallery.editor.ui.LocalEditorHazeState
import com.dot.gallery.editor.ui.TAG_VIDEO_REGION_HEADER
import com.dot.gallery.editor.ui.TAG_VIDEO_REGION_PREVIEW
import com.dot.gallery.editor.ui.TAG_VIDEO_REGION_TIMELINE
import com.dot.gallery.editor.ui.TAG_VIDEO_REGION_TRANSPORT
import com.dot.gallery.editor.ui.TAG_VIDEO_V3_INLINE_INSPECTOR
import com.dot.gallery.editor.ui.WorkbenchIconButton
import com.dot.gallery.editor.ui.generated.resources.Res
import com.dot.gallery.editor.ui.generated.resources.editor_cancel_export
import com.dot.gallery.editor.ui.generated.resources.editor_cancel_export_confirm
import com.dot.gallery.editor.ui.generated.resources.editor_cancel_export_title
import com.dot.gallery.editor.ui.generated.resources.editor_discard_confirm
import com.dot.gallery.editor.ui.generated.resources.editor_discard_title
import com.dot.gallery.editor.ui.generated.resources.editor_dismiss
import com.dot.gallery.editor.ui.generated.resources.editor_done
import com.dot.gallery.editor.ui.generated.resources.editor_group_audio
import com.dot.gallery.editor.ui.generated.resources.editor_group_deliver
import com.dot.gallery.editor.ui.generated.resources.editor_group_edit
import com.dot.gallery.editor.ui.generated.resources.editor_group_picture
import com.dot.gallery.editor.ui.generated.resources.editor_keep_editing
import com.dot.gallery.editor.ui.generated.resources.editor_open_export
import com.dot.gallery.editor.ui.generated.resources.editor_retry
import com.dot.gallery.editor.ui.generated.resources.editor_undo
import com.dot.gallery.editor.ui.generated.resources.editor_redo
import com.dot.gallery.editor.ui.generated.resources.editor_video_back_to_timeline
import com.dot.gallery.editor.ui.generated.resources.editor_video_buffering
import com.dot.gallery.editor.ui.generated.resources.editor_video_canvas
import com.dot.gallery.editor.ui.generated.resources.editor_video_error_export
import com.dot.gallery.editor.ui.generated.resources.editor_video_error_open
import com.dot.gallery.editor.ui.generated.resources.editor_video_error_playback
import com.dot.gallery.editor.ui.generated.resources.editor_video_error_preview
import com.dot.gallery.editor.ui.generated.resources.editor_video_error_unsupported
import com.dot.gallery.editor.ui.generated.resources.editor_video_exit_focus
import com.dot.gallery.editor.ui.generated.resources.editor_video_export_video
import com.dot.gallery.editor.ui.generated.resources.editor_video_exporting
import com.dot.gallery.editor.ui.generated.resources.editor_video_focus_canvas
import com.dot.gallery.editor.ui.generated.resources.editor_video_saved
import com.dot.gallery.editor.ui.generated.resources.editor_video_tools_title
import com.dot.gallery.editor.ui.generated.resources.editor_export_cancelling
import com.dot.gallery.editor.ui.icons.Close
import com.dot.gallery.editor.ui.icons.EditorSymbols
import com.dot.gallery.editor.ui.toEditorImageBitmap
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

internal const val TAG_VIDEO_ERROR = "videoEditor.error"
internal const val TAG_VIDEO_ERROR_RETRY = "videoEditor.error.retry"
internal const val TAG_VIDEO_ERROR_DISMISS = "videoEditor.error.dismiss"
internal const val TAG_VIDEO_EXPORT_STRIP = "videoEditor.exportStrip"
internal const val TAG_VIDEO_EXPORT_CANCEL = "videoEditor.exportCancel"
internal const val TAG_VIDEO_EXPORT_OPEN = "videoEditor.exportOpen"
internal const val TAG_VIDEO_DISCARD_CONFIRM = "videoEditor.discard.confirm"
internal const val TAG_VIDEO_DISCARD_KEEP = "videoEditor.discard.keep"
internal const val TAG_VIDEO_EXPORT_DIALOG_CANCEL = "videoEditor.exportDialog.cancel"
internal const val TAG_VIDEO_EXPORT_DIALOG_KEEP = "videoEditor.exportDialog.keep"
internal const val TAG_VIDEO_PREVIEW = "videoEditor.preview"
internal const val TAG_VIDEO_BUSY = "videoEditor.busy"
internal const val TAG_VIDEO_TAB_TIMELINE = "videoEditor.tab.timeline"
internal const val TAG_VIDEO_TAB_TRIM = "videoEditor.tab.trim"
internal const val TAG_VIDEO_TAB_CLIPS = "videoEditor.tab.clips"
internal const val TAG_VIDEO_INSPECTOR = "videoEditor.inspector"
internal const val TAG_VIDEO_MEDIA = "videoEditor.media"
internal const val TAG_VIDEO_LAYERS = "videoEditor.layers"
internal const val TAG_VIDEO_AUDIO = "videoEditor.audio"
internal const val TAG_VIDEO_TOOLS = "videoEditor.tools"
internal const val TAG_VIDEO_PREVIEW_TIMECODE = "videoEditor.previewTimecode"
internal const val TAG_VIDEO_CONTEXT_BACK = "videoEditor.contextBack"
internal const val TAG_VIDEO_CATALOGUE_SHEET = "videoEditor.catalogueSheet"
internal const val TAG_VIDEO_MEDIA_SHEET = "videoEditor.mediaSheet"

/** Headroom the floating pill needs above the timeline's last lane. */
private val PILL_CLEARANCE = 84.dp

/**
 * Atomic (source reference, decoded bitmap) pair — the value records which
 * reference its pixels came from so a caller can refuse stale frames the
 * instant the source switches, before the producer has re-run.
 */
private class DecodedFrame(val reference: String?, val bitmap: ImageBitmap?)

/**
 * Incremental filmstrip bitmap conversion, scoped to one source reference
 * by `remember(reference)`: each arriving [VideoThumbnail] converts once
 * into a `clipId|sourceTime -> ImageBitmap` entry — the clip is part of
 * the key because two clips can share a source-time sample (a duplicated
 * clip, or the same source cut twice) and must never alias tiles.
 * Access-ordered so the bound evicts the least recently produced tile;
 * a new [generation] stamps each produce pass so a batch superseded
 * mid-conversion cannot write into the live map.
 */
private class TileBitmapHolder {
    var generation = 0L
    val bitmaps = LinkedHashMap<String, ImageBitmap>(32, 0.75f, true)
}

private const val MAX_TILE_BITMAPS = 32

internal fun videoTileKey(clipId: String, sourceTime: EditorTime) =
    "$clipId|${sourceTime.ticks}"

/**
 * Which presentation a window gets — the pure selection the layout and
 * its tests share. Compact is the V4 phone stack (portrait phones and
 * tablets), Landscape the rail + canvas + inspector arrangement for
 * landscape phones and small windows, Desktop the x-video-desktop pane
 * composition for tablets in landscape and desktop windows.
 */
internal enum class VideoLayout { Compact, Landscape, Desktop }

internal fun videoLayoutFor(widthDp: Float, heightDp: Float): VideoLayout {
    if (widthDp <= 0f || heightDp <= 0f || !widthDp.isFinite() ||
        !heightDp.isFinite()
    ) {
        return VideoLayout.Compact
    }
    return when (EditorLayoutMode.resolve(widthDp, heightDp)) {
        EditorLayoutMode.Compact -> VideoLayout.Compact
        EditorLayoutMode.Desktop -> VideoLayout.Desktop
        else -> VideoLayout.Landscape
    }
}

/**
 * The lanes a compact tool surface keeps above its inspector — audio
 * tools see the audio lanes, every other tool the lane holding the
 * selected clip (Main when nothing is selected). Layers and Add media
 * list the lanes themselves and show no strip.
 */
internal fun videoToolLaneFilter(
    state: VideoEditorState,
    tool: EditorToolId
): ((com.dot.gallery.editor.core.model.VideoTrack) -> Boolean)? {
    val sequence = state.sequence ?: return null
    if (videoToolEntries().firstOrNull { it.descriptor.id == tool }
            ?.descriptor?.group == EditorToolGroup.Audio
    ) {
        return { false }
    }
    val selectedTrack = state.selectedVideoClip()?.let { sequence.trackOf(it) }
    return { track ->
        if (selectedTrack != null) track.id == selectedTrack.id
        else track.kind == VideoTrackKind.Main
    }
}

/** Tools whose compact surface shows no lane strip above the inspector. */
internal fun videoToolShowsLaneStrip(tool: EditorToolId): Boolean =
    tool != EditorToolIds.VideoLayers && tool != EditorToolIds.VideoAddMedia &&
        tool != EditorToolIds.VideoMixer && tool != EditorToolIds.VideoVoice

/**
 * The video-editor workspace, driven entirely by [state] and [actions].
 * Phones get the V4 stack (header, utility pills, preview, transport,
 * lane timeline under the floating Media · Layers · Audio · Tools pill);
 * a tool replaces the pills with its title/check header, a lane strip
 * and its inspector. Landscape windows put a 48 dp rail beside the canvas
 * column and an inspector; tablets and desktops use the pane workspace.
 * Focus canvas is view state — it hides the secondary panes without
 * touching the sequence, the selection or the timeline viewport.
 *
 * Trim drafts are owned by the session ([VideoEditorState.isDrafting]) —
 * grips and the trim slider drive begin/update/commit/cancel, commands and
 * close cancel the draft first.
 */
@Composable
fun VideoEditorContent(
    state: VideoEditorState,
    actions: VideoEditorActions,
    modifier: Modifier = Modifier,
    onOpenExport: ((VideoExportResult) -> Unit)? = null,
    closeRequest: Int = 0
) {
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }
    var showCancelExportDialog by rememberSaveable { mutableStateOf(false) }
    var pendingCloseAfterExport by rememberSaveable { mutableStateOf(false) }
    var canvasFocused by rememberSaveable { mutableStateOf(false) }
    var moreOpen by rememberSaveable { mutableStateOf(false) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    val settingsStore = rememberEditorSettingsStore()
    var panel by rememberSaveable {
        mutableStateOf(VideoPanel.Timeline.name)
    }
    var activeToolId by rememberSaveable { mutableStateOf<String?>(null) }
    var mediaInsertion by rememberSaveable {
        mutableStateOf(VideoInsertion.AtPlayhead.name)
    }
    var exportSheetOpen by rememberSaveable { mutableStateOf(false) }
    var exportStarted by rememberSaveable { mutableStateOf(false) }
    var exportBaseline by rememberSaveable { mutableStateOf<String?>(null) }
    var exportWasRunning by remember { mutableStateOf(false) }
    val timelineViewport = rememberSaveable(
        saver = VideoTimelineViewport.Saver
    ) { VideoTimelineViewport() }

    // Pixel conversion off the UI thread, keyed on (reference, pixels) and
    // stamped with the reference the decode belongs to — a stale frame is
    // dropped at read time the moment the source changes, while a
    // same-reference decode keeps the previous frame until the new one
    // lands.
    val reference = state.info?.asset?.reference
    val decoded by produceState<DecodedFrame?>(
        null, reference, state.preview
    ) {
        value = DecodedFrame(
            reference,
            state.preview?.let { pixels ->
                withContext(Dispatchers.Default) { pixels.toEditorImageBitmap() }
            }
        )
    }
    val previewBitmap = decoded?.takeIf { it.reference == reference }?.bitmap

    // Clip-strip tiles convert once per (reference, clipId+sourceTime) —
    // a tile arrival republishes the list but only the *new* entries run
    // the pixel conversion, so arrival N never reconverts N old tiles.
    val tileHolder = remember(reference) { TileBitmapHolder() }
    val tileBitmaps by produceState<Map<String, ImageBitmap>>(
        emptyMap(), tileHolder, state.thumbnails
    ) {
        if (reference == null) {
            value = emptyMap()
            return@produceState
        }
        val generation = ++tileHolder.generation
        val missing = state.thumbnails.filter {
            !tileHolder.bitmaps.containsKey(
                videoTileKey(it.clipId, it.sourceTime)
            )
        }
        val decodedTiles = withContext(Dispatchers.Default) {
            missing.map {
                videoTileKey(it.clipId, it.sourceTime) to
                    it.pixels.toEditorImageBitmap()
            }
        }
        // A newer publish superseded this batch — its own pass fills in.
        if (generation != tileHolder.generation) return@produceState
        decodedTiles.forEach { (key, bitmap) ->
            tileHolder.bitmaps[key] = bitmap
            while (tileHolder.bitmaps.size > MAX_TILE_BITMAPS) {
                tileHolder.bitmaps.remove(tileHolder.bitmaps.keys.first())
            }
        }
        val wanted = state.thumbnails.mapTo(mutableSetOf()) {
            videoTileKey(it.clipId, it.sourceTime)
        }
        value = tileHolder.bitmaps
            .filterKeys { it in wanted }
    }

    val latestState by rememberUpdatedState(state)
    val latestActions by rememberUpdatedState(actions)
    val previewFocus = remember { FocusRequester() }
    val hazeState = remember { HazeState() }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        CompositionLocalProvider(LocalEditorHazeState provides hazeState) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val windowWidthDp = maxWidth.value
            val windowHeightDp = maxHeight.value
            val layout = videoLayoutFor(windowWidthDp, windowHeightDp)

            fun cancelDrafts() {
                if (latestState.isDrafting) {
                    latestActions.cancelTrim()
                    latestActions.cancelClipEdit()
                    latestActions.cancelAudioEdit()
                }
            }

            fun closeTool() {
                cancelDrafts()
                activeToolId = null
                panel = VideoPanel.Timeline.name
            }

            fun openExport() {
                if (!latestState.exporting) exportStarted = false
                exportSheetOpen = true
            }

            fun startExport() {
                exportBaseline = latestState.exported?.reference
                exportStarted = true
                exportSheetOpen = true
                latestActions.saveCopy()
            }

            // A cancelled run (no new receipt, no export error) returns
            // straight to the editor instead of re-offering the options.
            LaunchedEffect(state.exporting) {
                if (state.exporting) {
                    exportWasRunning = true
                } else if (exportWasRunning) {
                    exportWasRunning = false
                    val current = latestState
                    if (exportStarted && current.error != VideoErrorKind.Export &&
                        current.exported?.reference == exportBaseline
                    ) {
                        exportStarted = false
                        exportSheetOpen = false
                    }
                }
            }

            fun selectTool(id: EditorToolId) {
                panel = VideoPanel.Timeline.name
                if (id == EditorToolIds.VideoExport) {
                    openExport()
                } else {
                    cancelDrafts()
                    activeToolId = id.value
                }
            }

            fun requestClose() {
                val current = latestState
                when {
                    showDiscardDialog || showCancelExportDialog -> Unit
                    settingsOpen -> settingsOpen = false
                    exportSheetOpen -> exportSheetOpen = false
                    moreOpen -> moreOpen = false
                    canvasFocused -> canvasFocused = false
                    panel != VideoPanel.Timeline.name ->
                        panel = VideoPanel.Timeline.name
                    // A live draft (trim, clip or audio recipe) is
                    // cancelled before closing — each cancel only touches
                    // its own owner, so calling all of them covers any.
                    current.isDrafting -> cancelDrafts()
                    // Phones unwind an open tool to the timeline first.
                    layout != VideoLayout.Desktop && activeToolId != null ->
                        closeTool()
                    current.exporting -> showCancelExportDialog = true
                    current.isDirty -> showDiscardDialog = true
                    else -> latestActions.close()
                }
            }

            // Host-driven close (a desktop window's X, for instance)
            // funnels through the same focus/draft/export/dirty prompts as
            // Back.
            LaunchedEffect(closeRequest) {
                if (closeRequest != 0) requestClose()
            }

            // The export finished winding down after the user confirmed the
            // close — leave now.
            LaunchedEffect(pendingCloseAfterExport, state.exporting) {
                if (pendingCloseAfterExport && !state.exporting) {
                    pendingCloseAfterExport = false
                    latestActions.close()
                }
            }

            val exportEnabled = (state.sequence?.duration?.ticks ?: 0L) > 0L &&
                !state.exporting && !state.opening && !state.isDrafting
            val form = videoStudioFormFor(layout, windowWidthDp, windowHeightDp)
            val phoneSheets = form == VideoStudioForm.Phone
            val activeTool = activeToolId?.let(::EditorToolId)?.takeIf { id ->
                videoToolEntries().any { it.descriptor.id == id }
            }
            val sheetGroup = when (panel) {
                VideoPanel.Tools.name -> EditorToolGroup.Edit
                VideoPanel.Audio.name -> EditorToolGroup.Audio
                else -> null
            }
            // Wide layouts keep the timeline command row beside the
            // Arrange panel, so the panel's commands carry their own tags.
            val workspace = VideoWorkspaceContext(
                insertion = VideoInsertion.valueOf(mediaInsertion),
                onSelectInsertion = { mediaInsertion = it.name },
                openMedia = {
                    mediaInsertion = it.name
                    cancelDrafts()
                    panel = VideoPanel.Timeline.name
                    activeToolId = EditorToolIds.VideoAddMedia.value
                },
                viewport = null,
                arrangeCommands = form == VideoStudioForm.Phone ||
                    form == VideoStudioForm.PhoneLandscape,
                openAudioTask = { selectTool(it) },
                closeTool = ::closeTool
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) {
                            return@onPreviewKeyEvent false
                        }
                        val meta = event.isCtrlPressed || event.isMetaPressed
                        val shortcut = videoChordShortcut(
                            event.key, meta, event.isShiftPressed
                        ) ?: return@onPreviewKeyEvent false
                        runVideoShortcut(
                            shortcut, latestState, latestActions,
                            onExport = ::startExport,
                            onEscape = ::requestClose
                        )
                    }
                    .onKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) {
                            return@onKeyEvent false
                        }
                        val meta = event.isCtrlPressed || event.isMetaPressed
                        val shortcut = videoKeyShortcut(
                            event.key, meta, event.isShiftPressed
                        ) ?: return@onKeyEvent false
                        runVideoShortcut(
                            shortcut, latestState, latestActions,
                            onExport = ::startExport,
                            onEscape = ::requestClose
                        )
                    }
            ) {
                CompositionLocalProvider(LocalVideoWorkspace provides workspace) {
                    VideoStudioWorkspace(
                        VideoStudioArgs(
                            state = state,
                            actions = actions,
                            form = form,
                            activeTool = activeTool,
                            sheetGroup = sheetGroup,
                            moreOpen = moreOpen,
                            canvasFocused = canvasFocused,
                            previewBitmap = previewBitmap,
                            thumbnails = tileBitmaps,
                            viewport = timelineViewport,
                            previewFocus = previewFocus,
                            exportEnabled = exportEnabled,
                            onOpenExport = onOpenExport,
                            onClose = ::requestClose,
                            onExport = ::openExport,
                            onMore = { moreOpen = true },
                            onCloseTool = ::closeTool,
                            onSelectTool = ::selectTool,
                            onDock = { item ->
                                cancelDrafts()
                                canvasFocused = false
                                when (item) {
                                    VideoDockItem.Media -> {
                                        panel = VideoPanel.Timeline.name
                                        activeToolId = if (activeTool == EditorToolIds.VideoAddMedia) {
                                            null
                                        } else EditorToolIds.VideoAddMedia.value
                                    }
                                    VideoDockItem.Layers -> {
                                        panel = VideoPanel.Timeline.name
                                        activeToolId = if (activeTool == EditorToolIds.VideoLayers) {
                                            null
                                        } else EditorToolIds.VideoLayers.value
                                    }
                                    VideoDockItem.Audio -> panel = VideoPanel.Audio.name
                                    VideoDockItem.Tools -> panel = VideoPanel.Tools.name
                                }
                            },
                            onToggleFocus = { canvasFocused = !canvasFocused },
                            onAddMedia = {
                                cancelDrafts()
                                activeToolId = EditorToolIds.VideoAddMedia.value
                            }
                        )
                    )
                }
            }

            if (sheetGroup != null) {
                VideoCatalogueSheet(
                    state = state,
                    phone = phoneSheets,
                    initialGroup = sheetGroup,
                    activeTool = activeTool,
                    onSelect = ::selectTool,
                    onDismiss = { panel = VideoPanel.Timeline.name }
                )
            }
            if (moreOpen) {
                VideoMoreSheet(
                    phone = phoneSheets,
                    canvasFocused = canvasFocused,
                    onToggleFocus = {
                        moreOpen = false
                        canvasFocused = !canvasFocused
                    },
                    onAllTools = {
                        moreOpen = false
                        panel = VideoPanel.Tools.name
                    },
                    onMixer = {
                        moreOpen = false
                        selectTool(EditorToolIds.VideoMixer)
                    },
                    onDismiss = { moreOpen = false },
                    onSettings = {
                        moreOpen = false
                        settingsOpen = true
                    }
                )
            }
            if (settingsOpen) {
                VideoEditorSettingsPage(
                    store = settingsStore,
                    state = state,
                    actions = actions,
                    onBack = { settingsOpen = false }
                )
            }
            if (exportSheetOpen) {
                val stage = videoExportStage(state, exportStarted, exportBaseline)
                VideoExportSheet(
                    compact = layout == VideoLayout.Compact,
                    onDismiss = { exportSheetOpen = false }
                ) {
                    VideoExportBody(
                        state = state,
                        actions = actions,
                        stage = stage,
                        onExport = ::startExport,
                        onHide = { exportSheetOpen = false },
                        onOpenExport = onOpenExport
                    )
                }
            }

            if (showDiscardDialog) {
                AlertDialog(
                    onDismissRequest = { showDiscardDialog = false },
                    title = {
                        Text(stringResource(Res.string.editor_discard_title))
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showDiscardDialog = false
                                actions.close()
                            },
                            modifier =
                                Modifier.testTag(TAG_VIDEO_DISCARD_CONFIRM)
                        ) {
                            Text(
                                stringResource(Res.string.editor_discard_confirm)
                            )
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showDiscardDialog = false },
                            modifier = Modifier.testTag(TAG_VIDEO_DISCARD_KEEP)
                        ) {
                            Text(stringResource(Res.string.editor_keep_editing))
                        }
                    }
                )
            }
            if (showCancelExportDialog) {
                AlertDialog(
                    onDismissRequest = { showCancelExportDialog = false },
                    title = {
                        Text(
                            stringResource(Res.string.editor_cancel_export_title)
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showCancelExportDialog = false
                                pendingCloseAfterExport = true
                                actions.cancelExport()
                            },
                            modifier = Modifier.testTag(
                                TAG_VIDEO_EXPORT_DIALOG_CANCEL
                            )
                        ) {
                            Text(
                                stringResource(
                                    Res.string.editor_cancel_export_confirm
                                )
                            )
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showCancelExportDialog = false },
                            modifier = Modifier.testTag(
                                TAG_VIDEO_EXPORT_DIALOG_KEEP
                            )
                        ) {
                            Text(stringResource(Res.string.editor_keep_editing))
                        }
                    }
                )
            }

            // A blocked project restore owns the whole surface — the
            // snapshot stays pending behind it, never silently flattened.
            val recovery = state.recovery
            if (recovery != null) {
                EditorRecoverySurface(
                    missingReferences = recovery.missingReferences,
                    onRetry = actions.retryRecovery,
                    onDiscard = actions.discardRecovery,
                    onRelink = actions.requestRelink
                )
            }

            NavigationEventHandler(
                state = rememberNavigationEventState(NavigationEventInfo.None),
                isForwardEnabled = false,
                onBackCompleted = { requestClose() }
            )
        }
        }
    }
}

/**
 * The decoded preview surface: the latest frame fitted inside the canvas
 * colour, a small busy indicator (never a blocking scrim) while opening,
 * seeking or buffering, and Space/arrow keys when focused. Compact paints
 * the Figma V2 phone stage — a full-bleed crop plus the 86 x 26 dp
 * current-time badge at top 10/end 16.
 */
@Composable
internal fun VideoPreview(
    state: VideoEditorState,
    actions: VideoEditorActions,
    bitmap: ImageBitmap?,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val description = stringResource(
        Res.string.editor_video_canvas,
        state.info?.displayName.orEmpty()
    )
    // The preview takes focus once per source so Space/arrows work
    // immediately — not on every recomposition.
    LaunchedEffect(state.info?.asset?.reference) {
        if (state.info != null) {
            try {
                focusRequester.requestFocus()
            } catch (_: IllegalStateException) {
                // Not yet attached to the focus system — a later tap works.
            }
        }
    }
    Box(
        modifier = modifier
            .background(LocalEditorColors.current.canvas)
            .focusRequester(focusRequester)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val sequence = state.sequence ?: return@onKeyEvent false
                val ticksPerFrame = sequence.frameRate.ticksPerFrame
                fun step(delta: Long) {
                    actions.seek(
                        EditorTime(state.position.ticks + delta)
                            .coerceIn(EditorTime.Zero, sequence.duration)
                    )
                }
                when (event.key) {
                    Key.Spacebar -> {
                        if (state.playing) actions.pause() else actions.play()
                        true
                    }
                    Key.DirectionLeft -> { step(-ticksPerFrame); true }
                    Key.DirectionRight -> { step(ticksPerFrame); true }
                    else -> false
                }
            }
            .focusable()
            .testTag(TAG_VIDEO_PREVIEW),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = description,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
        if (state.opening || state.seeking || state.buffering) {
            Column(
                modifier = Modifier
                    .align(
                        if (compact) Alignment.TopStart
                        else Alignment.TopEnd
                    )
                    .padding(12.dp)
                    .testTag(TAG_VIDEO_BUSY),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
                if (state.buffering) {
                    Text(
                        text = stringResource(
                            Res.string.editor_video_buffering
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** The compact preview's 86 x 26 dp current-time pill. */
@Composable
internal fun VideoTimecodeBadge(position: EditorTime, modifier: Modifier) {
    Box(
        modifier = modifier
            .size(width = 86.dp, height = 26.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(
                MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.9f)
            )
            .testTag(TAG_VIDEO_PREVIEW_TIMECODE),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = formatVideoTimecode(position),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 12.sp,
                fontFeatureSettings = "tnum"
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}

/** Export progress/result and error banners between the header and preview. */
@Composable
internal fun VideoStatusStrips(
    state: VideoEditorState,
    actions: VideoEditorActions,
    onOpenExport: ((VideoExportResult) -> Unit)?,
    modifier: Modifier = Modifier
) {
    if (state.exporting) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .testTag(TAG_VIDEO_EXPORT_STRIP),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        if (state.exportCancelling) {
                            Res.string.editor_export_cancelling
                        } else {
                            Res.string.editor_video_exporting
                        }
                    ),
                    style = MaterialTheme.typography.labelLarge
                )
                LinearProgressIndicator(
                    progress = { state.exportProgress ?: 0f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                )
            }
            TextButton(
                onClick = actions.cancelExport,
                enabled = !state.exportCancelling,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag(TAG_VIDEO_EXPORT_CANCEL)
            ) {
                Text(stringResource(Res.string.editor_cancel_export))
            }
        }
    } else if (state.exported != null) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(Res.string.editor_video_saved),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 8.dp)
            )
            val result = state.exported
            if (onOpenExport != null && result != null) {
                TextButton(
                    onClick = { onOpenExport(result) },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag(TAG_VIDEO_EXPORT_OPEN)
                ) {
                    Text(stringResource(Res.string.editor_open_export))
                }
            }
        }
    }
    val errorKind = state.error
    if (errorKind != null) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .testTag(TAG_VIDEO_ERROR),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Text(
                text = videoErrorMessage(errorKind),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
            )
            when (errorKind) {
                // Capability failures can't be retried into success —
                // dismiss+close is the whole affordance, with the generic
                // message (never a native path or decoder log).
                VideoErrorKind.Unsupported -> TextButton(
                    onClick = {
                        actions.clearError()
                        actions.close()
                    },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag(TAG_VIDEO_ERROR_DISMISS)
                ) {
                    Text(stringResource(Res.string.editor_dismiss))
                }
                else -> {
                    TextButton(
                        onClick = {
                            when (errorKind) {
                                VideoErrorKind.Preview ->
                                    actions.seek(state.position)
                                VideoErrorKind.Playback -> actions.play()
                                VideoErrorKind.Export -> actions.saveCopy()
                                else -> actions.retryOpen()
                            }
                        },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag(TAG_VIDEO_ERROR_RETRY)
                    ) {
                        Text(stringResource(Res.string.editor_retry))
                    }
                    TextButton(
                        onClick = actions.clearError,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag(TAG_VIDEO_ERROR_DISMISS)
                    ) {
                        Text(stringResource(Res.string.editor_dismiss))
                    }
                }
            }
        }
    }
}

@Composable
private fun videoErrorMessage(kind: VideoErrorKind): String = when (kind) {
    VideoErrorKind.Preview ->
        stringResource(Res.string.editor_video_error_preview)
    VideoErrorKind.Playback ->
        stringResource(Res.string.editor_video_error_playback)
    VideoErrorKind.Export ->
        stringResource(Res.string.editor_video_error_export)
    VideoErrorKind.Unsupported ->
        stringResource(Res.string.editor_video_error_unsupported)
    else -> stringResource(Res.string.editor_video_error_open)
}
