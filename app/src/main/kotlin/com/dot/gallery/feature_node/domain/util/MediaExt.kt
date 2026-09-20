package com.dot.gallery.feature_node.domain.util

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
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
/**
 * Common camera-RAW file extensions. Used as a fallback for [Media.isRaw] because MediaStore
 * does not always report a canonical `image/x-*` / `image/vnd.*` mime for RAW files — DNGs in
 * particular are frequently scanned as `image/dng`, `image/tiff`, or with a blank mime, which
 * would otherwise make them read as non-RAW and win the representative tie-break by file size
 * (#995).
 */
private val RAW_EXTENSIONS = setOf(
    "dng", "arw", "cr2", "cr3", "crw", "nef", "nrw", "orf", "raf", "rw2",
    "pef", "srw", "raw", "dcr", "kdc", "k25", "mrw", "x3f", "erf", "3fr",
    "mef", "mos", "iiq", "sr2", "srf"
)

val Media.isRaw: Boolean
    get() = (mimeType.isNotBlank() &&
            (mimeType.startsWith("image/x-") || mimeType.startsWith("image/vnd."))) ||
            label.substringAfterLast('.', "").lowercase() in RAW_EXTENSIONS

private val Media.rawExtension: String
    get() = if (mimeType.startsWith("image/vnd."))
        mimeType.substringAfterLast(".").removePrefix(".") else mimeType.substringAfterLast("-")
        .removePrefix("-")

val Media.fileExtension: String
    get() = if (isRaw) {
        // Prefer the real filename extension so RAW files matched by extension (e.g. an
        // `image/dng` DNG) still report the correct type instead of a mime fragment.
        label.substringAfterLast('.', "").ifEmpty { rawExtension }
    } else label.substringAfterLast(".").removePrefix(".")

val Media.volume: String
    get() = path.substringBeforeLast("/").removeSuffix(relativePath.removeSuffix("/"))

/**
 * Resolves a destination path (absolute or relative) into a pair of
 * (MediaStore volume name, relative path).
 *
 * - Absolute paths like `/storage/emulated/0/DCIM/Camera/` resolve to
 *   `(VOLUME_EXTERNAL_PRIMARY, "DCIM/Camera/")`
 * - The bare primary storage root `/storage/emulated/0` (the "/" pseudo-bucket)
 *   resolves to `(VOLUME_EXTERNAL_PRIMARY, "/")`
 * - SD card paths like `/storage/71F8-2C0A/DCIM/Camera/` resolve to
 *   `("71f8-2c0a", "DCIM/Camera/")`
 * - Relative paths like `DCIM/Camera/` resolve to
 *   `(VOLUME_EXTERNAL_PRIMARY, "DCIM/Camera/")`
 */
fun resolveMediaStoreVolume(path: String): Pair<String, String> {
    val primaryStorage = Environment.getExternalStorageDirectory().absolutePath.trimEnd('/')

    return when {
        path.trimEnd('/') == primaryStorage -> {
            // The storage root is a valid move/copy destination (MediaStore stores
            // RELATIVE_PATH="/" for it), but parsing it as a generic
            // /storage/<id>/<rest> path would split the "emulated/0" specifier into
            // a bogus ("emulated", "0") volume.
            MediaStore.VOLUME_EXTERNAL_PRIMARY to "/"
        }
        path.startsWith("$primaryStorage/") -> {
            val relativePath = path.removePrefix("$primaryStorage/")
            MediaStore.VOLUME_EXTERNAL_PRIMARY to relativePath
        }
        path.startsWith("/storage/") -> {
            val afterStorage = path.removePrefix("/storage/")
            val volumeId = afterStorage.substringBefore("/").lowercase()
            // A bare "/storage/<id>" root has no sub-path; MediaStore expects "/"
            // for it (an empty RELATIVE_PATH is ignored as a no-op on update).
            val relativePath = afterStorage.substringAfter("/", "")
                .ifEmpty { "/" }
            volumeId to relativePath
        }
        else -> {
            MediaStore.VOLUME_EXTERNAL_PRIMARY to path
        }
    }
}

