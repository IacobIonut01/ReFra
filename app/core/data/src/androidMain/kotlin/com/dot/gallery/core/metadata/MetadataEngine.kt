package com.dot.gallery.core.metadata

import com.dot.gallery.core.metadata.MediaContainerFormat.AVIF
import com.dot.gallery.core.metadata.MediaContainerFormat.GIF
import com.dot.gallery.core.metadata.MediaContainerFormat.HEIF
import com.dot.gallery.core.metadata.MediaContainerFormat.JP2
import com.dot.gallery.core.metadata.MediaContainerFormat.JPEG
import com.dot.gallery.core.metadata.MediaContainerFormat.JXL
import com.dot.gallery.core.metadata.MediaContainerFormat.MP4
import com.dot.gallery.core.metadata.MediaContainerFormat.PNG
import com.dot.gallery.core.metadata.MediaContainerFormat.QUICKTIME
import com.dot.gallery.core.metadata.MediaContainerFormat.THREE_GPP
import com.dot.gallery.core.metadata.MediaContainerFormat.WEBP
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.security.MessageDigest

/**
 * Format dispatch for metadata inspection, removal and verification. Images are parsed in memory;
 * ISO base media files (HEIF, AVIF, MP4, MOV, 3GP) are edited in place through a file channel so
 * large videos never have to fit in memory.
 */
internal object MetadataEngine {
    const val MAX_IN_MEMORY = 256L * 1024 * 1024

    private val inMemoryFormats = setOf(JPEG, PNG, WEBP, GIF, JP2, JXL)
    private val isoBmffFormats = setOf(HEIF, AVIF, MP4, QUICKTIME, THREE_GPP)

    fun limitation(format: MediaContainerFormat): String? = when (format) {
        in inMemoryFormats, in isoBmffFormats -> null
        MediaContainerFormat.BMP -> "BMP files carry no removable metadata."
        MediaContainerFormat.TIFF -> "TIFF and RAW files store metadata alongside image data and cannot be cleaned safely."
        MediaContainerFormat.PSD -> "Photoshop documents are not supported."
        MediaContainerFormat.SVG -> "SVG files are not supported."
        MediaContainerFormat.J2K -> "Raw JPEG 2000 codestreams carry no removable metadata."
        MediaContainerFormat.MATROSKA, MediaContainerFormat.WEBM, MediaContainerFormat.AVI,
        MediaContainerFormat.MPEG_TS, MediaContainerFormat.OGG -> "This video container is not supported."
        else -> "This file type could not be recognised."
    }

    private fun readAll(channel: FileChannel): ByteArray {
        val size = channel.size()
        if (size > MAX_IN_MEMORY) throw UnsupportedMetadataException("The file is too large to clean.")
        val buffer = ByteBuffer.allocate(size.toInt())
        var position = 0L
        while (buffer.hasRemaining()) {
            val read = channel.read(buffer, position)
            if (read < 0) malformed("File ended early")
            position += read
        }
        return buffer.array()
    }

    fun inspect(format: MediaContainerFormat, channel: FileChannel): MetadataInventory {
        val inventory = MetadataInventory()
        when (format) {
            in isoBmffFormats -> IsoBmffMetadata.inspect(ChannelRandomBytes(channel), inventory)
            in inMemoryFormats -> inspectBytes(format, readAll(channel), inventory)
            else -> Unit
        }
        return inventory
    }

    private fun inspectBytes(format: MediaContainerFormat, bytes: ByteArray, inventory: MetadataInventory) = when (format) {
        JPEG -> JpegMetadata.inspect(bytes, inventory)
        PNG -> PngMetadata.inspect(bytes, inventory)
        WEBP -> WebpMetadata.inspect(bytes, inventory)
        GIF -> GifMetadata.inspect(bytes, inventory)
        JP2, JXL -> BoxMetadata.inspect(bytes, inventory)
        else -> throw UnsupportedMetadataException("Unsupported format $format")
    }

