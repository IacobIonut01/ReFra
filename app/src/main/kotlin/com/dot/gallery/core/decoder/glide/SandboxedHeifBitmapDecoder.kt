/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.decoder.glide

import android.content.Context
import android.graphics.Bitmap
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.ResourceDecoder
import com.bumptech.glide.load.engine.Resource
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapResource
import com.dot.gallery.core.sandbox.SandboxedDecoderHolder
import kotlinx.coroutines.runBlocking
import java.io.InputStream

/**
 * Glide decoder for HEIF/AVIF [InputStream] that delegates to the isolated decoder
 * when sandboxed decoding is enabled. Falls through to the standard decoder when disabled.
 */
class SandboxedHeifBitmapDecoder(
    private val context: Context,
    private val bitmapPool: BitmapPool
) : ResourceDecoder<InputStream, Bitmap> {

    override fun handles(source: InputStream, options: Options): Boolean {
        if (!SandboxedDecoderHolder.isEnabled(context)) return false
        source.mark(128)
        val header = ByteArray(128)
        val read = source.read(header)
        source.reset()
        if (read < 12) return false
        return HeifSniffer.findBrand(header, read) != null
    }

    override fun decode(
        source: InputStream,
        width: Int,
        height: Int,
        options: Options
    ): Resource<Bitmap>? {
        val decoder = SandboxedDecoderHolder.decoder ?: return null
        val bytes = source.readBytes()
        val bitmap = runBlocking {
            decoder.decode(bytes, "image/heif", width, height)
        } ?: return null
        return BitmapResource.obtain(bitmap, bitmapPool)
    }
}

/**
 * Glide decoder for HEIF/AVIF [MimeInputStream] that delegates to the isolated decoder
 * when sandboxed decoding is enabled. This handles the MIME-based path
 * (content URIs via [MimeInputStreamModelLoader]), which is the primary HEIF decode path.
 */
class SandboxedHeifMimeDecoder(
    private val context: Context,
    private val bitmapPool: BitmapPool
) : ResourceDecoder<MimeInputStream, Bitmap> {

    override fun handles(source: MimeInputStream, options: Options): Boolean {
        if (!SandboxedDecoderHolder.isEnabled(context)) return false
        return HeifMime.isHeifMime(source.mimeType)
    }

    override fun decode(
        source: MimeInputStream,
        width: Int,
        height: Int,
        options: Options
    ): Resource<Bitmap>? {
        val decoder = SandboxedDecoderHolder.decoder ?: return null
        val bytes = source.inputStream.readBytes()
        val mime = source.mimeType ?: "image/heif"
        val bitmap = runBlocking {
            decoder.decode(bytes, mime, width, height)
        } ?: return null
        return BitmapResource.obtain(bitmap, bitmapPool)
    }
}
