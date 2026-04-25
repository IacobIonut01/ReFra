package com.dot.gallery.feature_node.domain.model.editor;

import android.os.Parcelable
import androidx.annotation.Keep
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Filter
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.dot.gallery.R
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Keep
@Serializable
@Parcelize
enum class EditorItems : Parcelable {
    Lighting,
    Filters,
    Markup,
    Colour,
    More;

    @get:Composable
    val translatedName : String
        get() = when (this) {
            Lighting -> stringResource(R.string.editor_lighting)
            Filters -> stringResource(R.string.filters)
            Markup -> stringResource(R.string.markup)
            Colour -> stringResource(R.string.editor_colour)
            More -> stringResource(R.string.editor_more)
        }

    @IgnoredOnParcel
    val icon: ImageVector
        get() = when (this) {
            Lighting -> Icons.Outlined.WbSunny
            Filters -> Icons.Outlined.Filter
            Markup -> Icons.Outlined.Draw
            Colour -> Icons.Outlined.Palette
            More -> Icons.Outlined.MoreHoriz
        }
}