package com.dot.gallery.editor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.graphics.get
import com.dot.gallery.core.AppIdentity
import com.dot.gallery.core.logging.printError
import com.dot.gallery.core.ml.CutoutHelper
import com.dot.gallery.core.ml.FaceHelper
import com.dot.gallery.core.ml.ModelGroup
import com.dot.gallery.core.ml.ModelManager
import com.dot.gallery.editor.core.model.PhotoCutoutMatte
import com.dot.gallery.editor.render.SrgbPixels
import com.dot.gallery.editor.ui.EditorFaceBox
import com.dot.gallery.editor.ui.EditorHostTools
import com.dot.gallery.editor.ui.EditorSegmentPoint
import com.dot.gallery.editor.ui.EditorSegmenterSession
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "editor.photo"
private const val SUBJECT_DIR = "editor_subject"

/**
 * The Gallery's on-device models and sharing behind the editor's host seam.
 * Cutout tools and Blur faces appear only once their model groups are
 * installed, matching how the old editor gates the same tools.
 */
internal fun galleryEditorHostTools(
    context: Context,
    openInExternalEditor: () -> Unit
): EditorHostTools {
    val appContext = context.applicationContext
    val models = ModelManager(appContext)
    return EditorHostTools(
        openSegmenter = if (models.isReady(ModelGroup.CUTOUT)) {
            { pixels -> openSegmenter(appContext, models, pixels) }
        } else {
            null
        },
        detectFaces = if (models.isReady(ModelGroup.FACE_DETECT)) {
            { pixels -> detectFaces(models, pixels) }
        } else {
            null
        },
        copySubject = { pixels -> copySubject(appContext, pixels) },
        shareSubject = { pixels -> shareSubject(appContext, pixels) },
        openInExternalEditor = openInExternalEditor
    )
}

private fun SrgbPixels.toArgbBitmap(): Bitmap =
    Bitmap.createBitmap(copyPixels(), size.width, size.height, Bitmap.Config.ARGB_8888)

private suspend fun openSegmenter(
    context: Context,
    models: ModelManager,
    pixels: SrgbPixels
): EditorSegmenterSession? = withContext(Dispatchers.Default) {
    val session = CutoutHelper.CutoutSession(context, null, models)
    val bitmap = pixels.toArgbBitmap()
    if (!session.initAndRunEncoder(bitmap)) {
        session.close()
        printError(TAG, "Cutout encoder could not start")
        return@withContext null
    }
    GallerySegmenterSession(session)
}

private class GallerySegmenterSession(
    private val session: CutoutHelper.CutoutSession
) : EditorSegmenterSession {

    override suspend fun segment(points: List<EditorSegmentPoint>): PhotoCutoutMatte? =
        withContext(Dispatchers.Default) {
            val width = session.widthOrig
            val height = session.heightOrig
            val prompts = points.map {
                CutoutHelper.PromptPoint(it.x * width, it.y * height, it.include)
            }
            val result = session.runDecoder(prompts) ?: return@withContext null
            try {
                // The decoder returns the subject cropped to its bounds.
                val alpha = ByteArray(width * height)
                val bounds = result.originalBounds
                for (y in 0 until result.bitmap.height) {
                    for (x in 0 until result.bitmap.width) {
                        val value = result.bitmap[x, y] ushr 24
                        val targetX = bounds.left + x
                        val targetY = bounds.top + y
                        if (targetX in 0 until width && targetY in 0 until height) {
                            alpha[targetY * width + targetX] = value.toByte()
                        }
                    }
                }
                PhotoCutoutMatte(width, height, alpha)
            } finally {
                result.bitmap.recycle()
            }
        }

    override fun close() = session.close()
}

private suspend fun detectFaces(
    models: ModelManager,
    pixels: SrgbPixels
): List<EditorFaceBox> = withContext(Dispatchers.Default) {
    val helper = FaceHelper(models)
    try {
        helper.detect(pixels.toArgbBitmap()).map {
            EditorFaceBox(it.left, it.top, it.right, it.bottom)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        printError(TAG, "Face detection failed", e)
        emptyList()
    } finally {
        helper.close()
    }
}

/** Writes the subject as a PNG into the shareable cache and returns its Uri. */
private suspend fun writeSubject(context: Context, pixels: SrgbPixels): Uri? =
    withContext(Dispatchers.IO) {
        try {
            val directory = File(context.cacheDir, SUBJECT_DIR).apply { mkdirs() }
            val file = File(directory, "subject-${System.currentTimeMillis()}.png")
            file.outputStream().use { stream ->
                check(pixels.toArgbBitmap().compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                    "PNG encoder refused the subject"
                }
            }
            FileProvider.getUriForFile(context, AppIdentity.contentAuthority, file)
        } catch (e: IOException) {
            printError(TAG, "Could not write the subject", e)
            null
        } catch (e: IllegalStateException) {
            printError(TAG, "Could not write the subject", e)
            null
        }
    }

private suspend fun copySubject(context: Context, pixels: SrgbPixels): Boolean {
    val uri = writeSubject(context, pixels) ?: return false
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return false
    clipboard.setPrimaryClip(ClipData.newUri(context.contentResolver, "Subject", uri))
    return true
}

private suspend fun shareSubject(context: Context, pixels: SrgbPixels): Boolean {
    val uri = writeSubject(context, pixels) ?: return false
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(
        Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
    return true
}
