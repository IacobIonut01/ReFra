package com.dot.gallery.core.metadata

import com.dot.gallery.core.logging.printWarn
import com.dot.gallery.core.metadata.MetadataCategory.DESCRIPTION_AUTHORSHIP
import java.security.MessageDigest

/** GIF: comment extensions and application extensions (XMP, vendor data); frames are copied as is. */
internal object GifMetadata {
    private val keptApplications = setOf("NETSCAPE2.0", "ANIMEXTS1.0", "ICCRGBG1012")
    private const val XMP_APPLICATION = "XMP DataXMP"

    private enum class Kind { COMMENT, APPLICATION, STRUCTURE }

    private class Block(val kind: Kind, val start: Int, val end: Int, val application: String? = null)

    private fun skipSubBlocks(bytes: ByteArray, from: Int): Int {
        var offset = from
        while (true) {
            val length = bytes.u8(offset)
            offset += 1 + length
            if (length == 0) return offset
            if (offset > bytes.size) malformed("GIF sub-block overruns the file")
        }
    }

    private fun blocks(bytes: ByteArray): List<Block> {
        if (!bytes.startsWithAscii("GIF87a") && !bytes.startsWithAscii("GIF89a")) malformed("Missing GIF header")
        val blocks = mutableListOf<Block>()
        val flags = bytes.u8(10)
        var offset = 13 + if (flags and 0x80 != 0) 3 * (1 shl ((flags and 0x07) + 1)) else 0
        if (offset > bytes.size) malformed("GIF colour table overruns the file")
        blocks += Block(Kind.STRUCTURE, 0, offset)
        while (offset < bytes.size) {
            val start = offset
            when (bytes.u8(offset)) {
                0x3B -> {
                    blocks += Block(Kind.STRUCTURE, start, bytes.size)
                    return blocks
                }
                0x21 -> {
                    val label = bytes.u8(offset + 1)
                    if (label == 0xFF && bytes.u8(offset + 2) == 11) {
                        val application = bytes.ascii(offset + 3, 11)
                        blocks += Block(Kind.APPLICATION, start, skipSubBlocks(bytes, offset + 14), application)
                    } else {
                        val end = skipSubBlocks(bytes, offset + 2)
                        blocks += Block(if (label == 0xFE) Kind.COMMENT else Kind.STRUCTURE, start, end)
                    }
                }
                0x2C -> {
                    val localFlags = bytes.u8(offset + 9)
                    var cursor = offset + 10 + if (localFlags and 0x80 != 0) 3 * (1 shl ((localFlags and 0x07) + 1)) else 0
                    cursor += 1 // LZW minimum code size
                    blocks += Block(Kind.STRUCTURE, start, skipSubBlocks(bytes, cursor))
                }
                else -> malformed("Unknown GIF block at $offset")
            }
            offset = blocks.last().end
        }
        malformed("GIF has no trailer")
    }

    private fun classify(block: Block): MetadataClass = when (block.kind) {
        Kind.STRUCTURE -> MetadataClass.Keep
        Kind.COMMENT -> MetadataClass.of(DESCRIPTION_AUTHORSHIP)
        Kind.APPLICATION -> when (block.application) {
            in keptApplications -> MetadataClass.Keep
            XMP_APPLICATION -> MetadataClass.Opaque
            else -> MetadataClass.Other
        }
    }

    private fun subBlockData(bytes: ByteArray, from: Int, end: Int): ByteArray {
        val sink = ByteSink()
        var offset = from
        while (offset < end) {
            val length = bytes.u8(offset)
            if (length == 0) break
            sink.write(bytes, offset + 1, length)
            offset += 1 + length
        }
        return sink.toByteArray()
    }

    fun inspect(bytes: ByteArray, inventory: MetadataInventory) {
        blocks(bytes).forEach { block ->
            val metadataClass = classify(block)
            when {
                block.kind == Kind.COMMENT -> inventory.record(metadataClass, subBlockData(bytes, block.start + 2, block.end).cleanText())
                block.application == XMP_APPLICATION -> {
                    inventory.record(metadataClass)
                    // XMP in GIF is stored raw with a "magic trailer" that is not valid XML.
                    val data = subBlockDataRaw(bytes, block.start + 14, block.end)
                    val end = data.indexOf("</x:xmpmeta>".toByteArray()).takeIf { it >= 0 }
                    if (end != null) {
                        runCatching { XmpMetadata.inspect(data.copyOfRange(0, end + 12), inventory) }
                            .onFailure { printWarn("media-ops.metadata", "GIF XMP could not be inspected: ${it.message}") }
                    }
                }
                else -> inventory.record(metadataClass)
            }
        }
    }

    private fun subBlockDataRaw(bytes: ByteArray, from: Int, end: Int): ByteArray = bytes.copyOfRange(from, end)

    fun scrub(bytes: ByteArray, selection: MetadataSelection): ByteArray {
        val out = ByteSink(bytes.size)
        blocks(bytes).forEach { block ->
            if (!selection.removes(classify(block))) out.write(bytes, block.start, block.end - block.start)
        }
        return out.toByteArray()
    }

    fun essence(bytes: ByteArray, digest: MessageDigest) {
        blocks(bytes).forEach { block ->
            if (block.kind == Kind.STRUCTURE || classify(block) == MetadataClass.Keep) digest.update(bytes, block.start, block.end - block.start)
        }
    }
}
