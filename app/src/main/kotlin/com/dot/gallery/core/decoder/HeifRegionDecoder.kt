/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.decoder

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Canvas
import android.graphics.Gainmap
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.util.Size as AndroidSize
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import com.dot.gallery.core.decoder.format.HeifBitstreamProbe
import com.dot.gallery.core.decoder.format.HeifDecodeEngine
import com.github.panpf.zoomimage.subsampling.BitmapTileImage
import com.github.panpf.zoomimage.subsampling.ImageInfo
import com.github.panpf.zoomimage.subsampling.ImageSource
import com.github.panpf.zoomimage.subsampling.RegionDecoder
import com.github.panpf.zoomimage.subsampling.SubsamplingImage
import com.github.panpf.zoomimage.subsampling.TileBitmap
import com.github.panpf.zoomimage.subsampling.internal.ExifOrientationHelper
import com.github.panpf.zoomimage.util.IntRectCompat
import com.github.panpf.zoomimage.util.IntSizeCompat
import okio.buffer
import java.nio.ByteBuffer
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * A zoomimage [RegionDecoder] for HEIC/HEIF (and AVIF, same container) images.
 *
 * Three decode modes, chosen once per shared source in [SharedHeifRegion.ensureProbed]:
 *  1. **Native tiled** — libheif's per-tile decode ([NativeHeifTiler]) reads only the grid tiles
 *     overlapping the requested region, each at native resolution, and assembles them into a
 *     bounded output. This is the guaranteed path for very large (e.g. 100MP) and 10-bit HDR grid
 *     HEICs: memory stays small and detail is pixel-crisp. Preferred when the image is large
 *     (> [NATIVE_PIXEL_THRESHOLD]) or >8-bit; requires the prebuilt native library for the ABI.
 *  2. **Hardware tiled** — the platform [BitmapRegionDecoder] (API 28+, minSdk here is 29) routes
 *     to the device HEVC/AV1 codec and decodes only the requested rectangle. Preferred for small
 *     SDR images (faster than the software libde265 tiler) and used whenever native is unavailable.
 *  3. **Software full-crop fallback** — last resort when neither above works: the image is decoded
 *     once via [HeifDecodeEngine], capped to [MAX_FALLBACK_DIM] on the long edge to bound RAM, and
 *     each tile is cropped from that shared bitmap (region coordinates scaled to the capped size).
 *
 * [getImageInfo] always reports the true original pixel size so zoomimage's tile math is correct
 * in both modes. The shared native decoder / bitmap is reference-counted across pooled [copy]
 * instances and released exactly once when the last copy is [close]d.
 */
