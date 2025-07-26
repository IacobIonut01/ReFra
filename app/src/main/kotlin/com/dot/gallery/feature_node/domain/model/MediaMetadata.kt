package com.dot.gallery.feature_node.domain.model

import android.content.Context
import android.location.Geocoder
import android.media.MediaMetadataRetriever
import android.os.Build
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
import com.dot.gallery.feature_node.domain.util.getUri
import com.dot.gallery.feature_node.domain.util.isImage
import com.dot.gallery.feature_node.domain.util.isVideo
import com.dot.gallery.feature_node.presentation.util.formattedAddress
import com.dot.gallery.feature_node.presentation.util.getLocation
import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.exif.ExifIFD0Directory
import com.drew.metadata.exif.ExifSubIFDDirectory
import com.drew.metadata.exif.GpsDirectory
import com.drew.metadata.xmp.XmpDirectory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.math.RoundingMode
import java.text.DecimalFormat
import java.util.Locale

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

suspend fun Context.retrieveExtraMediaMetadata(media: Media): MediaMetadata? =
    withContext(Dispatchers.IO) {
        runCatching {
            val uri = media.getUri()
            val label = media.label

            // placeholders
            var imageDescription: String? = null
            var dateTimeOriginal: String? = null
            var manufacturerName: String? = null
            var modelName: String? = null
            var aperture: String? = null
            var exposureTime: String? = null
            var iso: String? = null
            var gpsLatitude: Double? = null
            var gpsLongitude: Double? = null
            var gpsLocationName: String? = null
            var gpsLocationCountry: String? = null
            var gpsLocationCity: String? = null
            var imgW = 0;
            var imgH = 0
            var resX: Double? = null;
            var resY: Double? = null;
            var resUnit: Int? = null

            var durationMs: Long? = null
            var vidW: Int? = null;
            var vidH: Int? = null
            var frameRate: Float? = null
            var bitRate: Int? = null

            // feature flags
            var isNightMode = false
            var isPanorama = false
            var isPhotosphere = false
            var isLongExposure = false
            var isMotionPhoto = false

            if (media.isImage) {
                contentResolver.openInputStream(uri).use { stream ->
                    val meta = runCatching { ImageMetadataReader.readMetadata(stream) }.getOrNull()
                        ?: return@use

                    // EXIF0 directories (image description, make, model, resolution)
                    meta.getDirectoriesOfType(ExifIFD0Directory::class.java).forEach { dir ->
                        if (imageDescription == null)
                            imageDescription = dir.getString(ExifIFD0Directory.TAG_IMAGE_DESCRIPTION)
                        if (manufacturerName == null)
                            manufacturerName = dir.getString(ExifIFD0Directory.TAG_MAKE)
                        if (modelName == null)
                            modelName = dir.getString(ExifIFD0Directory.TAG_MODEL)
                        if (imgW == 0)
                            imgW = dir.getInteger(ExifIFD0Directory.TAG_IMAGE_WIDTH) ?: 0
                        if (imgH == 0)
                            imgH = dir.getInteger(ExifIFD0Directory.TAG_IMAGE_HEIGHT) ?: 0
                        if (resX == null)
                            resX = dir.getDoubleObject(ExifIFD0Directory.TAG_X_RESOLUTION)
                        if (resY == null)
                            resY = dir.getDoubleObject(ExifIFD0Directory.TAG_Y_RESOLUTION)
                        if (resUnit == null)
                            resUnit = dir.getInteger(ExifIFD0Directory.TAG_RESOLUTION_UNIT)
                    }

                    // SubIFD directories (datetime, aperture, exposure, ISO, alternate dimensions)
                    meta.getDirectoriesOfType(ExifSubIFDDirectory::class.java).forEach { dir ->
                        if (dateTimeOriginal == null)
                            dateTimeOriginal =
                                dir.getString(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL)
                        if (aperture == null)
                            aperture = dir.getDescription(ExifSubIFDDirectory.TAG_FNUMBER)
                        if (exposureTime == null)
                            exposureTime = dir.getDescription(ExifSubIFDDirectory.TAG_EXPOSURE_TIME)
                        if (iso == null)
                            iso = dir.getString(ExifSubIFDDirectory.TAG_ISO_EQUIVALENT)
                        if (imgW == 0)
                            imgW = dir.getInteger(ExifSubIFDDirectory.TAG_EXIF_IMAGE_WIDTH) ?: 0
                        if (imgH == 0)
                            imgH = dir.getInteger(ExifSubIFDDirectory.TAG_EXIF_IMAGE_HEIGHT) ?: 0
                    }

                    // GPS directories
                    meta.getDirectoriesOfType(GpsDirectory::class.java).forEach { dir ->
                        dir.geoLocation?.let {
                            if (gpsLatitude == null) gpsLatitude = it.latitude
                            if (gpsLongitude == null) gpsLongitude = it.longitude
                        }
                    }

                    // If GPS location is available, try to get the location name
                    if (gpsLatitude != null) {
                        val geocoder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && Geocoder.isPresent())
                            Geocoder(this@retrieveExtraMediaMetadata) else null
                        geocoder?.getLocation(
                            gpsLatitude,
                            gpsLongitude!!
                        ) { address ->
                            gpsLocationName = address?.formattedAddress
                            gpsLocationCountry = address?.countryName
                            gpsLocationCity = address?.locality
                        }
                    }

                    // XMP directories for your feature flags
                    val xmps = meta.getDirectoriesOfType(XmpDirectory::class.java)

                    // Night mode: any subIfd dir that has both ISO & EXP tags
                    isNightMode =
                        meta.getDirectoriesOfType(ExifSubIFDDirectory::class.java).any { dir ->
                            dir.containsTag(ExifSubIFDDirectory.TAG_ISO_EQUIVALENT) &&
                                    dir.containsTag(ExifSubIFDDirectory.TAG_EXPOSURE_TIME) &&
                                    run {
                                        val isoVal =
                                            dir.getInt(ExifSubIFDDirectory.TAG_ISO_EQUIVALENT)
                                        val expVal =
                                            dir.getDouble(ExifSubIFDDirectory.TAG_EXPOSURE_TIME)
                                        label.matches("(?i).*\\.NIGHT\\..*".toRegex()) ||
                                                (isoVal < 100 && expVal > 0.01)
                                    }
                        }

                    // Photosphere
                    isPhotosphere = xmps.any { xmp ->
                        xmp.xmpProperties.any { prop ->
                            (MediaMetadata.PHOTOSPERE_KEYS.any { key ->
                                prop.key.contains(
                                    key,
                                    true
                                )
                            }
                                    && prop.value == MediaMetadata.PHOTOSPHERE_VALUES[0])
                                    || prop.value == MediaMetadata.PHOTOSPHERE_VALUES[1]
                        }
                    }

                    // Panorama (but not photosphere)
                    isPanorama = xmps.any { xmp ->
                        xmp.xmpProperties.any { prop ->
                            MediaMetadata.PANORMA_KEYS.any { key -> prop.key.contains(key, true) }
                        }
                    } && !isPhotosphere

                    // Long exposure
                    isLongExposure = xmps.any { xmp ->
                        xmp.xmpProperties.any { prop ->
                            MediaMetadata.LONG_EXPOSURE_KEYS.any { key ->
                                prop.key.contains(
                                    key,
                                    true
                                )
                            }
                        }
                    }

                    // Motion photo
                    isMotionPhoto = xmps.any { xmp ->
                        xmp.xmpProperties.any { prop ->
                            prop.key == "GCamera:MotionPhoto" && prop.value == "1"
                        }
                    }
                }
            } else if (media.isVideo) {
                val retriever = MediaMetadataRetriever().apply {
                    setDataSource(applicationContext, uri)
                }

                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()
                vidW = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    ?.toIntOrNull()
                vidH = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    ?.toIntOrNull()
                bitRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                    ?.toIntOrNull()

                frameRate = retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                    ?.toFloatOrNull()
                    ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)
                        ?.let { cnt -> durationMs?.let { d -> cnt.toFloat() / (d / 1000f) } }
            }

            MediaMetadata(
                mediaId = media.id,
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
                gpsLocationNameCountry = gpsLocationCountry,
                gpsLocationNameCity = gpsLocationCity,
                imageWidth = imgW,
                imageHeight = imgH,
                imageResolutionX = resX,
                imageResolutionY = resY,
                resolutionUnit = resUnit,
                durationMs = durationMs,
                videoWidth = vidW,
                videoHeight = vidH,
                frameRate = frameRate,
                bitRate = bitRate,
                isNightMode = isNightMode,
                isPanorama = isPanorama,
                isPhotosphere = isPhotosphere,
                isLongExposure = isLongExposure,
                isMotionPhoto = isMotionPhoto
            )
        }.getOrElse {
            it.printStackTrace()
            null
        }
    }

