package com.dot.gallery.editor.ui.video

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.dot.gallery.editor.core.model.ColorAdjustments
import com.dot.gallery.editor.core.model.PhotoBlendMode
import com.dot.gallery.editor.core.model.VideoClip
import com.dot.gallery.editor.core.model.VideoContentMode
import com.dot.gallery.editor.core.model.VideoLayerTransform
import com.dot.gallery.editor.core.model.VideoSequence
import com.dot.gallery.editor.core.model.VideoTrack
import com.dot.gallery.editor.core.model.VideoTrackKind
import com.dot.gallery.editor.core.model.contrastAmount
import com.dot.gallery.editor.core.model.contrastFactor
import com.dot.gallery.editor.render.video.VideoClipVisuals
import com.dot.gallery.editor.render.video.VideoEditorState
import com.dot.gallery.editor.render.video.VideoInsertion
import com.dot.gallery.editor.ui.EditorRulerSlab
import com.dot.gallery.editor.ui.NumericEntryDialog
import com.dot.gallery.editor.ui.blendModeTitle
import com.dot.gallery.editor.ui.formatDegrees
import com.dot.gallery.editor.ui.formatSignedCompact
import com.dot.gallery.editor.ui.generated.resources.Res
import com.dot.gallery.editor.ui.generated.resources.editor_contrast
import com.dot.gallery.editor.ui.generated.resources.editor_double_blend
import com.dot.gallery.editor.ui.generated.resources.editor_exposure
import com.dot.gallery.editor.ui.generated.resources.editor_flip
import com.dot.gallery.editor.ui.generated.resources.editor_highlights
import com.dot.gallery.editor.ui.generated.resources.editor_layer_opacity
import com.dot.gallery.editor.ui.generated.resources.editor_param_summary
import com.dot.gallery.editor.ui.generated.resources.editor_precision_ruler
import com.dot.gallery.editor.ui.generated.resources.editor_saturation
import com.dot.gallery.editor.ui.generated.resources.editor_shadows
import com.dot.gallery.editor.ui.generated.resources.editor_transform_rotation
import com.dot.gallery.editor.ui.generated.resources.editor_transform_scale
import com.dot.gallery.editor.ui.generated.resources.editor_transform_x
import com.dot.gallery.editor.ui.generated.resources.editor_transform_y
import com.dot.gallery.editor.ui.generated.resources.editor_video_add_overlay
import com.dot.gallery.editor.ui.generated.resources.editor_video_cancel_import
import com.dot.gallery.editor.ui.generated.resources.editor_video_center
import com.dot.gallery.editor.ui.generated.resources.editor_video_choose_videos
import com.dot.gallery.editor.ui.generated.resources.editor_video_colour_colour
import com.dot.gallery.editor.ui.generated.resources.editor_video_colour_light
import com.dot.gallery.editor.ui.generated.resources.editor_video_crop
import com.dot.gallery.editor.ui.generated.resources.editor_video_delete
import com.dot.gallery.editor.ui.generated.resources.editor_video_duplicate
import com.dot.gallery.editor.ui.generated.resources.editor_video_earlier
import com.dot.gallery.editor.ui.generated.resources.editor_video_fill
import com.dot.gallery.editor.ui.generated.resources.editor_video_fit
import com.dot.gallery.editor.ui.generated.resources.editor_video_hide_layer
import com.dot.gallery.editor.ui.generated.resources.editor_video_importing
import com.dot.gallery.editor.ui.generated.resources.editor_video_insert_end
import com.dot.gallery.editor.ui.generated.resources.editor_video_insert_end_sub
import com.dot.gallery.editor.ui.generated.resources.editor_video_insert_overlay
import com.dot.gallery.editor.ui.generated.resources.editor_video_insert_overlay_sub
import com.dot.gallery.editor.ui.generated.resources.editor_video_insert_playhead
import com.dot.gallery.editor.ui.generated.resources.editor_video_insert_playhead_sub
import com.dot.gallery.editor.ui.generated.resources.editor_video_later
import com.dot.gallery.editor.ui.generated.resources.editor_video_layer_main_meta
import com.dot.gallery.editor.ui.generated.resources.editor_video_layer_overlay_meta
import com.dot.gallery.editor.ui.generated.resources.editor_video_limit_down
import com.dot.gallery.editor.ui.generated.resources.editor_video_limit_left
import com.dot.gallery.editor.ui.generated.resources.editor_video_limit_right
import com.dot.gallery.editor.ui.generated.resources.editor_video_limit_up
import com.dot.gallery.editor.ui.generated.resources.editor_video_link_av
import com.dot.gallery.editor.ui.generated.resources.editor_video_lock_layer
import com.dot.gallery.editor.ui.generated.resources.editor_video_no_clip
import com.dot.gallery.editor.ui.generated.resources.editor_video_ripple_editing
import com.dot.gallery.editor.ui.generated.resources.editor_video_show_layer
import com.dot.gallery.editor.ui.generated.resources.editor_video_split
import com.dot.gallery.editor.ui.generated.resources.editor_video_unlock_layer
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

