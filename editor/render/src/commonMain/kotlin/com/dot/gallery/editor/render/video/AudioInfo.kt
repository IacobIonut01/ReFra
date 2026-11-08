package com.dot.gallery.editor.render.video

import com.dot.gallery.editor.core.model.MediaAsset

/**
 * Probed facts about one audio-only source: [asset] carries the model-level
 * identity, duration, native sample rate and channel count; [displayName]
 * is the human-readable label from the resolver.
 */
data class AudioInfo(
    val asset: MediaAsset.Audio,
    val displayName: String
) {
    init {
        require(displayName.isNotBlank()) { "displayName must be non-blank" }
    }
}
