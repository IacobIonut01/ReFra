package com.dot.gallery.editor.android

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import com.dot.gallery.editor.core.model.PixelSize
import com.dot.gallery.editor.core.model.VideoSequence
import com.dot.gallery.editor.core.model.VideoTrackKind
import com.dot.gallery.editor.core.time.EditorTime
import com.dot.gallery.editor.render.video.AudioInfo
import com.dot.gallery.editor.render.video.VideoExportResult
import com.dot.gallery.editor.render.video.VideoFrame
import com.dot.gallery.editor.render.video.VideoInfo
import com.dot.gallery.editor.render.video.VideoMediaAccess
import com.dot.gallery.editor.videocodec.CompositeAudioPlan
import com.dot.gallery.editor.videocodec.FfmpegExportVerification
import com.dot.gallery.editor.videocodec.FfmpegPcmReader
import com.dot.gallery.editor.videocodec.FfmpegPixelConverter
import com.dot.gallery.editor.videocodec.FfmpegProbe
import com.dot.gallery.editor.videocodec.FfmpegProbeResult
import com.dot.gallery.editor.videocodec.FfmpegVideoExporter
import com.dot.gallery.editor.videocodec.FfmpegVideoPlayback
import com.dot.gallery.editor.videocodec.FfmpegVideoReader
import com.dot.gallery.editor.videocodec.FfmpegWaveform
import com.dot.gallery.editor.videocodec.ResolvedVideoSource
import com.dot.gallery.editor.videocodec.SingleSourcePlan
import com.dot.gallery.editor.videocodec.VideoCodecEvents
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Android [VideoMediaAccess] over the packaged FFmpeg runtime: probes and
 * decodes through the shared video-codec readers, and exports a cut sequence
 * into `Movies/ReFra` via MediaStore.
 *
 * Export mirrors the photo contract: encode into a private
 * `cache/editor/exports` staging file, fsync, reopen-and-verify the streams,
 * then copy into a pending `MediaStore.Video` row — the `IS_PENDING` flip
 * and the `onPublished` receipt run under [NonCancellable] so a racing
 * cancel can never lose a verified result. The source is only ever read.
 * [onError] receives cleanup failures that must not be swallowed (a pending
 * row delete failing after the real error).
 */
