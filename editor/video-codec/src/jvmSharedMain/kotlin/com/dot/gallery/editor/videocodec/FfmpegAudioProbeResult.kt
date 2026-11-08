package com.dot.gallery.editor.videocodec

import com.dot.gallery.editor.render.video.AudioInfo

/**
 * What [FfmpegProbe.probeAudio] learned about one audio-bearing source:
 * the portable [info] plus the FFmpeg-selected audio stream index every
 * later grabber must pin before `start()`.
 */
class FfmpegAudioProbeResult internal constructor(
    val info: AudioInfo,
    val audioStreamIndex: Int
)
