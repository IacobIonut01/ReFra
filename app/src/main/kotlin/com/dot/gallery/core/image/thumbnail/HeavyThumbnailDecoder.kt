/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.image.thumbnail

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.awxkee.jxlcoder.JxlCoder
import com.dot.gallery.core.decoder.NativeRawDecoder
import com.dot.gallery.core.decoder.format.HeifDecodeEngine
import com.dot.gallery.core.decoder.format.Jp2ImageDecoder
import com.dot.gallery.core.decoder.format.PsdImageDecoder
import com.dot.gallery.core.decoder.format.TiffImageDecoder
import com.dot.gallery.core.sandbox.SandboxedDecoderHolder
import com.dot.gallery.feature_node.presentation.util.printWarn
import kotlinx.coroutines.runBlocking

/**
 * #1276: canonical-size decode dispatch for [HeavyThumbFormat]s, feeding [LocalThumbnailStore].
 * Runs on Glide's source executor (a background thread). All decoders are synchronous; JXL and
 * >10-bit HEIF bridge to the isolated decoder service via runBlocking when the user's sandboxed
 * decode preference is enabled — the same pattern as `SandboxedJxlBitmapDecoder`.
 *
 * Every failure path returns null so the Glide fetcher can fail the request and let the normal
 * pipeline (per-mime decoders) take over — the store is a cache, not a replacement.
 */
internal object HeavyThumbnailDecoder {

    private const val TAG = "decode.thumb"

    /**
     * Long edge of the single stored thumbnail per file: serves the 256 px MOTION tier and any
     * refined grid cell (the platform-thumbnail path is already bounded at 512 px).
     */
    const val CANONICAL_PX = 512

    /** Byte-based decoders refuse sources larger than this; the mmap TIFF path is exempt. */
    private const val MAX_SOURCE_BYTES = 160L * 1024 * 1024

    fun decode(
        context: Context,
        uri: Uri,
        kind: HeavyThumbFormat,
        maxPx: Int = CANONICAL_PX
    ): Bitmap? = runCatching {
        when (kind) {
            // mmap-based decode — no full byte copy needed for what can be a 100 MB TIFF.
            HeavyThumbFormat.TIFF -> TiffImageDecoder.decode(context, uri, maxPx, maxPx)
            else -> readSource(context, uri)?.let { decodeBytes(context, it, kind, maxPx) }
        }
    }.onFailure {
        printWarn(TAG, "heavy thumbnail decode failed kind=$kind: ${it.message}")
    }.getOrNull()

    private fun decodeBytes(
        context: Context,
        bytes: ByteArray,
        kind: HeavyThumbFormat,
        maxPx: Int
    ): Bitmap? = when (kind) {
        HeavyThumbFormat.JXL -> decodeJxl(context, bytes, maxPx)
        HeavyThumbFormat.PSD -> PsdImageDecoder.decode(bytes, maxPx, maxPx)
        HeavyThumbFormat.JP2 -> Jp2ImageDecoder.decode(bytes, maxPx, maxPx)
        // Embedded JPEG preview first (fast); LibRaw's decoded thumbnail covers non-TIFF RAW.
        HeavyThumbFormat.RAW -> TiffImageDecoder.decodePreview(bytes, maxPx, maxPx)
            ?: NativeRawDecoder.getThumbnail(bytes)
        HeavyThumbFormat.HEIF_SW -> decodeHeifSoftware(context, bytes, maxPx)
        HeavyThumbFormat.TIFF -> null // unreachable — mmap path above
    }

    private fun decodeJxl(context: Context, bytes: ByteArray, maxPx: Int): Bitmap? {
        val isolated = SandboxedDecoderHolder.decoder
            ?.takeIf { SandboxedDecoderHolder.isEnabled(context) }
        return if (isolated != null) {
            runBlocking { isolated.decode(bytes, "image/jxl", maxPx, maxPx) }
        } else {
            JxlCoder.decodeSampled(bytes, maxPx, maxPx)
        }
    }

    /**
     * Only >10-bit HEIF/AVIF lands here. Sandboxed decode when enabled; otherwise
     * [HeifDecodeEngine.decodeCapped] — the pure software path, since the hardware
     * `ImageDecoder` corrupts these streams (#1244).
     */
    private fun decodeHeifSoftware(context: Context, bytes: ByteArray, maxPx: Int): Bitmap? {
        val isolated = SandboxedDecoderHolder.decoder
            ?.takeIf { SandboxedDecoderHolder.isEnabled(context) }
        return if (isolated != null) {
            runBlocking { isolated.decode(bytes, "image/heif", maxPx, maxPx) }
        } else {
            HeifDecodeEngine.decodeCapped(bytes, maxPx)
        }
    }

    private fun readSource(context: Context, uri: Uri): ByteArray? {
        val resolver = context.contentResolver
        val declared = runCatching {
            resolver.openAssetFileDescriptor(uri, "r")?.use { it.declaredLength }
        }.getOrNull()
        if (declared != null && declared > MAX_SOURCE_BYTES) {
            printWarn(
                TAG,
                "heavy thumbnail skipped: ${declared / 1048576}MB source exceeds ${MAX_SOURCE_BYTES / 1048576}MB cap"
            )
            return null
        }
        return runCatching { resolver.openInputStream(uri)?.use { it.readBytes() } }
            .onFailure { printWarn(TAG, "heavy thumbnail source read failed: ${it.message}") }
            .getOrNull()
    }
}
