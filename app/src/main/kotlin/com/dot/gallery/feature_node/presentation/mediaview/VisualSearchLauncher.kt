/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.mediaview

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import com.awxkee.jxlcoder.JxlCoder
import com.dot.gallery.BuildConfig
import com.dot.gallery.core.Settings
import com.dot.gallery.core.decoder.NativeRawDecoder
import com.dot.gallery.core.decoder.RawDevelopParams
import com.dot.gallery.core.decoder.format.ImageReencoder
import com.dot.gallery.core.decoder.format.Jp2ImageDecoder
import com.dot.gallery.core.decoder.format.PsdImageDecoder
import com.dot.gallery.core.decoder.format.SvgImageDecoder
import com.dot.gallery.core.decoder.format.TiffImageDecoder
import com.dot.gallery.feature_node.data.data_source.KeychainHolder
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.MediaMetadata
import com.dot.gallery.feature_node.domain.model.Vault
import com.dot.gallery.feature_node.domain.util.isCloud
import com.dot.gallery.feature_node.domain.util.isEncrypted
import com.dot.gallery.feature_node.domain.util.isRaw
import com.dot.gallery.feature_node.domain.util.isVideo
import com.dot.gallery.feature_node.presentation.frameextract.FrameDecoderSession
import com.dot.gallery.feature_node.presentation.frameextract.FrameSourceKind
import com.dot.gallery.feature_node.presentation.frameextract.FrameSourceMaterializer
import com.dot.gallery.feature_node.presentation.frameextract.FrameSourceSpec
import com.dot.gallery.feature_node.presentation.util.createDecryptedTempFile
import com.dot.gallery.feature_node.presentation.util.resolveShareableUri
import com.radzivon.bartoshyk.avif.coder.HeifCoder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** What the progress chip reports while a shareable image is being produced. */
enum class VisualSearchStage { DOWNLOADING, DECRYPTING, PREPARING, EXTRACTING, CONVERTING }

/** A shareable image ready to hand to a provider — URI plus the MIME the intent must carry. */
data class PreparedVisualSearchImage(val uri: Uri, val mimeType: String)

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
    @param:ApplicationContext private val context: Context,
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
        val file = File(dir, "vs_${UUID.randomUUID()}.${format.fileExtension}")
        FileOutputStream(file).use { out ->
            ImageReencoder.writeToStream(bitmap, format, ENCODE_CONFIG, out)
        }
        return file
    }

    private fun fileProviderUri(file: File): Uri =
        FileProvider.getUriForFile(context, BuildConfig.CONTENT_AUTHORITY, file)

    private fun tempDir(): File = File(context.cacheDir, TEMP_DIR_NAME).apply { mkdirs() }

    /** Best-effort sweep of stale converted frames so the cache dir does not accumulate them. */
    private fun sweepTempDir(nowMs: Long = System.currentTimeMillis()) {
        tempDir().listFiles()?.forEach { file ->
            if (nowMs - file.lastModified() > MAX_TEMP_AGE_MS) file.delete()
        }
    }

    companion object {
        private const val TEMP_DIR_NAME = "visual_search"
        private const val MAX_TEMP_AGE_MS = 24L * 60L * 60L * 1000L
        private val ENCODE_CONFIG = ImageReencoder.ReencodeConfig(lossyQuality = 95)

        private val HEIF_EXTENSIONS = setOf("heic", "heif", "hif", "avif", "avis")
        private val JP2_EXTENSIONS = setOf("jp2", "jpx", "j2k", "jpf", "jpm", "j2c", "jpc")
    }
}

/** Maps the stored convert-format preference onto a concrete encoder format. */
fun String.toVisualSearchWriteFormat(): ImageReencoder.ImageWriteFormat = when (this) {
    Settings.Misc.VISUAL_SEARCH_FORMAT_PNG -> ImageReencoder.ImageWriteFormat.PNG
    Settings.Misc.VISUAL_SEARCH_FORMAT_WEBP -> ImageReencoder.ImageWriteFormat.WEBP_LOSSY
    else -> ImageReencoder.ImageWriteFormat.JPEG
}

/**
 * Fires `ACTION_SEND` pinned to the resolved provider activity. Call with an Activity context so
 * the provider lands on the viewer's task.
 */
fun Context.launchVisualSearch(target: VisualSearchTarget, uri: Uri, mimeType: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        setClassName(target.packageName, target.activityName)
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        // ClipData carries the grant — a bare EXTRA_STREAM loses it on some targets.
        clipData = ClipData.newUri(contentResolver, target.label, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(intent)
}