internal const val TAG_VIDEO_INSERT_PREFIX = "videoEditor.insert."
internal const val TAG_VIDEO_CHOOSE_VIDEOS = "videoEditor.chooseVideos"
internal const val TAG_VIDEO_IMPORT_STRIP = "videoEditor.importStrip"
internal const val TAG_VIDEO_IMPORT_CANCEL = "videoEditor.importCancel"
internal const val TAG_VIDEO_ARRANGE_DUPLICATE = "videoEditor.arrange.duplicate"
internal const val TAG_VIDEO_ARRANGE_RIPPLE = "videoEditor.arrange.ripple"
internal const val TAG_VIDEO_ARRANGE_LINK = "videoEditor.arrange.link"
internal const val TAG_VIDEO_TRANSFORM_MODE_PREFIX = "videoEditor.transform.mode."
internal const val TAG_VIDEO_TRANSFORM_XY = "videoEditor.transform.xy"
internal const val TAG_VIDEO_TRANSFORM_SR = "videoEditor.transform.scaleRotation"
internal const val TAG_VIDEO_TRANSFORM_LOCK = "videoEditor.transform.lock"
internal const val TAG_VIDEO_TRANSFORM_ALIGN = "videoEditor.transform.align"
internal const val TAG_VIDEO_TRANSFORM_FLIP = "videoEditor.transform.flip"
internal const val TAG_VIDEO_TRANSFORM_OPACITY = "videoEditor.transform.opacity"
internal const val TAG_VIDEO_TRANSFORM_BLEND = "videoEditor.transform.blend"
internal const val TAG_VIDEO_TRANSFORM_PARAM_PREFIX = "videoEditor.transform.param."
internal const val TAG_VIDEO_TRANSFORM_VALUE = "videoEditor.transform.value"
internal const val TAG_VIDEO_COLOUR_TAB_PREFIX = "videoEditor.colour.tab."
internal const val TAG_VIDEO_COLOUR_PARAM_PREFIX = "videoEditor.colour.param."
internal const val TAG_VIDEO_COLOUR_VALUE_PREFIX = "videoEditor.colour.value."
internal const val TAG_VIDEO_COLOUR_SLIDER_PREFIX = "videoEditor.colour.slider."
internal const val TAG_VIDEO_LAYER_ROW_PREFIX = "videoEditor.layerRow."
internal const val TAG_VIDEO_TRACK_VISIBLE_PREFIX = "videoEditor.trackVisible."
internal const val TAG_VIDEO_TRACK_LOCK_PREFIX = "videoEditor.trackLock."
internal const val TAG_VIDEO_LAYERS_ADD_OVERLAY = "videoEditor.layers.addOverlay"

/**
 * From this panel content width, in a tablet-sized window, parameters show
 * as rulers without chips. The window check keeps a 412 dp phone, whose
 * panel also reaches this width, on the chips.
 */
internal val VIDEO_PANEL_WIDE = 360.dp

/** The narrowest window that counts as a tablet for the panel layouts. */
private val VIDEO_TABLET_WINDOW = 600.dp

/** From this panel content width, those rulers sit in two columns. */
internal val VIDEO_PANEL_TWO_COLUMN = 600.dp

// ---------------------------------------------------------------------------
// Shared lookups
// ---------------------------------------------------------------------------

internal fun VideoEditorState.selectedVideoClip(): VideoClip? {
    val seq = sequence ?: return null
    val id = selectedClipId ?: return null
    return seq.tracks.asSequence().flatMap { it.clips.asSequence() }
        .firstOrNull { it.id == id }
}

internal fun VideoSequence.trackOf(clip: VideoClip): VideoTrack? =
    tracks.firstOrNull { track -> track.clips.any { it.id == clip.id } }

internal fun VideoEditorState.clipDisplayName(clip: VideoClip): String =
    sourceDisplayName(clip.sourceId)

/**
 * A source's user-facing name — its probed display name, else the opened
 * file's name for the primary source (whose facts live in [info]), and
 * only then the internal id.
 */
internal fun VideoEditorState.sourceDisplayName(sourceId: String): String =
    sourceInfos[sourceId]?.displayName
        ?: info?.displayName?.takeIf {
            sequence?.sources?.firstOrNull()?.id == sourceId
        }
        ?: sourceId

private fun VideoClipVisuals.withTransform(
    edit: VideoLayerTransform.() -> VideoLayerTransform
) = copy(transform = (transform ?: VideoLayerTransform()).edit())

internal fun VideoClip.visuals() = VideoClipVisuals(
    transform = transform,
    contentMode = contentMode,
    opacity = opacity,
    blendMode = blendMode,
    adjustments = adjustments
)

/**
 * The lanes the timeline renders, top to bottom: Title tracks that
 * actually hold clips, then overlays in track order, then Main. Audio
 * lanes stay absent until A1 — the model carries no audio tracks yet.
 */
internal fun VideoSequence.displayLanes(): List<VideoTrack> {
    val titles = tracks.filter {
        it.kind == VideoTrackKind.Title && it.clips.isNotEmpty()
    }
    val overlays = tracks.filter { it.kind == VideoTrackKind.Overlay }
    val mains = tracks.filter { it.kind == VideoTrackKind.Main }
    return titles + overlays + mains
}

