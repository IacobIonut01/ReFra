package com.dot.gallery.feature_node.domain.model

import android.content.ContentUris
import android.content.Context
import android.graphics.BitmapFactory
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Exposure
import androidx.compose.material.icons.outlined.MotionPhotosOn
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Panorama
import androidx.compose.material.icons.outlined.PanoramaPhotosphere
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.dot.gallery.core.decoder.format.SpecialFormatProbe
import com.dot.gallery.core.util.SafeExif
import com.dot.gallery.core.sandbox.IsolatedMetadataParser
import com.dot.gallery.core.sandbox.IsolatedMetadataService.Companion as Keys
import com.dot.gallery.feature_node.domain.util.getUri
import com.dot.gallery.feature_node.domain.util.isImage
import com.dot.gallery.feature_node.domain.util.isVideo
import com.dot.gallery.feature_node.presentation.util.formattedAddress
import com.dot.gallery.feature_node.presentation.util.locationGroupName
import com.dot.gallery.feature_node.presentation.util.printDebug
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.math.RoundingMode
import java.text.DecimalFormat
import java.util.Locale
import kotlin.math.roundToInt

@Entity(tableName = "media_metadata_core")
data class MediaMetadataCore(
    @PrimaryKey val mediaId: Long,
    val imageDescription: String?,
    val dateTimeOriginal: String?,
    val manufacturerName: String?,
    val modelName: String?,
    @ColumnInfo(defaultValue = "")
    val lensModel: String? = null,
    val aperture: String?,
    val exposureTime: String?,
    val iso: String?,
    @ColumnInfo(defaultValue = "0.0")
    val focalLength: Double? = null,
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
    val lensModel: String? = null,
    val aperture: String?,
    val exposureTime: String?,
    val iso: String?,
    val focalLength: Double? = null,
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

    val searchableText: String
        get() = buildString {
            imageDescription?.let { append(it).append(' ') }
            gpsLocationName?.let { append(it).append(' ') }
            gpsLocationNameCity?.let { append(it).append(' ') }
            gpsLocationNameCountry?.let { append(it).append(' ') }
            manufacturerName?.let { append(it).append(' ') }
            modelName?.let { append(it) }
        }

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

enum class MetadataParsingPolicy {
    ON_DEMAND_COMPATIBLE,
    BULK_ISOLATED_ONLY
}

internal fun metadataParsingPolicy(bulk: Boolean): MetadataParsingPolicy =
    if (bulk) MetadataParsingPolicy.BULK_ISOLATED_ONLY else MetadataParsingPolicy.ON_DEMAND_COMPATIBLE

internal suspend fun <T> bestEffortReverseGeocode(
    enabled: Boolean,
    latitude: Double?,
    longitude: Double?,
    lookup: suspend (Double, Double) -> T?
): T? {
    if (!enabled || latitude == null || longitude == null) return null
    return try {
        lookup(latitude, longitude)
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        null
    }
}

@Suppress("DEPRECATION")
suspend fun Context.retrieveExtraMediaMetadata(
    isolatedParser: IsolatedMetadataParser,
    geocoder: Geocoder?,
    media: Media,
    usePerFileIsolation: Boolean = false,
    policy: MetadataParsingPolicy = MetadataParsingPolicy.ON_DEMAND_COMPATIBLE
): MediaMetadata? =
    withContext(Dispatchers.IO) {
        runCatching {
            val uri = media.getUri()
            val label = media.label
            printDebug("Retrieving extra metadata for ${media.id} - $uri (perFile=$usePerFileIsolation)")

            if (media.isImage) {
                val bundle = if (usePerFileIsolation) {
                    isolatedParser.parseImageMetadataPerFile(
                        uri,
                        label,
                        media.id,
                        fallbackToShared = policy == MetadataParsingPolicy.ON_DEMAND_COMPATIBLE
                    )
                } else {
                    isolatedParser.parseImageMetadata(uri, label)
                }
                if (bundle != null) {
                    mediaMetadataFromImageBundle(
                        media.id,
                        bundle,
                        geocoder,
                        this@retrieveExtraMediaMetadata,
                        allowInProcessFallback = policy == MetadataParsingPolicy.ON_DEMAND_COMPATIBLE
                    )
                } else if (policy == MetadataParsingPolicy.ON_DEMAND_COMPATIBLE) {
                    buildFallbackImageMetadata(media.id, uri, geocoder, this@retrieveExtraMediaMetadata)
                } else null
            } else if (media.isVideo) {
                val bundle = if (usePerFileIsolation) {
                    isolatedParser.parseVideoMetadataPerFile(
                        uri,
                        media.id,
                        fallbackToShared = policy == MetadataParsingPolicy.ON_DEMAND_COMPATIBLE
                    )
                } else {
                    isolatedParser.parseVideoMetadata(uri)
                } ?: return@runCatching null
                mediaMetadataFromVideoBundle(media.id, bundle)
            } else {
                null
            }?.also {
                printDebug("Retrieved metadata for ${media.id} - $uri\n$it")
            }
        }.getOrElse {
            if (it is CancellationException) throw it
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
    context: Context,
    allowInProcessFallback: Boolean
): MediaMetadata {
    val gpsLatitude = if (bundle.containsKey(Keys.KEY_GPS_LAT)) bundle.getDouble(Keys.KEY_GPS_LAT) else null
    val gpsLongitude = if (bundle.containsKey(Keys.KEY_GPS_LON)) bundle.getDouble(Keys.KEY_GPS_LON) else null

    // Geocoding runs in the main app process (needs network + GMS)
    val address = bestEffortReverseGeocode(
        enabled = geocoder != null,
        latitude = gpsLatitude,
        longitude = gpsLongitude
    ) { latitude, longitude ->
        geocoder?.getFromLocation(latitude, longitude, 1).orEmpty().firstOrNull()
    }
    var imgW = bundle.getInt(Keys.KEY_IMAGE_WIDTH, 0)
    var imgH = bundle.getInt(Keys.KEY_IMAGE_HEIGHT, 0)

    // BitmapFactory fallback runs in main process (needs ContentResolver)
    if (allowInProcessFallback && (imgW == 0 || imgH == 0)) {
        // We need the URI again for the fallback — reconstruct from mediaId
        // This is a rare path so we tolerate the extra cost
        val uri = ContentUris.withAppendedId(
            MediaStore.Files.getContentUri("external"),
            mediaId
        )
        runCatching {
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
        // BitmapFactory can't decode JXL/JP2/PSD/SVG — recover dimensions via native probes so
        // even EXIF-carrying special formats report their resolution.
        if (imgW == 0 || imgH == 0) {
            SpecialFormatProbe.getSize(context, uri)?.let {
                imgW = it.width
                imgH = it.height
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
        gpsLocationName = address?.formattedAddress,
        gpsLocationNameCountry = address?.countryName,
        gpsLocationNameCity = address?.locationGroupName,
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

/**
 * In-process fallback used when the isolated metadata parser returns nothing for an image
 * (e.g. metadata-extractor cannot read the format, or the isolated service is unavailable on the
 * device). Reads the dimensions via [BitmapFactory] bounds and EXIF via the platform
 * [ExifInterface] so that at least resolution, size and any embedded EXIF (date, camera, ISO,
 * shutter, aperture, GPS) still appear instead of an empty properties sheet (#1002).
 *
 * This uses the same in-process [BitmapFactory]/[ContentResolver] reads that the primary path
 * already relies on for its dimension fallback, so it does not weaken the sandbox guarantee
 * (which only applies to the isolated primary parse).
 */
@Suppress("DEPRECATION")
private suspend fun buildFallbackImageMetadata(
    mediaId: Long,
    uri: Uri,
    geocoder: Geocoder?,
    context: Context
): MediaMetadata? {
    val resolver = context.contentResolver

    var imgW = 0
    var imgH = 0
    runCatching {
        resolver.openInputStream(uri)?.use { stream ->
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(stream, null, options)
            if (options.outWidth > 0 && options.outHeight > 0) {
                imgW = options.outWidth
                imgH = options.outHeight
            }
        }
    }

    // BitmapFactory can't decode JXL/JP2/PSD/SVG (and some HEIF/AVIF/TIFF), so recover the
    // intrinsic dimensions from the same native decoders used for rendering. This guarantees the
    // properties sheet shows resolution + size instead of nothing for those formats (#1002).
    if (imgW == 0 || imgH == 0) {
        SpecialFormatProbe.getSize(context, uri)?.let {
            imgW = it.width
            imgH = it.height
        }
    }

    var description: String? = null
    var dateTimeOriginal: String? = null
    var make: String? = null
    var model: String? = null
    var aperture: String? = null
    var exposureTime: String? = null
    var iso: String? = null
    var focalLength: Double? = null
    var resX: Double? = null
    var resY: Double? = null
    var resUnit: Int? = null
    var gpsLatitude: Double? = null
    var gpsLongitude: Double? = null

    runCatching {
        // Seekable-FD ExifInterface (via SafeExif) instead of an InputStream: the latter buffers up
        // to the strip offset and OOMs on large 16-bit TIFFs (the file IS the TIFF/EXIF structure).
        SafeExif.open(context, uri)?.let { exif ->
            description = exif.getAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION)
            dateTimeOriginal = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
            make = exif.getAttribute(ExifInterface.TAG_MAKE)
            model = exif.getAttribute(ExifInterface.TAG_MODEL)
            exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0)
                .takeIf { it > 0 }?.let { aperture = "f/${it.stripTrailingZero()}" }
            exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0)
                .takeIf { it > 0 }?.let { exposureTime = it.toExposureString() }
            iso = exif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)
                ?: exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS)
            focalLength = exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0)
                .takeIf { it > 0 }
            resX = exif.getAttributeDouble(ExifInterface.TAG_X_RESOLUTION, 0.0).takeIf { it > 0 }
            resY = exif.getAttributeDouble(ExifInterface.TAG_Y_RESOLUTION, 0.0).takeIf { it > 0 }
            resUnit = exif.getAttributeInt(ExifInterface.TAG_RESOLUTION_UNIT, 0).takeIf { it > 0 }
            exif.latLong?.let {
                gpsLatitude = it[0]
                gpsLongitude = it[1]
            }
            if (imgW == 0) imgW = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0)
            if (imgH == 0) imgH = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0)
        }
    }.onFailure { it.printStackTrace() }

    // Nothing usable could be read — behave as before and show no properties sheet.
    val hasAnything = imgW > 0 || imgH > 0 || description != null || dateTimeOriginal != null ||
            make != null || model != null || aperture != null || exposureTime != null ||
            iso != null || focalLength != null || gpsLatitude != null
    if (!hasAnything) return null

    val address = bestEffortReverseGeocode(
        enabled = geocoder != null,
        latitude = gpsLatitude,
        longitude = gpsLongitude
    ) { latitude, longitude ->
        geocoder?.getFromLocation(latitude, longitude, 1).orEmpty().firstOrNull()
    }

    return MediaMetadata(
        mediaId = mediaId,
        imageDescription = description,
        dateTimeOriginal = dateTimeOriginal,
        manufacturerName = make,
        modelName = model,
        aperture = aperture,
        exposureTime = exposureTime,
        iso = iso,
        focalLength = focalLength,
        gpsLatitude = gpsLatitude,
        gpsLongitude = gpsLongitude,
        gpsLocationName = address?.formattedAddress,
        gpsLocationNameCountry = address?.countryName,
        gpsLocationNameCity = address?.locationGroupName,
        imageWidth = imgW,
        imageHeight = imgH,
        imageResolutionX = resX,
        imageResolutionY = resY,
        resolutionUnit = resUnit,
        durationMs = null,
        videoWidth = null,
        videoHeight = null,
        frameRate = null,
        bitRate = null,
        isNightMode = false,
        isPanorama = false,
        isPhotosphere = false,
        isLongExposure = false,
        isMotionPhoto = false
    )
}

/** Formats a plain double without a trailing ".0" (e.g. 2.0 -> "2", 2.2 -> "2.2"). */
private fun Double.stripTrailingZero(): String =
    if (this == toLong().toDouble()) toLong().toString() else toString()

/**
 * Formats an exposure time in seconds the same way the summary row expects: a "num/den sec"
 * fraction for sub-second exposures (e.g. 0.008 -> "1/125 sec") or a plain value otherwise.
 */
private fun Double.toExposureString(): String =
    if (this >= 1.0) "${stripTrailingZero()} sec" else "1/${(1.0 / this).roundToInt()} sec"

fun MediaMetadata.toCore() = MediaMetadataCore(
    mediaId = mediaId,
    imageDescription = imageDescription,
    dateTimeOriginal = dateTimeOriginal,
    manufacturerName = manufacturerName,
    modelName = modelName,
    lensModel = lensModel,
    aperture = aperture,
    exposureTime = exposureTime,
    iso = iso,
    focalLength = focalLength,
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
        lensModel = core.lensModel,
        aperture = core.aperture,
        exposureTime = core.exposureTime,
        iso = core.iso,
        focalLength = core.focalLength,
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