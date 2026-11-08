/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.search.helpers

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.providers.NNAPIFlags
import android.graphics.Bitmap
import com.dot.gallery.app.presentation.search.tokenizer.ClipTokenizer
import com.dot.gallery.app.presentation.search.tokenizer.GemmaTokenizer
import com.dot.gallery.core.logging.printDebug
import com.dot.gallery.core.ml.ModelManager
import com.dot.gallery.core.ml.ModelsNotAvailableException
import com.dot.gallery.core.ml.SearchModel
import com.dot.gallery.core.ui.util.centerCrop
import com.dot.gallery.core.ui.util.preProcess
import com.dot.gallery.domain.util.normalizeL2
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.nio.LongBuffer
import java.util.EnumSet

/**
 * Search-embedding inference for whichever [SearchModel] is active.
 *
 * CLIP (`SearchModel.CLIP`) runs two independent encoders — `textual_quant` for tokenized
 * queries, `visual_quant` for 224-px center-cropped images — into a 512-d space.
 *
 * EmbeddingGemma 2 (`SearchModel.EMBEDDING_GEMMA_2`) is a two-stage pipeline:
 *   - `vision_encoder_q4.onnx` turns image patches into per-image soft tokens
 *     (`pixel_values` [N, maxPatches, 768], `pixel_position_ids` [N, maxPatches, 2]
 *     → `image_features` [softTokens, 512]).
 *   - `model_q4.onnx` (the shared text backbone) consumes `input_ids` where each media
 *     item expands to `boi + placeholder×softTokens + eoi`, plus the modality feature
 *     tensors (empty [0,512] for absent modalities) → `sentence_embedding` [1, 768],
 *     already mean-pooled and L2-normalized.
 *
 * Both engines expose the same session shape — a text session plus an optional vision
 * session — so [SearchSessions] carries the resolved model and every embed call
 * dispatches on it. Sessions are therefore valid for exactly one engine: callers get a
 * fresh [SearchSessions] whenever [ModelManager.activeSearchModel] changes.
 */
class SearchVisionHelper(private val modelManager: ModelManager) {

    /**
     * The sessions one embedding path needs, bound to [model]. The text encoder
     * ([text]) is always present; [vision] is opened only for media indexing / visual
     * search. Closing closes all — except for shared sessions ([owned] == false), where
     * [close] is a no-op and the helper owns the lifetime.
     */
    class SearchSessions(
        val model: SearchModel,
        val text: OrtSession,
        vision: OrtSession? = null,
        internal val owned: Boolean = true,
    ) : AutoCloseable {
        @Volatile
        var vision: OrtSession? = vision
            internal set

        override fun close() {
            if (owned) forceClose()
        }

        internal fun forceClose() {
            runCatching { text.close() }
            runCatching { vision?.close() }
            vision = null
        }
    }

    private val ortEnv = OrtEnvironment.getEnvironment()

    /** Lazily resolved so only the active engine's tokenizer pays the parse cost. */
    private val clipTokenizer by lazy {
        ClipTokenizer(
            vocabFile = modelManager.getModelFile(CLIP_VOCAB_FILE),
            mergesFile = modelManager.getModelFile(CLIP_MERGES_FILE),
        )
    }
    private val gemmaTokenizer by lazy {
        GemmaTokenizer(modelManager.getModelFile(EG2_TOKENIZER_FILE))
    }

    /**
     * Warm session shared by interactive callers (the search field runs one embed per
     * keystroke). Session init is heavy — EG2's backbone alone loads 174 MB of external
     * data plus full graph optimization — so creating it per query took seconds; a cached
     * session is created once and reused. [SearchSessions.close] on it is a no-op; it is
     * dropped when the active model changes or stops being ready.
     */
    private val sharedLock = Any()

    @Volatile
    private var sharedSessions: SearchSessions? = null

    /** Text-only queries: the active engine's text encoder/backbone alone. */
    fun setupTextSession(): SearchSessions {
        val model = modelManager.activeSearchModel
        return SearchSessions(model = model, text = createOrtSessionWithFallback(model, model.textModelFile))
    }

    /** Image/video embeds need the vision encoder in addition to the text session. */
    fun setupVisionSession(): SearchSessions {
        val model = modelManager.activeSearchModel
        return SearchSessions(
            model = model,
            text = createOrtSessionWithFallback(model, model.textModelFile),
            vision = createOrtSessionWithFallback(model, model.visionModelFile),
        )
    }

    /** Shared text-encoder session for interactive text queries. */
    fun sharedTextSession(): SearchSessions = sharedSession(needVision = false)

    /** Shared text+vision session for visual search / one-off media embeds. */
    fun sharedVisionSession(): SearchSessions = sharedSession(needVision = true)