    fun capability(format: MediaContainerFormat, channel: FileChannel): SanitizationCapability {
        limitation(format)?.let { return SanitizationCapability(format, emptySet(), limitation = it) }
        val inventory = inspect(format, channel)
        val removable = MetadataPolicy.ALL - inventory.blocked
        return SanitizationCapability(
            format = format,
            removableCategories = removable,
            presentCategories = inventory.present.toSet(),
            previews = inventory.previews.toMap(),
            hasOtherMetadata = inventory.other,
            limitation = if (inventory.blocked.isNotEmpty()) {
                "This video records location in a telemetry track that is part of the media, so it stays."
            } else null
        )
    }

    /** Writes a cleaned copy of [source] to [target]. */
    fun scrub(format: MediaContainerFormat, source: File, target: File, selection: MetadataSelection) {
        limitation(format)?.let { throw UnsupportedMetadataException(it) }
        if (format in isoBmffFormats) {
            source.copyTo(target, overwrite = true)
            RandomAccessFile(target, "rw").use { file ->
                IsoBmffMetadata.scrub(ChannelRandomBytes(file.channel), selection)
                file.channel.force(true)
            }
            return
        }
        val bytes = FileInputStream(source).use { readAll(it.channel) }
        val cleaned = when (format) {
            JPEG -> JpegMetadata.scrub(bytes, selection)
            PNG -> PngMetadata.scrub(bytes, selection)
            WEBP -> WebpMetadata.scrub(bytes, selection)
            GIF -> GifMetadata.scrub(bytes, selection)
            JP2, JXL -> BoxMetadata.scrub(bytes, selection)
            else -> throw UnsupportedMetadataException("Unsupported format $format")
        }
        RandomAccessFile(target, "rw").use { file ->
            file.setLength(0)
            file.write(cleaned)
            file.channel.force(true)
        }
    }

    private fun essence(format: MediaContainerFormat, file: File): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val channel = input.channel
            if (format in isoBmffFormats) {
                IsoBmffMetadata.essence(ChannelRandomBytes(channel), digest)
            } else {
                val bytes = readAll(channel)
                when (format) {
                    JPEG -> JpegMetadata.essence(bytes, digest)
                    PNG -> PngMetadata.essence(bytes, digest)
                    WEBP -> WebpMetadata.essence(bytes, digest)
                    GIF -> GifMetadata.essence(bytes, digest)
                    JP2, JXL -> BoxMetadata.essence(bytes, digest)
                    else -> throw UnsupportedMetadataException("Unsupported format $format")
                }
            }
        }
        return digest.digest()
    }

    private fun inspect(format: MediaContainerFormat, file: File): MetadataInventory =
        FileInputStream(file).use { inspect(format, it.channel) }

    /** Returns null when [candidate] is a valid removal of [selection] from [source], else the reason it is not. */
    fun verify(format: MediaContainerFormat, source: File, candidate: File, selection: MetadataSelection): String? {
        val before = inspect(format, source)
        val after = inspect(format, candidate)
        val leaked = after.present.filter(selection::removes)
        if (leaked.isNotEmpty()) return "Selected metadata is still present: ${leaked.joinToString { it.name }}"
        if (selection.everything && after.other) return "Unclassified metadata is still present"
        if (!selection.isEmpty && after.opaque) return "A metadata block that can only be removed whole is still present"
        val expected = before.classes.filter { classSet -> classSet.none(selection::removes) }.flatten().toSet()
        if (!after.present.containsAll(expected)) {
            return "Metadata that should have been kept was removed: ${(expected - after.present).joinToString { it.name }}"
        }
        if (!after.functional.containsAll(before.functional)) {
            return "Functional metadata changed: ${(before.functional - after.functional).joinToString()}"
        }
        if (selection.removes(MetadataCategory.LOCATION) && format in inMemoryFormats && before.locationSignatures.isNotEmpty()) {
            val bytes = FileInputStream(candidate).use { readAll(it.channel) }
            if (before.locationSignatures.any { bytes.indexOf(it) >= 0 }) return "Location data is still present in the file"
        }
        if (!essence(format, source).contentEquals(essence(format, candidate))) return "The image or video data changed"
        return null
    }
}
