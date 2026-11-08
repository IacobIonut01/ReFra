package com.dot.gallery.editor.android

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.dot.gallery.editor.core.model.PixelSize
import com.dot.gallery.editor.core.time.EditorTime
import com.dot.gallery.editor.render.video.VideoEditorResult
import com.dot.gallery.editor.render.video.VideoExportResult

/**
 * The external contract of [VideoEditorActivity]: an explicit intent
 * carrying a `content://` video under [ACTION_EDIT], and a typed result —
 * [VideoEditorResult.Saved] with the new copy's URI, MIME, pixel size and
 * duration; [VideoEditorResult.Cancelled]; or [VideoEditorResult.Failed]
 * with a safe message in [EXTRA_ERROR].
 *
 * Saving into `MediaStore.EXTRA_OUTPUT` is not supported yet — callers must
 * consume the returned URI. The result URI is granted back to the caller
 * via the result's data/ClipData with [Intent.FLAG_GRANT_READ_URI_PERMISSION].
 */
object VideoEditorIntents {

    const val ACTION_EDIT = "com.dot.gallery.editor.action.EDIT_VIDEO"

    /** Separate from [Activity.RESULT_CANCELED] so a bad request is never silent. */
    const val RESULT_ERROR = Activity.RESULT_FIRST_USER

    const val EXTRA_ERROR = "com.dot.gallery.editor.extra.ERROR"
    const val EXTRA_OUTPUT_WIDTH = "com.dot.gallery.editor.extra.WIDTH"
    const val EXTRA_OUTPUT_HEIGHT = "com.dot.gallery.editor.extra.HEIGHT"
    const val EXTRA_DURATION_TICKS = "com.dot.gallery.editor.extra.DURATION_TICKS"

    fun create(
        context: Context,
        source: Uri,
        activityClass: Class<out VideoEditorActivity> = VideoEditorActivity::class.java
    ): Intent = Intent(context, activityClass)
        .setAction(ACTION_EDIT)
        .setDataAndType(source, "video/*")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        .apply { clipData = ClipData.newRawUri(null, source) }

    fun parseResult(resultCode: Int, data: Intent?): VideoEditorResult =
        when (resultCode) {
            Activity.RESULT_OK -> {
                val uri = data?.data
                val mimeType = data?.type
                val width = data?.getIntExtra(EXTRA_OUTPUT_WIDTH, -1) ?: -1
                val height = data?.getIntExtra(EXTRA_OUTPUT_HEIGHT, -1) ?: -1
                val durationTicks =
                    data?.getLongExtra(EXTRA_DURATION_TICKS, -1L) ?: -1L
                // A Saved is only real with a content URI we can grant back,
                // the MP4 MIME the exporter writes, and honest size/duration
                // — anything less is a contract violation, not a guess.
                if (uri != null && uri.scheme == "content" &&
                    mimeType == "video/mp4" && width > 0 && height > 0 &&
                    durationTicks > 0L
                ) {
                    VideoEditorResult.Saved(
                        VideoExportResult(
                            reference = uri.toString(),
                            mimeType = mimeType,
                            size = PixelSize(width, height),
                            duration = EditorTime(durationTicks)
                        )
                    )
                } else {
                    VideoEditorResult.Failed(
                        "Editor finished without a saved copy"
                    )
                }
            }
            Activity.RESULT_CANCELED -> VideoEditorResult.Cancelled
            RESULT_ERROR -> VideoEditorResult.Failed(
                data?.getStringExtra(EXTRA_ERROR) ?: "Video editor failed"
            )
            else -> VideoEditorResult.Failed(
                "Video editor returned an unknown result"
            )
        }

    internal fun savedResult(result: VideoExportResult): Intent {
        val uri = Uri.parse(result.reference)
        return Intent()
            .setDataAndType(uri, result.mimeType)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .putExtra(EXTRA_OUTPUT_WIDTH, result.size.width)
            .putExtra(EXTRA_OUTPUT_HEIGHT, result.size.height)
            .putExtra(EXTRA_DURATION_TICKS, result.duration.ticks)
            .apply { clipData = ClipData.newRawUri(null, uri) }
    }

    internal fun errorResult(message: String): Intent =
        Intent().putExtra(EXTRA_ERROR, message)
}