    private fun sharedSession(needVision: Boolean): SearchSessions = synchronized(sharedLock) {
        val model = modelManager.activeSearchModel
        val existing = sharedSessions
        if (existing != null && (existing.model != model || !modelManager.isReady(model))) {
            // Model switched or was removed — drop the stale session so its native
            // memory frees; embeddings from another engine would also be invalid.
            existing.forceClose()
            sharedSessions = null
        }
        if (!modelManager.isReady(model)) throw ModelsNotAvailableException()
        val cached = sharedSessions
        if (cached != null && (!needVision || cached.vision != null)) return cached
        val session = cached ?: SearchSessions(
            model = model,
            text = createOrtSessionWithFallback(model, model.textModelFile),
            owned = false,
        ).also { sharedSessions = it }
        if (needVision && session.vision == null) {
            session.vision = createOrtSessionWithFallback(model, model.visionModelFile)
        }
        session
    }

    private fun createOrtSessionWithFallback(model: SearchModel, modelName: String): OrtSession {
        if (!modelManager.isReady(model)) throw ModelsNotAvailableException()

        val isQuantized = modelName.contains("q4") || modelName.contains("quant")
        val options = OrtSession.SessionOptions().apply {
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            // The vision encoders are intra-op bound; allow up to 8 threads so indexing
            // saturates big cores on modern SoCs (low-core devices keep the 4 floor).
            setIntraOpNumThreads(Runtime.getRuntime().availableProcessors().coerceIn(4, 8))
        }

        // Only try NNAPI for non-quantized models — NNAPI hurts int8/q4 graphs
        // (long compiles, partitioned graphs, CPU↔NNAPI copies at every boundary).
        if (!isQuantized) {
            try {
                printDebug("ml.search", "Available providers: ${OrtEnvironment.getAvailableProviders()}")
                printDebug("ml.search", "Using NNAPI for inference")
                options.addNnapi(EnumSet.of(NNAPIFlags.USE_FP16))
            } catch (e: Exception) {
                printDebug("ml.search", "NNAPI not available, falling back to CPU: ${e.message}")
            }
        } else {
            printDebug("ml.search", "Using optimized CPU inference for quantized model: $modelName")
        }

        val modelFile = modelManager.getModelFile(modelName)
        return ortEnv.createSession(modelFile.absolutePath, options)
    }

    /** Embed a text query in the active engine's shared space. */
    fun getTextEmbedding(sessions: SearchSessions, text: String): FloatArray =
        when (sessions.model) {
            SearchModel.CLIP -> clipTextEmbedding(sessions.text, text)
            SearchModel.EMBEDDING_GEMMA_2 ->
                runBackbone(sessions.text, gemmaTokenizer.encode(QUERY_PROMPT + text))
        }

    /** Embed an image for indexing / visual search. */
    fun getImageEmbedding(sessions: SearchSessions, bitmap: Bitmap): FloatArray =
        when (sessions.model) {
            SearchModel.CLIP -> clipImageEmbedding(
                requireNotNull(sessions.vision) { "vision session required for image embeds" },
                bitmap
            )
            SearchModel.EMBEDDING_GEMMA_2 -> {
                val vision = requireNotNull(sessions.vision) { "vision session required for image embeds" }
                val patchified = EmbeddingGemmaImageProcessor.process(
                    bitmap,
                    EmbeddingGemmaImageProcessor.IMAGE_MAX_SOFT_TOKENS,
                )
                val imageFeatures = runVisionEncoder(vision, listOf(patchified))
                val ids = mediaTokenIds(BOI_ID, IMAGE_TOKEN_ID, patchified.numSoftTokens, EOI_ID)
                runBackbone(sessions.text, ids, imageFeatures = imageFeatures)
            }
        }

    /**
     * Embed a video from sampled [frames]. Under EG2 every frame is patchified with the
     * video soft-token budget and the backbone gets `(boi <video|…|> eoi)` per frame.
     * CLIP has no video modality — callers normally gate on [SearchModel.supportsVideo],
     * but as a graceful fallback the first frame is embedded as an image.
     */
    fun getVideoEmbedding(sessions: SearchSessions, frames: List<Bitmap>): FloatArray {
        require(frames.isNotEmpty()) { "at least one frame is required" }
        return when (sessions.model) {
            SearchModel.CLIP -> clipImageEmbedding(
                requireNotNull(sessions.vision) { "vision session required for image embeds" },
                frames.first()
            )
            SearchModel.EMBEDDING_GEMMA_2 -> {
                val vision = requireNotNull(sessions.vision) { "vision session required for video embeds" }
                val patchified = frames.map {
                    EmbeddingGemmaImageProcessor.process(it, EmbeddingGemmaImageProcessor.VIDEO_MAX_SOFT_TOKENS)
                }
                val videoFeatures = runVisionEncoder(vision, patchified)
                val softPerFrame = patchified.map { it.numSoftTokens }
                val ids = buildList {
                    add(GemmaTokenizer.BOS_ID)
                    softPerFrame.forEach { n ->
                        add(BOI_ID)
                        repeat(n) { add(VIDEO_TOKEN_ID) }
                        add(EOI_ID)
                    }
                    add(GemmaTokenizer.EOS_ID)
                }.toIntArray()
                runBackbone(sessions.text, ids, videoFeatures = videoFeatures)
            }
        }
    }

