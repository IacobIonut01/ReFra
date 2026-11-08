package com.dot.gallery.editor.android

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.FileNotFoundException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * Test-only provider that streams fixture bytes through a *non-seekable*
 * pipe — the `pread`/`ESPIPE` staging path in [AndroidPhotoMediaAccess] and
 * [AndroidVideoSourceResolver] can only be exercised with a descriptor like
 * this.
 *
 * Not exported; same-uid access only. Payloads are registered per test in
 * [payloads] keyed by the URI's last path segment. [openCounts] records how
 * many descriptors the provider handed out per name (a staged copy costs
 * two: the seekability probe and the stream read) and [bytesWritten] how
 * many bytes have actually flowed — the lease-cache tests use both to prove
 * a source is copied once per session, not once per call.
 */
class PipeContentProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.dot.gallery.editor.android.test.pipe"

        fun uri(name: String): Uri = Uri.parse("content://$AUTHORITY/$name")

        val payloads = ConcurrentHashMap<String, ByteArray>()
        val openCounts = ConcurrentHashMap<String, AtomicInteger>()
        val bytesWritten = ConcurrentHashMap<String, AtomicInteger>()

        fun openCount(name: String): Int = openCounts[name]?.get() ?: 0
        fun writtenCount(name: String): Int = bytesWritten[name]?.get() ?: 0
    }

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val name = uri.lastPathSegment ?: throw FileNotFoundException("no name")
        val bytes = payloads[name] ?: throw FileNotFoundException(name)
        openCounts.getOrPut(name) { AtomicInteger() }.incrementAndGet()
        val pipe = ParcelFileDescriptor.createPipe()
        val out = ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])
        thread {
            try {
                out.use { stream ->
                    var i = 0
                    // Chunked writes so bytesWritten visibly grows while a
                    // copy is in flight (cancellation tests poll it).
                    while (i < bytes.size) {
                        val take = minOf(65_536, bytes.size - i)
                        stream.write(bytes, i, take)
                        i += take
                        bytesWritten.getOrPut(name) { AtomicInteger() }
                            .addAndGet(take)
                    }
                }
            } catch (broken: java.io.IOException) {
                // The reader may finish early — a broken pipe is expected
                // teardown, not a provider failure.
            }
        }
        return pipe[0]
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor = MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME)).apply {
        addRow(arrayOf(uri.lastPathSegment ?: "pipe.bin"))
    }

    override fun getType(uri: Uri): String = "image/png"

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0
}
