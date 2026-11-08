package com.dot.gallery.core.metadata

/**
 * How one metadata item (an EXIF tag, XMP property, PNG chunk, MP4 atom…) is classified.
 * Every scrubber, the inspector and the verifier read the same per-format tables, so what the
 * probe reports, what removal strips and what verification checks cannot drift apart.
 */
internal sealed interface MetadataClass {
    /** Rendering, structural or linkage data that is never removed. */
    data object Keep : MetadataClass

    /** Unclassified metadata; removed only when every category is selected. */
    data object Other : MetadataClass

    /**
     * A blob that can only be removed whole (a compressed or nested block whose contents were
     * inspected separately); removed whenever any category is selected.
     */
    data object Opaque : MetadataClass

    data class Of(val categories: Set<MetadataCategory>) : MetadataClass

    companion object {
        fun of(vararg categories: MetadataCategory): MetadataClass = Of(categories.toSet())
    }
}
