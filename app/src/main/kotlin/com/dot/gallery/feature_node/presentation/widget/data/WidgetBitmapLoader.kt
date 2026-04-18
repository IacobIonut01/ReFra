/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */
package com.dot.gallery.feature_node.presentation.widget.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Size
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
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
     * safe to use from AppWidgetProvider.onUpdate.
     */
    fun loadCachedBitmap(context: Context, widgetId: Int, index: Int): Bitmap? {
        val file = getBitmapFile(context, widgetId, index)
        if (!file.exists()) return null
        return try {
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (e: Exception) {
            null
        }
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
                .diskCacheStrategy(DiskCacheStrategy.ALL)
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
        } catch (_: Exception) { }

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
