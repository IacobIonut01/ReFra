package com.dot.gallery.core.metadata

import com.dot.gallery.core.metadata.MetadataCategory.DESCRIPTION_AUTHORSHIP
import com.dot.gallery.core.metadata.MetadataCategory.EDIT_HISTORY
import com.dot.gallery.core.metadata.MetadataCategory.IDENTITY_DEVICE
import com.dot.gallery.core.metadata.MetadataCategory.PROVENANCE
import com.dot.gallery.core.metadata.MetadataCategory.TIMESTAMPS
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.Inflater

/** PNG and APNG: text chunks, eXIf, ImageMagick raw profiles, tIME and C2PA. */
internal object PngMetadata {
    private val SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    private const val XMP_KEYWORD = "XML:com.adobe.xmp"
    private const val MAX_INFLATED = 16 * 1024 * 1024

    private val keptChunks = setOf(
        "IHDR", "PLTE", "IDAT", "IEND", "tRNS", "cHRM", "gAMA", "iCCP", "sRGB", "sBIT", "cICP", "mDCv",
        "cLLi", "bKGD", "pHYs", "hIST", "sPLT", "acTL", "fcTL", "fdAT"
    )

    private class Chunk(val type: String, val start: Int, val dataStart: Int, val length: Int) {
        val end: Int get() = dataStart + length + 4
    }

    /** A decoded tEXt, zTXt or iTXt chunk. */
    private class Text(val keyword: String, val value: ByteArray, val international: Boolean)

