package com.dot.gallery.editor.android

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import com.dot.gallery.editor.videocodec.ResolvedVideoSource
import com.dot.gallery.editor.videocodec.VideoSourceResolver
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Resolves `content://` and `file://` references into a seekable local path
 * FFmpeg can open — once per source, not once per call.
 *
 * `content://` keeps one reference-counted *backing* per original reference
 * for the resolver's lifetime:
 * - A seekable descriptor is exposed through `/proc/self/fd/<fd>` when the
 *   process may reopen that symlink (scoped storage denies it for media the
 *   app did not create — the provided fd itself stays valid, only the path
 *   reopen fails; this adapter stages in that case, it is not an OS rule
 *   that foreign content must always be copied). The
 *   [ParcelFileDescriptor] then backs every lease until it is retired.
 * - Otherwise the content is staged into a private `cache/editor/imports`
 *   file — capped at [MAX_IMPORT_BYTES], deleted on release, never held
 *   whole in heap. Staging is what makes repeated probe/frame/export calls
 *   cheap: the copy happens once per opened source, not per operation.
 * - `file://` resolves straight to its path with no backing at all.
 *
 * Every [resolve] returns a lease ([ResolvedVideoSource]) that retains the
 * backing; `close()` decrements the count. A backing stays alive for the
 * resolver's lifetime even at zero refs — a later resolve of the same
 * reference reuses it with no new copy. Resolving a *different* reference
 * retires the previous backing: it is released immediately when unused, or
 * when its last lease closes. [close] retires the same way, so a file is
 * never deleted while FFmpeg still reads it. [creationMutex] serializes the
 * expensive part (descriptor open/staging copy) — two concurrent resolves of
 * one reference produce one copy, and a copy that finishes after [close]
 * is cleaned up instead of being published.
 *
 * The provider must grant read access (persistable grants are taken by the
 * caller/intent layer, not here).
 */