/** The lane label — Main is always V1, overlays V2 onward in order. */
internal fun VideoSequence.laneLabel(track: VideoTrack): String =
    when (track.kind) {
        VideoTrackKind.Main -> "V1"
        VideoTrackKind.Overlay -> "V${2 + tracks.count {
            it.kind == VideoTrackKind.Overlay && tracks.indexOf(it) <
                tracks.indexOf(track)
        }}"
        VideoTrackKind.Title -> "T${1 + tracks.count {
            it.kind == VideoTrackKind.Title && tracks.indexOf(it) <
                tracks.indexOf(track)
        }}"
        VideoTrackKind.Audio -> "A${1 + tracks.count {
            it.kind == VideoTrackKind.Audio && tracks.indexOf(it) <
                tracks.indexOf(track)
        }}"
    }

// ---------------------------------------------------------------------------
// Shared panel parts
// ---------------------------------------------------------------------------

/**
 * The surface's own side inset where nothing else provides one: the tool
 * panel pads its content, the desktop inspector does not.
 */
@Composable
internal fun Modifier.videoSurfaceInset(): Modifier =
    if (LocalVideoPanelReset.current == null) padding(horizontal = 16.dp) else this

/** Whether the window is tablet-sized rather than a phone in either hold. */
@Composable
internal fun videoTabletWindow(): Boolean {
    val windowWidth = LocalWindowInfo.current.containerSize.width
    return with(LocalDensity.current) { windowWidth.toDp() } >= VIDEO_TABLET_WINDOW
}

/** One parameter a [VideoKeyedParams] group edits through the draft seam. */
internal class VideoParam(
    val key: String,
    val label: String,
    val value: Float,
    val display: String,
    val range: ClosedFloatingPointRange<Float>,
    val step: Float,
    val limitStart: String,
    val limitEnd: String,
    val sliderTag: String,
    val onBegin: () -> Unit,
    val onUpdate: (Float) -> Unit,
    val onCommit: () -> Unit,
    val onCancel: () -> Unit,
    val onApply: (Float) -> Unit,
    val icon: ImageVector? = null,
    val valueTag: String = "$sliderTag.value",
    val chipTag: String = "$sliderTag.chip",
    val default: Float = 0f,
    val enabled: Boolean = true
)

/**
 * The keyed parameter block. A narrow panel shows value chips over one
 * ruler for the chosen parameter; a tablet panel at least [VIDEO_PANEL_WIDE]
 * wide shows one ruler per parameter under a name/value header, in two columns
 * from [VIDEO_PANEL_TWO_COLUMN]. The ruler's readout opens exact entry,
 * which lands as one apply.
 */
@Composable
internal fun VideoKeyedParams(
    params: List<VideoParam>,
    identity: String,
    interactionActive: Boolean,
    modifier: Modifier = Modifier
) {
    if (params.isEmpty()) return
    var entryKey by remember(identity) { mutableStateOf<String?>(null) }
    val tablet = videoTabletWindow()
    BoxWithConstraints(modifier.fillMaxWidth()) {
        if (tablet && maxWidth >= VIDEO_PANEL_WIDE) {
            val columns = if (maxWidth >= VIDEO_PANEL_TWO_COLUMN) 2 else 1
            val single = params.size == 1 && columns == 2
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                params.chunked(columns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { param ->
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (!single) VideoParamHeader(param)
                                VideoParamRuler(
                                    param, identity, interactionActive
                                ) { entryKey = param.key }
                            }
                        }
                        if (!single) {
                            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        } else {
            var activeKey by rememberSaveable(identity) { mutableStateOf(params.first().key) }
            val active = params.firstOrNull { it.key == activeKey } ?: params.first()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (params.size > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        params.forEach { param ->
                            VideoValueChip(
                                label = param.label,
                                value = param.display,
                                selected = param.key == active.key,
                                icon = param.icon,
                                enabled = param.enabled,
                                tag = param.chipTag,
                                onClick = {
                                    if (param.key != active.key) {
                                        // A parameter switch drops an
                                        // unfinished gesture on the old one.
                                        if (interactionActive) active.onCancel()
                                        activeKey = param.key
                                    }
                                }
                            )
                        }
                    }
                }
                VideoParamRuler(active, identity, interactionActive) { entryKey = active.key }
            }
        }
    }
    val entry = params.firstOrNull { it.key == entryKey }
    if (entry != null) {
        NumericEntryDialog(
            label = entry.label,
            initialValue = entry.value,
            range = entry.range,
            onConfirm = { entered ->
                entry.onApply(entered)
                entryKey = null
            },
            onDismiss = { entryKey = null }
        )
    }
}

