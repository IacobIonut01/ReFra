/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.insight

import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/**
 * Reads QR codes and barcodes with zxing-cpp. The meta array is allocated once, so a scan that
 * finds nothing allocates nothing on the Java heap.
 *
 * [formatMask] is read on every scan, so the settings can change it without recreating the
 * analyzer.
 */
class ZxingCodeAnalyzer(
    @Volatile var formatMask: Int = CodeFormat.maskForGroups(CodeFormat.DEFAULT_GROUPS),
) : ImageAnalyzer {

    override val kind: InsightKind get() = InsightKind.CODES

    private val meta = IntArray(MAX_CODES * CodeScanNative.META_STRIDE)

    override suspend fun analyze(frame: LuminanceFrame, thorough: Boolean): List<Insight> {
        val mask = formatMask
        if (mask == 0 || frame.width <= 0 || frame.height <= 0) return emptyList()
        coroutineContext.ensureActive()
        val texts = CodeScanNative.scan(
            buffer = frame.buffer,
            width = frame.width,
            height = frame.height,
            formatMask = mask,
            thorough = thorough,
            maxCodes = MAX_CODES,
            maxTextBytes = MAX_TEXT_BYTES,
            meta = meta,
        ) ?: return emptyList()
        return texts.indices.mapNotNull { index ->
            val base = index * CodeScanNative.META_STRIDE
            val format = CodeFormat.fromBit(meta[base]) ?: return@mapNotNull null
            val text = texts[index]
            CodeInsight(
                format = format,
                text = text,
                payload = CodePayloadParser.parse(text, format),
                quad = ImageQuad.fromPixels(meta, base + 2, frame.width, frame.height),
            )
        }
    }

    companion object {
        const val MAX_CODES = 8
        const val MAX_TEXT_BYTES = 64 * 1024
    }
}
