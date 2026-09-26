/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.decoder.format

import android.graphics.Canvas
import android.graphics.Bitmap
import android.util.Size
import androidx.core.graphics.createBitmap
import com.caverock.androidsvg.SVG
import com.dot.gallery.feature_node.presentation.util.printError
import java.io.ByteArrayInputStream

/**
 * Rasterizes SVG documents to a [Bitmap] using AndroidSVG. Glide (used for the timeline grid)
 * has no SVG support, so SVG thumbnails would otherwise fail to load even though the Sketch-based
 * media viewer renders them fine.
 */
object SvgImageDecoder {

    private const val TAG = "decode.svg"
    private const val DEFAULT_SIZE = 512

    /**
     * Long-edge cap used when rendering an SVG as the base bitmap for subsampling. Larger values
     * give crisper zoom at the cost of memory; 4096 keeps a full RGBA bitmap under ~64MB.
     */
    const val REGION_MAX_DIM = 4096

    /** Computes the raster dimensions [decode] would produce for the given bounds, without rendering. */
    fun renderSize(bytes: ByteArray, reqW: Int, reqH: Int): Size? {
        return try {
            val svg = SVG.getFromInputStream(ByteArrayInputStream(bytes))
            val (w, h) = computeTarget(svg, reqW, reqH)
            Size(w, h)
        } catch (e: Throwable) {
            printError(TAG, "renderSize failed: ${e.message}")
            null
        }
    }

    fun decode(bytes: ByteArray, reqW: Int, reqH: Int): Bitmap? {
        return try {
            val svg = SVG.getFromInputStream(ByteArrayInputStream(bytes))
            val (targetW, targetH) = computeTarget(svg, reqW, reqH)

            ensureViewBox(svg)
            svg.setDocumentWidth(targetW.toFloat())
            svg.setDocumentHeight(targetH.toFloat())

            val bitmap = createBitmap(targetW, targetH)
            val canvas = Canvas(bitmap)
            svg.renderToCanvas(canvas)
            bitmap
        } catch (e: Throwable) {
            printError(TAG, "decode failed: ${e.message}", e)
            null
        }
    }

    /**
     * AndroidSVG only *scales* content to a new viewport size ([setDocumentWidth]/[setDocumentHeight])
     * when the document declares a `viewBox`. SVGs that specify `width`/`height` but no `viewBox`
     * (e.g. PowerPoint/Office exports) draw their content at native user-coordinates in the top-left
     * of the enlarged viewport, leaving the rest transparent. When rendered as the high-res base for
     * subsampling this makes the raster size disagree with the drawn content, producing a shrunken
     * duplicate in the top-left over the base painter (#1020). Synthesizing a viewBox from the
     * intrinsic width/height lets AndroidSVG scale the content to fill the target raster.
     */
    private fun ensureViewBox(svg: SVG) {
        if (svg.documentViewBox != null) return
        val w = svg.documentWidth
        val h = svg.documentHeight
        if (w > 0f && h > 0f) {
            svg.setDocumentViewBox(0f, 0f, w, h)
        }
    }

    /** Aspect-preserving target dimensions for the given bounds (0 = unconstrained). */
    private fun computeTarget(svg: SVG, reqW: Int, reqH: Int): Pair<Int, Int> {
        val aspect = svg.documentAspectRatio // width / height, -1 if unknown
        val intrinsicW = svg.documentWidth
        val intrinsicH = svg.documentHeight

        val maxW = if (reqW > 0) reqW else if (intrinsicW > 0) intrinsicW.toInt() else DEFAULT_SIZE
        val maxH = if (reqH > 0) reqH else if (intrinsicH > 0) intrinsicH.toInt() else DEFAULT_SIZE

        return when {
            aspect > 0 -> {
                if (maxW.toFloat() / maxH > aspect) {
                    (maxH * aspect).toInt().coerceAtLeast(1) to maxH
                } else {
                    maxW to (maxW / aspect).toInt().coerceAtLeast(1)
                }
            }
            else -> maxW to maxH
        }
    }
}
