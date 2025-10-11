package com.dot.gallery.feature_node.presentation.classifier

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtProvider
import ai.onnxruntime.OrtSession
import ai.onnxruntime.providers.NNAPIFlags
import android.content.Context
import android.graphics.Bitmap
import com.dot.gallery.R
import com.dot.gallery.feature_node.presentation.search.util.centerCrop
import com.dot.gallery.feature_node.presentation.search.util.preProcess
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Collections
import java.util.EnumSet
import kotlin.math.exp
import kotlin.system.measureTimeMillis

class ImageClassifierHelper(private val context: Context) {
    private val ortEnv = OrtEnvironment.getEnvironment()
    private val labels: List<String> = loadLabels()

    fun setupClassificationSession(): OrtSession {
        val options = OrtSession.SessionOptions()

        try {
            // Check for available providers and add acceleration options
            if (OrtEnvironment.getAvailableProviders().contains(OrtProvider.NNAPI)) {
                options.addNnapi(EnumSet.of(NNAPIFlags.USE_FP16))
            } else if (OrtEnvironment.getAvailableProviders().contains(OrtProvider.QNN)) {
                val qnnOptions = mapOf(
                    "backend_type" to "htp",
                    "qnn_context_cache_enable" to "1",
                    "qnn_context_priority" to "high",
                    "enable_htp_fp16_precision" to "1"
                )
                options.addQnn(qnnOptions)
            }
        } catch (e: Exception) {
            // Fallback to CPU if NNAPI or QNN is not available
            e.printStackTrace()
        }

        // Load the MobileNet V3 Large Quantized model
        val modelBytes = context.resources.openRawResource(R.raw.mobile_ica_8bit).readBytes()
        return ortEnv.createSession(modelBytes, options)
    }

    @Suppress("UNCHECKED_CAST")
    fun classifyImage(session: OrtSession, bitmap: Bitmap): Pair<List<Category>, Long> {
        // Preprocess the image to match the model input size
        val processedBitmap = centerCrop(bitmap, INPUT_SIZE)
        val inputShape = longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong())
        val inputName = "conv_net_input" // Adjust if the ONNX model uses a different input name
        val imgData = preProcess(processedBitmap)

        val inputTensor = OnnxTensor.createTensor(ortEnv, imgData, inputShape)

        var inferenceTime: Long
        val categories: List<Category>
        inputTensor.use {
            // Measure inference time
            inferenceTime = measureTimeMillis {
                val output = session.run(Collections.singletonMap(inputName, inputTensor))
                output.use {
                    val outputValue = output[0].value

                    // Properly handle 2D tensor (float[][]) output
                    val rawOutput: FloatArray = parseOutputTensor(outputValue)

                    // Apply softmax to convert logits to probabilities
                    val probabilities = applySoftmax(rawOutput)

                    // Map the top predictions to categories
                    categories = probabilities.mapIndexed { index, score ->
                        Category(label = getLabel(index), score = score)
                    }.sortedByDescending { it.score } // Sort by score
                        .take(TOP_K_RESULTS) // Take top K results
                }
            }
        }

        return categories to inferenceTime
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseOutputTensor(outputValue: Any?): FloatArray {
        return when (outputValue) {
            is Array<*> -> {
                when {
                    outputValue.isArrayOf<FloatArray>() -> (outputValue as Array<FloatArray>)[0] // 2D tensor
                    outputValue.isArrayOf<Array<FloatArray>>() -> flatten3DTensor(outputValue as Array<Array<FloatArray>>) // 3D tensor
                    // Handle 4D tensor ([[[[F)
                    outputValue.isArrayOf<Array<*>>() && outputValue[0] is Array<*> &&
                            (outputValue[0] as Array<*>)[0] is Array<*> &&
                            ((outputValue[0] as Array<*>)[0] as Array<*>)[0] is FloatArray -> {
                        flatten4DTensor(outputValue as Array<Array<Array<FloatArray>>>)
                    }
                    else -> {
                        throw IllegalArgumentException("Unsupported tensor output structure: ${outputValue.javaClass.name}")
                    }
                }
            }
            else -> throw IllegalArgumentException("Unexpected tensor output type: ${outputValue?.javaClass?.name}")
        }
    }

    // Add this method to handle 3D tensors
    private fun flatten3DTensor(tensor: Array<Array<FloatArray>>): FloatArray {
        return tensor.flatMap { batch ->
            batch.flatMap { channel ->
                channel.toList()
            }
        }.toFloatArray()
    }

    // Update this method to handle actual 4D tensors
    private fun flatten4DTensor(tensor: Array<Array<Array<FloatArray>>>): FloatArray {
        return tensor.flatMap { batch ->
            batch.flatMap { channel ->
                channel.flatMap { row ->
                    row.toList()
                }
            }
        }.toFloatArray()
    }

    private fun applySoftmax(logits: FloatArray): FloatArray {
        val maxLogit = logits.maxOrNull() ?: 0f // For numerical stability
        val exps = logits.map { exp((it - maxLogit).toDouble()) } // Exponentiate logits
        val sumExps = exps.sum()
        return exps.map { (it / sumExps).toFloat() }.toFloatArray() // Normalize
    }

    private fun getLabel(index: Int): String {
        return if (index in labels.indices) labels[index] else "Unknown"
    }

    private fun loadLabels(): List<String> {
        val labelsInputStream = context.resources.openRawResource(R.raw.mobile_ica_labels)
        val labels = mutableListOf<String>()
        BufferedReader(InputStreamReader(labelsInputStream)).use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                labels.add(line!!.trim())
            }
        }
        return labels
    }

    companion object {
        private const val INPUT_SIZE = 224 // Adjust if your model uses a different input size
        private const val TOP_K_RESULTS = 5 // Number of top results to return
    }

    data class Category(val label: String, val score: Float)
}