package com.dot.gallery.feature_node.domain.model.editor

import com.dot.gallery.feature_node.presentation.edit.adjustments.varfilter.VariableFilterTypes
import kotlinx.serialization.Serializable

@Serializable
sealed class EditorDestination {

    @Serializable
    data object Editor : EditorDestination()

    @Serializable
    data object Markup : EditorDestination()

    @Serializable
    data object MarkupDraw : EditorDestination()

    @Serializable
    data object Filters : EditorDestination()

    @Serializable
    data object Lighting : EditorDestination()

    @Serializable
    data object Colour : EditorDestination()

    @Serializable
    data object More : EditorDestination()

    @Serializable
    data class AdjustDetail(val adjustment: VariableFilterTypes) : EditorDestination()

}