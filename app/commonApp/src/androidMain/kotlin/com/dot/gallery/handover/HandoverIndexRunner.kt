/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.handover

import android.graphics.Bitmap
import com.dot.gallery.core.injection.Singleton
import com.dot.gallery.core.logging.printWarn
import com.dot.gallery.core.ml.SearchModel
import com.dot.gallery.core.smart.SmartThumbnailLoader
import com.dot.gallery.core.smart.VideoFrameSampler
import com.dot.gallery.core.smart.smartMediaSourceRevision
import com.dot.gallery.domain.model.UriMedia
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

/**
 * Drives the remote SEARCH_INDEX path: prepares each media item's wire
 * payload (JPEG thumbnails / concatenated video frames), submits HPKG
 * envelopes chunked to the server's limits, polls jobs, and reports
 * per-item outcomes back to the caller.
 *
 * Database/lease bookkeeping stays in SearchIndexPhaseProcessor — this
 * runner only knows media → bytes → results.
 */
@Singleton
class HandoverIndexRunner @Inject constructor(
    private val thumbnailLoader: SmartThumbnailLoader,
    private val videoFrameSampler: VideoFrameSampler,
) {
    /** A media item ready for the envelope — [wire] already carries JPEG bytes. */
    class PreparedItem(
        val media: UriMedia,
        val sourceRevision: String,
        val wire: HandoverEnvelope.WireItem,
    )

    /**
     * Decodes + JPEG-encodes one item's prepared media. Videos become
     * concatenated frame JPEGs with byte offsets; an undecodable video track
     * falls back to a poster-frame image (same rule as the local path).
     * Throws on decode failure — caller maps to MediaFeatureStatus.FAILED.
     */
    suspend fun prepareItem(item: UriMedia, model: SearchModel): PreparedItem {
        val isVideo = item.mimeType.startsWith("video/") && model.supportsVideo
        val payload: ByteArray
        val frameOffsets: LongArray?
        if (isVideo) {
            val frames = videoFrameSampler.sampleFrames(item.uri)
            if (frames.isNotEmpty()) {
                try {
                    val out = ByteArrayOutputStream()
                    val offsets = LongArray(frames.size)
                    frames.forEachIndexed { i, frame ->
                        offsets[i] = out.size().toLong()
                        frame.compressJpeg(out)
                    }
                    payload = out.toByteArray()
                    frameOffsets = offsets
                } finally {
                    frames.forEach { it.recycle() }
                }
            } else {
                val poster = thumbnailLoader.load(item, model.indexDecodeSize)
                    ?: error("decode_failed")
                try {
                    payload = ByteArrayOutputStream().also { poster.compressJpeg(it) }.toByteArray()
                    frameOffsets = null
                } finally {
                    poster.recycle()
                }
            }
        } else {
            val bitmap = thumbnailLoader.load(item, model.indexDecodeSize)
                ?: error("decode_failed")
            try {
                payload = ByteArrayOutputStream().also { bitmap.compressJpeg(it) }.toByteArray()
                frameOffsets = null
            } finally {
                bitmap.recycle()
            }
        }
        val source = smartMediaSourceRevision(item)
        return PreparedItem(
            media = item,
            sourceRevision = source,
            wire = HandoverEnvelope.WireItem(
                id = item.id.toString(),
                kind = if (frameOffsets != null) HandoverEnvelope.KIND_VIDEO
                       else HandoverEnvelope.KIND_IMAGE,
                mime = "image/jpeg",
                payload = payload,
                timestamp = item.timestamp,
                sourceRevision = source,
                frameOffsets = frameOffsets,
            ),
        )
    }

    /** Per-item terminal outcome delivered from [submit]. */
    fun interface ItemOutcome {
        /** [vector] is non-null only when the job's sink returns embeddings. */
        suspend fun onResult(item: PreparedItem, vector: FloatArray?, errorCode: String?)
    }

    /**
     * Submits [items] as chunked envelopes and reports every item's outcome
     * through [onItem]. Returns normally when all jobs settle; throws
     * [HandoverException] when the run must abort (caller marks remaining
     * items failed and decides retryable/not from `e.retryable`).
     */
    suspend fun submit(
        client: HandoverClient,
        config: HandoverConfig,
        limits: HandoverInfo.Limits,
        items: List<PreparedItem>,
        onItem: ItemOutcome,
        onProgress: suspend (done: Int) -> Unit,
    ) {
        val byteCap = (limits.maxBatchBytes * 9 / 10).coerceAtMost(MAX_ENVELOPE_BYTES)
        var done = 0
        var i = 0
        while (i < items.size) {
            // One envelope: up to maxBatchItems items AND under the byte cap.
            var bytes = 0
            var j = i
            while (j < items.size && j - i < limits.maxBatchItems &&
                bytes + items[j].wire.payload.size <= byteCap) {
                bytes += items[j].wire.payload.size
                j++
            }
            val chunk = items.subList(i, j)
            val envelope = HandoverEnvelope.write(
                items = chunk.map { it.wire },
                engine = config.engine.ifBlank { null },
                sink = config.sink,
                client = mapOf("app" to "refra-gallery"),
            )
            val submission = client.submit(config, envelope)
            val rejected = submission.rejected.associate { it.id to it.error }
            chunk.forEach { item ->
                rejected[item.wire.id]?.let { onItem.onResult(item, null, it) }
            }
            if (submission.accepted > 0) {
                awaitJob(client, config, submission.jobId)
                applyResults(client, config, submission.jobId, chunk, rejected, onItem)
            }
            done += chunk.size
            onProgress(done)
            i = j
        }
    }

    /** Polls a job to a terminal state; cancels + throws on timeout. */
    private suspend fun awaitJob(
        client: HandoverClient, config: HandoverConfig, jobId: String
    ): JobStatus {
        val deadline = System.currentTimeMillis() + JOB_TIMEOUT_MILLIS
        var pollMs = POLL_START_MILLIS
        while (true) {
            currentCoroutineContext().ensureActive()
            delay(pollMs)
            val status = client.status(config, jobId)
            if (status.isTerminal) return status
            if (System.currentTimeMillis() > deadline) {
                runCatching { client.cancel(config, jobId) }
                    .onFailure {
                        printWarn("handover.index", "best-effort cancel of $jobId failed after timeout")
                    }
                throw HandoverException("job_timeout", "job $jobId timed out", retryable = true)
            }
            pollMs = (pollMs * 2).coerceAtMost(POLL_MAX_MILLIS)
        }
    }

    /** Pages through job results and reports every non-rejected item. */
    private suspend fun applyResults(
        client: HandoverClient,
        config: HandoverConfig,
        jobId: String,
        chunk: List<PreparedItem>,
        rejected: Map<String, String>,
        onItem: ItemOutcome,
    ) {
        val byId = chunk.associateBy { it.wire.id }
        val seen = HashSet<String>()
        var cursor = 0
        while (true) {
            val page = client.results(config, jobId, cursor, RESULTS_PAGE_LIMIT)
            page.items.forEach { result ->
                if (!seen.add(result.id)) return@forEach
                val item = byId[result.id] ?: return@forEach
                when (result.status) {
                    "ok", "indexed" -> {
                        val vector = result.embedding?.decodeWireVector(result.dim)
                        onItem.onResult(item, vector, null)
                    }
                    else -> onItem.onResult(item, null, result.error ?: "item_error")
                }
            }
            cursor = page.nextCursor ?: break
        }
        // Items the server never produced a result for (aborted job mid-flight).
        chunk.forEach { item ->
            if (item.wire.id !in seen && item.wire.id !in rejected) {
                onItem.onResult(item, null, "no_result")
            }
        }
    }

    private fun Bitmap.compressJpeg(out: ByteArrayOutputStream) {
        compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
    }

    private companion object {
        const val JPEG_QUALITY = 85
        const val MAX_ENVELOPE_BYTES = 48 * 1024 * 1024
        const val RESULTS_PAGE_LIMIT = 256
        const val POLL_START_MILLIS = 800L
        const val POLL_MAX_MILLIS = 4_000L
        const val JOB_TIMEOUT_MILLIS = 15 * 60 * 1000L
    }
}

/** Engine-selection logic — pure so host tests can exercise it without the runner. */
internal fun resolveHandoverEngine(
    info: HandoverInfo, model: SearchModel, override: String
): EngineInfo? = when {
    override.isNotBlank() -> info.engines.find { it.id == override }
    else -> info.engines.find { it.appEngine == model.name }
        ?: info.engines.find { it.indexRevisionTag == model.indexRevisionTag }
}
