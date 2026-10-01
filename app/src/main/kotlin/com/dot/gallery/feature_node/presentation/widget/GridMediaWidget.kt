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
import android.net.Uri
import android.widget.RemoteViews
import com.dot.gallery.R
import com.dot.gallery.feature_node.presentation.main.MainActivity
import com.dot.gallery.feature_node.presentation.widget.data.WidgetBitmapLoader
import com.dot.gallery.feature_node.presentation.widget.data.WidgetPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GridMediaWidgetReceiver : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Rebind the collection immediately so the widget is never blank
        // while reloading.
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }

        // Self-heal: if cached bitmaps are missing (e.g. after an app update, reboot
        // or the system clearing app cache) but the URIs are still persisted,
        // reload and re-cache them, then push the update again.
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val awm = AppWidgetManager.getInstance(appContext)
                for (appWidgetId in appWidgetIds) {
                    val uris = WidgetPreferences.getMediaUris(appContext, appWidgetId)
                    if (uris.isEmpty()) continue
                    var reloaded = false
                    uris.forEachIndexed { index, uri ->
                        if (WidgetBitmapLoader.loadCachedBitmap(appContext, appWidgetId, index) != null) return@forEachIndexed
                        if (WidgetBitmapLoader.loadAndCacheBitmap(appContext, uri, appWidgetId, index)) {
                            reloaded = true
                        }
                    }
                    if (reloaded) {
                        updateWidget(appContext, awm, appWidgetId)
                    }
                }
            } finally {
                pendingResult.finish()
            }
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

        // setRemoteAdapter(Intent) is "deprecated" in favor of eagerly-built
        // RemoteCollectionItems, but that API is API 31+ and this widget's
        // photos are decoded lazily per cell — the service adapter stays.
        @Suppress("DEPRECATION")
        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_grid_content)

            // RemoteViewsService intent identity ignores extras, so a unique
            // data URI per widget id keeps each widget bound to its own
            // factory instance.
            val serviceIntent = Intent(context, GridMediaWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse("refra-widget://grid/$appWidgetId")
            }
            views.setRemoteAdapter(R.id.widget_grid, serviceIntent)
            views.setEmptyView(R.id.widget_grid, R.id.widget_no_photo_text)

            // Per-cell media ids arrive through the fill-in intents the factory
            // sets in getViewAt; the template alone is a plain app open.
            // FLAG_MUTABLE is required — an immutable template silently drops
            // fill-in extras on API 31+, which would lose the tapped media id.
            val templateIntent = Intent(context, MainActivity::class.java)
            val template = PendingIntent.getActivity(
                context, appWidgetId, templateIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            views.setPendingIntentTemplate(R.id.widget_grid, template)

            appWidgetManager.updateAppWidget(appWidgetId, views)
            appWidgetManager.notifyAppWidgetViewDataChanged(
                intArrayOf(appWidgetId),
                R.id.widget_grid
            )
        }
    }
}