val Media.mediaStoreVolumeName: String
    get() = runCatching {
        getUri().takeIf { it.scheme == "content" && it.authority == "media" }
            ?.pathSegments?.firstOrNull()
    }.getOrNull() ?: resolveMediaStoreVolume(path).first

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

val Media.isGif: Boolean get() = mimeType == "image/gif"

/**
 * Returns true if this media is a raw file format that should not be converted
 * through bitmap encoding/decoding (e.g., GIF, animated WebP).
 * These formats would lose animation or quality if processed as bitmaps.
 */
val Media.isRawFile: Boolean get() = mimeType in listOf(
    "image/gif",
    "image/apng",  // APNG is animated PNG
    "image/webp",  // WebP can be animated
    "image/avif",  // AVIF can be animated
    "image/avis",  // AVIF sequence (animated)
    "image/svg+xml",
    "image/bmp"
)

val Media.isApng: Boolean
    get() = mimeType == "image/apng" || label.endsWith(".apng", ignoreCase = true)

val Media.isAvif: Boolean
    get() = mimeType == "image/avif" || mimeType == "image/avis" ||
            label.endsWith(".avif", ignoreCase = true)

val Media.isJxl: Boolean
    get() = mimeType == "image/jxl" || label.endsWith(".jxl", ignoreCase = true)

val Media.isHeif: Boolean
    get() = mimeType == "image/heif" || mimeType == "image/heic" ||
            mimeType == "image/heif-sequence" || mimeType == "image/heic-sequence" ||
            label.endsWith(".heic", ignoreCase = true) || label.endsWith(".heif", ignoreCase = true) ||
            label.endsWith(".hif", ignoreCase = true)

val Media.isPsd: Boolean
    get() = mimeType == "image/vnd.adobe.photoshop" || mimeType == "image/x-photoshop" ||
            label.endsWith(".psd", ignoreCase = true) || label.endsWith(".psb", ignoreCase = true)

val Media.isJp2: Boolean
    get() = mimeType == "image/jp2" || mimeType == "image/jpeg2000" || mimeType == "image/jpx" ||
            label.endsWith(".jp2", ignoreCase = true) || label.endsWith(".j2k", ignoreCase = true) ||
            label.endsWith(".jpf", ignoreCase = true) || label.endsWith(".jpx", ignoreCase = true) ||
            label.endsWith(".j2c", ignoreCase = true) || label.endsWith(".jpc", ignoreCase = true)

val Media.isTiff: Boolean
    get() = mimeType == "image/tiff" || mimeType == "image/tif" ||
            label.endsWith(".tif", ignoreCase = true) || label.endsWith(".tiff", ignoreCase = true)

val Media.isSvg: Boolean
    get() = mimeType == "image/svg+xml" || label.endsWith(".svg", ignoreCase = true)

val Media.isTrashed: Boolean get() = trashed == 1

val Media.isFavorite: Boolean get() = favorite == 1

val Media.isEncrypted: Boolean
    get() = instanceOf(Media.UriMedia::class) && getUri().toString()
        .contains(BuildConfig.APPLICATION_ID)

val Media.isCloud: Boolean
    get() = instanceOf(Media.UriMedia::class) && getUri().scheme == "cloud"

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

// Pre-compiled regex patterns for groupBaseName suffix stripping.
// These were previously created inline on every groupBaseName access,
// causing ~8 Regex compilations × 670 items = 5360 compilations per pass.
private val PIXEL_SUFFIX_REGEX = Regex("\\.(ORIGINAL|RAW-\\d+|NIGHT|PORTRAIT|LONG_EXPOSURE|MP|MOTION-\\d+|PANO|TOP|BOTTOM|COVER|BURST\\d*)", RegexOption.IGNORE_CASE)
private val COPY_PARENS_REGEX = Regex("\\(\\d+\\)$")
private val COPY_TILDE_REGEX = Regex("~\\d+$")
private val EDITED_UNDERSCORE_REGEX = Regex("_edited$", RegexOption.IGNORE_CASE)
private val EDITED_DASH_REGEX = Regex("-edited$", RegexOption.IGNORE_CASE)
private val COVER_SUFFIX_REGEX = Regex("_COVER$", RegexOption.IGNORE_CASE)
private val BURST_SUFFIX_REGEX = Regex("_BURST\\d*$", RegexOption.IGNORE_CASE)
private val HDR_SUFFIX_REGEX = Regex("_HDR$", RegexOption.IGNORE_CASE)

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
            .replace(PIXEL_SUFFIX_REGEX, "")
            // Copy / duplicate suffixes
            .replace(COPY_PARENS_REGEX, "")           // (1), (2), etc.
            .replace(COPY_TILDE_REGEX, "")                 // ~2, ~3, etc.
            // Edit suffixes
            .replace(EDITED_UNDERSCORE_REGEX, "")
            .replace(EDITED_DASH_REGEX, "")
            // Burst / cover suffixes (generic, after manufacturer-specific failed)
            .replace(COVER_SUFFIX_REGEX, "")
            .replace(BURST_SUFFIX_REGEX, "")
            // HDR suffix
            .replace(HDR_SUFFIX_REGEX, "")
            .trim()
    }

