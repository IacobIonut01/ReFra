package com.dot.gallery.editor.desktop

import com.dot.gallery.editor.core.model.PhotoDocument
import com.dot.gallery.editor.core.model.PhotoExportFormat
import com.dot.gallery.editor.core.model.PhotoMapping
import com.dot.gallery.editor.core.model.PixelSize
import com.dot.gallery.editor.io.JpegExifOrientation
import com.dot.gallery.editor.io.NativeColorEffect
import com.dot.gallery.editor.io.StreamingPngWriter
import com.dot.gallery.editor.render.BasicColorEffect
import com.dot.gallery.editor.render.FrameEffect
import com.dot.gallery.editor.render.photo.PhotoExportResult
import com.dot.gallery.editor.render.photo.PhotoInfo
import com.dot.gallery.editor.render.photo.PhotoMediaAccess
import com.dot.gallery.editor.render.photo.PhotoRaster
import com.dot.gallery.editor.render.photo.PhotoStripExporter
import com.dot.gallery.editor.render.photo.PhotoTooLargeException
import com.dot.gallery.editor.render.photo.UnsupportedPhotoException
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import javax.imageio.ImageIO
import javax.imageio.ImageReader
import kotlin.coroutines.coroutineContext
import kotlin.io.path.isReadable
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Desktop [PhotoMediaAccess]: opens `file://` URIs and plain filesystem paths
 * through ImageIO and exports edited PNG copies next to the source (or into
 * [outputDirectory]).
 *
 * Sources are verified by magic bytes (JPEG `FF D8`, PNG signature) and gated
 * to JPEG/PNG — declared MIME and extensions are not trusted. Exports write a
 * `.refra-*.tmp` staging file in the destination directory, fsync it, verify
 * bounds, then move it onto `<base>-edited-<uuid>.png` without replacing
 * anything. The publication move plus the `onPublished` commit receipt run
 * under [NonCancellable]: once encoding completed, a racing cancel cannot
 * leave a half-published file or lose a successful result. The source is
 * never touched and no metadata is carried over — EXIF orientation is
 * applied, not copied. [onError] receives cleanup failures that must not be
 * swallowed.
 */
