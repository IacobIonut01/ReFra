/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ml

/**
 * The on-device embedding engine behind Smart Search and Categories. Both models produce
 * L2-normalized embeddings compared by cosine similarity, but they occupy very different
 * similarity bands, so thresholds are expressed as a model-agnostic *sensitivity*
 * (0 = permissive … 1 = strict) and mapped onto each model's operating band via
 * [thresholdFromSensitivity].
 *
 * Switching models changes [embeddingDimension] and every derived revision tag, which
 * makes the scan pipeline treat all previously embedded rows as stale — the library is
 * re-embedded automatically on the next Smart Scan.
 */
enum class SearchModel(
    /** Model files owned by this engine. */
    val group: ModelGroup,
    /** Embedding vector length in floats (CLIP 512-d, EG2 768-d). */
    val embeddingDimension: Int,
    /** Search-index phase revision tag; must match the tag that wrote legacy rows. */
    val indexRevisionTag: String,
    /** Category-classification phase revision tag. */
    val categoriesRevisionTag: String,
    /** Minimum cosine score for a search hit, inside this model's similarity band. */
    val searchThreshold: Float,
    /** Whether media search/indexing includes videos (multi-frame embeddings). */
    val supportsVideo: Boolean,
    /** Thumbnail decode size feeding the embedding pipeline during indexing. */
    val indexDecodeSize: Int,
    /** Decode size for the single media item used by visual similarity search. */
    val visualSearchInputSize: Int,
    /** Cosine at sensitivity 0 — just above this model's noise floor. */
    val sensitivityMinCosine: Float,
    /** Cosine at sensitivity 1 — near-exact matches only. */
    val sensitivityMaxCosine: Float,
    /** Approximate installed/download size, surfaced in model management UI. */
    val approxSizeBytes: Long,
) {
    /** OpenAI CLIP ViT-B/32 (INT8): separate text + vision encoders, 512-d, images only. */
    CLIP(
        group = ModelGroup.SEARCH_CLIP,
        embeddingDimension = 512,
        indexRevisionTag = "clip-v2",
        categoriesRevisionTag = "categories-v2",
        // CLIP scores spread wide (noise floor ~0.15) — 0.2 was the historical floor.
        searchThreshold = 0.2f,
        supportsVideo = false,
        indexDecodeSize = 256,
        visualSearchInputSize = 224,
        sensitivityMinCosine = 0.15f,
        sensitivityMaxCosine = 0.80f,
        approxSizeBytes = 150L * 1024 * 1024,
    ),

    /**
     * EmbeddingGemma 2 (onnx-community/embeddinggemma-2-ONNX): two-stage pipeline —
     * vision encoder emits soft tokens consumed by a shared multimodal backbone —
     * 768-d, text/image/video in one space.
     */
    EMBEDDING_GEMMA_2(
        group = ModelGroup.SEARCH_EG2,
        embeddingDimension = 768,
        indexRevisionTag = "eg2-v1",
        categoriesRevisionTag = "categories-v3",
        // EG2 scores sit in a compressed band (noise ~0.50-0.60, matches ~0.63-0.75).
        searchThreshold = 0.55f,
        supportsVideo = true,
        indexDecodeSize = 768,
        visualSearchInputSize = 768,
        sensitivityMinCosine = 0.50f,
        sensitivityMaxCosine = 0.90f,
        approxSizeBytes = 320L * 1024 * 1024,
    );

    /** Map a normalized sensitivity (see [Category.threshold] semantics) onto this model's cosine band. */
    fun thresholdFromSensitivity(sensitivity: Float): Float =
        (sensitivityMinCosine + sensitivity.coerceIn(0f, 1f) *
            (sensitivityMaxCosine - sensitivityMinCosine)).coerceIn(sensitivityMinCosine, sensitivityMaxCosine)

    companion object {
        val DEFAULT = CLIP
    }
}
