/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.edit

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.smarttoolfactory.cropper.ImageCropper
import com.smarttoolfactory.cropper.model.AspectRatio
import com.smarttoolfactory.cropper.model.OutlineType
import com.smarttoolfactory.cropper.model.RectCropShape
import com.smarttoolfactory.cropper.settings.CropDefaults
import com.smarttoolfactory.cropper.settings.CropOutlineProperty
import java.util.Collections
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * Regression coverage for #1278 — picking a preset ratio swaps the cropper through
 * `AnimatedContent`, which briefly composes the old- and new-ratio subtrees together.
 * The shared `crop` flag must be consumed only by the subtree rendering the current
 * target ratio, and the reported rect must be the settled post-init geometry — never
 * the outgoing subtree's stale (often full-frame) rect.
 */
@MediumTest
@RunWith(AndroidJUnit4::class)
class CropRatioSwapCommitTest {

    @get:Rule
    val rule = createComposeRule()

    private fun fullFrame(rect: RectF) =
        rect.width() >= 0.999f && rect.height() >= 0.999f

    @Test
    fun cropDuringRatioTransitionFiresOnceFromCurrentSubtree() {
        val captured = Collections.synchronizedList(mutableListOf<Pair<AspectRatio, RectF>>())
        var ratio by mutableStateOf(AspectRatio.Original)
        var cropping by mutableStateOf(false)

        rule.setContent {
            val bitmap = remember {
                Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888).asImageBitmap()
            }
            Box(Modifier.size(240.dp)) {
                AnimatedContent(
                    targetState = ratio,
                    transitionSpec = {
                        fadeIn(tween(100)) togetherWith fadeOut(tween(100))
                    },
                    label = "cropper",
                ) { targetRatio ->
                    val props = remember(targetRatio) {
                        CropDefaults.properties(
                            cropOutlineProperty = CropOutlineProperty(
                                outlineType = OutlineType.RoundedRect,
                                cropOutline = RectCropShape(
                                    id = 0,
                                    title = OutlineType.RoundedRect.name
                                )
                            ),
                            aspectRatio = targetRatio,
                            overlayRatio = 1f,
                            fixedAspectRatio = targetRatio != AspectRatio.Original
                        )
                    }
                    ImageCropper(
                        imageBitmap = bitmap,
                        cropProperties = props,
                        // The gate under test: only the subtree rendering the current
                        // target ratio may consume the shared crop flag.
                        crop = cropping && targetRatio == ratio,
                        onCropStart = {},
                        onCropSuccess = {},
                        onCropRect = { rect -> captured.add(targetRatio to rect) },
                    )
                }
            }
        }

        rule.waitForIdle()
        // Flip the ratio and raise the crop flag in the same snapshot — the commit
        // lands while both cropper subtrees are still composed mid-crossfade.
        rule.runOnUiThread {
            ratio = AspectRatio(4f / 3f)
            cropping = true
        }

        rule.waitUntil("crop callback delivered", 5_000) { captured.isNotEmpty() }
        rule.waitForIdle()

        assertEquals("expected exactly one crop commit", 1, captured.size)
        val (firedRatio, rect) = captured.single()
        assertEquals(AspectRatio(4f / 3f), firedRatio)
        assertFalse(
            "outgoing subtree committed its stale full-frame rect: $rect",
            fullFrame(rect)
        )
        // 2:1 source cropped to 4:3 keeps full height and ~2/3 of the width.
        assertEquals(1f, rect.height(), 0.001f)
        assertTrue(
            "expected ~2:3 width for a 4:3 crop of a 2:1 bitmap, got $rect",
            abs(rect.width() - 2f / 3f) < 0.05f
        )
    }

    @Test
    fun settledFreeformCropStillFiresFullFrame() {
        val captured = Collections.synchronizedList(mutableListOf<RectF>())
        var cropping by mutableStateOf(false)

        rule.setContent {
            val bitmap = remember {
                Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888).asImageBitmap()
            }
            Box(Modifier.size(240.dp)) {
                ImageCropper(
                    imageBitmap = bitmap,
                    cropProperties = CropDefaults.properties(
                        cropOutlineProperty = CropOutlineProperty(
                            outlineType = OutlineType.RoundedRect,
                            cropOutline = RectCropShape(
                                id = 0,
                                title = OutlineType.RoundedRect.name
                            )
                        ),
                        aspectRatio = AspectRatio.Original,
                        overlayRatio = 1f,
                        fixedAspectRatio = false
                    ),
                    crop = cropping,
                    onCropStart = {},
                    onCropSuccess = {},
                    onCropRect = { captured.add(it) },
                )
            }
        }

        rule.waitForIdle()
        rule.runOnUiThread { cropping = true }
        rule.waitUntil("crop callback delivered", 5_000) { captured.isNotEmpty() }

        assertEquals(1, captured.size)
        // Freeform at rest covers the whole image — the editor treats it as a no-op.
        assertTrue(fullFrame(captured.single()))
    }
}
