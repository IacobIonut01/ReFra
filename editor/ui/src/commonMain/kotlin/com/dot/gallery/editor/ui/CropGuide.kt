package com.dot.gallery.editor.ui

import androidx.compose.runtime.Immutable
import com.dot.gallery.editor.core.geometry.GuideAxis
import com.dot.gallery.editor.core.geometry.Vec2

/**
 * One guided-upright guide as the crop tool keeps it: endpoints in
 * rigid-frame pixels (attached to the photo, not the screen), so the
 * guide follows the scene while the warp changes.
 */
@Immutable
internal data class CropGuide(val a: Vec2, val b: Vec2, val axis: GuideAxis)
