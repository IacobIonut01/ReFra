/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */
package com.dot.gallery.feature_node.presentation.widget.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import android.util.Size
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.dot.gallery.feature_node.presentation.util.printError
import com.dot.gallery.feature_node.presentation.util.printWarn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object WidgetBitmapLoader {

    private const val CACHE_DIR = "widget_cache"

    /**
     * Loads a bitmap from [uri] using Glide (with all registered decoders) and
     * caches it as a JPEG file for the given [widgetId]/[index].
     * Returns true if the bitmap was successfully cached.
     */
    suspend fun loadAndCacheBitmap(
        context: Context,
        uri: Uri,
        widgetId: Int,
        index: Int,
        maxWidth: Int = 1024,
        maxHeight: Int = 1024
    ): Boolean = withContext(Dispatchers.IO) {
        val bitmap = loadBitmap(context, uri, maxWidth, maxHeight)
        if (bitmap != null) {
            saveBitmapToFile(context, bitmap, widgetId, index)
            true
        } else {
            false
        }
    }

    /**
     * Reads a previously cached bitmap from file. This is a synchronous call
     * safe to use from AppWidgetProvider.onUpdate. When [grayscale] is set the
     * decoded bitmap is returned desaturated — the cached file stays in colour
     * so a style change never forces a re-decode.
     */
    fun loadCachedBitmap(
        context: Context,
        widgetId: Int,
        index: Int,
        grayscale: Boolean = false
    ): Bitmap? {
        val file = getBitmapFile(context, widgetId, index)
        if (!file.exists()) return null
        val bitmap = try {
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (e: Exception) {
            printWarn("app.widget", "cached widget bitmap decode failed", ctx = mapOf("file" to file.name))
            null
        } ?: return null
        return if (grayscale) bitmap.toGrayscale() else bitmap
    }

    /** Saturation-0 copy for [WidgetDisplayStyle.GRAYSCALE]. */
    private fun Bitmap.toGrayscale(): Bitmap {
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        }
        Canvas(result).drawBitmap(this, 0f, 0f, paint)
        return result
    }

    fun clearCache(context: Context, widgetId: Int) {
        val dir = getCacheDir(context)
        dir.listFiles()?.filter { it.name.startsWith("widget_${widgetId}_") }?.forEach { it.delete() }
    }

    private suspend fun loadBitmap(
        context: Context,
        uri: Uri,
        maxWidth: Int,
        maxHeight: Int
    ): Bitmap? = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext

        // Try Glide first (handles HEIF, JXL, RAW, video thumbnails, GIF, etc.)
        try {
            val bitmap = Glide.with(appContext)
                .asBitmap()
                .load(uri)
                .centerCrop()
                .override(maxWidth, maxHeight)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .submit()
                .get()
            return@withContext bitmap
        } catch (_: Exception) {
        }

        // Fallback: ContentResolver.loadThumbnail (API 29+)
        try {
            val bitmap = appContext.contentResolver.loadThumbnail(
                uri, Size(maxWidth, maxHeight), null
            )
            return@withContext bitmap
        } catch (e: Exception) {
            printError("app.widget", "widget bitmap load failed", e)
        }

        null
    }

    private fun saveBitmapToFile(context: Context, bitmap: Bitmap, widgetId: Int, index: Int) {
        val file = getBitmapFile(context, widgetId, index)
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    }

    private fun getBitmapFile(context: Context, widgetId: Int, index: Int): File {
        return File(getCacheDir(context), "widget_${widgetId}_$index.jpg")
    }

    private fun getCacheDir(context: Context): File {
        return File(context.applicationContext.filesDir, CACHE_DIR)
    }
}
