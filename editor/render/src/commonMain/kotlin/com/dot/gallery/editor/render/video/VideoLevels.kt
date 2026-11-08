package com.dot.gallery.editor.render.video

/**
 * One published meter reading for the stereo mix: the virtual Main-lane
 * input, every audio lane in [com.dot.gallery.editor.core.model.VideoSequence.audioTracks]
 * order, and the master output — each as a stereo RMS pair in linear
 * amplitude (0 = silence, 1 = full scale). Playback emits one snapshot
 * per written audio block; a paused or silent engine emits nothing and
 * the meters idle at zero.
 */
data class VideoLevels(
    val sourceSound: Stereo = Stereo(),
    val lanes: List<Stereo> = emptyList(),
    val master: Stereo = Stereo()
) {
    /** Left/right root-mean-square amplitude for one channel pair. */
    data class Stereo(val left: Float = 0f, val right: Float = 0f)
}
