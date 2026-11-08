/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */
package com.dot.gallery.app.presentation.picker

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.dot.gallery.injection.ComponentOwner
import com.dot.gallery.domain.model.AllowedMedia
import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.core.view.WindowCompat
import com.dot.gallery.core.AppConstants
import com.dot.gallery.core.DefaultEventHandler
import com.dot.gallery.core.MediaDistributor
import com.dot.gallery.core.MediaHandler
import com.dot.gallery.core.MediaSelector
import com.dot.gallery.core.MediaSelectorImpl
import com.dot.gallery.core.util.SetupMediaProviders
import com.dot.gallery.domain.model.Media
import com.dot.gallery.domain.model.UIEvent
import com.dot.gallery.domain.util.EventHandler
import com.dot.gallery.app.presentation.picker.components.PickerScreen
import com.dot.gallery.ui.theme.GalleryTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import com.dot.gallery.app.presentation.main.MainActivity
import com.dot.gallery.ui.theme.AppThemeLocals
import com.dot.gallery.core.presentation.util.add
import com.dot.gallery.core.presentation.util.selectedMedia
import com.dot.gallery.core.ui.settings.LocalSettingsStore
import com.dot.gallery.domain.settings.DataStoreSettingsStore
import com.dot.gallery.core.activeDataStore
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.photo
import com.dot.gallery.generated.resources.photos
import com.dot.gallery.generated.resources.photos_and_videos
import com.dot.gallery.generated.resources.select
import com.dot.gallery.generated.resources.video
import com.dot.gallery.generated.resources.videos
import com.dot.gallery.core.util.getString


/**
 * Launches the in-app picker. Pass a vault uuid string as the launch input to hide that vault
 * from the picker, so adding to a vault can't pick from (and re-encrypt into) itself.
 */
class PickerActivityContract(
    private val mediaType: String = "*/*",
    private val allowMultiple: Boolean = true
) : ActivityResultContract<Any?, List<String>>() {

    override fun createIntent(context: Context, input: Any?): Intent {
        return Intent(context, PickerActivity::class.java).apply {
            type = mediaType
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, allowMultiple)
            (input as? String)?.let { putExtra(PickerActivity.EXCLUDED_VAULT_UUID, it) }
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): List<String> {
        if (resultCode != Activity.RESULT_OK || intent == null) {
            return emptyList()
        }


        return mutableListOf<String>().apply {
            intent.clipData?.let { clipData ->
                for (i in 0 until clipData.itemCount) {
                    add(clipData.getItemAt(i).uri.toString())
                }
            } ?: intent.data?.let { add(it.toString()) }
        }
    }
}

class PickerActivity : FragmentActivity() {

    private val component get() = (application as ComponentOwner).component

    // Use a picker-local EventHandler instead of the process-wide @Singleton so the picker never
    // clobbers MainActivity's navigateUpAction (which would break back navigation after the picker
    // closes while the gallery is open). Mirrors the private mediaSelector below.
    private val eventHandler: EventHandler = DefaultEventHandler()

    val mediaDistributor: MediaDistributor get() = component.mediaDistributor
    val mediaHandler: MediaHandler get() = component.mediaHandler

    val mediaSelector: MediaSelector = MediaSelectorImpl()

