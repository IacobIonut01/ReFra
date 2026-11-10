/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr.engine

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import com.dot.gallery.core.logging.printDebug
import com.dot.gallery.core.ocr.catalog.OcrCatalogModel
import com.dot.gallery.core.ocr.decode.BitmapOcrMediaSource
import com.dot.gallery.core.ocr.decode.OcrMediaSource
import com.dot.gallery.core.ocr.decode.OcrTile
import com.dot.gallery.core.ocr.decode.OcrTileKind
import com.dot.gallery.core.ocr.decode.OcrTilePlanner
import com.dot.gallery.core.ocr.decode.OcrTileTransform
import com.dot.gallery.core.ocr.domain.NormalizedQuad
import com.dot.gallery.core.ocr.domain.OcrDocument
import com.dot.gallery.core.ocr.domain.OcrLimits
import com.dot.gallery.core.ocr.domain.OcrLine
import com.dot.gallery.core.ocr.domain.OcrWord
import com.dot.gallery.core.ocr.domain.stableOcrId
import com.dot.gallery.core.ocr.geometry.DbPostProcessor
import com.dot.gallery.core.ocr.geometry.OcrCropGeometry
import com.dot.gallery.core.ocr.geometry.OcrGeometry
import com.dot.gallery.core.ocr.geometry.OcrReadingOrder
import com.dot.gallery.core.ocr.geometry.OcrSeamMerge
import com.dot.gallery.core.ocr.geometry.PixelQuad
import com.dot.gallery.core.ocr.text.CtcDecodeResult
import com.dot.gallery.core.ocr.text.CtcDecoder
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class PpOcrV5Engine(
    private val models: OcrModelSource,
    private val sessionManager: OcrSessionManager,
    /** Longest side the detector reads (see [OcrDetectorInput]); lower trades small text for memory and time. */
    private val detectorMaxSide: Int = OcrDetectorInput.MAX_SIDE,
) {
    /** Closes the ONNX sessions (low memory, switch off, packs changed); the next read reopens them. */
    suspend fun releaseSessions() = sessionManager.release()

    /** Like [releaseSessions], but returns at once: safe on the main thread while a read is running. */
    fun releaseSessionsAsync() = sessionManager.releaseAsync()

    suspend fun recognize(
        bitmap: Bitmap,
        packIds: Set<String>,
        priority: OcrRequestPriority,
    ): OcrDocument {
        require(bitmap.width > 0 && bitmap.height > 0)
        require(OcrLimits.checkedSourceArea(bitmap.width, bitmap.height) != null)
        val copy = bitmap.copy(Bitmap.Config.ARGB_8888, false)
            ?: throw IllegalStateException("Unable to copy OCR input")
        return BitmapOcrMediaSource(copy).use { source -> recognize(source, packIds, priority) }
    }

    /** Runs overview plus real source-region tiles when the media source supports them. */
    suspend fun recognize(
        source: OcrMediaSource,
        packIds: Set<String>,
        priority: OcrRequestPriority,
    ): OcrDocument = withContext(Dispatchers.Default) {
        val sourceWidth = source.orientedWidth
        val sourceHeight = source.orientedHeight
        require(OcrLimits.checkedSourceArea(sourceWidth, sourceHeight) != null)
        require(models.areReady(packIds)) { "Selected OCR packs are unavailable" }
        val plan = OcrTilePlanner.plan(
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
            supportsRegionDecoding = source.supportsRegionDecode,
        ) ?: throw IllegalArgumentException("Unsupported OCR source dimensions")
        val detector = models.detectorFor(packIds)
        val packs = models.packsFor(packIds)
        val recognizers = packs.map { models.recognizerFor(it.id) }
        // One router per photo: the pack that reads most of its lines goes first for the rest.
        val router = OcrPackRouter(packs.map { it.id })
        val recognized = ArrayList<RecognizedLine>()
        val detectionsPerTile = if (plan.tiles.size == 1) {
            OcrLimits.MAX_LINES
        } else {
            maxOf(MIN_DETECTIONS_PER_TILE, MAX_RAW_DETECTIONS / plan.tiles.size)
        }

        for (tile in plan.tiles) {
            currentCoroutineContext().ensureActive()
            val result = processTileWithRetry(
                source = source,
                tile = tile,
                detector = detector,
                recognizers = recognizers,
                router = router,
                priority = priority,
                sourceWidth = sourceWidth,
                sourceHeight = sourceHeight,
                maximumDetections = detectionsPerTile,
            ) ?: continue
            recognized += result.lines.take(MAX_RAW_DETECTIONS - recognized.size)
        }
        buildDocument(sourceWidth, sourceHeight, packIds, recognized)
    }

    private suspend fun processTileWithRetry(
        source: OcrMediaSource,
        tile: OcrTile,
        detector: OcrCatalogModel,
        recognizers: List<OcrCatalogModel>,
        router: OcrPackRouter,
        priority: OcrRequestPriority,
        sourceWidth: Int,
        sourceHeight: Int,
        maximumDetections: Int,
    ): TileResult? {
        val requestedEdge = maxOf(tile.decodeWidth, tile.decodeHeight)
        val attempts = if (requestedEdge > LOW_MEMORY_TILE_EDGE) {
            intArrayOf(requestedEdge, LOW_MEMORY_TILE_EDGE)
        } else {
            intArrayOf(requestedEdge)
        }
        var lastOutOfMemory: OutOfMemoryError? = null
        for (edge in attempts) {
            currentCoroutineContext().ensureActive()
            var bitmap: Bitmap? = null
            try {
                bitmap = if (tile.kind == OcrTileKind.OVERVIEW) {
                    source.overview(edge)
                } else {
                    source.region(tile.source, edge)
                }
                if (bitmap == null || bitmap.width <= 0 || bitmap.height <= 0) return null
                return processDecodedTile(
                    bitmap = bitmap,
                    tile = tile,
                    detector = detector,
                    recognizers = recognizers,
                    router = router,
                    priority = priority,
                    sourceWidth = sourceWidth,
                    sourceHeight = sourceHeight,
                    maximumDetections = maximumDetections,
                )
            } catch (outOfMemory: OutOfMemoryError) {
                lastOutOfMemory = outOfMemory
            } finally {
                bitmap?.takeUnless(Bitmap::isRecycled)?.recycle()
            }
        }
        throw lastOutOfMemory ?: OutOfMemoryError("OCR tile decode failed")
    }

    private suspend fun processDecodedTile(
        bitmap: Bitmap,
        tile: OcrTile,
        detector: OcrCatalogModel,
        recognizers: List<OcrCatalogModel>,
        router: OcrPackRouter,
        priority: OcrRequestPriority,
        sourceWidth: Int,
        sourceHeight: Int,
        maximumDetections: Int,
    ): TileResult {
        val detectStart = System.nanoTime()
        // A cheap coarse pass first; full resolution only when it shows text too small to read there.
        val coarseSide = min(detectorMaxSide, OcrDetectorInput.COARSE_SIDE)
        var quads = detect(bitmap, detector, priority, maximumDetections, coarseSide)
        val coarseScale = (OcrDetectorInput.size(bitmap.width, bitmap.height, coarseSide)?.first ?: bitmap.width)
            .toFloat() / bitmap.width
        val coarseShortSides = quads.map { min(it.cropWidth, it.cropHeight) }
        val thinnestCoarseBox = (coarseShortSides.minOrNull() ?: 0f) * coarseScale
        val finePass = detectorMaxSide > coarseSide && OcrDetectorInput.needsFinePass(coarseShortSides, coarseScale)
        if (finePass) quads = detect(bitmap, detector, priority, maximumDetections, detectorMaxSide)
        val readStart = System.nanoTime()
        val lines = ArrayList<RecognizedLine>(quads.size)
        // Crops are cut and read a chunk at a time, so a photo full of text never holds every crop.
        for (chunkStart in quads.indices step LINES_PER_CHUNK) {
            currentCoroutineContext().ensureActive()
            val crops = ArrayList<LineCrop>()
            try {
                for (index in chunkStart until min(quads.size, chunkStart + LINES_PER_CHUNK)) {
                    val quad = quads[index]
                    val rawCrop = cropPerspective(bitmap, quad) ?: continue
                    // PaddleOCR's rule: a crop 1.5 times taller than wide is a vertical line, turned anticlockwise.
                    val rotated = OcrCropGeometry.rotatesCrop(rawCrop.width, rawCrop.height)
                    val crop = if (rotated) rotateBitmap(rawCrop, -90f).also { rawCrop.recycle() } else rawCrop
                    crops += LineCrop(index, quad, crop, rotated)
                }
                val reads = readLines(crops.map(LineCrop::bitmap), recognizers, router, priority)
                for ((slot, crop) in crops.withIndex()) {
                    val read = reads[slot] ?: continue
                    val candidate = read.candidate.takeIf {
                        it.result.text.isNotBlank() && it.calibratedConfidence >= MIN_LINE_CONFIDENCE
                    } ?: continue
                    val local = crop.quad.toNormalizedQuad(bitmap.width, bitmap.height)
                    val sourceQuad = OcrTileTransform.toNormalizedSource(local, tile, sourceWidth, sourceHeight)
                    lines += RecognizedLine(
                        id = stableOcrId(tile.id, crop.index, candidate.packId, candidate.result.text),
                        sourceQuad = sourceQuad,
                        candidate = candidate,
                        rotated = crop.rotated,
                        flipped = read.flipped,
                    )
                }
            } finally {
                crops.forEach { it.bitmap.takeUnless(Bitmap::isRecycled)?.recycle() }
            }
        }
        printDebug(
            TAG,
            "tile ${tile.id} ${bitmap.width}x${bitmap.height}: detect ${(readStart - detectStart) / 1_000_000} ms" +
                " (thinnest coarse box ${"%.1f".format(thinnestCoarseBox)} px" +
                (if (finePass) ", full-size pass) " else ") ") +
                "(${quads.size} boxes), read ${(System.nanoTime() - readStart) / 1_000_000} ms (${lines.size} lines)",
        )
        return TileResult(lines)
    }

    /**
     * Reads every crop: the router's first pack reads them all, the next packs only the lines still
     * below the accept confidence, and lines that stay unsure are tried again turned 180°.
     */
    private suspend fun readLines(
        crops: List<Bitmap>,
        recognizers: List<OcrCatalogModel>,
        router: OcrPackRouter,
        priority: OcrRequestPriority,
    ): List<LineRead?> {
        if (crops.isEmpty()) return emptyList()
        val byPack = recognizers.associateBy(::packIdOf)
        val best = arrayOfNulls<Candidate>(crops.size)
        for ((attempt, packId) in router.order().withIndex()) {
            val model = byPack[packId] ?: continue
            val targets = crops.indices.filter {
                attempt == 0 || OcrPackRouter.needsAnotherPack(best[it]?.calibratedConfidence)
            }
            if (targets.isEmpty()) break
            val read = recognizeBatch(targets.map(crops::get), model, priority)
            targets.forEachIndexed { slot, index ->
                val candidate = read[slot] ?: return@forEachIndexed
                val current = best[index]
                if (current == null || candidate.calibratedConfidence > current.calibratedConfidence ||
                    (candidate.calibratedConfidence == current.calibratedConfidence && candidate.packId < current.packId)
                ) best[index] = candidate
            }
        }
        best.forEach { it?.let { candidate -> router.record(candidate.packId) } }

        val flipped = BooleanArray(crops.size)
        val unsure = crops.indices.filter { index ->
            best[index]?.let { OcrPackRouter.triesFlip(it.calibratedConfidence) } == true
        }
        for ((packId, indices) in unsure.groupBy { best[it]!!.packId }) {
            val model = byPack[packId] ?: continue
            val turned = ArrayList<Bitmap>(indices.size)
            try {
                indices.forEach { turned += rotateBitmap(crops[it], 180f) }
                val read = recognizeBatch(turned, model, priority)
                indices.forEachIndexed { slot, index ->
                    val candidate = read[slot] ?: return@forEachIndexed
                    if (OcrPackRouter.prefersFlipped(best[index]!!.calibratedConfidence, candidate.calibratedConfidence)) {
                        best[index] = candidate
                        flipped[index] = true
                    }
                }
            } finally {
                turned.forEach(Bitmap::recycle)
            }
        }
        return List(crops.size) { index -> best[index]?.let { LineRead(it, flipped[index]) } }
    }

    private suspend fun detect(
        bitmap: Bitmap,
        model: OcrCatalogModel,
        priority: OcrRequestPriority,
        maximumDetections: Int,
        maxSide: Int,
    ): List<PixelQuad> {
        if (maximumDetections <= 0) return emptyList()
        val (scaledWidth, scaledHeight) = OcrDetectorInput.size(bitmap.width, bitmap.height, maxSide)
            ?: return emptyList()
        if (scaledWidth.toLong() * scaledHeight > MAX_DETECTOR_PIXELS) return emptyList()
        val resized = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
        try {
            val pixels = IntArray(scaledWidth * scaledHeight)
            resized.getPixels(pixels, 0, scaledWidth, 0, 0, scaledWidth, scaledHeight)
            val input = directFloats(3 * scaledWidth * scaledHeight)
            normalizeInto(input, 0, pixels, scaledWidth, scaledHeight, scaledWidth, model, defaultMean = 0f, defaultStd = 1f)
            input.rewind()
            val tensor = sessionManager.run(
                model,
                priority,
                input,
                longArrayOf(1, 3, scaledHeight.toLong(), scaledWidth.toLong()),
            ) { result -> firstOutput(result) }
            val mapHeight = tensor.shape.getOrNull(tensor.shape.size - 2) ?: return emptyList()
            val mapWidth = tensor.shape.getOrNull(tensor.shape.size - 1) ?: return emptyList()
            val planeSize = mapWidth.toLong() * mapHeight
            if (planeSize <= 0L || planeSize > Int.MAX_VALUE || tensor.values.size < planeSize) return emptyList()
            return DbPostProcessor.boxes(
                map = tensor.values,
                offset = tensor.values.size - planeSize.toInt(),
                width = mapWidth,
                height = mapHeight,
                threshold = model.detectorContract?.dbThreshold?.toFloat() ?: 0.3f,
                boxThreshold = model.detectorContract?.boxThreshold?.toFloat() ?: 0.6f,
                unclipRatio = model.detectorContract?.unclipRatio?.toFloat() ?: 1.5f,
                sourceWidth = bitmap.width,
                sourceHeight = bitmap.height,
                maximumDetections = maximumDetections,
            )
        } finally {
            if (resized !== bitmap) resized.recycle()
        }
    }

    /** Reads [crops] with one recognizer, in width-sorted batches; one result per crop, in order. */
    private suspend fun recognizeBatch(
        crops: List<Bitmap>,
        model: OcrCatalogModel,
        priority: OcrRequestPriority,
    ): List<Candidate?> {
        val height = model.preprocessing.resize.height
        val minimumWidth = model.preprocessing.resize.width
        val textWidths = crops.map {
            OcrCropGeometry.recognizerWidths(it.width, it.height, height, minimumWidth).textWidth
        }
        val results = arrayOfNulls<Candidate>(crops.size)
        for (batch in OcrRecognitionBatches.plan(textWidths, minimumWidth)) {
            currentCoroutineContext().ensureActive()
            // Each crop is scaled to the model height keeping its aspect ratio and zero padded
            // (the normalised padding value, as in PaddleOCR) to the batch's widest crop.
            val inputWidth = max(minimumWidth, batch.maxOf(textWidths::get))
            val sample = 3 * height * inputWidth
            val input = directFloats(batch.size * sample)
            batch.forEachIndexed { slot, index ->
                val width = textWidths[index]
                val resized = Bitmap.createScaledBitmap(crops[index], width, height, true)
                try {
                    val pixels = IntArray(width * height)
                    resized.getPixels(pixels, 0, width, 0, 0, width, height)
                    normalizeInto(input, slot * sample, pixels, width, height, inputWidth, model, defaultMean = 0.5f, defaultStd = 0.5f)
                } finally {
                    if (resized !== crops[index]) resized.recycle()
                }
            }
            input.rewind()
            val output = sessionManager.run(
                model,
                priority,
                input,
                longArrayOf(batch.size.toLong(), 3, height.toLong(), inputWidth.toLong()),
            ) { result -> firstOutput(result) }
            if (output.shape.size < 3) continue
            val classCount = output.shape.last()
            val timeSteps = output.shape[output.shape.size - 2]
            val rowSize = timeSteps.toLong() * classCount
            if (classCount <= 1 || timeSteps <= 0 || rowSize * batch.size > output.values.size) continue
            batch.forEachIndexed { slot, index ->
                val start = (slot * rowSize).toInt()
                val row = output.values.copyOfRange(start, start + rowSize.toInt())
                results[index] = decodeLine(row, timeSteps, classCount, model, textWidths[index].toFloat() / inputWidth)
            }
        }
        return results.toList()
    }

    private fun decodeLine(
        values: FloatArray,
        timeSteps: Int,
        classCount: Int,
        model: OcrCatalogModel,
        contentFraction: Float,
    ): Candidate? {
        val contract = model.recognizerContract ?: return null
        val decoded = CtcDecoder.decode(
            values,
            timeSteps,
            classCount,
            models.dictionary(model),
            contract.ctcBlankIndex,
            probabilities = contract.outputInterpretation == "PROBABILITIES",
            reverseVisualOrder = readsRightToLeft(model),
        ) ?: return null
        val average = decoded.tokens.map { it.confidence }.average().takeIf(Double::isFinite)?.toFloat() ?: 0f
        val calibrated = (average * contract.confidenceCalibration.scale + contract.confidenceCalibration.offset)
            .toFloat().coerceIn(0f, 1f)
        return Candidate(packIdOf(model), decoded, calibrated, contentFraction)
    }

    /**
     * Writes [pixels] (`width` × `height`, ARGB) into [target] from [start] as normalised CHW floats in
     * the model's colour order, with rows [stride] wide; anything past `width` keeps the zero the
     * direct buffer was allocated with. Rows go through one small array, never a full-size copy.
     */
    private fun normalizeInto(
        target: FloatBuffer,
        start: Int,
        pixels: IntArray,
        width: Int,
        height: Int,
        stride: Int,
        model: OcrCatalogModel,
        defaultMean: Float,
        defaultStd: Float,
    ) {
        val preprocessing = model.preprocessing
        val scale = preprocessing.scale.toFloat()
        val mean = FloatArray(3) { preprocessing.mean.getOrNull(it)?.toFloat() ?: defaultMean }
        val inverseStd = FloatArray(3) { 1f / (preprocessing.std.getOrNull(it)?.toFloat() ?: defaultStd) }
        val shifts = if (preprocessing.colorOrder.equals("BGR", ignoreCase = true)) {
            intArrayOf(0, 8, 16)
        } else {
            intArrayOf(16, 8, 0)
        }
        val plane = height * stride
        val row = FloatArray(width)
        for (channel in 0..2) {
            val shift = shifts[channel]
            val channelMean = mean[channel]
            val channelScale = inverseStd[channel]
            for (y in 0 until height) {
                val source = y * width
                for (x in 0 until width) {
                    val value = (pixels[source + x] shr shift) and 0xff
                    row[x] = (value * scale - channelMean) * channelScale
                }
                target.position(start + channel * plane + y * stride)
                target.put(row, 0, width)
            }
        }
    }

    /** The first output tensor as a flat float array, read from its buffer (no boxed values). */
    private fun firstOutput(result: OrtSession.Result): FlatTensor {
        val tensor = result[0] as? OnnxTensor ?: error("OCR model output is not a tensor")
        val shape = tensor.info.shape
        require(shape.all { it in 0..Int.MAX_VALUE.toLong() }) { "Unexpected OCR output shape" }
        val buffer = tensor.floatBuffer
        val values = FloatArray(buffer.remaining())
        buffer.get(values)
        return FlatTensor(IntArray(shape.size) { shape[it].toInt() }, values)
    }

    /** Whether [model] is the recognizer of a right-to-left script pack (its output is in visual order). */
    private fun readsRightToLeft(model: OcrCatalogModel): Boolean = models.catalog.packs.any { pack ->
        model.id in pack.rootModelIds && pack.scripts.any { it in RIGHT_TO_LEFT_SCRIPTS }
    }

    private fun packIdOf(model: OcrCatalogModel): String =
        models.catalog.packs.firstOrNull { model.id in it.rootModelIds }?.id ?: model.id

    private fun buildDocument(
        width: Int,
        height: Int,
        packIds: Set<String>,
        recognized: List<RecognizedLine>,
    ): OcrDocument {
        val mergeLines = recognized.mapIndexed { index, item ->
            OcrLine(
                id = item.id,
                readingIndex = index,
                text = item.candidate.result.text,
                startOffset = 0,
                endOffset = item.candidate.result.text.length,
                quad = item.sourceQuad,
                confidence = item.candidate.calibratedConfidence,
            )
        }
        val retainedIds = OcrSeamMerge.mergeLines(mergeLines).mapTo(HashSet(), OcrLine::id)
        val retained = recognized.filter { it.id in retainedIds }
        val byId = retained.associateBy(RecognizedLine::id)
        // Columns run right to left only when most lines are right-to-left text, not whenever an
        // Arabic pack is enabled (it also reads the Latin text next to it).
        val rtl = retained.count { CtcDecoder.hasRightToLeftText(it.candidate.result.text) } * 2 > retained.size
        val vertical = retained.isNotEmpty() && retained.count {
            val bounds = OcrGeometry.bounds(it.sourceQuad)
            bounds.height > bounds.width * VERTICAL_LINE_RATIO
        } * 2 > retained.size
        val ordered = OcrReadingOrder.order(
            retained.mapIndexed { index, item ->
                OcrLine(item.id, index, item.candidate.result.text, 0, item.candidate.result.text.length,
                    item.sourceQuad, item.candidate.calibratedConfidence)
            },
            rtl = rtl,
            vertical = vertical,
        ).mapNotNull { byId[it.id] }.take(OcrLimits.MAX_LINES)

        val fullText = StringBuilder()
        val lines = ArrayList<OcrLine>(ordered.size)
        var codePoints = 0
        var wordReadingIndex = 0
        for (item in ordered) {
            val text = item.candidate.result.text
            val addedCodePoints = text.codePointCount(0, text.length) + if (lines.isEmpty()) 0 else 1
            if (codePoints + addedCodePoints > OcrLimits.MAX_TEXT_CODE_POINTS) break
            if (lines.isNotEmpty()) fullText.append('\n')
            val start = fullText.length
            fullText.append(text)
            val end = fullText.length
            codePoints += addedCodePoints
            val lineIndex = lines.size
            val lineId = stableOcrId(item.candidate.packId, lineIndex, text, item.sourceQuad.points)
            val words = item.candidate.result.words
                .take(OcrLimits.MAX_TOKENS - wordReadingIndex)
                .map { word ->
                    // From the CTC time steps, not the token count: letters are not evenly spaced, the
                    // input is padded past the text, and right-to-left tokens run backwards.
                    val (spanStart, spanEnd) = item.candidate.result.wordSpan(word, item.candidate.contentFraction)
                    // Text read turned 180° runs from the far end of the quad.
                    val (leftFraction, rightFraction) = if (item.flipped) 1f - spanEnd to 1f - spanStart else spanStart to spanEnd
                    OcrWord(
                        id = stableOcrId(lineId, wordReadingIndex, word.text),
                        readingIndex = wordReadingIndex++,
                        text = word.text,
                        startOffset = start + word.startOffset,
                        endOffset = start + word.endOffset,
                        quad = OcrCropGeometry.span(item.sourceQuad, leftFraction, rightFraction, item.rotated),
                        confidence = word.confidence,
                    )
                }
            lines += OcrLine(
                id = lineId,
                readingIndex = lineIndex,
                text = text,
                startOffset = start,
                endOffset = end,
                quad = item.sourceQuad,
                confidence = item.candidate.calibratedConfidence,
                words = words,
                packId = item.candidate.packId,
            )
        }
        return OcrDocument(
            sourceWidth = width,
            sourceHeight = height,
            fullText = fullText.toString(),
            attemptedPackIds = packIds.sorted(),
            lines = lines,
        )
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap = Bitmap.createBitmap(
        bitmap,
        0,
        0,
        bitmap.width,
        bitmap.height,
        Matrix().apply { postRotate(degrees) },
        true
    )

    private fun cropPerspective(bitmap: Bitmap, quad: PixelQuad): Bitmap? {
        val points = quad.points
        val outputWidth = quad.cropWidth.roundToInt().coerceIn(1, OcrLimits.OVERVIEW_MAX_DIMENSION)
        val outputHeight = quad.cropHeight.roundToInt().coerceIn(1, OcrLimits.OVERVIEW_MAX_DIMENSION)
        if (outputWidth < 2 || outputHeight < 2 ||
            OcrLimits.checkedRgbaByteCount(outputWidth, outputHeight) == null
        ) return null
        val output = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
        try {
            val matrix = Matrix()
            val source = floatArrayOf(
                points[0].x, points[0].y,
                points[1].x, points[1].y,
                points[2].x, points[2].y,
                points[3].x, points[3].y,
            )
            val destination = floatArrayOf(
                0f, 0f,
                outputWidth.toFloat(), 0f,
                outputWidth.toFloat(), outputHeight.toFloat(),
                0f, outputHeight.toFloat(),
            )
            if (!matrix.setPolyToPoly(source, 0, destination, 0, 4)) {
                output.recycle()
                return null
            }
            Canvas(output).apply {
                concat(matrix)
                drawBitmap(bitmap, 0f, 0f, null)
            }
            return output
        } catch (error: Throwable) {
            output.takeUnless(Bitmap::isRecycled)?.recycle()
            throw error
        }
    }

    private fun directFloats(size: Int): FloatBuffer = ByteBuffer.allocateDirect(size * Float.SIZE_BYTES)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()

    private data class FlatTensor(val shape: IntArray, val values: FloatArray)
    private data class Candidate(
        val packId: String,
        val result: CtcDecodeResult,
        val calibratedConfidence: Float,
        /** Share of the recognizer input width the text filled before padding. */
        val contentFraction: Float,
    )
    private data class RecognizedLine(
        val id: String,
        val sourceQuad: NormalizedQuad,
        val candidate: Candidate,
        /** The crop was turned anticlockwise: the text runs from the quad's top edge to its bottom. */
        val rotated: Boolean,
        /** The text was read turned 180°: it runs the other way along the quad. */
        val flipped: Boolean = false,
    )
    private class LineCrop(val index: Int, val quad: PixelQuad, val bitmap: Bitmap, val rotated: Boolean)
    private class LineRead(val candidate: Candidate, val flipped: Boolean)
    private data class TileResult(val lines: List<RecognizedLine>)
    private companion object {
        const val LOW_MEMORY_TILE_EDGE = 1_024
        const val MAX_RAW_DETECTIONS = OcrLimits.MAX_LINES * 2
        const val MIN_DETECTIONS_PER_TILE = 16
        const val MAX_DETECTOR_PIXELS = OcrLimits.MAX_DECODE_BYTES / 4L
        const val MIN_LINE_CONFIDENCE = 0.30f
        const val VERTICAL_LINE_RATIO = 1.25f
        const val LINES_PER_CHUNK = 48
        const val TAG = "ml.ocr"
        val RIGHT_TO_LEFT_SCRIPTS = setOf("Arab", "Hebr", "Syrc", "Thaa")
    }
}
