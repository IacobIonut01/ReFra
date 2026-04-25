package com.dot.gallery.feature_node.domain.model

import android.content.ContentUris
import android.content.Context
import android.graphics.BitmapFactory
import android.location.Geocoder
import android.os.Bundle
import android.provider.MediaStore
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Exposure
import androidx.compose.material.icons.outlined.MotionPhotosOn
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Panorama
import androidx.compose.material.icons.outlined.PanoramaPhotosphere
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.dot.gallery.core.sandbox.IsolatedMetadataParser
import com.dot.gallery.core.sandbox.IsolatedMetadataService.Companion as Keys
import com.dot.gallery.feature_node.domain.util.getUri
import com.dot.gallery.feature_node.domain.util.isImage
import com.dot.gallery.feature_node.domain.util.isVideo
import com.dot.gallery.feature_node.presentation.util.formattedAddress
import com.dot.gallery.feature_node.presentation.util.printDebug
import com.dot.gallery.feature_node.presentation.util.printWarning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.math.RoundingMode
import java.text.DecimalFormat
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Entity(tableName = "media_metadata_core")
data class MediaMetadataCore(
    @PrimaryKey val mediaId: Long,
    val imageDescription: String?,
    val dateTimeOriginal: String?,
    val manufacturerName: String?,
    val modelName: String?,
    val aperture: String?,
    val exposureTime: String?,
    val iso: String?,
    val gpsLatitude: Double?,
    val gpsLongitude: Double?,
    val gpsLocationName: String?,
    val gpsLocationNameCountry: String?,
    val gpsLocationNameCity: String?,
    val imageWidth: Int,
    val imageHeight: Int,
    val imageResolutionX: Double?,
    val imageResolutionY: Double?,
    val resolutionUnit: Int?
)

@Entity(tableName = "media_metadata_video")
data class MediaMetadataVideo(
    @PrimaryKey val mediaId: Long,
    val durationMs: Long?,
    val videoWidth: Int?,
    val videoHeight: Int?,
    val frameRate: Float?,
    val bitRate: Int?
)

@Entity(tableName = "media_metadata_flags")
data class MediaMetadataFlags(
    @PrimaryKey val mediaId: Long,
    val isNightMode: Boolean,
    val isPanorama: Boolean,
    val isPhotosphere: Boolean,
    val isLongExposure: Boolean,
    val isMotionPhoto: Boolean
)

data class FullMediaMetadata(
    @Embedded val core: MediaMetadataCore,
    @Relation(
        parentColumn = "mediaId",
        entityColumn = "mediaId"
    ) val video: MediaMetadataVideo?,
    @Relation(
        parentColumn = "mediaId",
        entityColumn = "mediaId"
    ) val flags: MediaMetadataFlags?
)

