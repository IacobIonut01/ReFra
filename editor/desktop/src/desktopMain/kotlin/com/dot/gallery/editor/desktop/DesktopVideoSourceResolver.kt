package com.dot.gallery.editor.desktop

import com.dot.gallery.editor.videocodec.ResolvedVideoSource
import com.dot.gallery.editor.videocodec.VideoSourceResolver
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.isReadable
import kotlin.io.path.isRegularFile
import kotlin.io.path.name

/**
 * Resolves plain filesystem paths and `file://` URIs to a seekable local
 * file. The release is a no-op — nothing is staged and nothing is held;
 * every consumer opens its own handle on [ResolvedVideoSource.path].
 */
class DesktopVideoSourceResolver : VideoSourceResolver {

    override suspend fun resolve(reference: String): ResolvedVideoSource {
        val path = if (reference.startsWith("file:")) {
            Path.of(java.net.URI.create(reference))
        } else {
            if (reference.contains("://")) {
                throw IllegalArgumentException(
                    "unsupported video reference '$reference' — only file " +
                        "paths and file:// URIs are accepted"
                )
            }
            Path.of(reference)
        }
        if (!path.isRegularFile() || !path.isReadable()) {
            throw IOException("not a readable file: $reference")
        }
        return ResolvedVideoSource(path.toAbsolutePath().toString(), path.name) {}
    }
}
