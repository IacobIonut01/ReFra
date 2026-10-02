/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.sandbox

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import com.dot.gallery.core.decoder.format.SpecialFormatProbe
import com.dot.gallery.core.sandbox.IsolatedMetadataService.Companion.KEY_ERROR
import com.dot.gallery.core.sandbox.IsolatedMetadataService.Companion.KEY_IS_VIDEO
import com.dot.gallery.core.sandbox.IsolatedMetadataService.Companion.KEY_LABEL
import com.dot.gallery.core.sandbox.IsolatedMetadataService.Companion.KEY_PFD
import com.dot.gallery.core.sandbox.IsolatedMetadataService.Companion.KEY_RAW_DIR_NAMES
import com.dot.gallery.core.sandbox.IsolatedMetadataService.Companion.MSG_PARSE_IMAGE
import com.dot.gallery.core.sandbox.IsolatedMetadataService.Companion.MSG_PARSE_RAW_METADATA
import com.dot.gallery.core.sandbox.IsolatedMetadataService.Companion.MSG_PARSE_VIDEO
import com.dot.gallery.core.util.openUnredactedFileDescriptor
import com.dot.gallery.feature_node.presentation.exif.MetadataDirectory
import com.dot.gallery.feature_node.presentation.exif.MetadataTag
import com.dot.gallery.feature_node.presentation.util.printDebug
import com.dot.gallery.feature_node.presentation.util.printWarning
import java.util.concurrent.Executors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Client for [IsolatedMetadataService].
 *
 * Binds to the isolated-process service on first use, keeps the connection alive
 * for reuse, and falls back to in-process parsing if the service is unavailable
 * or times out.
 *
 */
class IsolatedMetadataParser(private val context: Context) {

    @Volatile
    private var serviceMessenger: Messenger? = null

    @Volatile
    private var bound = false

    private val bindMutex = Mutex()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            serviceMessenger = Messenger(binder)
            bound = true
            printDebug("IsolatedMetadataParser: service connected")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serviceMessenger = null
            bound = false
            printWarning("IsolatedMetadataParser: service disconnected")
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────

    suspend fun ensureBound() {
        if (bound && serviceMessenger != null) return
        bindMutex.withLock {
            if (bound && serviceMessenger != null) return
            withContext(Dispatchers.Main) {
                val intent = Intent(context, IsolatedMetadataService::class.java)
                context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            }
            // Wait for connection (up to 5s)
            var attempts = 0
            while (serviceMessenger == null && attempts < 50) {
                kotlinx.coroutines.delay(100)
                attempts++
            }
            if (serviceMessenger == null) {
                printWarning("IsolatedMetadataParser: bind timed out after 5s")
            }
        }
    }

    fun unbind() {
        if (bound) {
            runCatching { context.unbindService(connection) }
            bound = false
            serviceMessenger = null
        }
    }

    // ── Per-file isolation (API 29+) ──────────────────────────────────────

    private val perFileExecutor = Executors.newSingleThreadExecutor()

