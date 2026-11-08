package com.dot.gallery.editor.ui.video

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dot.gallery.editor.core.model.AudioDucking
import com.dot.gallery.editor.core.model.AudioEq
import com.dot.gallery.editor.core.model.AudioTrack
import com.dot.gallery.editor.render.video.AudioEqChain
import com.dot.gallery.editor.render.video.AudioTrackEdit
import com.dot.gallery.editor.render.video.VideoEditorState
import com.dot.gallery.editor.ui.generated.resources.Res
import com.dot.gallery.editor.ui.generated.resources.editor_ducking_attack
import com.dot.gallery.editor.ui.generated.resources.editor_ducking_no_lanes
import com.dot.gallery.editor.ui.generated.resources.editor_ducking_preview
import com.dot.gallery.editor.ui.generated.resources.editor_ducking_reduction
import com.dot.gallery.editor.ui.generated.resources.editor_ducking_release
import com.dot.gallery.editor.ui.generated.resources.editor_eq_bypass
import com.dot.gallery.editor.ui.generated.resources.editor_eq_flat
import com.dot.gallery.editor.ui.generated.resources.editor_eq_high
import com.dot.gallery.editor.ui.generated.resources.editor_eq_highpass
import com.dot.gallery.editor.ui.generated.resources.editor_eq_low
import com.dot.gallery.editor.ui.generated.resources.editor_eq_mid
import com.dot.gallery.editor.ui.generated.resources.editor_eq_no_track
import com.dot.gallery.editor.ui.generated.resources.editor_eq_voice_clarity
import com.dot.gallery.editor.ui.generated.resources.editor_video_duck_enable
import com.dot.gallery.editor.ui.generated.resources.editor_video_duck_listen
import com.dot.gallery.editor.ui.generated.resources.editor_video_duck_listen_sub
import com.dot.gallery.editor.ui.generated.resources.editor_video_eq_axis_end
import com.dot.gallery.editor.ui.generated.resources.editor_video_eq_axis_start
import com.dot.gallery.editor.ui.generated.resources.editor_video_eq_curve
import com.dot.gallery.editor.ui.generated.resources.editor_video_eq_high_label
import com.dot.gallery.editor.ui.generated.resources.editor_video_eq_low_label
import com.dot.gallery.editor.ui.generated.resources.editor_video_eq_mid_label
import com.dot.gallery.editor.ui.generated.resources.editor_video_eq_off
import com.dot.gallery.editor.ui.generated.resources.editor_video_pause
import com.dot.gallery.editor.ui.trimTrailingZeros
import kotlin.math.pow
import org.jetbrains.compose.resources.stringResource

internal const val TAG_DUCKING_TARGET = "videoEditor.ducking.target"
internal const val TAG_DUCKING_TRIGGER = "videoEditor.ducking.trigger"
internal const val TAG_DUCKING_ENABLE = "videoEditor.ducking.enable"
internal const val TAG_DUCKING_REDUCTION = "videoEditor.ducking.reduction"
internal const val TAG_DUCKING_ATTACK = "videoEditor.ducking.attack"
internal const val TAG_DUCKING_RELEASE = "videoEditor.ducking.release"
internal const val TAG_DUCKING_PREVIEW = "videoEditor.ducking.preview"
internal const val TAG_EQ_TRACK = "videoEditor.eq.track"
internal const val TAG_EQ_CURVE = "videoEditor.eq.curve"
internal const val TAG_EQ_PRESET_FLAT = "videoEditor.eq.preset.flat"
internal const val TAG_EQ_PRESET_VOICE = "videoEditor.eq.preset.voice"
internal const val TAG_EQ_LOW = "videoEditor.eq.low"
internal const val TAG_EQ_MID = "videoEditor.eq.mid"
internal const val TAG_EQ_HIGH = "videoEditor.eq.high"
internal const val TAG_EQ_HIGHPASS = "videoEditor.eq.highpass"
internal const val TAG_EQ_BYPASS = "videoEditor.eq.bypass"

private val EQ_RANGE = AudioEq.MIN_BAND_DB..AudioEq.MAX_BAND_DB
private val EQ_HP_RANGE = 0f..AudioEq.MAX_HIGH_PASS_HZ
private val DUCK_RANGE = -40f..0f
private val ATTACK_RANGE = 5f..1000f
private val RELEASE_RANGE = 10f..3000f
private const val EQ_CURVE_POINTS = 96

/** The Voice-clarity one-shot — presence lift over a 90 Hz rumble cut. */
private val EQ_PRESET_VOICE = AudioEq(
    lowDb = -2f, midDb = 1.5f, highDb = 4f, highPassHz = 90f
)

