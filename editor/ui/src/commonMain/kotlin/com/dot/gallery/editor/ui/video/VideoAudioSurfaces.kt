package com.dot.gallery.editor.ui.video

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.dot.gallery.editor.ui.generated.resources.editor_video_choose_audio
import com.dot.gallery.editor.ui.generated.resources.editor_video_choose_audio_sub
import com.dot.gallery.editor.ui.generated.resources.editor_video_extract_audio
import com.dot.gallery.editor.ui.generated.resources.editor_video_limit_left
import com.dot.gallery.editor.ui.generated.resources.editor_video_limit_right
import com.dot.gallery.editor.ui.generated.resources.editor_video_link_left_right
import com.dot.gallery.editor.ui.generated.resources.editor_video_mix_to_mono
import com.dot.gallery.editor.ui.generated.resources.editor_video_mute_clip
import com.dot.gallery.editor.ui.generated.resources.editor_video_record_voice
import com.dot.gallery.editor.ui.generated.resources.editor_video_record_voice_sub
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.foundation.focusable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.dot.gallery.editor.core.model.AudioClip
import com.dot.gallery.editor.core.model.AudioRouting
import com.dot.gallery.editor.core.model.AudioTrack
import com.dot.gallery.editor.core.time.EDITOR_TICKS_PER_SECOND
import com.dot.gallery.editor.core.time.EditorTime
import com.dot.gallery.editor.render.video.AudioClipEdit
import com.dot.gallery.editor.render.video.AudioTrackEdit
import com.dot.gallery.editor.render.video.VideoEditorState
import com.dot.gallery.editor.render.video.VideoLevels
import com.dot.gallery.editor.ui.EditorToolIds
import com.dot.gallery.editor.ui.NumericEntryDialog
import com.dot.gallery.editor.ui.TAG_VIDEO_TIMELINE_EFFECT_PREFIX
import com.dot.gallery.editor.ui.generated.resources.Res
import com.dot.gallery.editor.ui.generated.resources.editor_audio_clip_a11y
import com.dot.gallery.editor.ui.generated.resources.editor_audio_extract_sub
import com.dot.gallery.editor.ui.generated.resources.editor_audio_fade_in
import com.dot.gallery.editor.ui.generated.resources.editor_audio_fade_out
import com.dot.gallery.editor.ui.generated.resources.editor_audio_fader
import com.dot.gallery.editor.ui.generated.resources.editor_audio_gain
import com.dot.gallery.editor.ui.generated.resources.editor_audio_limiter
import com.dot.gallery.editor.ui.generated.resources.editor_audio_link
import com.dot.gallery.editor.ui.generated.resources.editor_audio_link_sub
import com.dot.gallery.editor.ui.generated.resources.editor_audio_detached_sub
import com.dot.gallery.editor.ui.generated.resources.editor_audio_master
import com.dot.gallery.editor.ui.generated.resources.editor_audio_mono
import com.dot.gallery.editor.ui.generated.resources.editor_audio_mute
import com.dot.gallery.editor.ui.generated.resources.editor_audio_no_clip
import com.dot.gallery.editor.ui.generated.resources.editor_audio_no_track
import com.dot.gallery.editor.ui.generated.resources.editor_audio_pan
import com.dot.gallery.editor.ui.generated.resources.editor_audio_pan_centre
import com.dot.gallery.editor.ui.generated.resources.editor_audio_pan_left
import com.dot.gallery.editor.ui.generated.resources.editor_audio_pan_right
import com.dot.gallery.editor.ui.generated.resources.editor_audio_solo
import com.dot.gallery.editor.ui.generated.resources.editor_audio_source_sound
import com.dot.gallery.editor.ui.generated.resources.editor_audio_stereo
import com.dot.gallery.editor.ui.generated.resources.editor_audio_unmute
import com.dot.gallery.editor.ui.generated.resources.editor_video_select_audio_clip
import com.dot.gallery.editor.ui.icons.Mic
import com.dot.gallery.editor.ui.icons.Movie
import com.dot.gallery.editor.ui.trimTrailingZeros
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource

// ---------------------------------------------------------------------------
// Tags — every interactive/audio node a test or accessibility pass can name.
// ---------------------------------------------------------------------------

internal const val TAG_VIDEO_AUDIO_LANE_PREFIX = "videoEditor.audioLane."
internal const val TAG_VIDEO_AUDIO_CLIP_PREFIX = "videoEditor.audioClip."
internal const val TAG_AUDIO_TRACK_ROW_PREFIX = "videoEditor.audio.track."
internal const val TAG_AUDIO_FILES = "videoEditor.audio.files"
internal const val TAG_AUDIO_RECORD = "videoEditor.audio.record"
internal const val TAG_AUDIO_EXTRACT = "videoEditor.audio.extract"
internal const val TAG_AUDIO_CLIP_GAIN_VALUE = "videoEditor.audio.clipGainValue"
internal const val TAG_AUDIO_CLIP_GAIN_SLIDER = "videoEditor.audio.clipGain"
internal const val TAG_AUDIO_FADES = "videoEditor.audio.fades"
internal const val TAG_AUDIO_CLIP_MUTE = "videoEditor.audio.clipMute"
internal const val TAG_AUDIO_VIDEO_LINK = "videoEditor.audio.videoLink"
internal const val TAG_AUDIO_MIXER = "videoEditor.audio.mixer"
internal const val TAG_AUDIO_CARD_PREFIX = "videoEditor.audio.card."
internal const val TAG_AUDIO_FADER_PREFIX = "videoEditor.audio.fader."
internal const val TAG_AUDIO_METER_PREFIX = "videoEditor.audio.meter."
internal const val TAG_AUDIO_MUTE_PREFIX = "videoEditor.audio.mute."
internal const val TAG_AUDIO_SOLO_PREFIX = "videoEditor.audio.solo."
internal const val TAG_AUDIO_GAIN_VALUE_PREFIX = "videoEditor.audio.gainValue."
internal const val TAG_AUDIO_PAN_PREFIX = "videoEditor.audio.pan."
internal const val TAG_AUDIO_LIMITER = "videoEditor.audio.limiter"
internal const val TAG_AUDIO_SOURCE_MUTE = "videoEditor.audio.sourceMute"
internal const val TAG_AUDIO_MASTER_GAIN = "videoEditor.audio.masterGain"
internal const val TAG_AUDIO_ACTION_CHANNELS = "videoEditor.audio.channels"
internal const val TAG_AUDIO_ACTION_DUCKING = "videoEditor.audio.ducking"
internal const val TAG_AUDIO_ACTION_EQ = "videoEditor.audio.eq"
internal const val TAG_AUDIO_ROUTING = "videoEditor.audio.routing"
internal const val TAG_AUDIO_ROUTING_TRACK = "videoEditor.audio.routingTrack"
internal const val TAG_AUDIO_ROUTING_LEFT = "videoEditor.audio.routeLeft"
internal const val TAG_AUDIO_ROUTING_RIGHT = "videoEditor.audio.routeRight"
internal const val TAG_AUDIO_ROUTING_LINK = "videoEditor.audio.routeLink"
internal const val TAG_AUDIO_ROUTING_PAN = "videoEditor.audio.routePan"
internal const val TAG_AUDIO_ROUTING_MONO = "videoEditor.audio.monoDownmix"
internal const val TAG_AUDIO_WAVEFORM = "videoEditor.audio.waveform"