@Entity(tableName = "media_metadata")
@Serializable
data class MediaMetadata(
    @PrimaryKey val mediaId: Long,

    // Image EXIF/XMP
    val imageDescription: String?,
    val dateTimeOriginal: String?,
    val manufacturerName: String?,
    val modelName: String?,
    val aperture: String?,
    val exposureTime: String?,
    val iso: String?,
    val gpsLatitude: Double?,
    val gpsLongitude: Double?,
    val gpsLocationName: String?,
    val gpsLocationNameCountry: String?,
    val gpsLocationNameCity: String?,
    val imageWidth: Int,
    val imageHeight: Int,
    val imageResolutionX: Double?,    // e.g. 72.0 PPI
    val imageResolutionY: Double?,    // e.g. 72.0 PPI
    val resolutionUnit: Int?,         // 2=inches, 3=cm

    // Video metadata
    val durationMs: Long?,
    val videoWidth: Int?,
    val videoHeight: Int?,
    val frameRate: Float?,
    val bitRate: Int?,

    // Feature flags
    val isNightMode: Boolean,
    val isPanorama: Boolean,
    val isPhotosphere: Boolean,
    val isLongExposure: Boolean,
    val isMotionPhoto: Boolean
) {

    val isRelevant: Boolean
        get() = isNightMode || isPanorama || isPhotosphere || isLongExposure || isMotionPhoto

    val formattedCords: String?
        get() = if (gpsLatitude != null && gpsLongitude != null) String.format(
            Locale.getDefault(), "%.3f, %.3f", gpsLatitude, gpsLongitude
        ) else null

    val imageMp: String
        get() {
            val roundingMP = DecimalFormat("#.#").apply { roundingMode = RoundingMode.DOWN }
            return roundingMP.format(imageWidth * imageHeight / 1024000.0)
        }

    val lensDescription: String?
        get() {
            return if (!manufacturerName.isNullOrEmpty() && !modelName.isNullOrEmpty() && aperture != null) {
                "$manufacturerName $modelName - $aperture - $imageMp MP"
            } else null
        }

    companion object {
        val PANORMA_KEYS = arrayOf(
            "ProjectionType",
            "FullPanoHeightPixels",
            "FullPanoWidthPixels"
        )
        val PHOTOSPERE_KEYS = arrayOf(
            "IsPhotosphere",
            "UsePanoramaViewer"
        )
        val PHOTOSPHERE_VALUES = arrayOf(
            "True",
            "com.google.android.apps.camera.gallery.specialtype.SpecialType-PHOTOSPHERE"
        )
        val LONG_EXPOSURE_KEYS = arrayOf(
            "BurstID",
            "CameraBurstID"
        )
    }


}

fun MediaMetadata.getIcon(): ImageVector? {
    return if (isNightMode) {
        Icons.Outlined.NightsStay
    } else if (isPanorama) {
        Icons.Outlined.Panorama
    } else if (isPhotosphere) {
        Icons.Outlined.PanoramaPhotosphere
    } else if (isLongExposure) {
        Icons.Outlined.Exposure
    } else if (isMotionPhoto) {
        Icons.Outlined.MotionPhotosOn
    } else {
        null
    }
}

@Suppress("DEPRECATION")
suspend fun Context.retrieveExtraMediaMetadata(
    isolatedParser: IsolatedMetadataParser,
    geocoder: Geocoder?,
    media: Media
): MediaMetadata? =
    withContext(Dispatchers.IO) {
        runCatching {
            val uri = media.getUri()
            val label = media.label
            printDebug("Retrieving extra metadata for ${media.id} - $uri")

            if (media.isImage) {
                val bundle = isolatedParser.parseImageMetadata(uri, label)
                    ?: return@runCatching null
                mediaMetadataFromImageBundle(media.id, bundle, geocoder, this@retrieveExtraMediaMetadata)
            } else if (media.isVideo) {
                val bundle = isolatedParser.parseVideoMetadata(uri)
                    ?: return@runCatching null
                mediaMetadataFromVideoBundle(media.id, bundle)
            } else {
                null
            }?.also {
                printDebug("Retrieved metadata for ${media.id} - $uri\n$it")
            }
        }.getOrElse {
            it.printStackTrace()
            null
        }
    }

/**
 * Converts the [Bundle] returned by the isolated image parser into a [MediaMetadata],
 * performing Geocoder lookup in the main process (Geocoder needs network + Play Services).
 */
