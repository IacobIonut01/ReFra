package com.dot.gallery.core.decoder.glide

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.ParcelFileDescriptor
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.ResourceDecoder
import com.bumptech.glide.load.engine.Resource
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapDrawableResource
import com.bumptech.glide.load.resource.drawable.DrawableResource
import com.bumptech.glide.util.Util
import com.radzivon.bartoshyk.avif.coder.HeifCoder
import com.radzivon.bartoshyk.avif.coder.PreferredColorConfig
import java.io.FileInputStream
import java.io.InputStream

/**
 * #1244: software decoders for >10-bit HEIF/AVIF on the Drawable-producing resource paths.
 *
 * `decode(Drawable)` requests (what the grid uses for `.avif`) never consult `Bitmap` resource
 * decoders — Glide picks a `* -> Drawable`/`BitmapDrawable` decode path first, whose built-in
 * platform decoder corrupts >10-bit streams. These depth-gated twins claim the same bytes the
 * platform can't handle and decode through HeifCoder; <=10-bit streams decline and keep the
 * platform path.
 */
private fun HeifCoder.decodeDeepHeif(source: ByteArray, width: Int, height: Int): Bitmap? {
    val size = getSize(source) ?: return null
    val targetW = if (width > 0) width else size.width
    val targetH = if (height > 0) height else size.height
    return decodeSampled(source, targetW, targetH, PreferredColorConfig.RGBA_8888)
}

private fun ParcelFileDescriptor.readAllBytes(): ByteArray? = try {
    FileInputStream(fileDescriptor).use { it.readBytes() }
} catch (_: Exception) {
    null
}

/** [Resource] over a plain [Drawable] decoded outside the bitmap pool (bytes already consumed). */
private class HeifDrawableResource(
    drawable: Drawable,
    private val byteCount: Int
) : DrawableResource<Drawable>(drawable) {
    override fun getResourceClass(): Class<Drawable> = Drawable::class.java
    override fun getSize(): Int = byteCount
    override fun recycle() {}
}

class HeifDrawableDecoder(
    context: Context
) : ResourceDecoder<InputStream, Drawable> {

    private val resources = context.applicationContext.resources
    private val coder = HeifCoder()

    override fun handles(source: InputStream, options: Options): Boolean =
        source.sniffsDeepHeif()

    override fun decode(
        source: InputStream,
        width: Int,
        height: Int,
        options: Options
    ): Resource<Drawable>? = coder
        .decodeDeepHeif(source.readBytes(), width, height)
        ?.let { HeifDrawableResource(BitmapDrawable(resources, it), Util.getBitmapByteSize(it)) }
}

class HeifBitmapDrawableDecoder(
    context: Context,
    private val bitmapPool: BitmapPool
) : ResourceDecoder<InputStream, BitmapDrawable> {

    private val resources = context.applicationContext.resources
    private val coder = HeifCoder()

    override fun handles(source: InputStream, options: Options): Boolean =
        source.sniffsDeepHeif()

    override fun decode(
        source: InputStream,
        width: Int,
        height: Int,
        options: Options
    ): Resource<BitmapDrawable>? = coder
        .decodeDeepHeif(source.readBytes(), width, height)
        ?.let { BitmapDrawableResource(BitmapDrawable(resources, it), bitmapPool) }
}

class HeifPfdDrawableDecoder(
    context: Context
) : ResourceDecoder<ParcelFileDescriptor, Drawable> {

    private val resources = context.applicationContext.resources
    private val coder = HeifCoder()

    override fun handles(source: ParcelFileDescriptor, options: Options): Boolean =
        source.sniffsDeepHeif()

    override fun decode(
        source: ParcelFileDescriptor,
        width: Int,
        height: Int,
        options: Options
    ): Resource<Drawable>? = source.readAllBytes()
        ?.let { coder.decodeDeepHeif(it, width, height) }
        ?.let { HeifDrawableResource(BitmapDrawable(resources, it), Util.getBitmapByteSize(it)) }
}

class HeifPfdBitmapDrawableDecoder(
    context: Context,
    private val bitmapPool: BitmapPool
) : ResourceDecoder<ParcelFileDescriptor, BitmapDrawable> {

    private val resources = context.applicationContext.resources
    private val coder = HeifCoder()

    override fun handles(source: ParcelFileDescriptor, options: Options): Boolean =
        source.sniffsDeepHeif()

    override fun decode(
        source: ParcelFileDescriptor,
        width: Int,
        height: Int,
        options: Options
    ): Resource<BitmapDrawable>? = source.readAllBytes()
        ?.let { coder.decodeDeepHeif(it, width, height) }
        ?.let { BitmapDrawableResource(BitmapDrawable(resources, it), bitmapPool) }
}