/** "80 ms", or whole seconds once it reaches them — the ruler's limits. */
private fun formatMs(ms: Float): String =
    if (ms >= 1000f) "${trimTrailingZeros(ms / 1000f)} s" else "${trimTrailingZeros(ms)} ms"

/** A parameter of one lane written through the track-edit draft seam. */
private fun VideoEditorActions.laneParam(
    track: AudioTrack,
    historyLabel: String,
    key: String,
    label: String,
    icon: ImageVector,
    value: Float,
    display: String,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    limitStart: String,
    limitEnd: String,
    sliderTag: String,
    valueTag: String,
    chipTag: String,
    default: Float,
    enabled: Boolean,
    edit: (Float) -> AudioTrackEdit
) = VideoParam(
    key = key, label = label, icon = icon, value = value, display = display,
    range = range, step = step, limitStart = limitStart, limitEnd = limitEnd,
    sliderTag = sliderTag, valueTag = valueTag, chipTag = chipTag,
    default = default, enabled = enabled,
    onBegin = { beginAudioEdit(historyLabel) },
    onUpdate = { updateAudioTrackEdit(track.id, edit(it)) },
    onCommit = commitAudioEdit,
    onCancel = cancelAudioEdit,
    onApply = { applyAudioTrackEdit(historyLabel, track.id, edit(it)) }
)

// ---------------------------------------------------------------------------
// Ducking — lane-to-lane sidechain (v-ducking).
// ---------------------------------------------------------------------------

/**
 * The Ducking panel: the lane being ducked, the lanes it can listen to as
 * option rows, the Reduction, Attack and Release rulers, the enable switch
 * and a live Preview that plays the real mix (draft edits reach playback
 * through the same sequence). Every write is a history step through the
 * track-edit draft contract.
 */
@Composable
internal fun VideoDuckingSurface(
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
        if (tracks.size < 2 || track == null) {
            Text(
                text = stringResource(Res.string.editor_ducking_no_lanes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
            return@Column
        }
        val ducking = track.ducking
        val editable = !state.isDrafting && !state.opening && !track.locked
        val others = tracks.filter { it.id != track.id }

        fun applyDucking(next: AudioDucking, label: String = "Edit ducking") {
            latestActions.applyAudioTrackEdit(label, track.id, AudioTrackEdit(ducking = next))
        }

        val defaults = AudioDucking()
        val recipeChanged = ducking.reductionDb != defaults.reductionDb ||
            ducking.attackMs != defaults.attackMs || ducking.releaseMs != defaults.releaseMs
        PublishVideoPanelReset(enabled = editable && recipeChanged) {
            applyDucking(
                ducking.copy(
                    reductionDb = defaults.reductionDb,
                    attackMs = defaults.attackMs,
                    releaseMs = defaults.releaseMs
                ),
                "Reset ducking"
            )
        }

        AudioLaneSegments(
            tracks, track, !state.isDrafting, actions,
            modifier = Modifier.testTag(TAG_DUCKING_TARGET)
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.testTag(TAG_DUCKING_TRIGGER)
        ) {
            others.forEach { other ->
                VideoOptionRow(
                    title = stringResource(Res.string.editor_video_duck_listen, other.name),
                    supporting = stringResource(
                        Res.string.editor_video_duck_listen_sub, track.name
                    ),
                    icon = VideoGlyphs.Mic,
                    selected = ducking.triggerTrackId == other.id ||
                        (ducking.triggerTrackId.isEmpty() && other.id == others.first().id),
                    enabled = editable && ducking.enabled,
                    onClick = {
                        applyDucking(
                            ducking.copy(triggerTrackId = other.id), "Edit ducking trigger"
                        )
                    }
                )
            }
        }
        val rulers = !state.opening && !track.locked && ducking.enabled
        VideoKeyedParams(
            params = listOf(
                actions.laneParam(
                    track, "Edit ducking", "reduction",
                    stringResource(Res.string.editor_ducking_reduction), VideoGlyphs.Ducking,
                    ducking.reductionDb, "${formatDb(ducking.reductionDb)} dB",
                    DUCK_RANGE, 0.5f, "−40 dB", "0 dB",
                    TAG_DUCKING_REDUCTION, "$TAG_DUCKING_REDUCTION.value",
                    "$TAG_DUCKING_REDUCTION.chip", defaults.reductionDb, rulers
                ) { AudioTrackEdit(ducking = ducking.copy(reductionDb = it)) },
                actions.laneParam(
                    track, "Edit ducking", "attack",
                    stringResource(Res.string.editor_ducking_attack), VideoGlyphs.FadeOut,
                    ducking.attackMs, formatMs(ducking.attackMs),
                    ATTACK_RANGE, 5f, formatMs(ATTACK_RANGE.start), formatMs(ATTACK_RANGE.endInclusive),
                    "$TAG_DUCKING_ATTACK.slider", TAG_DUCKING_ATTACK,
                    "$TAG_DUCKING_ATTACK.chip", defaults.attackMs, rulers
                ) { AudioTrackEdit(ducking = ducking.copy(attackMs = it)) },
                actions.laneParam(
                    track, "Edit ducking", "release",
                    stringResource(Res.string.editor_ducking_release), VideoGlyphs.FadeIn,
                    ducking.releaseMs, formatMs(ducking.releaseMs),
                    RELEASE_RANGE, 10f, formatMs(RELEASE_RANGE.start), formatMs(RELEASE_RANGE.endInclusive),
                    "$TAG_DUCKING_RELEASE.slider", TAG_DUCKING_RELEASE,
                    "$TAG_DUCKING_RELEASE.chip", defaults.releaseMs, rulers
                ) { AudioTrackEdit(ducking = ducking.copy(releaseMs = it)) }
            ),
            identity = "ducking/${track.id}",
            interactionActive = state.isDrafting
        )
        VideoStudioSwitchRow(
            label = stringResource(Res.string.editor_video_duck_enable),
            checked = ducking.enabled,
            enabled = editable,
            tag = TAG_DUCKING_ENABLE,
            onCheckedChange = { on ->
                // Enabling without a trigger adopts the first other lane — a
                // duck with no sidechain is the model's neutral "off" anyway.
                applyDucking(
                    ducking.copy(
                        enabled = on,
                        triggerTrackId = if (on && ducking.triggerTrackId.isEmpty()) {
                            others.first().id
                        } else {
                            ducking.triggerTrackId
                        }
                    ),
                    if (on) "Enable ducking" else "Disable ducking"
                )
            }
        )
        VideoTonalAction(
            label = stringResource(
                if (state.playing) Res.string.editor_video_pause
                else Res.string.editor_ducking_preview
            ),
            icon = if (state.playing) VideoGlyphs.Pause else VideoGlyphs.Play,
            onClick = { if (state.playing) actions.pause() else actions.play() },
            enabled = !state.opening,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TAG_DUCKING_PREVIEW)
        )
    }
}

