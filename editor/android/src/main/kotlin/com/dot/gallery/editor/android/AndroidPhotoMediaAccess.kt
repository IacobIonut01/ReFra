package com.dot.gallery.editor.android

import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Mutex
import com.dot.gallery.editor.render.SrgbPixels
import com.dot.gallery.editor.core.model.toEdits
import com.dot.gallery.editor.core.model.isDevelopOnly
import com.dot.gallery.editor.core.model.PhotoRawDevelop
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import androidx.exifinterface.media.ExifInterface
import com.awxkee.jxlcoder.JxlChannelsConfiguration
import com.awxkee.jxlcoder.JxlCoder
import com.awxkee.jxlcoder.JxlCompressionOption
import com.awxkee.jxlcoder.JxlDecodingSpeed
import com.awxkee.jxlcoder.JxlEffort
import com.dot.gallery.editor.core.model.PhotoDocument
import com.dot.gallery.editor.core.model.PhotoExportFormat
import com.dot.gallery.editor.core.model.PhotoMapping
import com.dot.gallery.editor.core.model.PixelSize
import com.dot.gallery.editor.io.JpegQualityProbe
import com.dot.gallery.editor.io.NativeColorEffect
import com.dot.gallery.editor.io.StreamingPngWriter
import com.dot.gallery.editor.render.BasicColorEffect
import com.dot.gallery.editor.render.FrameEffect
import com.dot.gallery.editor.render.photo.PhotoExportResult
import com.dot.gallery.editor.render.photo.PhotoInfo
import com.dot.gallery.editor.render.photo.PhotoMediaAccess
import com.dot.gallery.editor.render.photo.PhotoRaster
import com.dot.gallery.editor.render.photo.PhotoStripExporter
import com.dot.gallery.editor.render.photo.ScanlineSink
import com.dot.gallery.editor.render.photo.PhotoTooLargeException
import com.dot.gallery.editor.render.photo.UnsupportedPhotoException
import java.io.BufferedOutputStream
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
 * Android [PhotoMediaAccess]: opens `content://` and `file://` photos through
 * [ParcelFileDescriptor] + [BitmapRegionDecoder] and exports edited copies
 * into `Pictures/ReFra` via MediaStore.
 *
 * Sources are probed by magic bytes (JPEG `FF D8`, PNG signature) — declared
 * MIME is not trusted. Non-seekable sources (pipe-backed `content://`
 * providers answering `ESPIPE`) are staged into a private `cache/editor/
 * imports` file first — capped at [MAX_IMPORT_BYTES] and deleted when the
 * raster closes.
 *
 * Copies keep the source's container at the original resolution: a JPEG
 * source is re-encoded as JPEG at the source's own quality (read from its
 * quantization tables), and a PNG source stays a lossless PNG. Lossless PNG
 * for camera JPEGs was the wrong default — the copy landed an order of
 * magnitude above the original for no visible gain. Both paths stream the
 * output band by band; the whole image is never held in memory.
 *
 * The source is never modified and no source metadata or location is copied —
 * the EXIF orientation is *applied*, not forwarded. All blocking work runs on
 * [ioDispatcher]. [onError] receives cleanup failures that must not be
 * swallowed (e.g. a pending-row delete that fails after the real error).
 */
