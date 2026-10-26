package com.dot.gallery.core.metadata

import com.dot.gallery.domain.model.Media

interface MetadataSanitizer {
    suspend fun probe(media: Media): SanitizationCapability
    suspend fun sanitize(
        media: Media,
        categories: Set<MetadataCategory>,
        saveMode: MetadataSaveMode = MetadataSaveMode.SAVE_COPY
    ): SanitizationResult
    suspend fun recoverPendingTransactions()
}
