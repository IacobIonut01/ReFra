package com.dot.gallery.core.metadata

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.util.getUri
import com.dot.gallery.feature_node.presentation.util.printError
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.Properties
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

interface MetadataSanitizer {
    suspend fun probe(media: Media): SanitizationCapability
    suspend fun sanitize(
        media: Media,
        mode: MetadataRemovalMode,
        saveMode: MetadataSaveMode = MetadataSaveMode.SAVE_COPY
    ): SanitizationResult
    suspend fun recoverPendingTransactions()
}

@Singleton
class AndroidMetadataSanitizer @Inject constructor(
    @param:ApplicationContext private val context: Context
) : MetadataSanitizer {
    private val resolver get() = context.contentResolver
    private val transactionDirectory = File(context.noBackupFilesDir, "metadata-rewrite")

    override suspend fun probe(media: Media): SanitizationCapability = withContext(Dispatchers.IO) {
        try {
            val uri = media.getUri()
            val format = resolver.openInputStream(uri)?.use {
                MediaFormatDetector.detect(it, media.mimeType, media.label)
            } ?: MediaContainerFormat.UNKNOWN
            if (format == MediaContainerFormat.JPEG && isMotionPhoto(uri)) {
                return@withContext SanitizationCapability(
                    format = format,
                    supportedModes = emptySet(),
                    limitation = "Motion Photo sanitization is unavailable until its embedded video and linkage can be preserved."
                )
            }
            val base = MetadataCapabilities.forFormat(format)
            if (format in setOf(MediaContainerFormat.JPEG, MediaContainerFormat.PNG, MediaContainerFormat.WEBP) &&
                (hasOpaqueSelectiveMetadata(uri, format) || hasEmbeddedExifThumbnail(uri))
            ) {
                return@withContext base.copy(
                    supportedModes = emptySet(),
                    limitation = "This file contains nested or opaque metadata that cannot currently be removed safely."
                )
            }
            base
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: SecurityException) {
            SanitizationCapability(
                MediaContainerFormat.UNKNOWN,
                emptySet(),
                limitation = "Permission to read this media is unavailable."
            )
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
        mode: MetadataRemovalMode,
        saveMode: MetadataSaveMode
    ): SanitizationResult = withContext(Dispatchers.IO) {
        val capability = probe(media)
        if (!capability.supports(mode)) {
            return@withContext SanitizationResult.Unsupported(
                capability.format,
                capability.limitation ?: "The selected metadata mode is not supported."
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
            if (format == MediaContainerFormat.JPEG && isMotionPhoto(backup)) {
                return@withContext SanitizationResult.Unsupported(
                    format,
                    "Motion Photo sanitization is unavailable until its embedded video and linkage can be preserved."
                )
            }
            if (format in setOf(MediaContainerFormat.JPEG, MediaContainerFormat.PNG, MediaContainerFormat.WEBP) &&
                (hasOpaqueSelectiveMetadata(backup, format) || hasEmbeddedExifThumbnail(backup))
            ) {
                return@withContext SanitizationResult.Unsupported(
                    format,
                    "This file contains nested or opaque metadata that cannot currently be removed safely."
                )
            }
            val sourceSha256 = sha256(backup)
            val beforeFingerprint = MediaEssenceFingerprint.calculate(backup, format)
            LosslessMetadataRewriter.rewrite(backup, candidate, format, mode)
            FileOutputStream(candidate, true).use { it.fd.sync() }
            if (!MetadataVerifier.verify(candidate, format, mode)) {
                return@withContext SanitizationResult.VerificationFailed("The sanitized candidate still contains selected metadata.")
            }
            val afterFingerprint = MediaEssenceFingerprint.calculate(candidate, format)
            if (beforeFingerprint != afterFingerprint) {
                return@withContext SanitizationResult.VerificationFailed("Encoded media payload changed during sanitization.")
            }
            val outputUri = saveCandidateCopy(
                candidate = candidate,
                media = media,
                format = format,
                mode = mode,
                expectedFingerprint = afterFingerprint
            )
            SanitizationResult.Success(
                mode = mode,
                format = format,
                removedCategories = MetadataPolicy.forMode(mode).removedCategories,
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
        } catch (error: Exception) {
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
        media: Media,
        format: MediaContainerFormat,
        mode: MetadataRemovalMode,
        expectedFingerprint: String
    ): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, sanitizedCopyName(media.label))
            put(MediaStore.MediaColumns.MIME_TYPE, media.mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, media.relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val copyUri = resolver.insert(imageCollection(media.getUri()), values)
            ?: throw IOException("Unable to create sanitized copy")
        try {
            writeFileToUri(candidate, copyUri)
            verifyOutput(copyUri, candidate, media, format, mode, expectedFingerprint)
            restoreMediaColumns(copyUri, media.timestamp, media.takenTimestamp)
            verifyOutput(copyUri, candidate, media, format, mode, expectedFingerprint)
            return copyUri
        } catch (error: Exception) {
            runCatching { resolver.delete(copyUri, null, null) }
            throw error
        }
    }

    private fun verifyOutput(
        uri: Uri,
        candidate: File,
        media: Media,
        format: MediaContainerFormat,
        mode: MetadataRemovalMode,
        expectedFingerprint: String
    ) {
        if (sha256(uri) != sha256(candidate)) {
            throw IOException("Saved file does not match the verified candidate")
        }
        if (!verifyCommitted(uri, media, format, mode, expectedFingerprint)) {
            throw IOException("Saved file failed metadata or payload verification")
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

    private fun imageCollection(sourceUri: Uri): Uri = runCatching {
        MediaStore.Images.Media.getContentUri(MediaStore.getVolumeName(sourceUri))
    }.getOrDefault(MediaStore.Images.Media.EXTERNAL_CONTENT_URI)

    private fun sanitizedCopyName(label: String): String {
        val extensionIndex = label.lastIndexOf('.').takeIf { it > 0 } ?: label.length
        val baseName = label.substring(0, extensionIndex)
        val extension = label.substring(extensionIndex)
        return "${baseName}_sanitized$extension"
    }

    private fun verifyCommitted(
        uri: Uri,
        media: Media,
        format: MediaContainerFormat,
        mode: MetadataRemovalMode,
        expectedFingerprint: String
    ): Boolean {
        val verification = File.createTempFile("metadata-verify", ".tmp", transactionDirectory)
        return try {
            copyUriToFile(uri, verification)
            FileInputStream(verification).use {
                MediaFormatDetector.detect(it, media.mimeType, media.label)
            } == format &&
                MetadataVerifier.verify(verification, format, mode) &&
                MediaEssenceFingerprint.calculate(verification, format) == expectedFingerprint
        } finally {
            verification.delete()
        }
    }

    private fun hasEmbeddedExifThumbnail(uri: Uri): Boolean = runCatching {
        val input = resolver.openInputStream(uri) ?: return@runCatching true
        input.use { ExifInterface(it).hasThumbnail() }
    }.getOrDefault(true)

    private fun hasEmbeddedExifThumbnail(file: File): Boolean = runCatching {
        ExifInterface(file.absolutePath).hasThumbnail()
    }.getOrDefault(true)

    private fun hasOpaqueSelectiveMetadata(uri: Uri, format: MediaContainerFormat): Boolean = runCatching {
        val input = resolver.openInputStream(uri) ?: return@runCatching true
        input.buffered().use { hasOpaqueSelectiveMetadata(it, format) }
    }.getOrDefault(true)

    private fun hasOpaqueSelectiveMetadata(file: File, format: MediaContainerFormat): Boolean = runCatching {
        file.inputStream().buffered().use { hasOpaqueSelectiveMetadata(it, format) }
    }.getOrDefault(true)

    private fun hasOpaqueSelectiveMetadata(
        input: BufferedInputStream,
        format: MediaContainerFormat
    ): Boolean {
        val tokens = when (format) {
            MediaContainerFormat.JPEG -> listOf(
                "http://ns.adobe.com/xap/1.0/".toByteArray(),
                "http://ns.adobe.com/xmp/extension/".toByteArray(),
                "Photoshop 3.0".toByteArray()
            )
            MediaContainerFormat.PNG -> listOf(
                "iTXt".toByteArray(),
                "tEXt".toByteArray(),
                "zTXt".toByteArray()
            )
            MediaContainerFormat.WEBP -> listOf("XMP ".toByteArray())
            else -> emptyList()
        }
        if (tokens.isEmpty()) return false
        val longest = tokens.maxOf { it.size }
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE + longest)
        var carried = 0
        while (true) {
            val count = input.read(buffer, carried, DEFAULT_BUFFER_SIZE)
            if (count < 0) return false
            val total = carried + count
            if (tokens.any { token -> buffer.contains(token, total) }) return true
            carried = minOf(longest - 1, total)
            buffer.copyInto(buffer, 0, total - carried, total)
        }
    }

    private fun ByteArray.contains(token: ByteArray, length: Int): Boolean {
        if (token.isEmpty() || token.size > length) return false
        for (start in 0..length - token.size) {
            if (token.indices.all { this[start + it] == token[it] }) return true
        }
        return false
    }

    private fun isMotionPhoto(uri: Uri): Boolean = resolver.openInputStream(uri)?.buffered()?.use(
        ::containsMotionPhotoSignature
    ) ?: false

    private fun isMotionPhoto(file: File): Boolean = file.inputStream().buffered().use(
        ::containsMotionPhotoSignature
    )

    private fun containsMotionPhotoSignature(input: BufferedInputStream): Boolean {
        val bytes = ByteArray(MOTION_PHOTO_SNIFF_BYTES)
        var count = 0
        while (count < bytes.size) {
            val read = input.read(bytes, count, bytes.size - count)
            if (read <= 0) break
            count += read
        }
        val header = bytes.copyOf(count).toString(Charsets.ISO_8859_1)
        return "GCamera:MotionPhoto" in header ||
            "Camera:MotionPhoto" in header ||
            "GCamera:MicroVideo" in header ||
            "MotionPhoto_Data" in header
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
        const val MOTION_PHOTO_SNIFF_BYTES = 1024 * 1024
    }
}
