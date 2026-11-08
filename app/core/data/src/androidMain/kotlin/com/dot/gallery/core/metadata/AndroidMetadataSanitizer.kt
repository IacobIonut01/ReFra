package com.dot.gallery.core.metadata

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.dot.gallery.domain.model.Media
import com.dot.gallery.domain.util.getUri
import com.dot.gallery.core.logging.printError
import com.dot.gallery.core.logging.printWarn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.channels.Channels
import java.security.MessageDigest
import java.util.Properties
import java.util.UUID
import javax.inject.Inject
import com.dot.gallery.core.injection.Singleton
import kotlin.coroutines.coroutineContext
import com.dot.gallery.domain.uri.query

@Singleton
class AndroidMetadataSanitizer @Inject constructor(
    private val context: Context
) : MetadataSanitizer {
    private val resolver get() = context.contentResolver
    private val transactionDirectory = File(context.noBackupFilesDir, "metadata-rewrite")

    override suspend fun probe(media: Media): SanitizationCapability = withContext(Dispatchers.IO) {
        try {
            val descriptor = resolver.openFileDescriptor(media.getUri(), "r")
                ?: throw IOException("Unable to open source media")
            descriptor.use {
                FileInputStream(it.fileDescriptor).use { input ->
                    val channel = input.channel
                    val format = MediaFormatDetector.detect(Channels.newInputStream(channel.position(0)), media.mimeType, media.label)
                    MetadataEngine.capability(format, channel.position(0))
                }
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: SecurityException) {
            SanitizationCapability(
                MediaContainerFormat.UNKNOWN,
                emptySet(),
                limitation = "Permission to read this media is unavailable."
            )
        } catch (e: UnsupportedMetadataException) {
            SanitizationCapability(MediaContainerFormat.UNKNOWN, emptySet(), limitation = e.message)
        } catch (e: Exception) {
            printError("media-ops.metadata", "sanitizer probe failed", e)
            SanitizationCapability(
                MediaContainerFormat.UNKNOWN,
                emptySet(),
                limitation = "The media could not be inspected safely."
            )
        }
    }

    override suspend fun sanitize(
        media: Media,
        categories: Set<MetadataCategory>,
        saveMode: MetadataSaveMode
    ): SanitizationResult = withContext(Dispatchers.IO) {
        val selection = MetadataSelection(categories)
        val capability = probe(media)
        if (selection.isEmpty || !capability.supports(selection.categories)) {
            return@withContext SanitizationResult.Unsupported(
                capability.format,
                capability.limitation ?: "The selected metadata cannot be removed from this file."
            )
        }
        transactionDirectory.mkdirs()
        val required = requiredTemporarySpace(media.size.coerceAtLeast(1L), 3)
        val available = transactionDirectory.usableSpace
        if (available < required) {
            return@withContext SanitizationResult.InsufficientSpace(required, available)
        }

        val id = UUID.randomUUID().toString()
        val backup = File(transactionDirectory, "$id.original")
        val candidate = File(transactionDirectory, "$id.candidate")
        try {
            copyUriToFile(media.getUri(), backup)
            val actualRequired = requiredTemporarySpace(backup.length(), 2)
            val actualAvailable = transactionDirectory.usableSpace
            if (actualAvailable < actualRequired) {
                return@withContext SanitizationResult.InsufficientSpace(actualRequired, actualAvailable)
            }
            coroutineContext.ensureActive()
            val format = FileInputStream(backup).use {
                MediaFormatDetector.detect(it, media.mimeType, media.label)
            }
            if (format != capability.format) {
                return@withContext SanitizationResult.MalformedInput("Media format changed while preparing sanitization.")
            }
            val sourceSha256 = sha256(backup)
            val present = FileInputStream(backup).use { MetadataEngine.inspect(format, it.channel).present.toSet() }
            MetadataEngine.scrub(format, backup, candidate, selection)
            coroutineContext.ensureActive()
            MetadataEngine.verify(format, backup, candidate, selection)?.let { reason ->
                printError("media-ops.metadata", "sanitized candidate failed verification", ctx = mapOf("format" to format.name, "reason" to reason))
                return@withContext SanitizationResult.VerificationFailed(reason)
            }
            val outputUri = saveCandidateCopy(candidate, backup, media, format, selection)
            SanitizationResult.Success(
                format = format,
                removedCategories = selection.categories intersect present,
                retainedRequiredMetadata = capability.retainedRequiredMetadata,
                bytesBefore = backup.length(),
                bytesAfter = candidate.length(),
                saveMode = saveMode,
                sourceUri = media.getUri(),
                sourceSha256 = sourceSha256,
                outputUri = outputUri
            )
        } catch (_: kotlinx.coroutines.CancellationException) {
            SanitizationResult.Cancelled
        } catch (_: SecurityException) {
            SanitizationResult.PermissionDenied
        } catch (error: MalformedMetadataException) {
            printWarn("media-ops.metadata", "metadata removal rejected malformed input: ${error.message}")
            SanitizationResult.MalformedInput(error.message ?: "The file structure is invalid.")
        } catch (error: UnsupportedMetadataException) {
            printWarn("media-ops.metadata", "metadata removal unsupported: ${error.message}")
            SanitizationResult.Unsupported(capability.format, error.message ?: "This file is not supported.")
        } catch (error: Exception) {
            printError("media-ops.metadata", "metadata removal failed", error)
            SanitizationResult.CommitFailed(error.message ?: error.javaClass.simpleName, false)
        } finally {
            backup.delete()
            candidate.delete()
        }
    }

    override suspend fun recoverPendingTransactions() = withContext(Dispatchers.IO) {
        if (!transactionDirectory.exists()) return@withContext
        transactionDirectory.listFiles { file -> file.extension == "properties" }.orEmpty().forEach { journal ->
            val properties = runCatching { journal.readProperties() }.getOrNull() ?: return@forEach
            val uri = properties.getProperty("uri")?.let(Uri::parse) ?: return@forEach
            val backup = properties.getProperty("backup")?.let(::File) ?: return@forEach
            val candidate = properties.getProperty("candidate")?.let(::File) ?: return@forEach
            val backupHash = properties.getProperty("backupSha256") ?: return@forEach
            val dateModified = properties.getProperty("dateModified")?.toLongOrNull()
            val dateTaken = properties.getProperty("dateTaken")?.toLongOrNull()
            val backupValid = backup.isFile && runCatching {
                sha256(backup) == backupHash
            }.getOrDefault(false)
            if (!backupValid) return@forEach
            val currentHash = runCatching { sha256(uri) }.getOrNull() ?: return@forEach
            val candidateHash = candidate.takeIf(File::isFile)?.let { runCatching { sha256(it) }.getOrNull() }
            val transactionResolved = if (currentHash == backupHash || currentHash == candidateHash) {
                finalizeRecoveredMedia(uri, dateModified, dateTaken, currentHash)
            } else {
                false
            }
            if (!transactionResolved) return@forEach
            candidate.delete()
            backup.delete()
            journal.delete()
        }
    }

    private fun saveCandidateCopy(
        candidate: File,
        source: File,
        media: Media,
        format: MediaContainerFormat,
        selection: MetadataSelection
    ): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, sanitizedCopyName(media.label))
            put(MediaStore.MediaColumns.MIME_TYPE, media.mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, media.relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val copyUri = resolver.insert(collection(media.getUri(), media.mimeType), values)
            ?: throw IOException("Unable to create sanitized copy")
        try {
            writeFileToUri(candidate, copyUri)
            verifyOutput(copyUri, candidate, source, media, format, selection)
            restoreMediaColumns(copyUri, media.timestamp, media.takenTimestamp)
            return copyUri
        } catch (error: Exception) {
            runCatching { resolver.delete(copyUri, null, null) }
                .onFailure { printWarn("media-ops.metadata", "could not delete a failed sanitized copy: ${it.message}") }
            throw error
        }
    }

    private fun verifyOutput(
        uri: Uri,
        candidate: File,
        source: File,
        media: Media,
        format: MediaContainerFormat,
        selection: MetadataSelection
    ) {
        if (sha256(uri) != sha256(candidate)) {
            throw IOException("Saved file does not match the verified candidate")
        }
        val verification = File.createTempFile("metadata-verify", ".tmp", transactionDirectory)
        try {
            copyUriToFile(uri, verification)
            val savedFormat = FileInputStream(verification).use { MediaFormatDetector.detect(it, media.mimeType, media.label) }
            if (savedFormat != format || MetadataEngine.verify(format, source, verification, selection) != null) {
                throw IOException("Saved file failed metadata or payload verification")
            }
        } finally {
            verification.delete()
        }
    }

    private fun restoreMediaColumns(uri: Uri, dateModified: Long, dateTaken: Long?) {
        val updated = resolver.update(
            uri,
            ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
                put(MediaStore.MediaColumns.DATE_MODIFIED, dateModified)
                dateTaken?.let { put(MediaStore.MediaColumns.DATE_TAKEN, it) }
            },
            null,
            null
        )
        if (updated <= 0 || isPending(uri) != false) {
            throw IOException("Unable to publish verified media")
        }
    }

    private fun finalizeRecoveredMedia(
        uri: Uri,
        dateModified: Long?,
        dateTaken: Long?,
        expectedHash: String
    ): Boolean = runCatching {
        val updated = resolver.update(
            uri,
            ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
                dateModified?.let { put(MediaStore.MediaColumns.DATE_MODIFIED, it) }
                dateTaken?.let { put(MediaStore.MediaColumns.DATE_TAKEN, it) }
            },
            null,
            null
        )
        updated > 0 && isPending(uri) == false && sha256(uri) == expectedHash
    }.getOrDefault(false)

    private fun isPending(uri: Uri): Boolean? = resolver.query(
        uri,
        arrayOf(MediaStore.MediaColumns.IS_PENDING),
        null,
        null,
        null
    )?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getInt(0) != 0 else null
    }

    private fun collection(sourceUri: Uri, mimeType: String): Uri {
        val video = mimeType.startsWith("video/")
        return runCatching {
            val volume = MediaStore.getVolumeName(sourceUri)
            if (video) MediaStore.Video.Media.getContentUri(volume) else MediaStore.Images.Media.getContentUri(volume)
        }.getOrElse {
            printWarn("media-ops.metadata", "falling back to the primary volume for the sanitized copy: ${it.message}")
            if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
    }

    private fun sanitizedCopyName(label: String): String {
        val extensionIndex = label.lastIndexOf('.').takeIf { it > 0 } ?: label.length
        val baseName = label.substring(0, extensionIndex)
        val extension = label.substring(extensionIndex)
        return "${baseName}_sanitized$extension"
    }

    private fun copyUriToFile(uri: Uri, target: File) {
        resolver.openInputStream(uri)?.use { input ->
            FileOutputStream(target).use { output ->
                input.copyTo(output)
                output.flush()
                output.fd.sync()
            }
        } ?: throw IOException("Unable to open source media")
        if (target.length() <= 0L) throw IOException("Source media is empty")
    }

    private fun writeFileToUri(source: File, uri: Uri) {
        resolver.openOutputStream(uri, "w")?.use { output ->
            FileInputStream(source).use { it.copyTo(output) }
            output.flush()
            (output as? FileOutputStream)?.fd?.sync()
        } ?: throw IOException("Unable to write sanitized copy")
    }

    private fun File.readProperties(): Properties = Properties().also { properties ->
        inputStream().use(properties::load)
    }

    private fun sha256(file: File): String = file.inputStream().use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun sha256(uri: Uri): String = resolver.openInputStream(uri)?.use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    } ?: throw IOException("Unable to verify media")

    private fun requiredTemporarySpace(size: Long, copies: Int): Long {
        val maximumSize = (Long.MAX_VALUE - MINIMUM_FREE_MARGIN) / copies
        return if (size > maximumSize) Long.MAX_VALUE else size * copies + MINIMUM_FREE_MARGIN
    }

    private companion object {
        const val MINIMUM_FREE_MARGIN = 16L * 1024L * 1024L
    }
}
