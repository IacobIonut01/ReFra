/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.edit

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.smarttoolfactory.cropper.crop
import com.smarttoolfactory.cropper.model.AspectRatio
import com.smarttoolfactory.cropper.model.OutlineType
import com.smarttoolfactory.cropper.model.RectCropShape
import com.smarttoolfactory.cropper.settings.CropDefaults
import com.smarttoolfactory.cropper.settings.CropOutlineProperty
import com.smarttoolfactory.cropper.state.CropState
import com.smarttoolfactory.cropper.state.rememberCropState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression coverage for #1373 — a fitted image in the dynamic editor cropper could be
 * dragged out of alignment before zooming. Pan is read while the finger is still down so a
 * release animation cannot hide the displacement.
 */
@MediumTest
@RunWith(AndroidJUnit4::class)
class EditorImagePanBoundsTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun fittedImageStaysCenteredDuringDrag() {
        lateinit var state: CropState
        val imageSize = IntSize(400, 200)

        rule.setContent {
            val side = with(LocalDensity.current) { 240.dp.roundToPx() }
            val bitmap = remember {
                Bitmap.createBitmap(imageSize.width, imageSize.height, Bitmap.Config.ARGB_8888)
                    .asImageBitmap()
            }
            state = rememberCropState(
                imageSize = imageSize,
                containerSize = IntSize(side, side),
                drawAreaSize = IntSize(side, side / 2),
                cropProperties = CropDefaults.properties(
                    cropOutlineProperty = CropOutlineProperty(
                        outlineType = OutlineType.RoundedRect,
                        cropOutline = RectCropShape(id = 0, title = OutlineType.RoundedRect.name)
                    ),
                    aspectRatio = AspectRatio.Original,
                    overlayRatio = 1f,
                    fixedAspectRatio = false
                )
            )
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(240.dp)
                    .testTag("cropper")
                    .crop(imageSize, cropState = state)
            )
        }

        rule.waitForIdle()
        val cropper = rule.onNodeWithTag("cropper")
        // Letterbox of a landscape image in a square viewport, away from crop handles.
        cropper.performTouchInput { down(Offset(width * .5f, height * .05f)) }
        cropper.performTouchInput { moveBy(Offset(width * .15f, height * .15f)) }
        rule.runOnIdle {
            assertEquals(0f, state.pan.x, .001f)
            assertEquals(0f, state.pan.y, .001f)
        }
        cropper.performTouchInput { up() }
        rule.runOnIdle {
            assertEquals(0f, state.pan.x, .001f)
            assertEquals(0f, state.pan.y, .001f)
        }
    }
}
