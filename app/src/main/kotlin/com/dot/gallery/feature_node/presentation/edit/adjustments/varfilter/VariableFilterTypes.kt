package com.dot.gallery.feature_node.presentation.edit.adjustments.varfilter

import androidx.annotation.Keep
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Brightness4
import androidx.compose.material.icons.outlined.Brightness5
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.Details
import androidx.compose.material.icons.outlined.FilterDrama
import androidx.compose.material.icons.outlined.InvertColors
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Rotate90DegreesCcw
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.Tonality
import androidx.compose.material.icons.outlined.Vignette
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.ui.graphics.vector.ImageVector
import com.dot.gallery.feature_node.domain.model.editor.VariableFilter
import kotlinx.serialization.Serializable

@Keep
@Serializable
enum class VariableFilterTypes {
    // Legacy
    Brightness, Contrast, Saturation, Rotate,
    // Lighting
    Tone, BlackPoint, WhitePoint, Highlights, Shadows, Vignette,
    // Colour
    Warmth, Tint, SkinTone, BlueTone,
    // Actions
    Pop, Sharpen, Denoise;

    fun createFilter(value: Float): VariableFilter =
        when (this) {
            Brightness -> Brightness(value)
            Contrast -> Contrast(value)
            Saturation -> Saturation(value)
            Rotate -> Rotate(value)
            Tone -> com.dot.gallery.feature_node.presentation.edit.adjustments.varfilter.Tone(value)
            BlackPoint -> com.dot.gallery.feature_node.presentation.edit.adjustments.varfilter.BlackPoint(value)
            WhitePoint -> com.dot.gallery.feature_node.presentation.edit.adjustments.varfilter.WhitePoint(value)
            Highlights -> com.dot.gallery.feature_node.presentation.edit.adjustments.varfilter.Highlights(value)
            Shadows -> com.dot.gallery.feature_node.presentation.edit.adjustments.varfilter.Shadows(value)
            Vignette -> com.dot.gallery.feature_node.presentation.edit.adjustments.varfilter.Vignette(value)
            Warmth -> com.dot.gallery.feature_node.presentation.edit.adjustments.varfilter.Warmth(value)
            Tint -> com.dot.gallery.feature_node.presentation.edit.adjustments.varfilter.Tint(value)
            SkinTone -> com.dot.gallery.feature_node.presentation.edit.adjustments.varfilter.SkinTone(value)
            BlueTone -> com.dot.gallery.feature_node.presentation.edit.adjustments.varfilter.BlueTone(value)
            Pop -> com.dot.gallery.feature_node.presentation.edit.adjustments.varfilter.Pop(value)
            Sharpen -> Sharpness(value)
            Denoise -> com.dot.gallery.feature_node.presentation.edit.adjustments.varfilter.Denoise(value)
        }

    fun createDefaultFilter(): VariableFilter =
        when (this) {
            Brightness -> Brightness()
            Contrast -> Contrast()
            Saturation -> Saturation()
            Rotate -> Rotate()
            Tone -> com.dot.gallery.feature_node.presentation.edit.adjustments.varfilter.Tone()
            BlackPoint -> com.dot.gallery.feature_node.presentation.edit.adjustments.varfilter.BlackPoint()
            WhitePoint -> com.dot.gallery.feature_node.presentation.edit.adjustments.varfilter.WhitePoint()
            Highlights -> com.dot.gallery.feature_node.presentation.edit.adjustments.varfilter.Highlights()
            Shadows -> com.dot.gallery.feature_node.presentation.edit.adjustments.varfilter.Shadows()
            Vignette -> com.dot.gallery.feature_node.presentation.edit.adjustments.varfilter.Vignette()
            Warmth -> com.dot.gallery.feature_node.presentation.edit.adjustments.varfilter.Warmth()
            Tint -> com.dot.gallery.feature_node.presentation.edit.adjustments.varfilter.Tint()
            SkinTone -> com.dot.gallery.feature_node.presentation.edit.adjustments.varfilter.SkinTone()
            BlueTone -> com.dot.gallery.feature_node.presentation.edit.adjustments.varfilter.BlueTone()
            Pop -> com.dot.gallery.feature_node.presentation.edit.adjustments.varfilter.Pop()
            Sharpen -> Sharpness()
            Denoise -> com.dot.gallery.feature_node.presentation.edit.adjustments.varfilter.Denoise()
        }

    val icon: ImageVector get() =
        when (this) {
            Brightness -> Icons.Outlined.Brightness5
            Contrast -> Icons.Outlined.Contrast
            Saturation -> Icons.Outlined.WaterDrop
            Rotate -> Icons.Outlined.Rotate90DegreesCcw
            Tone -> Icons.Outlined.Tonality
            BlackPoint -> Icons.Outlined.RadioButtonUnchecked
            WhitePoint -> Icons.Outlined.Circle
            Highlights -> Icons.Outlined.Layers
            Shadows -> Icons.Outlined.FilterDrama
            Vignette -> Icons.Outlined.Vignette
            Warmth -> Icons.Outlined.Thermostat
            Tint -> Icons.Outlined.Palette
            SkinTone -> Icons.Outlined.InvertColors
            BlueTone -> Icons.Outlined.Waves
            Pop -> Icons.Outlined.Contrast
            Sharpen -> Icons.Outlined.Details
            Denoise -> Icons.Outlined.Brightness4
        }
}