fun MediaMetadata.toCore() = MediaMetadataCore(
    mediaId           = mediaId,
    imageDescription  = imageDescription,
    dateTimeOriginal  = dateTimeOriginal,
    manufacturerName  = manufacturerName,
    modelName         = modelName,
    aperture          = aperture,
    exposureTime      = exposureTime,
    iso               = iso,
    gpsLatitude       = gpsLatitude,
    gpsLongitude      = gpsLongitude,
    gpsLocationName   = gpsLocationName,
    gpsLocationNameCountry= gpsLocationNameCountry,
    gpsLocationNameCity   = gpsLocationNameCity,
    imageWidth        = imageWidth,
    imageHeight       = imageHeight,
    imageResolutionX  = imageResolutionX,
    imageResolutionY  = imageResolutionY,
    resolutionUnit    = resolutionUnit
)

fun MediaMetadata.toVideo() = MediaMetadataVideo(
    mediaId    = mediaId,
    durationMs = durationMs,
    videoWidth = videoWidth,
    videoHeight= videoHeight,
    frameRate  = frameRate,
    bitRate    = bitRate
)

fun MediaMetadata.toFlags() = MediaMetadataFlags(
    mediaId       = mediaId,
    isNightMode   = isNightMode,
    isPanorama    = isPanorama,
    isPhotosphere = isPhotosphere,
    isLongExposure= isLongExposure,
    isMotionPhoto = isMotionPhoto
)

