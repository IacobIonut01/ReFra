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
import com.dot.gallery.feature_node.presentation.util.printWarning

/**
 * Media uri flow
 *
 * This class is responsible for fetching media from the media store based on the provided uris
 *
 * @property contentResolver
 * @property mimeType
 * @property uris
 * @property onlyMatchingUris If true, only media that matches the provided uris will be returned
 */
class MediaUriFlow(
    private val contentResolver: ContentResolver,
    private val mimeType: String? = null,
    private val uris: List<Uri>,
    private val onlyMatchingUris: Boolean = false,
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
            val isTrashed = it.getInt(indexCache[i])
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
                expiryTimestamp = null,
                fullDate = formattedDate,
                duration = duration,
                favorite = isFavorite,
                trashed = isTrashed,
                size = size,
                mimeType = mimeType
            )
        }.let { flow ->
            // Derive candidate media IDs from provided URIs. These may be either
            // MediaStore content:// URIs or file:// URIs that represent encrypted
            // vault files whose filenames follow the pattern <originalId>.enc
            val ids: List<Long> = uris.mapNotNull { uri ->
                parseCandidateId(uri)
            }.distinct()
            if (onlyMatchingUris) {
                return flow.map { mediaList ->
                    mediaList.filter { media -> ids.contains(media.id) && !media.isTrashed }
                }
            } else {
                val bucketId = getBucketIdFromFirstUri()
                flow.map { mediaList ->
                    mediaList.filter { media ->
                        bucketId?.let {
                            media.albumID == bucketId && !media.isTrashed
                        } ?: ids.contains(media.id) && !media.isTrashed
                    }
                }
            }
        }

    private fun getBucketIdFromFirstUri(): Long? {
        val firstUri = uris.firstOrNull() ?: return null
        // Bucket lookup only makes sense for MediaStore content URIs. File based
        // (encrypted) URIs won't have a bucket; skip early.
        if (firstUri.scheme != ContentResolver.SCHEME_CONTENT) return null
        val id = try {
            ContentUris.parseId(firstUri)
        } catch (e: NumberFormatException) {
            e.printStackTrace()
            return null
        }
        val projection = arrayOf(MediaStore.Files.FileColumns.BUCKET_ID)
        val selection = "${MediaStore.Files.FileColumns._ID} = ?"
        val selectionArgs = arrayOf(id.toString())
        contentResolver.query(
            MediaQuery.MediaStoreFileUri,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_ID))
            }
        }
        return null
    }

    /**
     * Attempt to derive a stable numeric media ID from a supplied URI.
     *  - For content:// URIs we delegate to [ContentUris.parseId].
     *  - For file:// URIs pointing to encrypted vault files we strip a trailing
     *    ".enc" extension and parse the remaining filename as a Long.
     * Returns null (instead of a random fabricated ID) if parsing fails so the
     * caller can simply exclude the unmatched entry. This prevents accidental
     * association with unrelated media rows and avoids decryption failures due
     * to ID skew.
     */
    private fun parseCandidateId(uri: Uri): Long? {
        return if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            try {
                ContentUris.parseId(uri)
            } catch (e: NumberFormatException) {
                // Unexpected malformed content URI; exclude.
                printWarning("MediaUriFlow: Failed to parse content URI id: $uri -> ${e.message}")
                null
            }
        } else {
            // file:// or other scheme; check for encrypted vault naming pattern
            val name = uri.lastPathSegment ?: return null
            val numericPart = if (name.endsWith(".enc", ignoreCase = true)) {
                name.removeSuffix(".enc")
            } else name
            numericPart.toLongOrNull().also { parsed ->
                if (parsed == null) {
                    printWarning("MediaUriFlow: Unable to derive id from URI filename '$name'")
                }
            }
        }
    }
}