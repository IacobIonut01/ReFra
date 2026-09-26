/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.crop

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.dot.gallery.R
import com.dot.gallery.feature_node.presentation.util.printError
import com.dot.gallery.ui.theme.GalleryTheme
import com.smarttoolfactory.cropper.ImageCropper
import com.smarttoolfactory.cropper.model.AspectRatio
import com.smarttoolfactory.cropper.model.OutlineType
import com.smarttoolfactory.cropper.model.RectCropShape
import com.smarttoolfactory.cropper.settings.CropDefaults
import com.smarttoolfactory.cropper.settings.CropOutlineProperty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.max

/**
 * Handles the legacy `com.android.camera.action.CROP` intent so ReFra can serve as a crop provider
 * for callers such as the Settings AvatarPicker (Settings → System → Users → profile picture),
 * matching Gallery2/Google Photos (#997).
 *
 * Contract (subset used by AvatarPicker and most callers):
 * - source image: [android.content.Intent.getData]
 * - `aspectX` / `aspectY`: desired crop aspect ratio (fixes the overlay when both > 0)
 * - `outputX` / `outputY`: pixel size the result is scaled to
 * - [MediaStore.EXTRA_OUTPUT]: destination Uri the cropped image is written to
 * - `return-data`: when true, the cropped bitmap is returned inline in the result's `data` extra
 *
 * On success it writes to the output Uri (and/or returns the bitmap) and sets [RESULT_OK]; any
 * failure or cancellation returns [RESULT_CANCELED] so the caller falls back gracefully.
 */
class CropActivity : ComponentActivity() {

    private val sourceUri: Uri? by lazy { intent?.data }
    private val outputUri: Uri? by lazy { parcelableExtra(MediaStore.EXTRA_OUTPUT) }
    private val aspectX: Int by lazy { intent?.getIntExtra(EXTRA_ASPECT_X, 0) ?: 0 }
    private val aspectY: Int by lazy { intent?.getIntExtra(EXTRA_ASPECT_Y, 0) ?: 0 }
    private val outputX: Int by lazy { intent?.getIntExtra(EXTRA_OUTPUT_X, 0) ?: 0 }
    private val outputY: Int by lazy { intent?.getIntExtra(EXTRA_OUTPUT_Y, 0) ?: 0 }
    private val returnData: Boolean by lazy { intent?.getBooleanExtra(EXTRA_RETURN_DATA, false) ?: false }

