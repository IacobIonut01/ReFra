package com.dot.gallery.editor.ui.video

/**
 * The semantic kind of one real recipe deviation — the badge's identity,
 * kept separate from its localized text so the model layer never
 * hardcodes English. [TimelineEffectStrip] maps each kind to its string
 * resource; nothing user-visible is ever an enum `.name`.
 */
internal enum class TimelineEffectKind {
    Transform, Fill, Crop, Colour, BlendOpacity, Muted,
    Gain, TrackGain, Fade, Routing, Eq, Duck
}
