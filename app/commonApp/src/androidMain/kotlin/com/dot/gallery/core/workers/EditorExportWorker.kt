/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.dot.gallery.R
import com.dot.gallery.core.logging.printWarn
import com.dot.gallery.core.work.GalleryWorker
import com.dot.gallery.core.work.GalleryWorkerAdapter
import com.dot.gallery.core.work.WorkComponentHolder
import com.dot.gallery.core.work.WorkInput
import com.dot.gallery.core.work.WorkSignals
import com.dot.gallery.core.work.workData
import dev.brewkits.kmpworkmanager.annotations.Worker
import dev.brewkits.kmpworkmanager.background.domain.WorkerEnvironment
import dev.brewkits.kmpworkmanager.background.domain.WorkerResult
import kotlinx.coroutines.CancellationException
import me.tatarka.inject.annotations.Inject

/**
 * Runs a serialized photo/video editor recipe as a background export with a
 * live progress notification. The recipe is opaque here — the registered
 * [EditorExportBridge] (app module) decodes it and drives the real encoder;
 * this worker owns the notification lifecycle, progress signals and the
 * terminal output payload.
 *
 * Input keys (written by the app-side enqueue): [KEY_KIND] is
 * [KIND_PHOTO]/[KIND_VIDEO], [KEY_LABEL] the source display name,
 * [KEY_MIME] the output MIME for the Open action; the recipe itself rides
 * under keys the bridge defines. Progress/output flow through
 * [WorkSignals] keyed on the unique work name so the caller's session sees
 * the same stream the notification does.
 */
class EditorExportWorker @Inject constructor(
    private val appContext: Context,
) : GalleryWorker {

    override suspend fun doWork(input: WorkInput, env: WorkerEnvironment): WorkerResult {
        val workName = input.getString(KEY_WORK_NAME) ?: WORKER_NAME
        val kind = input.getString(KEY_KIND)
        val video = kind == KIND_VIDEO
        val notifier = EditorExportNotifier(appContext, workName, video)
        val bridge = EditorExportBridgeHolder.bridge
            ?: return failure(workName, "editor export bridge is not installed")
        notifier.started()
        try {
            val output = bridge.export(input) { progress ->
                WorkSignals.reportProgress(
                    workName,
                    workData(KEY_PROGRESS to progress)
                )
                notifier.progress(progress)
            }
            val uri = output[KEY_RESULT_URI]
                ?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
            notifier.succeeded(uri)
            WorkSignals.recordOutput(workName, output)
            return WorkerResult.Success(data = output)
        } catch (error: CancellationException) {
            notifier.cancelled()
            throw error
        } catch (error: Throwable) {
            val message = error.message ?: "editor export failed"
            notifier.failed(message)
            WorkSignals.recordOutput(workName, workData(KEY_ERROR to message))
            return WorkerResult.Failure(message)
        }
    }

    private fun failure(workName: String, message: String): WorkerResult {
        WorkSignals.recordOutput(workName, workData(KEY_ERROR to message))
        return WorkerResult.Failure(message)
    }

    companion object {
        const val WORKER_NAME = "EditorExportWorker"
        const val KEY_WORK_NAME = "work_name"
        const val KEY_KIND = "kind"
        const val KIND_PHOTO = "photo"
        const val KIND_VIDEO = "video"
        const val KEY_LABEL = "label"
        const val KEY_MIME = "mime"
        const val KEY_PROGRESS = "progress"
        const val KEY_RESULT_URI = "result_uri"
        const val KEY_ERROR = "error"

        fun workName(sessionId: String): String = "editor-export-$sessionId"
    }
}

/**
 * The "live" part of the export: one ongoing notification per run —
 * determinate progress while encoding, then a terminal success (with an
 * Open action into the saved copy) or failure. LOW importance while running
 * so progress updates never alert; the terminal notify re-posts on the same
 * id and swaps to the default channel so completion is heard. Skipped
 * entirely without the POST_NOTIFICATIONS grant.
 */
private class EditorExportNotifier(
    private val context: Context,
    workName: String,
    private val video: Boolean,
) {
    private val nm = NotificationManagerCompat.from(context)
    private val id = workName.hashCode()

    private val savingTitle = context.getString(
        if (video) R.string.editor_export_saving_video
        else R.string.editor_export_saving_photo
    )
    private val savedTitle = context.getString(
        if (video) R.string.editor_export_video_saved
        else R.string.editor_export_photo_saved
    )
    private val failedTitle = context.getString(
        if (video) R.string.editor_export_failed_video
        else R.string.editor_export_failed_photo
    )

    private val canNotify: Boolean
        get() = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

    private fun channel(id: String, nameRes: Int, importance: Int): String {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(id) == null) {
            manager.createNotificationChannel(
                NotificationChannel(id, context.getString(nameRes), importance)
            )
        }
        return id
    }

    private fun notify(builder: NotificationCompat.Builder) {
        if (!canNotify) return
        runCatching { nm.notify(id, builder.build()) }
            .onFailure { printWarn("export.notif", "notification post failed", mapOf("error" to it.toString())) }
    }

    fun started() = progress(0f)

    fun progress(fraction: Float) {
        val pct = (fraction.coerceIn(0f, 1f) * 100).toInt()
        notify(
            NotificationCompat.Builder(
                context,
                channel(
                    CHANNEL_PROGRESS, R.string.editor_export_channel_progress,
                    NotificationManager.IMPORTANCE_LOW
                )
            )
                .setSmallIcon(R.drawable.ic_editor_save)
                .setContentTitle(savingTitle)
                .setContentText("$pct%")
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setProgress(100, pct, false)
                .setStyle(
                    NotificationCompat.ProgressStyle()
                        .setProgressIndeterminate(false)
                        .setProgress(pct)
                )
        )
    }

    fun succeeded(uri: String?) {
        val builder = NotificationCompat.Builder(
            context,
            channel(
                CHANNEL_DONE, R.string.editor_export_channel_done,
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
            .setSmallIcon(R.drawable.ic_editor_save)
            .setContentTitle(savedTitle)
            .setContentText(context.getString(R.string.editor_export_tap_to_open))
            .setAutoCancel(true)
            .setOngoing(false)
        if (uri != null) {
            val view = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(
                    Uri.parse(uri),
                    if (video) "video/*" else "image/*"
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            builder.setContentIntent(
                PendingIntent.getActivity(
                    context, id, view,
                    PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE
                )
            )
        }
        notify(builder)
    }

    fun failed(message: String) {
        notify(
            NotificationCompat.Builder(
                context,
                channel(
                    CHANNEL_DONE, R.string.editor_export_channel_done,
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
                .setSmallIcon(R.drawable.ic_editor_save)
                .setContentTitle(failedTitle)
                .setContentText(message)
                .setAutoCancel(true)
                .setOngoing(false)
        )
    }

    fun cancelled() {
        if (canNotify) runCatching { nm.cancel(id) }
    }

    private companion object {
        const val CHANNEL_PROGRESS = "editor_export_progress"
        const val CHANNEL_DONE = "editor_export_done"
    }
}

@Worker(name = EditorExportWorker.WORKER_NAME)
class EditorExportWorkerAdapter : GalleryWorkerAdapter(EditorExportWorker.WORKER_NAME) {
    override suspend fun run(input: WorkInput, env: WorkerEnvironment): WorkerResult =
        WorkComponentHolder.component.editorExportWorker.doWork(input, env)
}