    // ==================== CLIP engine ====================

    /**
     * CLIP query path: BOS + BPE tokens (lowercased, punctuation stripped) + EOS,
     * right-padded to the fixed 77-token window, through `textual_quant`.
     */
    private fun clipTextEmbedding(session: OrtSession, text: String): FloatArray {
        val tokens = ArrayList<Int>(77)
        tokens.add(CLIP_BOS_ID)
        tokens.addAll(clipTokenizer.encode(CLIP_FILTER.replace(text, "").lowercase()))
        tokens.add(CLIP_EOS_ID)
        val mask = ArrayList<Int>(77)
        repeat(tokens.size.coerceAtMost(77)) { mask.add(1) }
        while (tokens.size < 77) {
            tokens.add(0)
            mask.add(0)
        }
        val inputIds = IntBuffer.allocate(77)
        repeat(77) { inputIds.put(tokens[it]) }
        inputIds.rewind()
        val attentionMask = IntBuffer.allocate(77)
        repeat(77) { attentionMask.put(mask[it]) }
        attentionMask.rewind()
        val shape = longArrayOf(1, 77)
        val inputIdsTensor = OnnxTensor.createTensor(ortEnv, inputIds, shape)
        val attentionMaskTensor = OnnxTensor.createTensor(ortEnv, attentionMask, shape)
        return try {
            session.run(mapOf("input_ids" to inputIdsTensor, "attention_mask" to attentionMaskTensor))
                .use { output ->
                    @Suppress("UNCHECKED_CAST")
                    normalizeL2((output[0].value as Array<FloatArray>)[0])
                }
        } finally {
            inputIdsTensor.close()
            attentionMaskTensor.close()
        }
    }

    /** CLIP image path: 224-px center crop → channel-normalized `pixel_values` → `visual_quant`. */
    private fun clipImageEmbedding(session: OrtSession, bitmap: Bitmap): FloatArray {
        val cropped = centerCrop(bitmap, CLIP_INPUT_SIZE)
        try {
            val imgData = preProcess(cropped)
            val inputTensor = OnnxTensor.createTensor(
                ortEnv, imgData, longArrayOf(1, 3, CLIP_INPUT_SIZE.toLong(), CLIP_INPUT_SIZE.toLong())
            )
            return inputTensor.use {
                session.run(mapOf("pixel_values" to inputTensor)).use { output ->
                    @Suppress("UNCHECKED_CAST")
                    normalizeL2((output[0].value as Array<FloatArray>)[0])
                }
            }
        } finally {
            if (cropped !== bitmap) cropped.recycle()
        }
    }

    // ==================== EmbeddingGemma 2 engine ====================

    private fun mediaTokenIds(boi: Int, token: Int, count: Int, eoi: Int): IntArray {
        val ids = IntArray(count + 4)
        ids[0] = GemmaTokenizer.BOS_ID
        ids[1] = boi
        for (i in 0 until count) ids[2 + i] = token
        ids[count + 2] = eoi
        ids[count + 3] = GemmaTokenizer.EOS_ID
        return ids
    }

    /**
     * Run the vision encoder on a batch of patchified images/frames.
     * Returns the flat `[totalSoftTokens, 512]` feature block.
     */
    private fun runVisionEncoder(
        vision: OrtSession,
        patchified: List<EmbeddingGemmaImageProcessor.Patchified>,
    ): FloatArray {
        val batch = patchified.size
        val maxPatches = patchified.first().pixelValues.size / EmbeddingGemmaImageProcessor.PATCH_DIM
        require(patchified.all { it.pixelValues.size == maxPatches * EmbeddingGemmaImageProcessor.PATCH_DIM }) {
            "all inputs must share the patch budget"
        }
        val pixels = FloatArray(batch * maxPatches * EmbeddingGemmaImageProcessor.PATCH_DIM)
        val positions = LongArray(batch * maxPatches * 2)
        patchified.forEachIndexed { i, p ->
            System.arraycopy(p.pixelValues, 0, pixels, i * p.pixelValues.size, p.pixelValues.size)
            System.arraycopy(p.positionIds, 0, positions, i * p.positionIds.size, p.positionIds.size)
        }
        val pixelTensor = OnnxTensor.createTensor(
            ortEnv, FloatBuffer.wrap(pixels),
            longArrayOf(batch.toLong(), maxPatches.toLong(), EmbeddingGemmaImageProcessor.PATCH_DIM.toLong())
        )
        val positionTensor = OnnxTensor.createTensor(
            ortEnv, LongBuffer.wrap(positions),
            longArrayOf(batch.toLong(), maxPatches.toLong(), 2)
        )
        pixelTensor.use { pt ->
            positionTensor.use { post ->
                vision.run(mapOf("pixel_values" to pt, "pixel_position_ids" to post)).use { result ->
                    @Suppress("UNCHECKED_CAST")
                    val output = result["image_features"].get().value as Array<FloatArray>
                    return output.flatRows()
                }
            }
        }
    }