@Composable
private fun VideoParamHeader(param: VideoParam) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = param.label,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = param.display,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun VideoParamRuler(
    param: VideoParam,
    identity: String,
    interactionActive: Boolean,
    onOpenEntry: () -> Unit
) {
    EditorRulerSlab(
        label = param.label,
        value = param.value.coerceIn(param.range.start, param.range.endInclusive),
        display = param.display,
        accent = param.value != param.default || interactionActive,
        range = param.range,
        step = param.step,
        summary = stringResource(
            Res.string.editor_param_summary, param.label, param.display
        ),
        description = stringResource(
            Res.string.editor_precision_ruler, param.label, param.display
        ),
        enabled = param.enabled,
        interactionActive = interactionActive,
        identity = identity to param.key,
        sliderTag = param.sliderTag,
        valueTag = param.valueTag,
        stepKey = param.key,
        limitStartLabel = param.limitStart,
        limitEndLabel = param.limitEnd,
        onOpenEntry = onOpenEntry,
        onBeginInteraction = param.onBegin,
        onUpdateInteraction = param.onUpdate,
        onCommitInteraction = param.onCommit,
        onCancelInteraction = param.onCancel,
        onApplyStep = param.onApply
    )
}

/**
 * Tonal actions in an auto-fit grid: as many 92 dp-minimum columns as the
 * panel holds, with a short final row left-aligned.
 */
@Composable
internal fun VideoActionGrid(
    count: Int,
    modifier: Modifier = Modifier,
    item: @Composable (index: Int, modifier: Modifier) -> Unit
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val columns = ((maxWidth + VideoStudio.GroupGap) / (92.dp + VideoStudio.GroupGap))
            .toInt().coerceIn(1, count)
        Column(verticalArrangement = Arrangement.spacedBy(VideoStudio.GroupGap)) {
            (0 until count).chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(VideoStudio.GroupGap)) {
                    row.forEach { index -> item(index, Modifier.weight(1f)) }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Add media — the insertion choice the host picker resolves through.
// ---------------------------------------------------------------------------

/**
 * The Add-media panel: three option rows pick where the import lands, then
 * the primary action asks the host for videos. While a probe or decode
 * runs the action swaps for an explicit progress and cancel strip — an
 * import is work, not a dead button.
 */
@Composable
internal fun VideoAddMediaSurface(
    state: VideoEditorState,
    actions: VideoEditorActions,
    insertion: VideoInsertion,
    onInsertionChange: (VideoInsertion) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.videoSurfaceInset(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(VideoInsertion.End, VideoInsertion.AtPlayhead, VideoInsertion.Overlay).forEach { option ->
            VideoOptionRow(
                title = stringResource(
                    when (option) {
                        VideoInsertion.AtPlayhead -> Res.string.editor_video_insert_playhead
                        VideoInsertion.End -> Res.string.editor_video_insert_end
                        VideoInsertion.Overlay -> Res.string.editor_video_insert_overlay
                    }
                ),
                supporting = stringResource(
                    when (option) {
                        VideoInsertion.AtPlayhead -> Res.string.editor_video_insert_playhead_sub
                        VideoInsertion.End -> Res.string.editor_video_insert_end_sub
                        VideoInsertion.Overlay -> Res.string.editor_video_insert_overlay_sub
                    }
                ),
                icon = when (option) {
                    VideoInsertion.AtPlayhead -> VideoGlyphs.InsertPlayhead
                    VideoInsertion.End -> VideoGlyphs.AppendEnd
                    VideoInsertion.Overlay -> VideoGlyphs.Overlay
                },
                selected = insertion == option,
                role = Role.RadioButton,
                onClick = { onInsertionChange(option) },
                rowModifier = Modifier.testTag(TAG_VIDEO_INSERT_PREFIX + option.name)
            )
        }
        if (state.importing) {
            VideoImportStrip(actions)
        } else {
            VideoPrimaryAction(
                label = stringResource(Res.string.editor_video_choose_videos),
                icon = VideoGlyphs.Add,
                onClick = { actions.requestAddMedia(insertion) },
                enabled = actions.mediaPickerAvailable &&
                    state.sequence != null && !state.isDrafting &&
                    !state.opening,
                modifier = Modifier.testTag(TAG_VIDEO_CHOOSE_VIDEOS)
            )
        }
    }
}

/** The running-import strip shared by Add media and Add audio. */
@Composable
internal fun VideoImportStrip(actions: VideoEditorActions, modifier: Modifier = Modifier) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag(TAG_VIDEO_IMPORT_STRIP)
    ) {
        Text(
            text = stringResource(Res.string.editor_video_importing),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LinearProgressIndicator(Modifier.fillMaxWidth())
        VideoTonalAction(
            label = stringResource(Res.string.editor_video_cancel_import),
            onClick = actions.cancelImport,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TAG_VIDEO_IMPORT_CANCEL)
        )
    }
}

// ---------------------------------------------------------------------------
// Arrange — clip commands plus the insert-behaviour preference.
// ---------------------------------------------------------------------------

/**
 * The Arrange panel: the Ripple preference and the embedded-sound link as
 * switches, then the clip commands as an auto-fit tonal grid. [showCommands]
 * is false where a timeline command row already owns the canonical command
 * tags, so the grid's own tags take an `.arrange` suffix there.
 */
