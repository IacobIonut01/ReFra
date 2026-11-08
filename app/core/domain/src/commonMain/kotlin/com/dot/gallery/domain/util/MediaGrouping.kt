package com.dot.gallery.domain.util

import com.dot.gallery.domain.model.Media

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
private val PIXEL_SUFFIX_REGEX = Regex(
    "\\.(ORIGINAL|RAW-\\d+|NIGHT|PORTRAIT|LONG_EXPOSURE|MP|MOTION-\\d+|PANO|TOP|BOTTOM|COVER|BURST\\d*)",
    RegexOption.IGNORE_CASE
)
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
 * opened view and multi-file share consistent with the cover shown in the grid.
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
