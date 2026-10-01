package com.dot.gallery.feature_node.presentation.frameextract

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorSpace
import android.graphics.Matrix
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import com.dot.gallery.feature_node.presentation.mediaview.components.video.isDolbyVisionCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.io.IOException
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

class FrameDecodeException(message: String) : IOException(message)

class FrameDecoderSession(
    private val context: Context,
    private val sourceUri: Uri,
    private val localFile: File? = null,
) : Closeable {
    private val mutex = Mutex()
    private val retriever = MediaMetadataRetriever()
    private val extractor = MediaExtractor()
    private val previewCache = object : LruCache<String, Bitmap>(PREVIEW_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }
    private var videoTrackIndex = -1
    private var sourceWidth = 0
    private var sourceHeight = 0
    private var closed = false

    lateinit var metadata: FrameVideoMetadata
        private set
    lateinit var timeline: FrameTimeline
        private set

    suspend fun prepare(): FrameVideoMetadata = withContext(Dispatchers.IO) {
        mutex.withLock {
            checkOpen()
            setRetrieverDataSource()
            setExtractorDataSource()
            videoTrackIndex = findVideoTrack()
            if (videoTrackIndex < 0) throw FrameDecodeException("Unsupported codec")
            extractor.selectTrack(videoTrackIndex)
            val format = extractor.getTrackFormat(videoTrackIndex)
            val durationUs = retriever.metadataLong(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.times(1000L)
                ?: format.longOrNull(MediaFormat.KEY_DURATION)
                ?: 0L
            val frameCount = retriever.metadataInt(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)
                ?.takeIf { it > 0 }
            val captureFrameRate = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                ?.toFloatOrNull()
                ?.takeIf { it > 0f }
            val frameRate = captureFrameRate
                ?: format.floatOrNull(MediaFormat.KEY_FRAME_RATE)?.takeIf { it > 0f }
            val width = retriever.metadataInt(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?: format.intOrNull(MediaFormat.KEY_WIDTH)
                ?: 0
            val height = retriever.metadataInt(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?: format.intOrNull(MediaFormat.KEY_HEIGHT)
                ?: 0
            val rotation = retriever.metadataInt(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?: format.intOrNull(MediaFormat.KEY_ROTATION)
                ?: 0
            if (durationUs <= 0L || width <= 0 || height <= 0) {
                throw FrameDecodeException("Video metadata is unavailable")
            }
            sourceWidth = width
            sourceHeight = height
            val expectedFrames = frameRate?.let { durationUs / 1_000_000.0 * it }
            val constant = frameCount != null && captureFrameRate != null && expectedFrames != null &&
                abs(frameCount - expectedFrames) <= maxOf(2.0, expectedFrames * 0.05)
            val colorTransfer = format.intOrNull(MediaFormat.KEY_COLOR_TRANSFER)
            val hdr = colorTransfer == MediaFormat.COLOR_TRANSFER_ST2084 ||
                colorTransfer == MediaFormat.COLOR_TRANSFER_HLG
            // Dolby Vision tracks surface as video/dolby-vision (or a dvhe/dvh1/dvav codec
            // string on the container MIME) — flagged for metadata only; extracted frames are
            // still tone-mapped to sRGB by normalize().
            val dolbyVision = isDolbyVisionCodec(
                format.stringOrNull(MediaFormat.KEY_MIME),
                format.stringOrNull(MediaFormat.KEY_CODECS_STRING)
            )
            metadata = FrameVideoMetadata(
                durationUs = durationUs,
                frameCount = frameCount,
                frameRate = frameRate,
                width = if (rotation == 90 || rotation == 270) height else width,
                height = if (rotation == 90 || rotation == 270) width else height,
                rotationDegrees = rotation,
                isConstantFrameRate = constant,
                isHdr = hdr || dolbyVision,
                isDolbyVision = dolbyVision,
            )
            timeline = if (constant) {
                FrameTimeline.constant(durationUs, frameCount, frameRate)
            } else {
                FrameTimeline.unknown(durationUs, frameRate)
            }
            metadata
        }
    }

    suspend fun resolveInitial(preferredTimeUs: Long, initialPositionMs: Long?): FrameIdentity =
        mutex.withLock {
            checkPrepared()
            val target = when {
                preferredTimeUs in 0..metadata.durationUs -> preferredTimeUs
                initialPositionMs != null -> initialPositionMs * 1000L
                else -> if (localFile != null && sourceUri.path?.endsWith(".mp4") == true) 0L else metadata.durationUs / 2L
            }.coerceIn(0L, metadata.durationUs)
            resolveRealFrame(target)
        }

    suspend fun closest(timeUs: Long): FrameIdentity = mutex.withLock {
        checkPrepared()
        if (timeline.usesFrameIndexes) timeline.closest(timeUs) else resolveRealFrame(timeUs)
    }

    suspend fun step(current: FrameIdentity, delta: Int): FrameIdentity = mutex.withLock {
        checkPrepared()
        if (delta == 0) return@withLock current
        if (timeline.usesFrameIndexes) return@withLock timeline.step(current, delta)
        var resolved = current
        repeat(abs(delta).coerceAtMost(60)) {
            resolved = if (delta > 0) nextTimestamp(resolved) else previousTimestamp(resolved)
        }
        resolved
    }

    suspend fun filmstrip(center: FrameIdentity, count: Int = 16): List<FrameIdentity> = mutex.withLock {
        checkPrepared()
        timeline.filmstrip(center, count).map { candidate ->
            if (timeline.usesFrameIndexes) candidate else resolveRealFrame(candidate.presentationTimeUs)
        }.distinct()
    }

    suspend fun decodePreview(
        identity: FrameIdentity,
        maxWidth: Int,
        maxHeight: Int,
    ): Bitmap = withContext(Dispatchers.IO) {
        val key = "${identity.encode()}:$maxWidth:$maxHeight"
        previewCache.get(key)?.takeIf { !it.isRecycled }?.let { return@withContext it }
        mutex.withLock {
            previewCache.get(key)?.takeIf { !it.isRecycled }?.let { return@withLock it }
            checkPrepared()
            val scale = min(
                maxWidth.coerceAtLeast(1).toFloat() / metadata.width,
                maxHeight.coerceAtLeast(1).toFloat() / metadata.height,
            ).coerceAtMost(1f)
            val outputWidth = (metadata.width * scale).roundToInt().coerceAtLeast(1)
            val outputHeight = (metadata.height * scale).roundToInt().coerceAtLeast(1)
            val targetWidth = if (metadata.rotationDegrees == 90 || metadata.rotationDegrees == 270) {
                outputHeight
            } else outputWidth
            val targetHeight = if (metadata.rotationDegrees == 90 || metadata.rotationDegrees == 270) {
                outputWidth
            } else outputHeight
            val frame = runCatching {
                retriever.getScaledFrameAtTime(
                    identity.presentationTimeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST,
                    targetWidth,
                    targetHeight,
                )
            }.getOrNull() ?: throw FrameDecodeException("Frame unavailable")
            val normalized = normalize(frame)
            previewCache.put(key, normalized)
            normalized
        }
    }

    suspend fun decodeFullResolution(identity: FrameIdentity): Bitmap = withContext(Dispatchers.IO) {
        mutex.withLock {
            checkPrepared()
            val frame = runCatching {
                if (timeline.usesFrameIndexes && identity.frameIndex >= 0) {
                    retriever.getFrameAtIndex(identity.frameIndex)
                } else {
                    retriever.getFrameAtTime(
                        identity.presentationTimeUs,
                        MediaMetadataRetriever.OPTION_CLOSEST,
                    )
                }
            }.getOrNull() ?: throw FrameDecodeException("Frame unavailable")
            normalize(frame)
        }
    }

    private fun resolveRealFrame(timeUs: Long): FrameIdentity {
        extractor.seekTo(timeUs.coerceIn(0L, metadata.durationUs), MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        var bestTime = extractor.sampleTime.takeIf { it >= 0L } ?: timeUs
        var bestDistance = abs(bestTime - timeUs)
        var scanned = 0
        while (scanned++ < MAX_SAMPLES_PER_WINDOW && extractor.sampleTime >= 0L) {
            val candidate = extractor.sampleTime
            val distance = abs(candidate - timeUs)
            if (distance <= bestDistance) {
                bestTime = candidate
                bestDistance = distance
            }
            if (candidate > timeUs && distance > bestDistance) break
            if (!extractor.advance()) break
        }
        return FrameIdentity(-1, bestTime.coerceIn(0L, metadata.durationUs))
    }

    private fun nextTimestamp(current: FrameIdentity): FrameIdentity {
        extractor.seekTo(current.presentationTimeUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        var scanned = 0
        while (scanned++ < MAX_SAMPLES_PER_WINDOW && extractor.sampleTime >= 0L) {
            val candidate = extractor.sampleTime
            if (candidate > current.presentationTimeUs) return FrameIdentity(-1, candidate)
            if (!extractor.advance()) break
        }
        return current
    }

    private fun previousTimestamp(current: FrameIdentity): FrameIdentity {
        val start = (current.presentationTimeUs - PREVIOUS_WINDOW_US).coerceAtLeast(0L)
        extractor.seekTo(start, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        var previous = -1L
        var scanned = 0
        while (scanned++ < MAX_SAMPLES_PER_WINDOW && extractor.sampleTime >= 0L) {
            val candidate = extractor.sampleTime
            if (candidate >= current.presentationTimeUs) break
            previous = candidate
            if (!extractor.advance()) break
        }
        return if (previous >= 0L) FrameIdentity(-1, previous) else current
    }

    private fun normalize(source: Bitmap): Bitmap {
        var current = source
        val rawWidth = sourceWidth.takeIf { it > 0 } ?: source.width
        val rawHeight = sourceHeight.takeIf { it > 0 } ?: source.height
        val rotation = metadata.rotationDegrees.mod(360)
        val needsRotation = when (rotation) {
            90, 270 -> current.width * rawHeight == current.height * rawWidth
            180 -> true
            else -> false
        }
        if (needsRotation) {
            val rotated = Bitmap.createBitmap(
                current,
                0,
                0,
                current.width,
                current.height,
                Matrix().apply { postRotate(rotation.toFloat()) },
                true,
            )
            if (rotated !== current) current.recycle()
            current = rotated
        }
        val isSrgbArgb = current.config == Bitmap.Config.ARGB_8888 &&
            current.colorSpace?.isSrgb == true && current.config != Bitmap.Config.HARDWARE
        if (isSrgbArgb) return current
        val converted = Bitmap.createBitmap(
            current.width,
            current.height,
            Bitmap.Config.ARGB_8888,
            false,
            ColorSpace.get(ColorSpace.Named.SRGB),
        )
        Canvas(converted).drawBitmap(current, 0f, 0f, null)
        if (converted !== current) current.recycle()
        return converted
    }

    private fun setRetrieverDataSource() {
        localFile?.takeIf(File::exists)?.let {
            retriever.setDataSource(it.absolutePath)
            return
        }
        retriever.setDataSource(context, sourceUri)
    }

    private fun setExtractorDataSource() {
        localFile?.takeIf(File::exists)?.let {
            extractor.setDataSource(it.absolutePath)
            return
        }
        extractor.setDataSource(context, sourceUri, null)
    }

    private fun findVideoTrack(): Int = (0 until extractor.trackCount).firstOrNull { index ->
        extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
    } ?: -1

    private fun checkPrepared() {
        checkOpen()
        if (!::metadata.isInitialized) throw FrameDecodeException("Decoder is not prepared")
    }

    private fun checkOpen() {
        if (closed) throw FrameDecodeException("Decoder is closed")
    }

    suspend fun closeSafely() {
        mutex.withLock { releaseResources() }
    }

    override fun close() {
        releaseResources()
    }

    private fun releaseResources() {
        if (closed) return
        closed = true
        previewCache.evictAll()
        runCatching { extractor.release() }
        runCatching { retriever.release() }
    }

    private fun MediaMetadataRetriever.metadataInt(key: Int): Int? = extractMetadata(key)?.toIntOrNull()
    private fun MediaMetadataRetriever.metadataLong(key: Int): Long? = extractMetadata(key)?.toLongOrNull()
    private fun MediaFormat.intOrNull(key: String): Int? = if (containsKey(key)) getInteger(key) else null
    private fun MediaFormat.stringOrNull(key: String): String? =
        if (containsKey(key)) getString(key) else null
    private fun MediaFormat.longOrNull(key: String): Long? = if (containsKey(key)) getLong(key) else null
    private fun MediaFormat.floatOrNull(key: String): Float? = if (containsKey(key)) {
        runCatching { getFloat(key) }.getOrElse { getInteger(key).toFloat() }
    } else null

    companion object {
        private const val PREVIEW_CACHE_BYTES = 24 * 1024 * 1024
        private const val MAX_SAMPLES_PER_WINDOW = 10_000
        private const val PREVIOUS_WINDOW_US = 10_000_000L
    }
}
