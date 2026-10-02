/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.sandbox

import android.app.Service
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.Metadata
import com.drew.metadata.exif.ExifIFD0Directory
import com.drew.metadata.exif.ExifSubIFDDirectory
import com.drew.metadata.exif.GpsDirectory
import com.drew.metadata.mov.media.QuickTimeVideoDirectory
import com.drew.metadata.mp4.media.Mp4VideoDirectory
import com.drew.metadata.xmp.XmpDirectory
import com.drew.metadata.xmp.XmpReader
import com.dot.gallery.feature_node.domain.model.isRedactedCoordinate
import com.dot.gallery.feature_node.domain.model.parseCaptureTimestamp
import com.dot.gallery.feature_node.presentation.util.printError
import com.dot.gallery.feature_node.presentation.util.printWarn
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.InputStream
import java.io.PushbackInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Isolated-process service for metadata parsing.
 *
 * Runs under a fake UID with no permissions, no app data, and no network.
 * If a malformed file exploits a bug in metadata-extractor or MediaMetadataRetriever,
 * the attacker cannot reach the user's media library or network.
 *
 * Communication is via [Messenger] IPC:
 * - Client sends a [Message] with [MSG_PARSE_IMAGE], [MSG_PARSE_VIDEO], or [MSG_PARSE_RAW_METADATA]
 * - Data is passed as a [ParcelFileDescriptor] in the message Bundle
 * - Service replies via [Message.replyTo] with a result Bundle
 */
class IsolatedMetadataService : Service() {

    private lateinit var messenger: Messenger

    override fun onCreate() {
        super.onCreate()
        messenger = Messenger(IncomingHandler(Looper.getMainLooper()))
    }

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    private inner class IncomingHandler(looper: Looper) : Handler(looper) {
        override fun handleMessage(msg: Message) {
            val replyTo = msg.replyTo ?: return
            val data = msg.data
            data.classLoader = ParcelFileDescriptor::class.java.classLoader

            val reply = Message.obtain()
            reply.what = msg.what

            try {
                reply.data = when (msg.what) {
                    MSG_PARSE_IMAGE -> parseImageMetadata(data)
                    MSG_PARSE_VIDEO -> parseVideoMetadata(data)
                    MSG_PARSE_RAW_METADATA -> parseRawMetadata(data)
                    else -> Bundle().apply { putBoolean(KEY_ERROR, true) }
                }
            } catch (e: Exception) {
                reply.data = Bundle().apply {
                    putBoolean(KEY_ERROR, true)
                    putString(KEY_ERROR_MESSAGE, e.message)
                }
            }

            try {
                replyTo.send(reply)
            } catch (e: Exception) {
                // Client is gone
                printError("service.sandbox", "metadata reply send failed; client is gone", e)
            }
        }
    }

    // ── Image EXIF/XMP parsing (metadata-extractor) ───────────────────────

    @Suppress("DEPRECATION")
    private fun parseImageMetadata(input: Bundle): Bundle {
        val pfd = input.getParcelable<ParcelFileDescriptor>(KEY_PFD)
            ?: return Bundle().apply { putBoolean(KEY_ERROR, true) }
        val label = input.getString(KEY_LABEL, "")

        return pfd.use { fd ->
            // Primary: stream straight into metadata-extractor (the common JPEG/PNG/HEIF path).
            val meta = runCatching {
                readFromCurrent(fd, ::readImageMetadata)
            }.getOrNull()

            if (meta != null) {
                bundleFromImageMetadata(meta, label)
            } else {
                // metadata-extractor cannot read this container (e.g. JXL: "File format could not
                // be determined"). Recover embedded EXIF/XMP from direct or Brotli-compressed
                // metadata boxes so date/camera/GPS/ISO and feature flags still surface.
                val embeddedMeta = extractEmbeddedMetadata(fd)
                if (embeddedMeta != null) {
                    bundleFromImageMetadata(embeddedMeta, label)
                } else {
                    // No EXIF recoverable — signal error so the client's in-process dimension
                    // fallback (native size probe) runs and the sheet still shows resolution/size.
                    Bundle().apply { putBoolean(KEY_ERROR, true) }
                }
            }
        }
    }

    /**
     * Reads a bounded prefix of the shared descriptor, walks JPEG XL metadata boxes, and merges
     * direct or Brotli-compressed EXIF/XMP into metadata-extractor's model. Also accepts a generic
     * `Exif\u0000\u0000` preamble. Rewinds the descriptor first since the primary streaming read
     * advanced its offset.
     */
    private fun extractEmbeddedMetadata(fd: ParcelFileDescriptor): Metadata? =
        runCatching {
            val metadata = readFromStart(fd) { JxlBoxMetadataExtractor.extract(it) }
            readFromStart(fd) {
                JxlBoxMetadataExtractor.mergePreamble(
                    readCappedBytes(it, MAX_EXIF_SCAN_BYTES),
                    metadata
                )
            }
        }.getOrNull()

    private inline fun <T> readFromStart(
        fd: ParcelFileDescriptor,
        block: (InputStream) -> T
    ): T {
        Os.lseek(fd.fileDescriptor, 0, OsConstants.SEEK_SET)
        return readFromCurrent(fd, block)
    }

