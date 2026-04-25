package com.dot.gallery.feature_node.domain.model.editor

import androidx.annotation.Keep
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.InvertColors
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.dot.gallery.R
import kotlinx.serialization.Serializable

@Keep
@Serializable
enum class ColourTool {
    Saturation,
    Warmth,
    Tint,
    SkinTone,
    BlueTone;

    @get:Composable
    val translatedName: String
        get() = when (this) {
            Saturation -> stringResource(R.string.tool_saturation)
            Warmth -> stringResource(R.string.tool_warmth)
            Tint -> stringResource(R.string.tool_tint)
            SkinTone -> stringResource(R.string.tool_skin_tone)
            BlueTone -> stringResource(R.string.tool_blue_tone)
        }

    val icon: ImageVector
        get() = when (this) {
            Saturation -> Icons.Outlined.WaterDrop
            Warmth -> Icons.Outlined.Thermostat
            Tint -> Icons.Outlined.Palette
            SkinTone -> Icons.Outlined.InvertColors
            BlueTone -> Icons.Outlined.Waves
        }

    val minValue: Float get() = -1f
    val maxValue: Float get() = 1f
    val defaultValue: Float get() = 0f
}
