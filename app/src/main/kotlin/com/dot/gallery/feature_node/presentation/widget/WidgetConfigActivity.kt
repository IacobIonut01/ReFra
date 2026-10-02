/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */
package com.dot.gallery.feature_node.presentation.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import com.dot.gallery.R
import com.dot.gallery.core.Constants
import com.dot.gallery.core.DefaultEventHandler
import com.dot.gallery.core.MediaDistributor
import com.dot.gallery.core.MediaHandler
import com.dot.gallery.core.MediaSelector
import com.dot.gallery.core.MediaSelectorImpl
import com.dot.gallery.core.util.SetupMediaProviders
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.UIEvent
import com.dot.gallery.feature_node.domain.util.EventHandler
import com.dot.gallery.feature_node.domain.util.getUri
import com.dot.gallery.feature_node.domain.util.isCloud
import com.dot.gallery.feature_node.domain.util.isLocalContent
import com.dot.gallery.feature_node.presentation.picker.AllowedMedia
import com.dot.gallery.feature_node.presentation.picker.components.PickerScreen
import com.dot.gallery.feature_node.presentation.widget.data.WidgetBitmapLoader
import com.dot.gallery.feature_node.presentation.widget.data.WidgetData
import com.dot.gallery.feature_node.presentation.widget.data.WidgetDeepLink
import com.dot.gallery.feature_node.presentation.widget.data.WidgetDisplayStyle
import com.dot.gallery.feature_node.presentation.widget.data.WidgetPreferences
import com.dot.gallery.feature_node.presentation.widget.data.WidgetType
import com.dot.gallery.ui.theme.GalleryTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class WidgetConfigActivity : FragmentActivity() {

    private val eventHandler: EventHandler = DefaultEventHandler()

    @Inject
    lateinit var mediaDistributor: MediaDistributor

    @Inject
    lateinit var mediaHandler: MediaHandler

    val mediaSelector: MediaSelector = MediaSelectorImpl()

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var isReconfigure = false

    /** Saved config read at launch — pre-fills the style step on reconfigure. */
    private var existingData: WidgetData? = null

    /** URIs handed back by the picker's first result callback, consumed by the second. */
    private var pendingUris: List<Uri> = emptyList()

    /** Step-1 pick awaiting the style step; non-null swaps in [WidgetStyleScreen]. */
    private var pendingSelection by mutableStateOf<Pair<List<Uri>, List<Media>>?>(null)

    private val widgetType: WidgetType by lazy {
        // Determine widget type from the provider info
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val info = appWidgetManager.getAppWidgetInfo(appWidgetId)
        if (info?.provider?.className?.contains("GridMediaWidgetReceiver") == true) {
            WidgetType.GRID
        } else {
            WidgetType.SINGLE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Get the widget ID from the intent
        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        // Detect reconfigure: widget already has saved data
        existingData = WidgetPreferences.getWidgetData(this, appWidgetId)
        isReconfigure = existingData != null

        // For initial config, set CANCELED so backing out doesn't add the widget.
        // For reconfigure, the widget already exists — just finish normally on back.
        if (!isReconfigure) {
            setResult(Activity.RESULT_CANCELED)
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        enableEdgeToEdge()

        val allowMultiple = widgetType == WidgetType.GRID
        val title = if (allowMultiple) {
            getString(R.string.widget_select_photos)
        } else {
            getString(R.string.widget_select_photo)
        }

        setContent {
            LaunchedEffect(eventHandler) {
                lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    val navigateUpAction: () -> Unit = { finish() }
                    eventHandler.navigateUpAction = navigateUpAction
                    try {
                        eventHandler.updaterFlow.collect { event ->
                            when (event) {
                                UIEvent.UpdateDatabase -> Unit
                                UIEvent.NavigationUpEvent -> eventHandler.navigateUpAction()
                                is UIEvent.NavigationRouteEvent -> eventHandler.navigateAction(event.route)
                                is UIEvent.ToggleNavigationBarEvent ->
                                    eventHandler.toggleNavigationBarAction(event.isVisible)
                                is UIEvent.SetFollowThemeEvent ->
                                    eventHandler.setFollowThemeAction(event.followTheme)
                            }
                        }
                    } finally {
                        eventHandler.navigateUpAction = {}
                    }
                }
            }
            SetupMediaProviders(
                eventHandler = eventHandler,
                mediaDistributor = mediaDistributor,
                mediaHandler = mediaHandler,
                mediaSelector = mediaSelector
            ) {
                GalleryTheme {
                    val selection = pendingSelection
                    if (selection == null) {
                        WidgetConfigScreen(
                            title = title,
                            allowMultiple = allowMultiple
                        )
                    } else {
                        WidgetStyleScreen(
                            previewUris = selection.first,
                            widgetType = widgetType,
                            initialStyle = existingData?.displayStyle
                                ?: WidgetDisplayStyle.IMAGE,
                            initialIcon = existingData?.icon,
                            isReconfigure = isReconfigure,
                            onBack = { pendingSelection = null },
                            onDone = { style, icon ->
                                finishConfig(selection.first, selection.second, style, icon)
                            }
                        )
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalPermissionsApi::class)
    @Composable
    private fun WidgetConfigScreen(
        title: String,
        allowMultiple: Boolean
    ) {
        val mediaPermissions = rememberMultiplePermissionsState(Constants.PERMISSIONS)
        if (!mediaPermissions.allPermissionsGranted) {
            LaunchedEffect(Unit) {
                mediaPermissions.launchMultiplePermissionRequest()
            }
        }
        PickerScreen(
            title = title,
            allowedMedia = AllowedMedia.PHOTOS,
            allowSelection = allowMultiple,
            onClose = ::finish,
            sendMediaAsResult = { uris -> pendingUris = uris },
            sendMediaAsMediaResult = { media -> onMediaSelected(pendingUris, media) }
        )
    }

    private fun onMediaSelected(selectedMedia: List<Uri>, selectedItems: List<Media>) {
        if (selectedMedia.isEmpty()) {
            finish()
            return
        }
        // Step 2 of the flow: hold the pick and let WidgetStyleScreen choose how
        // the widget renders it (colour / black & white / icon stand-in).
        pendingSelection = selectedMedia to selectedItems
    }

    private fun finishConfig(
        selectedMedia: List<Uri>,
        selectedItems: List<Media>,
        style: WidgetDisplayStyle,
        icon: String?,
    ) {
        // The media viewer deep link needs the timeline id of each picked item.
        // Vault and private-folder picks are excluded — they are absent from the
        // unified timeline and a vault id is its original MediaStore row id.
        val selected = selectedItems.map {
            Triple(it.getUri().toString(), it.id, it.isLocalContent || it.isCloud)
        }
        val mediaIds = WidgetDeepLink.pairDeepLinkIds(
            materialized = selectedMedia.map(Uri::toString),
            selected = selected
        )

        // Persist read permission for the selected URIs
        selectedMedia.forEach { uri ->
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Some URIs may not support persistable permissions
            }
        }

        // Clear old cache on reconfigure
        if (isReconfigure) {
            WidgetBitmapLoader.clearCache(this, appWidgetId)
        }

        WidgetPreferences.saveWidgetData(
            context = this,
            widgetId = appWidgetId,
            type = widgetType,
            uris = selectedMedia,
            mediaIds = mediaIds,
            displayStyle = style,
            icon = icon
        )

        // Load bitmaps, cache to files, and push widget update
        val appContext = applicationContext
        val wId = appWidgetId
        val wType = widgetType
        val uris = selectedMedia
        lifecycleScope.launch {
            uris.forEachIndexed { index, uri ->
                WidgetBitmapLoader.loadAndCacheBitmap(appContext, uri, wId, index)
            }
            // Push RemoteViews via the provider's updateWidget
            val awm = AppWidgetManager.getInstance(appContext)
            when (wType) {
                WidgetType.SINGLE -> SingleMediaWidgetReceiver.updateWidget(appContext, awm, wId)
                WidgetType.GRID -> GridMediaWidgetReceiver.updateWidget(appContext, awm, wId)
            }

            val resultValue = Intent().apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, wId)
            }
            setResult(Activity.RESULT_OK, resultValue)
            finish()
        }
    }

}
