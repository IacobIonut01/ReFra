/*
 * Copyright 2022 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *             http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dot.gallery.feature_node.presentation.classifier;

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import androidx.compose.ui.util.fastFlatMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.nnapi.NnApiDelegate
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.label.Category
import org.tensorflow.lite.task.core.BaseOptions
import org.tensorflow.lite.task.core.vision.ImageProcessingOptions
import org.tensorflow.lite.task.vision.classifier.ImageClassifier


class ImageClassifierHelper(
    private var threshold: Float = 0.8f,
    private var numThreads: Int = 4,
    private var maxResults: Int = 1,
    private val modelName: String = "mobile_ica_8bit_with_metadata.tflite",
    val context: Context,
    val imageClassifierListener: ClassifierListener?
) {
    private var imageClassifier: ImageClassifier? = null

    init {
        setupImageClassifier()
    }

    fun clearImageClassifier() {
        imageClassifier = null
    }

    private fun setupImageClassifier() {

        val optionsBuilder = ImageClassifier.ImageClassifierOptions.builder()
            .setScoreThreshold(threshold)
            .setMaxResults(maxResults)

        val baseOptionsBuilder = BaseOptions.builder().setNumThreads(numThreads)

        val nNAPISupported = try {
            NnApiDelegate()
            true
        } catch (e: Exception) {
            false
        }

        if (nNAPISupported) {
            Log.i(TAG, "Using NNAPI delegate")
            baseOptionsBuilder.useNnapi()
        } else {
            if (CompatibilityList().isDelegateSupportedOnThisDevice) {
                Log.i(TAG, "Using GPU delegate")
                baseOptionsBuilder.useGpu()
            } else {
                Log.i(TAG, "Falling back to CPU delegate")
                // Falling back to CPU
            }
        }

        optionsBuilder.setBaseOptions(baseOptionsBuilder.build())

        try {
            imageClassifier =
                ImageClassifier.createFromFileAndOptions(context, modelName, optionsBuilder.build())
        } catch (e: IllegalStateException) {
            imageClassifierListener?.onError(
                "Image classifier failed to initialize. See error logs for details"
            )
            Log.e(TAG, "TFLite failed to load model with error: " + e.message)
        }
    }

    suspend fun classify(image: Bitmap, rotation: Int = 0): Pair<List<Category>, Long>? = runCatching {
        withContext(Dispatchers.IO) {
            var compatibleImage = image
            if (imageClassifier == null) {
                setupImageClassifier()
            }

            // Inference time is the difference between the system time at the start and finish of the
            // process
            var inferenceTime = SystemClock.uptimeMillis()

            // Create preprocessor for the image.
            // See https://www.tensorflow.org/lite/inference_with_metadata/
            //            lite_support#imageprocessor_architecture
            val imageProcessor = ImageProcessor.Builder().build()

            // Check compatible bitmap config
            if (image.config != Bitmap.Config.ARGB_8888) {
                // Create a new bitmap with ARGB_8888 config
                compatibleImage = image.copy(Bitmap.Config.ARGB_8888, false)
            }

            // Preprocess the image and convert it into a TensorImage for classification.
            val tensorImage = imageProcessor.process(TensorImage.fromBitmap(compatibleImage))

            val imageProcessingOptions = ImageProcessingOptions.builder()
                .setOrientation(getOrientationFromRotation(rotation))
                .build()

            val results = imageClassifier?.classify(tensorImage, imageProcessingOptions)
            inferenceTime = SystemClock.uptimeMillis() - inferenceTime
            return@withContext (results ?: emptyList()).fastFlatMap { it.categories } to inferenceTime
        }
    }.getOrNull()

    // Receive the device rotation (Surface.x values range from 0->3) and return EXIF orientation
    // http://jpegclub.org/exif_orientation.html
    private fun getOrientationFromRotation(rotation: Int) : ImageProcessingOptions.Orientation {
        return when (rotation) {
            Surface.ROTATION_270 ->
                ImageProcessingOptions.Orientation.BOTTOM_RIGHT

            Surface.ROTATION_180 ->
                ImageProcessingOptions.Orientation.RIGHT_BOTTOM

            Surface.ROTATION_90 ->
                ImageProcessingOptions.Orientation.TOP_LEFT

            else ->
                ImageProcessingOptions.Orientation.RIGHT_TOP
        }
    }

    interface ClassifierListener {
        fun onError(error: String)
    }

    companion object {
        private const val TAG = "ImageClassifierHelper"
    }
}