/**
 * Group key combines the base filename with the relative path
 * so that files in different directories are never grouped together.
 * Cloud media uses only the base filename so it can match with
 * local copies that share the same name but different path prefixes.
 */
val Media.groupKey: String
    get() = if (isCloud) "cloud_match/$groupBaseName" else "$relativePath/$groupBaseName"

/**
 * Cloud-aware group key that strips path prefixes to allow matching
 * between cloud and local copies of the same media.
 * Used only when CLOUD_LOCAL grouping is enabled.
 */
val Media.cloudGroupKey: String
    get() = groupBaseName

/**
 * Selects the "best" representative from a group of related media items.
 * Priority: non-RAW > RAW, original (no suffix) > edit, larger file > smaller.
 */
private fun <T : Media> representativeComparator(): Comparator<T> =
    compareBy<T> { it.isCloud }           // local first (false < true)
        .thenBy { it.isRaw }              // non-RAW first (false < true)
        .thenBy { it.label != it.groupBaseName + "." + it.label.substringAfterLast(".") } // original first
        .thenByDescending { it.size }     // larger files first

fun <T : Media> List<T>.selectRepresentative(): T {
    if (size == 1) return first()
    return sortedWith(representativeComparator()).first()
}

/**
 * Orders a group of related media by the same priority as [selectRepresentative] so the chosen
 * representative (e.g. the developed JPG over its RAW/DNG sibling) comes first. Keeps the stack's
 * opened view and multi-file share consistent with the cover shown in the grid (#995).
 */
fun <T : Media> List<T>.sortedByRepresentative(): List<T> =
    if (size <= 1) this else sortedWith(representativeComparator())

/**
 * Type of a media group, used for search carousel cards.
 */
enum class MediaGroupType {
    BURST,
    RAW_JPG,
    EDITS,
    CLOUD_LOCAL
}

/**
 * Classifies a group of related media items into a [MediaGroupType].
 * - CLOUD_LOCAL: group contains both cloud and local copies of the same media
 * - BURST: at least one filename matches a burst pattern
 * - RAW_JPG: group contains both RAW and non-RAW files
 * - EDITS: fallback for any other multi-member group (edit copies, HDR, etc.)
 */
fun <T : Media> List<T>.classifyGroupType(): MediaGroupType {
    val hasCloud = any { it.isCloud }
    val hasLocal = any { !it.isCloud }
    if (hasCloud && hasLocal) return MediaGroupType.CLOUD_LOCAL

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

fun List<Album>.mapPinned(pinnedAlbums: List<PinnedAlbum>): List<Album> {
    val pinnedIds = pinnedAlbums.mapTo(HashSet(pinnedAlbums.size)) { it.id }
    return map { album -> album.copy(isPinned = album.id in pinnedIds) }
}

fun List<Album>.mapLocked(lockedAlbums: List<LockedAlbum>): List<Album> {
    val lockedIds = lockedAlbums.mapTo(HashSet(lockedAlbums.size)) { it.id }
    return map { album -> album.copy(isLocked = album.id in lockedIds) }
}

fun List<Album>.removeBlacklisted(blacklistedAlbums: List<IgnoredAlbum>): List<Album> =
    toMutableList().apply {
        removeAll { album -> blacklistedAlbums.any { it.matchesAlbum(album) && it.hiddenInAlbums } }
    }