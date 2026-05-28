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
 * Glide decoder for JXL that delegates to [com.dot.gallery.core.sandbox.IsolatedDecoderService]
 * when sandboxed decoding is enabled. Falls through to the standard [JxlBitmapDecoder]
 * when disabled.
 */
class SandboxedJxlBitmapDecoder(
    private val context: Context,
    private val bitmapPool: BitmapPool
) : ResourceDecoder<InputStream, Bitmap> {

    override fun handles(source: InputStream, options: Options): Boolean {
        if (!SandboxedDecoderHolder.isEnabled(context)) return false
        // Same magic-byte sniffing as JxlBitmapDecoder
        source.mark(12)
        val head = ByteArray(12)
        val read = source.read(head)
        source.reset()
        if (read < 2) return false
        // Raw JPEG XL codestream: 0xFF 0x0A
        if (head[0] == 0xFF.toByte() && head[1] == 0x0A.toByte()) return true
        // ISO-BMFF container
        if (read >= 12 &&
            head[0] == 0x00.toByte() && head[1] == 0x00.toByte() &&
            head[2] == 0x00.toByte() && head[3] == 0x0C.toByte() &&
            head[4] == 0x4A.toByte() && head[5] == 0x58.toByte() &&
            head[6] == 0x4C.toByte() && head[7] == 0x20.toByte() &&
            head[8] == 0x0D.toByte() && head[9] == 0x0A.toByte() &&
            head[10] == 0x87.toByte() && head[11] == 0x0A.toByte()
        ) return true
        return false
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
            decoder.decode(bytes, "image/jxl", width, height)
        } ?: return null
        return BitmapResource.obtain(bitmap, bitmapPool)
    }
}