@Suppress("DEPRECATION")
private suspend fun mediaMetadataFromImageBundle(
    mediaId: Long,
    bundle: Bundle,
    geocoder: Geocoder?,
    context: Context
): MediaMetadata {
    val gpsLatitude = if (bundle.containsKey(Keys.KEY_GPS_LAT)) bundle.getDouble(Keys.KEY_GPS_LAT) else null
    val gpsLongitude = if (bundle.containsKey(Keys.KEY_GPS_LON)) bundle.getDouble(Keys.KEY_GPS_LON) else null

    // Geocoding runs in the main app process (needs network + GMS)
    var gpsLocationName: String? = null
    var gpsLocationCountry: String? = null
    var gpsLocationCity: String? = null
    if (gpsLatitude != null && gpsLongitude != null) {
        if (geocoder != null) {
            suspendCoroutine {
                val address = geocoder.getFromLocation(gpsLatitude, gpsLongitude, 1)
                    .orEmpty().firstOrNull()
                gpsLocationName = address?.formattedAddress
                gpsLocationCountry = address?.countryName
                gpsLocationCity = address?.locality
                it.resume(Unit)
            }
        } else {
            printWarning("MetadataReader: Geocoder not available")
        }
    }

    var imgW = bundle.getInt(Keys.KEY_IMAGE_WIDTH, 0)
    var imgH = bundle.getInt(Keys.KEY_IMAGE_HEIGHT, 0)

    // BitmapFactory fallback runs in main process (needs ContentResolver)
    if (imgW == 0 || imgH == 0) {
        // We need the URI again for the fallback — reconstruct from mediaId
        // This is a rare path so we tolerate the extra cost
        runCatching {
            val uri = ContentUris.withAppendedId(
                MediaStore.Files.getContentUri("external"),
                mediaId
            )
            context.contentResolver.openInputStream(uri)?.use { fallbackStream ->
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(fallbackStream, null, options)
                if (options.outWidth > 0 && options.outHeight > 0) {
                    imgW = options.outWidth
                    imgH = options.outHeight
                }
            }
        }
    }

    return MediaMetadata(
        mediaId = mediaId,
        imageDescription = bundle.getString(Keys.KEY_IMAGE_DESCRIPTION),
        dateTimeOriginal = bundle.getString(Keys.KEY_DATETIME_ORIGINAL),
        manufacturerName = bundle.getString(Keys.KEY_MANUFACTURER),
        modelName = bundle.getString(Keys.KEY_MODEL),
        aperture = bundle.getString(Keys.KEY_APERTURE),
        exposureTime = bundle.getString(Keys.KEY_EXPOSURE_TIME),
        iso = bundle.getString(Keys.KEY_ISO),
        gpsLatitude = gpsLatitude,
        gpsLongitude = gpsLongitude,
        gpsLocationName = gpsLocationName,
        gpsLocationNameCountry = gpsLocationCountry,
        gpsLocationNameCity = gpsLocationCity,
        imageWidth = imgW,
        imageHeight = imgH,
        imageResolutionX = if (bundle.containsKey(Keys.KEY_RES_X)) bundle.getDouble(Keys.KEY_RES_X) else null,
        imageResolutionY = if (bundle.containsKey(Keys.KEY_RES_Y)) bundle.getDouble(Keys.KEY_RES_Y) else null,
        resolutionUnit = if (bundle.containsKey(Keys.KEY_RES_UNIT)) bundle.getInt(Keys.KEY_RES_UNIT) else null,
        durationMs = null,
        videoWidth = null,
        videoHeight = null,
        frameRate = null,
        bitRate = null,
        isNightMode = bundle.getBoolean(Keys.KEY_IS_NIGHT_MODE),
        isPanorama = bundle.getBoolean(Keys.KEY_IS_PANORAMA),
        isPhotosphere = bundle.getBoolean(Keys.KEY_IS_PHOTOSPHERE),
        isLongExposure = bundle.getBoolean(Keys.KEY_IS_LONG_EXPOSURE),
        isMotionPhoto = bundle.getBoolean(Keys.KEY_IS_MOTION_PHOTO)
    )
}

