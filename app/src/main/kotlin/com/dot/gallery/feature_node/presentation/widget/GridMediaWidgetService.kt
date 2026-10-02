/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */
package com.dot.gallery.feature_node.presentation.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.dot.gallery.R
import android.view.View
import com.dot.gallery.feature_node.presentation.widget.data.WidgetBitmapLoader
import com.dot.gallery.feature_node.presentation.widget.data.WidgetData
import com.dot.gallery.feature_node.presentation.widget.data.WidgetDeepLink
import com.dot.gallery.feature_node.presentation.widget.data.WidgetDisplayStyle
import com.dot.gallery.feature_node.presentation.widget.data.WidgetPreferences

/**
 * Collection adapter for the grid media widget. Each cell is its own
 * RemoteViews item carrying a fill-in intent with the picked photo's media id,
 * so a tap opens THAT photo instead of just the app (#1268).
 */
class GridMediaWidgetService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        return GridMediaWidgetFactory(applicationContext, appWidgetId)
    }
}

private class GridMediaWidgetFactory(
    private val context: Context,
    private val appWidgetId: Int,
) : RemoteViewsService.RemoteViewsFactory {

    private var data: WidgetData? = null

    override fun onCreate() = Unit

    // Runs on a binder thread — reloading prefs and decoding the cached
    // bitmaps from disk is safe here.
    override fun onDataSetChanged() {
        data = WidgetPreferences.getWidgetData(context, appWidgetId)
    }

    override fun onDestroy() {
        data = null
    }

    override fun getCount(): Int = data?.mediaUris?.size ?: 0

    override fun getViewAt(position: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_grid_item)
        // Icon mode draws the stored emoji/label in every cell instead of the
        // photos; a blank icon falls back to the photo.
        val iconText = data?.icon?.takeIf {
            data?.displayStyle == WidgetDisplayStyle.ICON && it.isNotBlank()
        }
        if (iconText != null) {
            views.setTextViewText(R.id.widget_grid_icon, iconText)
            views.setViewVisibility(R.id.widget_grid_icon, View.VISIBLE)
            views.setViewVisibility(R.id.widget_grid_image, View.GONE)
        } else {
            views.setViewVisibility(R.id.widget_grid_icon, View.GONE)
            views.setViewVisibility(R.id.widget_grid_image, View.VISIBLE)
            val bitmap = WidgetBitmapLoader.loadCachedBitmap(
                context, appWidgetId, position,
                grayscale = data?.displayStyle == WidgetDisplayStyle.GRAYSCALE
            )
            if (bitmap != null) {
                views.setImageViewBitmap(R.id.widget_grid_image, bitmap)
            } else {
                views.setImageViewResource(R.id.widget_grid_image, R.drawable.widget_preview_bg)
            }
        }
        val fillIn = Intent()
        WidgetDeepLink.resolveDeepLinkId(data, position)?.let { mediaId ->
            fillIn.putExtra(WidgetDeepLink.EXTRA_WIDGET_MEDIA_ID, mediaId)
        }
        // On the item root so the tap lands whether the cell shows the photo
        // or its icon stand-in.
        views.setOnClickFillInIntent(R.id.widget_grid_item_root, fillIn)
        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = true
}