class AndroidPhotoMediaAccess(
    context: Context,
    private val onError: (Throwable) -> Unit,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val preferNative: Boolean = true
) : PhotoMediaAccess {

    private val appContext = context.applicationContext

    // The colour stage of the strip exporter: the packaged C kernel when it
    // loads, the Kotlin reference when it does not. Created lazily on the
    // export IO thread — the JNI load never runs on a UI thread; the kernel
    // is stateless so one instance is safe across exports.
    private val colourEffect: FrameEffect by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        if (preferNative) {
            NativeColorEffect(AndroidEditorNativeLoader(), onUnavailable = onError)
        } else {
            BasicColorEffect()
        }
    }

    /**
     * Test seam for descriptor lookup (e.g. a pipe-backed provider on a
     * device test); production code never sets it.
     */
    internal var descriptorOpener: ((Uri) -> ParcelFileDescriptor?)? = null

    override suspend fun open(reference: String): PhotoRaster =
        openDeveloped(reference, PhotoRawDevelop())

    override suspend fun openDeveloped(reference: String, develop: PhotoRawDevelop): PhotoRaster {
        // A prompt cancellation can discard the withContext result before the
        // caller receives it — hold the raster so it is closed anyway.
        var acquired: PhotoRaster? = null
        try {
            return withContext(ioDispatcher) {
                val raster = openRawOnIo(reference, develop) ?: openOnIo(reference)
                acquired = raster
                raster
            }
        } catch (t: Throwable) {
            acquired?.close()
            throw t
        }
    }

    /** The last RAW read and its decoded base, so re-tones stay instant. */
    private val rawLock = Mutex()
    private var rawBytes: Pair<String, ByteArray>? = null
    private var developBase: DevelopBase? = null

    private class DevelopBase(
        val reference: String,
        val recipe: PhotoRawDevelop,
        val maxEdge: Int,
        val bitmap: Bitmap
    )

    /**
     * The source's bytes when it is a RAW the host's decoder can read. JPEG and
     * PNG magic bytes short-circuit this, so ordinary photos never buffer whole.
     */
    private suspend fun rawBytesFor(reference: String, decoder: EditorRawDecoder): ByteArray? {
        rawLock.withLock { rawBytes?.takeIf { it.first == reference }?.let { return it.second } }
        val uri = parseReference(reference)
        val header = ByteArray(RAW_SNIFF_BYTES)
        val read = appContext.contentResolver.openInputStream(uri)?.use { it.read(header) } ?: return null
        if (read >= 3 && header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte()) return null
        if (read >= PNG_MAGIC.size && header.copyOf(PNG_MAGIC.size).contentEquals(PNG_MAGIC)) return null
        val data = readCapped(uri)
        if (decoder.size(data) == null) return null
        rawLock.withLock { rawBytes = reference to data }
        return data
    }

    private suspend fun readCapped(uri: Uri): ByteArray {
        val input = appContext.contentResolver.openInputStream(uri)
            ?: throw IOException("cannot open stream for $uri")
        return input.use { stream ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(COPY_BUFFER_BYTES)
            while (true) {
                coroutineContext.ensureActive()
                val count = stream.read(buffer)
                if (count < 0) break
                out.write(buffer, 0, count)
                if (out.size() > MAX_IMPORT_BYTES) {
                    throw PhotoTooLargeException(
                        "Photo exceeds the ${MAX_IMPORT_BYTES / (1024 * 1024)} MiB import limit"
                    )
                }
            }
            out.toByteArray()
        }
    }

    /** A raster over a RAW source, or null when [reference] is not one. */
    private suspend fun openRawOnIo(
        reference: String,
        develop: PhotoRawDevelop,
        decodeNow: Boolean = false
    ): AndroidRawPhotoRaster? {
        val decoder = EditorRawDispatch.decoder ?: return null
        val data = rawBytesFor(reference, decoder) ?: return null
        val name = displayName(parseReference(reference))
        if (!decodeNow) {
            val size = decoder.size(data) ?: return null
            return AndroidRawPhotoRaster(
                reference, name, size, data, develop, decoder, ioDispatcher
            )
        }
        // An export sizes itself from the real decode, half size included.
        val decoded = decodeRawOrThrow(decoder, data, develop, develop.halfSize)
        return AndroidRawPhotoRaster(
            reference, name, PixelSize(decoded.width, decoded.height), data, develop,
            decoder, ioDispatcher, decoded
        )
    }

    override suspend fun developPreview(
        reference: String,
        develop: PhotoRawDevelop,
        maxEdge: Int,
        reuseBase: Boolean
    ): SrgbPixels? = withContext(ioDispatcher) {
        val decoder = EditorRawDispatch.decoder ?: return@withContext null
        val data = rawBytesFor(reference, decoder) ?: return@withContext null
        val size = decoder.size(data) ?: return@withContext null
        val half = develop.halfSize || maxOf(size.width, size.height) >= maxEdge * 2
        if (!reuseBase) {
            val decoded = decodeRawOrThrow(decoder, data, develop, half)
            val bounded = decoded.boundedTo(maxEdge)
            return@withContext try {
                bounded.toSrgbPixels()
            } finally {
                if (bounded !== decoded) bounded.recycle()
                decoded.recycle()
            }
        }
        val base = rawLock.withLock {
            developBase?.takeIf {
                it.reference == reference && it.maxEdge == maxEdge && it.recipe.sharesBaseWith(develop)
            }?.bitmap ?: run {
                val decoded = decodeRawOrThrow(decoder, data, develop.baseOnly, half)
                val bounded = decoded.boundedTo(maxEdge)
                if (bounded !== decoded) decoded.recycle()
                developBase?.bitmap?.recycle()
                developBase = DevelopBase(reference, develop.baseOnly, maxEdge, bounded)
                bounded
            }
        }
        if (!develop.hasTone) return@withContext base.toSrgbPixels()
        val toned = decoder.applyTone(base, develop) ?: return@withContext base.toSrgbPixels()
        try {
            toned.toSrgbPixels()
        } finally {
            toned.recycle()
        }
    }

    override fun releaseDevelopCache() {
        // Runs from close(); a preview still holding the lock frees nothing here
        // and the next open replaces what it left.
        if (rawLock.tryLock()) {
            try {
                developBase?.bitmap?.recycle()
                developBase = null
                rawBytes = null
            } finally {
                rawLock.unlock()
            }
        }
    }

    /** Streams a developed RAW straight to a TIFF file; it carries the develop alone. */
    private suspend fun exportRawTiff(
        document: PhotoDocument,
        format: PhotoExportFormat,
        onPublished: (PhotoExportResult) -> Unit,
        onProgress: (Float) -> Unit
    ): PhotoExportResult {
        if (!document.toEdits().isDevelopOnly()) {
            throw UnsupportedPhotoException("A TIFF can only carry the RAW develop")
        }
        val reference = document.source.reference
        val decoder = EditorRawDispatch.decoder
            ?: throw UnsupportedPhotoException("This build cannot develop RAW photos")
        val data = rawBytesFor(reference, decoder)
            ?: throw UnsupportedPhotoException("Only a RAW photo can be saved as TIFF")
        val fullSize = decoder.size(data)
            ?: throw UnsupportedPhotoException("Only a RAW photo can be saved as TIFF")
        val size = if (document.develop.halfSize) {
            PixelSize(fullSize.width / 2, fullSize.height / 2)
        } else {
            fullSize
        }
        val dir = File(appContext.cacheDir, "editor/exports")
        if (!dir.exists() && !dir.mkdirs()) {
            throw IOException("cannot create staging directory $dir")
        }
        val staging = File(dir, "${UUID.randomUUID()}.${format.extension}")
        try {
            val written = ParcelFileDescriptor.open(
                staging,
                ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_WRITE_ONLY or
                    ParcelFileDescriptor.MODE_TRUNCATE
            ).use { descriptor ->
                decoder.exportTiff(
                    data, document.develop, descriptor.fd,
                    if (format == PhotoExportFormat.TIFF_16) 16 else 8
                )
            }
            if (!written || staging.length() == 0L) {
                throw IOException("the RAW decoder could not write the TIFF")
            }
            onProgress(PROGRESS_ENCODE)
            coroutineContext.ensureActive()
            val result = publish(
                displayName(parseReference(reference)), staging, size, format, onPublished
            )
            onProgress(1f)
            return result
        } finally {
            deleteStaging(staging)
        }
    }

    private suspend fun openOnIo(reference: String): AndroidPhotoRaster {
        val uri = parseReference(reference)
        var descriptor = openDescriptor(uri)
        var importFile: File? = null
        try {
            if (!isSeekable(descriptor)) {
                // Pipe-backed content provider: copy into a private cache file
                // so the region decoder gets a real, seekable fd.
                descriptor.close()
                importFile = stageImport(uri)
                descriptor = ParcelFileDescriptor.open(
                    importFile, ParcelFileDescriptor.MODE_READ_ONLY
                )
            }
            // Magic-byte format check: declared MIME / extension is not trusted.
            val mime = sniffMime(descriptor)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFileDescriptor(descriptor.fileDescriptor, null, bounds)
            val width = bounds.outWidth
            val height = bounds.outHeight
            if (width <= 0 || height <= 0) {
                throw IOException("not a decodable image: $reference")
            }
            if (width > MAX_DIMENSION || height > MAX_DIMENSION ||
                width.toLong() * height > MAX_PIXELS
            ) {
                throw PhotoTooLargeException(
                    "image ${width}x$height exceeds limits " +
                        "($MAX_DIMENSION max dimension, $MAX_PIXELS max pixels)"
                )
            }
            val orientation = if (mime == MIME_JPEG) {
                ExifInterface(descriptor.fileDescriptor).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                ).takeIf { it in 1..8 } ?: ExifInterface.ORIENTATION_NORMAL
            } else {
                ExifInterface.ORIENTATION_NORMAL
            }
            val info = PhotoInfo(
                reference = reference,
                displayName = displayName(uri),
                rawSize = PixelSize(width, height),
                orientation = orientation
            )
            // The one-argument newInstance overload requires API 31; min is 29.
            @Suppress("DEPRECATION")
            val decoder = BitmapRegionDecoder.newInstance(
                descriptor.fileDescriptor, false
            )
            return AndroidPhotoRaster(
                info, mime, descriptor, decoder, ioDispatcher, importFile
            )
        } catch (t: Throwable) {
            descriptor.close()
            importFile?.delete()
            throw t
        }
    }

    /**
     * Copies a non-seekable source into a privately owned cache file.
     * Cancellable between chunks; oversized sources fail with our own
     * capability error and delete their partial copy.
     */
    private suspend fun stageImport(uri: Uri): File {
        val dir = File(appContext.cacheDir, "editor/imports")
        if (!dir.exists() && !dir.mkdirs()) {
            throw IOException("cannot create import directory $dir")
        }
        val target = File(dir, UUID.randomUUID().toString())
        try {
            val input = appContext.contentResolver.openInputStream(uri)
                ?: throw IOException("cannot open stream for $uri")
            input.use {
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(COPY_BUFFER_BYTES)
                    var total = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_IMPORT_BYTES) {
                            throw PhotoTooLargeException(
                                "Photo exceeds the " +
                                    "${MAX_IMPORT_BYTES / (1024 * 1024)} MiB import limit"
                            )
                        }
                        output.write(buffer, 0, read)
                    }
                }
            }
            return target
        } catch (t: Throwable) {
            target.delete()
            throw t
        }
    }

    private fun isSeekable(descriptor: ParcelFileDescriptor): Boolean = try {
        Os.lseek(descriptor.fileDescriptor, 0L, OsConstants.SEEK_CUR)
        true
    } catch (e: ErrnoException) {
        if (e.errno == OsConstants.ESPIPE) false else throw IOException(e)
    }

    override val supportedExportFormats: Set<PhotoExportFormat> by lazy {
        buildSet {
            add(PhotoExportFormat.JPEG)
            add(PhotoExportFormat.PNG)
            add(PhotoExportFormat.WEBP)
            if (HeifScanlineEncoder.isAvailable()) add(PhotoExportFormat.HEIF)
            if (JxlBitmapEncoder.isAvailable()) add(PhotoExportFormat.JXL)
        }
    }

    override suspend fun export(
        document: PhotoDocument,
        format: PhotoExportFormat?,
        onPublished: (PhotoExportResult) -> Unit,
        onProgress: (Float) -> Unit
    ): PhotoExportResult =
        EditorExportDispatch.photo?.export(document, format, onPublished, onProgress)
            ?: exportNow(document, format, onPublished, onProgress)

    /** The real encode+publish, run inline — the worker path calls this. */
    suspend fun exportNow(
        document: PhotoDocument,
        format: PhotoExportFormat?,
        onPublished: (PhotoExportResult) -> Unit,
        onProgress: (Float) -> Unit
    ): PhotoExportResult = withContext(ioDispatcher) {
        if (format?.isTiff == true) {
            return@withContext exportRawTiff(document, format, onPublished, onProgress)
        }
        val raster: PhotoRaster = openRawOnIo(
            document.source.reference, document.develop, decodeNow = true
        ) ?: openOnIo(document.source.reference)
        // Visible layer sources opened read-only, deduplicated by
        // reference; every acquired raster closes in the finally.
        val openLayers = LinkedHashMap<String, PhotoRaster>()
        try {
            val layerRasters = LinkedHashMap<String, PhotoRaster>()
            for (layer in document.layers) {
                if (!layer.visible) continue
                var opened = openLayers[layer.source.reference]
                if (opened == null) {
                    opened = openOnIo(layer.source.reference)
                    openLayers[layer.source.reference] = opened
                }
                layerRasters[layer.source.id] = opened
            }
            val mapping = PhotoMapping(
                raster.info.rawSize, raster.info.orientation, document.geometry
            )
            val outputSize = mapping.outputSize
            // An explicit format wins; otherwise re-encode in the source's own
            // container whenever that container is lossy JPEG: a lossless PNG
            // of a camera photo is roughly an order of magnitude larger than
            // the original for no visible gain. PNG sources keep lossless PNG
            // (they may carry alpha). If the native JPEG encoder is missing the
            // copy stays PNG rather than failing.
            val outputFormat = format ?: sourceExportFormatFor(raster)
            val dir = File(appContext.cacheDir, "editor/exports")
            if (!dir.exists() && !dir.mkdirs()) {
                throw IOException("cannot create staging directory $dir")
            }
            val staging = File(dir, "${UUID.randomUUID()}.${outputFormat.extension}")
            try {
                encode(raster, document, layerRasters, outputSize, outputFormat, staging) {
                    onProgress(it * PROGRESS_ENCODE)
                }
                verifyStaged(staging, outputSize, outputFormat)
                // Everything up to publication may cancel; once published the
                // copy must be fully committed before returning.
                coroutineContext.ensureActive()
                val result = publish(
                    raster.info.displayName, staging, outputSize, outputFormat, onPublished
                )
                onProgress(1f)
                result
            } finally {
                deleteStaging(staging)
            }
        } finally {
            raster.close()
            for (layerRaster in openLayers.values) layerRaster.close()
        }
    }

    /**
     * The container a source-shaped save uses. JPEG sources are re-encoded as
     * JPEG at the source's own quality: a lossless PNG of a photo is an order
     * of magnitude larger with no visible benefit, and camera originals are
     * the case that made saves balloon. PNG sources (which may carry alpha)
     * stay lossless. If this build has no native JPEG encoder the copy stays
     * PNG rather than the save failing.
     */
    private fun sourceExportFormatFor(raster: PhotoRaster): PhotoExportFormat =
        if ((raster.info.isRaw || (raster as? AndroidPhotoRaster)?.mimeType == MIME_JPEG) &&
            JpegScanlineEncoder.isAvailable()
        ) {
            PhotoExportFormat.JPEG
        } else {
            PhotoExportFormat.PNG
        }

    /**
     * Encodes the full-resolution result into [staging], band by band, in
     * [format]. PNG/JPEG/HEIF never hold the whole output: PNG compresses each
     * row through a streaming deflater, JPEG hands row batches to libjpeg and
     * HEIF buffers one tile row at a time for libheif's grid encoder. WebP and
     * JPEG XL expose no streaming encoder, so those two render into a single
     * full-frame bitmap under an explicit memory budget.
     */
    private suspend fun encode(
        raster: PhotoRaster,
        document: PhotoDocument,
        layerRasters: Map<String, PhotoRaster>,
        outputSize: PixelSize,
        format: PhotoExportFormat,
        staging: File,
        onProgress: (Float) -> Unit
    ) {
        when (format) {
            PhotoExportFormat.PNG -> FileOutputStream(staging).use { raw ->
                BufferedOutputStream(raw).use { stream ->
                    StreamingPngWriter(stream, outputSize).use { sink ->
                        renderTo(raster, document, layerRasters, sink, onProgress)
                    }
                    stream.flush()
                    raw.fd.sync()
                }
            }
            PhotoExportFormat.JPEG -> {
                if (JpegScanlineEncoder.isAvailable()) {
                    val quality = jpegQualityFor(raster)
                    encodeStreamed(
                        staging = staging,
                        openSink = { fd -> JpegScanlineWriter(fd, outputSize, quality) },
                        export = { sink ->
                            renderTo(raster, document, layerRasters, sink, onProgress)
                        }
                    )
                } else {
                    // No native encoder in this build: fall back to the
                    // platform's whole-bitmap JPEG writer under the same budget
                    // WebP/JPEG XL use.
                    val quality = jpegQualityFor(raster)
                    encodeFullFrame(
                        raster, document, layerRasters, outputSize, format, onProgress
                    ) { bitmap ->
                        writeBitmap(bitmap, Bitmap.CompressFormat.JPEG, quality, staging)
                    }
                }
            }
            PhotoExportFormat.WEBP -> encodeFullFrame(
                raster, document, layerRasters, outputSize, format, onProgress
            ) { bitmap ->
                writeBitmap(bitmap, webpCompressFormat(), WEBP_QUALITY, staging)
            }
            PhotoExportFormat.HEIF -> encodeStreamed(
                staging = staging,
                openSink = { fd -> HeifScanlineWriter(fd, outputSize, HEIF_QUALITY) },
                export = { sink ->
                    renderTo(raster, document, layerRasters, sink, onProgress)
                }
            )
            // Exported before this path: a TIFF never passes through the strips.
            PhotoExportFormat.TIFF_8, PhotoExportFormat.TIFF_16 ->
                throw UnsupportedPhotoException("TIFF is streamed from the RAW decoder")
            PhotoExportFormat.JXL -> encodeFullFrame(
                raster, document, layerRasters, outputSize, format, onProgress
            ) { bitmap ->
                writeJxl(bitmap, staging)
            }
        }
    }

    private suspend fun renderTo(
        raster: PhotoRaster,
        document: PhotoDocument,
        layerRasters: Map<String, PhotoRaster>,
        sink: ScanlineSink,
        onProgress: (Float) -> Unit
    ) = PhotoStripExporter(colourEffect).export(
        raster, document, sink, layerRasters, onProgress
    )

    /**
     * Streams the render into an encoder that owns [staging]'s descriptor
     * (libjpeg, libheif). The encoder writes through its own dup() of the fd,
     * so the shared file is flushed here before it is closed.
     */
    private suspend fun encodeStreamed(
        staging: File,
        openSink: (fd: Int) -> ScanlineSink,
        export: suspend (ScanlineSink) -> Unit
    ) {
        ParcelFileDescriptor.open(
            staging,
            ParcelFileDescriptor.MODE_CREATE or
                ParcelFileDescriptor.MODE_TRUNCATE or
                ParcelFileDescriptor.MODE_READ_WRITE
        ).use { descriptor ->
            openSink(descriptor.fd).use { sink -> export(sink) }
            val synced = runCatching { Os.fsync(descriptor.fileDescriptor) }.isSuccess
            if (!synced) throw IOException("cannot flush staged export $staging")
        }
    }

    /**
     * Renders the full output into one ARGB bitmap — the shape WebP and JPEG
     * XL encoders require — after checking the frame fits the process heap.
     * This is the one export path that is not memory-bounded; [format] names
     * it in the failure when a photo is too large for it.
     */
    private suspend fun encodeFullFrame(
        raster: PhotoRaster,
        document: PhotoDocument,
        layerRasters: Map<String, PhotoRaster>,
        outputSize: PixelSize,
        format: PhotoExportFormat,
        onProgress: (Float) -> Unit,
        write: (Bitmap) -> Unit
    ) {
        requireFullFrameBudget(outputSize, format)
        val bitmap = try {
            Bitmap.createBitmap(outputSize.width, outputSize.height, Bitmap.Config.ARGB_8888)
        } catch (e: OutOfMemoryError) {
            throw PhotoTooLargeException(
                "${format.name} export needs more memory than this photo allows"
            )
        }
        try {
            renderTo(raster, document, layerRasters, BitmapRowSink(bitmap), onProgress)
            write(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * Rejects a full-frame export the heap cannot hold with headroom: the
     * bitmap alone is four bytes per output pixel, and the encoders allocate
     * scratch buffers on top of it.
     */
    private fun requireFullFrameBudget(size: PixelSize, format: PhotoExportFormat) {
        val budgetPixels = Runtime.getRuntime().maxMemory() /
            FULL_FRAME_HEAP_DIVISOR / BYTES_PER_PIXEL
        if (size.pixelCount > budgetPixels) {
            throw PhotoTooLargeException(
                "${format.name} export needs more memory than this photo allows"
            )
        }
    }

    private fun writeBitmap(
        bitmap: Bitmap,
        compressFormat: Bitmap.CompressFormat,
        quality: Int,
        staging: File
    ) {
        FileOutputStream(staging).use { raw ->
            BufferedOutputStream(raw).use { stream ->
                if (!bitmap.compress(compressFormat, quality, stream)) {
                    throw IOException("platform encoder failed")
                }
                stream.flush()
                raw.fd.sync()
            }
        }
    }

    private fun writeJxl(bitmap: Bitmap, staging: File) {
        if (!JxlBitmapEncoder.isAvailable()) {
            throw UnsupportedPhotoException("JPEG XL export is not available on this device")
        }
        val encoded = JxlCoder.encode(
            bitmap,
            JxlChannelsConfiguration.RGBA,
            JxlCompressionOption.LOSSY,
            JxlEffort.FALCON,
            JXL_QUALITY,
            JxlDecodingSpeed.SLOWEST
        )
        FileOutputStream(staging).use { output ->
            output.write(encoded)
            output.fd.sync()
        }
    }

    private fun webpCompressFormat(): Bitmap.CompressFormat =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }

    /** A [ScanlineSink] that writes the export rows straight into [bitmap]. */
    private class BitmapRowSink(private val bitmap: Bitmap) : ScanlineSink {

        private var rows = 0

        override fun writeRow(argb: IntArray, offset: Int) {
            check(rows < bitmap.height) { "too many rows: image height is ${bitmap.height}" }
            bitmap.setPixels(argb, offset, bitmap.width, 0, rows, bitmap.width, 1)
            rows += 1
        }

        override fun finish() {
            check(rows == bitmap.height) {
                "expected ${bitmap.height} rows but wrote $rows"
            }
        }

        override fun close() = Unit
    }

    /**
     * The JPEG quality the copy is written at: the estimate of the source's own
     * quantization table, so an edit neither drops fidelity below the original
     * nor inflates the file past it. Unreadable tables fall back to
     * [DEFAULT_JPEG_QUALITY].
     */
    private suspend fun jpegQualityFor(raster: PhotoRaster): Int =
        (raster as? AndroidPhotoRaster)?.let {
            JpegQualityProbe.estimate(it.readPrefix(QUALITY_PROBE_BYTES))
                ?.coerceIn(MIN_JPEG_QUALITY, MAX_JPEG_QUALITY)
        } ?: DEFAULT_JPEG_QUALITY

    private fun deleteStaging(staging: File) {
        try {
            if (staging.exists() && !staging.delete()) {
                onError(IOException("could not delete staging file $staging"))
            }
        } catch (cleanup: Throwable) {
            onError(IOException("could not delete staging file $staging", cleanup))
        }
    }

    private fun parseReference(reference: String): Uri {
        val uri = Uri.parse(reference)
        return when (uri.scheme) {
            "content", "file" -> uri
            else -> throw IllegalArgumentException(
                "unsupported photo reference '$reference' — only content:// and file:// are accepted"
            )
        }
    }

    private fun openDescriptor(uri: Uri): ParcelFileDescriptor =
        descriptorOpener?.invoke(uri)
            ?: when (uri.scheme) {
                "content" -> appContext.contentResolver.openFileDescriptor(uri, "r")
                    ?: throw IOException("cannot open $uri")
                else -> ParcelFileDescriptor.open(
                    File(requireNotNull(uri.path) { "file URI without a path: $uri" }),
                    ParcelFileDescriptor.MODE_READ_ONLY
                )
            }

    /**
     * Reads the first bytes with [Os.pread] — position-independent and with no
     * ownership of the shared file descriptor (a FileInputStream over the same
     * fd would close it on GC). Only called on descriptors already proven
     * seekable.
     */
    private fun sniffMime(descriptor: ParcelFileDescriptor): String {
        val header = ByteArray(8)
        val read = try {
            Os.pread(descriptor.fileDescriptor, header, 0, header.size, 0L)
        } catch (e: ErrnoException) {
            throw IOException("cannot probe $descriptor", e)
        }
        if (read >= 3 && header[0] == 0xFF.toByte() &&
            header[1] == 0xD8.toByte() && header[2] == 0xFF.toByte()
        ) {
            return MIME_JPEG
        }
        if (read == header.size && header.contentEquals(PNG_MAGIC)) return MIME_PNG
        throw UnsupportedPhotoException("Only JPEG and PNG photos are supported")
    }

    /**
     * The user's own file name: `DISPLAY_NAME` from the provider for
     * `content://` (a bare last segment is just the MediaStore row id), the
     * path segment for files. Query failures are reported to [onError] and
     * fall back to the segment.
     */
    private fun displayName(uri: Uri): String {
        if (uri.scheme == "content") {
            try {
                appContext.contentResolver.query(
                    uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index >= 0) {
                            cursor.getString(index)
                                ?.takeIf { it.isNotBlank() }
                                ?.let { return it }
                        }
                    }
                }
            } catch (e: Exception) {
                onError(e)
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { null } ?: "photo"
    }

    private fun outputName(displayName: String, extension: String): String {
        val base = displayName
            .substringBeforeLast('.')
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(40)
            .ifBlank { "photo" }
        return "$base-edited-${UUID.randomUUID().toString().take(8)}.$extension"
    }

    /**
     * Proves the staged copy is a complete, correctly-sized image of the
     * requested container before it is published. JPEG/PNG/WebP/HEIF are
     * bounds-decoded by the platform; JPEG XL has no platform decoder, so its
     * own library reads the header.
     */
    private fun verifyStaged(
        staging: File,
        outputSize: PixelSize,
        format: PhotoExportFormat
    ) {
        if (staging.length() <= 0L) throw IOException("empty export staging file")
        if (format == PhotoExportFormat.JXL) {
            val size = runCatching {
                JxlCoder.getSize(staging.readBytes())
            }.getOrNull() ?: throw IOException("staged export failed verification: unreadable JXL")
            if (size.width != outputSize.width || size.height != outputSize.height) {
                throw IOException(
                    "staged export failed verification: ${size.width}x${size.height}, " +
                        "expected ${outputSize.width}x${outputSize.height} ${format.mimeType}"
                )
            }
            return
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(staging.absolutePath, bounds)
        if (bounds.outMimeType != format.mimeType ||
            bounds.outWidth != outputSize.width || bounds.outHeight != outputSize.height
        ) {
            throw IOException(
                "staged export failed verification: ${bounds.outMimeType} " +
                    "${bounds.outWidth}x${bounds.outHeight}, expected " +
                    "${outputSize.width}x${outputSize.height} ${format.mimeType}"
            )
        }
    }

    /**
     * Copies the staged file into a pending MediaStore row, then — inside one
     * non-cancellable commit — flips `IS_PENDING` off, invokes the
     * [onPublished] commit receipt and returns the result. Failure —
     * including cancellation — removes the inserted row; the copy loop
     * checks cancellation between chunks.
     */
    private suspend fun publish(
        displayName: String,
        staging: File,
        outputSize: PixelSize,
        outputFormat: PhotoExportFormat,
        onPublished: (PhotoExportResult) -> Unit
    ): PhotoExportResult {
        val resolver = appContext.contentResolver
        val values = ContentValues().apply {
            put(
                MediaStore.Images.Media.DISPLAY_NAME,
                outputName(displayName, outputFormat.extension)
            )
            put(MediaStore.Images.Media.MIME_TYPE, outputFormat.mimeType)
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ReFra")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("MediaStore insert returned no URI")
        try {
            resolver.openOutputStream(uri)?.use { output ->
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
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }
                if (resolver.update(uri, published, null, null) != 1) {
                    throw IOException("MediaStore publish failed for $uri")
                }
                val result = PhotoExportResult(
                    uri.toString(), outputFormat.mimeType, outputSize
                )
                onPublished(result)
                result
            }
        } catch (t: Throwable) {
            withContext(NonCancellable) {
                try {
                    resolver.delete(uri, null, null)
                } catch (cleanup: Throwable) {
                    t.addSuppressed(cleanup)
                    onError(cleanup)
                }
            }
            throw t
        }
    }

    private companion object {
        const val MIME_JPEG = "image/jpeg"
        const val MIME_PNG = "image/png"
        const val MAX_DIMENSION = 65_535
        const val MAX_PIXELS = 200_000_000L
        private const val RAW_SNIFF_BYTES = 16
        const val MAX_IMPORT_BYTES = 512L * 1024 * 1024
        const val COPY_BUFFER_BYTES = 65_536
        const val PROGRESS_ENCODE = 0.9f

        /** Header block probed for the source's JPEG quantization tables. */
        const val QUALITY_PROBE_BYTES = 256 * 1024

        /** Quality used when the source tables cannot be read. */
        const val DEFAULT_JPEG_QUALITY = 92

        /** Keeps the estimate inside libjpeg's sane, visible-fidelity range. */
        const val MIN_JPEG_QUALITY = 60
        const val MAX_JPEG_QUALITY = 98

        /** Lossy formats with no source to mirror use a high-fidelity default. */
        const val WEBP_QUALITY = 90
        const val HEIF_QUALITY = 90

        /**
         * JXL quality 90 is libjxl distance 1.0 — the "visually lossless"
         * point most encoders ship as default.
         */
        const val JXL_QUALITY = 90

        /** ARGB_8888 pixels are four bytes. */
        const val BYTES_PER_PIXEL = 4L

        /**
         * A full-frame export may use at most half the heap for its bitmap,
         * leaving the encoder's own buffers room.
         */
        const val FULL_FRAME_HEAP_DIVISOR = 2L

        val PNG_MAGIC = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        )
    }
}
