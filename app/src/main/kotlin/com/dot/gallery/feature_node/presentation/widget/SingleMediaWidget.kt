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
import android.view.View
import android.widget.RemoteViews
import com.dot.gallery.R
import com.dot.gallery.feature_node.presentation.main.MainActivity
import com.dot.gallery.feature_node.presentation.widget.data.WidgetBitmapLoader
import com.dot.gallery.feature_node.presentation.widget.data.WidgetDeepLink
import com.dot.gallery.feature_node.presentation.widget.data.WidgetPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SingleMediaWidgetReceiver : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Render immediately from cache so the widget is never blank while reloading.
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }

        // Self-heal: if a cached bitmap is missing (e.g. after an app update, reboot
        // or the system clearing app cache) but the URIs are still persisted,
        // reload and re-cache the bitmap, then push the update again.
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val awm = AppWidgetManager.getInstance(appContext)
                for (appWidgetId in appWidgetIds) {
                    if (WidgetBitmapLoader.loadCachedBitmap(appContext, appWidgetId, 0) != null) continue
                    val uris = WidgetPreferences.getMediaUris(appContext, appWidgetId)
                    val uri = uris.firstOrNull() ?: continue
                    val cached = WidgetBitmapLoader.loadAndCacheBitmap(appContext, uri, appWidgetId, 0)
                    if (cached) {
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
        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val bitmap = WidgetBitmapLoader.loadCachedBitmap(context, appWidgetId, 0)
            val views = RemoteViews(context.packageName, R.layout.widget_single_content)

            if (bitmap != null) {
                views.setImageViewBitmap(R.id.widget_image, bitmap)
                views.setViewVisibility(R.id.widget_image, View.VISIBLE)
                views.setViewVisibility(R.id.widget_no_photo_text, View.GONE)
            } else {
                views.setViewVisibility(R.id.widget_image, View.GONE)
                views.setViewVisibility(R.id.widget_no_photo_text, View.VISIBLE)
            }

            // Tap opens the displayed photo in the viewer; without a resolvable
            // media id (empty widget, vault/private pick, pre-fix widget) it
            // falls back to a plain app open.
            val mediaId = WidgetDeepLink.resolveDeepLinkId(
                WidgetPreferences.getWidgetData(context, appWidgetId), 0
            )
            val intent = Intent(context, MainActivity::class.java).apply {
                if (mediaId != null) {
                    putExtra(WidgetDeepLink.EXTRA_WIDGET_MEDIA_ID, mediaId)
                }
            }
            // FLAG_MUTABLE so a reconfigured widget's PendingIntent picks up the
            // new media id instead of keeping the extras frozen at creation.
            val pendingIntent = PendingIntent.getActivity(
                context, appWidgetId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