class AndroidVideoMediaAccess(
    context: Context,
    private val onError: (Throwable) -> Unit,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : VideoMediaAccess {

    private val appContext = context.applicationContext
    private val resolver = AndroidVideoSourceResolver(appContext, onError)
    private val exporter = FfmpegVideoExporter(resolver)

    init {
        // Mirror hardware-decode fallbacks into the host's error channel
        // (Session logs in the app); selection explanations stay on the
        // library's java.util.logging default.
        val default = VideoCodecEvents.defaultListener
        VideoCodecEvents.listener = { message, failure ->
            default(message, failure)
            if (failure != null) onError(failure)
        }
    }

    // The resolver keeps one cached backing per reference; this keeps the
    // matching probe so a scrub pays one container open per source, not one
    // per frame. Bounded to the single in-scope source.
    private val probeLock = Any()
    private var cachedProbe: Pair<String, FfmpegProbeResult>? = null

    // Bounded waveform summaries keyed "reference#buckets" — a scrub or a
    // re-layout never re-decodes the file.
    private val waveformCache = LinkedHashMap<String, FloatArray>()

    /**
     * Playback engine bound to this access's resolver and an AudioTrack sink;
     * [onFocusLost] is invoked on any audio focus loss (permanent, transient
     * or duck) — the controller pauses.
     */
    fun createPlayback(
        workerDispatcher: CoroutineDispatcher,
        onFocusLost: () -> Unit
    ): FfmpegVideoPlayback = FfmpegVideoPlayback(
        resolver,
        { AndroidAudioTrackOutput(appContext, onError, onFocusLost) },
        workerDispatcher
    )

    override suspend fun probe(reference: String): VideoInfo =
        withContext(ioDispatcher) {
            resolver.resolve(reference).use { source ->
                probeCached(source, reference).info
            }
        }

    override suspend fun probeAudio(reference: String): AudioInfo =
        withContext(ioDispatcher) {
            resolver.resolve(reference).use { source ->
                FfmpegProbe.probeAudio(source, reference).info
            }
        }

    override suspend fun waveform(reference: String, buckets: Int): FloatArray =
        withContext(ioDispatcher) {
            val cacheKey = "$reference#$buckets"
            synchronized(probeLock) {
                waveformCache[cacheKey]?.let { return@withContext it }
            }
            val peaks = resolver.resolve(reference).use { source ->
                val probe = FfmpegProbe.probeAudio(source, reference)
                FfmpegPcmReader(source.path, probe.audioStreamIndex).use {
                    reader ->
                    FfmpegWaveform.peaks(
                        reader,
                        probe.info.asset.duration
                            .samplesAtOrAfter(FfmpegPcmReader.OUTPUT_RATE),
                        buckets
                    )
                }
            }
            synchronized(probeLock) {
                if (waveformCache.size >= MAX_WAVEFORM_CACHE) {
                    waveformCache.remove(waveformCache.keys.first())
                }
                waveformCache[cacheKey] = peaks
            }
            peaks
        }

    override suspend fun frame(
        reference: String,
        at: EditorTime,
        maxEdge: Int
    ): VideoFrame = withContext(ioDispatcher) {
        resolver.resolve(reference).use { source ->
            val probe = probeCached(source, reference)
            FfmpegPixelConverter(
                probe.colorspace, probe.colorRange, probe.rawSize.height
            ).use { conv ->
                FfmpegVideoReader(
                    source.path, conv, probe.info.asset.rotationDegrees,
                    probe.videoStreamIndex
                ).use { reader ->
                    reader.frameAt(at, maxEdge)
                }
            }
        }
    }

    /** Container probe, cached per reference for the resolver's lifetime. */
    private fun probeCached(
        source: ResolvedVideoSource,
        reference: String
    ): FfmpegProbeResult {
        synchronized(probeLock) {
            cachedProbe?.let { if (it.first == reference) return it.second }
        }
        val probed = FfmpegProbe.probe(source, reference)
        synchronized(probeLock) { cachedProbe = reference to probed }
        return probed
    }

    /** Retires the resolver's staged/cached backing for live leases to drain. */
    override fun close() {
        synchronized(probeLock) {
            cachedProbe = null
            waveformCache.clear()
        }
        resolver.close()
    }

    override suspend fun export(
        sequence: VideoSequence,
        onPublished: (VideoExportResult) -> Unit,
        onProgress: (Float) -> Unit
    ): VideoExportResult =
        EditorExportDispatch.video?.export(sequence, onPublished, onProgress)
            ?: exportNow(sequence, onPublished, onProgress)

    /** The real encode+publish, run inline — the worker path calls this. */
    suspend fun exportNow(
        sequence: VideoSequence,
        onPublished: (VideoExportResult) -> Unit,
        onProgress: (Float) -> Unit
    ): VideoExportResult = withContext(ioDispatcher) {
        require(sequence.sources.isNotEmpty()) { "sequence has no source" }
        // The exporter's own shape test decides the verification contract:
        // a single-source identity sequence verifies against the source's
        // raw streams; anything else wrote composited upright pixels on the
        // sequence canvas with Main-only audio.
        val eligible = SingleSourcePlan.isEligible(sequence)
        val baselineReference = if (eligible) {
            sequence.sources[0].reference
        } else {
            val mainSourceId = sequence.tracks
                .firstOrNull { it.kind == VideoTrackKind.Main }
                ?.clips?.firstOrNull()?.sourceId
            sequence.sources.firstOrNull { it.id == mainSourceId }
                ?.reference ?: sequence.sources[0].reference
        }
        resolver.resolve(baselineReference).use { baseline ->
            val sourceProbe = FfmpegProbe.probe(baseline, baselineReference)
            val rotation = if (eligible) {
                sourceProbe.info.asset.rotationDegrees
            } else {
                0
            }
            val raw = if (eligible) sourceProbe.rawSize else sequence.size
            val upright = if (!eligible) {
                sequence.size
            } else if (rotation % 180 != 0) {
                PixelSize(raw.height, raw.width)
            } else {
                raw
            }
            val expectAudio = if (eligible) {
                sourceProbe.info.hasAudio
            } else {
                // The mixer's audible-input set — the encoder opens a PCM
                // reader for exactly these, so an audio stream exists iff
                // the mix has something to say.
                CompositeAudioPlan.expectsAudio(sequence)
            }
            val dir = File(appContext.cacheDir, "editor/exports")
            if (!dir.exists() && !dir.mkdirs()) {
                throw IOException("cannot create staging directory $dir")
            }
            val staging = File(dir, "${UUID.randomUUID()}.tmp")
            try {
                val report = exporter.encode(sequence, staging) { progress ->
                    onProgress(progress * PROGRESS_ENCODE / 0.9f)
                }
                coroutineContext.ensureActive()
                FileOutputStream(staging, true).use { it.fd.sync() }
                resolver.resolve(staging.toURI().toString()).use { staged ->
                    FfmpegExportVerification.verify(
                        staged, raw, rotation, sequence.duration, expectAudio,
                        report.videoCodecId, report.slackTicks
                    )
                }
                onProgress(PROGRESS_VERIFIED)
                val result = publish(
                    sourceProbe.info.displayName, staging, upright,
                    sequence.duration, onPublished
                )
                onProgress(1f)
                result
            } finally {
                deleteStaging(staging)
            }
        }
    }

    private fun deleteStaging(staging: File) {
        try {
            if (staging.exists() && !staging.delete()) {
                onError(IOException("could not delete staging file $staging"))
            }
        } catch (cleanup: Throwable) {
            onError(IOException("could not delete staging file $staging", cleanup))
        }
    }

    private fun outputName(displayName: String): String {
        val base = displayName
            .substringBeforeLast('.')
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(40)
            .ifBlank { "video" }
        return "$base-edited-${UUID.randomUUID().toString().take(8)}.mp4"
    }

    /**
     * Copies the staged file into a pending `MediaStore.Video` row under
     * `Movies/ReFra`, then — inside one non-cancellable commit — flips
     * `IS_PENDING` off (the update must affect exactly one row), invokes the
     * [onPublished] receipt and returns the result. Any failure removes the
     * inserted row.
     */
    private suspend fun publish(
        displayName: String,
        staging: File,
        upright: PixelSize,
        duration: EditorTime,
        onPublished: (VideoExportResult) -> Unit
    ): VideoExportResult {
        val contentResolver = appContext.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, outputName(displayName))
            put(MediaStore.Video.Media.MIME_TYPE, MIME_MP4)
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/ReFra")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values
        ) ?: throw IOException("MediaStore insert returned no URI")
        try {
            contentResolver.openOutputStream(uri)?.use { output ->
                FileInputStream(staging).use { input ->
                    val buffer = ByteArray(COPY_BUFFER_BYTES)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                    }
                }
            } ?: throw IOException("cannot open $uri for writing")
            coroutineContext.ensureActive()
            return withContext(NonCancellable) {
                val published = ContentValues().apply {
                    put(MediaStore.Video.Media.IS_PENDING, 0)
                }
                if (contentResolver.update(uri, published, null, null) != 1) {
                    throw IOException("MediaStore publish failed for $uri")
                }
                val result = VideoExportResult(
                    reference = uri.toString(),
                    mimeType = MIME_MP4,
                    size = upright,
                    duration = duration
                )
                onPublished(result)
                result
            }
        } catch (t: Throwable) {
            withContext(NonCancellable) {
                try {
                    contentResolver.delete(uri, null, null)
                } catch (cleanup: Throwable) {
                    t.addSuppressed(cleanup)
                    onError(cleanup)
                }
            }
            throw t
        }
    }

    private companion object {
        const val MIME_MP4 = "video/mp4"
        const val COPY_BUFFER_BYTES = 65_536
        const val PROGRESS_ENCODE = 0.9f
        const val PROGRESS_VERIFIED = 0.95f
        const val MAX_WAVEFORM_CACHE = 16
    }
}
