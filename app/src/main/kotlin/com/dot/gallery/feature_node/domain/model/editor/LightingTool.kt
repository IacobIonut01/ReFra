package com.dot.gallery.feature_node.domain.model.editor

import androidx.annotation.Keep
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Brightness5
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.FilterDrama
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Tonality
import androidx.compose.material.icons.outlined.Vignette
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.dot.gallery.R
import kotlinx.serialization.Serializable

@Keep
@Serializable
enum class LightingTool {
    Brightness,
    Tone,
    Contrast,
    BlackPoint,
    WhitePoint,
    Highlights,
    Shadows,
    Vignette;

    @get:Composable
    val translatedName: String
        get() = when (this) {
            Brightness -> stringResource(R.string.tool_brightness)
            Tone -> stringResource(R.string.tool_tone)
            Contrast -> stringResource(R.string.tool_contrast)
            BlackPoint -> stringResource(R.string.tool_black_point)
            WhitePoint -> stringResource(R.string.tool_white_point)
            Highlights -> stringResource(R.string.tool_highlights)
            Shadows -> stringResource(R.string.tool_shadows)
            Vignette -> stringResource(R.string.tool_vignette)
        }

    val icon: ImageVector
        get() = when (this) {
            Brightness -> Icons.Outlined.Brightness5
            Tone -> Icons.Outlined.Tonality
            Contrast -> Icons.Outlined.Contrast
            BlackPoint -> Icons.Outlined.RadioButtonUnchecked
            WhitePoint -> Icons.Outlined.Circle
            Highlights -> Icons.Outlined.Layers
            Shadows -> Icons.Outlined.FilterDrama
            Vignette -> Icons.Outlined.Vignette
        }

    val allowNegative: Boolean
        get() = this != Vignette

    val minValue: Float
        get() = if (allowNegative) -1f else 0f

    val maxValue: Float
        get() = 1f

    val defaultValue: Float
        get() = 0f
}
