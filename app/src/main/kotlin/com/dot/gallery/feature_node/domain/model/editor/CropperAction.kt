package com.dot.gallery.feature_node.domain.model.editor;

import androidx.annotation.Keep;
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.RotateRight
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Flip
import androidx.compose.ui.graphics.vector.ImageVector;
import com.dot.gallery.feature_node.presentation.edit.adjustments.Flip
import com.dot.gallery.feature_node.presentation.edit.adjustments.Rotate90CW

@Keep
enum class CropperAction {
    ROTATE_90, APPLY_CROP, FLIP;

    val icon: ImageVector
        get() = when (this) {
            APPLY_CROP -> Icons.Outlined.Crop
            ROTATE_90 -> Icons.AutoMirrored.Outlined.RotateRight
            FLIP -> Icons.Outlined.Flip
        }


    fun asAdjustment(): Adjustment? {
        return when (this) {
            ROTATE_90 -> Rotate90CW(90f)
            FLIP -> Flip(horizontal = true)
            else -> null
        }
    }
}