/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.mediaview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import com.awxkee.jxlcoder.JxlCoder
import com.dot.gallery.core.decoder.NativeRawDecoder
import com.dot.gallery.core.decoder.RawDevelopParams
import com.dot.gallery.core.decoder.format.ImageReencoder
import com.dot.gallery.core.decoder.format.ImageReencoderCodecs
import com.dot.gallery.core.decoder.format.Jp2ImageDecoder
import com.dot.gallery.core.decoder.format.PsdImageDecoder
import com.dot.gallery.core.decoder.format.SvgImageDecoder
import com.dot.gallery.core.decoder.format.TiffImageDecoder
import com.dot.gallery.domain.model.Media
import com.dot.gallery.data.entity.MediaMetadata
import com.dot.gallery.domain.model.Vault
import com.dot.gallery.app.presentation.frameextract.FrameDecoderSession
import com.dot.gallery.app.presentation.frameextract.FrameSourceKind
import com.dot.gallery.app.presentation.frameextract.FrameSourceSpec
import com.dot.gallery.core.presentation.util.resolveShareableUri
import com.radzivon.bartoshyk.avif.coder.HeifCoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.uuid.Uuid
import javax.inject.Inject
import com.dot.gallery.core.injection.Singleton
import com.dot.gallery.core.util.or
import java.util.UUID
import com.dot.gallery.data.data_source.KeychainHolder
import com.dot.gallery.app.presentation.frameextract.FrameSourceMaterializer
import com.dot.gallery.domain.util.isCloud
import com.dot.gallery.domain.util.isEncrypted
import com.dot.gallery.domain.util.isVideo
import com.dot.gallery.core.presentation.util.FileProviderShareDirs
import com.dot.gallery.core.presentation.util.createDecryptedTempFile
import com.dot.gallery.domain.util.isRaw
import com.dot.gallery.BuildConfig
import com.dot.gallery.feature.viewer.PreparedVisualSearchImage
import com.dot.gallery.feature.viewer.VisualSearchStage


/**
 * Turns the current viewer media into something a visual-search provider can consume and fires
 * the share intent at it.
 *
 * - Images pass through the regular share path (MediaStore URI passthrough / FileProvider / cloud
 *   download); exotic formats optionally detour through decode → re-encode to JPEG/PNG/WebP.
 * - Vault items materialize via the same decrypt-to-temp path as share.
 * - Videos contribute the frame at the current playback position, decoded through the frame
 *   picker's materialize → session pipeline.
 */
