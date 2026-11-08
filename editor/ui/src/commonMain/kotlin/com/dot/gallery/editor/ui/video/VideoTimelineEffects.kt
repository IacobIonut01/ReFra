package com.dot.gallery.editor.ui.video

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.dot.gallery.editor.core.model.AudioClip
import com.dot.gallery.editor.core.model.AudioRouting
import com.dot.gallery.editor.core.model.AudioTrack
import com.dot.gallery.editor.core.model.ColorAdjustments
import com.dot.gallery.editor.core.model.PhotoBlendMode
import com.dot.gallery.editor.core.model.VideoClip
import com.dot.gallery.editor.core.model.VideoContentMode
import com.dot.gallery.editor.core.model.VideoLayerTransform
import com.dot.gallery.editor.ui.TAG_VIDEO_TIMELINE_FADE_PREFIX
import com.dot.gallery.editor.ui.blendModeTitle
import com.dot.gallery.editor.ui.generated.resources.Res
import com.dot.gallery.editor.ui.generated.resources.editor_fx_colour
import com.dot.gallery.editor.ui.generated.resources.editor_fx_compact
import com.dot.gallery.editor.ui.generated.resources.editor_fx_crop
import com.dot.gallery.editor.ui.generated.resources.editor_fx_duck
import com.dot.gallery.editor.ui.generated.resources.editor_fx_eq
import com.dot.gallery.editor.ui.generated.resources.editor_fx_fade
import com.dot.gallery.editor.ui.generated.resources.editor_fx_fill
import com.dot.gallery.editor.ui.generated.resources.editor_fx_gain
import com.dot.gallery.editor.ui.generated.resources.editor_fx_more
import com.dot.gallery.editor.ui.generated.resources.editor_fx_muted
import com.dot.gallery.editor.ui.generated.resources.editor_fx_routing
import com.dot.gallery.editor.ui.generated.resources.editor_fx_track_gain
import com.dot.gallery.editor.ui.generated.resources.editor_fx_transform
import com.dot.gallery.editor.ui.trimTrailingZeros
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource

/**
 * One real recipe deviation a timeline clip surfaces as a badge.
 * [value] carries the kind's non-localized payload — the signed dB text
 * for Gain/TrackGain, or `blend|percent` for BlendOpacity (the blend
 * segment is the enum's stable key; the strip resolves it through the
 * localized blend titles, never painting it raw). [destructive] marks
 * the labels that silence output (Muted) so they read as warnings rather
 * than decorations.
 */
internal data class TimelineEffectLabel(
    val kind: TimelineEffectKind,
    val value: String? = null,
    val destructive: Boolean = false
)

/**
 * The clip's visible recipe badges, derived only from real fields — a
 * non-neutral transform, a non-Fit content mode, colour adjustments,
 * a non-default blend/opacity pair and an audio mute. There is no speed
 * model yet, so no speed badge is ever invented.
 */
internal fun VideoClip.timelineEffects(): List<TimelineEffectLabel> {
    val labels = mutableListOf<TimelineEffectLabel>()
    if (transform != VideoLayerTransform()) {
        labels += TimelineEffectLabel(TimelineEffectKind.Transform)
    }
    if (contentMode != VideoContentMode.Fit) {
        labels += TimelineEffectLabel(
            if (contentMode == VideoContentMode.Fill) {
                TimelineEffectKind.Fill
            } else {
                TimelineEffectKind.Crop
            }
        )
    }
    if (adjustments != ColorAdjustments()) {
        labels += TimelineEffectLabel(TimelineEffectKind.Colour)
    }
    val blendKey = if (blendMode != PhotoBlendMode.Normal) {
        blendMode.name
    } else {
        ""
    }
    val opacityPart = if (opacity != 1f) {
        (opacity * 100f).roundToInt().toString()
    } else {
        ""
    }
    if (blendKey.isNotEmpty() || opacityPart.isNotEmpty()) {
        labels += TimelineEffectLabel(
            kind = TimelineEffectKind.BlendOpacity,
            value = "$blendKey|$opacityPart"
        )
    }
    if (audioMuted) {
        labels += TimelineEffectLabel(
            TimelineEffectKind.Muted, destructive = true
        )
    }
    return labels
}

/**
 * The audio clip's recipe badges: a clip- or lane-level mute, clip and
 * track gains, a fade pair, non-neutral routing, the lane's active EQ
 * and its ducking insert — every entry real metadata, nothing guessed.
 */
internal fun AudioClip.timelineEffects(
    track: AudioTrack
): List<TimelineEffectLabel> {
    val labels = mutableListOf<TimelineEffectLabel>()
    if (muted || track.muted) {
        labels += TimelineEffectLabel(
            TimelineEffectKind.Muted, destructive = true
        )
    }
    if (gainDb != 0f) {
        labels += TimelineEffectLabel(
            TimelineEffectKind.Gain, value = formatGain(gainDb)
        )
    }
    if (track.gainDb != 0f) {
        labels += TimelineEffectLabel(
            TimelineEffectKind.TrackGain, value = formatGain(track.gainDb)
        )
    }
    if (fadeIn.ticks > 0 || fadeOut.ticks > 0) {
        labels += TimelineEffectLabel(TimelineEffectKind.Fade)
    }
    if (routing != AudioRouting()) {
        labels += TimelineEffectLabel(TimelineEffectKind.Routing)
    }
    if (track.eq.active) {
        labels += TimelineEffectLabel(TimelineEffectKind.Eq)
    }
    if (track.ducking.enabled) {
        labels += TimelineEffectLabel(TimelineEffectKind.Duck)
    }
    return labels
}