    private inline fun <T> readFromCurrent(
        fd: ParcelFileDescriptor,
        block: (InputStream) -> T
    ): T = ParcelFileDescriptor.AutoCloseInputStream(
        ParcelFileDescriptor.dup(fd.fileDescriptor)
    ).use(block)

    /** Reads up to [cap] bytes from [stream]'s current offset into a heap buffer. */
    private fun readCappedBytes(stream: InputStream, cap: Int): ByteArray {
        val buffer = ByteArray(cap)
        var total = 0
        while (total < cap) {
            val read = stream.read(buffer, total, cap - total)
            if (read < 0) break
            total += read
        }
        return if (total == cap) buffer else buffer.copyOf(total)
    }

    private fun readImageMetadata(stream: InputStream): Metadata? {
        val input = PushbackInputStream(stream, JXL_CONTAINER_SIGNATURE.size)
        val signature = ByteArray(JXL_CONTAINER_SIGNATURE.size)
        var count = 0
        while (count < signature.size) {
            val read = input.read(signature, count, signature.size - count)
            if (read <= 0) break
            count += read
        }
        if (count > 0) input.unread(signature, 0, count)
        return if (count == signature.size && signature.contentEquals(JXL_CONTAINER_SIGNATURE)) {
            JxlBoxMetadataExtractor.extract(input)
        } else {
            ImageMetadataReader.readMetadata(input)
        }
    }

