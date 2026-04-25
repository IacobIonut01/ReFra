package com.dot.gallery.feature_node.domain.model.editor

import android.os.Parcelable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.dot.gallery.R
import com.dot.gallery.ui.core.icons.InkHighlighter
import com.dot.gallery.ui.core.icons.InkMarker
import com.dot.gallery.ui.core.icons.Ink_Eraser
import com.dot.gallery.ui.core.icons.Stylus
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import com.dot.gallery.ui.core.Icons as DotIcons

@Serializable
@Parcelize
enum class MarkupItems : Parcelable {
    Stylus,
    Highlighter,
    Marker,
    Text,
    Eraser,
    Pan;

    @get:Composable
    val translatedName get() = when (this) {
        Stylus -> stringResource(R.string.type_stylus)
        Highlighter -> stringResource(R.string.type_highlighter)
        Marker -> stringResource(R.string.type_marker)
        Text -> stringResource(R.string.type_text)
        Eraser -> stringResource(R.string.type_erase)
        Pan -> stringResource(R.string.type_pan)
    }

    @IgnoredOnParcel
    val icon: ImageVector
        get() = when (this) {
            Stylus -> DotIcons.Stylus
            Highlighter -> DotIcons.InkHighlighter
            Marker -> DotIcons.InkMarker
            Text -> Icons.Outlined.TextFields
            Eraser -> DotIcons.Ink_Eraser
            Pan -> Icons.Outlined.PanTool
        }
}