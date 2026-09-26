/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.domain.model

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.room.ColumnInfo
import androidx.room.Entity
import com.dot.gallery.core.Constants
import com.dot.gallery.feature_node.domain.util.UriSerializer
import com.dot.gallery.feature_node.domain.util.getUri
import com.dot.gallery.feature_node.presentation.util.getDate
import com.dot.gallery.feature_node.presentation.util.printError
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import java.io.File
import java.util.UUID
import kotlin.random.Random

@Serializable
@Parcelize
sealed class Media : Parcelable {


    abstract val id: Long
    abstract val label: String
    abstract val path: String
    abstract val relativePath: String
    abstract val albumID: Long
    abstract val albumLabel: String
    abstract val timestamp: Long
    abstract val expiryTimestamp: Long?
    abstract val takenTimestamp: Long?
    abstract val fullDate: String
    abstract val mimeType: String
    abstract val favorite: Int
    abstract val trashed: Int
    abstract val size: Long
    abstract val duration: String?

    val definedTimestamp: Long
        get() = takenTimestamp?.div(1000) ?: timestamp

    val key: String
        get() = "{$id, ${try { getUri() } catch (_: Exception) { path} }, $definedTimestamp}"

    val idLessKey: String
        get() = "{${try { getUri() } catch (_: Exception) { path} }, $definedTimestamp}"

    @Serializable
    @Parcelize
    @Entity(tableName = "media", primaryKeys = ["id"])
    data class UriMedia(
        override val id: Long = 0,
        override val label: String,
        @Serializable(with = UriSerializer::class)
        val uri: Uri,
        override val path: String,
        override val relativePath: String,
        override val albumID: Long,
        override val albumLabel: String,
        override val timestamp: Long,
        override val expiryTimestamp: Long? = null,
        override val takenTimestamp: Long? = null,
        @ColumnInfo(defaultValue = "'modified_fallback'")
        val captureTimeOrigin: CaptureTimeOrigin = if (takenTimestamp != null) {
            CaptureTimeOrigin.MEDIA_STORE
        } else {
            CaptureTimeOrigin.MODIFIED_FALLBACK
        },
        override val fullDate: String,
        override val mimeType: String,
        override val favorite: Int,
        override val trashed: Int,
        override val size: Long,
        override val duration: String? = null
    ) : Media()

    @Serializable
    @Parcelize
    @Entity(tableName = "classified_media", primaryKeys = ["id"])
    data class ClassifiedMedia(
        override val id: Long = 0,
        override val label: String,
        @Serializable(with = UriSerializer::class)
        val uri: Uri,
        override val path: String,
        override val relativePath: String,
        override val albumID: Long,
        override val albumLabel: String,
        override val timestamp: Long,
        override val expiryTimestamp: Long?,
        override val takenTimestamp: Long?,
        override val fullDate: String,
        override val mimeType: String,
        override val favorite: Int,
        override val trashed: Int,
        override val size: Long,
        override val duration: String?,
        val category: String?,
        val score: Float,
    ): Media()

    @Serializable
    @Parcelize
    data class EncryptedMedia(
        override val id: Long = 0,
        override val label: String,
        val bytes: ByteArray,
        override val path: String,
        override val relativePath: String,
        override val albumID: Long,
        override val albumLabel: String,
        override val timestamp: Long,
        override val expiryTimestamp: Long? = null,
        override val takenTimestamp: Long? = null,
        override val fullDate: String,
        override val mimeType: String,
        override val favorite: Int,
        override val trashed: Int,
        override val size: Long,
        override val duration: String? = null
    ): Media() {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as EncryptedMedia

            if (id != other.id) return false
            if (label != other.label) return false
            if (!bytes.contentEquals(other.bytes)) return false
            if (path != other.path) return false
            if (relativePath != other.relativePath) return false
            if (albumID != other.albumID) return false
            if (albumLabel != other.albumLabel) return false
            if (timestamp != other.timestamp) return false
            if (expiryTimestamp != other.expiryTimestamp) return false
            if (takenTimestamp != other.takenTimestamp) return false
            if (fullDate != other.fullDate) return false
            if (mimeType != other.mimeType) return false
            if (favorite != other.favorite) return false
            if (trashed != other.trashed) return false
            if (size != other.size) return false
            if (duration != other.duration) return false

            return true
        }

