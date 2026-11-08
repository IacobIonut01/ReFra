package com.dot.gallery.editor.core.model

import com.dot.gallery.editor.core.time.EditorTime
import com.dot.gallery.editor.core.time.TimeRange

/**
 * One placement of a video source on a track: [timelineStart] is where the clip
 * lands in sequence time, [sourceRange] is the media range it plays back. The
 * clip's timeline duration equals its source range duration; [timelineRange] is
 * computed eagerly at construction, so an overflowing placement throws here —
 * not later when the range is first read.
 *
 * [transform]/[opacity]/[blendMode]/[visible] are the visual-composite
 * recipe — meaningful on [VideoTrackKind.Overlay] clips and applied
 * uniformly to every visual clip, so a Main clip carrying a non-default
 * recipe is composited with it rather than silently flattened. Their
 * defaults are the legacy identity: full-canvas opaque Normal.
 *
 * [audioMuted] silences this clip's own embedded sound without touching
 * the virtual Main-lane input — the "detached" half of the
 * extract-audio operation, whose sound then lives on an [AudioTrack].
 * The sequence-wide [VideoSequence.sourceSoundMuted] still gates the
 * whole input on top of the per-clip flag.
 */
data class VideoClip(
    val id: String,
    val sourceId: String,
    val timelineStart: EditorTime,
    val sourceRange: TimeRange,
    val adjustments: ColorAdjustments = ColorAdjustments(),
    val transform: VideoLayerTransform = VideoLayerTransform(),
    val contentMode: VideoContentMode = VideoContentMode.Fit,
    val opacity: Float = 1f,
    val blendMode: PhotoBlendMode = PhotoBlendMode.Normal,
    val visible: Boolean = true,
    val audioMuted: Boolean = false
) {
    val timelineRange: TimeRange = TimeRange(timelineStart, sourceRange.duration)

    /**
     * True when the clip's visual recipe is the identity — the pre-layers
     * shape a plain decode reproduces exactly. Any deviation routes the
     * sequence through the compositor instead of being silently dropped.
     */
    val hasNeutralVisuals: Boolean
        get() = transform == VideoLayerTransform() &&
            contentMode == VideoContentMode.Fit &&
            opacity == 1f &&
            blendMode == PhotoBlendMode.Normal &&
            visible

    init {
        require(id.isNotBlank()) { "clip id must be non-blank" }
        require(sourceId.isNotBlank()) { "clip sourceId must be non-blank" }
        require(timelineStart.ticks >= 0L) {
            "timelineStart must be non-negative: ${timelineStart.ticks}"
        }
        require(opacity.isFinite() && opacity in 0f..1f) {
            "opacity must be in [0, 1]: $opacity"
        }
    }
}