    private fun bundleFromImageMetadata(
        meta: com.drew.metadata.Metadata,
        label: String
    ): Bundle {
        val result = Bundle()

        // EXIF IFD0
        meta.getDirectoriesOfType(ExifIFD0Directory::class.java).forEach { dir ->
            if (!result.containsKey(KEY_IMAGE_DESCRIPTION))
                dir.getString(ExifIFD0Directory.TAG_IMAGE_DESCRIPTION)
                    ?.let { result.putString(KEY_IMAGE_DESCRIPTION, it) }
            if (!result.containsKey(KEY_MANUFACTURER))
                dir.getString(ExifIFD0Directory.TAG_MAKE)
                    ?.let { result.putString(KEY_MANUFACTURER, it) }
            if (!result.containsKey(KEY_MODEL))
                dir.getString(ExifIFD0Directory.TAG_MODEL)
                    ?.let { result.putString(KEY_MODEL, it) }
            if (result.getInt(KEY_IMAGE_WIDTH, 0) == 0)
                dir.getInteger(ExifIFD0Directory.TAG_IMAGE_WIDTH)
                    ?.let { result.putInt(KEY_IMAGE_WIDTH, it) }
            if (result.getInt(KEY_IMAGE_HEIGHT, 0) == 0)
                dir.getInteger(ExifIFD0Directory.TAG_IMAGE_HEIGHT)
                    ?.let { result.putInt(KEY_IMAGE_HEIGHT, it) }
            if (!result.containsKey(KEY_RES_X))
                dir.getDoubleObject(ExifIFD0Directory.TAG_X_RESOLUTION)
                    ?.let { result.putDouble(KEY_RES_X, it) }
            if (!result.containsKey(KEY_RES_Y))
                dir.getDoubleObject(ExifIFD0Directory.TAG_Y_RESOLUTION)
                    ?.let { result.putDouble(KEY_RES_Y, it) }
            if (!result.containsKey(KEY_RES_UNIT))
                dir.getInteger(ExifIFD0Directory.TAG_RESOLUTION_UNIT)
                    ?.let { result.putInt(KEY_RES_UNIT, it) }
        }

        // EXIF SubIFD
        meta.getDirectoriesOfType(ExifSubIFDDirectory::class.java).forEach { dir ->
            if (!result.containsKey(KEY_DATETIME_ORIGINAL))
                dir.getString(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL)
                    ?.let { result.putString(KEY_DATETIME_ORIGINAL, it) }
            if (!result.containsKey(KEY_APERTURE))
                dir.getDescription(ExifSubIFDDirectory.TAG_FNUMBER)
                    ?.let { result.putString(KEY_APERTURE, it) }
            if (!result.containsKey(KEY_EXPOSURE_TIME))
                dir.getDescription(ExifSubIFDDirectory.TAG_EXPOSURE_TIME)
                    ?.let { result.putString(KEY_EXPOSURE_TIME, it) }
            if (!result.containsKey(KEY_ISO))
                dir.getString(ExifSubIFDDirectory.TAG_ISO_EQUIVALENT)
                    ?.let { result.putString(KEY_ISO, it) }
            if (result.getInt(KEY_IMAGE_WIDTH, 0) == 0)
                dir.getInteger(ExifSubIFDDirectory.TAG_EXIF_IMAGE_WIDTH)
                    ?.let { result.putInt(KEY_IMAGE_WIDTH, it) }
            if (result.getInt(KEY_IMAGE_HEIGHT, 0) == 0)
                dir.getInteger(ExifSubIFDDirectory.TAG_EXIF_IMAGE_HEIGHT)
                    ?.let { result.putInt(KEY_IMAGE_HEIGHT, it) }
        }

        if (!result.containsKey(KEY_DATETIME_ORIGINAL)) {
            meta.getDirectoriesOfType(ExifIFD0Directory::class.java)
                .firstNotNullOfOrNull { it.getString(ExifIFD0Directory.TAG_DATETIME) }
                ?.let { result.putString(KEY_DATETIME_ORIGINAL, it) }
        }
        result.getString(KEY_DATETIME_ORIGINAL)?.let { rawDate ->
            val offset = meta.directories.asSequence()
                .flatMap { it.tags.asSequence() }
                .firstOrNull { it.tagName.equals("Offset Time Original", ignoreCase = true) }
                ?.description
            parseCaptureTimestamp(rawDate, offset)?.let {
                result.putLong(KEY_CAPTURE_TIMESTAMP_MILLIS, it)
            }
        }

        // GPS. A redacted read (no ACCESS_MEDIA_LOCATION) reaches us as a zeroed
        // GpsDirectory parsing to 0,0 — drop it so the row stores absent, not a fake fix.
        meta.getDirectoriesOfType(GpsDirectory::class.java).forEach { dir ->
            dir.geoLocation
                ?.takeUnless { isRedactedCoordinate(it.latitude, it.longitude) }
                ?.let {
                    if (!result.containsKey(KEY_GPS_LAT)) {
                        result.putDouble(KEY_GPS_LAT, it.latitude)
                        result.putDouble(KEY_GPS_LON, it.longitude)
                    }
                }
        }

        // XMP + feature flags
        val xmps = meta.getDirectoriesOfType(XmpDirectory::class.java)

        // Night mode
        val isNightMode = meta.getDirectoriesOfType(ExifSubIFDDirectory::class.java).any { dir ->
            dir.containsTag(ExifSubIFDDirectory.TAG_ISO_EQUIVALENT) &&
                    dir.containsTag(ExifSubIFDDirectory.TAG_EXPOSURE_TIME) &&
                    run {
                        val isoVal = dir.getInt(ExifSubIFDDirectory.TAG_ISO_EQUIVALENT)
                        val expVal = dir.getDouble(ExifSubIFDDirectory.TAG_EXPOSURE_TIME)
                        label.matches("(?i).*\\.NIGHT\\..*".toRegex()) ||
                                (isoVal < 100 && expVal > 0.01)
                    }
        }
        result.putBoolean(KEY_IS_NIGHT_MODE, isNightMode)

        // Photosphere
        val isPhotosphere = xmps.any { xmp ->
            xmp.xmpProperties.any { prop ->
                (PHOTOSPHERE_KEYS.any { key -> prop.key.contains(key, true) }
                        && prop.value == PHOTOSPHERE_VALUES[0])
                        || prop.value == PHOTOSPHERE_VALUES[1]
            }
        }
        result.putBoolean(KEY_IS_PHOTOSPHERE, isPhotosphere)

        // Panorama (but not photosphere)
        val isPanorama = xmps.any { xmp ->
            xmp.xmpProperties.any { prop ->
                PANORAMA_KEYS.any { key -> prop.key.contains(key, true) }
            }
        } && !isPhotosphere
        result.putBoolean(KEY_IS_PANORAMA, isPanorama)

        // Long exposure
        val isLongExposure = xmps.any { xmp ->
            xmp.xmpProperties.any { prop ->
                LONG_EXPOSURE_KEYS.any { key -> prop.key.contains(key, true) }
            }
        }
        result.putBoolean(KEY_IS_LONG_EXPOSURE, isLongExposure)

        // Motion photo
        val isMotionPhoto = xmps.any { xmp ->
            xmp.xmpProperties.any { prop ->
                (prop.key == "GCamera:MotionPhoto" && prop.value == "1") ||
                        (prop.key == "GCamera:MicroVideo" && prop.value == "1")
            }
        }
        result.putBoolean(KEY_IS_MOTION_PHOTO, isMotionPhoto)

        // Fallback dimensions via BitmapFactory not possible in isolated process
        // (no content resolver), so we return what we have. The client can fallback.

        return result
    }

    // ── Video metadata parsing (MediaMetadataRetriever) ───────────────────

    private var retrieverWedged = false

