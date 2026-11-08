package com.dot.gallery.editor.ui.video

import com.dot.gallery.editor.core.model.AudioClip
import com.dot.gallery.editor.core.model.AudioDucking
import com.dot.gallery.editor.core.model.AudioEq
import com.dot.gallery.editor.core.model.AudioRouting
import com.dot.gallery.editor.core.model.AudioTrack
import com.dot.gallery.editor.core.model.ColorAdjustments
import com.dot.gallery.editor.core.model.PhotoBlendMode
import com.dot.gallery.editor.core.model.VideoClip
import com.dot.gallery.editor.core.model.VideoContentMode
import com.dot.gallery.editor.core.model.VideoLayerTransform
import com.dot.gallery.editor.core.time.EditorTime
import com.dot.gallery.editor.core.time.TimeRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf

/**
 * The badge model — kinds and payloads derive only from real recipe
 * fields; neutral clips compose nothing and nothing user-visible is an
 * enum `.name`.
 */
class VideoTimelineEffectsTest {

    private fun clip(
        transform: VideoLayerTransform = VideoLayerTransform(),
        contentMode: VideoContentMode = VideoContentMode.Fit,
        adjustments: ColorAdjustments = ColorAdjustments(),
        opacity: Float = 1f,
        blendMode: PhotoBlendMode = PhotoBlendMode.Normal,
        audioMuted: Boolean = false
    ) = VideoClip(
        id = "clip-1",
        sourceId = "source-1",
        timelineStart = EditorTime.Zero,
        sourceRange = TimeRange(
            EditorTime.Zero, EditorTime.fromMicroseconds(1_000_000)
        ),
        adjustments = adjustments,
        transform = transform,
        contentMode = contentMode,
        opacity = opacity,
        blendMode = blendMode,
        audioMuted = audioMuted
    )

    @Test
    fun aNeutralClipEarnsNoBadges() {
        assertEquals(emptyList(), clip().timelineEffects())
    }

    @Test
    fun videoKindsFollowRealFieldsInOrder() {
        val clip = clip(
            transform = VideoLayerTransform(scale = 2f),
            contentMode = VideoContentMode.Crop,
            adjustments = ColorAdjustments(exposureStops = 0.7f),
            opacity = 0.72f,
            blendMode = PhotoBlendMode.SoftLight,
            audioMuted = true
        )
        val labels = clip.timelineEffects()
        assertEquals(
            listOf(
                TimelineEffectKind.Transform,
                TimelineEffectKind.Crop,
                TimelineEffectKind.Colour,
                TimelineEffectKind.BlendOpacity,
                TimelineEffectKind.Muted
            ),
            labels.map { it.kind }
        )
        assertEquals("SoftLight|72", labels[3].value)
        assertTrue(labels.last().destructive)
    }

    @Test
    fun fillModeReadsFillAndOpacityAloneStillBadges() {
        val labels = clip(
            contentMode = VideoContentMode.Fill, opacity = 0.5f
        ).timelineEffects()
        assertEquals(
            listOf(TimelineEffectKind.Fill, TimelineEffectKind.BlendOpacity),
            labels.map { it.kind }
        )
        assertEquals("|50", labels[1].value)
    }

    @Test
    fun audioKindsCoverMuteGainFadeRoutingEqAndDuck() {
        val clip = AudioClip(
            id = "a-1",
            sourceId = "as-1",
            timelineStart = EditorTime.Zero,
            sourceRange = TimeRange(
                EditorTime.Zero, EditorTime.fromMicroseconds(1_000_000)
            ),
            gainDb = 3f,
            fadeIn = EditorTime.fromMicroseconds(100_000),
            routing = AudioRouting(monoDownmix = true)
        )
        val track = AudioTrack(
            id = "t-1",
            clips = persistentListOf(clip),
            muted = true,
            gainDb = -3f,
            eq = AudioEq(lowDb = 2f),
            ducking = AudioDucking(
                enabled = true,
                triggerTrackId = "t-2",
                reductionDb = -12f
            )
        )
        val labels = clip.timelineEffects(track)
        assertEquals(
            listOf(
                TimelineEffectKind.Muted,
                TimelineEffectKind.Gain,
                TimelineEffectKind.TrackGain,
                TimelineEffectKind.Fade,
                TimelineEffectKind.Routing,
                TimelineEffectKind.Eq,
                TimelineEffectKind.Duck
            ),
            labels.map { it.kind }
        )
        assertEquals("+3", labels[1].value)
        assertEquals("-3", labels[2].value)
        assertTrue(labels.first().destructive)
    }
}
