/*
 * SPDX-FileCopyrightText: 2023 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */
package com.dot.gallery.feature_node.presentation.picker

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.dot.gallery.R
import com.dot.gallery.core.Constants
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.presentation.picker.components.PickerScreen
import com.dot.gallery.ui.theme.GalleryTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class PickerActivityContract : ActivityResultContract<Any?, List<Uri>>() {

    override fun createIntent(context: Context, input: Any?): Intent {
        return Intent(context, PickerActivity::class.java).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): List<Uri> {
        if (resultCode != Activity.RESULT_OK || intent == null) {
            return emptyList()
        }


        return mutableListOf<Uri>().apply {
            intent.clipData?.let { clipData ->
                for (i in 0 until clipData.itemCount) {
                    add(clipData.getItemAt(i).uri)
                }
            } ?: intent.data?.let { add(it) }
        }
    }
}

@AndroidEntryPoint
class PickerActivity : ComponentActivity() {

    private val exportAsMedia by lazy {
        intent.getBooleanExtra(EXPORT_AS_MEDIA, false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        enableEdgeToEdge()
        val type = intent.type
        val allowMultiple = intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
        var title = getString(R.string.select)
        title += " " + if (allowMultiple) {
            if (type.pickAny) getString(R.string.photos_and_videos)
            else if (type.pickImage) getString(R.string.photos)
            else getString(R.string.videos)
        } else {
            if (type.pickImage) getString(R.string.photo)
            else if (type.pickVideo) getString(R.string.video)
            else getString(R.string.photos_and_videos)
        }
        setContent {
            GalleryTheme {
                PickerRootScreen(title, type.allowedMedia, allowMultiple)
            }
        }
    }

    @OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
    @Composable
    fun PickerRootScreen(title: String, allowedMedia: AllowedMedia, allowMultiple: Boolean) {
        val mediaPermissions =
            rememberMultiplePermissionsState(Constants.PERMISSIONS)
        if (!mediaPermissions.allPermissionsGranted) {
            LaunchedEffect(Unit) {
                mediaPermissions.launchMultiplePermissionRequest()
            }
        }
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(text = title) },
                    navigationIcon = {
                        IconButton(onClick = ::finish) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = getString(R.string.close)
                            )
                        }
                    }
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .padding(top = it.calculateTopPadding()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PickerScreen(
                    allowedMedia = allowedMedia,
                    allowSelection = allowMultiple,
                    sendMediaAsResult = ::sendMediaAsResult,
                    sendMediaAsMediaResult = ::sendMediaAsMediaResult
                )
            }
        }
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
    }
}