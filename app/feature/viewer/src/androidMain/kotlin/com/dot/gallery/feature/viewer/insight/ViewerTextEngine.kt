/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.viewer.insight

import android.content.Context
import android.graphics.Bitmap
import com.dot.gallery.core.insight.ImageQuad
import com.dot.gallery.core.insight.Insight
import com.dot.gallery.core.insight.TextInsight
import com.dot.gallery.core.insight.TextLine
import com.dot.gallery.core.logging.printDebug
import com.dot.gallery.core.logging.printWarn
import com.dot.gallery.core.ocr.domain.OcrDocument
import com.dot.gallery.core.ocr.domain.OcrLimits
import com.dot.gallery.core.ocr.engine.OcrRequestPriority
import com.dot.gallery.domain.model.Media
import com.dot.gallery.domain.util.getUri
import com.dot.gallery.domain.util.isCloud
import com.dot.gallery.domain.util.isEncrypted
import com.dot.gallery.feature.viewer.components.media.HEIF_PLATFORM_PREVIEW_EXTRA
import com.dot.gallery.feature.viewer.components.media.mediaPreviewOptions
import com.github.panpf.sketch.asBitmapOrNull
import com.github.panpf.sketch.cache.CachePolicy
import com.github.panpf.sketch.request.ImageRequest
import com.github.panpf.sketch.sketch
import kotlinx.coroutines.CancellationException

private const val TAG = "insight.ocr"

/**
 * Reads the text of the settled photo for the viewer.
 *
 * Decodes a bounded copy (long side [OcrLimits.OVERVIEW_MAX_DIMENSION]) through the viewer's Sketch
 * pipeline with every cache disabled, so nothing decoded reaches disk (vault media included), runs
 * the engine at interactive priority and recycles the copy. Cloud media uses the preview already in
 * memory and never downloads. Results stay in the controller's memory; this engine never persists.
 */
class ViewerTextEngine(
    context: Context,
    private val runtime: OcrRuntime,
    private val packs: () -> List<String>,
) : InsightEngine<Media> {
    private val appContext = context.applicationContext

    override suspend fun analyze(target: Media, thorough: Boolean): List<Insight> {
        // One full read per photo: the "thorough" retry the controller makes after an empty result is a no-op.
        if (thorough) return emptyList()
        val packIds = packs()
        if (packIds.isEmpty() || !runtime.store.areReady(packIds)) return emptyList()
        // Indexed photos show their lines straight from the text index (never for vault media).
        val revision = runtime.store.revision(packIds)
        val indexed = if (target.isEncrypted) null else runCatching { runtime.repository?.current(target, revision) }
            .onFailure { printWarn(TAG, "Text index lookup failed", mapOf("error" to it.toString())) }
            .getOrNull()
        if (indexed != null) return listOfNotNull(indexed.toInsight(packIds.first()))
        val bitmap = decodeForTextRecognition(appContext, target) ?: return emptyList()
        return try {
            val start = System.nanoTime()
            val document = runtime.engine.recognize(bitmap, packIds.toSet(), OcrRequestPriority.INTERACTIVE)
            printDebug(TAG, "read ${bitmap.width}x${bitmap.height} lines=${document.lines.size} in ${(System.nanoTime() - start) / 1_000_000} ms")
            listOfNotNull(document.toInsight(packIds.first()))
        } finally {
            bitmap.recycle()
        }
    }

    override fun release() = Unit
}

/**
 * Decodes [media] for text recognition: a bounded copy (long side [OcrLimits.OVERVIEW_MAX_DIMENSION])
 * through the viewer's Sketch pipeline with every cache disabled, so nothing decoded reaches disk.
 * Cloud media uses its preview and never downloads an original. The caller owns and recycles the bitmap.
 */
suspend fun decodeForTextRecognition(context: Context, media: Media): Bitmap? = try {
    val appContext = context.applicationContext
    val side = OcrLimits.OVERVIEW_MAX_DIMENSION
    appContext.sketch.execute(
        ImageRequest(appContext, media.getUri().toString()) {
            if (media.isCloud) mediaPreviewOptions(media) else mediaPreviewOptions(media, side, side)
            setExtra(key = HEIF_PLATFORM_PREVIEW_EXTRA, value = false)
            memoryCachePolicy(CachePolicy.DISABLED)
            resultCachePolicy(CachePolicy.DISABLED)
        }
    ).image?.asBitmapOrNull()?.let { decoded ->
        if (decoded.config == Bitmap.Config.ARGB_8888) decoded
        else decoded.copy(Bitmap.Config.ARGB_8888, false).also { decoded.recycle() }
    }
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    printWarn(TAG, "Decode for text recognition failed", mapOf("error" to e.toString()))
    null
}

/**
 * Maps an engine document to the viewer insight. Each line keeps the pack that read it; [packId] is
 * only the fallback for documents stored before lines recorded their pack.
 */
fun OcrDocument.toInsight(packId: String): TextInsight? = TextInsight.of(
    lines.map { line ->
        val q = line.quad
        TextLine(
            text = line.text,
            quad = ImageQuad(
                q.topLeft.x, q.topLeft.y, q.topRight.x, q.topRight.y,
                q.bottomRight.x, q.bottomRight.y, q.bottomLeft.x, q.bottomLeft.y,
            ),
            confidence = line.confidence,
            packId = line.packId ?: packId,
        )
    }
)
