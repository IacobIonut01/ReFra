package com.dot.gallery.core.decoder

import android.content.ContentResolver
import android.os.Build
import android.os.CancellationSignal
import android.os.OperationCanceledException
import android.provider.MediaStore
import com.dot.gallery.core.image.thumbnail.HeavyThumbnailClassifier
import com.dot.gallery.core.logging.printWarn
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import com.github.panpf.sketch.ComponentRegistry
import com.github.panpf.sketch.request.ImageData
import com.github.panpf.sketch.decode.Decoder
import com.github.panpf.sketch.decode.ImageInfo
import com.github.panpf.sketch.fetch.FetchResult
import com.github.panpf.sketch.request.RequestContext
import com.github.panpf.sketch.request.get
import com.github.panpf.sketch.source.DataSource
import com.dot.gallery.core.decoder.format.HeifDecodeEngine
import com.dot.gallery.core.decoder.glide.HeifUriProbe
import okio.buffer
import com.dot.gallery.core.decoder.glide.HeifSniffer
import com.dot.gallery.core.util.and
import com.dot.gallery.core.util.or




private const val HEIF_PREVIEW_TAG = "decode.heif"

fun ComponentRegistry.Builder.supportHeifDecoder(): ComponentRegistry.Builder = apply {
    add(SketchHeifDecoder.Factory())
}