    /**
     * Runs a [MediaMetadataRetriever] leg on a one-shot worker thread with a bounded wait.
     *
     * Inside an isolated process the retriever may block forever instead of throwing —
     * on Android 12 even the constructor loops on "MediaPlayerService not published"
     * because isolated_app can never reach media.player via servicemanager. Since
     * requests are handled serially on the main looper, one wedged retriever blocks
     * this service for every later parse (each burning the client's 30s timeout), so
     * the retriever is constructed inside the worker too.
     *
     * The native wait is uninterruptible, so on timeout the thread is abandoned and
     * [retrieverWedged] latches: every later parse skips the retriever leg outright
     * instead of leaking another thread. Returns null when the leg wedged, threw, or
     * was skipped.
     */
    private fun <T> withRetrieverWatchdog(block: (MediaMetadataRetriever) -> T): T? {
        if (retrieverWedged) return null
        val latch = CountDownLatch(1)
        var outcome: Result<T>? = null
        Thread({
            var retriever: MediaMetadataRetriever? = null
            outcome = runCatching {
                retriever = MediaMetadataRetriever()
                block(retriever!!)
            }
            latch.countDown()
            retriever?.let { r -> runCatching { r.release() } }
        }, "metadata-retriever").apply { isDaemon = true }.start()
        if (!latch.await(RETRIEVER_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            retrieverWedged = true
            printWarn(
                "service.sandbox",
                "MediaMetadataRetriever wedged waiting for media.player; " +
                    "skipping the retriever leg for the rest of this process"
            )
            return null
        }
        return outcome?.onFailure {
            printError(
                "service.sandbox",
                "video metadata retriever failed; falling back to metadata-extractor",
                it
            )
        }?.getOrNull()
    }

    @Suppress("DEPRECATION")
    private fun parseVideoMetadata(input: Bundle): Bundle {
        val pfd = input.getParcelable<ParcelFileDescriptor>(KEY_PFD)
            ?: return Bundle().apply { putBoolean(KEY_ERROR, true) }

        return pfd.use { fd ->
            val result = Bundle()

            // ── 1) MediaMetadataRetriever (best-effort, watchdogged) ──────
            // setDataSource throws a RuntimeException on some containers/codecs
            // (e.g. HEVC .MOV from iPhones). Treat that as non-fatal so the
            // summary — and the "View all metadata" entry point — still appears;
            // dimensions are recovered from metadata-extractor below.
            withRetrieverWatchdog { retriever ->
                retriever.setDataSource(fd.fileDescriptor)
                Bundle().apply {
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull()?.let { putLong(KEY_DURATION_MS, it) }
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                        ?.toIntOrNull()?.let { putInt(KEY_VIDEO_WIDTH, it) }
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                        ?.toIntOrNull()?.let { putInt(KEY_VIDEO_HEIGHT, it) }
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                        ?.toIntOrNull()?.let { putInt(KEY_BIT_RATE, it) }
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
                        ?.let { rawDate ->
                            parseCaptureTimestamp(rawDate)?.let {
                                putLong(KEY_CAPTURE_TIMESTAMP_MILLIS, it)
                            }
                        }

                    val frameRate = retriever
                        .extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                        ?.toFloatOrNull()
                        ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)
                            ?.let { cnt ->
                                getLong(KEY_DURATION_MS, 0L).takeIf { it > 0 }
                                    ?.let { d -> cnt.toFloat() / (d / 1000f) }
                            }
                    frameRate?.let { putFloat(KEY_FRAME_RATE, it) }
                }
            }?.let(result::putAll)

            // ── 2) metadata-extractor fallback for dimensions and capture time ──
            // Recovers width/height from the QuickTime/MP4 video track when the
            // retriever failed or omitted them. Rewind the shared descriptor first.
            if (!result.containsKey(KEY_VIDEO_WIDTH) || !result.containsKey(KEY_VIDEO_HEIGHT) ||
                !result.containsKey(KEY_CAPTURE_TIMESTAMP_MILLIS)
            ) {
                runCatching {
                    Os.lseek(fd.fileDescriptor, 0, OsConstants.SEEK_SET)
                    FileInputStream(fd.fileDescriptor).use { stream ->
                        val meta = ImageMetadataReader.readMetadata(stream)
                        meta.getFirstDirectoryOfType(QuickTimeVideoDirectory::class.java)?.let { dir ->
                            if (!result.containsKey(KEY_VIDEO_WIDTH))
                                dir.getInteger(QuickTimeVideoDirectory.TAG_WIDTH)
                                    ?.takeIf { it > 0 }?.let { result.putInt(KEY_VIDEO_WIDTH, it) }
                            if (!result.containsKey(KEY_VIDEO_HEIGHT))
                                dir.getInteger(QuickTimeVideoDirectory.TAG_HEIGHT)
                                    ?.takeIf { it > 0 }?.let { result.putInt(KEY_VIDEO_HEIGHT, it) }
                        }
                        meta.getFirstDirectoryOfType(Mp4VideoDirectory::class.java)?.let { dir ->
                            if (!result.containsKey(KEY_VIDEO_WIDTH))
                                dir.getInteger(Mp4VideoDirectory.TAG_WIDTH)
                                    ?.takeIf { it > 0 }?.let { result.putInt(KEY_VIDEO_WIDTH, it) }
                            if (!result.containsKey(KEY_VIDEO_HEIGHT))
                                dir.getInteger(Mp4VideoDirectory.TAG_HEIGHT)
                                    ?.takeIf { it > 0 }?.let { result.putInt(KEY_VIDEO_HEIGHT, it) }
                        }
                        if (!result.containsKey(KEY_CAPTURE_TIMESTAMP_MILLIS)) {
                            meta.directories.asSequence()
                                .flatMap { it.tags.asSequence() }
                                .filter {
                                    it.tagName.contains("creation", ignoreCase = true) ||
                                        it.tagName.contains("date/time original", ignoreCase = true)
                                }
                                .firstNotNullOfOrNull { parseCaptureTimestamp(it.description) }
                                ?.let { result.putLong(KEY_CAPTURE_TIMESTAMP_MILLIS, it) }
                        }
                    }
                }
            }

            result
        }
    }

    // ── Raw metadata for the "View all metadata" screen ───────────────────