class AndroidVideoSourceResolver(
    context: Context,
    private val onError: (Throwable) -> Unit
) : VideoSourceResolver, AutoCloseable {

    private val appContext = context.applicationContext

    /**
     * One session-scoped backing per source reference. `refs` counts live
     * leases; `retired` means it must be released when the last one closes.
     */
    private class Backing(
        val reference: String,
        val path: String,
        val displayName: String,
        val release: () -> Unit
    ) {
        var refs = 0
        var retired = false
    }

    private val lock = Any()
    private val creationMutex = Mutex()
    private var backing: Backing? = null
    private var closed = false

    /** Test seam for descriptor lookup; production never sets it. */
    internal var descriptorOpener: ((Uri) -> ParcelFileDescriptor?)? = null

    override suspend fun resolve(reference: String): ResolvedVideoSource {
        val uri = Uri.parse(reference)
        when (uri.scheme) {
            "content" -> Unit
            "file" -> return ResolvedVideoSource(
                requireNotNull(uri.path) { "file URI without a path: $uri" },
                displayName(uri)
            ) {}
            else -> throw IllegalArgumentException(
                "unsupported video reference '$reference' — only content:// " +
                    "and file:// are accepted"
            )
        }
        synchronized(lock) {
            check(!closed) { "resolver is closed" }
            backing?.takeIf { it.reference == reference && !it.retired }
                ?.let { return lease(it) }
        }
        return creationMutex.withLock {
            // Lost the creation race — reuse what the winner published.
            synchronized(lock) {
                check(!closed) { "resolver is closed" }
                backing?.takeIf { it.reference == reference && !it.retired }
                    ?.let { return@withLock lease(it) }
            }
            val loaded = loadBacking(uri, reference)
            synchronized(lock) {
                if (closed) {
                    // Loaded while close raced us: clean up instead of
                    // publishing a backing nobody will release.
                    loaded.release()
                    throw IllegalStateException("resolver is closed")
                }
                val old = backing
                if (old != null) {
                    old.retired = true
                    if (old.refs <= 0) {
                        backing = null
                        safeRelease(old)
                    }
                }
                loaded.refs = 1
                backing = loaded
            }
            ResolvedVideoSource(loaded.path, loaded.displayName) {
                releaseLease(loaded)
            }
        }
    }

    /** Retires everything; live leases finish on their last close. */
    override fun close() {
        val releaseNow: Backing? = synchronized(lock) {
            if (closed) {
                null
            } else {
                closed = true
                backing?.let {
                    it.retired = true
                    if (it.refs <= 0) {
                        backing = null
                        it
                    } else {
                        null
                    }
                }
            }
        }
        releaseNow?.let { safeRelease(it) }
    }

    /** Builds a new lease on [entry]; caller holds [lock]. */
    private fun lease(entry: Backing): ResolvedVideoSource {
        entry.refs++
        return ResolvedVideoSource(entry.path, entry.displayName) {
            releaseLease(entry)
        }
    }

    private fun releaseLease(entry: Backing) {
        val releaseNow = synchronized(lock) {
            entry.refs--
            if (entry.refs <= 0 && (entry.retired || closed)) {
                if (backing === entry) backing = null
                true
            } else {
                false
            }
        }
        if (releaseNow) safeRelease(entry)
    }

    private fun safeRelease(entry: Backing) {
        try {
            entry.release()
        } catch (e: Exception) {
            onError(e)
        }
    }

    /**
     * Creates the backing for [uri]: a live procfd view of the descriptor
     * when reopenable, else a staged private copy. Slow — call under
     * [creationMutex], never under [lock].
     */
    private suspend fun loadBacking(uri: Uri, reference: String): Backing {
        val name = displayName(uri)
        val descriptor = openDescriptor(uri)
        val usable = try {
            isSeekable(descriptor) && canOpenThroughProcFd(descriptor)
        } catch (t: Throwable) {
            descriptor.close()
            throw t
        }
        if (usable) {
            // FFmpeg opens the symlink itself — a second fd on the same
            // file, fully seekable, owned by our process. The PFD must
            // outlive every decoder, so it is released with the backing.
            return Backing(reference, "/proc/self/fd/${descriptor.fd}", name) {
                safeClose(descriptor)
            }
        }
        descriptor.close()
        val staged = stageImport(uri)
        return Backing(reference, staged.absolutePath, name) {
            if (!staged.delete() && staged.exists()) {
                onError(IOException("could not delete staged import $staged"))
            }
        }
    }

    private fun openDescriptor(uri: Uri): ParcelFileDescriptor =
        descriptorOpener?.invoke(uri)
            ?: appContext.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IOException("cannot open $uri")

    private fun isSeekable(descriptor: ParcelFileDescriptor): Boolean = try {
        Os.lseek(descriptor.fileDescriptor, 0L, OsConstants.SEEK_CUR)
        true
    } catch (e: ErrnoException) {
        if (e.errno == OsConstants.ESPIPE) false else throw IOException(e)
    }

    /**
     * The procfd trick only works when the process may also open the
     * symlink target by path. Scoped storage denies that for media the app
     * did not create itself (`EACCES`) — that is the one denial that falls
     * back to staging; other open failures (EMFILE, EIO, …) are real errors
     * and propagate.
     */
    private fun canOpenThroughProcFd(descriptor: ParcelFileDescriptor): Boolean {
        val opened = try {
            Os.open(
                "/proc/self/fd/${descriptor.fd}", OsConstants.O_RDONLY, 0
            )
        } catch (e: ErrnoException) {
            if (e.errno == OsConstants.EACCES) return false
            throw IOException("cannot reopen media descriptor", e)
        }
        try {
            Os.close(opened)
        } catch (e: ErrnoException) {
            onError(IOException("failed to close procfd probe", e))
        }
        return true
    }

    /** Copies a non-seekable source into a privately owned cache file. */
    private suspend fun stageImport(uri: Uri): File {
        val dir = File(appContext.cacheDir, "editor/imports")
        if (!dir.exists() && !dir.mkdirs()) {
            throw IOException("cannot create import directory $dir")
        }
        val target = File(dir, UUID.randomUUID().toString())
        try {
            val input = appContext.contentResolver.openInputStream(uri)
                ?: throw IOException("cannot open stream for $uri")
            input.use {
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(COPY_BUFFER_BYTES)
                    var total = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_IMPORT_BYTES) {
                            throw IOException(
                                "video exceeds the " +
                                    "${MAX_IMPORT_BYTES / (1024 * 1024 * 1024)} GiB " +
                                    "import limit"
                            )
                        }
                        output.write(buffer, 0, read)
                    }
                }
            }
            return target
        } catch (t: Throwable) {
            target.delete()
            throw t
        }
    }

    private fun safeClose(descriptor: ParcelFileDescriptor) {
        try {
            descriptor.close()
        } catch (e: IOException) {
            onError(e)
        }
    }

    /** `DISPLAY_NAME` for `content://`, path segment for files. */
    private fun displayName(uri: Uri): String {
        if (uri.scheme == "content") {
            try {
                appContext.contentResolver.query(
                    uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index =
                            cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index >= 0) {
                            cursor.getString(index)
                                ?.takeIf { it.isNotBlank() }
                                ?.let { return it }
                        }
                    }
                }
            } catch (e: Exception) {
                onError(e)
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { null }
            ?: "video"
    }

    private companion object {
        const val MAX_IMPORT_BYTES = 4L * 1024 * 1024 * 1024
        const val COPY_BUFFER_BYTES = 65_536
    }
}
