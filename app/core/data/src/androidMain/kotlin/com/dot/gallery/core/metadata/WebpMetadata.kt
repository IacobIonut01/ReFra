package com.dot.gallery.core.metadata

import java.security.MessageDigest

/** WebP RIFF: EXIF and XMP chunks, with the VP8X feature flags kept in step with what remains. */
internal object WebpMetadata {
    private val keptChunks = setOf("VP8 ", "VP8L", "VP8X", "ALPH", "ANIM", "ANMF", "ICCP")
    private val EXIF_PREFIX = "Exif\u0000\u0000".toByteArray(Charsets.ISO_8859_1)
    private const val FLAG_ICC = 0x20
    private const val FLAG_ALPHA = 0x10
    private const val FLAG_EXIF = 0x08
    private const val FLAG_XMP = 0x04
    private const val FLAG_ANIMATION = 0x02

    private class Chunk(val type: String, val start: Int, val dataStart: Int, val length: Int) {
        val end: Int get() = dataStart + length + (length and 1)
    }

    private fun chunks(bytes: ByteArray): List<Chunk> {
        if (!bytes.startsWithAscii("RIFF") || !bytes.startsWithAscii("WEBP", 8)) malformed("Missing WebP header")
        val riffEnd = minOf(bytes.size.toLong(), 8 + bytes.u32(4, little = true)).toInt()
        val chunks = mutableListOf<Chunk>()
        var offset = 12
        while (offset + 8 <= riffEnd) {
            val length = bytes.u32(offset + 4, little = true)
            if (length > riffEnd - offset - 8) malformed("WebP chunk overruns the file")
            val chunk = Chunk(bytes.ascii(offset, 4), offset, offset + 8, length.toInt())
            chunks += chunk
            offset = minOf(chunk.end, riffEnd)
        }
        if (chunks.isEmpty()) malformed("WebP has no chunks")
        return chunks
    }

    private fun data(bytes: ByteArray, chunk: Chunk) = bytes.copyOfRange(chunk.dataStart, chunk.dataStart + chunk.length)

    private fun tiffStart(data: ByteArray): Int =
        if (data.size >= EXIF_PREFIX.size && EXIF_PREFIX.indices.all { data[it] == EXIF_PREFIX[it] }) EXIF_PREFIX.size else 0

    private fun classify(type: String): MetadataClass = if (type in keptChunks) MetadataClass.Keep else MetadataClass.Other

    fun inspect(bytes: ByteArray, inventory: MetadataInventory) {
        chunks(bytes).forEach { chunk ->
            when (chunk.type) {
                "EXIF" -> data(bytes, chunk).let { TiffMetadata.inspect(it.copyOfRange(tiffStart(it), it.size), inventory) }
                "XMP " -> XmpMetadata.inspect(data(bytes, chunk), inventory)
                else -> inventory.record(classify(chunk.type))
            }
        }
    }

    fun scrub(bytes: ByteArray, selection: MetadataSelection): ByteArray {
        val out = ByteSink(bytes.size)
        out.ascii("RIFF")
        out.writeU32(0)
        out.ascii("WEBP")
        var vp8xAt = -1
        chunks(bytes).forEach { chunk ->
            when (chunk.type) {
                "EXIF" -> {
                    val data = data(bytes, chunk)
                    val start = tiffStart(data)
                    val result = TiffMetadata.scrub(data.copyOfRange(start, data.size), selection)
                    if (result.changed) {
                        writeChunk(out, "EXIF", data.copyOfRange(0, start) + result.bytes.copyOf(result.usedLength))
                    } else {
                        out.write(bytes, chunk.start, chunk.end - chunk.start)
                    }
                }
                "XMP " -> {
                    val original = data(bytes, chunk)
                    val packet = XmpMetadata.scrub(original, selection) ?: return@forEach
                    if (packet === original) out.write(bytes, chunk.start, chunk.end - chunk.start) else writeChunk(out, "XMP ", packet)
                }
                else -> {
                    if (selection.removes(classify(chunk.type))) return@forEach
                    if (chunk.type == "VP8X") vp8xAt = out.size
                    out.write(bytes, chunk.start, chunk.end - chunk.start)
                }
            }
        }
        val result = out.toByteArray()
        result.putU32(4, (result.size - 8).toLong(), little = true)
        if (vp8xAt >= 0) {
            val present = chunks(result).map { it.type }.toSet()
            var flags = result.u8(vp8xAt + 8) and (FLAG_ICC or FLAG_ALPHA or FLAG_EXIF or FLAG_XMP or FLAG_ANIMATION).inv()
            if ("ICCP" in present) flags = flags or FLAG_ICC
            if (result.u8(vp8xAt + 8) and FLAG_ALPHA != 0) flags = flags or FLAG_ALPHA
            if ("EXIF" in present) flags = flags or FLAG_EXIF
            if ("XMP " in present) flags = flags or FLAG_XMP
            if ("ANIM" in present) flags = flags or FLAG_ANIMATION
            result[vp8xAt + 8] = flags.toByte()
        }
        return result
    }

    private fun writeChunk(out: ByteSink, type: String, data: ByteArray) {
        out.ascii(type)
        out.writeU32(data.size.toLong(), little = true)
        out.write(data)
        out.align2()
    }

    /** Image chunks plus the VP8X canvas, excluding the metadata flags that removal updates. */
    fun essence(bytes: ByteArray, digest: MessageDigest) {
        chunks(bytes).forEach { chunk ->
            if (classify(chunk.type) != MetadataClass.Keep) return@forEach
            if (chunk.type == "VP8X") {
                digest.update((bytes[chunk.dataStart].toInt() and (FLAG_ICC or FLAG_ALPHA or FLAG_ANIMATION)).toByte())
                digest.update(bytes, chunk.dataStart + 1, chunk.length - 1)
            } else {
                digest.update(bytes, chunk.start, chunk.end - chunk.start)
            }
        }
    }
}
