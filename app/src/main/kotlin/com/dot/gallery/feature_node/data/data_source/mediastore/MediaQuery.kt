package com.dot.gallery.feature_node.data.data_source.mediastore

import android.net.Uri
import android.provider.MediaStore
import com.dot.gallery.core.util.SdkCompat
import com.dot.gallery.core.util.eq
import com.dot.gallery.core.util.or

object MediaQuery {
    val MediaStoreFileUri: Uri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)

    private val MediaProjectionBase = arrayOf(
        MediaStore.Files.FileColumns._ID,
        MediaStore.Files.FileColumns.DATA,
        MediaStore.Files.FileColumns.RELATIVE_PATH,
        MediaStore.Files.FileColumns.DISPLAY_NAME,
        MediaStore.Files.FileColumns.BUCKET_ID,
        MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME,
        MediaStore.Files.FileColumns.DATE_TAKEN,
        MediaStore.Files.FileColumns.DATE_MODIFIED,
        MediaStore.Files.FileColumns.DURATION,
        MediaStore.Files.FileColumns.SIZE,
        MediaStore.Files.FileColumns.MIME_TYPE,
    )

    val MediaProjection: Array<String>
        get() = if (SdkCompat.supportsFavorites) {
            MediaProjectionBase + arrayOf(
                MediaStore.Files.FileColumns.IS_FAVORITE,
                MediaStore.Files.FileColumns.IS_TRASHED
            )
        } else {
            MediaProjectionBase
        }

    val MediaProjectionTrash: Array<String>
        get() = if (SdkCompat.supportsTrash) {
            MediaProjectionBase + arrayOf(
                MediaStore.Files.FileColumns.IS_FAVORITE,
                MediaStore.Files.FileColumns.IS_TRASHED,
                MediaStore.Files.FileColumns.DATE_EXPIRES
            )
        } else {
            // Trash is not supported on API 29; return base projection
            MediaProjectionBase
        }

    val MediaMetadataProjection: Array<String>
        get() {
            val base = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DATA,
                MediaStore.Files.FileColumns.DATE_TAKEN,
                MediaStore.Files.FileColumns.DATE_MODIFIED,
                MediaStore.Files.FileColumns.DURATION,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.MIME_TYPE,
            )
            return if (SdkCompat.supportsTrash) {
                base + arrayOf(MediaStore.Files.FileColumns.DATE_EXPIRES)
            } else {
                base
            }
        }

    val AlbumsProjection = arrayOf(
        MediaStore.Files.FileColumns._ID,
        MediaStore.Files.FileColumns.DATA,
        MediaStore.Files.FileColumns.RELATIVE_PATH,
        MediaStore.Files.FileColumns.DISPLAY_NAME,
        MediaStore.Files.FileColumns.BUCKET_ID,
        MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME,
        MediaStore.Files.FileColumns.DATE_TAKEN,
        MediaStore.Files.FileColumns.DATE_MODIFIED,
        MediaStore.Files.FileColumns.SIZE,
        MediaStore.Files.FileColumns.MIME_TYPE,
    )

    object Selection {
        val image =
            MediaStore.Files.FileColumns.MEDIA_TYPE eq MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE
        val video =
            MediaStore.Files.FileColumns.MEDIA_TYPE eq MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
        val imageOrVideo = image or video
    }
}