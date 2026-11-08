/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.handover

import java.io.ByteArrayOutputStream

/**
 * HPKG batch envelope writer — Handover Protocol v1 (ReFra-Handover
 * `src/handover/envelope.py`, docs/protocol.md §envelope).
 *
 * Little-endian layout:
 * ```
 * u8[4]  magic "HPKG"
 * u16    format version (1)
 * u16    flags (bit0: each item payload is gzip-compressed)
 * u32    manifest JSON byte length
 * u8[*]  manifest JSON: {engine?, client?, sink, items:[{id, kind, mime, offset, length, ...}]}
 * u8[*]  item payloads back-to-back at the declared offsets (relative to the
 *        start of the payload region)
 * ```
 *
 * Pure Kotlin — no Android dependencies, host-testable.
 */
object HandoverEnvelope {

    const val VERSION = 1
    const val FLAG_GZIP_ITEMS = 0x0001

    const val KIND_IMAGE = "image"
    const val KIND_VIDEO = "video"
    const val KIND_EMBEDDING = "embedding"

    /** Where computed embeddings land. */
    const val SINK_CLIENT = "client" // returned in job results only (inference mode)
    const val SINK_SERVER = "server" // upserted into the caller's server-side index
    const val SINK_BOTH = "both"     // indexed server-side AND returned

    /** One batch item. [id] is the media id string the server echoes back in results. */
    data class WireItem(
        val id: String,
        val kind: String,
        val mime: String,
        val payload: ByteArray,
        val timestamp: Long = 0L,
        val sourceRevision: String = "",
        /** kind=video: payload is concatenated JPEG frames at these offsets. */
        val frameOffsets: LongArray? = null,
        /** kind=embedding: vector dimension; payload must be dim*4 fp32 LE bytes. */
        val dim: Int = 0,
    ) {
        /** Whether [payload] is gzip-compressed before writing. */
        var gzip: Boolean = false
    }

    /**
     * Encodes [items] into an HPKG envelope.
     *
     * @param engine engine id override (server uses its default when null)
     * @param sink   one of [SINK_CLIENT]/[SINK_SERVER]/[SINK_BOTH]
     * @param client informational client block (app/build) echoed in job listings
     * @param gzipItems compress every payload (sets flag bit0)
     */
    fun write(
        items: List<WireItem>,
        engine: String? = null,
        sink: String = SINK_CLIENT,
        client: Map<String, String> = emptyMap(),
        gzipItems: Boolean = false,
    ): ByteArray {
        require(sink in setOf(SINK_CLIENT, SINK_SERVER, SINK_BOTH)) { "bad sink $sink" }
        val blob = ByteArrayOutputStream()
        val manifestItems = StringBuilder()
        var offset = 0L
        items.forEachIndexed { i, item ->
            val wire = if (gzipItems) gzip(item.payload) else item.payload
            if (i > 0) manifestItems.append(',')
            manifestItems.append('{')
                .append("\"id\":").append(jsonString(item.id)).append(',')
                .append("\"kind\":").append(jsonString(item.kind)).append(',')
                .append("\"mime\":").append(jsonString(item.mime)).append(',')
                .append("\"offset\":").append(offset).append(',')
                .append("\"length\":").append(wire.size).append(',')
                .append("\"timestamp\":").append(item.timestamp).append(',')
                .append("\"sourceRevision\":").append(jsonString(item.sourceRevision))
            if (item.frameOffsets != null) {
                manifestItems.append(',')
                    .append("\"frameCount\":").append(item.frameOffsets.size).append(',')
                    .append("\"frameOffsets\":")
                    .append(item.frameOffsets.joinToString(",", "[", "]"))
            }
            if (item.dim > 0) manifestItems.append(",\"dim\":").append(item.dim)
            manifestItems.append('}')
            blob.write(wire)
            offset += wire.size
        }
        val manifest = buildString {
            append('{')
            if (engine != null) append("\"engine\":").append(jsonString(engine)).append(',')
            append("\"client\":{")
            client.entries.forEachIndexed { i, (k, v) ->
                if (i > 0) append(',')
                append(jsonString(k)).append(':').append(jsonString(v))
            }
            append("},\"sink\":").append(jsonString(sink))
                .append(",\"items\":[").append(manifestItems).append("]}")
        }.toByteArray(Charsets.UTF_8)

        val out = ByteArrayOutputStream(12 + manifest.size + offset.toInt())
        out.write(byteArrayOf('H'.code.toByte(), 'P'.code.toByte(), 'K'.code.toByte(), 'G'.code.toByte()))
        out.write(VERSION and 0xFF); out.write((VERSION ushr 8) and 0xFF)
        val flags = if (gzipItems) FLAG_GZIP_ITEMS else 0
        out.write(flags and 0xFF); out.write((flags ushr 8) and 0xFF)
        out.write(manifest.size and 0xFF)
        out.write((manifest.size ushr 8) and 0xFF)
        out.write((manifest.size ushr 16) and 0xFF)
        out.write((manifest.size ushr 24) and 0xFF)
        out.write(manifest)
        out.write(blob.toByteArray())
        return out.toByteArray()
    }

    /** Packs a normalized float vector as fp32 little-endian (kind=embedding payload). */
    fun packFloat32(values: FloatArray): ByteArray {
        val out = ByteArray(values.size * 4)
        values.forEachIndexed { i, v ->
            val bits = v.toRawBits()
            out[i * 4] = (bits and 0xFF).toByte()
            out[i * 4 + 1] = ((bits ushr 8) and 0xFF).toByte()
            out[i * 4 + 2] = ((bits ushr 16) and 0xFF).toByte()
            out[i * 4 + 3] = ((bits ushr 24) and 0xFF).toByte()
        }
        return out
    }

    private fun gzip(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        java.util.zip.GZIPOutputStream(out).use { it.write(data) }
        return out.toByteArray()
    }

    private fun jsonString(s: String): String = buildString(s.length + 2) {
        append('"')
        for (c in s) when (c) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (c.code < 0x20) append("\\u").append(c.code.toString(16).padStart(4, '0'))
                    else append(c)
        }
        append('"')
    }
}
