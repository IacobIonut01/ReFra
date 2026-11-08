package com.dot.gallery.editor.videocodec

/**
 * A source reference resolved to a seekable local file path FFmpeg can open.
 * The resolver owns whatever backs [path] (a staged copy, an open
 * ParcelFileDescriptor surfaced through `/proc/self/fd`, or a plain file);
 * [close] releases it. Resolvers keep that resource alive even when the
 * resolving coroutine is cancelled promptly — same contract as PhotoRaster.
 */
class ResolvedVideoSource(
    val path: String,
    val displayName: String,
    private val release: () -> Unit
) : AutoCloseable {
    private var released = false

    @Synchronized
    override fun close() {
        if (!released) {
            released = true
            release()
        }
    }
}
