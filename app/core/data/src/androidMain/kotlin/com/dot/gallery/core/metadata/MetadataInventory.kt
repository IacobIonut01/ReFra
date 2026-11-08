package com.dot.gallery.core.metadata

/**
 * What a file carries. [functional] holds markers (orientation value, HDR gain map, Motion
 * Photo linkage) that removal must preserve; [locationSignatures] holds raw location bytes that
 * must not survive anywhere in the output once location is removed.
 */
internal class MetadataInventory {
    val present = linkedSetOf<MetadataCategory>()
    val previews = linkedMapOf<MetadataCategory, String>()
    var other = false

    /** Whether a blob removable only as a whole is present; any removal must drop it. */
    var opaque = false

    /** The distinct category sets items were classified under, to tell what must survive a removal. */
    val classes = linkedSetOf<Set<MetadataCategory>>()
    val functional = linkedSetOf<String>()
    val locationSignatures = mutableListOf<ByteArray>()

    /** Categories present in a form the engine cannot remove (for example a GPS telemetry track). */
    val blocked = linkedSetOf<MetadataCategory>()

    fun record(metadataClass: MetadataClass, preview: String? = null) {
        when (metadataClass) {
            MetadataClass.Keep -> Unit
            MetadataClass.Other -> other = true
            MetadataClass.Opaque -> {
                other = true
                opaque = true
            }
            is MetadataClass.Of -> {
                present += metadataClass.categories
                classes += metadataClass.categories
                if (preview != null) preview(metadataClass.categories.first(), preview)
            }
        }
    }

    fun preview(category: MetadataCategory, value: String?) {
        val cleaned = value?.replace(Regex("[\\u0000-\\u001F]+"), " ")?.trim().orEmpty()
        if (cleaned.isNotEmpty() && category !in previews) previews[category] = cleaned.take(MAX_PREVIEW)
    }

    fun locationSignature(bytes: ByteArray) {
        // Low-entropy values (zero coordinates, padding) could match unrelated bytes elsewhere.
        if (bytes.size >= 6 && bytes.toSet().size >= 5) locationSignatures += bytes
    }

    private companion object {
        const val MAX_PREVIEW = 64
    }
}