private fun mediaMetadataFromVideoBundle(mediaId: Long, bundle: Bundle): MediaMetadata {
    return MediaMetadata(
        mediaId = mediaId,
        imageDescription = null,
        dateTimeOriginal = null,
        manufacturerName = null,
        modelName = null,
        aperture = null,
        exposureTime = null,
        iso = null,
        gpsLatitude = null,
        gpsLongitude = null,
        gpsLocationName = null,
        gpsLocationNameCountry = null,
        gpsLocationNameCity = null,
        imageWidth = 0,
        imageHeight = 0,
        imageResolutionX = null,
        imageResolutionY = null,
        resolutionUnit = null,
        durationMs = if (bundle.containsKey(Keys.KEY_DURATION_MS)) bundle.getLong(Keys.KEY_DURATION_MS) else null,
        videoWidth = if (bundle.containsKey(Keys.KEY_VIDEO_WIDTH)) bundle.getInt(Keys.KEY_VIDEO_WIDTH) else null,
        videoHeight = if (bundle.containsKey(Keys.KEY_VIDEO_HEIGHT)) bundle.getInt(Keys.KEY_VIDEO_HEIGHT) else null,
        frameRate = if (bundle.containsKey(Keys.KEY_FRAME_RATE)) bundle.getFloat(Keys.KEY_FRAME_RATE) else null,
        bitRate = if (bundle.containsKey(Keys.KEY_BIT_RATE)) bundle.getInt(Keys.KEY_BIT_RATE) else null,
        isNightMode = false,
        isPanorama = false,
        isPhotosphere = false,
        isLongExposure = false,
        isMotionPhoto = false
    )
}

fun MediaMetadata.toCore() = MediaMetadataCore(
    mediaId = mediaId,
    imageDescription = imageDescription,
    dateTimeOriginal = dateTimeOriginal,
    manufacturerName = manufacturerName,
    modelName = modelName,
    aperture = aperture,
    exposureTime = exposureTime,
    iso = iso,
    gpsLatitude = gpsLatitude,
    gpsLongitude = gpsLongitude,
    gpsLocationName = gpsLocationName,
    gpsLocationNameCountry = gpsLocationNameCountry,
    gpsLocationNameCity = gpsLocationNameCity,
    imageWidth = imageWidth,
    imageHeight = imageHeight,
    imageResolutionX = imageResolutionX,
    imageResolutionY = imageResolutionY,
    resolutionUnit = resolutionUnit
)

fun MediaMetadata.toVideo() = MediaMetadataVideo(
    mediaId = mediaId,
    durationMs = durationMs,
    videoWidth = videoWidth,
    videoHeight = videoHeight,
    frameRate = frameRate,
    bitRate = bitRate
)

fun MediaMetadata.toFlags() = MediaMetadataFlags(
    mediaId = mediaId,
    isNightMode = isNightMode,
    isPanorama = isPanorama,
    isPhotosphere = isPhotosphere,
    isLongExposure = isLongExposure,
    isMotionPhoto = isMotionPhoto
)

fun FullMediaMetadata.toMediaMetadata(): MediaMetadata {
    val v = video
    val f = flags
    return MediaMetadata(
        mediaId = core.mediaId,
        imageDescription = core.imageDescription,
        dateTimeOriginal = core.dateTimeOriginal,
        manufacturerName = core.manufacturerName,
        modelName = core.modelName,
        aperture = core.aperture,
        exposureTime = core.exposureTime,
        iso = core.iso,
        gpsLatitude = core.gpsLatitude,
        gpsLongitude = core.gpsLongitude,
        gpsLocationName = core.gpsLocationName,
        gpsLocationNameCountry = core.gpsLocationNameCountry,
        gpsLocationNameCity = core.gpsLocationNameCity,
        imageWidth = core.imageWidth,
        imageHeight = core.imageHeight,
        imageResolutionX = core.imageResolutionX,
        imageResolutionY = core.imageResolutionY,
        resolutionUnit = core.resolutionUnit,
        durationMs = v?.durationMs,
        videoWidth = v?.videoWidth,
        videoHeight = v?.videoHeight,
        frameRate = v?.frameRate,
        bitRate = v?.bitRate,
        isNightMode = f?.isNightMode ?: false,
        isPanorama = f?.isPanorama ?: false,
        isPhotosphere = f?.isPhotosphere ?: false,
        isLongExposure = f?.isLongExposure ?: false,
        isMotionPhoto = f?.isMotionPhoto ?: false
    )
}