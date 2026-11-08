package com.dot.gallery.editor

import android.content.Context
import com.dot.gallery.core.work.WorkInput
import com.dot.gallery.core.work.WorkSignals
import com.dot.gallery.core.work.isFinished
import com.dot.gallery.core.work.workData
import com.dot.gallery.core.work.workDataJson
import com.dot.gallery.core.work.workScheduler
import com.dot.gallery.core.workers.EditorExportBridge
import com.dot.gallery.core.workers.EditorExportWorker
import com.dot.gallery.editor.android.AndroidPhotoMediaAccess
import com.dot.gallery.editor.android.AndroidVideoMediaAccess
import com.dot.gallery.editor.android.EditorExportDispatch
import com.dot.gallery.editor.core.model.MediaAsset
import com.dot.gallery.editor.core.model.PhotoDocument
import com.dot.gallery.editor.core.model.PhotoExportFormat
import com.dot.gallery.editor.core.model.PixelSize
import com.dot.gallery.editor.core.model.VideoSequence
import com.dot.gallery.editor.core.model.toEdits
import com.dot.gallery.editor.core.model.toDocument
import com.dot.gallery.editor.core.project.PhotoProjectCodec
import com.dot.gallery.editor.core.project.PhotoProjectSnapshot
import com.dot.gallery.editor.core.project.VideoProjectCodec
import com.dot.gallery.editor.core.project.VideoProjectSnapshot
import com.dot.gallery.editor.core.time.EditorTime
import com.dot.gallery.editor.render.photo.PhotoExportResult
import com.dot.gallery.editor.render.video.VideoExportResult
import dev.brewkits.kmpworkmanager.background.domain.ExistingPolicy
import dev.brewkits.kmpworkmanager.background.domain.TaskTrigger
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject

/**
 * App-side glue for worker-backed editor exports. Installed at startup as
 * both the [EditorExportDispatch] hooks (the sessions' `export` calls land
 * here) and the [EditorExportBridge] the worker invokes. Each export is
 * serialized with the same project codecs the autosave uses, enqueued as a
 * one-shot [EditorExportWorker] that posts the live notification, then
 * replayed back to the session through [WorkSignals] — the
 * publish/progress/receipt contract is unchanged, and a caller cancel
 * cancels the task.
 */
