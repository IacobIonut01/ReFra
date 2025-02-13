/*
 * SPDX-FileCopyrightText: 2023 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.util

import android.app.Activity.RESULT_CANCELED
import android.app.Activity.RESULT_OK
import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.core.net.toFile
import androidx.exifinterface.media.ExifInterface
import com.dot.gallery.BuildConfig
import com.dot.gallery.feature_node.domain.model.InfoRow
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.util.getUri
import com.dot.gallery.feature_node.presentation.mediaview.components.retrieveMetadata
import java.io.IOException

val sdcardRegex = "^/storage/[A-Z0-9]+-[A-Z0-9]+/.*$".toRegex()


@Composable
fun rememberBitmapPainter(bitmap: Bitmap): State<Painter> {
    return remember(bitmap) { derivedStateOf { BitmapPainter(image = bitmap.asImageBitmap()) } }
}

fun FloatArray.to3x3Matrix(): FloatArray {
    return floatArrayOf(
        this[0], this[1], this[2],
        this[5], this[6], this[7],
        this[10], this[11], this[12]
    )
}

fun resizeBitmap(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
    val width = bitmap.width
    val height = bitmap.height
    val aspectRatio = width.toFloat() / height.toFloat()
    val newWidth: Int
    val newHeight: Int

    if (width > height) {
        newWidth = maxWidth
        newHeight = (maxWidth / aspectRatio).toInt()
    } else {
        newHeight = maxHeight
        newWidth = (maxHeight * aspectRatio).toInt()
    }

    return bitmap.scale(newWidth, newHeight)
}

fun overlayBitmaps(currentImage: Bitmap, markupBitmap: Bitmap): Bitmap {
    // Create a new bitmap with the same dimensions as the current image
    val resultBitmap = createBitmap(
        currentImage.width,
        currentImage.height,
        currentImage.config ?: Bitmap.Config.ARGB_8888
    )

    // Create a canvas to draw on the new bitmap
    val canvas = Canvas(resultBitmap)

    // Draw the current image on the canvas
    canvas.drawBitmap(currentImage, 0f, 0f, null)

    // Draw the markup bitmap on top of the current image
    canvas.drawBitmap(markupBitmap.copy(Bitmap.Config.ARGB_8888, true), 0f, 0f, null)

    return resultBitmap
}

fun Bitmap.flipHorizontally(): Bitmap {
    val matrix = Matrix().apply { postScale(-1f, 1f, width / 2f, height / 2f) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

fun Bitmap.flipVertically(): Bitmap {
    val matrix = Matrix().apply { postScale(1f, -1f, width / 2f, height / 2f) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

fun Bitmap.rotate(degrees: Float): Bitmap {
    val matrix = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

fun List<Media>.canBeTrashed(): Boolean {
    return find { it.path.matches(sdcardRegex) } == null
}

/**
 * first pair = trashable
 * second pair = non-trashable
 */
fun List<Media>.mediaPair(): Pair<List<Media>, List<Media>> {
    val trashableMedia = ArrayList<Media>()
    val nonTrashableMedia = ArrayList<Media>()
    forEach {
        if (it.path.matches(sdcardRegex)) {
            nonTrashableMedia.add(it)
        } else {
            trashableMedia.add(it)
        }
    }
    return trashableMedia to nonTrashableMedia
}

fun Media.canBeTrashed(): Boolean {
    return !path.matches(sdcardRegex)
}

@Composable
fun rememberActivityResult(onResultCanceled: () -> Unit = {}, onResultOk: () -> Unit = {}) =
    rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
        onResult = {
            if (it.resultCode == RESULT_OK) onResultOk()
            if (it.resultCode == RESULT_CANCELED) onResultCanceled()
        }
    )


fun <T: Media> T.writeRequest(
    contentResolver: ContentResolver,
) = IntentSenderRequest.Builder(MediaStore.createWriteRequest(contentResolver, arrayListOf(getUri())))
    .build()

fun <T: Media> List<T>.writeRequest(
    contentResolver: ContentResolver,
) = IntentSenderRequest.Builder(MediaStore.createWriteRequest(contentResolver, map { it.getUri() }))
    .build()

fun Uri.writeRequest(
    contentResolver: ContentResolver,
) = IntentSenderRequest.Builder(MediaStore.createWriteRequest(contentResolver, arrayListOf(this)))
    .build()

@Composable
fun <T: Media> rememberMediaInfo(
    media: T,
    exifMetadata: ExifMetadata?,
    onLabelClick: () -> Unit
): List<InfoRow> {
    val context = LocalContext.current
    return remember(media) {
        media.retrieveMetadata(context, exifMetadata, onLabelClick)
    }
}

@Composable
fun <T: Media> rememberExifMetadata(media: T, exifInterface: ExifInterface?): ExifMetadata? {
    return remember(media, exifInterface) {
        exifInterface?.let { ExifMetadata(it) }
    }
}

@Composable
fun <T: Media> rememberExifInterface(media: T, useDirectPath: Boolean = false): ExifInterface? {
    val context = LocalContext.current
    return remember(media) {
        if (useDirectPath) try {
            ExifInterface(media.path)
        } catch (_: IOException) {
            null
        }
        else getExifInterface(context, media.getUri())
    }
}

@Throws(IOException::class)
fun getExifInterface(context: Context, uri: Uri): ExifInterface? {
    if (uri.isFromApps()) return null
    return try {
        ExifInterface(context.uriToPath(uri).toString())
    } catch (_: IOException) {
        null
    }
}

fun Context.uriToPath(uri: Uri?): String? {
    if (uri == null) return null
    val proj = arrayOf(MediaStore.MediaColumns.DATA)
    var path: String? = null
    val cursor: Cursor? = contentResolver.query(uri, proj, null, null, null)
    if (cursor != null && cursor.count != 0) {
        cursor.moveToFirst()
        path = try {
            val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
            cursor.getString(columnIndex)
        } catch (_: IllegalArgumentException) {
            null
        }
    }
    cursor?.close()
    return path ?: FileUtils(this).getPath(uri)
}

fun Uri.authorizedUri(context: Context): Uri = if (this.toString()
        .startsWith("content://")
) this else FileProvider.getUriForFile(
    context,
    BuildConfig.CONTENT_AUTHORITY,
    this.toFile()
)

fun <T: Media> Context.shareMedia(media: T) {
    val originalUri = media.getUri()
    val uri = if (originalUri.toString()
            .startsWith("content://")
    ) originalUri else FileProvider.getUriForFile(
        this,
        BuildConfig.CONTENT_AUTHORITY,
        originalUri.toFile()
    )

    ShareCompat
        .IntentBuilder(this)
        .setType(media.mimeType)
        .addStream(uri)
        .startChooser()
}

fun <T: Media> Context.shareMedia(mediaList: List<T>) {
    val mimeTypes =
        if (mediaList.find { it.duration != null } != null) {
            if (mediaList.find { it.duration == null } != null) "video/*,image/*" else "video/*"
        } else "image/*"

    val shareCompat = ShareCompat
        .IntentBuilder(this)
        .setType(mimeTypes)
    mediaList.forEach {
        shareCompat.addStream(it.getUri())
    }
    shareCompat.startChooser()
}