@Suppress("SpellCheckingInspection")
class SketchHeifDecoder(
    private val requestContext: RequestContext,
    private val dataSource: DataSource,
    private val mimeType: String,
    private val allowPlatformPreview: Boolean = false,
) : Decoder {

    class Factory : Decoder.Factory {

        override val key: String
            get() = "HeifDecoder"

        override val sortWeight: Int = 0

        override fun create(requestContext: RequestContext, fetchResult: FetchResult): Decoder? {
            val mimeType = resolveMimeType(requestContext, fetchResult) ?: return null
            // Animated sequences keep the full decode so their preview can still animate.
            val allowPlatformPreview =
                requestContext.request.extras?.get(PLATFORM_PREVIEW_EXTRA) as? Boolean == true &&
                    !isAnimatedHeif(fetchResult.headerBytes)
            return SketchHeifDecoder(
                requestContext, fetchResult.dataSource, mimeType, allowPlatformPreview
            )
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            return other is Factory
        }

        override fun hashCode(): Int {
            return this@Factory::class.hashCode()
        }

        override fun toString(): String = key

        companion object {
            /**
             * Request extra (Boolean) marking a low-res, transitional preview. HEIC with this
             * extra may be served from the platform's cached MediaStore thumbnail instead of a
             * full HEVC decode of every tile, which took long enough to leave the viewer's
             * opening animation black (#1385). Set only by the viewer's `mediaPreviewOptions`.
             */
            const val PLATFORM_PREVIEW_EXTRA = "heifPlatformPreview"

            val HEIF_MIMETYPES = listOf(
                "image/heif",
                "image/heic",
                "image/heif-sequence",
                "image/heic-sequence",
                "image/avif",
                "image/avis"
            )

            /**
             * Resolve a HEIF-family MIME for this fetch, or null when none can be established.
             * Order: caller-supplied `realMimeType` extra, the provider-reported MIME when it
             * names a concrete format, then a strict `ftyp` brand sniff of the header bytes
             * for missing/generic MIMEs. A specific non-HEIF MIME resolves to null, keeping
             * those requests on Sketch's default decoder.
             */
            fun resolveMimeType(requestContext: RequestContext, fetchResult: FetchResult): String? {
                val mimeType = requestContext.request.extras?.get("realMimeType") as? String
                    ?: fetchResult.mimeType?.takeIf { HeifUriProbe.isUsableMime(it) }
                    ?: sniffHeifMimeType(fetchResult.headerBytes)
                    ?: return null
                return mimeType.takeIf { mime -> HEIF_MIMETYPES.any { mime.lowercase().contains(it) } }
            }

            /**
             * Canonical ISO-BMFF sniff: 'ftyp' at offset 4 with a HEIF-family primary brand.
             * Deliberately skips [HeifSniffer]'s heuristic substring pass, which can
             * false-positive on non-HEIF payloads containing a brand string.
             */
            // public: unit-tested from app test source set
            fun sniffHeifMimeType(header: ByteArray): String? {
                if (header.size < 12 || String(header, 4, 4, Charsets.ISO_8859_1) != "ftyp") return null
                return when (String(header, 8, 4, Charsets.ISO_8859_1).lowercase()) {
                    "avif", "avis" -> "image/avif"
                    "heic", "heix", "heif", "hevc", "hevx",
                    "heim", "heis", "hevm", "hevs", "mif1", "msf1" -> "image/heif"
                    else -> null
                }
            }
        }
    }

    override suspend fun decode(): ImageData {
        decodePlatformPreview()?.let { return it }

        val sourceData = dataSource.openSource().use { src ->
            src.buffer().readByteArray()
        }

        // Animated HEIC/AVIF sequence: requires API 31+ for ImageDecoder sequence support. Returns
        // null (falls through to a static decode) when the platform can't animate this container.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isAnimatedHeif(sourceData)) {
            val animated = decodeAnimatedHeif(
                bytes = sourceData,
                requestContext = requestContext,
                dataFrom = dataSource.dataFrom,
                mimeType = mimeType,
                getSize = HeifDecodeEngine::getSize
            )
            if (animated != null) return animated
        }

        // Hardware-first, software fallback (unified engine). This is the media viewer's base
        // painter (and the gain-map probe that drives per-page COLOR_MODE_HDR), so decode with HDR
        // ENABLED: keep any Ultra HDR gain map / 10-bit HLG/PQ color space so the fit-view image
        // renders true HDR on a capable display. Zoomed subsampling tiles remain SDR (region
        // decoders can't reproduce the gain map), so a slight brightness shift can appear when
        // zooming into HDR highlights — an accepted tradeoff, since the HDR pop matters most at fit
        // view. The grid (Glide) path stays allowHdr=false. On SDR displays / SDR images the gain
        // map is simply not applied, so this is a no-op there.
        val target = requestContext.size
        val reqW = if (target == com.github.panpf.sketch.util.Size.Origin) 0 else target.width
        val reqH = if (target == com.github.panpf.sketch.util.Size.Origin) 0 else target.height
        HeifDecodeEngine.decode(sourceData, reqW, reqH, allowHdr = true)?.let {
            return imageDataFromBitmap(it, requestContext, dataSource.dataFrom, mimeType)
        }

        // Last resort: software-only path via the engine (also drives the request's scaled resize).
        return decodeStaticFromBytes(
            sourceData = sourceData,
            requestContext = requestContext,
            dataFrom = dataSource.dataFrom,
            mimeType = mimeType,
            getSize = HeifDecodeEngine::getSize,
            decodeSampled = { bytes, w, h ->
                HeifDecodeEngine.decodeSoftware(bytes, w, h)
                    ?: throw IllegalStateException("Unable to decode HEIF image")
            }
        )
    }

    /**
     * Platform-thumbnail fast path for preview requests (see [Factory.PLATFORM_PREVIEW_EXTRA]).
     * Returns null, so the regular decode runs, for anything it does not cover: non-MediaStore
     * URIs, unbounded sizes, over-10-bit HEIF (the platform thumbnailer corrupts it) and platform
     * failures.
     */
    private suspend fun decodePlatformPreview(): ImageData? {
        if (!allowPlatformPreview) return null
        val uri = requestContext.request.uri
        if (uri.scheme != ContentResolver.SCHEME_CONTENT || uri.authority != MediaStore.AUTHORITY) {
            return null
        }
        val target = requestContext.size
        if (target == com.github.panpf.sketch.util.Size.Origin ||
            target.width <= 0 || target.height <= 0
        ) return null
        val resolver = requestContext.sketch.context.contentResolver
        val androidUri = android.net.Uri.parse(uri.toString())
        val heavyFormat = runCatching { HeavyThumbnailClassifier.classify(resolver, androidUri) }
            .getOrElse {
                printWarn(
                    HEIF_PREVIEW_TAG,
                    "preview classification failed; using full decode",
                    ctx = mapOf("uri" to uri.toString(), "error" to it.toString()),
                )
                return null
            }
        if (heavyFormat != null) return null
        val signal = CancellationSignal()
        currentCoroutineContext().job.invokeOnCompletion { signal.cancel() }
        return try {
            val bitmap = resolver.loadThumbnail(
                androidUri,
                android.util.Size(target.width, target.height),
                signal,
            )
            imageDataFromBitmap(bitmap, requestContext, dataSource.dataFrom, mimeType)
        } catch (e: OperationCanceledException) {
            throw e
        } catch (e: Exception) {
            printWarn(
                HEIF_PREVIEW_TAG,
                "platform preview thumbnail failed; using full decode",
                ctx = mapOf("uri" to uri.toString(), "error" to e.toString()),
            )
            null
        }
    }

    override suspend fun getImageInfo(): ImageInfo {
        return dataSource.getImageInfo(
            requestContext = requestContext,
            mimeType = mimeType,
            getSize = HeifDecodeEngine::getSize
        )
    }

}