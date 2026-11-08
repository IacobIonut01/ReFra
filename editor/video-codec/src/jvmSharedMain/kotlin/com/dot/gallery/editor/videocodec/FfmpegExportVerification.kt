package com.dot.gallery.editor.videocodec

import com.dot.gallery.editor.core.model.PixelSize
import com.dot.gallery.editor.core.time.EDITOR_TICKS_PER_SECOND
import com.dot.gallery.editor.core.time.EditorTime
import java.io.IOException
import kotlin.math.abs
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_AAC
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_H264

/**
 * Re-opens a staged export and proves it matches what was encoded: H.264
 * video at the source's raw dimensions and display rotation, an AAC stream
 * exactly when the source had audio, and a container duration within
 * [DURATION_TOLERANCE_TICKS] of the sequence length (one output frame plus
 * one AAC packet — the only slack a muxer may legitimately add). Any
 * mismatch throws [IOException]: an unverified file is never published.
 */
object FfmpegExportVerification {

    /** One 30 fps frame + one 1024-sample AAC packet, in ticks. */
    val DURATION_TOLERANCE_TICKS: Long =
        EDITOR_TICKS_PER_SECOND / 30L +
            EDITOR_TICKS_PER_SECOND * 1024L / FfmpegPcmReader.OUTPUT_RATE

    fun verify(
        staged: ResolvedVideoSource,
        expectedRawSize: PixelSize,
        expectedRotation: Int,
        expectedDuration: EditorTime,
        expectAudio: Boolean,
        expectedVideoCodecId: Int = AV_CODEC_ID_H264,
        extraSlackTicks: Long = 0L
    ) {
        val probe = FfmpegProbe.probe(staged, staged.path)
        val problems = ArrayList<String>(4)
        if (probe.videoCodecId != expectedVideoCodecId) {
            problems += "video codec ${probe.videoCodecId} " +
                "!= expected $expectedVideoCodecId"
        }
        if (expectAudio != (probe.audioCodecId != null)) {
            problems += "audio presence mismatch (expected $expectAudio)"
        } else if (expectAudio && probe.audioCodecId != AV_CODEC_ID_AAC) {
            problems += "audio codec ${probe.audioCodecId} is not AAC"
        }
        if (probe.rawSize != expectedRawSize) {
            problems += "size ${probe.rawSize} != expected $expectedRawSize"
        }
        if (probe.info.asset.rotationDegrees != expectedRotation) {
            problems += "rotation ${probe.info.asset.rotationDegrees} " +
                "!= expected $expectedRotation"
        }
        // Remux splices can add up to [extraSlackTicks] of held-frame time;
        // the file is only allowed to run long, never short.
        val drift = probe.info.asset.duration.ticks - expectedDuration.ticks
        if (drift < -DURATION_TOLERANCE_TICKS ||
            drift > DURATION_TOLERANCE_TICKS + extraSlackTicks
        ) {
            problems += "duration ${probe.info.asset.duration} differs by " +
                "$drift ticks " +
                "(allowed -$DURATION_TOLERANCE_TICKS .. " +
                "${DURATION_TOLERANCE_TICKS + extraSlackTicks})"
        }
        if (problems.isNotEmpty()) {
            throw IOException(
                "staged export failed verification: ${problems.joinToString("; ")}"
            )
        }
    }
}
