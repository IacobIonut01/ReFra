/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ml

/**
 * The detector + embedder pair behind on-device face grouping, offered as quality tiers.
 *
 * Only tiers whose model groups are published can be selected ([isAvailable]); the others are
 * listed so the choice is visible before their models ship. A tier owns its embedding space, so
 * switching tiers changes [revisionTag] and every face row written by another tier becomes stale:
 * the next PERSONS scan detects, embeds and clusters the library again.
 */
enum class FaceModel(
    /** Model groups this tier needs, detector first. Empty until the tier's models are published. */
    val groups: List<ModelGroup>,
    /** Embedding vector length the clusterer expects from this tier's embedder. */
    val embeddingDimension: Int,
    /**
     * Appended to the face index and cluster revisions. Empty for [STANDARD] so the rows written
     * before tiers existed stay current.
     */
    val revisionTag: String,
    /** Approximate download size of all [groups], surfaced before the user downloads them. */
    val approxSizeBytes: Long,
) {
    /** UltraFace RFB-320 detector + ArcFace embedder (512-d): the models the app has shipped so far. */
    STANDARD(
        groups = listOf(ModelGroup.FACE_DETECT, ModelGroup.FACE_RECOGNITION),
        embeddingDimension = 512,
        revisionTag = "",
        approxSizeBytes = 1_363_149L + 130L * 1024 * 1024,
    ),

    /** YOLOv8-Face nano (or BlazeFace) detector + MobileFaceNet embedder: lightest on battery. */
    FAST(
        groups = emptyList(),
        embeddingDimension = 128,
        revisionTag = "fast-v1",
        approxSizeBytes = 12L * 1024 * 1024,
    ),

    /** SCRFD detector + ArcFace MobileNetV2/ResNet18 embedder (512-d): better with small and profile faces. */
    ACCURATE(
        groups = emptyList(),
        embeddingDimension = 512,
        revisionTag = "accurate-v1",
        approxSizeBytes = 60L * 1024 * 1024,
    );

    /** Whether this tier's models are published, so it can be downloaded and selected. */
    val isAvailable: Boolean get() = groups.isNotEmpty()

    /** Suffix for the face index and cluster phase revisions; empty for [STANDARD]. */
    val revisionSuffix: String get() = if (revisionTag.isEmpty()) "" else ":$revisionTag"

    companion object {
        val DEFAULT = STANDARD

        /** A persisted tier name, falling back to [DEFAULT] for unknown or unpublished tiers. */
        fun resolve(name: String?): FaceModel =
            entries.firstOrNull { it.name == name }?.takeIf { it.isAvailable } ?: DEFAULT
    }
}
