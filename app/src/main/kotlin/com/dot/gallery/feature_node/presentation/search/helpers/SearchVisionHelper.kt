package com.dot.gallery.feature_node.presentation.search.helpers

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.providers.NNAPIFlags
import android.graphics.Bitmap
import com.dot.gallery.core.ml.ModelManager
import com.dot.gallery.core.ml.ModelsNotAvailableException
import com.dot.gallery.feature_node.presentation.search.tokenizer.ClipTokenizer
import com.dot.gallery.feature_node.presentation.search.util.centerCrop
import com.dot.gallery.feature_node.presentation.search.util.normalizeL2
import com.dot.gallery.feature_node.presentation.search.util.preProcess
import com.dot.gallery.feature_node.presentation.util.printDebug
import java.nio.IntBuffer
import java.util.Collections
import java.util.EnumSet

class SearchVisionHelper(private val modelManager: ModelManager) {
    private val tokenizer by lazy {
        ClipTokenizer(
            vocabFile = modelManager.getModelFile("vocab.json"),
            mergesFile = modelManager.getModelFile("merges.txt")
        )
    }
    private val ortEnv = OrtEnvironment.getEnvironment()

    fun setupVisionSession() = createOrtSessionWithFallback("visual_quant.onnx")
    fun setupTextSession() = createOrtSessionWithFallback("textual_quant.onnx")

    private fun createOrtSessionWithFallback(modelName: String): OrtSession {
        if (!modelManager.isReady) throw ModelsNotAvailableException()

        val isQuantized = modelName.contains("quant")

        val options = OrtSession.SessionOptions().apply {
            // Enable all graph optimizations (constant folding, op fusion, etc.)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            // Use multiple CPU threads for intra-op parallelism (e.g. matmul)
            setIntraOpNumThreads(Runtime.getRuntime().availableProcessors().coerceIn(2, 4))
        }

        // Only try NNAPI for non-quantized models.
        // Quantized (INT8) models cause severe NNAPI overhead:
        //  - NNAPI compilation can take minutes for complex models like CLIP
        //  - Many quantized ops aren't NNAPI-supported, causing graph partitioning
        //    with expensive CPU↔NNAPI memory copies at each boundary
        //  - USE_FP16 flag with INT8 model adds unnecessary type conversions
        if (!isQuantized) {
            try {
                printDebug("Available providers: ${OrtEnvironment.getAvailableProviders()}")
                printDebug("Using NNAPI for inference")
                options.addNnapi(EnumSet.of(NNAPIFlags.USE_FP16))
            } catch (e: Exception) {
                printDebug("NNAPI not available, falling back to CPU: ${e.message}")
            }
        } else {
            printDebug("Using optimized CPU inference for quantized model: $modelName")
        }

        // Load model from filesDir using path-based API (memory-mapped, avoids OOM)
        val modelFile = modelManager.getModelFile(modelName)
        return ortEnv.createSession(modelFile.absolutePath, options)
    }

    fun getTextEmbedding(session: OrtSession, text: String): FloatArray {
        val tokenBOS = 49406
        val tokenEOS = 49407

        val queryFilter = Regex("[^A-Za-z0-9 ]")
        // Tokenize
        val textClean = queryFilter.replace(text, "").lowercase()
        var tokens: MutableList<Int> = ArrayList()
        tokens.add(tokenBOS)
        tokens.addAll(tokenizer.encode(textClean))
        tokens.add(tokenEOS)

        var mask: MutableList<Int> = ArrayList()
        for (i in 0 until tokens.size) {
            mask.add(1)
        }
        while (tokens.size < 77) {
            tokens.add(0)
            mask.add(0)
        }
        tokens = tokens.subList(0, 77)
        mask = mask.subList(0, 77)

        // Convert to tensor
        val inputShape = longArrayOf(1, 77)
        val inputIds = IntBuffer.allocate(1 * 77)
        inputIds.rewind()
        for (i in 0 until 77) {
            inputIds.put(tokens[i])
        }
        inputIds.rewind()
        val inputIdsTensor = OnnxTensor.createTensor(ortEnv, inputIds, inputShape)

        val attentionMask = IntBuffer.allocate(1 * 77)
        attentionMask.rewind()
        for (i in 0 until 77) {
            attentionMask.put(mask[i])
        }
        attentionMask.rewind()
        val attentionMaskTensor = OnnxTensor.createTensor(ortEnv, attentionMask, inputShape)

        val inputMap: MutableMap<String, OnnxTensor> = HashMap()
        inputMap["input_ids"] = inputIdsTensor
        inputMap["attention_mask"] = attentionMaskTensor

        val output = session.run(inputMap)
        output.use {
            @Suppress("UNCHECKED_CAST") val rawOutput =
                ((output?.get(0)?.value) as Array<FloatArray>)[0]
            return normalizeL2(rawOutput)
        }
    }

    fun getImageEmbedding(session: OrtSession, bitmap: Bitmap): FloatArray {
        val rawBitmap = centerCrop(bitmap, 224)
        val inputShape = longArrayOf(1, 3, 224, 224)
        val inputName = "pixel_values"
        val imgData = preProcess(rawBitmap)
        val inputTensor = OnnxTensor.createTensor(ortEnv, imgData, inputShape)

        return inputTensor.use {
            val output = session.run(Collections.singletonMap(inputName, inputTensor))
            output.use {
                @Suppress("UNCHECKED_CAST") val rawOutput =
                    ((output?.get(0)?.value) as Array<FloatArray>)[0]
                return@use normalizeL2(rawOutput)
            }
        }
    }

    companion object {

        const val threshold = 0.2
    }

}
