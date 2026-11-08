package com.dot.gallery.editor.render.photo

import com.dot.gallery.editor.core.graph.EffectParameters
import com.dot.gallery.editor.core.model.PhotoDocument
import com.dot.gallery.editor.core.model.PhotoImageLayer
import com.dot.gallery.editor.core.model.PhotoLayerMapping
import com.dot.gallery.editor.core.model.PhotoMapping
import com.dot.gallery.editor.core.model.PhotoWarpMapping
import com.dot.gallery.editor.core.model.PixelRect
import com.dot.gallery.editor.core.model.PixelSize
import com.dot.gallery.editor.render.BasicColorEffect
import com.dot.gallery.editor.render.FrameEffect
import com.dot.gallery.editor.render.LinearRgbaFrame
import com.dot.gallery.editor.render.SrgbPixels
import com.dot.gallery.editor.render.compositeLayerPixel
import com.dot.gallery.editor.render.photoMaskAlpha
import com.dot.gallery.editor.render.processPhotoFinishing
import com.dot.gallery.editor.render.PhotoRetouchRenderer
import com.dot.gallery.editor.render.processPhotoSpatial
import com.dot.gallery.editor.render.toLinearFrame
import com.dot.gallery.editor.render.toSrgbPixels
import kotlin.coroutines.coroutineContext
import kotlin.math.floor
import kotlin.math.min
import kotlinx.coroutines.ensureActive

/**
 * Streaming full-resolution exporter. Output rows are produced in
 * horizontal bands of at most [BAND_PIXEL_BUDGET] pixels (or
 * [MAX_BAND_ROWS] rows); the whole original image is never held in
 * memory.
 *
 * Every band starts as a bounded linear RGBA buffer the base recipe
 * fills — a neutral warp keeps the rigid path (rectangular
 * [PhotoMapping.sourceBounds] reads, one nearest source pixel per output
 * pixel), an active warp fills it tile by tile through
 * [PhotoWarpMapping] with the colour stage applied to each raw region.
 * Visible image layers then composite into the same band bottom to top:
 * each output tile is inverse-mapped through [PhotoLayerMapping], only
 * the smallest raw rectangle covering the valid bilinear neighbours is
 * read (recursively split until it fits [SOURCE_TILE_PIXEL_BUDGET]),
 * sampled without the base colour stage, masked, and blended by the
 * shared [compositeLayerPixel] equation. A non-neutral spatial stage
 * (details, lens blur) fills each band expanded by the recipe's halo
 * and crops back to the band rows, so neighbourhood reads stay exact;
 * the finishing stage (white balance, curves, vignette, grain) then
 * runs per band in absolute output coordinates so strip boundaries
 * cannot change a pixel. The band is encoded once.
 *
 * [layerRasters] is keyed by `layer.source.id`; a missing or mismatched
 * raster fails before any output is written, and invisible layers are
 * never read at all. With no visible layers behaviour is identical to
 * the base-only pipeline.
 *
 * The sink is sized for [PhotoMapping.outputSize] by the caller; the
 * caller also owns the raster and sink lifecycles, and deletes any
 * truncated output if this suspends out with cancellation or failure —
 * [ScanlineSink.finish] runs only after every row has been written.
 */