private val AUDIO_FADER_RANGE = AudioRouting.MIN_GAIN_DB..AudioRouting.MAX_GAIN_DB
private const val AUDIO_FADER_STEP = 0.5f
private const val AUDIO_FADE_MAX_SECONDS = 5f
private val MIXER_STRIP_MIN_WIDTH = 78.dp
private val MIXER_STRIP_GAP = 6.dp

// ---------------------------------------------------------------------------
// Shared helpers — labels, formatting and the audio selection.
// ---------------------------------------------------------------------------

/** The A-lane label — audio tracks number independently of the visual lanes. */
internal fun audioLaneLabel(index: Int): String = "A${index + 1}"

/** The selected audio clip together with its owning lane. */
internal fun VideoEditorState.selectedAudioClip(): Pair<AudioTrack, AudioClip>? {
    val seq = sequence ?: return null
    val id = selectedAudioClipId ?: return null
    seq.audioTracks.forEach { track ->
        track.clips.firstOrNull { it.id == id }?.let { return track to it }
    }
    return null
}

private fun audioSeconds(time: EditorTime): Float =
    (time.ticks.toDouble() / EDITOR_TICKS_PER_SECOND).toFloat()

private fun editorTimeSeconds(seconds: Float): EditorTime =
    EditorTime((seconds.toDouble() * EDITOR_TICKS_PER_SECOND).toLong())

internal fun formatDb(db: Float): String {
    val text = trimTrailingZeros(kotlin.math.abs(db))
    return when {
        db > 0.0005f -> "+$text"
        db < -0.0005f -> "−$text"
        else -> "0"
    }
}

@Composable
private fun panLabel(pan: Float): String = when {
    pan < -0.005f -> stringResource(
        Res.string.editor_audio_pan_left,
        (-pan * 100f).roundToInt()
    )
    pan > 0.005f -> stringResource(
        Res.string.editor_audio_pan_right,
        (pan * 100f).roundToInt()
    )
    else -> stringResource(Res.string.editor_audio_pan_centre)
}

@Composable
private fun channelLabel(channels: Int): String = stringResource(
    if (channels == 1) Res.string.editor_audio_mono
    else Res.string.editor_audio_stereo
)

// ---------------------------------------------------------------------------
// Shared visuals — waveform, meters, faders, pan and the Mute/Solo buttons.
// ---------------------------------------------------------------------------

/**
 * The PCM-peak strip every audio surface draws: [peaks] is the bounded
 * summary the session publishes per source, windowed to the clip's source
 * range. A missing summary draws the honest centre baseline — never an
 * invented shape.
 */
@Composable
internal fun AudioWaveform(
    peaks: FloatArray?,
    windowStart: Float,
    windowEnd: Float,
    dimmed: Boolean,
    modifier: Modifier = Modifier,
    /**
     * The timeline lane's form: one mirrored envelope filled in the
     * tertiary ink instead of discrete bars.
     */
    filled: Boolean = false
) {
    val barColor = if (filled) MaterialTheme.colorScheme.onTertiaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant
    val dimColor = MaterialTheme.colorScheme.outline
    val span = (windowEnd - windowStart).coerceAtLeast(0f)
    Canvas(modifier = modifier.fillMaxSize()) {
        val alpha = if (dimmed) 0.38f else 0.9f
        val color = if (filled) {
            if (dimmed) dimColor else barColor
        } else {
            barColor.copy(alpha = alpha)
        }
        val centre = size.height / 2f
        if (peaks == null || peaks.isEmpty() || span <= 0f) {
            drawLine(
                color = color,
                start = Offset(0f, centre),
                end = Offset(size.width, centre),
                strokeWidth = 2.dp.toPx()
            )
            return@Canvas
        }
        if (filled) {
            // The envelope keeps a 2 px floor so silence still reads as a
            // line, and stays inside 90 % of the lane so the label chip
            // and the selection ring never collide with a peak.
            val stepPx = 2.dp.toPx()
            val reach = centre * 0.9f
            val upper = mutableListOf<Offset>()
            var x = 0f
            while (x <= size.width) {
                val fraction = windowStart + (x / size.width) * span
                val bucket = (fraction * peaks.size).toInt()
                    .coerceIn(0, peaks.size - 1)
                val half = (peaks[bucket].coerceIn(0f, 1f) * reach)
                    .coerceAtLeast(1f)
                upper += Offset(x, centre - half)
                x += stepPx
            }
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, centre)
                upper.forEach { lineTo(it.x, it.y) }
                for (i in upper.indices.reversed()) {
                    val p = upper[i]
                    lineTo(p.x, centre + (centre - p.y))
                }
                close()
            }
            drawPath(path, color)
            return@Canvas
        }
        val barPx = 2.dp.toPx()
        val stepPx = barPx + 1.dp.toPx()
        var x = 0f
        while (x < size.width) {
            val fraction = windowStart + (x / size.width) * span
            val bucket = (fraction * peaks.size).toInt()
                .coerceIn(0, peaks.size - 1)
            val half = (peaks[bucket].coerceIn(0f, 1f) * centre)
                .coerceAtLeast(0.5f)
            drawLine(
                color = color,
                start = Offset(x + barPx / 2f, centre - half),
                end = Offset(x + barPx / 2f, centre + half),
                strokeWidth = barPx
            )
            x += stepPx
        }
    }
}

/** One live meter: two stacked L/R bars on phones, a side-by-side pair on desktop strips. */
@Composable
internal fun AudioMeterPair(
    level: VideoLevels.Stereo,
    vertical: Boolean,
    modifier: Modifier = Modifier
) {
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val fillColor = MaterialTheme.colorScheme.primary
    if (vertical) {
        Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            listOf(level.left, level.right).forEach { channel ->
                Canvas(Modifier.width(5.dp).fillMaxHeight()) {
                    drawRect(trackColor)
                    val h = size.height * channel.coerceIn(0f, 1f)
                    drawRect(
                        color = fillColor,
                        topLeft = Offset(0f, size.height - h),
                        size = Size(size.width, h)
                    )
                }
            }
        }
    } else {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            listOf(level.left, level.right).forEach { channel ->
                Canvas(Modifier.fillMaxWidth().height(3.dp)) {
                    drawRect(trackColor)
                    drawRect(
                        color = fillColor,
                        size = Size(size.width * channel.coerceIn(0f, 1f), size.height)
                    )
                }
            }
        }
    }
}

