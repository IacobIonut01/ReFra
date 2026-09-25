package com.dot.gallery.core.decoder

import android.os.Build
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

fun ComponentRegistry.Builder.supportHeifDecoder(): ComponentRegistry.Builder = apply {
    add(SketchHeifDecoder.Factory())
}

@Suppress("SpellCheckingInspection")
class SketchHeifDecoder(
    private val requestContext: RequestContext,
    private val dataSource: DataSource,
    private val mimeType: String
) : Decoder {

    class Factory : Decoder.Factory {

        override val key: String
            get() = "HeifDecoder"

        override val sortWeight: Int = 0

        override fun create(requestContext: RequestContext, fetchResult: FetchResult): Decoder? {
            val mimeType = resolveMimeType(requestContext, fetchResult) ?: return null
            return SketchHeifDecoder(requestContext, fetchResult.dataSource, mimeType)
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
            internal fun sniffHeifMimeType(header: ByteArray): String? {
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

    override suspend fun getImageInfo(): ImageInfo {
        return dataSource.getImageInfo(
            requestContext = requestContext,
            mimeType = mimeType,
            getSize = HeifDecodeEngine::getSize
        )
    }

}