class PhotoStripExporter(
    private val effect: FrameEffect = BasicColorEffect()
) {

    suspend fun export(
        raster: PhotoRaster,
        document: PhotoDocument,
        sink: ScanlineSink,
        layerRasters: Map<String, PhotoRaster> = emptyMap(),
        onProgress: (Float) -> Unit
    ) {
        require(raster.info.reference == document.source.reference) {
            "raster '${raster.info.reference}' does not match " +
                "document source '${document.source.reference}'"
        }
        val rawSize = raster.info.rawSize
        require(rawSize.width <= MAX_DIMENSION && rawSize.height <= MAX_DIMENSION &&
            rawSize.pixelCount <= MAX_PIXELS) {
            "source ${rawSize.width}x${rawSize.height} exceeds export limits " +
                "(${MAX_DIMENSION} max dimension, $MAX_PIXELS max pixels)"
        }
        val mapping = PhotoMapping(rawSize, raster.info.orientation, document.geometry)
        val outWidth = mapping.outputSize.width
        val outHeight = mapping.outputSize.height
        require(outWidth <= MAX_DIMENSION && outHeight <= MAX_DIMENSION) {
            "output ${outWidth}x$outHeight exceeds max dimension $MAX_DIMENSION"
        }

        // Fail fast — a missing or mismatched layer source must never
        // produce a partially written output.
        val layerInputs = layerInputs(document, mapping.outputSize, layerRasters)

        val color = EffectParameters.Color(document.adjustments)
        val warpMapping = if (document.geometry.warp.isNeutral) {
            null
        } else {
            PhotoWarpMapping(rawSize, raster.info.orientation, document.geometry)
        }
        val bandRows = min(MAX_BAND_ROWS, maxOf(1, BAND_PIXEL_BUDGET / outWidth))
        val fullOutput = PixelSize(outWidth, outHeight)
        // The spatial stage reads a neighbourhood around every output
        // row, so a non-neutral recipe fills and composites each band
        // expanded by the recipe's halo, clipped to the output.
        val spatialHalo = document.spatial.requiredHalo
        val retouch = PhotoRetouchRenderer(document.retouch, fullOutput)
        var y = 0
        while (y < outHeight) {
            val rows = min(bandRows, outHeight - y)
            val retouchHalo = retouch.halo(y, rows)
            val halo = spatialHalo + retouchHalo
            val expandedTop = maxOf(0, y - halo)
            val expandedRows = min(outHeight, y + rows + halo) - expandedTop
            val band = FloatArray(outWidth * expandedRows * COMPONENTS_PER_PIXEL)
            if (warpMapping == null) {
                fillRigidBand(
                    raster, mapping, color, band, outWidth,
                    expandedTop, expandedRows
                )
            } else {
                fillWarpedBand(
                    raster, warpMapping, color, rawSize, band, outWidth,
                    expandedTop, expandedRows
                )
            }
            for ((layer, input) in layerInputs) {
                coroutineContext.ensureActive()
                fillLayerBand(
                    layer, input, band, outWidth, expandedTop, expandedRows
                )
            }
            // The spatial stage sees the expanded band in absolute
            // output coordinates — the focus mask stays continuous and
            // neighbourhood reads hit real halo pixels instead of
            // clamping, identical to a full-frame render. Only the
            // band's own rows continue downstream.
            var stage = LinearRgbaFrame.fromOwned(
                PixelSize(outWidth, expandedRows), band
            )
            if (!document.spatial.isNeutral) {
                stage = processPhotoSpatial(
                    stage, document.spatial, expandedTop, fullOutput
                )
            }
            // Retouch reads its own margin around the band, so finishing
            // runs over that margin too; only the band's rows are written.
            val retouchTop = maxOf(0, y - retouchHalo)
            val retouchRows = min(outHeight, y + rows + retouchHalo) - retouchTop
            stage = cropRows(stage, top = retouchTop - expandedTop, rows = retouchRows)
            // The finishing stage sees the band in absolute output
            // coordinates — vignette and grain stay continuous across
            // band boundaries, identical to a full-frame render.
            val finished = cropRows(
                retouch.process(
                    processPhotoFinishing(
                        stage, document.finishing,
                        originY = retouchTop,
                        fullSize = fullOutput
                    ),
                    originY = retouchTop
                ),
                top = y - retouchTop, rows = rows
            )
            val encoded = finished.toSrgbPixels().copyPixels()
            for (by in 0 until rows) {
                coroutineContext.ensureActive()
                sink.writeRow(encoded, by * outWidth)
            }
            y += rows
            onProgress(y.toFloat() / outHeight)
        }
        sink.finish()
    }

    /**
     * Renders an output-space zone through the same stages as export. Bands
     * bound working memory; two-dimensional halos keep tile edges invisible.
     * Sampling selects export pixels on the zone's own sample grid.
     */
    suspend fun renderRegion(
        raster: PhotoRaster,
        document: PhotoDocument,
        region: PixelRect,
        sampleSize: Int = 1,
        layerRasters: Map<String, PhotoRaster> = emptyMap()
    ): SrgbPixels {
        require(sampleSize > 0)
        require(raster.info.reference == document.source.reference)
        val rawSize = raster.info.rawSize
        val mapping = PhotoMapping(rawSize, raster.info.orientation, document.geometry)
        val fullSize = mapping.outputSize
        require(region.isInside(fullSize))
        val layers = layerInputs(document, fullSize, layerRasters)
        val warp = if (document.geometry.warp.isNeutral) null else
            PhotoWarpMapping(rawSize, raster.info.orientation, document.geometry)
        val color = EffectParameters.Color(document.adjustments)
        val width = (region.width - 1) / sampleSize + 1
        val height = (region.height - 1) / sampleSize + 1
        require(width.toLong() * height <= PhotoRegionLimits.MAX_DECODED_PIXELS) {
            "decoded zone ${width}x$height exceeds the output pixel budget"
        }
        val output = IntArray(width * height)
        val retouch = PhotoRetouchRenderer(document.retouch, fullSize)
        val retouchHalo = retouch.halo(region.top, region.height)
        val halo = document.spatial.requiredHalo + retouchHalo
        val left = maxOf(0, region.left - halo)
        val right = min(fullSize.width, region.rightExclusive + halo)
        val bandWidth = right - left
        val bandRows = min(MAX_BAND_ROWS, maxOf(1, BAND_PIXEL_BUDGET / bandWidth))
        var y = region.top
        while (y < region.bottomExclusive) {
            coroutineContext.ensureActive()
            val rows = min(bandRows, region.bottomExclusive - y)
            val top = maxOf(0, y - halo)
            val bottom = min(fullSize.height, y + rows + halo)
            val band = FloatArray(bandWidth * (bottom - top) * COMPONENTS_PER_PIXEL)
            if (warp == null) {
                fillRigidBand(raster, mapping, color, band, bandWidth, top, bottom - top, left)
            } else {
                fillWarpedBand(raster, warp, color, rawSize, band, bandWidth, top, bottom - top, left)
            }
            for ((layer, input) in layers) {
                fillLayerBand(layer, input, band, bandWidth, top, bottom - top, left)
            }
            val spatial = processPhotoSpatial(
                LinearRgbaFrame.fromOwned(PixelSize(bandWidth, bottom - top), band),
                document.spatial, top, fullSize, originX = left
            )
            val retouchTop = maxOf(0, y - retouchHalo)
            val retouchRows = min(fullSize.height, y + rows + retouchHalo) - retouchTop
            val stage = cropRows(spatial, retouchTop - top, retouchRows)
            val finished = cropRows(
                retouch.process(
                    processPhotoFinishing(
                        stage, document.finishing, retouchTop, fullSize, originX = left
                    ),
                    originY = retouchTop, originX = left
                ),
                y - retouchTop, rows
            ).toSrgbPixels()
            for (row in 0 until rows) {
                val relativeY = y + row - region.top
                if (relativeY % sampleSize != 0) continue
                for (x in 0 until width) {
                    output[relativeY / sampleSize * width + x] =
                        finished.component(row * bandWidth + region.left - left + x * sampleSize)
                }
            }
            y += rows
        }
        return SrgbPixels.fromOwned(PixelSize(width, height), output)
    }

    /**
     * Extracts [rows] rows starting at [top] from [frame] into a new
     * frame — the band's own slice out of its halo-expanded spatial
     * result.
     */
    private fun cropRows(
        frame: LinearRgbaFrame,
        top: Int,
        rows: Int
    ): LinearRgbaFrame {
        val width = frame.size.width
        require(top >= 0 && top + rows <= frame.size.height) {
            "crop $top + $rows exceeds frame height ${frame.size.height}"
        }
        val out = FloatArray(width * rows * COMPONENTS_PER_PIXEL)
        val source = frame.copyPixels()
        val rowBytes = width * COMPONENTS_PER_PIXEL
        source.copyInto(
            out, destinationOffset = 0,
            startIndex = top * rowBytes,
            endIndex = (top + rows) * rowBytes
        )
        return LinearRgbaFrame.fromOwned(PixelSize(width, rows), out)
    }

    /** One visible layer's raster plus its placement mapping on the canvas. */
    private class LayerInput(
        val raster: PhotoRaster,
        val mapping: PhotoLayerMapping
    )

    private fun layerInputs(
        document: PhotoDocument,
        outputSize: PixelSize,
        rasters: Map<String, PhotoRaster>
    ): Map<PhotoImageLayer, LayerInput> = document.layers.filter { it.visible }.associateWith { layer ->
        val raster = rasters[layer.source.id] ?: throw IllegalStateException(
            "no raster for layer source '${layer.source.id}'"
        )
        require(raster.info.reference == layer.source.reference)
        require(raster.info.uprightSize == layer.source.size)
        LayerInput(
            raster,
            PhotoLayerMapping(raster.info.rawSize, raster.info.orientation, outputSize, layer.transform)
        )
    }

    /**
     * The rigid base path: rectangular reads, nearest sampling and the
     * colour stage over the band — byte-exact with the pre-layer pipeline.
     */
    private suspend fun fillRigidBand(
        raster: PhotoRaster,
        mapping: PhotoMapping,
        color: EffectParameters.Color,
        band: FloatArray,
        bandWidth: Int,
        bandTop: Int,
        rows: Int,
        bandLeft: Int = 0
    ) {
        val bounds = mapping.sourceBounds(PixelRect(bandLeft, bandTop, bandWidth, rows))
        val region = raster.readRegion(bounds)
        require(
            region.size.width == bounds.width && region.size.height == bounds.height
        ) {
            "raster returned ${region.size.width}x${region.size.height} " +
                "for requested ${bounds.width}x${bounds.height}"
        }
        val argb = IntArray(bandWidth * rows)
        for (by in 0 until rows) {
            coroutineContext.ensureActive()
            for (x in 0 until bandWidth) {
                val packed = mapping.sourcePointPacked(bandLeft + x, bandTop + by)
                val sx = (packed shr 32).toInt() - bounds.left
                val sy = packed.toInt() - bounds.top
                argb[by * bandWidth + x] = region.component(sy * bounds.width + sx)
            }
        }
        val rendered = effect.render(
            listOf(
                SrgbPixels.fromOwned(PixelSize(bandWidth, rows), argb).toLinearFrame()
            ),
            color
        )
        rendered.copyPixels().copyInto(band)
    }

    /**
     * The warped base path: bounded source regions, colour-adjusted then
     * bilinear-sampled into the band through the continuous mapping.
     */
    private suspend fun fillWarpedBand(
        raster: PhotoRaster,
        mapping: PhotoWarpMapping,
        color: EffectParameters.Color,
        rawSize: PixelSize,
        band: FloatArray,
        bandWidth: Int,
        bandTop: Int,
        rows: Int,
        bandLeft: Int = 0
    ) {
        var tileY = 0
        while (tileY < rows) {
            val tileH = min(MAX_TILE_ROWS, rows - tileY)
            var tileX = 0
            while (tileX < bandWidth) {
                val tileW = min(MAX_TILE_COLS, bandWidth - tileX)
                fillTile(
                    raster, mapping, color, rawSize,
                    band, bandWidth, bandTop,
                    tileLeft = bandLeft + tileX, tileTop = bandTop + tileY,
                    tileW = tileW, tileH = tileH, bandLeft = bandLeft
                )
                tileX += tileW
            }
            tileY += tileH
        }
    }

    /**
     * Maps one output tile through [mapping], reads the smallest raw
     * rectangle covering every valid bilinear neighbour, colour-adjusts it
     * and fills the tile's slice of [band]. When the required rectangle
     * exceeds [SOURCE_TILE_PIXEL_BUDGET] the tile halves along its longer
     * output dimension and recurses — a 1x1 tile needs at most four
     * neighbours, so splitting always terminates inside the budget.
     */
    private suspend fun fillTile(
        raster: PhotoRaster,
        mapping: PhotoWarpMapping,
        color: EffectParameters.Color,
        rawSize: PixelSize,
        band: FloatArray,
        bandWidth: Int,
        bandTop: Int,
        tileLeft: Int,
        tileTop: Int,
        tileW: Int,
        tileH: Int,
        bandLeft: Int = 0
    ) {
        val count = tileW * tileH
        val sourceX = DoubleArray(count)
        val sourceY = DoubleArray(count)
        var anyValid = false
        var minX = rawSize.width
        var minY = rawSize.height
        var maxX = -1
        var maxY = -1
        for (dy in 0 until tileH) {
            coroutineContext.ensureActive()
            for (dx in 0 until tileW) {
                val index = dy * tileW + dx
                val point = mapping.sourcePoint(
                    (tileLeft + dx).toDouble(), (tileTop + dy).toDouble()
                )
                if (point == null) {
                    sourceX[index] = Double.NaN
                } else {
                    sourceX[index] = point.x
                    sourceY[index] = point.y
                    val fx = floor(point.x).toInt()
                    val fy = floor(point.y).toInt()
                    // Both bilinear neighbours bound the read; neighbours
                    // outside the source are transparent and need no pixels.
                    minX = min(minX, fx.coerceAtLeast(0))
                    maxX = maxOf(maxX, (fx + 1).coerceAtMost(rawSize.width - 1))
                    minY = min(minY, fy.coerceAtLeast(0))
                    maxY = maxOf(maxY, (fy + 1).coerceAtMost(rawSize.height - 1))
                    anyValid = true
                }
            }
        }
        if (!anyValid) return
        val bounds = PixelRect(minX, minY, maxX - minX + 1, maxY - minY + 1)
        if (bounds.width.toLong() * bounds.height > SOURCE_TILE_PIXEL_BUDGET) {
            check(tileW > 1 || tileH > 1) {
                "indivisible tile exceeded the source budget: $bounds"
            }
            if (tileW >= tileH) {
                val half = tileW / 2
                fillTile(raster, mapping, color, rawSize, band, bandWidth,
                    bandTop, tileLeft, tileTop, half, tileH, bandLeft)
                fillTile(raster, mapping, color, rawSize, band, bandWidth,
                    bandTop, tileLeft + half, tileTop, tileW - half, tileH, bandLeft)
            } else {
                val half = tileH / 2
                fillTile(raster, mapping, color, rawSize, band, bandWidth,
                    bandTop, tileLeft, tileTop, tileW, half, bandLeft)
                fillTile(raster, mapping, color, rawSize, band, bandWidth,
                    bandTop, tileLeft, tileTop + half, tileW, tileH - half, bandLeft)
            }
            return
        }
        val region = raster.readRegion(bounds)
        require(
            region.size.width == bounds.width && region.size.height == bounds.height
        ) {
            "raster returned ${region.size.width}x${region.size.height} " +
                "for requested ${bounds.width}x${bounds.height}"
        }
        val linear = effect.render(
            listOf(region.toLinearFrame()), color
        )
        for (dy in 0 until tileH) {
            coroutineContext.ensureActive()
            for (dx in 0 until tileW) {
                val index = dy * tileW + dx
                val px = sourceX[index]
                if (px.isNaN()) continue
                val py = sourceY[index]
                val x0 = floor(px).toInt()
                val y0 = floor(py).toInt()
                val tx = (px - x0).toFloat()
                val ty = (py - y0).toFloat()
                val dst =
                    ((tileTop + dy - bandTop) * bandWidth + tileLeft - bandLeft + dx) *
                        COMPONENTS_PER_PIXEL
                for (c in 0 until COMPONENTS_PER_PIXEL) {
                    val topLeft = sample(linear, bounds, rawSize, x0, y0, c)
                    val topRight = sample(linear, bounds, rawSize, x0 + 1, y0, c)
                    val bottomLeft = sample(linear, bounds, rawSize, x0, y0 + 1, c)
                    val bottomRight = sample(linear, bounds, rawSize, x0 + 1, y0 + 1, c)
                    val top = topLeft + (topRight - topLeft) * tx
                    val bottom = bottomLeft + (bottomRight - bottomLeft) * tx
                    band[dst + c] = top + (bottom - top) * ty
                }
            }
        }
    }

    /**
     * Composites one layer into the band, tile by tile: placement mapping
     * into raw layer coordinates, bounded region reads converted to
     * linear (no base colour stage), bilinear sampling, the layer mask
     * multiplying sampled alpha, then [compositeLayerPixel].
     */
    private suspend fun fillLayerBand(
        layer: PhotoImageLayer,
        input: LayerInput,
        band: FloatArray,
        bandWidth: Int,
        bandTop: Int,
        rows: Int,
        bandLeft: Int = 0
    ) {
        var tileY = 0
        while (tileY < rows) {
            val tileH = min(MAX_TILE_ROWS, rows - tileY)
            var tileX = 0
            while (tileX < bandWidth) {
                val tileW = min(MAX_TILE_COLS, bandWidth - tileX)
                fillLayerTile(
                    layer, input,
                    band, bandWidth, bandTop,
                    tileLeft = bandLeft + tileX, tileTop = bandTop + tileY,
                    tileW = tileW, tileH = tileH, bandLeft = bandLeft
                )
                tileX += tileW
            }
            tileY += tileH
        }
    }

    /**
     * Fills one layer tile: maps output points through the layer's
     * placement, reads only the raw rectangle covering every valid
     * bilinear neighbour (halving the longer output dimension while it
     * exceeds [SOURCE_TILE_PIXEL_BUDGET]) and blends each mapped sample
     * into the band. Rejected points and out-of-source neighbours stay
     * transparent.
     */
    private suspend fun fillLayerTile(
        layer: PhotoImageLayer,
        input: LayerInput,
        band: FloatArray,
        bandWidth: Int,
        bandTop: Int,
        tileLeft: Int,
        tileTop: Int,
        tileW: Int,
        tileH: Int,
        bandLeft: Int = 0
    ) {
        val rawSize = input.raster.info.rawSize
        val count = tileW * tileH
        val sourceX = DoubleArray(count)
        val sourceY = DoubleArray(count)
        val normX = DoubleArray(count)
        val normY = DoubleArray(count)
        var anyValid = false
        var minX = rawSize.width
        var minY = rawSize.height
        var maxX = -1
        var maxY = -1
        for (dy in 0 until tileH) {
            coroutineContext.ensureActive()
            for (dx in 0 until tileW) {
                val index = dy * tileW + dx
                val point = input.mapping.sourcePoint(
                    (tileLeft + dx).toDouble(), (tileTop + dy).toDouble()
                )
                if (point == null) {
                    sourceX[index] = Double.NaN
                } else {
                    sourceX[index] = point.raw.x
                    sourceY[index] = point.raw.y
                    normX[index] = point.normalizedX
                    normY[index] = point.normalizedY
                    val fx = floor(point.raw.x).toInt()
                    val fy = floor(point.raw.y).toInt()
                    minX = min(minX, fx.coerceAtLeast(0))
                    maxX = maxOf(maxX, (fx + 1).coerceAtMost(rawSize.width - 1))
                    minY = min(minY, fy.coerceAtLeast(0))
                    maxY = maxOf(maxY, (fy + 1).coerceAtMost(rawSize.height - 1))
                    anyValid = true
                }
            }
        }
        if (!anyValid) return
        val bounds = PixelRect(minX, minY, maxX - minX + 1, maxY - minY + 1)
        if (bounds.width.toLong() * bounds.height > SOURCE_TILE_PIXEL_BUDGET) {
            check(tileW > 1 || tileH > 1) {
                "indivisible layer tile exceeded the source budget: $bounds"
            }
            if (tileW >= tileH) {
                val half = tileW / 2
                fillLayerTile(layer, input, band, bandWidth, bandTop,
                    tileLeft, tileTop, half, tileH, bandLeft)
                fillLayerTile(layer, input, band, bandWidth, bandTop,
                    tileLeft + half, tileTop, tileW - half, tileH, bandLeft)
            } else {
                val half = tileH / 2
                fillLayerTile(layer, input, band, bandWidth, bandTop,
                    tileLeft, tileTop, tileW, half, bandLeft)
                fillLayerTile(layer, input, band, bandWidth, bandTop,
                    tileLeft, tileTop + half, tileW, tileH - half, bandLeft)
            }
            return
        }
        val region = input.raster.readRegion(bounds)
        require(
            region.size.width == bounds.width && region.size.height == bounds.height
        ) {
            "layer raster returned ${region.size.width}x${region.size.height} " +
                "for requested ${bounds.width}x${bounds.height}"
        }
        val linear = region.toLinearFrame()
        val upright = input.mapping.uprightSize
        for (dy in 0 until tileH) {
            coroutineContext.ensureActive()
            for (dx in 0 until tileW) {
                val index = dy * tileW + dx
                val px = sourceX[index]
                if (px.isNaN()) continue
                val py = sourceY[index]
                val x0 = floor(px).toInt()
                val y0 = floor(py).toInt()
                val tx = (px - x0).toFloat()
                val ty = (py - y0).toFloat()
                val dst =
                    ((tileTop + dy - bandTop) * bandWidth + tileLeft - bandLeft + dx) *
                        COMPONENTS_PER_PIXEL
                val alpha = bilinear(linear, bounds, rawSize, x0, y0, tx, ty, ALPHA_OFFSET)
                val maskAlpha = photoMaskAlpha(
                    layer.mask, normX[index], normY[index], upright
                )
                compositeLayerPixel(
                    band, dst,
                    bilinear(linear, bounds, rawSize, x0, y0, tx, ty, 0),
                    bilinear(linear, bounds, rawSize, x0, y0, tx, ty, 1),
                    bilinear(linear, bounds, rawSize, x0, y0, tx, ty, 2),
                    alpha * maskAlpha,
                    layer.blendMode, layer.opacity, layer.clipToBelow
                )
            }
        }
    }

    /** Bilinear blend of one component at raw coordinate `(x0 + tx, y0 + ty)`. */
    private fun bilinear(
        linear: LinearRgbaFrame,
        bounds: PixelRect,
        rawSize: PixelSize,
        x0: Int,
        y0: Int,
        tx: Float,
        ty: Float,
        c: Int
    ): Float {
        val topLeft = sample(linear, bounds, rawSize, x0, y0, c)
        val topRight = sample(linear, bounds, rawSize, x0 + 1, y0, c)
        val bottomLeft = sample(linear, bounds, rawSize, x0, y0 + 1, c)
        val bottomRight = sample(linear, bounds, rawSize, x0 + 1, y0 + 1, c)
        val top = topLeft + (topRight - topLeft) * tx
        val bottom = bottomLeft + (bottomRight - bottomLeft) * tx
        return top + (bottom - top) * ty
    }

    /** One component of the colour-adjusted region at raw coordinate (x, y). */
    private fun sample(
        linear: LinearRgbaFrame,
        bounds: PixelRect,
        rawSize: PixelSize,
        x: Int,
        y: Int,
        c: Int
    ): Float =
        if (x in 0 until rawSize.width && y in 0 until rawSize.height) {
            linear.component(
                ((y - bounds.top) * bounds.width + (x - bounds.left)) *
                    COMPONENTS_PER_PIXEL + c
            )
        } else {
            0f
        }

    private companion object {
        const val MAX_DIMENSION = 65_535
        const val MAX_PIXELS = 200_000_000L
        const val BAND_PIXEL_BUDGET = 262_144
        const val MAX_BAND_ROWS = 128
        const val SOURCE_TILE_PIXEL_BUDGET = 262_144L
        const val MAX_TILE_COLS = 256
        const val MAX_TILE_ROWS = 32
        const val COMPONENTS_PER_PIXEL = 4
        const val ALPHA_OFFSET = 3
    }
}
