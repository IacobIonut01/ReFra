package com.dot.gallery.editor.desktop

import com.dot.gallery.editor.io.EditorNativeLoader
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Loads the JNI library packaged as the jar resource
 * `native/<os>-<arch>/<libname>` — and only that resource; there is no
 * `System.loadLibrary` fallback onto Homebrew/system copies. The resource
 * bytes are SHA-256 digested, staged into a private
 * `<tmpdir>/refra-editor-native/<digest>/` cache via an atomic move, verified
 * and then loaded by absolute path. One load per library content version,
 * synchronised across instances. A missing/oversized resource or an
 * unverifiable stage surfaces as [IOException], which NativeColorEffect maps
 * to the Kotlin fallback.
 */
class DesktopEditorNativeLoader : EditorNativeLoader {

    /**
     * Diagnostics for tests only — `"<loaded path> sha256=<digest>"`, null
     * before the first load. Never shown in the UI.
     */
    internal var loadStatus: String? = null
        private set

    override fun load() {
        val resource = "/native/${hostOs()}-${hostArch()}/$LIBRARY_FILE"
        val bytes = readResource(resource)
        val digest = sha256(bytes)
        val path = synchronized(LOADED) {
            LOADED.getOrPut(digest) { materialize(bytes, digest) }
        }
        System.load(path.toAbsolutePath().toString())
        loadStatus = "$path sha256=$digest"
    }

    private fun readResource(path: String): ByteArray {
        val stream = javaClass.getResourceAsStream(path)
            ?: throw IOException(
                "no packaged native library for ${hostOs()}-${hostArch()} ($path)"
            )
        stream.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(READ_CHUNK)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_LIBRARY_BYTES) {
                    throw IOException(
                        "native library resource exceeds $MAX_LIBRARY_BYTES bytes"
                    )
                }
                out.write(buffer, 0, read)
            }
            return out.toByteArray()
        }
    }

    private fun materialize(bytes: ByteArray, digest: String): Path {
        val dir = Path.of(
            System.getProperty("java.io.tmpdir"), "refra-editor-native", digest
        )
        Files.createDirectories(dir)
        val target = dir.resolve(LIBRARY_FILE)
        if (!Files.isRegularFile(target) || sha256(Files.readAllBytes(target)) != digest) {
            val temp = Files.createTempFile(dir, "lib", ".tmp")
            try {
                Files.write(temp, bytes)
                Files.move(
                    temp, target,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING
                )
            } catch (t: Throwable) {
                Files.deleteIfExists(temp)
                throw t
            }
        }
        if (sha256(Files.readAllBytes(target)) != digest) {
            throw IOException("staged native library failed integrity verification")
        }
        return target
    }

    private companion object {
        val LIBRARY_FILE: String = System.mapLibraryName("refra_editor_jni")
        const val MAX_LIBRARY_BYTES = 10 * 1024 * 1024
        const val READ_CHUNK = 8192

        /** content digest -> staged path; System.load is idempotent per path. */
        val LOADED = ConcurrentHashMap<String, Path>()

        fun hostOs(): String {
            val name = System.getProperty("os.name").lowercase(Locale.ROOT)
            return when {
                name.contains("mac") -> "macos"
                name.contains("windows") -> "windows"
                name.contains("linux") -> "linux"
                else -> name.replace(Regex("[^a-z0-9]+"), "-")
            }
        }

        fun hostArch(): String = when (
            val arch = System.getProperty("os.arch").lowercase(Locale.ROOT)
        ) {
            "aarch64", "arm64" -> "arm64"
            "amd64", "x86_64", "x64" -> "x64"
            else -> arch.replace(Regex("[^a-z0-9]+"), "-")
        }

        fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }
    }
}