fun FullMediaMetadata.toMediaMetadata(): MediaMetadata {
    val v = video
    val f = flags
    return MediaMetadata(
        mediaId           = core.mediaId,
        imageDescription  = core.imageDescription,
        dateTimeOriginal  = core.dateTimeOriginal,
        manufacturerName  = core.manufacturerName,
        modelName         = core.modelName,
        aperture          = core.aperture,
        exposureTime      = core.exposureTime,
        iso               = core.iso,
        gpsLatitude       = core.gpsLatitude,
        gpsLongitude      = core.gpsLongitude,
        gpsLocationName   = core.gpsLocationName,
        gpsLocationNameCountry= core.gpsLocationNameCountry,
        gpsLocationNameCity   = core.gpsLocationNameCity,
        imageWidth        = core.imageWidth,
        imageHeight       = core.imageHeight,
        imageResolutionX  = core.imageResolutionX,
        imageResolutionY  = core.imageResolutionY,
        resolutionUnit    = core.resolutionUnit,
        durationMs        = v?.durationMs,
        videoWidth        = v?.videoWidth,
        videoHeight       = v?.videoHeight,
        frameRate         = v?.frameRate,
        bitRate           = v?.bitRate,
        isNightMode       = f?.isNightMode   ?: false,
        isPanorama        = f?.isPanorama    ?: false,
        isPhotosphere     = f?.isPhotosphere ?: false,
        isLongExposure    = f?.isLongExposure?: false,
        isMotionPhoto     = f?.isMotionPhoto ?: false
    )
}