    private fun Array<FloatArray>.flatRows(): FloatArray {
        val out = FloatArray(size * first().size)
        forEachIndexed { i, row -> System.arraycopy(row, 0, out, i * row.size, row.size) }
        return out
    }

    /**
     * Run the multimodal backbone and return the normalized `sentence_embedding` row.
     * Absent modalities are fed as empty [0,512] tensors (matching transformers.js).
     */
    private fun runBackbone(
        session: OrtSession,
        inputIds: IntArray,
        imageFeatures: FloatArray? = null,
        videoFeatures: FloatArray? = null,
        audioFeatures: FloatArray? = null,
    ): FloatArray {
        val seq = inputIds.size.toLong()
        val idTensor = OnnxTensor.createTensor(
            ortEnv,
            LongBuffer.wrap(LongArray(inputIds.size) { inputIds[it].toLong() }),
            longArrayOf(1, seq)
        )
        val maskTensor = OnnxTensor.createTensor(
            ortEnv, LongBuffer.wrap(LongArray(inputIds.size) { 1L }), longArrayOf(1, seq)
        )
        fun featuresTensor(values: FloatArray?): OnnxTensor {
            val rows = (values?.size ?: 0) / FEATURE_DIM
            val buffer = FloatBuffer.wrap(values ?: FloatArray(0))
            return OnnxTensor.createTensor(ortEnv, buffer, longArrayOf(rows.toLong(), FEATURE_DIM.toLong()))
        }
        val imageTensor = featuresTensor(imageFeatures)
        val videoTensor = featuresTensor(videoFeatures)
        val audioTensor = featuresTensor(audioFeatures)
        return try {
            val inputs = mapOf(
                "input_ids" to idTensor,
                "attention_mask" to maskTensor,
                "image_features" to imageTensor,
                "video_features" to videoTensor,
                "audio_features" to audioTensor,
            )
            session.run(inputs).use { result ->
                @Suppress("UNCHECKED_CAST")
                val output = result["sentence_embedding"].get().value as Array<FloatArray>
                normalizeL2(output[0])
            }
        } finally {
            idTensor.close()
            maskTensor.close()
            imageTensor.close()
            videoTensor.close()
            audioTensor.close()
        }
    }

    private companion object {
        // CLIP ViT-B/32 (INT8) files + inference constants.
        const val CLIP_TEXT_FILE = "textual_quant.onnx"
        const val CLIP_VISION_FILE = "visual_quant.onnx"
        const val CLIP_VOCAB_FILE = "vocab.json"
        const val CLIP_MERGES_FILE = "merges.txt"
        const val CLIP_INPUT_SIZE = 224
        const val CLIP_BOS_ID = 49406
        const val CLIP_EOS_ID = 49407
        val CLIP_FILTER = Regex("[^A-Za-z0-9 ]")

        // EmbeddingGemma 2 files + inference constants.
        const val EG2_TEXT_FILE = "model_q4.onnx"
        const val EG2_VISION_FILE = "vision_encoder_q4.onnx"
        const val EG2_TOKENIZER_FILE = "tokenizer.json"

        /** Soft-token feature width consumed by the EG2 backbone. */
        const val FEATURE_DIM = 512

        /** EmbeddingGemma task prefix for retrieval queries. */
        const val QUERY_PROMPT = "task: search result | query: "

        /** Media placeholder token ids (from tokenizer_config / config.json). */
        const val IMAGE_TOKEN_ID = 258880
        const val VIDEO_TOKEN_ID = 258884
        const val BOI_ID = 255999
        const val EOI_ID = 258882

        private val SearchModel.textModelFile: String
            get() = when (this) {
                SearchModel.CLIP -> CLIP_TEXT_FILE
                SearchModel.EMBEDDING_GEMMA_2 -> EG2_TEXT_FILE
            }

        private val SearchModel.visionModelFile: String
            get() = when (this) {
                SearchModel.CLIP -> CLIP_VISION_FILE
                SearchModel.EMBEDDING_GEMMA_2 -> EG2_VISION_FILE
            }
    }
}
