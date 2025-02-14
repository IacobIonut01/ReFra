/*
 * SPDX-FileCopyrightText: 2023 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.domain.model

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Parcelable
import android.webkit.MimeTypeMap
import androidx.room.Entity
import com.dot.gallery.core.Constants
import com.dot.gallery.feature_node.domain.util.UriSerializer
import com.dot.gallery.feature_node.domain.util.getUri
import com.dot.gallery.feature_node.presentation.util.getDate
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import java.io.File
import java.util.UUID
import kotlin.random.Random


@Serializable
@Parcelize
sealed class Media : Parcelable, java.io.Serializable {


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

    override fun toString(): String {
        return "$id, $path, $fullDate, $mimeType, $definedTimestamp"
    }

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
        fun createFromUri(context: Context?, uri: Uri): UriMedia? {
            if (uri.path == null) return null
            val extension = uri.toString().substringAfterLast(".")
            var mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension).toString()
            var duration: String? = null
            if (context != null) {
                try {
                    val retriever = MediaMetadataRetriever().apply {
                        setDataSource(context, uri)
                    }
                    val hasVideo =
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)
                    val isVideo = "yes" == hasVideo
                    if (isVideo) {
                        duration =
                            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    }
                    if (mimeType.isEmpty()) {
                        mimeType = if (isVideo) "video/*" else "image/*"
                    }
                } catch (_: Exception) {
                }
            }
            var timestamp = 0L
            uri.path?.let { File(it) }?.let {
                timestamp = try {
                    it.lastModified()
                } catch (_: Exception) {
                    0L
                }
            }
            var formattedDate = ""
            if (timestamp != 0L) {
                formattedDate = timestamp.getDate(Constants.EXTENDED_DATE_FORMAT)
            }
            return UriMedia(
                id = Random(System.currentTimeMillis()).nextLong(-1000, 25600000),
                label = uri.toString().substringAfterLast("/"),
                uri = uri,
                path = uri.path.toString(),
                relativePath = uri.path.toString().substringBeforeLast("/"),
                albumID = -99L,
                albumLabel = "",
                timestamp = timestamp,
                fullDate = formattedDate,
                mimeType = mimeType,
                duration = duration,
                favorite = 0,
                size = 0,
                trashed = 0
            )
        }
    }

}
