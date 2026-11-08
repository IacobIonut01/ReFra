package com.dot.gallery.editor.videocodec

import com.dot.gallery.editor.render.video.UnsupportedVideoException
import kotlinx.coroutines.CancellationException
import org.bytedeco.ffmpeg.global.avcodec.avcodec_configuration
import org.bytedeco.ffmpeg.global.avcodec.avcodec_find_encoder_by_name
import org.bytedeco.javacv.FFmpegFrameGrabber

/**
 * One-time capability gate for the packaged ByteDeco FFmpeg runtime. The
 * first video operation probes it; a missing native slice (32-bit ABIs are
 * not published by JavaCPP), a GPL build or a missing OpenH264/AAC encoder
 * becomes a typed [UnsupportedVideoException] — never an unsatisfied link
 * or a silent codec fallback.
 *
 * Only linkage/load failures are translated. Fatal errors that must never
 * be downgraded — [VirtualMachineError], [ThreadDeath],
 * [CancellationException] — are rethrown even when wrapped in a JavaCV
 * exception, so an OOM during native load is not misreported as a missing
 * 64-bit slice.
 */
object FfmpegSupport {

    /** Result of the codec audit for diagnostics and platform tests. */
    data class Audit(
        val gplEnabled: Boolean,
        val openh264: Boolean,
        val aac: Boolean
    )

    /**
     * True once the natives loaded and passed the codec audit; false only for
     * the typed "unavailable" outcome — fatal errors still propagate.
     */
    val available: Boolean by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        try {
            audited()
            true
        } catch (e: UnsupportedVideoException) {
            false
        }
    }

    /** Throws [UnsupportedVideoException] when the runtime is unusable. */
    fun require() {
        audited()
    }

    /** The cached audit — loading and configuration are probed exactly once. */
    fun audit(): Audit = audited()

    private val auditedResult: Audit by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        loadAndAudit()
    }

    private fun audited(): Audit = auditedResult

    private fun loadAndAudit(): Audit {
        try {
            FFmpegFrameGrabber.tryLoad()
        } catch (t: Throwable) {
            rethrowFatal(t)
            throw UnsupportedVideoException(
                "video codecs are unavailable on this device " +
                    "(the packaged FFmpeg runtime requires a 64-bit ABI)",
                t
            )
        }
        val result = try {
            readAudit()
        } catch (t: Throwable) {
            rethrowFatal(t)
            throw UnsupportedVideoException(
                "cannot read FFmpeg configuration", t
            )
        }
        if (result.gplEnabled) {
            throw UnsupportedVideoException(
                "packaged FFmpeg was built with --enable-gpl; refusing to use it"
            )
        }
        if (!result.openh264) {
            throw UnsupportedVideoException(
                "packaged FFmpeg lacks the libopenh264 encoder"
            )
        }
        if (!result.aac) {
            throw UnsupportedVideoException(
                "packaged FFmpeg lacks the native AAC encoder"
            )
        }
        return result
    }

    private fun readAudit(): Audit {
        val config = avcodec_configuration().getString()
        return Audit(
            gplEnabled = config.contains("--enable-gpl"),
            openh264 = avcodec_find_encoder_by_name("libopenh264") != null,
            aac = avcodec_find_encoder_by_name("aac") != null
        )
    }

    /** Rethrows a fatal error found anywhere in the cause chain. */
    private fun rethrowFatal(t: Throwable) {
        var cause: Throwable? = t
        while (cause != null) {
            when (cause) {
                is VirtualMachineError -> throw cause
                is ThreadDeath -> throw cause
                is CancellationException -> throw cause
            }
            cause = cause.cause
        }
    }
}
