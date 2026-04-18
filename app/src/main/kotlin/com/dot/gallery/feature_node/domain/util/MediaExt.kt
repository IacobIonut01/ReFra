package com.dot.gallery.feature_node.domain.util

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.dot.gallery.BuildConfig
import com.dot.gallery.feature_node.domain.model.Album
import com.dot.gallery.feature_node.domain.model.IgnoredAlbum
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.LockedAlbum
import com.dot.gallery.feature_node.domain.model.PinnedAlbum
import com.github.panpf.zoomimage.subsampling.ContentImageSource
import com.github.panpf.zoomimage.subsampling.SubsamplingImage
import io.ktor.util.reflect.instanceOf
import kotlinx.serialization.json.Json
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

/**
 * Returns true if this media is a raw file format that should not be converted
 * through bitmap encoding/decoding (e.g., GIF, animated WebP).
 * These formats would lose animation or quality if processed as bitmaps.
 */
val Media.isRawFile: Boolean get() = mimeType in listOf(
    "image/gif",
    "image/webp",  // WebP can be animated
    "image/svg+xml",
    "image/bmp"
)

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

/*
@Suppress("UNCHECKED_CAST")
fun <T : Serializable> fromByteArray(byteArray: ByteArray): T {
    ByteArrayInputStream(byteArray).use { byteArrayInputStream ->
        ObjectInputStream(byteArrayInputStream).use { objectInput ->
            return objectInput.readObject() as T
        }
    }
}
*/

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

fun <T : Media> T.toEncryptedMedia2(uuid: UUID): Media.EncryptedMedia2 {
    return Media.EncryptedMedia2(
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
    get() = this is String && this.contains("aboveGrid")

/**
 * Burst patterns by manufacturer
 *
 * Each regex matches the full filename (without extension) and captures
 * the grouping key as named group "key".
 *
 * - Fairphone/Motorola: IMG_YYYYMMDD_HHMMSS(mmm)_BURST###(_COVER)
 * - Samsung:            YYYYMMDD_HHMMSS_### (3+ digit burst sequence)
 * - Sony:               DSC(PDC)_####_BURST#################(_COVER)
 */
private val BURST_PATTERNS = listOf(
    // Fairphone / Motorola: IMG_20151021_072800_BURST007, IMG_20151021_072800123_BURST007_COVER
    Regex("^IMG_(?<key>\\d{8}_\\d{6,9})_BURST\\d+(_COVER)?$"),
    // Samsung: 20151021_072800_007
    Regex("^(?<key>\\d{8}_\\d{6})_(\\d+)$"),
    // Sony: DSC_0007_BURST20151021072800123, DSCPDC_0007_BURST20151021072800123_COVER
    Regex("^DSC(PDC)?_\\d+_BURST(?<key>\\d{17})(_COVER)?$"),
)

/**
 * Extracts the base filename used for grouping related media.
 * Strips the file extension and common edit/burst/RAW suffixes so that
 * RAW+JPG pairs, edited copies, and burst photos share the same group key.
 *
 * Handles:
 * - Pixel RAW+JPG:          PXL_20260418_155541857.RAW-01.jpg → PXL_20260418_155541857
 * - Pixel modes:            PXL_20260418_155541857.NIGHT.jpg  → PXL_20260418_155541857
 * - Edit copies:            IMG_20250305_123456(1).jpg         → IMG_20250305_123456
 * - Fairphone/Motorola:     IMG_20151021_072800_BURST007.jpg   → IMG_20151021_072800
 * - Samsung burst:          20151021_072800_007.jpg             → 20151021_072800
 * - Sony burst:             DSC_0007_BURST20151021072800123.jpg → 20151021072800123
 */
val Media.groupBaseName: String
    get() {
        // Strip the file extension
        val nameWithoutExt = label.substringBeforeLast(".")
        // Try manufacturer-specific burst patterns first
        for (pattern in BURST_PATTERNS) {
            val match = pattern.matchEntire(nameWithoutExt)
            if (match != null) {
                return match.groups["key"]?.value ?: nameWithoutExt
            }
        }
        // Fall back to generic suffix stripping for RAW pairs, edits, etc.
        return nameWithoutExt
            // Pixel-style dot-separated suffixes
            .replace(Regex("\\.(ORIGINAL|RAW-\\d+|NIGHT|PORTRAIT|LONG_EXPOSURE|MP|MOTION-\\d+|PANO|TOP|BOTTOM|COVER|BURST\\d*)", RegexOption.IGNORE_CASE), "")
            // Copy / duplicate suffixes
            .replace(Regex("\\(\\d+\\)$"), "")           // (1), (2), etc.
            .replace(Regex("~\\d+$"), "")                 // ~2, ~3, etc.
            // Edit suffixes
            .replace(Regex("_edited$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("-edited$", RegexOption.IGNORE_CASE), "")
            // Burst / cover suffixes (generic, after manufacturer-specific failed)
            .replace(Regex("_COVER$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("_BURST\\d*$", RegexOption.IGNORE_CASE), "")
            // HDR suffix
            .replace(Regex("_HDR$", RegexOption.IGNORE_CASE), "")
            .trim()
    }

/**
 * Group key combines the base filename with the relative path
 * so that files in different directories are never grouped together.
 */
val Media.groupKey: String
    get() = "$relativePath/$groupBaseName"

/**
 * Selects the "best" representative from a group of related media items.
 * Priority: non-RAW > RAW, original (no suffix) > edit, larger file > smaller.
 */
fun <T : Media> List<T>.selectRepresentative(): T {
    if (size == 1) return first()
    return sortedWith(
        compareBy<T> { it.isRaw }             // non-RAW first (false < true)
            .thenBy { it.label != it.groupBaseName + "." + it.label.substringAfterLast(".") } // original first
            .thenByDescending { it.size }      // larger files first
    ).first()
}

/**
 * Type of a media group, used for search carousel cards.
 */
enum class MediaGroupType {
    BURST,
    RAW_JPG,
    EDITS
}

/**
 * Classifies a group of related media items into a [MediaGroupType].
 * - BURST: at least one filename matches a burst pattern
 * - RAW_JPG: group contains both RAW and non-RAW files
 * - EDITS: fallback for any other multi-member group (edit copies, HDR, etc.)
 */
fun <T : Media> List<T>.classifyGroupType(): MediaGroupType {
    val hasRaw = any { it.isRaw }
    val hasNonRaw = any { !it.isRaw }
    if (hasRaw && hasNonRaw) return MediaGroupType.RAW_JPG

    val hasBurst = any { media ->
        val nameWithoutExt = media.label.substringBeforeLast(".")
        BURST_PATTERNS.any { it.matchEntire(nameWithoutExt) != null }
    }
    if (hasBurst) return MediaGroupType.BURST

    return MediaGroupType.EDITS
}

fun List<Album>.mapPinned(pinnedAlbums: List<PinnedAlbum>): List<Album> =
    map { album -> album.copy(isPinned = pinnedAlbums.any { it.id == album.id }) }

fun List<Album>.mapLocked(lockedAlbums: List<LockedAlbum>): List<Album> =
    map { album -> album.copy(isLocked = lockedAlbums.any { it.id == album.id }) }

fun List<Album>.removeBlacklisted(blacklistedAlbums: List<IgnoredAlbum>): List<Album> =
    toMutableList().apply {
        removeAll { album -> blacklistedAlbums.any { it.matchesAlbum(album) && it.hiddenInAlbums } }
    }