/** Signed dB text — `+3`, `-3`, one decimal when the value needs it. */
private fun formatGain(db: Float): String {
    val sign = if (db > 0f) "+" else ""
    return "$sign${trimTrailingZeros(db)}"
}

/**
 * The badge's localized text — every kind resolves through resources and
 * payload through the shared blend titles and the dB formats. Enum
 * `.name` never reaches the user.
 */
@Composable
private fun timelineEffectText(label: TimelineEffectLabel): String =
    when (label.kind) {
        TimelineEffectKind.Transform ->
            stringResource(Res.string.editor_fx_transform)
        TimelineEffectKind.Fill ->
            stringResource(Res.string.editor_fx_fill)
        TimelineEffectKind.Crop ->
            stringResource(Res.string.editor_fx_crop)
        TimelineEffectKind.Colour ->
            stringResource(Res.string.editor_fx_colour)
        TimelineEffectKind.Muted ->
            stringResource(Res.string.editor_fx_muted)
        TimelineEffectKind.Gain ->
            stringResource(
                Res.string.editor_fx_gain, label.value.orEmpty()
            )
        TimelineEffectKind.TrackGain ->
            stringResource(
                Res.string.editor_fx_track_gain, label.value.orEmpty()
            )
        TimelineEffectKind.Fade ->
            stringResource(Res.string.editor_fx_fade)
        TimelineEffectKind.Routing ->
            stringResource(Res.string.editor_fx_routing)
        TimelineEffectKind.Eq ->
            stringResource(Res.string.editor_fx_eq)
        TimelineEffectKind.Duck ->
            stringResource(Res.string.editor_fx_duck)
        TimelineEffectKind.BlendOpacity -> {
            val blendKey = label.value?.substringBefore('|').orEmpty()
            val percent = label.value?.substringAfter('|', "").orEmpty()
            buildString {
                if (blendKey.isNotEmpty()) {
                    append(blendModeTitle(PhotoBlendMode.valueOf(blendKey)))
                }
                if (percent.isNotEmpty()) {
                    if (isNotEmpty()) append(' ')
                    append(percent).append('%')
                }
            }
        }
    }

/**
 * The clip's effect badge on its own dark scrim. Compact lanes paint the
 * short `fx` indicator (the lane has no room for words); wide lanes show
 * the first real label and `+N` for the rest. Empty recipe — nothing is
 * composed, so no tag ever appears for a neutral clip.
 */
@Composable
internal fun TimelineEffectStrip(
    labels: List<TimelineEffectLabel>,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    if (labels.isEmpty()) return
    val text = if (compact) {
        stringResource(Res.string.editor_fx_compact)
    } else {
        buildString {
            append(timelineEffectText(labels.first()))
            if (labels.size > 1) {
                append(' ')
                append(
                    stringResource(
                        Res.string.editor_fx_more, labels.size - 1
                    )
                )
            }
        }
    }
    val destructive = labels.any { it.destructive }
    Box(
        modifier = modifier
            .background(
                color = Color.Black.copy(alpha = 0.55f),
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 4.dp, vertical = 1.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (destructive) {
                MaterialTheme.colorScheme.error
            } else {
                Color.White
            },
            maxLines = 1
        )
    }
}

/**
 * The fade pair drawn as the clip's amplitude envelope — a line that
 * ramps up over the fade-in fraction, sustains, and ramps down over the
 * fade-out fraction, hugging the clip's top edge so it reads over the
 * waveform without adding height. No fades — nothing is composed.
 */
@Composable
internal fun FadeEnvelopeOverlay(
    clip: AudioClip,
    modifier: Modifier = Modifier
) {
    if (clip.fadeIn.ticks <= 0 && clip.fadeOut.ticks <= 0) return
    val color = MaterialTheme.colorScheme.primary
    Canvas(
        modifier = modifier
            .testTag(TAG_VIDEO_TIMELINE_FADE_PREFIX + clip.id)
    ) {
        val durationTicks = clip.sourceRange.duration.ticks.toFloat()
        if (durationTicks <= 0f) return@Canvas
        val inFraction =
            (clip.fadeIn.ticks / durationTicks).coerceIn(0f, 1f)
        val outFraction =
            (clip.fadeOut.ticks / durationTicks).coerceIn(0f, 1f)
        val inset = 1.dp.toPx()
        val top = inset
        val bottom = size.height - inset
        val upEnd = size.width * inFraction
        val downStart = size.width * (1f - outFraction)
        val path = Path().apply {
            moveTo(0f, if (inFraction > 0f) bottom else top)
            if (inFraction > 0f) lineTo(upEnd, top)
            lineTo(downStart.coerceAtLeast(upEnd), top)
            if (outFraction > 0f) lineTo(size.width, bottom)
        }
        drawPath(
            path = path,
            color = color.copy(alpha = 0.9f),
            style = Stroke(width = 1.5f.dp.toPx())
        )
    }
}