    private val compressFormat: Bitmap.CompressFormat by lazy {
        if (intent?.getStringExtra(EXTRA_OUTPUT_FORMAT).equals("PNG", ignoreCase = true)) {
            Bitmap.CompressFormat.PNG
        } else {
            Bitmap.CompressFormat.JPEG
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (sourceUri == null) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        setContent {
            GalleryTheme(darkTheme = true) {
                LaunchedEffect(Unit) {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
                        navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
                    )
                }

                var source by remember { mutableStateOf<Bitmap?>(null) }
                var loadFailed by remember { mutableStateOf(false) }
                var isProcessing by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    val bitmap = withContext(Dispatchers.IO) { decodeSource() }
                    if (bitmap == null) loadFailed = true else source = bitmap
                }

                LaunchedEffect(loadFailed) {
                    if (loadFailed) {
                        printError("CropActivity: failed to decode source $sourceUri")
                        setResult(RESULT_CANCELED)
                        finish()
                    }
                }

                Scaffold(
                    containerColor = MaterialTheme.colorScheme.surface,
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(R.string.editor_crop)) },
                            navigationIcon = {
                                IconButton(
                                    enabled = !isProcessing,
                                    onClick = {
                                        setResult(RESULT_CANCELED)
                                        finish()
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = stringResource(R.string.cancel)
                                    )
                                }
                            },
                            actions = {
                                IconButton(
                                    enabled = source != null && !isProcessing,
                                    onClick = { isProcessing = true }
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Check,
                                        contentDescription = stringResource(R.string.editor_apply_crop)
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .background(MaterialTheme.colorScheme.surface),
                        contentAlignment = Alignment.Center
                    ) {
                        val current = source
                        if (current != null) {
                            CropContent(
                                source = current,
                                aspectX = aspectX,
                                aspectY = aspectY,
                                triggerCrop = isProcessing,
                                onCropRect = { rect -> processCrop(current, rect) }
                            )
                        }
                        if (isProcessing || current == null) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }

    /** Applies the crop, scales/writes the result, and finishes with [RESULT_OK] on success. */
    private fun processCrop(source: Bitmap, normalizedRect: RectF) {
        lifecycleScope.launch {
            val (ok, inlineResult) = withContext(Dispatchers.IO) {
                runCatching {
                    // createBitmap can return `source` itself when the crop covers the whole
                    // image; never recycle `source` since Compose still references it.
                    val cropped = cropBitmap(source, normalizedRect)
                    var result = cropped
                    if (outputX > 0 && outputY > 0) {
                        val scaled = Bitmap.createScaledBitmap(cropped, outputX, outputY, true)
                        if (scaled !== cropped && cropped !== source) cropped.recycle()
                        result = scaled
                    }

                    outputUri?.let { uri ->
                        contentResolver.openOutputStream(uri)?.use { out ->
                            result.compress(compressFormat, 100, out)
                        } ?: error("Unable to open output stream for $uri")
                    }

                    // Only echo the bitmap back when the caller asked for it AND left no output
                    // target, to stay under the Binder transaction size limit.
                    val inlineBitmap = if (returnData && outputUri == null) result else null
                    true to inlineBitmap
                }.getOrElse {
                    printError(TAG, "Failed to process crop", it)
                    false to null
                }
            }

            if (ok) {
                val resultIntent = Intent().apply {
                    outputUri?.let { setData(it) }
                    inlineResult?.let { putExtra(EXTRA_RETURN_DATA_KEY, it) }
                }
                setResult(RESULT_OK, resultIntent)
            } else {
                setResult(RESULT_CANCELED)
            }
            finish()
        }
    }

    private fun cropBitmap(source: Bitmap, r: RectF): Bitmap {
        val x = (r.left * source.width).toInt().coerceIn(0, source.width - 1)
        val y = (r.top * source.height).toInt().coerceIn(0, source.height - 1)
        val w = ((r.right - r.left) * source.width).toInt().coerceIn(1, source.width - x)
        val h = ((r.bottom - r.top) * source.height).toInt().coerceIn(1, source.height - y)
        return Bitmap.createBitmap(source, x, y, w, h)
    }

    /**
     * Decodes the source at a memory-safe resolution, applying EXIF orientation.
     * [ImageDecoder] applies orientation automatically for the formats it understands.
     */
    private fun decodeSource(): Bitmap? {
        val uri = sourceUri ?: return null
        return runCatching {
            val decoderSource = ImageDecoder.createSource(contentResolver, uri)
            ImageDecoder.decodeBitmap(decoderSource) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
                val longest = max(info.size.width, info.size.height)
                if (longest > MAX_SOURCE_DIMENSION) {
                    decoder.setTargetSampleSize(
                        ceil(longest.toFloat() / MAX_SOURCE_DIMENSION).toInt().coerceAtLeast(1)
                    )
                }
            }
        }.recoverCatching {
            // Fallback for formats ImageDecoder can't open (rare) — no orientation handling.
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
    }

    private fun parcelableExtra(key: String): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(key, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(key)
        }

    companion object {
        private const val TAG = "editor.crop"
        private const val MAX_SOURCE_DIMENSION = 4096

        private const val EXTRA_ASPECT_X = "aspectX"
        private const val EXTRA_ASPECT_Y = "aspectY"
        private const val EXTRA_OUTPUT_X = "outputX"
        private const val EXTRA_OUTPUT_Y = "outputY"
        private const val EXTRA_RETURN_DATA = "return-data"
        private const val EXTRA_RETURN_DATA_KEY = "data"
        private const val EXTRA_OUTPUT_FORMAT = "outputFormat"
    }
}

@Composable
private fun CropContent(
    source: Bitmap,
    aspectX: Int,
    aspectY: Int,
    triggerCrop: Boolean,
    onCropRect: (RectF) -> Unit
) {
    val cropDescription = stringResource(R.string.editor_crop)
    val previewBitmap = remember(source) {
        resizeForPreview(source, PREVIEW_MAX_DIMENSION).asImageBitmap()
    }
    val fixedAspect = aspectX > 0 && aspectY > 0
    val aspectRatio = remember(aspectX, aspectY) {
        if (fixedAspect) AspectRatio(aspectX.toFloat() / aspectY.toFloat()) else AspectRatio.Original
    }
    val properties = remember(aspectRatio, fixedAspect) {
        CropDefaults.properties(
            cropOutlineProperty = CropOutlineProperty(
                outlineType = OutlineType.RoundedRect,
                cropOutline = RectCropShape(id = 0, title = OutlineType.RoundedRect.name)
            ),
            aspectRatio = aspectRatio,
            overlayRatio = 1f,
            fixedAspectRatio = fixedAspect
        )
    }
    ImageCropper(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
        imageBitmap = previewBitmap,
        contentDescription = cropDescription,
        cropStyle = CropDefaults.style(
            handleColor = MaterialTheme.colorScheme.tertiary,
            strokeWidth = 1.dp
        ),
        cropProperties = properties,
        crop = triggerCrop,
        onCropStart = {},
        onCropSuccess = {},
        onCropRect = onCropRect
    )
}

private const val PREVIEW_MAX_DIMENSION = 2048

private fun resizeForPreview(bitmap: Bitmap, maxDimension: Int): Bitmap {
    val longest = max(bitmap.width, bitmap.height)
    if (longest <= maxDimension) return bitmap
    val scale = maxDimension.toFloat() / longest
    return Bitmap.createScaledBitmap(
        bitmap,
        (bitmap.width * scale).toInt().coerceAtLeast(1),
        (bitmap.height * scale).toInt().coerceAtLeast(1),
        true
    )
}
