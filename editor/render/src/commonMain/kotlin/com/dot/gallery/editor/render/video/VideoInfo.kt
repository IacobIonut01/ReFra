package com.dot.gallery.editor.render.video

import com.dot.gallery.editor.core.model.MediaAsset

/**
 * Probed facts about one video source. [asset] carries the model-level
 * identity, upright [MediaAsset.Video.size], duration, optional frame rate
 * and display rotation; [audioSampleRate]/[audioChannels] describe the
 * first audio stream, or are null for a silent source.
 */
data class VideoInfo(
    val asset: MediaAsset.Video,
    val displayName: String,
    val audioSampleRate: Int?,
    val audioChannels: Int?
) {
    val hasAudio: Boolean get() = asset.hasAudio

    init {
        require(displayName.isNotBlank()) { "displayName must be non-blank" }
        require((audioSampleRate == null) == (audioChannels == null)) {
            "audio rate and channels must be reported together or not at all"
        }
    }
}