class HeifRegionDecoder(
    val subsamplingImage: SubsamplingImage,
    val imageSource: ImageSource,
    private val hdrDisplay: Boolean = true,
    private val shared: SharedHeifRegion = SharedHeifRegion(imageSource, hdrDisplay),
) : RegionDecoder {

    private val cachedImageInfo: ImageInfo by lazy {
        val size = shared.originalSize() ?: AndroidSize(0, 0)
        HeifDebug.d("getImageInfo -> ${size.width}x${size.height}")
        ImageInfo(size.width, size.height, HEIF_MIMETYPE)
    }

    override fun getImageInfo(): ImageInfo = cachedImageInfo

    override fun prepare() {
        shared.acquire()
    }

    override fun decodeRegion(region: IntRectCompat, sampleSize: Int): TileBitmap {
        return shared.decodeRegion(region, sampleSize)
    }

    override fun copy(): RegionDecoder = HeifRegionDecoder(
        subsamplingImage = subsamplingImage,
        imageSource = imageSource,
        hdrDisplay = hdrDisplay,
        shared = shared,
    )

    override fun close() {
        shared.release()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as HeifRegionDecoder
        if (subsamplingImage != other.subsamplingImage) return false
        if (imageSource != other.imageSource) return false
        return true
    }

    override fun hashCode(): Int {
        var result = subsamplingImage.hashCode()
        result = 31 * result + imageSource.hashCode()
        return result
    }

    override fun toString(): String =
        "HeifRegionDecoder(subsamplingImage=$subsamplingImage, imageSource=$imageSource)"

    /**
     * Reference-counted holder shared across pooled decoder copies. Owns either the platform
     * [BitmapRegionDecoder] (hardware tiled mode) or the capped full [Bitmap] (software fallback),
     * and releases whichever it holds exactly once when the last reference is dropped.
     */
    class SharedHeifRegion(
        private val imageSource: ImageSource,
        /** When false (SDR-only display), the gain-map probe decode is skipped entirely. */
        private val hdrDisplay: Boolean = true,
    ) {

        private val bytes: ByteArray by lazy {
            imageSource.openSource().buffer().use { it.readByteArray() }
        }

        /** EXIF orientation of the source, or [ExifInterface.ORIENTATION_NORMAL] if absent. */
        private val exifOrientation: Int by lazy {
            runCatching {
                bytes.inputStream().use {
                    ExifInterface(it).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL,
                    )
                }
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        }

        // Set in HARDWARE mode when the source carries an EXIF orientation transform, so
        // BitmapRegionDecoder's raw-space tiles are rotated/mirrored to the displayed geometry.
        private var hardwareExif: ExifOrientationHelper? = null

        // Source Ultra HDR gain map (displayed-space, EXIF-oriented) shared across tiles, loaded once
        // on the first region decode. Null when the image is SDR or the platform predates API 34.
        // Each tile gets a spatially-cropped copy so zoomed regions render HDR-consistent with the
        // base painter (which keeps its gain map). See [attachTileGainmap].
        private var gainmapLoaded = false
        private var sourceGainmap: Gainmap? = null

        private val lock = Any()
        private var refCount = 0
        private var probed = false

        // Native libheif tiled mode (preferred): memory-bounded, native-resolution grid tiles.
        private var nativeHandle = 0L
        private var tileWidth = 0
        private var tileHeight = 0
        private var numColumns = 0
        private var numRows = 0

        // Hardware mode: platform BitmapRegionDecoder.
        private var regionDecoder: BitmapRegionDecoder? = null

        // Software fallback: one capped full bitmap.
        private var fullBitmap: Bitmap? = null
        private var fullDecodeAttempted = false

        private var origWidth = 0
        private var origHeight = 0

        fun originalSize(): AndroidSize? = synchronized(lock) {
            ensureProbed()
            if (origWidth > 0 && origHeight > 0) AndroidSize(origWidth, origHeight) else null
        }

        fun acquire() = synchronized(lock) {
            ensureProbed()
            refCount++
        }

        fun release() = synchronized(lock) {
            refCount--
            if (refCount <= 0) {
                if (nativeHandle != 0L) {
                    NativeHeifTiler.close(nativeHandle)
                    nativeHandle = 0L
                }
                runCatching { regionDecoder?.recycle() }
                regionDecoder = null
                hardwareExif = null
                // Drop the reference; the contents bitmap and any per-tile crops are GC-managed
                // (crops copy their pixels, so they outlive this and stay valid for cached tiles).
                sourceGainmap = null
                gainmapLoaded = false
                fullBitmap?.let { if (!it.isRecycled) it.recycle() }
                fullBitmap = null
                fullDecodeAttempted = false
                refCount = 0
                probed = false
            }
        }

        /**
         * Lightweight one-time probe choosing the decode mode. Order:
         *  1. Native libheif tiler (guaranteed memory-bounded tiled decode, incl. 100MP / 10-bit
         *     HDR grid HEIC) when the native library is available.
         *  2. Platform BitmapRegionDecoder (hardware) when it can open + probe the stream.
         *  3. Capped software full-decode-then-crop as a last resort.
         * Deliberately does NOT run any full-image decode here.
         */
        private fun ensureProbed() {
            if (probed) return
            HeifDecodeEngine.getSize(bytes)?.let {
                origWidth = it.width
                origHeight = it.height
            }

            // >10-bit (12-bit) sources: the platform region decoder silently corrupts them and the
            // bundled libheif tiler has no AV1 decoder, so the only correct path is the capped
            // software decode — select it directly instead of arriving via per-tile failures.
            if (HeifBitstreamProbe.needsSoftwareDecode(bytes)) {
                probed = true
                HeifDebug.d("mode=SOFTWARE (>10-bit declared) ${origWidth}x${origHeight}")
                return
            }

            if (NativeHeifTiler.isAvailable) {
                val handle = NativeHeifTiler.open(bytes)
                val info = if (handle != 0L) NativeHeifTiler.getInfo(handle) else null
                if (handle != 0L && info != null && info.size >= 6 &&
                    info[0] > 0 && info[1] > 0 && info[2] > 0 && info[3] > 0
                ) {
                    val iw = info[0]
                    val ih = info[1]
                    origWidth = iw
                    origHeight = ih
                    val lumaBits = if (info.size >= 7) info[6] else 8
                    val megapixels = iw.toLong() * ih.toLong()
                    // The native libde265 tiler is slower per tile than the hardware
                    // BitmapRegionDecoder, so reserve it for the cases that actually need it:
                    // very large images (memory-bounded tiling) and >8-bit / HDR images (correct
                    // color). Small SDR images use the faster hardware path when it works.
                    val preferNative = lumaBits > 8 || megapixels > NATIVE_PIXEL_THRESHOLD
                    HeifDebug.d(
                        "ensureProbed native-open ok: ${iw}x${ih} tile=${info[2]}x${info[3]} " +
                            "grid=${info[4]}x${info[5]} luma=$lumaBits mp=$megapixels " +
                            "threshold=$NATIVE_PIXEL_THRESHOLD preferNative=$preferNative"
                    )
                    if (!preferNative) {
                        val decoder = runCatching { newRegionDecoder(bytes) }.getOrNull()
                        if (decoder != null && decoder.width > 0 && decoder.height > 0 &&
                            probe(decoder, decoder.width, decoder.height)
                        ) {
                            val dw = decoder.width
                            val dh = decoder.height
                            // BitmapRegionDecoder decodes in RAW (un-rotated) pixel space and ignores
                            // the image's orientation transform, whereas libheif's native tiler here
                            // applies transformations (iw x ih is the DISPLAYED size, matching the
                            // base painter).
                            if (dw == iw && dh == ih) {
                                // No orientation transform: raw tiles already match the displayed
                                // geometry. Use the faster hardware codec.
                                regionDecoder = decoder
                                origWidth = dw
                                origHeight = dh
                                NativeHeifTiler.close(handle)
                                probed = true
                                HeifDebug.d("mode=HARDWARE (small SDR) brd=${dw}x${dh}")
                                return
                            }
                            // Dimensions differ => the image is rotated/mirrored. If that transform
                            // is carried in EXIF we can still use the fast hardware codec: decode raw
                            // tiles and rotate them with ExifOrientationHelper (mirrors
                            // EncryptedRegionDecoder), reporting the displayed (transformed) size so
                            // zoomimage's aspect-ratio gate passes and zoom stays sharp.
                            val orient = exifOrientation
                            val swaps = orient == ExifInterface.ORIENTATION_ROTATE_90 ||
                                orient == ExifInterface.ORIENTATION_ROTATE_270 ||
                                orient == ExifInterface.ORIENTATION_TRANSPOSE ||
                                orient == ExifInterface.ORIENTATION_TRANSVERSE
                            val exifExplains = orient != ExifInterface.ORIENTATION_NORMAL &&
                                orient != ExifInterface.ORIENTATION_UNDEFINED &&
                                if (swaps) dw == ih && dh == iw else dw == iw && dh == ih
                            if (exifExplains) {
                                regionDecoder = decoder
                                hardwareExif = ExifOrientationHelper(orient)
                                origWidth = iw
                                origHeight = ih
                                NativeHeifTiler.close(handle)
                                probed = true
                                HeifDebug.d(
                                    "mode=HARDWARE+EXIF orient=$orient brd=${dw}x${dh} " +
                                        "displayed=${iw}x${ih}"
                                )
                                return
                            }
                            // Orientation transform not carried in EXIF (e.g. HEIF `irot`/`imir`):
                            // BitmapRegionDecoder can't reproduce it, so keep the orientation-correct
                            // native tiler.
                            HeifDebug.w(
                                "BitmapRegionDecoder dims ${dw}x${dh} != native ${iw}x${ih}, " +
                                    "exifOrient=$orient does not explain it; keeping NATIVE tiler"
                            )
                            runCatching { decoder.recycle() }
                        } else {
                            HeifDebug.w("BitmapRegionDecoder probe failed; keeping NATIVE tiler")
                            runCatching { decoder?.recycle() }
                        }
                    }
                    // Keep the native tiler (large/HDR, or hardware region decode unavailable).
                    nativeHandle = handle
                    tileWidth = info[2]
                    tileHeight = info[3]
                    numColumns = info[4]
                    numRows = info[5]
                    probed = true
                    HeifDebug.d("mode=NATIVE tile=${tileWidth}x${tileHeight} grid=${numColumns}x${numRows}")
                    return
                }
                if (handle != 0L) NativeHeifTiler.close(handle)
            }

            val decoder = runCatching { newRegionDecoder(bytes) }.getOrNull()
            if (decoder != null) {
                val dw = decoder.width
                val dh = decoder.height
                if (dw > 0 && dh > 0 && probe(decoder, dw, dh)) {
                    regionDecoder = decoder
                    origWidth = dw
                    origHeight = dh
                    probed = true
                    return
                }
                runCatching { decoder.recycle() }
            }
            probed = true
            HeifDebug.d(
                if (regionDecoder != null) "mode=HARDWARE (native unavailable) ${origWidth}x${origHeight}"
                else "mode=SOFTWARE (capped) ${origWidth}x${origHeight}"
            )
        }

        private fun probe(decoder: BitmapRegionDecoder, w: Int, h: Int): Boolean = runCatching {
            val size = minOf(16, w, h).coerceAtLeast(1)
            val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
            val test = decoder.decodeRegion(Rect(0, 0, size, size), opts)
            val ok = test != null
            test?.recycle()
            ok
        }.getOrDefault(false)

        fun decodeRegion(region: IntRectCompat, sampleSize: Int): Bitmap = synchronized(lock) {
            ensureProbed()
            val mode = when {
                nativeHandle != 0L -> "NATIVE"
                regionDecoder != null -> "HARDWARE"
                else -> "SOFTWARE"
            }
            HeifDebug.d(
                "decodeRegion mode=$mode region=(${region.left},${region.top},${region.right}," +
                    "${region.bottom}) [${region.right - region.left}x${region.bottom - region.top}] " +
                    "sampleSize=$sampleSize"
            )
            val tile: Bitmap = run {
                if (nativeHandle != 0L) {
                    decodeRegionNative(region, sampleSize)?.let {
                        HeifDebug.d("  -> NATIVE tile ${it.width}x${it.height}")
                        return@run it
                    }
                    HeifDebug.w("  NATIVE decodeRegion returned null; falling through")
                }
                val decoder = regionDecoder
                if (decoder != null && !decoder.isRecycled) {
                    decodeRegionHardware(decoder, region, sampleSize)?.let {
                        HeifDebug.d("  -> HARDWARE tile ${it.width}x${it.height}")
                        return@run it
                    }
                    HeifDebug.w("  HARDWARE decodeRegion returned null; falling through")
                }
                val sw = decodeRegionSoftware(region, sampleSize)
                HeifDebug.d("  -> SOFTWARE tile ${sw.width}x${sw.height}")
                sw
            }
            // Make the tile HDR-consistent with the base painter: attach the gain map region that
            // corresponds to this tile. No-op for SDR sources / pre-API-34.
            return attachTileGainmap(tile, region)
        }

        /**
         * Attaches to [tile] the sub-region of the source Ultra HDR gain map that spatially
         * corresponds to [region] (in displayed image coords). Android then renders the tile with
         * the same HDR boost as the base painter, so zooming into HDR content no longer shows an
         * SDR↔HDR brightness seam. No-op below API 34 or for SDR sources.
         */
        private fun attachTileGainmap(tile: Bitmap, region: IntRectCompat): Bitmap {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return tile
            if (origWidth <= 0 || origHeight <= 0) return tile
            val src = sourceGainmapLocked() ?: return tile
            return runCatching {
                val contents = src.gainmapContents
                val gw = contents.width
                val gh = contents.height
                if (gw <= 0 || gh <= 0) return tile
                // Map the tile's displayed-space region onto the (typically lower-res) gain map.
                val sx = gw.toFloat() / origWidth
                val sy = gh.toFloat() / origHeight
                val l = floor(region.left * sx).toInt().coerceIn(0, gw)
                val t = floor(region.top * sy).toInt().coerceIn(0, gh)
                var r = ceil(region.right * sx).toInt().coerceIn(0, gw)
                var b = ceil(region.bottom * sy).toInt().coerceIn(0, gh)
                if (r <= l) r = (l + 1).coerceAtMost(gw)
                if (b <= t) b = (t + 1).coerceAtMost(gh)
                if (r <= l || b <= t) return tile
                // createBitmap(subset) copies pixels for a strict subregion; force a copy in the
                // rare whole-image case so the crop never aliases the shared source contents.
                var crop = Bitmap.createBitmap(contents, l, t, r - l, b - t)
                if (crop === contents) {
                    crop = contents.copy(contents.config ?: Bitmap.Config.ARGB_8888, false)
                }
                tile.gainmap = Gainmap(crop).also { copyGainmapMetadata(src, it) }
                tile
            }.getOrDefault(tile)
        }

        /** Loads (once) the source gain map, or null if the image is SDR. Caller holds [lock]. */
        private fun sourceGainmapLocked(): Gainmap? {
            if (!gainmapLoaded) {
                gainmapLoaded = true
                // Skip the extra probe decode on SDR-only displays: HDR would never render there.
                sourceGainmap = if (hdrDisplay) loadSourceGainmap() else null
            }
            return sourceGainmap
        }

        /**
         * Decodes the source once at a capped resolution (peak RAM bounded — the gain map is
         * low-frequency and scales fine) with the platform's default HDR output, then keeps an
         * independent copy of its gain map. The decode applies EXIF orientation, so the gain map is
         * in displayed-space, matching the tile region coordinates.
         */
        private fun loadSourceGainmap(): Gainmap? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null
            // >10-bit sources decode through the software base painter, which carries no gain map
            // — and probing with the platform decoder would return corrupt pixels anyway.
            if (HeifBitstreamProbe.needsSoftwareDecode(bytes)) return null
            return runCatching {
                val maxEdge = GAINMAP_DECODE_MAX_EDGE
                val source = ImageDecoder.createSource(ByteBuffer.wrap(bytes))
                var result: Gainmap? = null
                val base = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = false
                    val sw = info.size.width
                    val sh = info.size.height
                    val longEdge = maxOf(sw, sh)
                    if (longEdge > maxEdge && sw > 0 && sh > 0) {
                        val scale = maxEdge.toFloat() / longEdge
                        decoder.setTargetSize(
                            (sw * scale).roundToInt().coerceAtLeast(1),
                            (sh * scale).roundToInt().coerceAtLeast(1),
                        )
                    }
                }
                if (base.hasGainmap()) {
                    base.gainmap?.let { original ->
                        val cfg = original.gainmapContents.config ?: Bitmap.Config.ARGB_8888
                        original.gainmapContents.copy(cfg, false)?.let { contentsCopy ->
                            result = Gainmap(contentsCopy).also { copyGainmapMetadata(original, it) }
                        }
                    }
                }
                base.recycle()
                HeifDebug.d(
                    if (result != null) {
                        "gainmap loaded ${result!!.gainmapContents.width}x" +
                            "${result!!.gainmapContents.height} for displayed ${origWidth}x$origHeight"
                    } else {
                        "gainmap absent (SDR source)"
                    }
                )
                result
            }.getOrNull()
        }

        @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        private fun copyGainmapMetadata(from: Gainmap, to: Gainmap) {
            from.ratioMin.let { to.setRatioMin(it[0], it[1], it[2]) }
            from.ratioMax.let { to.setRatioMax(it[0], it[1], it[2]) }
            from.gamma.let { to.setGamma(it[0], it[1], it[2]) }
            from.epsilonSdr.let { to.setEpsilonSdr(it[0], it[1], it[2]) }
            from.epsilonHdr.let { to.setEpsilonHdr(it[0], it[1], it[2]) }
            to.displayRatioForFullHdr = from.displayRatioForFullHdr
            to.minDisplayRatioForHdrTransition = from.minDisplayRatioForHdrTransition
        }

        /**
         * Assembles the requested region from native libheif tiles. Only the grid tiles that
         * overlap [region] are decoded (each at native resolution, ~one tile in RAM at a time),
         * scaled by [sampleSize] into a bounded output bitmap sized to the sampled region — so
         * peak memory stays small even for a 100MP source.
         */
        private fun decodeRegionNative(region: IntRectCompat, sampleSize: Int): Bitmap? {
            val step = sampleSize.coerceAtLeast(1)
            val left = region.left.coerceIn(0, origWidth)
            val top = region.top.coerceIn(0, origHeight)
            val right = region.right.coerceIn(left, origWidth)
            val bottom = region.bottom.coerceIn(top, origHeight)
            if (right <= left || bottom <= top || tileWidth <= 0 || tileHeight <= 0) return null

            val outW = ((right - left) / step).coerceAtLeast(1)
            val outH = ((bottom - top) / step).coerceAtLeast(1)
            val out = runCatching {
                Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
            }.getOrNull() ?: return null
            val canvas = Canvas(out)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG)

            val col0 = (left / tileWidth).coerceIn(0, (numColumns - 1).coerceAtLeast(0))
            val col1 = ((right - 1) / tileWidth).coerceIn(0, (numColumns - 1).coerceAtLeast(0))
            val row0 = (top / tileHeight).coerceIn(0, (numRows - 1).coerceAtLeast(0))
            val row1 = ((bottom - 1) / tileHeight).coerceIn(0, (numRows - 1).coerceAtLeast(0))

            var anyDrawn = false
            for (ty in row0..row1) {
                for (tx in col0..col1) {
                    val px = NativeHeifTiler.decodeTile(nativeHandle, tx, ty) ?: continue
                    if (px.size < 2) continue
                    val tw = px[0]
                    val th = px[1]
                    if (tw <= 0 || th <= 0 || px.size < tw * th + 2) continue
                    val tileBmp = runCatching {
                        Bitmap.createBitmap(px, 2, tw, tw, th, Bitmap.Config.ARGB_8888)
                    }.getOrNull() ?: continue

                    val tileLeft = tx * tileWidth
                    val tileTop = ty * tileHeight
                    val ix0 = maxOf(left, tileLeft)
                    val iy0 = maxOf(top, tileTop)
                    val ix1 = minOf(right, tileLeft + tw)
                    val iy1 = minOf(bottom, tileTop + th)
                    if (ix1 > ix0 && iy1 > iy0) {
                        val src = Rect(ix0 - tileLeft, iy0 - tileTop, ix1 - tileLeft, iy1 - tileTop)
                        val dst = RectF(
                            (ix0 - left).toFloat() / step,
                            (iy0 - top).toFloat() / step,
                            (ix1 - left).toFloat() / step,
                            (iy1 - top).toFloat() / step,
                        )
                        canvas.drawBitmap(tileBmp, src, dst, paint)
                        anyDrawn = true
                    }
                    tileBmp.recycle()
                }
            }

            if (!anyDrawn) {
                out.recycle()
                return null
            }
            return out
        }

        /** Lazily runs the capped full-decode on first software tile request. */
        private fun ensureFullBitmap(): Bitmap? {
            if (fullBitmap != null) return fullBitmap
            if (fullDecodeAttempted) return null
            fullDecodeAttempted = true
            val bitmap = HeifDecodeEngine.decodeCapped(bytes, MAX_FALLBACK_DIM)
            fullBitmap = bitmap
            if (bitmap != null && (origWidth <= 0 || origHeight <= 0)) {
                origWidth = bitmap.width
                origHeight = bitmap.height
            }
            return bitmap
        }

        private fun decodeRegionHardware(
            decoder: BitmapRegionDecoder,
            region: IntRectCompat,
            sampleSize: Int,
        ): Bitmap? = runCatching {
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize.coerceAtLeast(1)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val exif = hardwareExif
            if (exif != null) {
                // [region] is in displayed (transformed) space (origWidth x origHeight); map it back
                // to the decoder's RAW pixel space, decode, then rotate/mirror the tile to match.
                val raw = exif.applyToRect(region, IntSizeCompat(origWidth, origHeight), true)
                val bmp = decoder.decodeRegion(
                    Rect(raw.left, raw.top, raw.right, raw.bottom), opts,
                ) ?: return@runCatching null
                exif.applyToTileImage(BitmapTileImage(bmp)).bitmap
            } else {
                val left = region.left.coerceIn(0, origWidth)
                val top = region.top.coerceIn(0, origHeight)
                val right = region.right.coerceIn(left, origWidth)
                val bottom = region.bottom.coerceIn(top, origHeight)
                decoder.decodeRegion(Rect(left, top, right, bottom), opts)
            }
        }.getOrNull()

        private fun decodeRegionSoftware(region: IntRectCompat, sampleSize: Int): Bitmap {
            val full = ensureFullBitmap()
                ?: throw IllegalStateException("Unable to decode HEIF image for subsampling")

            // Map the requested region (original image coords) into the capped bitmap.
            val sx = if (origWidth > 0) full.width.toFloat() / origWidth else 1f
            val sy = if (origHeight > 0) full.height.toFloat() / origHeight else 1f
            val left = (region.left * sx).roundToInt().coerceIn(0, full.width)
            val top = (region.top * sy).roundToInt().coerceIn(0, full.height)
            val right = (region.right * sx).roundToInt().coerceIn(left, full.width)
            val bottom = (region.bottom * sy).roundToInt().coerceIn(top, full.height)
            val width = (right - left).coerceAtLeast(1)
            val height = (bottom - top).coerceAtLeast(1)

            val regionBitmap = Bitmap.createBitmap(full, left, top, width, height)
            if (sampleSize <= 1) return regionBitmap

            val scaledWidth = (width / sampleSize).coerceAtLeast(1)
            val scaledHeight = (height / sampleSize).coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(regionBitmap, scaledWidth, scaledHeight, true)
            if (scaled != regionBitmap) regionBitmap.recycle()
            return scaled
        }

        @Suppress("DEPRECATION")
        private fun newRegionDecoder(data: ByteArray): BitmapRegionDecoder? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                BitmapRegionDecoder.newInstance(data, 0, data.size)
            } else {
                BitmapRegionDecoder.newInstance(data, 0, data.size, false)
            }
    }

    class Factory(private val hdrDisplay: Boolean = true) : RegionDecoder.Factory {

        override suspend fun accept(subsamplingImage: SubsamplingImage): Boolean = true

        override fun checkSupport(mimeType: String): Boolean? {
            val supported = if (mimeType in SUPPORTED_MIMETYPES) true else null
            HeifDebug.d("Factory.checkSupport mime=$mimeType -> $supported")
            return supported
        }

        override suspend fun create(
            subsamplingImage: SubsamplingImage,
            imageSource: ImageSource,
        ): HeifRegionDecoder {
            HeifDebug.d("Factory.create image=$imageSource hdrDisplay=$hdrDisplay")
            return HeifRegionDecoder(
                subsamplingImage = subsamplingImage,
                imageSource = imageSource,
                hdrDisplay = hdrDisplay,
            )
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false
            return hdrDisplay == (other as Factory).hdrDisplay
        }

        override fun hashCode(): Int = this::class.hashCode() * 31 + hdrDisplay.hashCode()

        override fun toString(): String = "HeifRegionDecoder"
    }

    companion object {
        const val HEIF_MIMETYPE = "image/heif"

        /** Long-edge cap for the software full-decode fallback, bounding worst-case RAM. */
        const val MAX_FALLBACK_DIM = 6144

        /**
         * Above this many pixels, prefer the memory-bounded native tiler over the hardware
         * BitmapRegionDecoder (which can internally full-decode a non-grid frame). ~32MP.
         */
        const val NATIVE_PIXEL_THRESHOLD = 32_000_000L

        /**
         * Long-edge cap for the one-time gain-map probe decode. The gain map is low-frequency, so a
         * downscaled decode is plenty for per-tile HDR while keeping the transient base decode's
         * peak RAM bounded.
         */
        const val GAINMAP_DECODE_MAX_EDGE = 2048

        private val SUPPORTED_MIMETYPES = setOf(
            "image/heif",
            "image/heic",
            "image/heif-sequence",
            "image/heic-sequence",
            "image/avif",
            "image/avis",
        )
    }
}