    @Suppress("DEPRECATION")
    private fun parseRawMetadata(input: Bundle): Bundle {
        val pfd = input.getParcelable<ParcelFileDescriptor>(KEY_PFD)
            ?: return Bundle().apply { putBoolean(KEY_ERROR, true) }
        val isVideo = input.getBoolean(KEY_IS_VIDEO, false)

        return pfd.use { fd ->
            if (isVideo) {
                parseRawVideoMetadata(fd)
            } else {
                parseRawImageMetadata(fd)
            }
        }
    }

    private fun parseRawImageMetadata(pfd: ParcelFileDescriptor): Bundle {
        val result = Bundle()

        // Primary streaming read. Falls back to the ISO-BMFF 'Exif' box (e.g. JXL) when
        // metadata-extractor can't read the container, so the "View all metadata" screen still
        // lists the embedded EXIF directories for those formats.
        val metadata = runCatching {
            readFromCurrent(pfd, ::readImageMetadata)
        }.getOrNull() ?: extractEmbeddedMetadata(pfd)

        val dirNames = ArrayList<String>()
        var dirIndex = 0
        metadata?.directories?.forEach { directory ->
            val tags = directory.tags.filter { it.description != null }
            if (tags.isEmpty()) return@forEach
            val dirKey = "dir_$dirIndex"
            dirNames.add(directory.name)
            val tagNames = ArrayList<String>(tags.size)
            val tagDescs = ArrayList<String>(tags.size)
            for (tag in tags) {
                tagNames.add(tag.tagName)
                tagDescs.add(tag.description)
            }
            result.putStringArrayList("${dirKey}_names", tagNames)
            result.putStringArrayList("${dirKey}_descs", tagDescs)
            dirIndex++
        }
        result.putStringArrayList(KEY_RAW_DIR_NAMES, dirNames)
        return result
    }

    @Suppress("DEPRECATION")
    private fun parseRawVideoMetadata(pfd: ParcelFileDescriptor): Bundle {
        val result = Bundle()
        val dirNames = ArrayList<String>()
        var dirIndex = 0

        // ── 1) MediaMetadataRetriever (container/stream level tags) ────────
        // Watchdogged like parseVideoMetadata: setDataSource may wedge inside
        // the isolated process, and one wedged retriever stalls every request.
        withRetrieverWatchdog { retriever ->
            retriever.setDataSource(pfd.fileDescriptor)
            val tagNames = ArrayList<String>()
            val tagDescs = ArrayList<String>()
            for ((key, name) in VIDEO_TAG_MAP) {
                retriever.extractMetadata(key)?.let { value ->
                    tagNames.add(name)
                    tagDescs.add(value)
                }
            }
            tagNames to tagDescs
        }?.takeIf { it.first.isNotEmpty() }?.let { (tagNames, tagDescs) ->
            val dirKey = "dir_$dirIndex"
            dirNames.add("Video Metadata")
            result.putStringArrayList("${dirKey}_names", tagNames)
            result.putStringArrayList("${dirKey}_descs", tagDescs)
            dirIndex++
        }

        // ── 2) metadata-extractor (rich container EXIF: QuickTime/MOV, MP4) ─
        // MediaMetadataRetriever does not surface QuickTime/EXIF tags such as
        // camera Make/Model, lens or focal length. metadata-extractor reads the
        // QuickTime ('mvhd'/'mebx'/'mdta') and MP4 atoms that hold them, so .MOV
        // files from iPhones show their full metadata like images do. Rewind the
        // shared file descriptor first since the retriever advanced its offset.
        runCatching {
            Os.lseek(pfd.fileDescriptor, 0, OsConstants.SEEK_SET)
            FileInputStream(pfd.fileDescriptor).use { stream ->
                val metadata = ImageMetadataReader.readMetadata(stream)
                for (directory in metadata.directories) {
                    val tags = directory.tags.filter { it.description != null }
                    if (tags.isEmpty()) continue
                    val dirKey = "dir_$dirIndex"
                    dirNames.add(directory.name)
                    val tagNames = ArrayList<String>(tags.size)
                    val tagDescs = ArrayList<String>(tags.size)
                    for (tag in tags) {
                        tagNames.add(tag.tagName)
                        tagDescs.add(tag.description)
                    }
                    result.putStringArrayList("${dirKey}_names", tagNames)
                    result.putStringArrayList("${dirKey}_descs", tagDescs)
                    dirIndex++
                }
            }
        }

        result.putStringArrayList(KEY_RAW_DIR_NAMES, dirNames)
        return result
    }

