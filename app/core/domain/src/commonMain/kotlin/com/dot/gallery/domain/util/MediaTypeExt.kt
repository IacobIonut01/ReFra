package com.dot.gallery.domain.util

import com.dot.gallery.domain.DomainHooks
import com.dot.gallery.domain.model.ClassifiedMedia
import com.dot.gallery.domain.model.Media
import com.dot.gallery.domain.model.UriMedia
import com.dot.gallery.domain.uri.scheme

/**
 * Common camera-RAW file extensions. Used as a fallback for [Media.isRaw] because MediaStore
 * does not always report a canonical `image/x-*` / `image/vnd.*` mime for RAW files — DNGs in
 * particular are frequently scanned as `image/dng`, `image/tiff`, or with a blank mime, which
 * would otherwise make them read as non-RAW and win the representative tie-break by file size.
 */
val RAW_EXTENSIONS = setOf(
    "dng", "arw", "cr2", "cr3", "crw", "nef", "nrw", "orf", "raf", "rw2",
    "pef", "srw", "raw", "dcr", "kdc", "k25", "mrw", "x3f", "erf", "3fr",
    "mef", "mos", "iiq", "sr2", "srf"
)

/**
 * Determine if the current media is a raw format
 *
 * Checks if [Media.mimeType] starts with "image/x-" or "image/vnd."
 */
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
val Media.readUriOnly: Boolean get() = albumID == -99L && albumLabel == "" && this is UriMedia

val Media.isVideo: Boolean get() = mimeType.startsWith("video/") && duration != null

val Media.isImage: Boolean get() = mimeType.startsWith("image/")

val Media.isGif: Boolean get() = mimeType == "image/gif"

/**
 * Returns true if this media is a raw file format that should not be converted
 * through bitmap encoding/decoding (e.g., GIF, animated WebP).
 * These formats would lose animation or quality if processed as bitmaps.
 */
val Media.isRawFile: Boolean
    get() = mimeType in listOf(
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
            label.endsWith(".heic", ignoreCase = true) || label.endsWith(
        ".heif",
        ignoreCase = true
    ) ||
            label.endsWith(".hif", ignoreCase = true)

val Media.isPsd: Boolean
    get() = mimeType == "image/vnd.adobe.photoshop" || mimeType == "image/x-photoshop" ||
            label.endsWith(".psd", ignoreCase = true) || label.endsWith(".psb", ignoreCase = true)

val Media.isJp2: Boolean
    get() = mimeType == "image/jp2" || mimeType == "image/jpeg2000" || mimeType == "image/jpx" ||
            label.endsWith(".jp2", ignoreCase = true) || label.endsWith(
        ".j2k",
        ignoreCase = true
    ) ||
            label.endsWith(".jpf", ignoreCase = true) || label.endsWith(
        ".jpx",
        ignoreCase = true
    ) ||
            label.endsWith(".j2c", ignoreCase = true) || label.endsWith(".jpc", ignoreCase = true)

val Media.isTiff: Boolean
    get() = mimeType == "image/tiff" || mimeType == "image/tif" ||
            label.endsWith(".tif", ignoreCase = true) || label.endsWith(".tiff", ignoreCase = true)

val Media.isSvg: Boolean
    get() = mimeType == "image/svg+xml" || label.endsWith(".svg", ignoreCase = true)

val Media.isTrashed: Boolean get() = trashed == 1

val Media.isFavorite: Boolean get() = favorite == 1

val Media.isEncrypted: Boolean
    get() = this is UriMedia && DomainHooks.applicationId.isNotEmpty() &&
            getUri().toString().contains(DomainHooks.applicationId)

val Media.isCloud: Boolean
    get() = this is UriMedia && getUri().scheme == "cloud"

val Media.isLocalContent: Boolean
    get() = this is UriMedia && getUri().toString().startsWith("content://media")

val Media.canMakeActions: Boolean get() = !isEncrypted && isLocalContent && this !is ClassifiedMedia && !readUriOnly

val Media.isClassified: Boolean get() = this is ClassifiedMedia

val Media.getCategory: String?
    get() = if (this is ClassifiedMedia) {
        this.category
    } else null

fun <T : Media> T.compatibleMimeType(): String {
    return if (isImage) when (mimeType) {
        "image/jpeg" -> "image/jpeg"
        "image/png" -> "image/png"
        else -> "image/png"
    } else mimeType
}
