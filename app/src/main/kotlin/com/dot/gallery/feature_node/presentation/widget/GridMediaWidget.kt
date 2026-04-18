/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */
package com.dot.gallery.feature_node.presentation.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import com.dot.gallery.R
import com.dot.gallery.feature_node.presentation.main.MainActivity
import com.dot.gallery.feature_node.presentation.widget.data.WidgetBitmapLoader
import com.dot.gallery.feature_node.presentation.widget.data.WidgetPreferences

class GridMediaWidgetReceiver : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        appWidgetIds.forEach { id ->
            WidgetPreferences.deleteWidgetData(context, id)
            WidgetBitmapLoader.clearCache(context, id)
        }
    }

    companion object {
        private const val GRID_SPACING = 2

        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val uris = WidgetPreferences.getMediaUris(context, appWidgetId)
            val bitmaps = uris.indices.mapNotNull { index ->
                WidgetBitmapLoader.loadCachedBitmap(context, appWidgetId, index)
            }
            val views = RemoteViews(context.packageName, R.layout.widget_single_content)

            if (bitmaps.isNotEmpty()) {
                val gridBitmap = createGridBitmap(bitmaps)
                views.setImageViewBitmap(R.id.widget_image, gridBitmap)
                views.setViewVisibility(R.id.widget_image, View.VISIBLE)
                views.setViewVisibility(R.id.widget_no_photo_text, View.GONE)
            } else {
                views.setViewVisibility(R.id.widget_image, View.GONE)
                views.setViewVisibility(R.id.widget_no_photo_text, View.VISIBLE)
            }

            // Set click to open app
            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context, appWidgetId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun createGridBitmap(bitmaps: List<Bitmap>): Bitmap {
            val cols = when {
                bitmaps.size <= 1 -> 1
                bitmaps.size <= 4 -> 2
                else -> 3
            }
            val rows = (bitmaps.size + cols - 1) / cols
            val cellSize = 1024 / cols
            val totalWidth = cols * cellSize + (cols - 1) * GRID_SPACING
            val totalHeight = rows * cellSize + (rows - 1) * GRID_SPACING

            val result = Bitmap.createBitmap(totalWidth, totalHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)
            canvas.drawColor(Color.DKGRAY)

            bitmaps.forEachIndexed { index, bitmap ->
                val row = index / cols
                val col = index % cols
                val x = col * (cellSize + GRID_SPACING)
                val y = row * (cellSize + GRID_SPACING)

                val scaled = Bitmap.createScaledBitmap(bitmap, cellSize, cellSize, true)
                canvas.drawBitmap(scaled, x.toFloat(), y.toFloat(), null)
                if (scaled !== bitmap) scaled.recycle()
            }

            return result
        }
    }
}
