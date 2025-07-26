package com.dot.gallery.feature_node.presentation.search.helpers

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtProvider
import ai.onnxruntime.OrtSession
import ai.onnxruntime.providers.NNAPIFlags
import android.content.Context
import android.graphics.Bitmap
import com.dot.gallery.R
import com.dot.gallery.feature_node.presentation.search.tokenizer.ClipTokenizer
import com.dot.gallery.feature_node.presentation.search.util.centerCrop
import com.dot.gallery.feature_node.presentation.search.util.normalizeL2
import com.dot.gallery.feature_node.presentation.search.util.preProcess
import com.dot.gallery.feature_node.presentation.util.printDebug
import java.nio.IntBuffer
import java.util.Collections
import java.util.EnumSet

class SearchVisionHelper(private val context: Context) {
    private val tokenizer = ClipTokenizer(context)
    private val ortEnv = OrtEnvironment.getEnvironment()

    fun setupVisionSession() = createOrtSessionWithFallback( R.raw.visual_quant)
    fun setupTextSession() = createOrtSessionWithFallback(R.raw.textual_quant)

    private fun createOrtSessionWithFallback(modelID: Int): OrtSession {
        val options = OrtSession.SessionOptions()

        try {
            printDebug("Available providers: ${OrtEnvironment.getAvailableProviders()}")
            // Try NNAPI first
            if (OrtEnvironment.getAvailableProviders().contains(OrtProvider.NNAPI)) {
                printDebug("Using NNAPI for inference")
                options.addNnapi(EnumSet.of(NNAPIFlags.USE_FP16))
            }
            // Optionally try QNN (Qualcomm)
            else if (OrtEnvironment.getAvailableProviders().contains(OrtProvider.QNN)) {
                printDebug("Using QNN for inference")
                val qnnOptions = mapOf(
                    "backend_type" to "htp",
                    "qnn_context_cache_enable" to "1",
                    "qnn_context_priority" to "high",
                    "enable_htp_fp16_precision" to "1"
                )
                options.addQnn(qnnOptions)
            }

        } catch (e: Exception) {
            printDebug("NNAPI or QNN not available, falling back to CPU: ${e.message}")
            // No need to add anything; CPU is default fallback
        }

        // Load model into session
        val modelBytes = context.resources.openRawResource(modelID).readBytes()
        return ortEnv.createSession(modelBytes, options)
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
