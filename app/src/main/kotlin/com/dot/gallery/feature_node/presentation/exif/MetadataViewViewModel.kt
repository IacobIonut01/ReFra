/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.exif

import android.app.Application
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.drew.imaging.ImageMetadataReader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MetadataDirectory(
    val name: String,
    val tags: List<MetadataTag>
)

data class MetadataTag(
    val name: String,
    val description: String
)

data class MetadataViewState(
    val isLoading: Boolean = true,
    val directories: List<MetadataDirectory> = emptyList()
)

@HiltViewModel
class MetadataViewViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(MetadataViewState())
    val state: StateFlow<MetadataViewState> = _state

    fun loadMetadata(mediaUri: String, isVideo: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = MetadataViewState(isLoading = true)
            val uri = Uri.parse(mediaUri)
            val directories = runCatching {
                if (isVideo) {
                    readVideoMetadata(uri)
                } else {
                    readImageMetadata(uri)
                }
            }.getOrElse { emptyList() }
            _state.value = MetadataViewState(
                isLoading = false,
                directories = directories
            )
        }
    }

    private fun readImageMetadata(uri: Uri): List<MetadataDirectory> {
        val context = getApplication<Application>()
        val result = mutableListOf<MetadataDirectory>()
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val metadata = ImageMetadataReader.readMetadata(stream)
            for (directory in metadata.directories) {
                val tags = directory.tags
                    .filter { it.description != null }
                    .map { tag ->
                        MetadataTag(
                            name = tag.tagName,
                            description = tag.description
                        )
                    }
                if (tags.isNotEmpty()) {
                    result.add(
                        MetadataDirectory(
                            name = directory.name,
                            tags = tags
                        )
                    )
                }
            }
        }
        return result
    }

    @Suppress("DEPRECATION")
    private fun readVideoMetadata(uri: Uri): List<MetadataDirectory> {
        val context = getApplication<Application>()
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val tags = mutableListOf<MetadataTag>()
            val keyMap = mapOf(
                MediaMetadataRetriever.METADATA_KEY_ALBUM to "Album",
                MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST to "Album Artist",
                MediaMetadataRetriever.METADATA_KEY_ARTIST to "Artist",
                MediaMetadataRetriever.METADATA_KEY_AUTHOR to "Author",
                MediaMetadataRetriever.METADATA_KEY_BITRATE to "Bitrate",
                MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER to "Track Number",
                MediaMetadataRetriever.METADATA_KEY_COMPILATION to "Compilation",
                MediaMetadataRetriever.METADATA_KEY_COMPOSER to "Composer",
                MediaMetadataRetriever.METADATA_KEY_DATE to "Date",
                MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER to "Disc Number",
                MediaMetadataRetriever.METADATA_KEY_DURATION to "Duration",
                MediaMetadataRetriever.METADATA_KEY_GENRE to "Genre",
                MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO to "Has Audio",
                MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO to "Has Video",
                MediaMetadataRetriever.METADATA_KEY_LOCATION to "Location",
                MediaMetadataRetriever.METADATA_KEY_MIMETYPE to "MIME Type",
                MediaMetadataRetriever.METADATA_KEY_NUM_TRACKS to "Number of Tracks",
                MediaMetadataRetriever.METADATA_KEY_TITLE to "Title",
                MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT to "Video Height",
                MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH to "Video Width",
                MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION to "Video Rotation",
                MediaMetadataRetriever.METADATA_KEY_WRITER to "Writer",
                MediaMetadataRetriever.METADATA_KEY_YEAR to "Year",
                MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT to "Frame Count",
                MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE to "Capture Frame Rate",
                MediaMetadataRetriever.METADATA_KEY_COLOR_STANDARD to "Color Standard",
                MediaMetadataRetriever.METADATA_KEY_COLOR_TRANSFER to "Color Transfer",
                MediaMetadataRetriever.METADATA_KEY_COLOR_RANGE to "Color Range",
                MediaMetadataRetriever.METADATA_KEY_SAMPLERATE to "Sample Rate",
                MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE to "Bits Per Sample",
            )
            for ((key, name) in keyMap) {
                retriever.extractMetadata(key)?.let { value ->
                    tags.add(MetadataTag(name = name, description = value))
                }
            }
            if (tags.isNotEmpty()) {
                listOf(MetadataDirectory(name = "Video Metadata", tags = tags))
            } else {
                emptyList()
            }
        } finally {
            retriever.release()
        }
    }
}