// ---------------------------------------------------------------------------
// EQ — three-band stack + high-pass per lane (v-eq).
// ---------------------------------------------------------------------------

/**
 * The EQ panel: the selected lane's tone stack — the real RBJ response
 * curve drawn from the mixer's own chain, the Low, Mid, High and High-pass
 * rulers, the honest Bypass that keeps the stored values, and the Flat and
 * Voice-clarity presets that write the whole recipe in one step.
 */
@Composable
internal fun VideoEqSurface(
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
                text = stringResource(Res.string.editor_eq_no_track),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
            return@Column
        }
        val eq = track.eq
        val editable = !state.isDrafting && !state.opening && !track.locked

        fun applyEq(next: AudioEq, label: String = "Edit EQ") {
            latestActions.applyAudioTrackEdit(label, track.id, AudioTrackEdit(eq = next))
        }

        PublishVideoPanelReset(enabled = editable && eq != AudioEq()) {
            applyEq(AudioEq(), "Reset EQ")
        }

        AudioLaneSegments(
            tracks, track, !state.isDrafting, actions,
            modifier = Modifier.testTag(TAG_EQ_TRACK)
        )
        EqResponseCurve(eq = eq)
        val rulers = !state.opening && !track.locked && !eq.bypass
        val bandParam: @Composable (
            key: String,
            label: String,
            icon: ImageVector,
            value: Float,
            tag: String,
            edit: (Float) -> AudioEq
        ) -> VideoParam = { key, label, icon, value, tag, edit ->
            actions.laneParam(
                track, "Edit EQ", key, label, icon, value, "${formatDb(value)} dB",
                EQ_RANGE, 0.5f, "−12 dB", "+12 dB",
                "$tag.slider", tag, "$tag.chip", 0f, rulers
            ) { AudioTrackEdit(eq = edit(it)) }
        }
        val off = stringResource(Res.string.editor_video_eq_off)
        VideoKeyedParams(
            params = listOf(
                bandParam(
                    "low", stringResource(Res.string.editor_eq_low), VideoGlyphs.Low,
                    eq.lowDb, TAG_EQ_LOW
                ) { eq.copy(lowDb = it) },
                bandParam(
                    "mid", stringResource(Res.string.editor_eq_mid), VideoGlyphs.Mid,
                    eq.midDb, TAG_EQ_MID
                ) { eq.copy(midDb = it) },
                bandParam(
                    "high", stringResource(Res.string.editor_eq_high), VideoGlyphs.MusicNote,
                    eq.highDb, TAG_EQ_HIGH
                ) { eq.copy(highDb = it) },
                actions.laneParam(
                    track, "Edit EQ", "highpass",
                    stringResource(Res.string.editor_eq_highpass), VideoGlyphs.HighPass,
                    eq.highPassHz,
                    if (eq.highPassHz > 0f) "${trimTrailingZeros(eq.highPassHz)} Hz" else off,
                    EQ_HP_RANGE, 5f, off, "${trimTrailingZeros(EQ_HP_RANGE.endInclusive)} Hz",
                    "$TAG_EQ_HIGHPASS.slider", TAG_EQ_HIGHPASS, "$TAG_EQ_HIGHPASS.chip",
                    0f, rulers
                ) { AudioTrackEdit(eq = eq.copy(highPassHz = it)) }
            ),
            identity = "eq/${track.id}",
            interactionActive = state.isDrafting
        )
        VideoStudioSwitchRow(
            label = stringResource(Res.string.editor_eq_bypass),
            checked = eq.bypass,
            enabled = editable,
            tag = TAG_EQ_BYPASS,
            onCheckedChange = { bypassed ->
                applyEq(eq.copy(bypass = bypassed), if (bypassed) "Bypass EQ" else "Enable EQ")
            }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(VideoStudio.GroupGap)) {
            VideoTonalAction(
                label = stringResource(Res.string.editor_eq_flat),
                onClick = { applyEq(AudioEq().copy(bypass = eq.bypass), "Apply EQ preset") },
                enabled = editable,
                modifier = Modifier
                    .weight(1f)
                    .semantics { selected = eq.copy(bypass = false) == AudioEq() }
                    .testTag(TAG_EQ_PRESET_FLAT)
            )
            VideoTonalAction(
                label = stringResource(Res.string.editor_eq_voice_clarity),
                onClick = { applyEq(EQ_PRESET_VOICE.copy(bypass = eq.bypass), "Apply EQ preset") },
                enabled = editable,
                modifier = Modifier
                    .weight(1f)
                    .semantics { selected = eq.copy(bypass = false) == EQ_PRESET_VOICE }
                    .testTag(TAG_EQ_PRESET_VOICE)
            )
        }
    }
}