class GalleryEditorExportBridge(context: Context) :
    EditorExportBridge,
    EditorExportDispatch.Photo,
    EditorExportDispatch.Video {

    private val appContext = context.applicationContext
    private val errors = mutableListOf<Throwable>()
    private val onError: (Throwable) -> Unit = { errors += it }

    // ---------------------------------------------------------------
    // Dispatch side: session export() → enqueue worker, await output.
    // ---------------------------------------------------------------

    override suspend fun export(
        document: PhotoDocument,
        format: PhotoExportFormat?,
        onPublished: (PhotoExportResult) -> Unit,
        onProgress: (Float) -> Unit
    ): PhotoExportResult {
        val workName = EditorExportWorker.workName(UUID.randomUUID().toString())
        val input = workDataJson(
            EditorExportWorker.KEY_WORK_NAME to workName,
            EditorExportWorker.KEY_KIND to EditorExportWorker.KIND_PHOTO,
            KEY_ASSET_ID to document.source.id,
            KEY_REFERENCE to document.source.reference,
            KEY_WIDTH to document.source.size.width,
            KEY_HEIGHT to document.source.size.height,
            KEY_FORMAT to format?.name,
            KEY_EDITS_FILE to stageRecipe(
                workName,
                PhotoProjectCodec.encode(PhotoProjectSnapshot(document.toEdits()))
            ),
        )
        val output = enqueueAndAwait(workName, input, onProgress)
        val uri = output.getString(EditorExportWorker.KEY_RESULT_URI)
            ?: throw IOException("photo export produced no result")
        val result = PhotoExportResult(
            reference = uri,
            mimeType = output.getString(EditorExportWorker.KEY_MIME)
                ?: "image/*",
            size = PixelSize(
                output.getInt(KEY_WIDTH, 0),
                output.getInt(KEY_HEIGHT, 0)
            )
        )
        onPublished(result)
        return result
    }

    override suspend fun export(
        sequence: VideoSequence,
        onPublished: (VideoExportResult) -> Unit,
        onProgress: (Float) -> Unit
    ): VideoExportResult {
        val workName = EditorExportWorker.workName(UUID.randomUUID().toString())
        val input = workDataJson(
            EditorExportWorker.KEY_WORK_NAME to workName,
            EditorExportWorker.KEY_KIND to EditorExportWorker.KIND_VIDEO,
            KEY_PROJECT to VideoProjectCodec.encode(
                VideoProjectSnapshot(sequence, null, EditorTime.Zero)
            ),
        )
        val output = enqueueAndAwait(workName, input, onProgress)
        val uri = output.getString(EditorExportWorker.KEY_RESULT_URI)
            ?: throw IOException("video export produced no result")
        val result = VideoExportResult(
            reference = uri,
            mimeType = output.getString(EditorExportWorker.KEY_MIME)
                ?: "video/mp4",
            size = PixelSize(
                output.getInt(KEY_WIDTH, 0),
                output.getInt(KEY_HEIGHT, 0)
            ),
            duration = EditorTime.fromMicroseconds(
                output.getLong(KEY_DURATION_US, 0L)
            )
        )
        onPublished(result)
        return result
    }

    /**
     * Markup strokes and background masks can outgrow a worker's input
     * payload, so the recipe travels as a private file the worker consumes.
     */
    private fun stageRecipe(workName: String, recipe: String): String {
        val directory = File(appContext.noBackupFilesDir, RECIPE_DIR).apply { mkdirs() }
        val file = File(directory, "$workName.json")
        file.writeText(recipe)
        return file.path
    }

    /**
     * Enqueues the export task and replays its signals: progress payloads
     * forward to [onProgress], a terminal success returns its output data,
     * failure/cancel throws. Cancelling the await cancels the task too.
     */
    private suspend fun enqueueAndAwait(
        workName: String,
        inputJson: String,
        onProgress: (Float) -> Unit
    ): WorkInput {
        workScheduler().enqueue(
            id = workName,
            trigger = TaskTrigger.OneTime(),
            workerClassName = EditorExportWorker.WORKER_NAME,
            inputJson = inputJson,
            policy = ExistingPolicy.KEEP,
            tags = setOf(TAG, workName),
        )
        try {
            val info = WorkSignals.observe(workName, workName)
                .first { info ->
                    info.progress.getFloat(KEY_PROGRESS, -1f)
                        .takeIf { it >= 0f }
                        ?.let(onProgress)
                    info.isFinished
                }
            if (info.failed || !info.succeeded) {
                throw IOException(
                    info.output?.getString(EditorExportWorker.KEY_ERROR)
                        ?: "editor export did not finish"
                )
            }
            return info.output ?: throw IOException("export ended with no output")
        } catch (e: CancellationException) {
            runCatching { workScheduler().cancel(workName) }
            throw e
        }
    }

    // ---------------------------------------------------------------
    // Worker side: decode the recipe, run the real export inline.
    // ---------------------------------------------------------------

    override suspend fun export(input: WorkInput, onProgress: (Float) -> Unit): JsonObject =
        when (input.getString(EditorExportWorker.KEY_KIND)) {
            EditorExportWorker.KIND_PHOTO -> runPhoto(input, onProgress)
            EditorExportWorker.KIND_VIDEO -> runVideo(input, onProgress)
            else -> throw IllegalArgumentException("unknown export kind")
        }

    private suspend fun runPhoto(input: WorkInput, onProgress: (Float) -> Unit): JsonObject {
        val reference = input.getString(KEY_REFERENCE)
            ?: throw IllegalArgumentException("missing photo reference")
        val source = MediaAsset.Image(
            id = input.getString(KEY_ASSET_ID) ?: "source",
            reference = reference,
            size = PixelSize(
                input.getInt(KEY_WIDTH, 0),
                input.getInt(KEY_HEIGHT, 0)
            )
        )
        val recipeFile = input.getString(KEY_EDITS_FILE)?.let(::File)
            ?: throw IllegalArgumentException("missing photo recipe")
        val edits = try {
            PhotoProjectCodec.decode(recipeFile.readText()).edits
        } finally {
            if (!recipeFile.delete()) {
                onError(IOException("could not delete the staged photo recipe"))
            }
        }
        val format = input.getString(KEY_FORMAT)
            ?.let { runCatching { PhotoExportFormat.valueOf(it) }.getOrNull() }
        val media = AndroidPhotoMediaAccess(appContext, onError)
        val result = media.exportNow(
            edits.toDocument(source), format,
            onPublished = {},
            onProgress = onProgress
        )
        return workData(
            EditorExportWorker.KEY_RESULT_URI to result.reference,
            EditorExportWorker.KEY_MIME to result.mimeType,
            KEY_WIDTH to result.size.width,
            KEY_HEIGHT to result.size.height,
        )
    }

    private suspend fun runVideo(input: WorkInput, onProgress: (Float) -> Unit): JsonObject {
        val project = input.getString(KEY_PROJECT)
            ?: throw IllegalArgumentException("missing video recipe")
        val sequence = VideoProjectCodec.decode(project).sequence
        val media = AndroidVideoMediaAccess(appContext, onError)
        val result = media.exportNow(
            sequence, onPublished = {}, onProgress = onProgress
        )
        return workData(
            EditorExportWorker.KEY_RESULT_URI to result.reference,
            EditorExportWorker.KEY_MIME to result.mimeType,
            KEY_WIDTH to result.size.width,
            KEY_HEIGHT to result.size.height,
            KEY_DURATION_US to result.duration.toMicrosecondsFloor(),
        )
    }

    companion object {
        const val TAG = "editor-export"
        private const val KEY_ASSET_ID = "asset_id"
        private const val KEY_REFERENCE = "reference"
        private const val KEY_WIDTH = "width"
        private const val KEY_HEIGHT = "height"
        private const val KEY_FORMAT = "format"
        private const val KEY_EDITS_FILE = "editsFile"
        private const val RECIPE_DIR = "editor_export"
        private const val KEY_PROJECT = "project"
        private const val KEY_PROGRESS = "progress"
        private const val KEY_DURATION_US = "duration_us"
    }
}

private fun WorkInput.getStringOrThrow(key: String): String =
    getString(key) ?: throw IllegalArgumentException("missing $key")