@Singleton
class VisualSearchLauncher @Inject constructor(
    private val context: Context,
    private val frameSourceMaterializer: FrameSourceMaterializer,
    private val keychainHolder: KeychainHolder,
) {

    suspend fun prepare(
        media: Media,
        metadata: MediaMetadata?,
        currentVault: Vault?,
        positionMs: Long?,
        convertTo: ImageReencoder.ImageWriteFormat?,
        onStage: (VisualSearchStage, Int?) -> Unit = { _, _ -> },
    ): PreparedVisualSearchImage = withContext(Dispatchers.IO) {
        sweepTempDir()
        when {
            media.isVideo -> prepareVideoFrame(media, metadata, currentVault, positionMs, onStage)

            convertTo != null -> {
                // Resolve a readable source first (decrypt for vault, download for cloud),
                // then decode → re-encode the exotic format.
                val sourceUri = resolveReadableSource(media, onStage)
                onStage(VisualSearchStage.CONVERTING, null)
                val bitmap = decodeForVisualSearch(sourceUri, media)
                    ?: throw IOException("Could not decode ${media.mimeType}")
                try {
                    val file = writeTempImage(bitmap, convertTo)
                    PreparedVisualSearchImage(fileProviderUri(file), convertTo.mimeType)
                } finally {
                    bitmap.recycle()
                }
            }

            else -> PreparedVisualSearchImage(
                uri = resolveReadableSource(media, onStage),
                mimeType = media.mimeType,
            )
        }
    }

    /** A URI the rest of the pipeline (or the provider itself) can open. */
    private suspend fun resolveReadableSource(
        media: Media,
        onStage: (VisualSearchStage, Int?) -> Unit,
    ): Uri = when {
        media.isEncrypted -> {
            onStage(VisualSearchStage.DECRYPTING, null)
            fileProviderUri(createDecryptedTempFile(media, keychainHolder, context.cacheDir))
        }

        media.isCloud -> {
            onStage(VisualSearchStage.DOWNLOADING, null)
            context.resolveShareableUri(media)
        }

        else -> context.resolveShareableUri(media)
    }

    private suspend fun prepareVideoFrame(
        media: Media,
        metadata: MediaMetadata?,
        currentVault: Vault?,
        positionMs: Long?,
        onStage: (VisualSearchStage, Int?) -> Unit,
    ): PreparedVisualSearchImage {
        val spec = FrameSourceSpec.from(
            media = media,
            metadata = metadata,
            currentVault = currentVault,
            initialPositionMs = positionMs,
        )
        val sourceStage = when (spec.sourceKind) {
            FrameSourceKind.CLOUD -> VisualSearchStage.DOWNLOADING
            FrameSourceKind.VAULT -> VisualSearchStage.DECRYPTING
            FrameSourceKind.LOCAL, FrameSourceKind.DOCUMENT -> VisualSearchStage.PREPARING
        }
        val prepared = frameSourceMaterializer.materialize(spec) { progress ->
            onStage(sourceStage, progress)
        }
        try {
            onStage(VisualSearchStage.EXTRACTING, null)
            val session = FrameDecoderSession(context, prepared.sourceUri, prepared.localFile)
            try {
                session.prepare()
                val identity = session.resolveInitial(preferredTimeUs = -1L, initialPositionMs = positionMs)
                val bitmap = session.decodeFullResolution(identity)
                try {
                    val file = writeTempImage(bitmap, ImageReencoder.ImageWriteFormat.JPEG)
                    return PreparedVisualSearchImage(fileProviderUri(file), "image/jpeg")
                } finally {
                    bitmap.recycle()
                }
            } finally {
                session.closeSafely()
            }
        } finally {
            prepared.deleteIfOwned()
        }
    }

    /**
     * Decode bytes with the repo's specialized decoders where the platform falls short
     * (RAW via LibRaw, TIFF/PSD/JP2/JXL via the bundled decoders, HEIF/AVIF via HeifCoder),
     * BitmapFactory covering whatever remains.
     */
    private fun decodeForVisualSearch(uri: Uri, media: Media): Bitmap? {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return null
        val mime = media.mimeType.lowercase()
        val ext = media.label.substringAfterLast('.', "").lowercase()
        return when {
            mime.contains("jxl") || ext == "jxl" ->
                JxlCoder.getSize(bytes)?.let { JxlCoder.decodeSampled(bytes, it.width, it.height) }

            mime.contains("heic") || mime.contains("heif") || mime.contains("avif") ||
                mime.contains("avis") || ext in HEIF_EXTENSIONS -> {
                val coder = HeifCoder()
                coder.getSize(bytes)?.let { coder.decodeSampled(bytes, it.width, it.height) }
            }

            mime.contains("tiff") || ext == "tif" || ext == "tiff" ->
                TiffImageDecoder.decode(bytes, 0, 0)

            mime.contains("jp2") || mime.contains("jpeg2000") || mime.contains("jpx") ||
                ext in JP2_EXTENSIONS ->
                Jp2ImageDecoder.decode(bytes, 0, 0)

            mime.contains("photoshop") || ext == "psd" || ext == "psb" ->
                PsdImageDecoder.decode(bytes, 0, 0)

            mime.contains("svg") || ext == "svg" ->
                SvgImageDecoder.decode(
                    bytes,
                    SvgImageDecoder.REGION_MAX_DIM,
                    SvgImageDecoder.REGION_MAX_DIM,
                )

            media.isRaw || mime.startsWith("image/x-") || mime.startsWith("image/vnd.") ->
                NativeRawDecoder.demosaic(bytes, RawDevelopParams.AUTO.copy(halfSize = true))
                    ?: NativeRawDecoder.getThumbnail(bytes)

            else -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
    }

    private fun writeTempImage(
        bitmap: Bitmap,
        format: ImageReencoder.ImageWriteFormat,
    ): File {
        val dir = tempDir()
        val file = File(dir, "vs_${Uuid.random()}.${format.fileExtension}")
        FileOutputStream(file).use { out ->
            ImageReencoderCodecs.writeToStream(bitmap, format, ENCODE_CONFIG, out)
        }
        return file
    }

    private fun fileProviderUri(file: File): Uri =
        FileProvider.getUriForFile(context, BuildConfig.CONTENT_AUTHORITY, file)

    private fun tempDir(): File =
        FileProviderShareDirs.cache(context, FileProviderShareDirs.VISUAL_SEARCH_DIRECTORY)

    /** Best-effort sweep of stale converted frames so the cache dir does not accumulate them. */
    private fun sweepTempDir(nowMs: Long = System.currentTimeMillis()) {
        tempDir().listFiles()?.forEach { file ->
            if (nowMs - file.lastModified() > MAX_TEMP_AGE_MS) file.delete()
        }
    }

    companion object {
        private const val MAX_TEMP_AGE_MS = 24L * 60L * 60L * 1000L
        private val ENCODE_CONFIG = ImageReencoder.ReencodeConfig(lossyQuality = 95)

        private val HEIF_EXTENSIONS = setOf("heic", "heif", "hif", "avif", "avis")
        private val JP2_EXTENSIONS = setOf("jp2", "jpx", "j2k", "jpf", "jpm", "j2c", "jpc")
    }
}
