package com.dot.gallery.feature_node.presentation.frameextract

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Parcelable
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.MediaMetadata
import com.dot.gallery.feature_node.domain.model.Vault
import com.dot.gallery.feature_node.domain.util.getUri
import com.dot.gallery.feature_node.domain.util.isCloud
import com.dot.gallery.feature_node.domain.util.isEncrypted
import kotlinx.parcelize.Parcelize
import java.io.File

@Parcelize
enum class FrameSourceKind : Parcelable {
    LOCAL,
    DOCUMENT,
    CLOUD,
    VAULT
}

@Parcelize
data class FrameSourceSpec(
    val mediaId: Long,
    val label: String,
    val uri: String,
    val mimeType: String,
    val sourceKind: FrameSourceKind,
    val relativePath: String,
    val captureTimestampMs: Long,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val motionPhotoHint: Boolean = false,
    val preferredPresentationTimeUs: Long = -1L,
    val initialPositionMs: Long? = null,
    val sourceSize: Long = 0L,
    val vaultId: String? = null,
) : Parcelable {
    val isMotionPhotoCandidate: Boolean
        get() = motionPhotoHint || mimeType.startsWith("image/")

    companion object {
        fun from(
            media: Media,
            metadata: MediaMetadata? = null,
            currentVault: Vault? = null,
            motionPhotoHint: Boolean = false,
            preferredPresentationTimeUs: Long = -1L,
            initialPositionMs: Long? = null,
        ): FrameSourceSpec {
            val sourceKind = when {
                media.isCloud -> FrameSourceKind.CLOUD
                media.isEncrypted -> FrameSourceKind.VAULT
                media.getUri().scheme == "content" &&
                    media.getUri().authority != "media" -> FrameSourceKind.DOCUMENT
                else -> FrameSourceKind.LOCAL
            }
            return FrameSourceSpec(
                mediaId = media.id,
                label = media.label,
                uri = media.getUri().toString(),
                mimeType = media.mimeType,
                sourceKind = sourceKind,
                relativePath = media.relativePath,
                captureTimestampMs = media.takenTimestamp ?: media.timestamp * 1000L,
                latitude = metadata?.gpsLatitude,
                longitude = metadata?.gpsLongitude,
                motionPhotoHint = motionPhotoHint,
                preferredPresentationTimeUs = preferredPresentationTimeUs,
                initialPositionMs = initialPositionMs,
                sourceSize = media.size,
                vaultId = currentVault?.uuid?.toString(),
            )
        }
    }
}

enum class FrameSourceOwnership {
    DIRECT,
    SESSION
}

data class PreparedFrameSource(
    val sourceUri: Uri,
    val localFile: File?,
    val ownership: FrameSourceOwnership,
    val ownershipToken: String,
    val source: FrameSourceSpec,
    val motionPhotoInfo: com.dot.gallery.feature_node.domain.util.MotionPhotoInfo? = null,
) {
    val isMotionPhoto: Boolean get() = motionPhotoInfo != null

    fun deleteIfOwned() {
        if (ownership == FrameSourceOwnership.SESSION) {
            localFile?.let {
                FrameSourceCleanup.release(it)
                it.delete()
            }
        }
    }
}

@Parcelize
data class FrameIdentity(
    val frameIndex: Int,
    val presentationTimeUs: Long,
) : Parcelable, Comparable<FrameIdentity> {
    override fun compareTo(other: FrameIdentity): Int =
        compareValuesBy(this, other, FrameIdentity::presentationTimeUs, FrameIdentity::frameIndex)

    fun encode(): String = "$frameIndex:$presentationTimeUs"

    companion object {
        fun decode(value: String): FrameIdentity? {
            val separator = value.indexOf(':')
            if (separator <= 0) return null
            val frameIndex = value.substring(0, separator).toIntOrNull() ?: return null
            val presentationTimeUs = value.substring(separator + 1).toLongOrNull() ?: return null
            return FrameIdentity(frameIndex, presentationTimeUs)
        }
    }
}

enum class FrameExportFormat(
    val persistedValue: String,
    val extension: String,
    val mimeType: String,
    val quality: Int,
    val compressFormat: Bitmap.CompressFormat,
) {
    JPEG("jpeg", "jpg", "image/jpeg", 95, Bitmap.CompressFormat.JPEG),
    PNG("png", "png", "image/png", 100, Bitmap.CompressFormat.PNG);

    companion object {
        fun fromPersisted(value: String?): FrameExportFormat =
            entries.firstOrNull { it.persistedValue == value } ?: JPEG
    }
}

data class FrameVideoMetadata(
    val durationUs: Long,
    val frameCount: Int?,
    val frameRate: Float?,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
    val isConstantFrameRate: Boolean,
    val isHdr: Boolean,
    val isDolbyVision: Boolean = false,
)

data class FramePreview(
    val identity: FrameIdentity,
    val bitmap: Bitmap,
)

sealed interface FramePickerUiState {
    data class PreparingSource(val progress: Int?) : FramePickerUiState
    data class Ready(
        val metadata: FrameVideoMetadata,
        val currentFrame: FramePreview?,
        val selection: List<FrameIdentity>,
        val selectedThumbnails: List<FramePreview>,
        val filmstrip: List<FramePreview>,
        val isPreviewLoading: Boolean,
        val isPlaying: Boolean,
        val format: FrameExportFormat,
        val preferredTimeUs: Long,
    ) : FramePickerUiState
    data class Exporting(val done: Int, val total: Int, val phase: String) : FramePickerUiState
    data class PartialSuccess(val saved: List<Uri>, val failed: Int, val warnings: Int) : FramePickerUiState
    data class Success(val savedUris: List<Uri>, val warnings: Int) : FramePickerUiState
    data class Failure(val reason: String, val retryable: Boolean) : FramePickerUiState
}

sealed interface SelectionChange {
    data class Updated(val frames: List<FrameIdentity>) : SelectionChange
    data object LimitReached : SelectionChange
}

object VideoLocationParser {
    private val pattern = Regex("^([+-]\\d{2,3}(?:\\.\\d+)?)([+-]\\d{3}(?:\\.\\d+)?)(?:[+-]\\d+(?:\\.\\d+)?)?/?$")

    fun parse(value: String?): Pair<Double, Double>? {
        val match = value?.trim()?.let(pattern::matchEntire) ?: return null
        val latitude = match.groupValues[1].toDoubleOrNull() ?: return null
        val longitude = match.groupValues[2].toDoubleOrNull() ?: return null
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
        return latitude to longitude
    }
}

object FrameSelectionReducer {
    const val MAX_SELECTION = 50

    fun toggle(
        selection: Collection<FrameIdentity>,
        frame: FrameIdentity,
    ): SelectionChange {
        if (frame in selection) {
            return SelectionChange.Updated(selection.filterNot { it == frame }.sorted())
        }
        if (selection.size >= MAX_SELECTION) return SelectionChange.LimitReached
        return SelectionChange.Updated((selection + frame).distinct().sorted())
    }

    fun restore(values: Collection<String>): List<FrameIdentity> =
        values.mapNotNull(FrameIdentity::decode)
            .distinct()
            .sorted()
            .take(MAX_SELECTION)
}
