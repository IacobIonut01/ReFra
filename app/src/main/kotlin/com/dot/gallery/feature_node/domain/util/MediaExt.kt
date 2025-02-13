package com.dot.gallery.feature_node.domain.util

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.dot.gallery.BuildConfig
import com.dot.gallery.feature_node.domain.model.Media
import com.github.panpf.zoomimage.subsampling.ContentImageSource
import com.github.panpf.zoomimage.subsampling.SubsamplingImage
import io.ktor.util.reflect.instanceOf
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ObjectInputStream
import java.io.Serializable
import java.util.UUID

/**
 * Determine if the current media is a raw format
 *
 * Checks if [Media.mimeType] starts with "image/x-" or "image/vnd."
 *
 * Most used formats:
 * - ARW: image/x-sony-arw
 * - CR2: image/x-canon-cr2
 * - CRW: image/x-canon-crw
 * - DCR: image/x-kodak-dcr
 * - DNG: image/x-adobe-dng
 * - ERF: image/x-epson-erf
 * - K25: image/x-kodak-k25
 * - KDC: image/x-kodak-kdc
 * - MRW: image/x-minolta-mrw
 * - NEF: image/x-nikon-nef
 * - ORF: image/x-olympus-orf
 * - PEF: image/x-pentax-pef
 * - RAF: image/x-fuji-raf
 * - RAW: image/x-panasonic-raw
 * - SR2: image/x-sony-sr2
 * - SRF: image/x-sony-srf
 * - X3F: image/x-sigma-x3f
 *
 * Other proprietary image types in the standard:
 * image/vnd.manufacturer.filename_extension for instance for NEF by Nikon and .mrv for Minolta:
 * - NEF: image/vnd.nikon.nef
 * - Minolta: image/vnd.minolta.mrw
 */
val Media.isRaw: Boolean
    get() =
        mimeType.isNotBlank() && (mimeType.startsWith("image/x-") || mimeType.startsWith("image/vnd."))

private val Media.rawExtension: String
    get() = if (mimeType.startsWith("image/vnd."))
        mimeType.substringAfterLast(".").removePrefix(".") else mimeType.substringAfterLast("-")
        .removePrefix("-")

val Media.fileExtension: String
    get() = if (isRaw) rawExtension else label.substringAfterLast(".").removePrefix(".")

val Media.volume: String
    get() = path.substringBeforeLast("/").removeSuffix(relativePath.removeSuffix("/"))

/**
 * Used to determine if the Media object is not accessible
 * via MediaStore.
 * This happens when the user tries to open media from an app
 * using external sources (in our case, Gallery Media Viewer), but
 * the specific media is only available internally in that app
 * (Android/data(OR media)/com.package.name/)
 *
 * If it's readUriOnly then we know that we should expect a barebone
 * Media object with limited functionality (no favorites, trash, timestamp etc)
 */
val Media.readUriOnly: Boolean get() = albumID == -99L && albumLabel == "" && instanceOf(Media.UriMedia::class)

val Media.isVideo: Boolean get() = mimeType.startsWith("video/") && duration != null

val Media.isImage: Boolean get() = mimeType.startsWith("image/")

val Media.isTrashed: Boolean get() = trashed == 1

val Media.isFavorite: Boolean get() = favorite == 1

val Media.isEncrypted: Boolean
    get() = instanceOf(Media.UriMedia::class) && getUri().toString()
        .contains(BuildConfig.APPLICATION_ID)

val Media.isLocalContent: Boolean
    get() = instanceOf(Media.UriMedia::class) && getUri().toString().startsWith("content://media")

val Media.canMakeActions: Boolean get() = !isEncrypted && isLocalContent && !instanceOf(Media.ClassifiedMedia::class) && !readUriOnly

val Media.isClassified: Boolean get() = instanceOf(Media.ClassifiedMedia::class)

val Media.getCategory: String?
    get() = if (this is Media.ClassifiedMedia) {
        this.category
    } else null

@Suppress("UNCHECKED_CAST")
fun <T : Serializable> fromByteArray(byteArray: ByteArray): T {
    ByteArrayInputStream(byteArray).use { byteArrayInputStream ->
        ObjectInputStream(byteArrayInputStream).use { objectInput ->
            return objectInput.readObject() as T
        }
    }
}

@Suppress("UNCHECKED_CAST")
inline fun <reified T> fromKotlinByteArray(byteArray: ByteArray): T =
    Json.decodeFromString(String(byteArray, Charsets.UTF_8))

inline fun <reified T> T.toKotlinByteArray() = Json.encodeToString(this).toByteArray(Charsets.UTF_8)

fun Media.EncryptedMedia.migrate(uuid: UUID): Media.EncryptedMedia2 = Media.EncryptedMedia2(
    id = id,
    label = label,
    uuid = uuid,
    path = path,
    timestamp = timestamp,
    mimeType = mimeType,
    duration = duration,
    trashed = trashed,
    favorite = favorite,
    albumID = albumID,
    albumLabel = albumLabel,
    relativePath = relativePath,
    fullDate = fullDate,
    size = size,
)

fun <T : Media> T.toEncryptedMedia(bytes: ByteArray): Media.EncryptedMedia {
    return Media.EncryptedMedia(
        id = id,
        label = label,
        bytes = bytes,
        path = path,
        timestamp = timestamp,
        mimeType = mimeType,
        duration = duration,
        trashed = trashed,
        favorite = favorite,
        albumID = albumID,
        albumLabel = albumLabel,
        relativePath = relativePath,
        fullDate = fullDate,
        size = size,
    )
}

fun <T : Media> T.asSubsamplingImage(context: Context): SubsamplingImage {
    return SubsamplingImage(imageSource = ContentImageSource(context, getUri()))
}

fun <T : Media> T.compatibleMimeType(): String {
    return if (isImage) when (mimeType) {
        "image/jpeg" -> "image/jpeg"
        "image/png" -> "image/png"
        else -> "image/png"
    } else mimeType
}

fun <T : Media> T.compatibleBitmapFormat(): Bitmap.CompressFormat {
    return when (mimeType) {
        "image/jpeg" -> Bitmap.CompressFormat.JPEG
        "image/png" -> Bitmap.CompressFormat.PNG
        else -> Bitmap.CompressFormat.PNG
    }
}

fun <T : Media> T.asUriMedia(uri: Uri): Media.UriMedia {
    return Media.UriMedia(
        id = id,
        label = label,
        uri = uri,
        path = path,
        timestamp = timestamp,
        mimeType = mimeType,
        duration = duration,
        trashed = trashed,
        favorite = favorite,
        albumID = albumID,
        albumLabel = albumLabel,
        relativePath = relativePath,
        fullDate = fullDate,
        size = size,
    )
}

fun <T : Media> T.getUri(): Uri {
    return when (this) {
        is Media.UriMedia -> uri
        is Media.ClassifiedMedia -> uri
        else -> throw IllegalArgumentException("Media type ${this.javaClass.simpleName} not supported")
    }
}

val Any.isHeaderKey: Boolean
    get() = this is String && this.startsWith("header_")

val Any.isBigHeaderKey: Boolean
    get() = this is String && this.startsWith("header_big_")

val Any.isIgnoredKey: Boolean
    get() = this is String && this == "aboveGrid"