    companion object {
        // Message types
        const val MSG_PARSE_IMAGE = 1
        const val MSG_PARSE_VIDEO = 2
        const val MSG_PARSE_RAW_METADATA = 3

        /**
         * Upper bound on the prefix read when recovering an embedded EXIF box from a container
         * metadata-extractor can't read (e.g. JXL). The EXIF box sits near the file start in
         * practice; capping the read keeps peak memory bounded in the isolated process.
         */
        private const val MAX_EXIF_SCAN_BYTES = 8 * 1024 * 1024

        /**
         * How long a retriever leg may run before it is abandoned. Must stay well under
         * the client's reply timeout so a wedged leg still lets the metadata-extractor
         * fallback answer within the same request.
         */
        private const val RETRIEVER_TIMEOUT_MS = 8_000L
        private val JXL_CONTAINER_SIGNATURE = byteArrayOf(
            0x00, 0x00, 0x00, 0x0C, 0x4A, 0x58, 0x4C, 0x20,
            0x0D, 0x0A, 0x87.toByte(), 0x0A
        )

        // Bundle keys — input
        const val KEY_PFD = "pfd"
        const val KEY_LABEL = "label"
        const val KEY_IS_VIDEO = "is_video"

        // Bundle keys — output (image EXIF)
        const val KEY_IMAGE_DESCRIPTION = "image_description"
        const val KEY_DATETIME_ORIGINAL = "datetime_original"
        const val KEY_CAPTURE_TIMESTAMP_MILLIS = "capture_timestamp_millis"
        const val KEY_MANUFACTURER = "manufacturer"
        const val KEY_MODEL = "model"
        const val KEY_APERTURE = "aperture"
        const val KEY_EXPOSURE_TIME = "exposure_time"
        const val KEY_ISO = "iso"
        const val KEY_GPS_LAT = "gps_lat"
        const val KEY_GPS_LON = "gps_lon"
        const val KEY_IMAGE_WIDTH = "image_width"
        const val KEY_IMAGE_HEIGHT = "image_height"
        const val KEY_RES_X = "res_x"
        const val KEY_RES_Y = "res_y"
        const val KEY_RES_UNIT = "res_unit"

        // Bundle keys — output (video)
        const val KEY_DURATION_MS = "duration_ms"
        const val KEY_VIDEO_WIDTH = "video_width"
        const val KEY_VIDEO_HEIGHT = "video_height"
        const val KEY_FRAME_RATE = "frame_rate"
        const val KEY_BIT_RATE = "bit_rate"

        // Bundle keys — output (flags)
        const val KEY_IS_NIGHT_MODE = "is_night_mode"
        const val KEY_IS_PANORAMA = "is_panorama"
        const val KEY_IS_PHOTOSPHERE = "is_photosphere"
        const val KEY_IS_LONG_EXPOSURE = "is_long_exposure"
        const val KEY_IS_MOTION_PHOTO = "is_motion_photo"

        // Bundle keys — output (raw metadata)
        const val KEY_RAW_DIR_NAMES = "raw_dir_names"

        /**
         * Container/stream tags extracted into the raw-metadata "Video Metadata"
         * directory. Shared with the client's in-process probe ([IsolatedMetadataParser])
         * so the synthesized fallback directory lists the same tags.
         */
        internal val VIDEO_TAG_MAP: Map<Int, String> = linkedMapOf(
            MediaMetadataRetriever.METADATA_KEY_ALBUM to "Album",
            MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST to "Album Artist",
            MediaMetadataRetriever.METADATA_KEY_ARTIST to "Artist",
            MediaMetadataRetriever.METADATA_KEY_AUTHOR to "Author",
            MediaMetadataRetriever.METADATA_KEY_BITRATE to "Bitrate",
            MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER to "Track Number",
            MediaMetadataRetriever.METADATA_KEY_COMPILATION to "Compilation",
            MediaMetadataRetriever.METADATA_KEY_COMPOSER to "Composer",
            MediaMetadataRetriever.METADATA_KEY_DATE to "Date",
            MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER to "Disc Number",
            MediaMetadataRetriever.METADATA_KEY_DURATION to "Duration",
            MediaMetadataRetriever.METADATA_KEY_GENRE to "Genre",
            MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO to "Has Audio",
            MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO to "Has Video",
            MediaMetadataRetriever.METADATA_KEY_LOCATION to "Location",
            MediaMetadataRetriever.METADATA_KEY_MIMETYPE to "MIME Type",
            MediaMetadataRetriever.METADATA_KEY_NUM_TRACKS to "Number of Tracks",
            MediaMetadataRetriever.METADATA_KEY_TITLE to "Title",
            MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT to "Video Height",
            MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH to "Video Width",
            MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION to "Video Rotation",
            MediaMetadataRetriever.METADATA_KEY_WRITER to "Writer",
            MediaMetadataRetriever.METADATA_KEY_YEAR to "Year",
            MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT to "Frame Count",
            MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE to "Capture Frame Rate",
            MediaMetadataRetriever.METADATA_KEY_COLOR_STANDARD to "Color Standard",
            MediaMetadataRetriever.METADATA_KEY_COLOR_TRANSFER to "Color Transfer",
            MediaMetadataRetriever.METADATA_KEY_COLOR_RANGE to "Color Range",
            MediaMetadataRetriever.METADATA_KEY_SAMPLERATE to "Sample Rate",
            MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE to "Bits Per Sample",
        )

        // Bundle keys — error
        const val KEY_ERROR = "error"
        const val KEY_ERROR_MESSAGE = "error_message"

        // Feature flag detection constants (mirrored from MediaMetadata companion)
        val PANORAMA_KEYS = arrayOf("ProjectionType", "FullPanoHeightPixels", "FullPanoWidthPixels")
        val PHOTOSPHERE_KEYS = arrayOf("IsPhotosphere", "UsePanoramaViewer")
        val PHOTOSPHERE_VALUES = arrayOf(
            "True",
            "com.google.android.apps.camera.gallery.specialtype.SpecialType-PHOTOSPHERE"
        )
        val LONG_EXPOSURE_KEYS = arrayOf("BurstID", "CameraBurstID")
    }
}

