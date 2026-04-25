package com.dot.gallery.feature_node.presentation.edit.utils

import androidx.annotation.Keep
import com.dot.gallery.feature_node.domain.model.editor.Adjustment
import com.dot.gallery.feature_node.presentation.edit.adjustments.varfilter.VariableFilterTypes

@Keep
fun List<Adjustment>.isApplied(variableFilterTypes: VariableFilterTypes): Boolean {
    return any { it.name.equals(variableFilterTypes.name, ignoreCase = true) }
}