    private fun chunks(bytes: ByteArray): List<Chunk> {
        if (!bytes.startsWith(SIGNATURE)) malformed("Missing PNG signature")
        val chunks = mutableListOf<Chunk>()
        var offset = SIGNATURE.size
        while (offset < bytes.size) {
            val length = bytes.u32(offset)
            if (length > bytes.size - offset - 12) malformed("PNG chunk overruns the file")
            val type = bytes.ascii(offset + 4, 4)
            if (!type.all { it.isLetter() }) malformed("PNG chunk type is invalid")
            val chunk = Chunk(type, offset, offset + 8, length.toInt())
            chunks += chunk
            offset = chunk.end
            if (type == "IEND") break
        }
        if (chunks.firstOrNull()?.type != "IHDR" || chunks.last().type != "IEND") malformed("PNG chunk order is invalid")
        return chunks
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

    private fun data(bytes: ByteArray, chunk: Chunk) = bytes.copyOfRange(chunk.dataStart, chunk.dataStart + chunk.length)

    private fun text(bytes: ByteArray, chunk: Chunk): Text {
        val data = data(bytes, chunk)
        val keywordEnd = data.indexOf(0).takeIf { it in 1..79 } ?: malformed("PNG text keyword is invalid")
        val keyword = String(data, 0, keywordEnd, Charsets.ISO_8859_1)
        return when (chunk.type) {
            "tEXt" -> Text(keyword, data.copyOfRange(keywordEnd + 1, data.size), false)
            "zTXt" -> Text(keyword, inflate(data.copyOfRange(keywordEnd + 2, data.size)), false)
            else -> {
                val compressed = data.u8(keywordEnd + 1) == 1
                var cursor = keywordEnd + 3
                repeat(2) {
                    val end = data.indexOf(0, cursor)
                    if (end < 0) malformed("PNG iTXt header is truncated")
                    cursor = end + 1
                }
                val value = data.copyOfRange(cursor, data.size)
                Text(keyword, if (compressed) inflate(value) else value, true)
            }
        }
    }

    private fun ByteArray.indexOf(value: Byte, from: Int = 0): Int {
        for (i in from until size) if (this[i] == value) return i
        return -1
    }

    private fun inflate(data: ByteArray): ByteArray {
        val inflater = Inflater()
        try {
            inflater.setInput(data)
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count == 0 && (inflater.needsInput() || inflater.needsDictionary())) malformed("PNG compressed text is truncated")
                out.write(buffer, 0, count)
                if (out.size() > MAX_INFLATED) malformed("PNG compressed text is too large")
            }
            return out.toByteArray()
        } catch (e: java.util.zip.DataFormatException) {
            throw MalformedMetadataException("PNG compressed text is corrupt")
        } finally {
            inflater.end()
        }
    }

    private fun classifyText(keyword: String): MetadataClass = when {
        keyword == XMP_KEYWORD -> MetadataClass.Keep
        keyword.startsWith("Raw profile type ") -> MetadataClass.Opaque
        keyword == "Creation Time" || keyword.startsWith("date:") -> MetadataClass.of(TIMESTAMPS)
        keyword == "Software" -> MetadataClass.of(EDIT_HISTORY)
        keyword == "Source" -> MetadataClass.of(IDENTITY_DEVICE)
        else -> MetadataClass.of(DESCRIPTION_AUTHORSHIP)
    }

    private fun classifyChunk(type: String): MetadataClass = when {
        type in keptChunks -> MetadataClass.Keep
        type == "tIME" -> MetadataClass.of(TIMESTAMPS)
        type == "caBX" -> MetadataClass.of(PROVENANCE)
        type[0].isUpperCase() -> MetadataClass.Keep
        else -> MetadataClass.Other
    }

    private fun isText(type: String) = type == "tEXt" || type == "zTXt" || type == "iTXt"

    private fun rawExif(value: ByteArray): ByteArray? {
        // ImageMagick layout: newline, profile name, newline, decimal length, then hex lines.
        val text = String(value, Charsets.ISO_8859_1).trim()
        val lines = text.lines()
        if (lines.size < 3) return null
        val hex = lines.drop(2).joinToString("").filter { !it.isWhitespace() }
        if (hex.length % 2 != 0) return null
        val decoded = ByteArray(hex.length / 2)
        for (i in decoded.indices) decoded[i] = (hex.substring(i * 2, i * 2 + 2).toIntOrNull(16) ?: return null).toByte()
        val tiff = decoded.indexOf(byteArrayOf(0x49, 0x49, 0x2A, 0x00)).takeIf { it >= 0 }
            ?: decoded.indexOf(byteArrayOf(0x4D, 0x4D, 0x00, 0x2A))
        return if (tiff >= 0) decoded.copyOfRange(tiff, decoded.size) else null
    }

    fun inspect(bytes: ByteArray, inventory: MetadataInventory) {
        chunks(bytes).forEach { chunk ->
            when {
                chunk.type == "eXIf" -> TiffMetadata.inspect(data(bytes, chunk), inventory)
                isText(chunk.type) -> {
                    val text = text(bytes, chunk)
                    val metadataClass = classifyText(text.keyword)
                    when {
                        text.keyword == XMP_KEYWORD -> XmpMetadata.inspect(text.value, inventory)
                        metadataClass == MetadataClass.Opaque -> {
                            inventory.record(metadataClass)
                            if (text.keyword.endsWith("exif") || text.keyword.endsWith("APP1")) {
                                rawExif(text.value)?.let { TiffMetadata.inspect(it, inventory) }
                            }
                        }
                        else -> inventory.record(metadataClass, String(text.value, if (text.international) Charsets.UTF_8 else Charsets.ISO_8859_1))
                    }
                }
                else -> inventory.record(classifyChunk(chunk.type))
            }
        }
    }

    fun scrub(bytes: ByteArray, selection: MetadataSelection): ByteArray {
        val out = ByteSink(bytes.size)
        out.write(SIGNATURE)
        chunks(bytes).forEach { chunk ->
            when {
                chunk.type == "eXIf" -> {
                    val result = TiffMetadata.scrub(data(bytes, chunk), selection)
                    if (result.changed) writeChunk(out, "eXIf", result.bytes.copyOf(result.usedLength)) else copy(out, bytes, chunk)
                }
                isText(chunk.type) -> {
                    val text = text(bytes, chunk)
                    if (text.keyword == XMP_KEYWORD) {
                        val packet = XmpMetadata.scrub(text.value, selection) ?: return@forEach
                        if (packet === text.value) copy(out, bytes, chunk) else writeChunk(out, "iTXt", xmpChunk(packet))
                    } else if (!selection.removes(classifyText(text.keyword))) {
                        copy(out, bytes, chunk)
                    }
                }
                !selection.removes(classifyChunk(chunk.type)) -> copy(out, bytes, chunk)
            }
        }
        return out.toByteArray()
    }

    private fun xmpChunk(packet: ByteArray): ByteArray {
        val sink = ByteSink(packet.size + 32)
        sink.ascii(XMP_KEYWORD)
        sink.write(0) // keyword terminator
        sink.write(0) // uncompressed
        sink.write(0) // compression method
        sink.write(0) // empty language tag
        sink.write(0) // empty translated keyword
        sink.write(packet)
        return sink.toByteArray()
    }

    private fun copy(out: ByteSink, bytes: ByteArray, chunk: Chunk) = out.write(bytes, chunk.start, chunk.end - chunk.start)

    private fun writeChunk(out: ByteSink, type: String, data: ByteArray) {
        out.writeU32(data.size.toLong())
        val typeBytes = type.toByteArray(Charsets.ISO_8859_1)
        out.write(typeBytes)
        out.write(data)
        val crc = CRC32()
        crc.update(typeBytes)
        crc.update(data)
        out.writeU32(crc.value)
    }

    fun essence(bytes: ByteArray, digest: MessageDigest) {
        chunks(bytes).forEach { chunk ->
            if (!isText(chunk.type) && chunk.type != "eXIf" && classifyChunk(chunk.type) == MetadataClass.Keep) {
                digest.update(bytes, chunk.start, chunk.end - chunk.start)
            }
        }
    }
}
