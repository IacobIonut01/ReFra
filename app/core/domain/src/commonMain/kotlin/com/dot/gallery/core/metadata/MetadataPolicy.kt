package com.dot.gallery.core.metadata

/**
 * Category presets for metadata removal. A removal request is a set of [MetadataCategory]
 * values; [COLOR_HDR][MetadataCategory.COLOR_HDR] and
 * [STRUCTURAL_FUNCTIONAL][MetadataCategory.STRUCTURAL_FUNCTIONAL] are never removable because
 * dropping them changes how the media renders or breaks embedded linkage.
 */
object MetadataPolicy {
    val alwaysPreserved: Set<MetadataCategory> = setOf(
        MetadataCategory.COLOR_HDR,
        MetadataCategory.STRUCTURAL_FUNCTIONAL
    )

    /** Every category that may be removed. Selecting all of them is "Remove all". */
    val ALL: Set<MetadataCategory> = MetadataCategory.entries.toSet() - alwaysPreserved

    val LOCATION: Set<MetadataCategory> = setOf(MetadataCategory.LOCATION)

    fun removesEverything(categories: Set<MetadataCategory>): Boolean = categories.containsAll(ALL)

    /** Drops categories that can never be removed, so callers can pass any selection. */
    fun normalize(categories: Set<MetadataCategory>): Set<MetadataCategory> = categories - alwaysPreserved
}
