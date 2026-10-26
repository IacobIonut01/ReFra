package com.dot.gallery.core.metadata

/**
 * What metadata removal can do for one file.
 *
 * [removableCategories] is what the engine can strip from this container, [presentCategories]
 * is what the file actually carries, and [previews] holds a short human-readable value for
 * some present categories (for example the device name or capture date). [hasOtherMetadata]
 * reports unclassified metadata that only "Remove all" strips.
 */
data class SanitizationCapability(
    val format: MediaContainerFormat,
    val removableCategories: Set<MetadataCategory>,
    val presentCategories: Set<MetadataCategory> = emptySet(),
    val previews: Map<MetadataCategory, String> = emptyMap(),
    val hasOtherMetadata: Boolean = false,
    val retainedRequiredMetadata: Set<MetadataCategory> = MetadataPolicy.alwaysPreserved,
    val limitation: String? = null
) {
    val isSupported: Boolean get() = removableCategories.isNotEmpty()

    /** Present categories the engine can remove, in declaration order. */
    val removablePresentCategories: List<MetadataCategory>
        get() = MetadataCategory.entries.filter { it in presentCategories && it in removableCategories }

    val hasRemovableMetadata: Boolean
        get() = removablePresentCategories.isNotEmpty() || (isSupported && hasOtherMetadata)

    fun supports(categories: Set<MetadataCategory>): Boolean =
        categories.isNotEmpty() && removableCategories.containsAll(categories)
}
