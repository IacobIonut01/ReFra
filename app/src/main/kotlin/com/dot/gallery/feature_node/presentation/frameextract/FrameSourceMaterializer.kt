package com.dot.gallery.feature_node.presentation.frameextract

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.StatFs
import android.system.Os
import android.system.OsConstants
import androidx.exifinterface.media.ExifInterface
import com.dot.gallery.cloud.core.CloudUri
import com.dot.gallery.cloud.core.ProviderRegistry
import com.dot.gallery.cloud.core.capabilities.RemoteMediaProvider
import com.dot.gallery.cloud.image.CloudFetcherRegistryHolder
import com.dot.gallery.cloud.offline.CloudMediaCache
import com.dot.gallery.core.util.openUnredactedFileDescriptor
import com.dot.gallery.feature_node.data.data_source.KeychainHolder
import com.dot.gallery.feature_node.domain.model.Vault
import com.dot.gallery.feature_node.domain.util.MotionPhotoHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

class FrameSourceException(
    message: String,
    val retryable: Boolean = false,
) : IOException(message)

@Singleton
class FrameSourceMaterializer @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val providerRegistry: ProviderRegistry,
    private val keychainHolder: KeychainHolder,
) {
    private val sourceDirectory = FrameSourceCleanup.directory(context)

    suspend fun materialize(
        spec: FrameSourceSpec,
        onProgress: (Int?) -> Unit = {},
    ): PreparedFrameSource = withContext(Dispatchers.IO) {
        FrameSourceCleanup.sweep(context)
        sourceDirectory.mkdirs()
        val prepared = when (spec.sourceKind) {
            FrameSourceKind.CLOUD -> prepareCloud(spec, onProgress)
            FrameSourceKind.VAULT -> prepareVault(spec, onProgress)
            FrameSourceKind.LOCAL,
            FrameSourceKind.DOCUMENT -> prepareLocal(spec, onProgress)
        }
        val playable = if (spec.isMotionPhotoCandidate) prepareMotionPhoto(prepared, onProgress) else prepared
        resolveVideoLocation(playable).also(FrameSourceCleanup::acquire)
    }

    private fun resolveVideoLocation(prepared: PreparedFrameSource): PreparedFrameSource {
        if (prepared.source.latitude != null && prepared.source.longitude != null) return prepared
        val retriever = MediaMetadataRetriever()
        return try {
            prepared.localFile?.takeIf(File::exists)?.let {
                retriever.setDataSource(it.absolutePath)
            } ?: retriever.setDataSource(context, prepared.sourceUri)
            val location = VideoLocationParser.parse(
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION)
            ) ?: return prepared
            prepared.copy(
                source = prepared.source.copy(
                    latitude = location.first,
                    longitude = location.second,
                )
            )
        } catch (_: Throwable) {
            prepared
        } finally {
            runCatching { retriever.release() }
        }
    }

    private suspend fun prepareLocal(
        spec: FrameSourceSpec,
        onProgress: (Int?) -> Unit,
    ): PreparedFrameSource {
        val uri = Uri.parse(spec.uri)
        if (spec.sourceKind == FrameSourceKind.LOCAL &&
            spec.mimeType.startsWith("video/") && isSeekable(uri)
        ) {
            onProgress(100)
            return PreparedFrameSource(
                sourceUri = uri,
                localFile = uri.takeIf { it.scheme == "file" }?.path?.let(::File),
                ownership = FrameSourceOwnership.DIRECT,
                ownershipToken = "direct:${spec.mediaId}",
                source = spec,
            )
        }
        val file = copyUriToSessionFile(uri, spec.sourceSize, onProgress)
        return PreparedFrameSource(
            sourceUri = Uri.fromFile(file),
            localFile = file,
            ownership = FrameSourceOwnership.SESSION,
            ownershipToken = file.nameWithoutExtension,
            source = spec,
        )
    }

    private suspend fun prepareCloud(
        spec: FrameSourceSpec,
        onProgress: (Int?) -> Unit,
    ): PreparedFrameSource {
        val parsed = CloudUri.parse(spec.uri)
            ?: throw FrameSourceException("Invalid cloud source")
        val provider = (
            if (parsed.configId > 0L) providerRegistry.getByConfigId(parsed.configId)
            else providerRegistry.get(parsed.providerType)
        ) as? RemoteMediaProvider
        if (provider == null || provider.providerType != parsed.providerType) {
            throw FrameSourceException("Cloud account is unavailable", retryable = true)
        }
        ensureStorage(spec.sourceSize.takeIf { it > 0L } ?: MIN_REMOTE_RESERVE)
        val partFile = sessionFile("part")
        val targetFile = File(sourceDirectory, "${partFile.nameWithoutExtension}.source")
        try {
            val request = Request.Builder()
                .url(provider.getOriginalUrl(parsed.remoteId))
                .get()
                .apply {
                    provider.getAuthHeaders().forEach { (key, value) -> addHeader(key, value) }
                    addHeader(
                        CloudMediaCache.HEADER_KEY,
                        CloudMediaCache.keyFor(
                            parsed.providerType,
                            parsed.configId,
                            parsed.remoteId,
                            "original",
                        )
                    )
                }
                .build()
            val client = CloudFetcherRegistryHolder.okHttpClient
                ?: throw FrameSourceException("Cloud networking is unavailable", retryable = true)
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw FrameSourceException(
                        "Cloud download failed (${response.code})",
                        retryable = response.code >= 500 || response.code == 408 || response.code == 429,
                    )
                }
                val body = response.body
                val total = body.contentLength().takeIf { it > 0L }
                total?.let { ensureStorage(if (it > Long.MAX_VALUE / 2L) Long.MAX_VALUE else it * 2L) }
                body.byteStream().use { input ->
                    FileOutputStream(partFile).use { output ->
                        copyWithProgress(input, output, total, onProgress)
                    }
                }
            }
            if (!partFile.renameTo(targetFile)) throw IOException("Unable to publish cloud source")
            return PreparedFrameSource(
                sourceUri = Uri.fromFile(targetFile),
                localFile = targetFile,
                ownership = FrameSourceOwnership.SESSION,
                ownershipToken = targetFile.nameWithoutExtension,
                source = spec,
            )
        } catch (error: Throwable) {
            partFile.delete()
            targetFile.delete()
            if (error is CancellationException) throw error
            if (error is FrameSourceException) throw error
            throw FrameSourceException(error.message ?: "Cloud source preparation failed", retryable = true)
        }
    }

    private suspend fun prepareVault(
        spec: FrameSourceSpec,
        onProgress: (Int?) -> Unit,
    ): PreparedFrameSource {
        val encryptedFile = Uri.parse(spec.uri).path?.let(::File)
            ?.takeIf(File::exists)
            ?: throw FrameSourceException("Vault source is unavailable")
        val expectedSize = spec.sourceSize.takeIf { it > 0L } ?: encryptedFile.length()
        ensureStorage(expectedSize.coerceAtLeast(MIN_REMOTE_RESERVE))
        val partFile = sessionFile("part")
        val targetFile = File(sourceDirectory, "${partFile.nameWithoutExtension}.source")
        try {
            if (keychainHolder.isPortableFile(encryptedFile)) {
                val vaultId = spec.vaultId
                    ?: encryptedFile.parentFile?.name
                    ?: throw FrameSourceException("Vault identity is unavailable")
                val vault = Vault(UUID.fromString(vaultId), "")
                FileOutputStream(partFile).use { output ->
                    onProgress(null)
                    keychainHolder.decryptPortableStream(vault, encryptedFile, output)
                }
            } else {
                val decrypted = keychainHolder.decryptVaultMedia(encryptedFile)
                try {
                    decrypted.openStream().use { input ->
                        FileOutputStream(partFile).use { output ->
                            copyWithProgress(input, output, expectedSize, onProgress)
                        }
                    }
                } finally {
                    decrypted.cleanup()
                }
            }
            if (!partFile.renameTo(targetFile)) throw IOException("Unable to publish vault source")
            onProgress(100)
            return PreparedFrameSource(
                sourceUri = Uri.fromFile(targetFile),
                localFile = targetFile,
                ownership = FrameSourceOwnership.SESSION,
                ownershipToken = targetFile.nameWithoutExtension,
                source = spec,
            )
        } catch (error: Throwable) {
            partFile.delete()
            targetFile.delete()
            if (error is CancellationException) throw error
            throw FrameSourceException(error.message ?: "Vault source preparation failed", retryable = true)
        }
    }

    private suspend fun prepareMotionPhoto(
        prepared: PreparedFrameSource,
        onProgress: (Int?) -> Unit,
    ): PreparedFrameSource {
        onProgress(null)
        val sourceWithLocation = resolveImageLocation(prepared)
        val info = MotionPhotoHelper.parseInfo(context, prepared.sourceUri)
            ?: run {
                prepared.deleteIfOwned()
                throw FrameSourceException("No embedded motion clip")
            }
        val videoFile = prepared.localFile?.takeIf { it.exists() }
            ?.let { MotionPhotoHelper.extractVideoFromFile(it, info, sourceDirectory) }
            ?: MotionPhotoHelper.extractVideo(context, prepared.sourceUri, info, sourceDirectory)
        if (videoFile == null) {
            prepared.deleteIfOwned()
            throw FrameSourceException("Embedded motion clip is invalid")
        }
        prepared.deleteIfOwned()
        onProgress(100)
        return PreparedFrameSource(
            sourceUri = Uri.fromFile(videoFile),
            localFile = videoFile,
            ownership = FrameSourceOwnership.SESSION,
            ownershipToken = videoFile.nameWithoutExtension,
            source = sourceWithLocation.source,
            motionPhotoInfo = info,
        )
    }

    private fun resolveImageLocation(prepared: PreparedFrameSource): PreparedFrameSource {
        if (prepared.source.latitude != null && prepared.source.longitude != null) return prepared
        val location = runCatching {
            prepared.localFile?.takeIf(File::exists)?.let { file ->
                ExifInterface(file).latLong
            } ?: context.openUnredactedFileDescriptor(prepared.sourceUri)?.use { descriptor ->
                ExifInterface(descriptor.fileDescriptor).latLong
            }
        }.getOrNull() ?: return prepared
        return prepared.copy(
            source = prepared.source.copy(
                latitude = location[0],
                longitude = location[1],
            )
        )
    }

    private suspend fun copyUriToSessionFile(
        uri: Uri,
        expectedSize: Long,
        onProgress: (Int?) -> Unit,
    ): File {
        ensureStorage(expectedSize.takeIf { it > 0L } ?: MIN_REMOTE_RESERVE)
        val partFile = sessionFile("part")
        val targetFile = File(sourceDirectory, "${partFile.nameWithoutExtension}.source")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(partFile).use { output ->
                    copyWithProgress(input, output, expectedSize.takeIf { it > 0L }, onProgress)
                }
            } ?: throw FrameSourceException("Source cannot be opened")
            if (!partFile.renameTo(targetFile)) throw IOException("Unable to publish source")
            return targetFile
        } catch (error: Throwable) {
            partFile.delete()
            targetFile.delete()
            if (error is CancellationException) throw error
            if (error is FrameSourceException) throw error
            throw FrameSourceException(error.message ?: "Source preparation failed", retryable = true)
        }
    }

    private suspend fun copyWithProgress(
        input: java.io.InputStream,
        output: java.io.OutputStream,
        total: Long?,
        onProgress: (Int?) -> Unit,
    ) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var copied = 0L
        onProgress(total?.let { 0 })
        while (true) {
            coroutineContext.ensureActive()
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            copied += read
            onProgress(total?.let { ((copied * 100L) / it).toInt().coerceIn(0, 100) })
        }
        output.flush()
    }

    private fun isSeekable(uri: Uri): Boolean = try {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
            Os.lseek(descriptor.fileDescriptor, 0L, OsConstants.SEEK_CUR)
            true
        } ?: false
    } catch (_: Throwable) {
        false
    }

    private fun ensureStorage(requiredBytes: Long) {
        val available = StatFs(sourceDirectory.absolutePath).availableBytes
        val required = requiredBytes.coerceAtLeast(MIN_REMOTE_RESERVE)
        val conservative = if (required > Long.MAX_VALUE - MIN_FREE_AFTER_PREPARE) {
            Long.MAX_VALUE
        } else required + MIN_FREE_AFTER_PREPARE
        if (available < conservative) throw FrameSourceException("Not enough free storage")
    }

    private fun sessionFile(extension: String): File =
        File(sourceDirectory, "${UUID.randomUUID()}.$extension")

    companion object {
        private const val MIN_REMOTE_RESERVE = 64L * 1024L * 1024L
        private const val MIN_FREE_AFTER_PREPARE = 128L * 1024L * 1024L
    }
}

object FrameSourceCleanup {
    private const val MAX_AGE_MS = 24L * 60L * 60L * 1000L
    private val activePaths = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    fun directory(context: Context): File = File(context.cacheDir, "frame_picker_sources")

    fun acquire(source: PreparedFrameSource) {
        if (source.ownership == FrameSourceOwnership.SESSION) {
            source.localFile?.let { activePaths += it.absolutePath }
        }
    }

    fun release(file: File) {
        activePaths -= file.absolutePath
    }

    fun sweep(context: Context, nowMs: Long = System.currentTimeMillis()) {
        directory(context).listFiles()?.forEach { file ->
            if (file.absolutePath !in activePaths && nowMs - file.lastModified() > MAX_AGE_MS) {
                file.delete()
            }
        }
    }
}
