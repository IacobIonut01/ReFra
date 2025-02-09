package com.dot.gallery.feature_node.domain.model.editor

import com.dot.gallery.feature_node.presentation.edit.adjustments.varfilter.VariableFilterTypes
import kotlinx.serialization.Serializable

@Serializable
sealed class EditorDestination {

    @Serializable
    data object Editor : EditorDestination()

    @Serializable
    data object Crop : EditorDestination()

    @Serializable
    data object Adjust : EditorDestination()

        @Serializable
        data class AdjustDetail(val adjustment: VariableFilterTypes) : EditorDestination()

    @Serializable
    data object Filters : EditorDestination()

    @Serializable
    data object Markup : EditorDestination()

        @Serializable
        data object ExternalEditor : EditorDestination()

}