class DesktopPhotoMediaAccess(
    private val outputDirectory: Path? = null,
    private val onError: (Throwable) -> Unit,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val preferNative: Boolean = true
) : PhotoMediaAccess {

    // The colour stage of the strip exporter: the packaged C kernel when it
    // loads, the Kotlin reference when it does not. Created lazily on the
    // export IO thread — the JNI load never runs on a UI thread; the kernel
    // is stateless so one instance is safe across exports.
    private val colourEffect: FrameEffect by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        if (preferNative) {
            NativeColorEffect(DesktopEditorNativeLoader(), onUnavailable = onError)
        } else {
            BasicColorEffect()
        }
    }

    override suspend fun open(reference: String): PhotoRaster {
        // A prompt cancellation can discard the withContext result before the
        // caller receives it — hold the raster so it is closed anyway.
        var acquired: DesktopPhotoRaster? = null
        try {
            return withContext(ioDispatcher) {
                val raster = openOnIo(reference)
                acquired = raster
                raster
            }
        } catch (t: Throwable) {
            acquired?.close()
            throw t
        }
    }

    private fun openOnIo(reference: String): DesktopPhotoRaster {
        val path = parseReference(reference)
        val mime = sniffMime(path)
        val input = try {
            ImageIO.createImageInputStream(path.toFile())
        } catch (e: IOException) {
            throw IOException("cannot read $reference", e)
        } ?: throw IOException("no image stream for $reference")
        var reader: ImageReader? = null
        try {
            val readers = ImageIO.getImageReaders(input)
            if (!readers.hasNext()) throw IOException("no ImageIO reader for $reference")
            reader = readers.next()
            reader.input = input
            val width = reader.getWidth(0)
            val height = reader.getHeight(0)
            if (width > MAX_DIMENSION || height > MAX_DIMENSION ||
                width.toLong() * height > MAX_PIXELS
            ) {
                throw PhotoTooLargeException(
                    "image ${width}x$height exceeds limits " +
                        "($MAX_DIMENSION max dimension, $MAX_PIXELS max pixels)"
                )
            }
            val orientation = if (mime == MIME_JPEG) {
                Files.newInputStream(path).use { JpegExifOrientation.read(it) }
            } else {
                1
            }
            val info = PhotoInfo(
                reference = reference,
                displayName = path.name,
                rawSize = PixelSize(width, height),
                orientation = orientation
            )
            return DesktopPhotoRaster(info, reader, input, ioDispatcher)
        } catch (t: Throwable) {
            // Both allocated halves are released; cleanup failures attach to
            // the primary error rather than replacing it.
            try {
                reader?.dispose()
            } catch (cleanup: Throwable) {
                t.addSuppressed(cleanup)
                onError(cleanup)
            }
            try {
                input.close()
            } catch (cleanup: Throwable) {
                t.addSuppressed(cleanup)
                onError(cleanup)
            }
            throw t
        }
    }

    private fun parseReference(reference: String): Path {
        val path = if (reference.startsWith("file:")) {
            Path.of(java.net.URI.create(reference))
        } else {
            if (reference.contains("://")) {
                throw IllegalArgumentException(
                    "unsupported photo reference '$reference' — only file paths and file:// URIs are accepted"
                )
            }
            Path.of(reference)
        }
        if (!path.isRegularFile() || !path.isReadable()) {
            throw IOException("not a readable file: $reference")
        }
        return path
    }

    private fun sniffMime(path: Path): String {
        val header = ByteArray(8)
        val read = Files.newInputStream(path).use { it.read(header, 0, header.size) }
        if (read >= 3 && header[0] == 0xFF.toByte() &&
            header[1] == 0xD8.toByte() && header[2] == 0xFF.toByte()
        ) {
            return MIME_JPEG
        }
        if (read == header.size && header.contentEquals(PNG_MAGIC)) return MIME_PNG
        throw UnsupportedPhotoException("Only JPEG and PNG photos are supported")
    }

    override suspend fun export(
        document: PhotoDocument,
        format: PhotoExportFormat?,
        onPublished: (PhotoExportResult) -> Unit,
        onProgress: (Float) -> Unit
    ): PhotoExportResult = withContext(ioDispatcher) {
        // The desktop port streams lossless PNG only; the shared UI lists
        // exactly [supportedExportFormats], so this is a contract guard.
        if (format != null && format != PhotoExportFormat.PNG) {
            throw UnsupportedPhotoException(
                "${format.name} export is not supported on this platform"
            )
        }
        val raster = openOnIo(document.source.reference)
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
            val sourcePath = parseReference(document.source.reference)
            val directory = outputDirectory ?: sourcePath.toAbsolutePath().parent
                ?: throw IOException("source has no parent directory")
            if (!Files.isDirectory(directory)) {
                throw IOException("output directory does not exist: $directory")
            }
            val staging = directory.resolve(".refra-${UUID.randomUUID()}.tmp")
            var failure: Throwable? = null
            try {
                FileOutputStream(staging.toFile()).use { raw ->
                    BufferedOutputStream(raw).use { stream ->
                        StreamingPngWriter(stream, outputSize).use { sink ->
                            PhotoStripExporter(colourEffect).export(
                                raster, document, sink, layerRasters
                            ) { progress ->
                                onProgress(progress * PROGRESS_ENCODE)
                            }
                        }
                        stream.flush()
                        raw.fd.sync()
                    }
                }
                verifyStaged(staging, outputSize)
                val target = directory.resolve(
                    "${sanitizeBase(sourcePath)}-edited-${UUID.randomUUID().toString().take(8)}.png"
                )
                // Commit boundary: once encode+verify succeeded the move and
                // the publication receipt run to completion even if the
                // caller is cancelled right now.
                coroutineContext.ensureActive()
                val result = withContext(NonCancellable) {
                    Files.move(staging, target)
                    val published = PhotoExportResult(
                        target.toAbsolutePath().toUri().toString(),
                        MIME_PNG,
                        outputSize
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
                    // Cleanup never masks the real outcome.
                    failure?.addSuppressed(cleanup)
                    onError(IOException("could not delete staging file $staging", cleanup))
                }
            }
        } finally {
            raster.close()
            for (layerRaster in openLayers.values) layerRaster.close()
        }
    }

    private fun sanitizeBase(path: Path): String =
        path.nameWithoutExtension
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(40)
            .ifBlank { "photo" }

    /** Header-only verification: format + dimensions, no pixel decode. */
    private fun verifyStaged(staging: Path, outputSize: PixelSize) {
        if (Files.size(staging) <= 0L) throw IOException("empty export staging file")
        val input = ImageIO.createImageInputStream(staging.toFile())
            ?: throw IOException("cannot reopen staged export")
        input.use {
            val readers = ImageIO.getImageReaders(input)
            if (!readers.hasNext()) throw IOException("staged export is not a readable image")
            val reader = readers.next()
            try {
                reader.input = input
                if (!reader.formatName.equals("png", ignoreCase = true) ||
                    reader.getWidth(0) != outputSize.width ||
                    reader.getHeight(0) != outputSize.height
                ) {
                    throw IOException(
                        "staged export failed verification: ${reader.formatName} " +
                            "${reader.getWidth(0)}x${reader.getHeight(0)}, expected " +
                            "${outputSize.width}x${outputSize.height} png"
                    )
                }
            } finally {
                reader.dispose()
            }
        }
    }

    private companion object {
        const val MIME_JPEG = "image/jpeg"
        const val MIME_PNG = "image/png"
        const val MAX_DIMENSION = 65_535
        const val MAX_PIXELS = 200_000_000L
        const val PROGRESS_ENCODE = 0.9f
        val PNG_MAGIC = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        )
    }
}