    private val exportAsMedia by lazy {
        intent.getBooleanExtra(EXPORT_AS_MEDIA, false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        enableEdgeToEdge()
        val type = intent.type
        val allowMultiple = intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
        var title = getString(Res.string.select)
        title += " " + if (allowMultiple) {
            if (type.pickAny) getString(Res.string.photos_and_videos)
            else if (type.pickImage) getString(Res.string.photos)
            else getString(Res.string.videos)
        } else {
            if (type.pickImage) getString(Res.string.photo)
            else if (type.pickVideo) getString(Res.string.video)
            else getString(Res.string.photos_and_videos)
        }
        setContent {
            LaunchedEffect(Unit) {
                eventHandler.navigateUpAction = { finish() }
            }
            LaunchedEffect(eventHandler) {
                withContext(Dispatchers.Main.immediate) {
                    eventHandler.updaterFlow.collectLatest { event ->
                        when (event) {
                            UIEvent.UpdateDatabase -> {
                            }

                            UIEvent.NavigationUpEvent -> eventHandler.navigateUpAction()
                            is UIEvent.NavigationRouteEvent -> eventHandler.navigateAction(event.route)
                            is UIEvent.ToggleNavigationBarEvent -> eventHandler.toggleNavigationBarAction(
                                event.isVisible
                            )

                            is UIEvent.SetFollowThemeEvent -> eventHandler.setFollowThemeAction(
                                event.followTheme
                            )
                        }
                    }
                }
            }
            SetupMediaProviders(
                eventHandler = eventHandler,
                mediaDistributor = mediaDistributor,
                mediaHandler = mediaHandler,
                mediaSelector = mediaSelector
            ) {
                val settingsStore = remember { DataStoreSettingsStore(activeDataStore) }
                CompositionLocalProvider(LocalSettingsStore provides settingsStore) {
                AppThemeLocals {
                    GalleryTheme {
                    PickerRootScreen(title, type.allowedMedia, allowMultiple)
                }
                }
                }
            }
        }
    }

    @OptIn(ExperimentalPermissionsApi::class)
    @Composable
    fun PickerRootScreen(title: String, allowedMedia: AllowedMedia, allowMultiple: Boolean) {
        val mediaPermissions =
            rememberMultiplePermissionsState(AppConstants.PERMISSIONS)
        if (!mediaPermissions.allPermissionsGranted) {
            LaunchedEffect(Unit) {
                mediaPermissions.launchMultiplePermissionRequest()
            }
        }
        PickerScreen(
            title = title,
            allowedMedia = allowedMedia,
            allowSelection = allowMultiple,
            excludedVaultUuid = intent.getStringExtra(EXCLUDED_VAULT_UUID),
            onClose = ::finish,
            sendMediaAsResult = ::sendMediaAsResult,
            sendMediaAsMediaResult = ::sendMediaAsMediaResult
        )
    }

    private fun sendMediaAsMediaResult(selectedMedia: List<Media>) {
        if (exportAsMedia) {
            val newIntent = Intent().apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                putExtra(MEDIA_LIST, Json.encodeToString(selectedMedia.toTypedArray()))
            }
            setResult(RESULT_OK, newIntent)
            finish()
        }
    }

    private fun sendMediaAsResult(selectedMedia: List<Uri>) {
        if (!exportAsMedia) {
            val newIntent = Intent().apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                data = selectedMedia[0]
            }
            if (selectedMedia.size == 1)
                setResult(RESULT_OK, newIntent)
            else {
                val newClipData = ClipData.newUri(contentResolver, null, selectedMedia[0])
                for (nextUri in selectedMedia.stream().skip(1)) {
                    newClipData.addItem(contentResolver, ClipData.Item(nextUri))
                }
                newIntent.clipData = newClipData
                setResult(RESULT_OK, newIntent)
            }
            finish()
        }
    }

    private val String?.pickImage: Boolean get() = this?.startsWith("image") == true
    private val String?.pickVideo: Boolean get() = this?.startsWith("video") == true
    private val String?.pickAny: Boolean get() = this == "*/*"
    private val String?.allowedMedia: AllowedMedia
        get() = if (pickImage) AllowedMedia.PHOTOS
        else if (pickVideo) AllowedMedia.VIDEOS
        else AllowedMedia.BOTH

    companion object {
        const val EXPORT_AS_MEDIA = "EXPORT_AS_MEDIA"
        const val MEDIA_LIST = "MEDIA_LIST"
        const val EXCLUDED_VAULT_UUID = "EXCLUDED_VAULT_UUID"
    }
}