package com.dot.gallery.feature_node.data.data_source.mediastore.queries

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.core.database.getLongOrNull
import androidx.core.os.bundleOf
import com.dot.gallery.core.util.PickerUtils
import com.dot.gallery.core.util.Query
import com.dot.gallery.core.util.and
import com.dot.gallery.core.util.eq
import com.dot.gallery.core.util.ext.queryFlow
import com.dot.gallery.core.util.ext.tryGetString
import com.dot.gallery.core.util.join
import com.dot.gallery.feature_node.data.data_source.mediastore.MediaQuery
import com.dot.gallery.feature_node.domain.model.Album
import com.dot.gallery.feature_node.domain.model.MediaType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Albums flow
 *
 * This class is responsible for fetching albums from the media store
 *
 * @property context
 * @property mimeType
 */
class AlbumsFlow(
    private val context: Context,
    private val mimeType: String? = null,
) : QueryFlow<Album>() {
    override fun flowCursor(): Flow<Cursor?> {
        val uri = MediaQuery.MediaStoreFileUri
        val projection = MediaQuery.AlbumsProjection
        val imageOrVideo = PickerUtils.mediaTypeFromGenericMimeType(mimeType)?.let {
            when (it) {
                MediaType.IMAGE -> MediaQuery.Selection.image
                MediaType.VIDEO -> MediaQuery.Selection.video
            }
        } ?: MediaQuery.Selection.imageOrVideo
        val rawMimeType = mimeType?.takeIf { PickerUtils.isMimeTypeNotGeneric(it) }
        val mimeTypeQuery = rawMimeType?.let {
            MediaStore.Files.FileColumns.MIME_TYPE eq Query.ARG
        }

        // Join all the non-null queries
        val selection = listOfNotNull(
            mimeTypeQuery,
            imageOrVideo,
        ).join(Query::and)

        val selectionArgs = listOfNotNull(
            rawMimeType,
        ).toTypedArray()

        val sortOrder = MediaStore.Files.FileColumns.DATE_MODIFIED + " DESC"

        val queryArgs = Bundle().apply {
            putAll(
                bundleOf(
                    ContentResolver.QUERY_ARG_SQL_SELECTION to selection?.build(),
                    ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS to selectionArgs,
                    ContentResolver.QUERY_ARG_SQL_SORT_ORDER to sortOrder,
                )
            )
        }

        return context.contentResolver.queryFlow(
            uri,
            projection,
            queryArgs,
        )
    }

    override fun flowData() = flowCursor().map {
        mutableMapOf<Int, Album>().apply {
            it?.use {
                val idIndex = it.getColumnIndex(MediaStore.Files.FileColumns._ID)
                val albumIdIndex = it.getColumnIndex(MediaStore.Files.FileColumns.BUCKET_ID)
                val labelIndex = it.getColumnIndex(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)
                val thumbnailPathIndex = it.getColumnIndex(MediaStore.Files.FileColumns.DATA)
                val thumbnailRelativePathIndex =
                    it.getColumnIndex(MediaStore.Files.FileColumns.RELATIVE_PATH)
                val thumbnailDateTakenIndex = it.getColumnIndex(MediaStore.Files.FileColumns.DATE_TAKEN)
                val thumbnailDateIndex = it.getColumnIndex(MediaStore.Files.FileColumns.DATE_MODIFIED)
                val sizeIndex = it.getColumnIndex(MediaStore.Files.FileColumns.SIZE)
                val mimeTypeIndex = it.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE)

                if (!it.moveToFirst()) {
                    return@use
                }

                while (!it.isAfterLast) {
                    val bucketId = it.getInt(albumIdIndex)

                    this[bucketId]?.also { album ->
                        album.count += 1
                        album.size += it.getLong(sizeIndex)
                    } ?: run {
                        val albumId = it.getLong(albumIdIndex)
                        val id = it.getLong(idIndex)
                        val label = it.tryGetString(labelIndex, Build.MODEL)
                        val thumbnailPath = it.getString(thumbnailPathIndex)
                        val thumbnailRelativePath = it.getString(thumbnailRelativePathIndex)
                        val thumbnailDateTaken = it.getLongOrNull(thumbnailDateTakenIndex)
                        val thumbnailDate = it.getLong(thumbnailDateIndex)
                        val size = it.getLong(sizeIndex)
                        val mimeType = it.getString(mimeTypeIndex)
                        val contentUri = if (mimeType.contains("image"))
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                        else
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI

                        this[bucketId] = Album(
                            id = albumId,
                            label = label ?: Build.MODEL,
                            uri = ContentUris.withAppendedId(contentUri, id),
                            pathToThumbnail = thumbnailPath,
                            relativePath = thumbnailRelativePath,
                            timestamp = thumbnailDateTaken?.div(1000) ?: thumbnailDate
                        ).apply {
                            this.count += 1
                            this.size += size
                        }
                    }

                    it.moveToNext()
                }
            }
        }.values.toList()
    }
}