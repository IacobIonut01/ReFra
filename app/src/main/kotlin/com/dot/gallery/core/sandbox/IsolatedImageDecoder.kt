/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.sandbox

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.SharedMemory
import com.dot.gallery.core.sandbox.IsolatedDecoderService.Companion.KEY_BYTE_COUNT
import com.dot.gallery.core.sandbox.IsolatedDecoderService.Companion.KEY_DECODED_BYTE_COUNT
import com.dot.gallery.core.sandbox.IsolatedDecoderService.Companion.KEY_DECODED_CONFIG
import com.dot.gallery.core.sandbox.IsolatedDecoderService.Companion.KEY_DECODED_HEIGHT
import com.dot.gallery.core.sandbox.IsolatedDecoderService.Companion.KEY_DECODED_WIDTH
import com.dot.gallery.core.sandbox.IsolatedDecoderService.Companion.KEY_ERROR
import com.dot.gallery.core.sandbox.IsolatedDecoderService.Companion.KEY_ERROR_MESSAGE
import com.dot.gallery.core.sandbox.IsolatedDecoderService.Companion.KEY_INPUT_SHM
import com.dot.gallery.core.sandbox.IsolatedDecoderService.Companion.KEY_MIME_TYPE
import com.dot.gallery.core.sandbox.IsolatedDecoderService.Companion.KEY_OUTPUT_SHM
import com.dot.gallery.core.sandbox.IsolatedDecoderService.Companion.KEY_TARGET_HEIGHT
import com.dot.gallery.core.sandbox.IsolatedDecoderService.Companion.KEY_TARGET_WIDTH
import com.dot.gallery.core.sandbox.IsolatedDecoderService.Companion.MSG_DECODE
import com.dot.gallery.feature_node.presentation.util.printDebug
import com.dot.gallery.feature_node.presentation.util.printWarning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.ByteBuffer
import kotlin.coroutines.resume

/**
 * Client for [IsolatedDecoderService].
 *
 * Sends encoded image bytes via [SharedMemory] to the isolated service,
 * which decodes them and returns the decoded bitmap pixels via [SharedMemory].
 * This provides zero-copy IPC for large image buffers.
 *
 * Usage:
 * ```
 * val decoder = IsolatedImageDecoder(context)
 * val bitmap = decoder.decode(encodedBytes, "image/heif", targetWidth, targetHeight)
 * decoder.unbind()
 * ```
 */
class IsolatedImageDecoder(private val context: Context) {

    @Volatile
    private var serviceMessenger: Messenger? = null

    @Volatile
    private var bound = false

    private val bindMutex = Mutex()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            serviceMessenger = Messenger(binder)
            bound = true
            printDebug("IsolatedImageDecoder: service connected")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serviceMessenger = null
            bound = false
            printWarning("IsolatedImageDecoder: service disconnected")
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────

