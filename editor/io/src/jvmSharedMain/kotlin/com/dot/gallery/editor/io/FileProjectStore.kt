package com.dot.gallery.editor.io

import java.io.File
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption

/**
 * An atomic, checkable home for persisted project JSON under a private
 * root (Android `noBackupFilesDir`, desktop `~/.refra-projects`). Every
 * [write] stages to a same-directory temp file, fsyncs its bytes, then
 * renames over the target — a crash mid-write can leave a stale or
 * absent project, never a torn one; a best-effort directory sync closes
 * the rename-durability gap where the platform allows it.
 *
 * Names are single path segments only: the store never resolves a
 * caller-supplied path outside [root].
 */
class FileProjectStore(private val root: File) {

    /** Writes [content] atomically; a failed write keeps the old file. */
    fun write(name: String, content: String) {
        val target = checked(name)
        val staging = File(root, "$name.tmp")
        try {
            if (!root.isDirectory && !root.mkdirs()) {
                throw IOException("cannot create project directory $root")
            }
            staging.outputStream().use { stream ->
                stream.write(content.toByteArray(Charsets.UTF_8))
                stream.fd.sync()
            }
            if (!staging.renameTo(target)) {
                target.delete()
                if (!staging.renameTo(target)) {
                    throw IOException("cannot commit project file $target")
                }
            }
            // Directory entries need their own durability — fsyncing the
            // file does not guarantee the rename itself survives a crash.
            runCatching {
                FileChannel.open(
                    root.toPath(), StandardOpenOption.READ
                ).use { it.force(true) }
            }
        } catch (error: IOException) {
            staging.delete()
            throw error
        } catch (error: RuntimeException) {
            staging.delete()
            throw error
        }
    }

    /** The stored content, or null when the file does not exist. */
    fun read(name: String): String? {
        val target = checked(name)
        if (!target.isFile) return null
        return target.readText(Charsets.UTF_8)
    }

    fun exists(name: String): Boolean = checked(name).isFile

    fun delete(name: String) {
        checked(name).delete()
    }

    /** The stored file names — stable order for tests and recents. */
    fun list(): List<String> =
        root.listFiles()
            ?.filter { it.isFile && !it.name.endsWith(".tmp") }
            ?.map { it.name }
            ?.sorted()
            ?: emptyList()

    private fun checked(name: String): File {
        require(name.isNotBlank() && !name.contains('/') &&
            !name.contains('\\') && !name.contains("..")
        ) { "project file name must be a single path segment: $name" }
        return File(root, name)
    }
}
