package com.dot.gallery.editor.desktop

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
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.coroutines.coroutineContext
import kotlin.io.path.nameWithoutExtension
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Desktop [VideoMediaAccess] over the packaged FFmpeg runtime: probes and
 * decodes through the shared video-codec readers, and exports a cut sequence
 * to a staged H.264/AAC MP4 next to the source (or into [outputDirectory]).
 *
 * Export mirrors the photo contract: encode into a `.refra-*.tmp` staging
 * file, fsync, reopen-and-verify the streams, then move it onto
 * `<base>-edited-<uuid>.mp4` and fire the publication receipt — move plus
 * `onPublished` under [NonCancellable] so a racing cancel can never lose a
 * verified result. The source file is only ever read. [onError] receives
 * cleanup failures that must not be swallowed.
 */
class DesktopVideoMediaAccess(
    private val outputDirectory: Path? = null,
    private val onError: (Throwable) -> Unit,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : VideoMediaAccess {

    private val resolver = DesktopVideoSourceResolver()
    private val exporter = FfmpegVideoExporter(resolver)

    // One container probe per reference — a scrub pays one open per source,
    // not one per frame. Bounded to the single in-scope source.
    private val probeLock = Any()
    private var cachedProbe: Pair<String, FfmpegProbeResult>? = null

    // Bounded waveform summaries keyed "reference#buckets" — a scrub or a
    // re-layout never re-decodes the file.
    private val waveformCache = LinkedHashMap<String, FloatArray>()

    /** Playback engine bound to this access's resolver and a JavaSound sink. */
    fun createPlayback(workerDispatcher: CoroutineDispatcher): FfmpegVideoPlayback =
        FfmpegVideoPlayback(resolver, ::JavaSoundAudioOutput, workerDispatcher)

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

    /** Container probe, cached per reference. */
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

    /** Stateless resolver — nothing is retained between calls. */
    override fun close() {
        synchronized(probeLock) {
            cachedProbe = null
            waveformCache.clear()
        }
    }

    override suspend fun export(
        sequence: VideoSequence,
        onPublished: (VideoExportResult) -> Unit,
        onProgress: (Float) -> Unit
    ): VideoExportResult = withContext(ioDispatcher) {
        require(sequence.sources.isNotEmpty()) { "sequence has no source" }
        // Same shape test the exporter uses: a single-source identity
        // sequence verifies against the source's raw streams; anything else
        // wrote composited upright pixels on the sequence canvas with
        // Main-track audio only. The Main clip's source still anchors the
        // staged output's directory and display name.
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
        // Baseline probe: expected stream facts for verification and the
        // result's upright dimensions.
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
            val sourcePath = Path.of(baseline.path)
            val directory = outputDirectory ?: sourcePath.toAbsolutePath().parent
                ?: throw IOException("source has no parent directory")
            if (!Files.isDirectory(directory)) {
                throw IOException("output directory does not exist: $directory")
            }
            val staging = directory.resolve(".refra-${UUID.randomUUID()}.tmp")
            var failure: Throwable? = null
            try {
                val report = exporter.encode(sequence, staging.toFile()) { progress ->
                    onProgress(progress * PROGRESS_ENCODE / 0.9f)
                }
                coroutineContext.ensureActive()
                FileOutputStream(staging.toFile(), true).use { it.fd.sync() }
                resolver.resolve(staging.toString()).use { staged ->
                    FfmpegExportVerification.verify(
                        staged, raw, rotation, sequence.duration, expectAudio,
                        report.videoCodecId, report.slackTicks
                    )
                }
                onProgress(PROGRESS_VERIFIED)
                val target = directory.resolve(
                    "${sanitizeBase(sourcePath)}-edited-" +
                        "${UUID.randomUUID().toString().take(8)}.mp4"
                )
                // Commit boundary: verified output moves and reports even if
                // the caller is cancelled right now.
                coroutineContext.ensureActive()
                val result = withContext(NonCancellable) {
                    Files.move(staging, target)
                    val published = VideoExportResult(
                        reference = target.toAbsolutePath().toUri().toString(),
                        mimeType = MIME_MP4,
                        size = upright,
                        duration = sequence.duration
                    )
                    onPublished(published)
                    published
                }
                onProgress(1f)
                result
            } catch (t: Throwable) {
                failure = t
                throw t
            } finally {
                try {
                    Files.deleteIfExists(staging)
                } catch (cleanup: Throwable) {
                    failure?.addSuppressed(cleanup)
                    onError(
                        IOException("could not delete staging file $staging", cleanup)
                    )
                }
            }
        }
    }

    private fun sanitizeBase(path: Path): String =
        path.nameWithoutExtension
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(40)
            .ifBlank { "video" }

    private companion object {
        const val MIME_MP4 = "video/mp4"
        const val PROGRESS_ENCODE = 0.9f
        const val PROGRESS_VERIFIED = 0.95f
        const val MAX_WAVEFORM_CACHE = 16
    }
}