    suspend fun ensureBound() {
        if (bound && serviceMessenger != null) return
        bindMutex.withLock {
            if (bound && serviceMessenger != null) return
            withContext(Dispatchers.Main) {
                val intent = Intent(context, IsolatedDecoderService::class.java)
                context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            }
            var attempts = 0
            while (serviceMessenger == null && attempts < 50) {
                kotlinx.coroutines.delay(100)
                attempts++
            }
            if (serviceMessenger == null) {
                printWarning("IsolatedImageDecoder: bind timed out after 5s")
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

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Decode an image in the isolated process.
     *
     * @param encodedBytes The raw encoded image data (HEIF/AVIF/JXL)
     * @param mimeType     The MIME type of the image
     * @param targetWidth  Desired output width (0 = native)
     * @param targetHeight Desired output height (0 = native)
     * @return Decoded [Bitmap] or null on failure
     */
    suspend fun decode(
        encodedBytes: ByteArray,
        mimeType: String,
        targetWidth: Int = 0,
        targetHeight: Int = 0
    ): Bitmap? = withContext(Dispatchers.IO) {
        val startNs = System.nanoTime()
        ensureBound()
        val messenger = serviceMessenger ?: return@withContext null

        // Write encoded bytes to shared memory
        val inputShm = try {
            SharedMemory.create("encoded_input", encodedBytes.size)
        } catch (e: Exception) {
            printWarning("IsolatedImageDecoder: failed to create SharedMemory: ${e.message}")
            return@withContext null
        }
        try {
            val inputBuffer = inputShm.mapReadWrite()
            inputBuffer.put(encodedBytes)
            SharedMemory.unmap(inputBuffer)
        } catch (e: Exception) {
            inputShm.close()
            printWarning("IsolatedImageDecoder: failed to write SharedMemory: ${e.message}")
            return@withContext null
        }

        val resultBundle = sendAndReceive(messenger, MSG_DECODE, Bundle().apply {
            putParcelable(KEY_INPUT_SHM, inputShm)
            putString(KEY_MIME_TYPE, mimeType)
            putInt(KEY_TARGET_WIDTH, targetWidth)
            putInt(KEY_TARGET_HEIGHT, targetHeight)
            putInt(KEY_BYTE_COUNT, encodedBytes.size)
        })
        inputShm.close()

        if (resultBundle == null || resultBundle.getBoolean(KEY_ERROR, false)) {
            val errorMsg = resultBundle?.getString(KEY_ERROR_MESSAGE, "Unknown error") ?: "Timeout"
            printWarning("IsolatedImageDecoder: decode failed: $errorMsg")
            return@withContext null
        }

        // Read decoded pixels from shared memory
        val bitmap = readBitmapFromResult(resultBundle)

        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000
        printDebug("IsolatedImageDecoder: decode took ${elapsedMs}ms (${bitmap?.width}x${bitmap?.height})")
        bitmap
    }

    @Suppress("DEPRECATION")
    private fun readBitmapFromResult(bundle: Bundle): Bitmap? {
        bundle.classLoader = SharedMemory::class.java.classLoader
        val outputShm = bundle.getParcelable<SharedMemory>(KEY_OUTPUT_SHM) ?: return null
        val width = bundle.getInt(KEY_DECODED_WIDTH, 0)
        val height = bundle.getInt(KEY_DECODED_HEIGHT, 0)
        val byteCount = bundle.getInt(KEY_DECODED_BYTE_COUNT, 0)
        val configName = bundle.getString(KEY_DECODED_CONFIG, "ARGB_8888")

        if (width <= 0 || height <= 0 || byteCount <= 0) {
            outputShm.close()
            return null
        }

        return try {
            val config = try {
                Bitmap.Config.valueOf(configName)
            } catch (_: Exception) {
                Bitmap.Config.ARGB_8888
            }
            val bitmap = createBitmap(width, height, config)
            val outputBuffer = outputShm.mapReadOnly()
            bitmap.copyPixelsFromBuffer(outputBuffer)
            SharedMemory.unmap(outputBuffer)
            bitmap
        } catch (e: Exception) {
            printWarning("IsolatedImageDecoder: failed to reconstruct bitmap: ${e.message}")
            null
        } finally {
            outputShm.close()
        }
    }

    // ── IPC internals ─────────────────────────────────────────────────────

    private suspend fun sendAndReceive(messenger: Messenger, what: Int, data: Bundle): Bundle? {
        return withTimeoutOrNull(SERVICE_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val replyHandler = Handler(Looper.getMainLooper()) { msg ->
                    val result = msg.data
                    result.classLoader = SharedMemory::class.java.classLoader
                    if (cont.isActive) cont.resume(result)
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
                    printWarning("IsolatedImageDecoder: send failed: ${e.message}")
                    if (cont.isActive) cont.resume(null)
                }

                cont.invokeOnCancellation { }
            }
        }
    }

    companion object {
        private const val SERVICE_TIMEOUT_MS = 30_000L
    }
}