@Composable
internal fun VideoArrangeSurface(
    state: VideoEditorState,
    actions: VideoEditorActions,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") viewport: VideoTimelineViewport? = null,
    showCommands: Boolean = true
) {
    val sequence = state.sequence
    val clip = state.selectedVideoClip()
    val editable = sequence != null && !state.isDrafting && !state.opening
    val track = clip?.let { sequence?.trackOf(it) }
    val clipIndex = track?.clips?.indexOfFirst { it.id == clip.id } ?: -1
    val commandTag: (String) -> String = { tag -> if (showCommands) tag else "$tag.arrange" }
    Column(
        modifier.videoSurfaceInset(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Ripple is a preference, not an edit — the row speaks
        // setRippleEnabled directly and never opens a draft.
        VideoStudioSwitchRow(
            label = stringResource(Res.string.editor_video_ripple_editing),
            checked = state.rippleEnabled,
            onCheckedChange = { actions.setRippleEnabled(it) },
            enabled = editable,
            tag = TAG_VIDEO_ARRANGE_RIPPLE
        )
        // Link video & audio — off extracts the clip's embedded sound onto
        // its own lane (the sound survives, independently); back on
        // re-mutes the lane-independent embedded input. The extraction is
        // the session's real import path, so the row goes quiet while an
        // import runs. With no clip selected it reports linked — the
        // default for every placed clip.
        VideoStudioSwitchRow(
            label = stringResource(Res.string.editor_video_link_av),
            checked = clip?.let { !it.audioMuted } ?: true,
            onCheckedChange = { linked ->
                if (clip != null && track != null) {
                    if (linked) {
                        actions.setAudioLink(track.id, clip.id, true)
                    } else {
                        actions.extractAudio()
                    }
                }
            },
            enabled = editable && clip != null && track != null &&
                !state.importing,
            tag = TAG_VIDEO_ARRANGE_LINK
        )
        if (clip == null) {
            Text(
                text = stringResource(Res.string.editor_video_no_clip),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        } else {
            val canEarlier = editable && clipIndex > 0
            val canLater = editable && track != null && clipIndex in 0 until track.clips.lastIndex
            VideoActionGrid(count = 5) { index, cell ->
                when (index) {
                    0 -> VideoTonalAction(
                        label = stringResource(Res.string.editor_video_split),
                        icon = VideoGlyphs.Split,
                        onClick = actions.split,
                        enabled = editable,
                        modifier = cell.testTag(commandTag(TAG_VIDEO_SPLIT))
                    )
                    1 -> VideoTonalAction(
                        label = stringResource(Res.string.editor_video_duplicate),
                        icon = VideoGlyphs.Duplicate,
                        onClick = actions.duplicate,
                        enabled = editable,
                        modifier = cell.testTag(TAG_VIDEO_ARRANGE_DUPLICATE)
                    )
                    2 -> VideoTonalAction(
                        label = stringResource(Res.string.editor_video_delete),
                        icon = VideoGlyphs.Delete,
                        danger = true,
                        onClick = actions.delete,
                        enabled = editable,
                        modifier = cell.testTag(commandTag(TAG_VIDEO_DELETE))
                    )
                    3 -> VideoTonalAction(
                        label = stringResource(Res.string.editor_video_earlier),
                        icon = VideoGlyphs.Left,
                        onClick = { actions.moveSelected(-1) },
                        enabled = canEarlier,
                        modifier = cell.testTag(commandTag(TAG_VIDEO_MOVE_EARLIER))
                    )
                    else -> VideoTonalAction(
                        label = stringResource(Res.string.editor_video_later),
                        icon = VideoGlyphs.Right,
                        onClick = { actions.moveSelected(1) },
                        enabled = canLater,
                        modifier = cell.testTag(commandTag(TAG_VIDEO_MOVE_LATER))
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Transform & crop — the selected clip's visual recipe.
// ---------------------------------------------------------------------------

/**
 * The Transform & crop panel: Fit/Fill/Crop writes `VideoClip.contentMode`,
 * the keyed block drives scale, rotation, X, Y (and opacity on overlay
 * clips) through the clip draft seam so one drag is one undo step, then
 * Centre and Flip. Blend is a real picker on overlays. Reset returns the
 * clip's placement to its defaults and leaves its colour alone.
 */
@Composable
internal fun VideoTransformSurface(
    state: VideoEditorState,
    actions: VideoEditorActions,
    modifier: Modifier = Modifier
) {
    val sequence = state.sequence ?: return
    val clip = state.selectedVideoClip() ?: return
    val track = sequence.trackOf(clip)
    val transform = clip.transform
    val enabled = !state.isDrafting && !state.opening
    val overlay = track?.kind == VideoTrackKind.Overlay
    val latestActions by rememberUpdatedState(actions)
    var blendMenu by remember { mutableStateOf(false) }

    fun writeVisuals(update: (VideoClipVisuals) -> VideoClipVisuals) {
        latestActions.applyClipVisuals("Edit clip", update(clip.visuals()))
    }

    val neutralTransform = VideoLayerTransform()
    val placementChanged = clip.contentMode != VideoContentMode.Fit ||
        transform != neutralTransform || clip.opacity != 1f ||
        clip.blendMode != PhotoBlendMode.Normal
    PublishVideoPanelReset(enabled = enabled && placementChanged) {
        writeVisuals {
            it.copy(
                contentMode = VideoContentMode.Fit,
                transform = neutralTransform,
                opacity = 1f,
                blendMode = PhotoBlendMode.Normal
            )
        }
    }

    fun param(
        key: String,
        label: String,
        icon: ImageVector,
        value: Float,
        display: String,
        range: ClosedFloatingPointRange<Float>,
        limitStart: String,
        limitEnd: String,
        tag: String,
        default: Float,
        write: (VideoClipVisuals, Float) -> VideoClipVisuals
    ) = VideoParam(
        key = key, label = label, icon = icon, value = value, display = display,
        range = range, step = 1f, limitStart = limitStart, limitEnd = limitEnd,
        sliderTag = tag,
        valueTag = TAG_VIDEO_TRANSFORM_VALUE + "." + key,
        chipTag = TAG_VIDEO_TRANSFORM_PARAM_PREFIX + "chip." + key,
        default = default,
        onBegin = { latestActions.beginClipEdit("Edit clip") },
        onUpdate = { v ->
            latestActions.updateClipVisuals(
                write(clip.visuals(), v.coerceIn(range.start, range.endInclusive))
            )
        },
        onCommit = latestActions.commitClipEdit,
        onCancel = latestActions.cancelClipEdit,
        onApply = { v ->
            latestActions.applyClipVisuals(
                "Edit clip",
                write(clip.visuals(), v.coerceIn(range.start, range.endInclusive))
            )
        }
    )

    val left = stringResource(Res.string.editor_video_limit_left)
    val right = stringResource(Res.string.editor_video_limit_right)
    val params = buildList {
        add(
            param(
                "scale", stringResource(Res.string.editor_transform_scale), VideoGlyphs.Scale,
                transform.scale * 100f, "${(transform.scale * 100f).roundToInt()}%",
                10f..400f, "10%", "400%", TAG_VIDEO_TRANSFORM_SR, 100f
            ) { v, x -> v.withTransform { copy(scale = x / 100f) } }
        )
        add(
            param(
                "rotation", stringResource(Res.string.editor_transform_rotation), VideoGlyphs.Rotation,
                transform.rotationDegrees, formatDegrees(transform.rotationDegrees),
                -180f..180f, "−180°", "+180°", TAG_VIDEO_TRANSFORM_PARAM_PREFIX + "rotation", 0f
            ) { v, x -> v.withTransform { copy(rotationDegrees = x) } }
        )
        add(
            param(
                "x", stringResource(Res.string.editor_transform_x), VideoGlyphs.PositionX,
                transform.translationX * 100f, formatSignedCompact(transform.translationX * 100f),
                -100f..100f, left, right, TAG_VIDEO_TRANSFORM_XY, 0f
            ) { v, x -> v.withTransform { copy(translationX = x / 100f) } }
        )
        add(
            param(
                "y", stringResource(Res.string.editor_transform_y), VideoGlyphs.PositionY,
                transform.translationY * 100f, formatSignedCompact(transform.translationY * 100f),
                -100f..100f,
                stringResource(Res.string.editor_video_limit_up),
                stringResource(Res.string.editor_video_limit_down),
                TAG_VIDEO_TRANSFORM_PARAM_PREFIX + "y", 0f
            ) { v, x -> v.withTransform { copy(translationY = x / 100f) } }
        )
        if (overlay) {
            add(
                param(
                    "opacity", stringResource(Res.string.editor_layer_opacity), VideoGlyphs.Opacity,
                    clip.opacity * 100f, "${(clip.opacity * 100f).roundToInt()}%",
                    0f..100f, "0%", "100%", TAG_VIDEO_TRANSFORM_OPACITY, 100f
                ) { v, x -> v.copy(opacity = (x / 100f).coerceIn(0f, 1f)) }
            )
        }
    }

    Column(
        modifier.videoSurfaceInset(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Fit/Fill/Crop — the clip's content mode. One tap is one
        // undoable apply, never a live draft.
        VideoPillSegments(
            options = VideoContentMode.entries.map { mode ->
                VideoSegment(
                    label = when (mode) {
                        VideoContentMode.Fit ->
                            stringResource(Res.string.editor_video_fit)
                        VideoContentMode.Fill ->
                            stringResource(Res.string.editor_video_fill)
                        VideoContentMode.Crop ->
                            stringResource(Res.string.editor_video_crop)
                    },
                    selected = clip.contentMode == mode,
                    enabled = enabled,
                    tag = TAG_VIDEO_TRANSFORM_MODE_PREFIX + mode.name,
                    onClick = { writeVisuals { it.copy(contentMode = mode) } }
                )
            }
        )
        VideoKeyedParams(
            params = params,
            identity = "transform/${clip.id}",
            interactionActive = state.isDrafting
        )
        Row(horizontalArrangement = Arrangement.spacedBy(VideoStudio.GroupGap)) {
            VideoTonalAction(
                label = stringResource(Res.string.editor_video_center),
                icon = VideoGlyphs.Centre,
                onClick = {
                    writeVisuals {
                        it.withTransform { copy(translationX = 0f, translationY = 0f) }
                    }
                },
                enabled = enabled &&
                    (transform.translationX != 0f || transform.translationY != 0f),
                modifier = Modifier.weight(1f).testTag(TAG_VIDEO_TRANSFORM_ALIGN)
            )
            VideoTonalAction(
                label = stringResource(Res.string.editor_flip),
                icon = VideoGlyphs.Flip,
                onClick = {
                    writeVisuals {
                        it.withTransform { copy(flipHorizontal = !flipHorizontal) }
                    }
                },
                enabled = enabled,
                on = transform.flipHorizontal,
                modifier = Modifier.weight(1f).testTag(TAG_VIDEO_TRANSFORM_FLIP)
            )
        }
        if (overlay) {
            Box {
                VideoV4Row(
                    label = stringResource(Res.string.editor_double_blend),
                    value = blendModeTitle(clip.blendMode),
                    enabled = enabled,
                    onClick = { blendMenu = true },
                    modifier = Modifier.testTag(TAG_VIDEO_TRANSFORM_BLEND)
                )
                DropdownMenu(
                    expanded = blendMenu,
                    onDismissRequest = { blendMenu = false }
                ) {
                    PhotoBlendMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(blendModeTitle(mode)) },
                            onClick = {
                                writeVisuals { it.copy(blendMode = mode) }
                                blendMenu = false
                            }
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Clip colour — the Light/Colour tabs over the clip's ColorAdjustments.
// ---------------------------------------------------------------------------

private enum class VideoColourTab { Light, Colour }

private class VideoColourParam(
    val key: String,
    val labelRes: StringResource,
    val icon: ImageVector,
    val rulerRange: ClosedFloatingPointRange<Float>,
    val step: Float,
    val unitSuffix: String = "",
    val read: (ColorAdjustments) -> Float,
    val write: (ColorAdjustments, Float) -> ColorAdjustments
) {
    fun displayOf(adjustments: ColorAdjustments): String =
        formatSignedCompact(read(adjustments)) + unitSuffix
}

private val VideoLightParameters = listOf(
    VideoColourParam(
        key = "exposure",
        labelRes = Res.string.editor_exposure,
        icon = VideoGlyphs.Exposure,
        rulerRange = -2f..2f,
        step = 0.1f,
        unitSuffix = " EV",
        read = { it.exposureStops },
        write = { adj, v -> adj.copy(exposureStops = v) }
    ),
    VideoColourParam(
        key = "contrast",
        labelRes = Res.string.editor_contrast,
        icon = VideoGlyphs.Contrast,
        rulerRange = -100f..100f,
        step = 1f,
        read = { contrastAmount(it.contrast) },
        write = { adj, v -> adj.copy(contrast = contrastFactor(v)) }
    ),
    VideoColourParam(
        key = "highlights",
        labelRes = Res.string.editor_highlights,
        icon = VideoGlyphs.Highlights,
        rulerRange = -100f..100f,
        step = 1f,
        read = { it.highlights * 100f },
        write = { adj, v -> adj.copy(highlights = v / 100f) }
    ),
    VideoColourParam(
        key = "shadows",
        labelRes = Res.string.editor_shadows,
        icon = VideoGlyphs.Shadows,
        rulerRange = -100f..100f,
        step = 1f,
        read = { it.shadows * 100f },
        write = { adj, v -> adj.copy(shadows = v / 100f) }
    )
)

private val VideoColourParameters = listOf(
    VideoColourParam(
        key = "saturation",
        labelRes = Res.string.editor_saturation,
        icon = VideoGlyphs.Saturation,
        rulerRange = -100f..100f,
        step = 1f,
        read = { it.saturation * 100f - 100f },
        write = { adj, v -> adj.copy(saturation = (v + 100f) / 100f) }
    )
)

/**
 * The Clip-colour panel: Light/Colour tabs over the selected clip's
 * `ColorAdjustments`, riding the clip draft seam so one gesture is one
 * undo step. Only parameters the model carries appear here — it has no
 * warmth or tint, so the Colour tab holds Saturation alone.
 */
@Composable
internal fun VideoColourSurface(
    state: VideoEditorState,
    actions: VideoEditorActions,
    modifier: Modifier = Modifier
) {
    val clip = state.selectedVideoClip() ?: return
    val adjustments = clip.adjustments
    val enabled = !state.isDrafting && !state.opening
    val latestActions by rememberUpdatedState(actions)

    var tab by rememberSaveable { mutableStateOf(VideoColourTab.Light.name) }
    val activeTab = VideoColourTab.valueOf(tab)
    val source = when (activeTab) {
        VideoColourTab.Light -> VideoLightParameters
        VideoColourTab.Colour -> VideoColourParameters
    }

    PublishVideoPanelReset(enabled = enabled && adjustments != ColorAdjustments()) {
        latestActions.applyClipVisuals(
            "Reset colour",
            clip.visuals().copy(adjustments = ColorAdjustments())
        )
    }

    fun visualsFor(param: VideoColourParam, display: Float): VideoClipVisuals {
        val current = clip.adjustments
        val next = if (display == param.read(current)) current else param.write(current, display)
        return clip.visuals().copy(adjustments = next)
    }

    val params = source.map { param ->
        VideoParam(
            key = param.key,
            label = stringResource(param.labelRes),
            icon = param.icon,
            value = param.read(adjustments),
            display = param.displayOf(adjustments),
            range = param.rulerRange,
            step = param.step,
            limitStart = formatSignedCompact(param.rulerRange.start) + param.unitSuffix,
            limitEnd = formatSignedCompact(param.rulerRange.endInclusive) + param.unitSuffix,
            sliderTag = TAG_VIDEO_COLOUR_SLIDER_PREFIX + param.key,
            valueTag = TAG_VIDEO_COLOUR_VALUE_PREFIX + param.key,
            chipTag = TAG_VIDEO_COLOUR_PARAM_PREFIX + param.key,
            onBegin = { latestActions.beginClipEdit("Edit clip") },
            onUpdate = { latestActions.updateClipVisuals(visualsFor(param, it)) },
            onCommit = latestActions.commitClipEdit,
            onCancel = latestActions.cancelClipEdit,
            onApply = { latestActions.applyClipVisuals("Edit clip", visualsFor(param, it)) }
        )
    }

    Column(
        modifier.videoSurfaceInset(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        VideoPillSegments(
            options = VideoColourTab.entries.map { entry ->
                VideoSegment(
                    label = when (entry) {
                        VideoColourTab.Light ->
                            stringResource(Res.string.editor_video_colour_light)
                        VideoColourTab.Colour ->
                            stringResource(Res.string.editor_video_colour_colour)
                    },
                    selected = activeTab == entry,
                    enabled = enabled,
                    tag = TAG_VIDEO_COLOUR_TAB_PREFIX + entry.name,
                    onClick = { tab = entry.name }
                )
            }
        )
        VideoKeyedParams(
            params = params,
            identity = "colour/${clip.id}/$tab",
            interactionActive = state.isDrafting
        )
    }
}

// ---------------------------------------------------------------------------
// Video layers — the visual tracks and their clips.
// ---------------------------------------------------------------------------

/**
 * The Layers panel: one option row per clip, topmost lane first — the same
 * selection the timeline paints — with the lane's real hide and lock as
 * side actions. Those act on the whole lane, so only a lane's first row
 * carries the lane tags. Add overlay switches the Media destination into
 * overlay-insertion mode.
 */
@Composable
internal fun VideoLayersSurface(
    state: VideoEditorState,
    actions: VideoEditorActions,
    onAddOverlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sequence = state.sequence ?: return
    Column(modifier.videoSurfaceInset()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            sequence.displayLanes().forEach { track ->
                track.clips.forEachIndexed { index, clip ->
                    VideoLayerRow(track, clip, index == 0, state, actions)
                }
            }
            VideoTonalAction(
                label = stringResource(Res.string.editor_video_add_overlay),
                icon = VideoGlyphs.Add,
                onClick = onAddOverlay,
                enabled = !state.isDrafting && !state.opening,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TAG_VIDEO_LAYERS_ADD_OVERLAY)
            )
        }
    }
}

@Composable
private fun VideoLayerRow(
    track: VideoTrack,
    clip: VideoClip,
    laneHead: Boolean,
    state: VideoEditorState,
    actions: VideoEditorActions
) {
    val enabled = !state.isDrafting && !state.opening
    val name = state.clipDisplayName(clip)
    val overlay = track.kind == VideoTrackKind.Overlay
    VideoOptionRow(
        title = name,
        supporting = stringResource(
            if (overlay) Res.string.editor_video_layer_overlay_meta
            else Res.string.editor_video_layer_main_meta,
            formatVideoTimecode(clip.timelineRange.duration)
        ),
        icon = if (overlay) VideoGlyphs.Overlay else VideoGlyphs.Movie,
        selected = state.selectedClipId == clip.id,
        enabled = enabled && !track.locked,
        onClick = { actions.selectClip(clip.id) },
        rowModifier = Modifier.testTag(TAG_VIDEO_LAYER_ROW_PREFIX + clip.id)
    ) {
        VideoRowSideAction(
            icon = if (track.visible) VideoGlyphs.Visible else VideoGlyphs.Hidden,
            label = stringResource(
                if (track.visible) Res.string.editor_video_hide_layer
                else Res.string.editor_video_show_layer,
                name
            ),
            onClick = { actions.setTrackVisible(track.id, !track.visible) },
            shape = RoundedCornerShape(6.dp),
            enabled = enabled,
            modifier = if (laneHead) {
                Modifier.testTag(TAG_VIDEO_TRACK_VISIBLE_PREFIX + track.id)
            } else Modifier
        )
        VideoRowSideAction(
            icon = if (track.locked) VideoGlyphs.Lock else VideoGlyphs.Unlock,
            label = stringResource(
                if (track.locked) Res.string.editor_video_unlock_layer
                else Res.string.editor_video_lock_layer,
                name
            ),
            onClick = { actions.setTrackLocked(track.id, !track.locked) },
            shape = RoundedCornerShape(
                topStart = 6.dp, bottomStart = 6.dp, topEnd = 16.dp, bottomEnd = 16.dp
            ),
            enabled = enabled,
            modifier = if (laneHead) {
                Modifier.testTag(TAG_VIDEO_TRACK_LOCK_PREFIX + track.id)
            } else Modifier
        )
    }
}