/**
 * The EQ card's response trace: the same RBJ coefficients the mixer runs,
 * sampled as dB over the log-frequency axis, as a filled area under a line.
 * A bypassed chain draws the flat line in the outline role — what you see
 * is literally what the export hears.
 */
@Composable
private fun EqResponseCurve(eq: AudioEq, modifier: Modifier = Modifier) {
    val curve = remember(eq) {
        val chain = AudioEqChain(eq)
        FloatArray(EQ_CURVE_POINTS) { index ->
            // Log-spaced 20 Hz–20 kHz — the axis a shelf bell reads on.
            val fraction = index.toFloat() / (EQ_CURVE_POINTS - 1)
            val hz = 20f * 1000.0.pow(fraction.toDouble()).toFloat()
            chain.responseDb(hz)
        }
    }
    val lineColor = if (eq.bypass) MaterialTheme.colorScheme.outline
    else MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val description = stringResource(Res.string.editor_video_eq_curve)
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .semantics { contentDescription = description }
            .testTag(TAG_EQ_CURVE)
            .padding(8.dp)
    ) {
        Canvas(Modifier.fillMaxWidth().height(72.dp)) {
            val midY = size.height / 2f
            for (index in 1..3) {
                val x = size.width * index / 4f
                drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), 1f)
            }
            drawLine(gridColor, Offset(0f, midY), Offset(size.width, midY), 1f)
            val line = Path()
            curve.forEachIndexed { index, db ->
                val x = size.width * index / (curve.size - 1)
                val shown = if (eq.bypass) 0f else db
                val y = midY - (shown / 12f).coerceIn(-1f, 1f) * midY
                if (index == 0) line.moveTo(x, y) else line.lineTo(x, y)
            }
            val area = Path().apply {
                addPath(line)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(area, color = lineColor.copy(alpha = 0.18f))
            drawPath(line, color = lineColor, style = Stroke(width = 2.5.dp.toPx()))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf(
                Res.string.editor_video_eq_axis_start,
                Res.string.editor_video_eq_low_label,
                Res.string.editor_video_eq_mid_label,
                Res.string.editor_video_eq_high_label,
                Res.string.editor_video_eq_axis_end
            ).forEach { label ->
                Text(
                    text = stringResource(label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}