private class FaderCallbacks(
    val value: Float,
    val enabled: Boolean,
    val interactionActive: Boolean,
    val onBegin: () -> Unit,
    val onUpdate: (Float) -> Unit,
    val onCommit: () -> Unit,
    val onCancel: () -> Unit,
    val onApply: (Float) -> Unit
)

/** The vertical counterpart for the desktop strip — same draft contract. */
@Composable
internal fun AudioVerticalFader(
    value: Float,
    description: String,
    enabled: Boolean,
    interactionActive: Boolean,
    identity: Any,
    tag: String,
    onBegin: () -> Unit,
    onUpdate: (Float) -> Unit,
    onCommit: () -> Unit,
    onCancel: () -> Unit,
    onApply: (Float) -> Unit,
    modifier: Modifier = Modifier,
    /** Replaces the default thin-line paint; receives the value's 0..1 height. */
    paint: (@Composable (fraction: Float) -> Unit)? = null
) {
    var gestureOpen by remember { mutableStateOf(false) }
    val latest by rememberUpdatedState(
        FaderCallbacks(value, enabled, interactionActive, onBegin, onUpdate,
            onCommit, onCancel, onApply)
    )
    fun quantize(fraction: Float): Float =
        (AUDIO_FADER_RANGE.start +
            fraction.coerceIn(0f, 1f) *
            (AUDIO_FADER_RANGE.endInclusive - AUDIO_FADER_RANGE.start))
            .let { (it / AUDIO_FADER_STEP).roundToInt() * AUDIO_FADER_STEP }
            .coerceIn(AUDIO_FADER_RANGE.start, AUDIO_FADER_RANGE.endInclusive)
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val fillColor = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .semantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = value.coerceIn(
                        AUDIO_FADER_RANGE.start,
                        AUDIO_FADER_RANGE.endInclusive
                    ),
                    range = AUDIO_FADER_RANGE
                )
                setProgress { entered ->
                    if (latest.enabled && entered.isFinite()) {
                        latest.onApply(
                            entered.coerceIn(
                                AUDIO_FADER_RANGE.start,
                                AUDIO_FADER_RANGE.endInclusive
                            )
                        )
                    }
                    true
                }
            }
            .testTag(tag)
            .focusable(enabled = enabled)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val step = if (event.isShiftPressed) 6f else 1f
                val delta = when (event.key) {
                    Key.DirectionUp -> step
                    Key.DirectionDown -> -step
                    else -> return@onKeyEvent false
                }
                if (latest.enabled) {
                    latest.onApply((latest.value + delta).coerceIn(
                        AUDIO_FADER_RANGE.start,
                        AUDIO_FADER_RANGE.endInclusive
                    ))
                }
                true
            }
            .pointerInput(identity, enabled) {
                detectTapGestures { offset ->
                    if (latest.enabled && !latest.interactionActive) {
                        latest.onApply(quantize(1f - offset.y / size.height))
                    }
                }
            }
            .pointerInput(identity, enabled) {
                detectDragGestures(
                    onDragStart = {
                        if (latest.enabled && !latest.interactionActive) {
                            gestureOpen = true
                            latest.onBegin()
                        }
                    },
                    onDragEnd = {
                        if (gestureOpen) {
                            gestureOpen = false
                            latest.onCommit()
                        }
                    },
                    onDragCancel = {
                        if (gestureOpen) {
                            gestureOpen = false
                            latest.onCancel()
                        }
                    }
                ) { change, _ ->
                    if (gestureOpen) {
                        change.consume()
                        latest.onUpdate(
                            quantize(1f - change.position.y / size.height)
                        )
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val fraction = ((value - AUDIO_FADER_RANGE.start) /
            (AUDIO_FADER_RANGE.endInclusive - AUDIO_FADER_RANGE.start))
            .coerceIn(0f, 1f)
        if (paint != null) {
            paint(fraction)
        } else {
            Canvas(Modifier.width(4.dp).fillMaxHeight()) {
                drawRoundRect(
                    color = trackColor,
                    size = size,
                    cornerRadius = androidx.compose.ui.geometry
                        .CornerRadius(size.width / 2f)
                )
                val h = size.height * fraction
                drawRoundRect(
                    color = fillColor,
                    topLeft = Offset(0f, size.height - h),
                    size = Size(size.width, h),
                    cornerRadius = androidx.compose.ui.geometry
                        .CornerRadius(size.width / 2f)
                )
            }
            Canvas(Modifier.fillMaxSize()) {
                val thumbY = size.height * (1f - fraction)
                drawLine(
                    color = fillColor,
                    start = Offset(0f, thumbY),
                    end = Offset(size.width, thumbY),
                    strokeWidth = 2.dp.toPx()
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Add audio — the pick list (v-add-audio).
// ---------------------------------------------------------------------------

/**
 * The Add-audio sheet (`v-add-audio`): the project's real audio lanes at
 * the top (tap selects the lane), then the three sources — Files asks the
 * host picker, Record opens the Voice-over surface (which itself owns
 * the honest permission/unavailable states), Extract-from-video demotes
 * the selected clip's embedded sound
 * onto its own lane through the real session op. New sources always land
 * as a new track — the tonal note says so rather than offering a fake
 * placement choice.
 */
@Composable
internal fun VideoAddAudioSurface(
    state: VideoEditorState,
    actions: VideoEditorActions,
    modifier: Modifier = Modifier
) {
    val sequence = state.sequence
    val workspace = LocalVideoWorkspace.current
    Column(
        modifier.videoSurfaceInset(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        VideoOptionRow(
            title = stringResource(Res.string.editor_video_choose_audio),
            supporting = stringResource(Res.string.editor_video_choose_audio_sub),
            icon = VideoGlyphs.LibraryMusic,
            enabled = sequence != null && !state.isDrafting && !state.importing,
            onClick = { actions.requestAddAudio() },
            rowModifier = Modifier.testTag(TAG_AUDIO_FILES)
        )
        VideoOptionRow(
            title = stringResource(Res.string.editor_video_record_voice),
            supporting = stringResource(Res.string.editor_video_record_voice_sub),
            icon = VideoGlyphs.Mic,
            enabled = sequence != null && !state.isDrafting && workspace != null,
            onClick = { workspace?.openAudioTask?.invoke(EditorToolIds.VideoVoice) },
            rowModifier = Modifier.testTag(TAG_AUDIO_RECORD)
        )
        val videoClip = state.selectedVideoClip()
        VideoOptionRow(
            title = stringResource(Res.string.editor_video_extract_audio),
            supporting = stringResource(Res.string.editor_audio_extract_sub),
            icon = VideoGlyphs.Movie,
            enabled = videoClip != null && !videoClip.audioMuted &&
                !state.isDrafting && !state.importing,
            onClick = { actions.extractAudio() },
            rowModifier = Modifier.testTag(TAG_AUDIO_EXTRACT)
        )
        if (state.importing) VideoImportStrip(actions)
    }
}

/** The lane picker every audio panel opens with. */
@Composable
internal fun AudioLaneSegments(
    tracks: List<AudioTrack>,
    selected: AudioTrack,
    enabled: Boolean,
    actions: VideoEditorActions,
    modifier: Modifier = Modifier
) {
    VideoPillSegments(
        options = tracks.map { track ->
            VideoSegment(
                label = track.name,
                selected = track.id == selected.id,
                enabled = enabled,
                onClick = { actions.selectAudioTrack(track.id) }
            )
        },
        modifier = modifier
    )
}

/** The lane the audio panels edit: the session's selection, else the first. */
internal fun VideoEditorState.panelAudioTrack(tracks: List<AudioTrack>): AudioTrack? =
    tracks.firstOrNull { it.id == selectedAudioTrackId } ?: tracks.firstOrNull()

/** A dB-scale parameter on the shared [-60, +12] range. */
private fun dbParam(
    key: String,
    label: String,
    value: Float,
    tag: String,
    onBegin: () -> Unit,
    onUpdate: (Float) -> Unit,
    onCommit: () -> Unit,
    onCancel: () -> Unit,
    onApply: (Float) -> Unit,
    icon: ImageVector? = null,
    valueTag: String = "$tag.value",
    chipTag: String = "$tag.chip",
    enabled: Boolean = true
) = VideoParam(
    key = key, label = label, value = value, display = "${formatDb(value)} dB",
    range = AUDIO_FADER_RANGE, step = AUDIO_FADER_STEP,
    limitStart = "−60 dB", limitEnd = "+12 dB", sliderTag = tag, icon = icon,
    valueTag = valueTag, chipTag = chipTag, enabled = enabled,
    onBegin = onBegin, onUpdate = onUpdate, onCommit = onCommit, onCancel = onCancel,
    onApply = onApply
)

// ---------------------------------------------------------------------------
// Clip audio — the selected audio clip's recipe (v-clip-audio).
// ---------------------------------------------------------------------------

/**
 * The Clip-audio panel: the lane picker, then Gain, Pan and the two fades
 * of the lane's selected clip through the audio draft seam, so one gesture
 * is one undo step, and its Mute. With no audio lane yet, the selected
 * video clip's embedded-sound link stands in: unlink extracts the sound
 * onto its own lane, relink unmutes the embedded input.
 */
@Composable
internal fun VideoClipAudioSurface(
    state: VideoEditorState,
    actions: VideoEditorActions,
    modifier: Modifier = Modifier
) {
    val sequence = state.sequence
    val tracks = sequence?.audioTracks.orEmpty()
    val latestActions by rememberUpdatedState(actions)
    val track = state.panelAudioTrack(tracks)
    val selectedPair = state.selectedAudioClip()?.takeIf { it.first.id == track?.id }
    val clip = selectedPair?.second ?: track?.clips?.firstOrNull()
    val editable = sequence != null && !state.opening

    Column(
        modifier.videoSurfaceInset(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (track != null) {
            AudioLaneSegments(tracks, track, !state.isDrafting, actions)
        }
        if (track != null && clip != null) {
            val rulerEnabled = editable && !track.locked
            val canReset = clip.gainDb != 0f || clip.pan != 0f ||
                clip.fadeIn != EditorTime.Zero || clip.fadeOut != EditorTime.Zero || clip.muted
            PublishVideoPanelReset(enabled = rulerEnabled && !state.isDrafting && canReset) {
                latestActions.applyAudioClipEdit(
                    "Reset clip audio", track.id, clip.id,
                    AudioClipEdit(
                        gainDb = 0f, pan = 0f, muted = false,
                        fadeIn = EditorTime.Zero, fadeOut = EditorTime.Zero
                    )
                )
            }
            val onBegin: (String) -> () -> Unit = { label -> { latestActions.beginAudioEdit(label) } }
            fun update(edit: AudioClipEdit) = latestActions.updateAudioClipEdit(track.id, clip.id, edit)
            fun apply(label: String, edit: AudioClipEdit) =
                latestActions.applyAudioClipEdit(label, track.id, clip.id, edit)
            val fadeRange = 0f..minOf(AUDIO_FADE_MAX_SECONDS, audioSeconds(clip.sourceRange.duration))
            val fadeEnd = "${trimTrailingZeros(fadeRange.endInclusive)} s"
            fun fadeParam(
                key: String,
                label: String,
                icon: ImageVector,
                value: EditorTime,
                tag: String,
                edit: (EditorTime) -> AudioClipEdit
            ) = VideoParam(
                key = key, label = label, icon = icon, value = audioSeconds(value),
                display = "${trimTrailingZeros(audioSeconds(value))} s",
                range = fadeRange, step = 0.1f, limitStart = "0 s", limitEnd = fadeEnd,
                sliderTag = tag, valueTag = "$tag.value", chipTag = "$tag.chip",
                enabled = rulerEnabled,
                onBegin = onBegin("Edit fades"),
                onUpdate = { update(edit(editorTimeSeconds(it))) },
                onCommit = latestActions.commitAudioEdit,
                onCancel = latestActions.cancelAudioEdit,
                onApply = { apply("Edit fades", edit(editorTimeSeconds(it))) }
            )
            VideoKeyedParams(
                params = listOf(
                    dbParam(
                        key = "gain",
                        label = stringResource(Res.string.editor_audio_gain),
                        icon = VideoGlyphs.VolumeUp,
                        value = clip.gainDb,
                        tag = TAG_AUDIO_CLIP_GAIN_SLIDER,
                        valueTag = TAG_AUDIO_CLIP_GAIN_VALUE,
                        enabled = rulerEnabled,
                        onBegin = onBegin("Edit clip gain"),
                        onUpdate = { update(AudioClipEdit(gainDb = it)) },
                        onCommit = latestActions.commitAudioEdit,
                        onCancel = latestActions.cancelAudioEdit,
                        onApply = { apply("Edit clip gain", AudioClipEdit(gainDb = it)) }
                    ),
                    VideoParam(
                        key = "pan",
                        label = stringResource(Res.string.editor_audio_pan),
                        icon = VideoGlyphs.Pan,
                        value = clip.pan,
                        display = panLabel(clip.pan),
                        range = -1f..1f,
                        step = 0.01f,
                        limitStart = stringResource(Res.string.editor_video_limit_left),
                        limitEnd = stringResource(Res.string.editor_video_limit_right),
                        sliderTag = "$TAG_AUDIO_CLIP_GAIN_SLIDER.pan",
                        enabled = rulerEnabled,
                        onBegin = onBegin("Edit clip pan"),
                        onUpdate = { update(AudioClipEdit(pan = it)) },
                        onCommit = latestActions.commitAudioEdit,
                        onCancel = latestActions.cancelAudioEdit,
                        onApply = { apply("Edit clip pan", AudioClipEdit(pan = it)) }
                    ),
                    fadeParam(
                        "fadeIn", stringResource(Res.string.editor_audio_fade_in),
                        VideoGlyphs.FadeIn, clip.fadeIn, TAG_AUDIO_FADES + ".in"
                    ) { AudioClipEdit(fadeIn = it) },
                    fadeParam(
                        "fadeOut", stringResource(Res.string.editor_audio_fade_out),
                        VideoGlyphs.FadeOut, clip.fadeOut, TAG_AUDIO_FADES + ".out"
                    ) { AudioClipEdit(fadeOut = it) }
                ),
                identity = "clipAudio/${clip.id}",
                interactionActive = state.isDrafting
            )
            VideoStudioSwitchRow(
                label = stringResource(Res.string.editor_video_mute_clip),
                checked = clip.muted,
                enabled = rulerEnabled && !state.isDrafting,
                tag = TAG_AUDIO_CLIP_MUTE,
                onCheckedChange = { checked ->
                    actions.applyAudioClipEdit(
                        if (checked) "Mute clip" else "Unmute clip",
                        track.id, clip.id, AudioClipEdit(muted = checked)
                    )
                }
            )
        } else {
            val videoClip = state.selectedVideoClip()
            val videoTrack = videoClip?.let { sequence?.trackOf(it) }
            if (videoClip != null && videoTrack != null) {
                VideoStudioSwitchRow(
                    label = stringResource(Res.string.editor_audio_link),
                    supporting = stringResource(
                        if (videoClip.audioMuted) Res.string.editor_audio_detached_sub
                        else Res.string.editor_audio_link_sub
                    ),
                    checked = !videoClip.audioMuted,
                    enabled = editable && !state.isDrafting && !state.importing,
                    tag = TAG_AUDIO_VIDEO_LINK,
                    onCheckedChange = { linked ->
                        if (linked) {
                            actions.setAudioLink(videoTrack.id, videoClip.id, true)
                        } else {
                            // Detach preserves the sound — extraction moves it to
                            // its own lane and mutes the embedded input in one step.
                            actions.extractAudio()
                        }
                    }
                )
            } else {
                Text(
                    text = stringResource(Res.string.editor_audio_no_clip),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Mixer — lane cards on phone, channel strips on desktop (v-mixer/x-mixer).
// ---------------------------------------------------------------------------

/**
 * The Mixer panel: one real strip per audio lane plus the virtual
 * Source-sound input and the Master bus — no fabricated channels. Each lane
 * strip carries its name (tap selects the lane), a vertical gain fader with
 * its live meter, the dB readout (tap for exact entry) and Mute/Solo. Below
 * sit the selected lane's Pan ruler and the master limiter. The panel header
 * owns the Channels, EQ and Ducking links.
 */
@Composable
internal fun VideoMixerSurface(
    state: VideoEditorState,
    actions: VideoEditorActions,
    modifier: Modifier = Modifier
) {
    val sequence = state.sequence ?: return
    val latestActions by rememberUpdatedState(actions)
    val track = state.panelAudioTrack(sequence.audioTracks)
    Column(
        modifier.videoSurfaceInset(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val tablet = videoTabletWindow()
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val faderHeight = when {
                tablet && maxWidth >= VIDEO_PANEL_TWO_COLUMN -> 97.dp
                tablet && maxWidth >= VIDEO_PANEL_WIDE -> 141.dp
                maxWidth >= 330.dp -> 169.dp
                else -> 85.dp
            }
            val count = sequence.audioTracks.size + 2
            val stripWidth = maxOf(
                MIXER_STRIP_MIN_WIDTH,
                (maxWidth - MIXER_STRIP_GAP * (count - 1)) / count
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(MIXER_STRIP_GAP),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .horizontalScroll(rememberScrollState())
                    .testTag(TAG_AUDIO_MIXER)
            ) {
                sequence.audioTracks.forEachIndexed { index, lane ->
                    MixerLaneStrip(
                        track = lane,
                        level = state.levels.lanes.getOrNull(index) ?: VideoLevels.Stereo(),
                        selected = lane.id == track?.id,
                        faderHeight = faderHeight,
                        state = state,
                        actions = actions,
                        modifier = Modifier.width(stripWidth).fillMaxHeight()
                    )
                }
                MixerSourceStrip(
                    state = state,
                    actions = actions,
                    faderHeight = faderHeight,
                    modifier = Modifier.width(stripWidth).fillMaxHeight()
                )
                MixerMasterStrip(
                    state = state,
                    actions = actions,
                    faderHeight = faderHeight,
                    modifier = Modifier.width(stripWidth).fillMaxHeight()
                )
            }
        }
        if (track != null) {
            VideoKeyedParams(
                params = listOf(
                    VideoParam(
                        key = "pan",
                        label = stringResource(Res.string.editor_audio_pan),
                        value = track.pan,
                        display = panLabel(track.pan),
                        range = -1f..1f,
                        step = 0.01f,
                        limitStart = stringResource(Res.string.editor_video_limit_left),
                        limitEnd = stringResource(Res.string.editor_video_limit_right),
                        sliderTag = TAG_AUDIO_PAN_PREFIX + track.id,
                        enabled = !state.opening,
                        onBegin = { latestActions.beginAudioEdit("Edit lane pan") },
                        onUpdate = {
                            latestActions.updateAudioTrackEdit(track.id, AudioTrackEdit(pan = it))
                        },
                        onCommit = latestActions.commitAudioEdit,
                        onCancel = latestActions.cancelAudioEdit,
                        onApply = {
                            latestActions.applyAudioTrackEdit(
                                "Edit lane pan", track.id, AudioTrackEdit(pan = it)
                            )
                        }
                    )
                ),
                identity = "mixerPan/${track.id}",
                interactionActive = state.isDrafting
            )
        }
        VideoStudioSwitchRow(
            label = stringResource(Res.string.editor_audio_limiter),
            checked = sequence.limiterEnabled,
            enabled = !state.isDrafting,
            tag = TAG_AUDIO_LIMITER,
            onCheckedChange = { actions.setLimiterEnabled(it) }
        )
    }
}

/** The strip card: name, a fader-height body, a readout and a control row. */
@Composable
private fun MixerStripCard(
    name: String,
    selected: Boolean,
    tag: String,
    faderHeight: Dp,
    modifier: Modifier = Modifier,
    onSelect: (() -> Unit)? = null,
    body: @Composable () -> Unit,
    readout: @Composable () -> Unit,
    controls: @Composable RowScope.() -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    val ring by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "stripRing"
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .clip(shape)
            .background(
                if (selected) MaterialTheme.colorScheme.surfaceContainerHigh
                else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)
            )
            .border(2.dp, ring, shape)
            .semantics { this.selected = selected }
            .testTag(tag)
            .padding(horizontal = 6.dp, vertical = 8.dp)
    ) {
        // The select affordance lives on the name alone — a whole-card
        // clickable would merge every control below it into one button.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 36.dp)
                .clip(RoundedCornerShape(12.dp))
                .then(
                    if (onSelect != null) {
                        Modifier
                            .heightIn(min = 48.dp)
                            .clickable(role = Role.Button, onClickLabel = name, onClick = onSelect)
                    } else Modifier
                )
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(Modifier.width(48.dp).height(faderHeight)) { body() }
        readout()
        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
            content = controls
        )
    }
}

/** A dB readout that opens exact entry — a full 48 dp target. */
@Composable
private fun MixerGainReadout(
    text: String,
    enabled: Boolean,
    label: String,
    tag: String,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick)
            .testTag(tag)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

/** The strip's fader paint: a well, a level fill and a 4 dp handle line. */
@Composable
private fun MixerFaderPaint(fraction: Float, fill: Color, level: VideoLevels.Stereo?, meterTag: String?) {
    val handle = MaterialTheme.colorScheme.onSurface
    val well = MaterialTheme.colorScheme.surfaceContainerLowest
    val meter = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.45f)
    Canvas(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .then(if (meterTag != null) Modifier.testTag(meterTag) else Modifier)
    ) {
        drawRect(well)
        val top = size.height * (1f - fraction)
        drawRect(fill, topLeft = Offset(0f, top), size = Size(size.width, size.height - top))
        if (level != null) {
            // The live level rides the fill's edge; silent at rest.
            val barWidth = 4.dp.toPx()
            listOf(level.left, level.right).forEachIndexed { index, channel ->
                val h = size.height * channel.coerceIn(0f, 1f)
                drawRect(
                    meter,
                    topLeft = Offset(
                        size.width - (index + 1) * (barWidth + 2.dp.toPx()),
                        size.height - h
                    ),
                    size = Size(barWidth, h)
                )
            }
        }
        val lineHeight = 4.dp.toPx()
        drawRect(
            handle,
            topLeft = Offset(0f, (top - lineHeight / 2f).coerceIn(0f, size.height - lineHeight)),
            size = Size(size.width, lineHeight)
        )
    }
}

/**
 * Mute or Solo on a strip: tonal at rest with square inner joins, springing
 * to a soft 16 dp shape in its state colour when on.
 */
@Composable
private fun RowScope.MixerStateButton(
    text: String,
    label: String,
    checked: Boolean,
    container: Color,
    ink: Color,
    startCorner: Dp,
    endCorner: Dp,
    enabled: Boolean,
    tag: String,
    onClick: () -> Unit
) {
    val spec = spring<Dp>(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)
    val start by animateDpAsState(if (checked) 16.dp else startCorner, spec, label = "stateStart")
    val end by animateDpAsState(if (checked) 16.dp else endCorner, spec, label = "stateEnd")
    val background by animateColorAsState(
        if (checked) container else MaterialTheme.colorScheme.outlineVariant,
        label = "stateContainer"
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(start, end, end, start))
            .background(background)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics {
                contentDescription = label
                selected = checked
            }
            .testTag(tag)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (checked) ink else MaterialTheme.colorScheme.onSurface
        )
    }
}

/** One lane's strip — fader, meter, readout, Mute and Solo. */
@Composable
private fun MixerLaneStrip(
    track: AudioTrack,
    level: VideoLevels.Stereo,
    selected: Boolean,
    faderHeight: Dp,
    state: VideoEditorState,
    actions: VideoEditorActions,
    modifier: Modifier = Modifier
) {
    val latestActions by rememberUpdatedState(actions)
    var gainDialog by remember { mutableStateOf(false) }
    val enabled = !state.opening
    val fill = if (track.muted) MaterialTheme.colorScheme.outlineVariant
    else MaterialTheme.colorScheme.primary
    MixerStripCard(
        name = track.name,
        selected = selected,
        tag = TAG_AUDIO_CARD_PREFIX + track.id,
        faderHeight = faderHeight,
        modifier = modifier,
        onSelect = if (enabled) ({ actions.selectAudioTrack(track.id) }) else null,
        body = {
            AudioVerticalFader(
                value = track.gainDb,
                description = stringResource(Res.string.editor_audio_fader, track.name),
                enabled = enabled,
                interactionActive = state.isDrafting,
                identity = track.id,
                tag = TAG_AUDIO_FADER_PREFIX + track.id,
                onBegin = { latestActions.beginAudioEdit("Edit lane gain") },
                onUpdate = {
                    latestActions.updateAudioTrackEdit(track.id, AudioTrackEdit(gainDb = it))
                },
                onCommit = latestActions.commitAudioEdit,
                onCancel = latestActions.cancelAudioEdit,
                onApply = {
                    latestActions.applyAudioTrackEdit(
                        "Edit lane gain", track.id, AudioTrackEdit(gainDb = it)
                    )
                },
                modifier = Modifier.fillMaxSize(),
                paint = { fraction ->
                    MixerFaderPaint(fraction, fill, level, TAG_AUDIO_METER_PREFIX + track.id)
                }
            )
        },
        readout = {
            MixerGainReadout(
                text = "${formatDb(track.gainDb)} dB",
                enabled = enabled && !state.isDrafting,
                label = stringResource(Res.string.editor_audio_fader, track.name),
                tag = TAG_AUDIO_GAIN_VALUE_PREFIX + track.id,
                onClick = { gainDialog = true }
            )
        },
        controls = {
            MixerStateButton(
                text = "M",
                label = stringResource(
                    if (track.muted) Res.string.editor_audio_unmute
                    else Res.string.editor_audio_mute
                ),
                checked = track.muted,
                container = MaterialTheme.colorScheme.error,
                ink = MaterialTheme.colorScheme.onError,
                startCorner = 12.dp,
                endCorner = 4.dp,
                enabled = enabled && !state.isDrafting,
                tag = TAG_AUDIO_MUTE_PREFIX + track.id,
                onClick = {
                    actions.applyAudioTrackEdit(
                        if (track.muted) "Unmute lane" else "Mute lane",
                        track.id, AudioTrackEdit(muted = !track.muted)
                    )
                }
            )
            MixerStateButton(
                text = "S",
                label = stringResource(Res.string.editor_audio_solo),
                checked = track.solo,
                container = MaterialTheme.colorScheme.tertiaryContainer,
                ink = MaterialTheme.colorScheme.onTertiaryContainer,
                startCorner = 4.dp,
                endCorner = 12.dp,
                enabled = enabled && !state.isDrafting,
                tag = TAG_AUDIO_SOLO_PREFIX + track.id,
                onClick = {
                    actions.applyAudioTrackEdit(
                        if (track.solo) "Unsolo lane" else "Solo lane",
                        track.id, AudioTrackEdit(solo = !track.solo)
                    )
                }
            )
        }
    )
    if (gainDialog) {
        NumericEntryDialog(
            label = track.name,
            initialValue = track.gainDb,
            range = AUDIO_FADER_RANGE,
            onConfirm = { entered ->
                latestActions.applyAudioTrackEdit(
                    "Edit lane gain", track.id, AudioTrackEdit(gainDb = entered)
                )
                gainDialog = false
            },
            onDismiss = { gainDialog = false }
        )
    }
}

/** The virtual Main-lane input — a live meter and mute, at unity gain. */
@Composable
private fun MixerSourceStrip(
    state: VideoEditorState,
    actions: VideoEditorActions,
    faderHeight: Dp,
    modifier: Modifier = Modifier
) {
    val muted = state.sequence?.sourceSoundMuted == true
    MixerStripCard(
        name = stringResource(Res.string.editor_audio_source_sound),
        selected = false,
        tag = TAG_AUDIO_CARD_PREFIX + "sourceSound",
        faderHeight = faderHeight,
        modifier = modifier,
        body = {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            ) {
                AudioMeterPair(
                    level = state.levels.sourceSound,
                    vertical = true,
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(vertical = 8.dp)
                        .testTag(TAG_AUDIO_METER_PREFIX + "sourceSound")
                )
            }
        },
        readout = {
            Box(Modifier.heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = "0 dB",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        controls = {
            MixerStateButton(
                text = "M",
                label = stringResource(
                    if (muted) Res.string.editor_audio_unmute else Res.string.editor_audio_mute
                ),
                checked = muted,
                container = MaterialTheme.colorScheme.error,
                ink = MaterialTheme.colorScheme.onError,
                startCorner = 12.dp,
                endCorner = 12.dp,
                enabled = !state.isDrafting && !state.opening,
                tag = TAG_AUDIO_SOURCE_MUTE,
                onClick = { actions.setSourceSoundMuted(!muted) }
            )
        }
    )
}

/** The Master bus — the output fader in the tertiary role, no Mute or Solo. */
@Composable
private fun MixerMasterStrip(
    state: VideoEditorState,
    actions: VideoEditorActions,
    faderHeight: Dp,
    modifier: Modifier = Modifier
) {
    val latestActions by rememberUpdatedState(actions)
    var gainDialog by remember { mutableStateOf(false) }
    val masterGain = state.sequence?.masterGainDb ?: 0f
    val master = stringResource(Res.string.editor_audio_master)
    val fill = MaterialTheme.colorScheme.tertiary
    MixerStripCard(
        name = master,
        selected = false,
        tag = TAG_AUDIO_CARD_PREFIX + "master",
        faderHeight = faderHeight,
        modifier = modifier,
        body = {
            AudioVerticalFader(
                value = masterGain,
                description = stringResource(Res.string.editor_audio_fader, master),
                enabled = !state.opening,
                interactionActive = state.isDrafting,
                identity = "master",
                tag = TAG_AUDIO_MASTER_GAIN,
                onBegin = { latestActions.beginAudioEdit("Edit master gain") },
                onUpdate = { latestActions.updateMasterGain(it) },
                onCommit = latestActions.commitAudioEdit,
                onCancel = latestActions.cancelAudioEdit,
                onApply = { latestActions.setMasterGain(it) },
                modifier = Modifier.fillMaxSize(),
                paint = { fraction ->
                    MixerFaderPaint(fraction, fill, state.levels.master, null)
                }
            )
        },
        readout = {
            MixerGainReadout(
                text = "${formatDb(masterGain)} dB",
                enabled = !state.isDrafting,
                label = master,
                tag = TAG_AUDIO_GAIN_VALUE_PREFIX + "master",
                onClick = { gainDialog = true }
            )
        },
        controls = {}
    )
    if (gainDialog) {
        NumericEntryDialog(
            label = master,
            initialValue = masterGain,
            range = AUDIO_FADER_RANGE,
            onConfirm = { entered ->
                latestActions.setMasterGain(entered)
                gainDialog = false
            },
            onDismiss = { gainDialog = false }
        )
    }
}

// ---------------------------------------------------------------------------
// Channels & routing — the selected lane's stereo mapping (v-routing).
// ---------------------------------------------------------------------------

/**
 * The Routing panel (and the desktop mixer's right pane): the selected
 * lane's channel map — the lane picker, independent Left/Right gains, the
 * Link coupling and the Mono downmix. Routing is a clip-level recipe; the
 * panel writes it to every clip in the lane in one history step, so a lane
 * behaves like a channel strip.
 */
@Composable
internal fun VideoRoutingSurface(
    state: VideoEditorState,
    actions: VideoEditorActions,
    modifier: Modifier = Modifier
) {
    val sequence = state.sequence ?: return
    val tracks = sequence.audioTracks
    val latestActions by rememberUpdatedState(actions)
    Column(
        modifier.videoSurfaceInset(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val track = state.panelAudioTrack(tracks)
        if (track == null) {
            Text(
                text = stringResource(Res.string.editor_audio_no_track),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
            return@Column
        }
        val clip = track.clips.firstOrNull()
        val routing = clip?.routing ?: AudioRouting()
        val editable = !state.isDrafting && !state.opening && clip != null && !track.locked

        fun updateLane(edit: AudioRouting) {
            track.clips.forEach { c ->
                latestActions.updateAudioClipEdit(track.id, c.id, AudioClipEdit(routing = edit))
            }
        }
        /** One-step routing write over every clip in the lane. */
        fun applyRouting(edit: AudioRouting) {
            latestActions.beginAudioEdit("Edit routing")
            updateLane(edit)
            latestActions.commitAudioEdit()
        }
        fun withLeft(db: Float) = routing.copy(
            leftGainDb = db,
            rightGainDb = if (routing.linkChannels) db else routing.rightGainDb
        )
        fun withRight(db: Float) = routing.copy(
            rightGainDb = db,
            leftGainDb = if (routing.linkChannels) db else routing.leftGainDb
        )

        PublishVideoPanelReset(enabled = editable && routing != AudioRouting()) {
            applyRouting(AudioRouting())
        }

        AudioLaneSegments(
            tracks, track, !state.isDrafting, actions,
            modifier = Modifier.testTag(TAG_AUDIO_ROUTING)
        )
        val rulerEnabled = !state.opening && clip != null && !track.locked
        VideoKeyedParams(
            params = listOf(
                dbParam(
                    key = "left",
                    label = stringResource(Res.string.editor_video_limit_left),
                    icon = VideoGlyphs.Left,
                    value = routing.leftGainDb,
                    tag = "$TAG_AUDIO_ROUTING_LEFT.slider",
                    valueTag = TAG_AUDIO_ROUTING_LEFT,
                    chipTag = "$TAG_AUDIO_ROUTING_LEFT.chip",
                    enabled = rulerEnabled,
                    onBegin = { latestActions.beginAudioEdit("Edit routing") },
                    onUpdate = { updateLane(withLeft(it)) },
                    onCommit = latestActions.commitAudioEdit,
                    onCancel = latestActions.cancelAudioEdit,
                    onApply = { applyRouting(withLeft(it)) }
                ),
                dbParam(
                    key = "right",
                    label = stringResource(Res.string.editor_video_limit_right),
                    icon = VideoGlyphs.Right,
                    value = routing.rightGainDb,
                    tag = "$TAG_AUDIO_ROUTING_RIGHT.slider",
                    valueTag = TAG_AUDIO_ROUTING_RIGHT,
                    chipTag = "$TAG_AUDIO_ROUTING_RIGHT.chip",
                    enabled = rulerEnabled,
                    onBegin = { latestActions.beginAudioEdit("Edit routing") },
                    onUpdate = { updateLane(withRight(it)) },
                    onCommit = latestActions.commitAudioEdit,
                    onCancel = latestActions.cancelAudioEdit,
                    onApply = { applyRouting(withRight(it)) }
                )
            ),
            identity = "routing/${track.id}",
            interactionActive = state.isDrafting
        )
        VideoStudioSwitchRow(
            label = stringResource(Res.string.editor_video_link_left_right),
            checked = routing.linkChannels,
            enabled = editable,
            tag = TAG_AUDIO_ROUTING_LINK,
            onCheckedChange = { linked ->
                // Linking mirrors the left channel, matching the model's
                // documented coupling semantics.
                applyRouting(
                    routing.copy(
                        linkChannels = linked,
                        rightGainDb = if (linked) routing.leftGainDb else routing.rightGainDb
                    )
                )
            }
        )
        VideoStudioSwitchRow(
            label = stringResource(Res.string.editor_video_mix_to_mono),
            checked = routing.monoDownmix,
            enabled = editable,
            tag = TAG_AUDIO_ROUTING_MONO,
            onCheckedChange = { downmix -> applyRouting(routing.copy(monoDownmix = downmix)) }
        )
    }
}

// ---------------------------------------------------------------------------
// Timeline lanes — the A-lane strip every timeline shell shares.
// ---------------------------------------------------------------------------

/**
 * One audio lane inside a timeline: the lane's clips at their true
 * timeline offsets, each painting the real PCM-peak waveform windowed to
 * its source range under the source's display name, the fade envelope
 * and the effect strip — all overlays, so the lane keeps its height.
 * Taps select the clip for the clip-audio and routing surfaces; muted
 * lanes dim. A long-press picks a clip up to drag it in time, onto
 * another lane or onto the new-track row ([dragHost]); no trim grips.
 */
@Composable
internal fun VideoAudioLane(
    track: AudioTrack,
    trackIndex: Int,
    state: VideoEditorState,
    actions: VideoEditorActions,
    pixelsPerTick: Float,
    compact: Boolean = false,
    dragHost: ClipDragHost? = null,
    slotIndex: Int = -1,
    modifier: Modifier = Modifier
) {
    val sequence = state.sequence ?: return
    val density = LocalDensity.current
    val draggedId = dragHost?.drag?.value?.clipId
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(VideoStudio.LaneCorner))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .testTag(TAG_VIDEO_AUDIO_LANE_PREFIX + track.id)
    ) {
        val laneSelected = track.id == state.selectedAudioTrackId
        track.clips.forEachIndexed { clipIndex, clip ->
            val startX = clip.timelineStart.ticks * pixelsPerTick
            val widthPx =
                (clip.sourceRange.duration.ticks * pixelsPerTick)
                    .coerceAtLeast(1f)
            val widthDp = with(density) { widthPx.toDp() }
            val selected = clip.id == state.selectedAudioClipId
            val dimmed = clip.muted || track.muted
            val ringed = selected || (laneSelected && state.selectedAudioClipId == null)
            val clipShape = RoundedCornerShape(
                if (ringed) VideoStudio.ClipSelected else VideoStudio.AudioClip
            )
            val asset = sequence.audioSources
                .firstOrNull { it.id == clip.sourceId }
            val windowStart = asset?.let {
                clip.sourceRange.start.ticks.toFloat() / it.duration.ticks
            } ?: 0f
            val windowEnd = asset?.let {
                clip.sourceRange.endExclusive.ticks.toFloat() /
                    it.duration.ticks
            } ?: 1f
            val clipName = stringResource(
                Res.string.editor_audio_clip_a11y,
                clipIndex + 1,
                formatVideoTimecode(clip.sourceRange.duration),
                formatVideoTimecode(clip.sourceRange.start),
                formatVideoTimecode(clip.sourceRange.endExclusive)
            )
            Box(
                modifier = Modifier
                    .offset { IntOffset(startX.roundToInt(), 0) }
                    .width(widthDp)
                    .fillMaxHeight()
                    .padding(end = 2.dp)
                    .alpha(if (clip.id == draggedId) 0.4f else 1f)
                    .clip(clipShape)
                    .background(
                        if (dimmed) MaterialTheme.colorScheme.surfaceContainerHigh
                        else MaterialTheme.colorScheme.tertiaryContainer
                    )
                    .then(
                        if (ringed) {
                            Modifier.border(3.dp, MaterialTheme.colorScheme.tertiary, clipShape)
                        } else Modifier
                    )
                    .clipDragSource(
                        host = dragHost,
                        clipId = clip.id,
                        kind = if (track.locked) LaneDropKind.None else LaneDropKind.Audio,
                        slotIndex = slotIndex,
                        startTicks = clip.timelineStart.ticks,
                        durationTicks = clip.sourceRange.duration.ticks,
                        pixelsPerTick = pixelsPerTick,
                        ticksPerFrame = sequence.frameRate.ticksPerFrame,
                        enabled = true
                    )
                    .clickable(
                        onClickLabel = stringResource(
                            Res.string.editor_video_select_audio_clip,
                            clipIndex + 1
                        )
                    ) {
                        actions.selectAudioClip(clip.id)
                    }
                    .semantics {
                        this.selected = selected
                        contentDescription = clipName
                    }
                    .testTag(TAG_VIDEO_AUDIO_CLIP_PREFIX + clip.id)
            ) {
                AudioWaveform(
                    peaks = state.waveforms[clip.sourceId],
                    windowStart = windowStart,
                    windowEnd = windowEnd,
                    dimmed = dimmed,
                    filled = true,
                    modifier = Modifier.fillMaxSize()
                )
                // The overlays ride the waveform — the source name, the
                // fade envelope and the recipe badges never add height.
                if (widthDp >= 72.dp) {
                    VideoClipLabelChip(
                        text = state.audioSourceInfos[clip.sourceId]?.displayName
                            ?: clip.sourceId,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 10.dp, bottom = 3.dp, end = 4.dp)
                    )
                }
                FadeEnvelopeOverlay(
                    clip = clip,
                    modifier = Modifier.fillMaxSize()
                )
                TimelineEffectStrip(
                    labels = clip.timelineEffects(track),
                    compact = compact,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                        .testTag(TAG_VIDEO_TIMELINE_EFFECT_PREFIX + clip.id)
                )
            }
        }
    }
}
