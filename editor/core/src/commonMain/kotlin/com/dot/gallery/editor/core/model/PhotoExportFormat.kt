package com.dot.gallery.editor.core.model

/**
 * A container an edited photo can be written in. `null` is not a member:
 * an export with no explicit format follows the source's own container
 * (JPEG stays JPEG, PNG stays PNG).
 *
 * [mimeType] and [extension] are the MediaStore/file identity of the copy;
 * display names live in the UI layer so they can be localised.
 */
enum class PhotoExportFormat(val mimeType: String, val extension: String) {
    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    WEBP("image/webp", "webp"),
    /** HEIF container with HEVC-coded stills (`.heic`). */
    HEIF("image/heic", "heic"),
    JXL("image/jxl", "jxl"),
    /** Streamed from the RAW decoder; only a developed RAW can be written so. */
    TIFF_8("image/tiff", "tiff"),
    TIFF_16("image/tiff", "tiff");

    val isTiff: Boolean get() = this == TIFF_8 || this == TIFF_16
}
