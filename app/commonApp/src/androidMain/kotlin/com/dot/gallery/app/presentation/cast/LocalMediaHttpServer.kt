/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.cast

import android.content.Context
import android.net.Uri
import fi.iki.elonen.NanoHTTPD
import com.dot.gallery.core.logging.printWarning
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap


/**
 * Lightweight HTTP server that serves local media files to the connected FCast receiver.
 *
 * Each item is served at /media/{token} with a random 128-bit token. When [receiverAddress]
 * is set, only that address may fetch media. Only the most recently registered item is kept; registering a new
 * item or stopping the server releases the previous one and deletes any owned temp file.
 */
class LocalMediaHttpServer(
    private val context: Context,
    private val receiverAddress: InetAddress?,
    port: Int = 0 // 0 = auto-assign
) : NanoHTTPD(port) {

    private data class MediaEntry(
        val uri: Uri?,
        val file: File?,
        val mimeType: String,
        val size: Long,
        val deleteFileOnRelease: Boolean
    )

    private val mediaEntries = ConcurrentHashMap<String, MediaEntry>()

    /**
     * Register a content:// URI for serving. Returns the token.
     */
    fun registerUri(uri: Uri, mimeType: String, size: Long): String =
        register(MediaEntry(uri = uri, file = null, mimeType = mimeType, size = size, deleteFileOnRelease = false))

    /**
     * Register a file for serving (e.g., decrypted vault media). Returns the token.
     * When [deleteOnRelease] is true the file is deleted once it is no longer served.
     */
    fun registerFile(file: File, mimeType: String, deleteOnRelease: Boolean): String =
        register(
            MediaEntry(
                uri = null,
                file = file,
                mimeType = mimeType,
                size = file.length(),
                deleteFileOnRelease = deleteOnRelease
            )
        )

    @Synchronized
    private fun register(entry: MediaEntry): String {
        unregisterAll()
        val token = newToken()
        mediaEntries[token] = entry
        return token
    }

    @Synchronized
    fun unregister(token: String) {
        mediaEntries.remove(token)?.let(::release)
    }

    @Synchronized
    fun unregisterAll() {
        val tokens = mediaEntries.keys.toList()
        tokens.forEach { token -> mediaEntries.remove(token)?.let(::release) }
    }

    override fun stop() {
        super.stop()
        unregisterAll()
    }

    private fun release(entry: MediaEntry) {
        val file = entry.file ?: return
        if (entry.deleteFileOnRelease && file.exists() && !file.delete()) {
            printWarning("FCast: Failed to delete cast temp file ${file.name}")
        }
    }

    /**
     * Build the full URL that the FCast receiver can use to fetch the media.
     */
    fun getMediaUrl(token: String): String? {
        if (!mediaEntries.containsKey(token)) return null
        val ip = getLocalIpAddress() ?: return null
        return "http://$ip:${listeningPort}/media/$token"
    }

    private fun isReceiver(remoteIp: String?): Boolean {
        val allowed = receiverAddress ?: return true
        if (remoteIp.isNullOrBlank()) return false
        return try {
            val remote = InetAddress.getByName(remoteIp.substringBefore('%'))
            remote.address.contentEquals(allowed.address)
        } catch (_: Exception) {
            false
        }
    }

    override fun serve(session: IHTTPSession): Response {
        if (!isReceiver(session.remoteIpAddress)) {
            return newFixedLengthResponse(
                Response.Status.FORBIDDEN, MIME_PLAINTEXT, "Forbidden"
            )
        }

        val uri = session.uri ?: return newFixedLengthResponse(
            Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found"
        )

        // Expect /media/{token}
        if (!uri.startsWith("/media/")) {
            return newFixedLengthResponse(
                Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found"
            )
        }

        val token = uri.removePrefix("/media/")
        val entry = mediaEntries[token] ?: return newFixedLengthResponse(
            Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found"
        )

        return try {
            val inputStream: InputStream = when {
                entry.file != null -> FileInputStream(entry.file)
                entry.uri != null -> context.contentResolver.openInputStream(entry.uri)
                    ?: return newFixedLengthResponse(
                        Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Cannot open media"
                    )
                else -> return newFixedLengthResponse(
                    Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "No source"
                )
            }

            val rangeHeader = session.headers["range"]
            if (rangeHeader != null && entry.size > 0) {
                serveRange(inputStream, entry, rangeHeader)
            } else {
                newFixedLengthResponse(
                    Response.Status.OK,
                    entry.mimeType,
                    inputStream,
                    entry.size
                )
            }
        } catch (e: Exception) {
            printWarning("FCast: Failed to serve media: ${e.message}")
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                MIME_PLAINTEXT,
                "Internal Error"
            )
        }
    }

    private fun serveRange(
        inputStream: InputStream,
        entry: MediaEntry,
        rangeHeader: String
    ): Response {
        val totalSize = entry.size
        // Parse "bytes=START-END"
        val range = rangeHeader.replace("bytes=", "").trim()
        val parts = range.split("-")
        val start = parts[0].toLongOrNull() ?: 0L
        val end = if (parts.size > 1 && parts[1].isNotBlank()) {
            parts[1].toLong()
        } else {
            totalSize - 1
        }

        val contentLength = end - start + 1
        inputStream.skip(start)

        val response = newFixedLengthResponse(
            Response.Status.PARTIAL_CONTENT,
            entry.mimeType,
            inputStream,
            contentLength
        )
        response.addHeader("Content-Range", "bytes $start-$end/$totalSize")
        response.addHeader("Accept-Ranges", "bytes")
        return response
    }

    companion object {
        private val secureRandom = SecureRandom()

        private fun newToken(): String {
            val bytes = ByteArray(16)
            secureRandom.nextBytes(bytes)
            return bytes.joinToString("") { "%02x".format(it) }
        }

        fun getLocalIpAddress(): String? {
            return try {
                NetworkInterface.getNetworkInterfaces()?.toList()
                    ?.flatMap { it.inetAddresses.toList() }
                    ?.firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
                    ?.hostAddress
            } catch (_: Exception) {
                null
            }
        }
    }
}
