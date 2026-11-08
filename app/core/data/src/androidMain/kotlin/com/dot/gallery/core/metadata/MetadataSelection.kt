package com.dot.gallery.core.metadata

internal class MetadataSelection(categories: Set<MetadataCategory>) {
    val categories: Set<MetadataCategory> = MetadataPolicy.normalize(categories)
    val everything: Boolean = MetadataPolicy.removesEverything(this.categories)
    val isEmpty: Boolean get() = categories.isEmpty()

    fun removes(category: MetadataCategory): Boolean = category in categories

    fun removes(metadataClass: MetadataClass): Boolean = when (metadataClass) {
        MetadataClass.Keep -> false
        MetadataClass.Other -> everything
        MetadataClass.Opaque -> !isEmpty
        is MetadataClass.Of -> metadataClass.categories.any { it in categories }
    }
}
