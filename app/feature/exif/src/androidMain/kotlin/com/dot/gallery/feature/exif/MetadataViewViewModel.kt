/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.exif

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dot.gallery.core.Settings
import com.dot.gallery.core.logging.printWarn
import com.dot.gallery.core.sandbox.IsolatedMetadataParser
import com.dot.gallery.feature.exif.ui.MetadataFileInfo
import com.dot.gallery.feature.exif.ui.MetadataViewState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

class MetadataViewViewModel @Inject constructor(
    private val isolatedParser: IsolatedMetadataParser,
    private val appContext: Context
) : ViewModel() {

    private val _state = MutableStateFlow(MetadataViewState())
    val state: StateFlow<MetadataViewState> = _state

    fun loadMetadata(mediaUri: String, isVideo: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val uri = mediaUri.toUri()
            _state.value = MetadataViewState(isLoading = true, file = MetadataFileInfo(thumbnailModel = uri))
            val file = queryFileInfo(uri)
            _state.value = MetadataViewState(isLoading = true, file = file)
            val directories = runCatching {
                val mode = Settings.Security.getMetadataIsolationMode(appContext)
                    .firstOrNull() ?: Settings.Security.METADATA_ISOLATION_SHARED
                val usePerFile = mode != Settings.Security.METADATA_ISOLATION_SHARED
                if (usePerFile) {
                    val mediaId = ContentUris.parseId(uri)
                    isolatedParser.parseRawMetadataPerFile(uri, isVideo, mediaId)
                } else {
                    isolatedParser.parseRawMetadata(uri, isVideo)
                }
            }.getOrElse {
                printWarn(
                    tag = "metadata.view",
                    message = "Raw metadata parse failed",
                    ctx = mapOf("error" to (it.message ?: it::class.simpleName.orEmpty())),
                )
                emptyList()
            }
            _state.value = MetadataViewState(
                isLoading = false,
                directories = directories,
                file = file,
            )
        }
    }

    /**
     * Name, size and pixel size for the file card. MediaStore rows carry all of them; other
     * providers usually answer only the OpenableColumns, so missing columns stay null.
     */
    private fun queryFileInfo(uri: Uri): MetadataFileInfo {
        // No projection: providers outside MediaStore reject columns they don't know.
        val info = runCatching {
            appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                fun column(name: String) = cursor.getColumnIndex(name).takeIf { it >= 0 && !cursor.isNull(it) }
                MetadataFileInfo(
                    name = column(OpenableColumns.DISPLAY_NAME)?.let(cursor::getString),
                    sizeBytes = column(OpenableColumns.SIZE)?.let(cursor::getLong),
                    width = column(MediaStore.MediaColumns.WIDTH)?.let(cursor::getInt),
                    height = column(MediaStore.MediaColumns.HEIGHT)?.let(cursor::getInt),
                    thumbnailModel = uri,
                )
            }
        }.getOrElse {
            printWarn(
                tag = "metadata.view",
                message = "File details query failed",
                ctx = mapOf("error" to (it.message ?: it::class.simpleName.orEmpty())),
            )
            null
        }
        return info ?: MetadataFileInfo(name = uri.lastPathSegment, thumbnailModel = uri)
    }
}
