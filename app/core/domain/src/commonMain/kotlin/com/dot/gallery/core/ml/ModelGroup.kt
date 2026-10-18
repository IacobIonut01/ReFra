/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ml

enum class ModelGroup(val subDir: String, val files: List<String>) {
    /**
     * CLIP ViT-B/32 (INT8): separate text (`textual_quant`) + image (`visual_quant`)
     * encoders producing 512-d embeddings, with the BPE vocab/merges tokenizer files.
     * Kept as the lightweight default search engine (see [SearchModel]).
     */
    SEARCH_CLIP(
        subDir = "clip",
        files = listOf("visual_quant.onnx", "textual_quant.onnx", "vocab.json", "merges.txt")
    ),

    /**
     * EmbeddingGemma 2 (onnx-community/embeddinggemma-2-ONNX): multimodal text/image/video
     * encoder producing 768-d embeddings. `model_q4` is the shared backbone,
     * `vision_encoder_q4` the image/frame patch encoder, `tokenizer.json` the Gemma BPE
     * tokenizer. ONNX external-data files must sit next to their `.onnx` graph.
     */
    SEARCH_EG2(
        subDir = "embeddinggemma2",
        files = listOf(
            "model_q4.onnx",
            "model_q4.onnx_data",
            "vision_encoder_q4.onnx",
            "vision_encoder_q4.onnx_data",
            "tokenizer.json",
            "tokenizer_config.json"
        )
    ),
    CUTOUT(
        subDir = "sam",
        files = listOf("mobile_sam_image_encoder.onnx", "sam_mask_decoder_single.onnx")
    ),

    /** UltraFace RFB-320 lightweight face detector — powers auto face-blur in the editor. */
    FACE_DETECT(
        subDir = "face_detect",
        files = listOf("version-RFB-320.onnx")
    ),

    /** ArcFace/MobileFaceNet face-embedding model — powers on-device Person grouping. */
    FACE_RECOGNITION(
        subDir = "face_recog",
        files = listOf("arcface.onnx")
    );

    companion object {
        /** The group a model file belongs to (defaults to [SEARCH_CLIP] for unknown names). */
        fun of(fileName: String): ModelGroup =
            entries.firstOrNull { fileName in it.files } ?: SEARCH_CLIP
    }
}
