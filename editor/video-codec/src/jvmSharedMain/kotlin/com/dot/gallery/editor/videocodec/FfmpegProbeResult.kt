package com.dot.gallery.editor.videocodec

import com.dot.gallery.editor.core.model.PixelSize
import com.dot.gallery.editor.render.video.VideoInfo

/**
 * What [FfmpegProbe] learned about one source: the portable [info] plus the
 * raw stream facts decoders/exporters need to reproduce the same colour and
 * stream selection — the FFmpeg-selected stream indices every later grabber
 * must pin before `start()`.
 */
class FfmpegProbeResult internal constructor(
    val info: VideoInfo,
    val rawSize: PixelSize,
    val colorspace: Int,
    val colorRange: Int,
    val colorPrimaries: Int,
    val colorTrc: Int,
    val videoCodecId: Int,
    val audioCodecId: Int?,
    val videoStreamIndex: Int,
    val audioStreamIndex: Int,
    /** FFmpeg codec profile, or -1/unknown when the stream does not say. */
    val videoProfile: Int = -1,
    /** Deepest component depth of the stream pixel format; 0 when unknown. */
    val videoBitDepth: Int = 0,
    /** Average frame rate from the container, or 0 when unstated. */
    val avgFps: Double = 0.0
)