        override fun hashCode(): Int {
            var result = id.hashCode()
            result = 31 * result + label.hashCode()
            result = 31 * result + bytes.contentHashCode()
            result = 31 * result + path.hashCode()
            result = 31 * result + relativePath.hashCode()
            result = 31 * result + albumID.hashCode()
            result = 31 * result + albumLabel.hashCode()
            result = 31 * result + timestamp.hashCode()
            result = 31 * result + (expiryTimestamp?.hashCode() ?: 0)
            result = 31 * result + (takenTimestamp?.hashCode() ?: 0)
            result = 31 * result + fullDate.hashCode()
            result = 31 * result + mimeType.hashCode()
            result = 31 * result + favorite
            result = 31 * result + trashed
            result = 31 * result + size.hashCode()
            result = 31 * result + (duration?.hashCode() ?: 0)
            return result
        }
    }

    @Serializable
    @Parcelize
    @Entity(tableName = "encrypted_media", primaryKeys = ["id"])
    data class EncryptedMedia2(
        override val id: Long = 0,
        override val label: String,
        @Serializable(with = UUIDSerializer::class)
        val uuid: UUID,
        override val path: String,
        override val relativePath: String,
        override val albumID: Long,
        override val albumLabel: String,
        override val timestamp: Long,
        override val expiryTimestamp: Long? = null,
        override val takenTimestamp: Long? = null,
        override val fullDate: String,
        override val mimeType: String,
        override val favorite: Int,
        override val trashed: Int,
        override val size: Long,
        override val duration: String? = null
    ): Media() {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as EncryptedMedia

            if (id != other.id) return false
            if (label != other.label) return false
            if (path != other.path) return false
            if (relativePath != other.relativePath) return false
            if (albumID != other.albumID) return false
            if (albumLabel != other.albumLabel) return false
            if (timestamp != other.timestamp) return false
            if (expiryTimestamp != other.expiryTimestamp) return false
            if (takenTimestamp != other.takenTimestamp) return false
            if (fullDate != other.fullDate) return false
            if (mimeType != other.mimeType) return false
            if (favorite != other.favorite) return false
            if (trashed != other.trashed) return false
            if (size != other.size) return false
            if (duration != other.duration) return false

            return true
        }

        override fun hashCode(): Int {
            var result = id.hashCode()
            result = 31 * result + label.hashCode()
            result = 31 * result + path.hashCode()
            result = 31 * result + relativePath.hashCode()
            result = 31 * result + albumID.hashCode()
            result = 31 * result + albumLabel.hashCode()
            result = 31 * result + timestamp.hashCode()
            result = 31 * result + (expiryTimestamp?.hashCode() ?: 0)
            result = 31 * result + (takenTimestamp?.hashCode() ?: 0)
            result = 31 * result + fullDate.hashCode()
            result = 31 * result + mimeType.hashCode()
            result = 31 * result + favorite
            result = 31 * result + trashed
            result = 31 * result + size.hashCode()
            result = 31 * result + (duration?.hashCode() ?: 0)
            return result
        }
    }

    companion object {
        private const val TAG = "db.media"

        fun createFromUri(context: Context?, uri: Uri): UriMedia? {
            if (uri.path == null) return null
            val isContent = uri.scheme == ContentResolver.SCHEME_CONTENT

            val extension = uri.toString().substringAfterLast(".")
            // For content:// URIs the string has no usable extension, so prefer
            // the resolver's declared type (works for pending items via the grant).
            var mimeType: String? = if (isContent) {
                context?.contentResolver?.getType(uri)
            } else null
            if (mimeType == null) {
                mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                    ?: when (extension.lowercase()) {
                        "apng" -> "image/apng"
                        "jxl" -> "image/jxl"
                        else -> null
                    }
            }

            var duration: String? = null
            var timestamp = 0L
            var size = 0L
            var displayName: String? = null

            // Query the granted URI directly. A query on the specific URI returns
            // metadata even when the item is still IS_PENDING and owned by another
            // app (e.g. a freshly captured photo from a secure camera session),
            // because the camera grants us per-URI read access.
            if (isContent && context != null) {
                try {
                    val projection = arrayOf(
                        MediaStore.MediaColumns.DISPLAY_NAME,
                        MediaStore.MediaColumns.SIZE,
                        MediaStore.MediaColumns.DATE_MODIFIED,
                        MediaStore.MediaColumns.MIME_TYPE,
                    )
                    val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val queryArgs = Bundle().apply {
                            putInt(
                                MediaStore.QUERY_ARG_MATCH_PENDING,
                                MediaStore.MATCH_INCLUDE
                            )
                        }
                        context.contentResolver.query(uri, projection, queryArgs, null)
                    } else {
                        context.contentResolver.query(uri, projection, null, null, null)
                    }
                    cursor?.use { c ->
                        if (c.moveToFirst()) {
                            c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                                .takeIf { it >= 0 }?.let { displayName = c.getString(it) }
                            c.getColumnIndex(MediaStore.MediaColumns.SIZE)
                                .takeIf { it >= 0 }?.let { size = c.getLong(it) }
                            c.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                                .takeIf { it >= 0 }?.let { timestamp = c.getLong(it) }
                            if (mimeType == null) {
                                c.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                                    .takeIf { it >= 0 }?.let { mimeType = c.getString(it) }
                            }
                        }
                    }
                } catch (e: Exception) {
                    printError(TAG, "Failed to query media metadata for $uri", e)
                }
            } else {
                uri.path?.let { File(it) }?.let {
                    timestamp = try {
                        it.lastModified()
                    } catch (_: Exception) {
                        0L
                    }
                }
            }

            if (context != null) {
                try {
                    MediaMetadataRetriever().use { retriever ->
                        retriever.setDataSource(context, uri)
                        val hasVideo =
                            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)
                        val isVideo = "yes" == hasVideo
                        if (isVideo) {
                            duration =
                                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                            if (timestamp == 0L) {
                                timestamp = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)?.toLongOrNull() ?: 0L
                            }
                        }
                        val originMimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
                        if (mimeType == null) {
                            mimeType = if (isVideo) originMimeType else "image/*"
                        }
                    }
                } catch (e: Exception) {
                    printError(TAG, "Failed to extract media metadata for $uri", e)
                }
            }
            var formattedDate = ""
            if (timestamp != 0L) {
                formattedDate = timestamp.getDate(Constants.EXTENDED_DATE_FORMAT)
            }
            // Use the real MediaStore id for content URIs so the viewer can resolve
            // the item; fall back to a random id only for non-MediaStore URIs.
            val resolvedId: Long = if (isContent) {
                try {
                    ContentUris.parseId(uri)
                } catch (_: Exception) {
                    Random(System.currentTimeMillis()).nextLong(-1000, 25600000)
                }
            } else {
                Random(System.currentTimeMillis()).nextLong(-1000, 25600000)
            }
            val label = displayName ?: uri.toString().substringAfterLast("/")
            return UriMedia(
                id = resolvedId,
                label = label,
                uri = uri,
                path = uri.path.toString(),
                relativePath = uri.path.toString().substringBeforeLast("/"),
                albumID = -99L,
                albumLabel = "",
                timestamp = timestamp,
                fullDate = formattedDate,
                mimeType = mimeType ?: "null",
                duration = duration,
                favorite = 0,
                size = size,
                trashed = 0
            )
        }
    }

}