internal object NativeBrotliDecoder {
    private val isAvailable = runCatching { System.loadLibrary("metadata_brotli") }.isSuccess

    fun decompress(bytes: ByteArray, maxOutputSize: Int): ByteArray? {
        if (!isAvailable) return null
        return runCatching { nativeDecompress(bytes, maxOutputSize) }.getOrNull()
    }

    private external fun nativeDecompress(bytes: ByteArray, maxOutputSize: Int): ByteArray?
}

/**
 * Recovers EXIF/XMP from JPEG XL boxes that metadata-extractor can't parse directly. Both direct
 * metadata boxes and their standard Brotli-compressed `brob` representation are supported. Parsing
 * stays inside the isolated process so a malformed file can't reach the main app.
 *
 * EXIF bytes are normalized to start at the TIFF header (`II*\u0000` / `MM\u0000*`) before they are
 * handed to [ImageMetadataReader.readMetadata]. XMP bytes are merged through [XmpReader].
 */
internal object JxlBoxMetadataExtractor {

    private const val EXIF_BOX_TYPE = "Exif"
    private const val XMP_BOX_TYPE = "xml "
    private const val BROTLI_BOX_TYPE = "brob"
    private const val MAX_METADATA_BYTES = 8 * 1024 * 1024
    private const val MAX_METADATA_BOXES = 64
    private val metadataBoxTypes = setOf(EXIF_BOX_TYPE, XMP_BOX_TYPE)

    /** Extracts all supported embedded metadata, or null if none is present in the scanned input. */
    fun extract(
        bytes: ByteArray,
        decompressor: (ByteArray, Int) -> ByteArray? = NativeBrotliDecoder::decompress
    ): Metadata? {
        val metadata = extractMetadata(ByteArrayInputStream(bytes), decompressor)
        return mergePreamble(bytes, metadata)
    }

    fun extract(
        input: InputStream,
        decompressor: (ByteArray, Int) -> ByteArray? = NativeBrotliDecoder::decompress
    ): Metadata? = extractMetadata(input, decompressor).takeIf { it.directoryCount > 0 }

    fun mergePreamble(bytes: ByteArray, metadata: Metadata? = null): Metadata? {
        val merged = metadata ?: Metadata()
        if (!containsExif(merged)) findExifPreamble(bytes)?.let { mergeTiff(it, merged) }
        return merged.takeIf { it.directoryCount > 0 }
    }

    /** Iterates top-level JPEG XL boxes and transparently expands supported `brob` boxes. */
    private fun extractMetadata(
        input: InputStream,
        decompressor: (ByteArray, Int) -> ByteArray?
    ): Metadata {
        val metadata = Metadata()
        var metadataBudget = MAX_METADATA_BYTES
        var compressedBudget = MAX_METADATA_BYTES
        repeat(MAX_METADATA_BOXES) {
            val header = readHeader(input) ?: return metadata
            when {
                header.type in metadataBoxTypes -> {
                    val payload = readBounded(input, header.payloadSize, metadataBudget)
                    if (payload != null) {
                        metadataBudget -= payload.size
                        mergePayload(header.type, payload, metadata)
                    }
                }
                header.type == BROTLI_BOX_TYPE -> {
                    if (header.payloadSize != null && header.payloadSize < 4) {
                        input.skipExact(header.payloadSize)
                    } else {
                        val wrapped = input.readExactOrNull(4) ?: return metadata
                        val wrappedType = wrapped.toString(Charsets.ISO_8859_1)
                        val compressedSize = header.payloadSize?.minus(4)
                        val compressed = if (wrappedType in metadataBoxTypes) {
                            readBounded(input, compressedSize, compressedBudget)
                        } else {
                            if (compressedSize == null) return metadata
                            input.skipExact(compressedSize)
                            null
                        }
                        if (compressed != null) {
                            compressedBudget -= compressed.size
                            decompressor(compressed, metadataBudget)?.let { payload ->
                                if (payload.size <= metadataBudget) {
                                    metadataBudget -= payload.size
                                    mergePayload(wrappedType, payload, metadata)
                                }
                            }
                        }
                    }
                }
                else -> {
                    if (header.payloadSize == null) return metadata
                    input.skipExact(header.payloadSize)
                }
            }
            if (header.payloadSize == null || metadataBudget == 0) return metadata
        }
        return metadata
    }

    private fun mergePayload(type: String, payload: ByteArray, metadata: Metadata) {
        when (type) {
            EXIF_BOX_TYPE -> mergeExif(payload, metadata)
            XMP_BOX_TYPE -> XmpReader().extract(payload, metadata)
        }
    }

    private fun readHeader(input: InputStream): BoxHeader? {
        val sizeBytes = input.readExactOrNull(4) ?: return null
        val type = input.readExactOrNull(4)?.toString(Charsets.ISO_8859_1) ?: return null
        val shortSize = readU32(sizeBytes, 0)
        if (shortSize == 0L) return BoxHeader(type, null)
        if (shortSize == 1L) {
            val extended = input.readExactOrNull(8) ?: return null
            val size = readU64(extended, 0)
            return if (size >= 16) BoxHeader(type, size - 16) else null
        }
        return if (shortSize >= 8) BoxHeader(type, shortSize - 8) else null
    }

