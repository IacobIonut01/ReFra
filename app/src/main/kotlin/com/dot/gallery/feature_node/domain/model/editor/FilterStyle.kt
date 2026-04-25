package com.dot.gallery.feature_node.domain.model.editor

import androidx.annotation.Keep
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.dot.gallery.R
import kotlinx.serialization.Serializable

@Keep
@Serializable
enum class FilterStyle {
    Vivid,
    Luminous,
    Radiant,
    Ember,
    Airy;

    @get:Composable
    val translatedName: String
        get() = when (this) {
            Vivid -> stringResource(R.string.filter_style_vivid)
            Luminous -> stringResource(R.string.filter_style_luminous)
            Radiant -> stringResource(R.string.filter_style_radiant)
            Ember -> stringResource(R.string.filter_style_ember)
            Airy -> stringResource(R.string.filter_style_airy)
        }

    val saturationDelta: Float
        get() = when (this) {
            Vivid -> 0.15f
            Luminous -> 0f
            Radiant -> 0.10f
            Ember -> 0f
            Airy -> -0.05f
        }

    val brightnessDelta: Float
        get() = when (this) {
            Vivid -> 0f
            Luminous -> 0.10f
            Radiant -> 0f
            Ember -> 0f
            Airy -> 0.10f
        }

    val contrastDelta: Float
        get() = when (this) {
            Vivid -> 0.05f
            Luminous -> 0f
            Radiant -> 0f
            Ember -> 0f
            Airy -> -0.10f
        }

    val warmthDelta: Float
        get() = when (this) {
            Vivid -> 0f
            Luminous -> 0f
            Radiant -> 0.10f
            Ember -> 0.15f
            Airy -> 0f
        }

    val highlightsDelta: Float
        get() = when (this) {
            Vivid -> 0f
            Luminous -> 0.05f
            Radiant -> 0f
            Ember -> -0.05f
            Airy -> 0f
        }
}
