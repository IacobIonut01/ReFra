package com.dot.gallery.editor.android

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.dot.gallery.editor.core.model.PhotoExportFormat
import com.dot.gallery.editor.core.model.PixelSize
import com.dot.gallery.editor.render.photo.PhotoEditorResult
import com.dot.gallery.editor.render.photo.PhotoExportResult

/**
 * The external contract of [EditorActivity]: an explicit intent carrying a
 * `content://` photo under [ACTION_EDIT], and a typed result —
 * [PhotoEditorResult.Saved] with the new copy's URI, MIME and pixel size;
 * [PhotoEditorResult.Cancelled]; or [PhotoEditorResult.Failed] with a safe
 * message in [EXTRA_ERROR].
 *
 * Saving into `MediaStore.EXTRA_OUTPUT` is not supported yet — callers must
 * consume the returned URI. The result URI is granted back to the caller via
 * the result's data/ClipData with [Intent.FLAG_GRANT_READ_URI_PERMISSION].
 */
object EditorIntents {

    const val ACTION_EDIT = "com.dot.gallery.editor.action.EDIT"

    /** Separate from [Activity.RESULT_CANCELED] so a bad request is never silent. */
    const val RESULT_ERROR = Activity.RESULT_FIRST_USER

    const val EXTRA_ERROR = "com.dot.gallery.editor.extra.ERROR"
    const val EXTRA_OUTPUT_WIDTH = "com.dot.gallery.editor.extra.WIDTH"
    const val EXTRA_OUTPUT_HEIGHT = "com.dot.gallery.editor.extra.HEIGHT"

    /** The containers the exporter can write — source-following or explicit. */
    private val EXPORT_MIME_TYPES = PhotoExportFormat.entries.map { it.mimeType }.toSet()

    fun create(
        context: Context,
        source: Uri,
        activityClass: Class<out EditorActivity> = EditorActivity::class.java
    ): Intent = Intent(context, activityClass)
        .setAction(ACTION_EDIT)
        .setDataAndType(source, "image/*")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        .apply { clipData = ClipData.newRawUri(null, source) }

    fun parseResult(resultCode: Int, data: Intent?): PhotoEditorResult = when (resultCode) {
        Activity.RESULT_OK -> {
            val uri = data?.data
            val mimeType = data?.type
            val width = data?.getIntExtra(EXTRA_OUTPUT_WIDTH, -1) ?: -1
            val height = data?.getIntExtra(EXTRA_OUTPUT_HEIGHT, -1) ?: -1
            // A Saved is only real with a content URI we can grant back, one
            // of the containers the exporter writes and honest dimensions —
            // anything less is a contract violation, not a guess to paper
            // over.
            if (uri != null && uri.scheme == "content" &&
                mimeType != null && mimeType in EXPORT_MIME_TYPES &&
                width > 0 && height > 0
            ) {
                PhotoEditorResult.Saved(
                    PhotoExportResult(
                        reference = uri.toString(),
                        mimeType = mimeType,
                        size = PixelSize(width, height)
                    )
                )
            } else {
                PhotoEditorResult.Failed("Editor finished without a saved copy")
            }
        }
        Activity.RESULT_CANCELED -> PhotoEditorResult.Cancelled
        RESULT_ERROR -> PhotoEditorResult.Failed(
            data?.getStringExtra(EXTRA_ERROR) ?: "Photo editor failed"
        )
        else -> PhotoEditorResult.Failed("Photo editor returned an unknown result")
    }

    internal fun savedResult(result: PhotoExportResult): Intent {
        val uri = Uri.parse(result.reference)
        return Intent()
            .setDataAndType(uri, result.mimeType)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .putExtra(EXTRA_OUTPUT_WIDTH, result.size.width)
            .putExtra(EXTRA_OUTPUT_HEIGHT, result.size.height)
            .apply { clipData = ClipData.newRawUri(null, uri) }
    }

    internal fun errorResult(message: String): Intent =
        Intent().putExtra(EXTRA_ERROR, message)
}