    private fun readBounded(input: InputStream, size: Long?, limit: Int): ByteArray? {
        if (limit <= 0) {
            input.skipExact(size)
            return null
        }
        if (size == null) return input.readToEnd(limit)
        if (size > limit || size > Int.MAX_VALUE) {
            input.skipExact(size)
            return null
        }
        return input.readExactOrNull(size.toInt())
    }

    /**
     * JXL `Exif` box payload begins with a 4-byte big-endian offset to the TIFF header (usually 0),
     * per ISO/IEC 18181-2. Some writers instead begin directly with the TIFF header.
     */
    private fun mergeExif(payload: ByteArray, metadata: Metadata): Boolean {
        val tiff = if (payload.size >= 4) {
            val tiffStart = 4L + readU32(payload, 0)
            if (tiffStart < payload.size && isTiffHeader(payload, tiffStart.toInt())) {
                payload.copyOfRange(tiffStart.toInt(), payload.size)
            } else if (isTiffHeader(payload, 0)) {
                payload
            } else {
                null
            }
        } else {
            null
        }
        return tiff?.let { mergeTiff(it, metadata) } ?: false
    }

    private fun mergeTiff(tiff: ByteArray, metadata: Metadata): Boolean {
        val parsed = runCatching {
            ImageMetadataReader.readMetadata(ByteArrayInputStream(tiff))
        }.getOrNull() ?: return false
        parsed.directories.forEach(metadata::addDirectory)
        return parsed.directoryCount > 0
    }

    /** Scans for a JPEG-style "Exif\u0000\u0000" preamble immediately followed by a TIFF header. */
    private fun findExifPreamble(bytes: ByteArray): ByteArray? {
        val n = bytes.size
        var i = 0
        while (i + 6 < n) {
            if (bytes[i] == 'E'.code.toByte() && bytes[i + 1] == 'x'.code.toByte() &&
                bytes[i + 2] == 'i'.code.toByte() && bytes[i + 3] == 'f'.code.toByte() &&
                bytes[i + 4] == 0x00.toByte() && bytes[i + 5] == 0x00.toByte()
            ) {
                val tiffStart = i + 6
                if (isTiffHeader(bytes, tiffStart)) return bytes.copyOfRange(tiffStart, n)
            }
            i++
        }
        return null
    }

    private fun containsExif(metadata: Metadata): Boolean =
        metadata.containsDirectoryOfType(ExifIFD0Directory::class.java) ||
                metadata.containsDirectoryOfType(ExifSubIFDDirectory::class.java) ||
                metadata.containsDirectoryOfType(GpsDirectory::class.java)

    private fun InputStream.readExactOrNull(size: Int): ByteArray? {
        val bytes = ByteArray(size)
        var offset = 0
        while (offset < size) {
            val count = read(bytes, offset, size - offset)
            if (count <= 0) return null
            offset += count
        }
        return bytes
    }

    private fun InputStream.readToEnd(limit: Int): ByteArray? {
        val output = ByteArrayOutputStream(minOf(limit, DEFAULT_BUFFER_SIZE))
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = read(buffer)
            if (count < 0) return output.toByteArray()
            if (output.size() + count > limit) return null
            output.write(buffer, 0, count)
        }
    }

    private fun InputStream.skipExact(size: Long?): Boolean {
        if (size == null) return true
        var remaining = size
        while (remaining > 0) {
            val skipped = skip(remaining)
            if (skipped > 0) {
                remaining -= skipped
            } else if (read() >= 0) {
                remaining--
            } else {
                return false
            }
        }
        return true
    }

    /** True if [off] points at a TIFF header: little-endian (II 2A 00) or big-endian (MM 00 2A). */
    private fun isTiffHeader(bytes: ByteArray, off: Int): Boolean {
        if (off < 0 || off + 4 > bytes.size) return false
        val le = bytes[off] == 0x49.toByte() && bytes[off + 1] == 0x49.toByte() &&
                bytes[off + 2] == 0x2A.toByte() && bytes[off + 3] == 0x00.toByte()
        val be = bytes[off] == 0x4D.toByte() && bytes[off + 1] == 0x4D.toByte() &&
                bytes[off + 2] == 0x00.toByte() && bytes[off + 3] == 0x2A.toByte()
        return le || be
    }

    private fun readU32(bytes: ByteArray, off: Int): Long =
        ((bytes[off].toLong() and 0xFF) shl 24) or
                ((bytes[off + 1].toLong() and 0xFF) shl 16) or
                ((bytes[off + 2].toLong() and 0xFF) shl 8) or
                (bytes[off + 3].toLong() and 0xFF)

    private fun readU64(bytes: ByteArray, off: Int): Long {
        if (bytes[off].toInt() and 0x80 != 0) return -1
        var value = 0L
        for (k in 0 until 8) {
            value = (value shl 8) or (bytes[off + k].toLong() and 0xFF)
        }
        return value
    }

    private data class BoxHeader(val type: String, val payloadSize: Long?)
}
