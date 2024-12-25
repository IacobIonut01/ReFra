/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.data.data_source.mediastore.queries

import android.content.ContentResolver
import android.content.ContentUris
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.core.os.bundleOf
import com.dot.gallery.core.Constants
import com.dot.gallery.core.util.MediaStoreBuckets
import com.dot.gallery.core.util.PickerUtils
import com.dot.gallery.core.util.Query
import com.dot.gallery.core.util.and
import com.dot.gallery.core.util.eq
import com.dot.gallery.core.util.ext.mapEachRow
import com.dot.gallery.core.util.ext.queryFlow
import com.dot.gallery.core.util.ext.tryGetLong
import com.dot.gallery.core.util.ext.tryGetString
import com.dot.gallery.core.util.join
import com.dot.gallery.feature_node.data.data_source.mediastore.MediaQuery
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.MediaType
import com.dot.gallery.feature_node.domain.util.isTrashed
import com.dot.gallery.feature_node.presentation.util.getDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.random.Random

/**
 * Media uri flow
 *
 * This class is responsible for fetching media from the media store based on the provided uris
 *
 * @property contentResolver
 * @property mimeType
 * @property uris
 * @property reviewMode
 */
class MediaUriFlow(
    private val contentResolver: ContentResolver,
    private val mimeType: String? = null,
    private val uris: List<Uri>,
    private val reviewMode: Boolean = false
) : QueryFlow<Media.UriMedia>() {

    private var buckedId: Long = MediaStoreBuckets.MEDIA_STORE_BUCKET_TIMELINE.id

    override fun flowCursor(): Flow<Cursor?> {
        val uri = MediaQuery.MediaStoreFileUri
        val projection = MediaQuery.MediaProjection
        val imageOrVideo = PickerUtils.mediaTypeFromGenericMimeType(mimeType)?.let {
            when (it) {
                MediaType.IMAGE -> MediaQuery.Selection.image
                MediaType.VIDEO -> MediaQuery.Selection.video
            }
        } ?: MediaQuery.Selection.imageOrVideo
        val albumFilter = when (buckedId) {
            MediaStoreBuckets.MEDIA_STORE_BUCKET_FAVORITES.id -> MediaStore.Files.FileColumns.IS_FAVORITE eq 1

            MediaStoreBuckets.MEDIA_STORE_BUCKET_TRASH.id ->
                MediaStore.Files.FileColumns.IS_TRASHED eq 1

            MediaStoreBuckets.MEDIA_STORE_BUCKET_TIMELINE.id,
            MediaStoreBuckets.MEDIA_STORE_BUCKET_PHOTOS.id,
            MediaStoreBuckets.MEDIA_STORE_BUCKET_VIDEOS.id -> null

            else -> MediaStore.Files.FileColumns.BUCKET_ID eq Query.ARG
        }
        val rawMimeType = mimeType?.takeIf { PickerUtils.isMimeTypeNotGeneric(it) }
        val mimeTypeQuery = rawMimeType?.let {
            MediaStore.Files.FileColumns.MIME_TYPE eq Query.ARG
        }

        // Join all the non-null queries
        val selection = listOfNotNull(
            imageOrVideo,
            albumFilter,
            mimeTypeQuery,
        ).join(Query::and)

        val selectionArgs = listOfNotNull(
            buckedId.takeIf {
                MediaStoreBuckets.entries.toTypedArray().none { bucket -> it == bucket.id }
            }?.toString(),
            rawMimeType,
        ).toTypedArray()

        val sortOrder = when (buckedId) {
            MediaStoreBuckets.MEDIA_STORE_BUCKET_TRASH.id ->
                "${MediaStore.Files.FileColumns.DATE_EXPIRES} DESC"

            else -> "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
        }

        val queryArgs = Bundle().apply {
            putAll(
                bundleOf(
                    ContentResolver.QUERY_ARG_SQL_SELECTION to selection?.build(),
                    ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS to selectionArgs,
                    ContentResolver.QUERY_ARG_SQL_SORT_ORDER to sortOrder,
                )
            )

            // Exclude trashed media unless we want data for the trashed album
            putInt(
                MediaStore.QUERY_ARG_MATCH_TRASHED, when (buckedId) {
                    MediaStoreBuckets.MEDIA_STORE_BUCKET_TRASH.id -> MediaStore.MATCH_ONLY

                    else -> MediaStore.MATCH_EXCLUDE
                }
            )
        }

        return contentResolver.queryFlow(
            uri,
            projection,
            queryArgs,
        )
    }

    override fun flowData() =
        flowCursor().mapEachRow(MediaQuery.MediaProjection) { it, indexCache ->
            var i = 0

            val id = it.getLong(indexCache[i++])
            val path = it.getString(indexCache[i++])
            val relativePath = it.getString(indexCache[i++])
            val title = it.getString(indexCache[i++])
            val albumID = it.getLong(indexCache[i++])
            val albumLabel = it.tryGetString(indexCache[i++], Build.MODEL)
            val takenTimestamp = it.tryGetLong(indexCache[i++])
            val modifiedTimestamp = it.getLong(indexCache[i++])
            val duration = it.tryGetString(indexCache[i++])
            val size = it.getLong(indexCache[i++])
            val mimeType = it.getString(indexCache[i++])
            val isFavorite = it.getInt(indexCache[i++])
            val isTrashed = it.getInt(indexCache[i++])
            val expiryTimestamp = it.tryGetLong(indexCache[i])
            val contentUri = if (mimeType.contains("image"))
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            else
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            val uri = ContentUris.withAppendedId(contentUri, id)
            val formattedDate = modifiedTimestamp.getDate(Constants.FULL_DATE_FORMAT)
            Media.UriMedia(
                id = id,
                label = title,
                uri = uri,
                path = path,
                relativePath = relativePath,
                albumID = albumID,
                albumLabel = albumLabel ?: Build.MODEL,
                timestamp = modifiedTimestamp,
                takenTimestamp = takenTimestamp,
                expiryTimestamp = expiryTimestamp,
                fullDate = formattedDate,
                duration = duration,
                favorite = isFavorite,
                trashed = isTrashed,
                size = size,
                mimeType = mimeType
            )
        }.let { flow ->
            val ids = uris.map {
                try {
                    ContentUris.parseId(it)
                } catch (e: NumberFormatException) {
                    Random.nextInt(1000000, 2000000)
                }
            }
            if (reviewMode) {
                flow.map { mediaList ->
                    mediaList.filter { media ->
                        ids.contains(media.id) && !media.isTrashed
                    }
                }
            } else {
                flow.map { mediaList ->
                    mediaList.filter { media ->
                        ids.contains(media.id)
                    }
                }
            }
        }
}