    /**
     * Bind a fresh per-file isolated instance.
     * Each unique [instanceName] forks a new Zygote child process via
     * [Context.bindIsolatedService]. Call [unbindPerFile] when done.
     *
     * Returns null if the bind fails or times out.
     */
    suspend fun bindPerFile(mediaId: Long): PerFileConnection? {
        val instanceName = "metadata_$mediaId"
        val intent = Intent(context, IsolatedMetadataService::class.java)
        var perFileMessenger: Messenger? = null

        val perFileConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                perFileMessenger = Messenger(binder)
                printDebug("IsolatedMetadataParser: per-file service connected ($instanceName)")
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                perFileMessenger = null
                printWarning("IsolatedMetadataParser: per-file service disconnected ($instanceName)")
            }
        }

        val bindResult = withContext(Dispatchers.Main) {
            runCatching {
                context.bindIsolatedService(
                    intent,
                    Context.BIND_AUTO_CREATE,
                    instanceName,
                    perFileExecutor,
                    perFileConnection
                )
            }.getOrElse { e ->
                printWarning("IsolatedMetadataParser: bindIsolatedService failed: ${e.message}")
                false
            }
        }

        if (!bindResult) return null

        // Wait for connection (up to 5s)
        var attempts = 0
        while (perFileMessenger == null && attempts < 50) {
            kotlinx.coroutines.delay(100)
            attempts++
        }

        val messenger = perFileMessenger
        if (messenger == null) {
            printWarning("IsolatedMetadataParser: per-file bind timed out ($instanceName)")
            runCatching { context.unbindService(perFileConnection) }
            return null
        }

        return PerFileConnection(
            serviceConnection = perFileConnection,
            messenger = messenger,
            instanceName = instanceName
        )
    }

    fun unbindPerFile(conn: PerFileConnection) {
        runCatching { context.unbindService(conn.serviceConnection) }
        printDebug("IsolatedMetadataParser: per-file service unbound (${conn.instanceName})")
    }

    // ── Per-file public API ───────────────────────────────────────────────

    /**
     * Parse image metadata using a per-file isolated instance.
     * Falls back to shared instance on failure.
     */
    suspend fun parseImageMetadataPerFile(
        uri: Uri,
        label: String,
        mediaId: Long,
        fallbackToShared: Boolean = true
    ): Bundle? = withContext(Dispatchers.IO) {
        val startNs = System.nanoTime()
        val conn = bindPerFile(mediaId)
        if (conn == null) {
            if (!fallbackToShared) return@withContext null
            printWarning("IsolatedMetadataParser: per-file bind failed, falling back to shared")
            return@withContext parseImageMetadata(uri, label)
        }
        try {
            val pfd = context.openUnredactedFileDescriptor(uri) ?: return@withContext null

            pfd.use {
                val result = sendAndReceive(conn.messenger, MSG_PARSE_IMAGE, Bundle().apply {
                    putParcelable(KEY_PFD, it)
                    putString(KEY_LABEL, label)
                })
                val elapsedMs = (System.nanoTime() - startNs) / 1_000_000
                printDebug("IsolatedMetadataParser: image parse took ${elapsedMs}ms (per-file: ${conn.instanceName})")
                result
            }
        } finally {
            unbindPerFile(conn)
        }
    }

    /**
     * Parse video metadata using a per-file isolated instance.
     * Falls back to shared instance on failure.
     */
    suspend fun parseVideoMetadataPerFile(
        uri: Uri,
        mediaId: Long,
        fallbackToShared: Boolean = true
    ): Bundle? = withContext(Dispatchers.IO) {
        val startNs = System.nanoTime()
        val conn = bindPerFile(mediaId)
        if (conn == null) {
            if (!fallbackToShared) return@withContext null
            printWarning("IsolatedMetadataParser: per-file bind failed, falling back to shared")
            return@withContext parseVideoMetadata(uri)
        }
        try {
            val pfd = context.openUnredactedFileDescriptor(uri) ?: return@withContext null

            pfd.use {
                val result = sendAndReceive(conn.messenger, MSG_PARSE_VIDEO, Bundle().apply {
                    putParcelable(KEY_PFD, it)
                })
                val elapsedMs = (System.nanoTime() - startNs) / 1_000_000
                printDebug("IsolatedMetadataParser: video parse took ${elapsedMs}ms (per-file: ${conn.instanceName})")
                result
            }
        } finally {
            unbindPerFile(conn)
        }
    }

    /**
     * Parse raw metadata using a per-file isolated instance.
     * Falls back to shared instance on failure.
     */
    suspend fun parseRawMetadataPerFile(
        uri: Uri,
        isVideo: Boolean,
        mediaId: Long
    ): List<MetadataDirectory> = withContext(Dispatchers.IO) {
        val startNs = System.nanoTime()
        val conn = bindPerFile(mediaId)
        if (conn == null) {
            printWarning("IsolatedMetadataParser: per-file bind failed, falling back to shared")
            return@withContext parseRawMetadata(uri, isVideo)
        }
        try {
            val pfd = context.openUnredactedFileDescriptor(uri) ?: return@withContext emptyList()

            pfd.use {
                val bundle = sendAndReceive(conn.messenger, MSG_PARSE_RAW_METADATA, Bundle().apply {
                    putParcelable(KEY_PFD, it)
                    putBoolean(KEY_IS_VIDEO, isVideo)
                }) ?: return@use augmentWithProbeSize(uri, isVideo, emptyList())

                val result = augmentWithProbeSize(uri, isVideo, unbundleRawMetadata(bundle))
                val elapsedMs = (System.nanoTime() - startNs) / 1_000_000
                printDebug("IsolatedMetadataParser: raw metadata parse took ${elapsedMs}ms (per-file: ${conn.instanceName})")
                result
            }
        } finally {
            unbindPerFile(conn)
        }
    }

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Parse image metadata via the isolated service.
     * Returns a [Bundle] with EXIF/XMP/GPS fields, or null on failure.
     */
    suspend fun parseImageMetadata(
        uri: Uri,
        label: String
    ): Bundle? = withContext(Dispatchers.IO) {
        val startNs = System.nanoTime()
        ensureBound()
        val pfd = context.openUnredactedFileDescriptor(uri) ?: return@withContext null

        pfd.use {
            val result = sendAndReceive(MSG_PARSE_IMAGE, Bundle().apply {
                putParcelable(KEY_PFD, it)
                putString(KEY_LABEL, label)
            })

            val elapsedMs = (System.nanoTime() - startNs) / 1_000_000
            printDebug("IsolatedMetadataParser: image parse took ${elapsedMs}ms (isolated)")
            result
        }
    }

    /**
     * Parse video metadata via the isolated service.
     * Returns a [Bundle] with video fields, or null on failure.
     */
    suspend fun parseVideoMetadata(uri: Uri): Bundle? = withContext(Dispatchers.IO) {
        val startNs = System.nanoTime()
        ensureBound()
        val pfd = context.openUnredactedFileDescriptor(uri) ?: return@withContext null

        pfd.use {
            val result = sendAndReceive(MSG_PARSE_VIDEO, Bundle().apply {
                putParcelable(KEY_PFD, it)
            })

            val elapsedMs = (System.nanoTime() - startNs) / 1_000_000
            printDebug("IsolatedMetadataParser: video parse took ${elapsedMs}ms (isolated)")
            result
        }
    }

    /**
     * Parse raw metadata for the "View all metadata" screen.
     * Returns a list of [MetadataDirectory] or empty list on failure.
     */
    suspend fun parseRawMetadata(uri: Uri, isVideo: Boolean): List<MetadataDirectory> =
        withContext(Dispatchers.IO) {
            val startNs = System.nanoTime()
            ensureBound()
            val pfd = context.openUnredactedFileDescriptor(uri) ?: return@withContext emptyList()

            pfd.use {
                val bundle = sendAndReceive(MSG_PARSE_RAW_METADATA, Bundle().apply {
                    putParcelable(KEY_PFD, it)
                    putBoolean(KEY_IS_VIDEO, isVideo)
                }) ?: return@use augmentWithProbeSize(uri, isVideo, emptyList())

                val result = augmentWithProbeSize(uri, isVideo, unbundleRawMetadata(bundle))
                val elapsedMs = (System.nanoTime() - startNs) / 1_000_000
                printDebug("IsolatedMetadataParser: raw metadata parse took ${elapsedMs}ms (isolated)")
                result
            }
        }

    // ── IPC internals ─────────────────────────────────────────────────────

    private suspend fun sendAndReceive(what: Int, data: Bundle): Bundle? {
        val messenger = serviceMessenger ?: return null
        return sendAndReceive(messenger, what, data)
    }

    private suspend fun sendAndReceive(messenger: Messenger, what: Int, data: Bundle): Bundle? {
        return withTimeoutOrNull(SERVICE_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val replyHandler = Handler(Looper.getMainLooper()) { msg ->
                    val result = msg.data
                    if (result.getBoolean(KEY_ERROR, false)) {
                        val errorMsg = result.getString(
                            IsolatedMetadataService.Companion.KEY_ERROR_MESSAGE,
                            "Unknown error"
                        )
                        printWarning("IsolatedMetadataParser: service error: $errorMsg")
                        if (cont.isActive) cont.resume(null)
                    } else {
                        if (cont.isActive) cont.resume(result)
                    }
                    true
                }

                val msg = Message.obtain().apply {
                    this.what = what
                    this.data = data
                    this.replyTo = Messenger(replyHandler)
                }

                try {
                    messenger.send(msg)
                } catch (e: Exception) {
                    printWarning("IsolatedMetadataParser: send failed: ${e.message}")
                    if (cont.isActive) cont.resume(null)
                }

                cont.invokeOnCancellation {
                    // Nothing to clean up; the service will finish and the reply is ignored
                }
            }
        }
    }

    /**
     * Ensures the "View all metadata" list shows resolution for special image formats (JXL, JP2,
     * PSD, SVG, HEIF/AVIF/TIFF). metadata-extractor omits or can't read their dimensions, so probe
     * the intrinsic size in-process (the same native decoders used for rendering) and prepend a
     * synthetic directory when no width/height tag is already present.
     *
     * For videos, an entirely empty reply is rebuilt with [probeVideoDirectories] — the
     * service-side retriever can take the isolated process down before metadata-extractor
     * runs (Android 12, #1267), which otherwise leaves the screen blank.
     */
    private fun augmentWithProbeSize(
        uri: Uri,
        isVideo: Boolean,
        directories: List<MetadataDirectory>
    ): List<MetadataDirectory> {
        if (isVideo) return directories.ifEmpty { probeVideoDirectories(uri) }
        val hasDimensions = directories.any { dir ->
            dir.tags.any { it.name.contains("width", ignoreCase = true) }
        }
        if (hasDimensions) return directories
        val size = SpecialFormatProbe.getSize(context, uri) ?: return directories
        val synthetic = MetadataDirectory(
            name = "Image",
            tags = listOf(
                MetadataTag("Image Width", "${size.width} pixels"),
                MetadataTag("Image Height", "${size.height} pixels"),
            )
        )
        return listOf(synthetic) + directories
    }

    /**
     * Rebuilds the "Video Metadata" directory in-process when the isolated reply came
     * back empty. Mirrors the service-side tag set ([IsolatedMetadataService.VIDEO_TAG_MAP]);
     * the retriever only forwards parsing to the platform's mediaextractor daemon, so this
     * does not move untrusted parsing into the app process.
     */
    private fun probeVideoDirectories(uri: Uri): List<MetadataDirectory> =
        runCatching {
            context.openUnredactedFileDescriptor(uri)?.use { pfd ->
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(pfd.fileDescriptor)
                    val tagNames = ArrayList<String>()
                    val tagDescs = ArrayList<String>()
                    for ((key, name) in IsolatedMetadataService.VIDEO_TAG_MAP) {
                        retriever.extractMetadata(key)?.let { value ->
                            tagNames.add(name)
                            tagDescs.add(value)
                        }
                    }
                    if (tagNames.isEmpty()) emptyList() else listOf(
                        MetadataDirectory(
                            name = "Video Metadata",
                            tags = tagNames.zip(tagDescs).map { (n, d) -> MetadataTag(n, d) }
                        )
                    )
                } finally {
                    retriever.release()
                }
            } ?: emptyList()
        }.getOrElse {
            if (it is CancellationException) throw it
            printWarning("IsolatedMetadataParser: in-process video probe failed: ${it.message}")
            emptyList()
        }

    private fun unbundleRawMetadata(bundle: Bundle): List<MetadataDirectory> {
        val dirNames = bundle.getStringArrayList(KEY_RAW_DIR_NAMES) ?: return emptyList()
        return dirNames.mapIndexedNotNull { index, name ->
            val dirKey = "dir_$index"
            val tagNames = bundle.getStringArrayList("${dirKey}_names") ?: return@mapIndexedNotNull null
            val tagDescs = bundle.getStringArrayList("${dirKey}_descs") ?: return@mapIndexedNotNull null
            MetadataDirectory(
                name = name,
                tags = tagNames.zip(tagDescs).map { (n, d) -> MetadataTag(n, d) }
            )
        }
    }

    companion object {
        private const val SERVICE_TIMEOUT_MS = 30_000L
    }
}
