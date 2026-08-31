package com.dot.gallery.feature_node.presentation.edit

import androidx.compose.ui.graphics.Color
import com.dot.gallery.feature_node.presentation.edit.adjustments.MatrixAdjustment
import com.dot.gallery.feature_node.presentation.edit.adjustments.varfilter.Brightness
import com.dot.gallery.feature_node.presentation.edit.adjustments.varfilter.Contrast
import com.dot.gallery.feature_node.presentation.edit.components.adjustment.shouldDispatchAdjustmentPreview
import com.dot.gallery.feature_node.presentation.edit.components.editor.shouldUseAsyncSourceRenderer
import com.dot.gallery.feature_node.presentation.edit.components.markup.markupPresetColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorPreviewPolicyTest {
    @Test
    fun sourceSubsamplingIsUsedOnlyForPristineNonPreviewScreens() {
        assertTrue(
            shouldShowSourceSubsampling(
                hasAppliedAdjustments = false,
                usesProxyPreview = false,
            )
        )
        assertFalse(
            shouldShowSourceSubsampling(
                hasAppliedAdjustments = false,
                usesProxyPreview = true,
            )
        )
        assertFalse(
            shouldShowSourceSubsampling(
                hasAppliedAdjustments = true,
                usesProxyPreview = false,
            )
        )
    }

    @Test
    fun asyncRendererIsReservedForSubsampledSources() {
        assertTrue(shouldUseAsyncSourceRenderer(showSourceSubsampling = true, hasSourceUri = true))
        assertFalse(shouldUseAsyncSourceRenderer(showSourceSubsampling = false, hasSourceUri = true))
        assertFalse(shouldUseAsyncSourceRenderer(showSourceSubsampling = true, hasSourceUri = false))
    }

    @Test
    fun replacingEarlierAdjustmentPreservesRecipePosition() {
        val contrast = Contrast(0.4f)
        val replacement = Brightness(0.6f)

        assertEquals(
            listOf(replacement, contrast),
            updatedEditorRecipe(listOf(Brightness(0.2f), contrast), replacement)
        )
        assertEquals(
            listOf(contrast),
            updatedEditorRecipe(listOf(Brightness(0.2f), contrast), Brightness())
        )

        val firstFilter = MatrixAdjustment(FloatArray(20), "First")
        val replacementFilter = MatrixAdjustment(FloatArray(20) { 1f }, "Replacement")
        assertEquals(
            listOf(replacementFilter, contrast),
            updatedEditorRecipe(listOf(firstFilter, contrast), replacementFilter)
        )
    }

    @Test
    fun markupPresetColorsPreserveBrushAlpha() {
        assertEquals(0.4f, markupPresetColor(Color.Blue, Color.Red.copy(alpha = 0.4f)).alpha, 0f)
    }

    @Test
    fun scrubberDispatchesOnlyActualValueChanges() {
        assertFalse(shouldDispatchAdjustmentPreview(currentValue = 0f, newValue = 0f))
        assertFalse(shouldDispatchAdjustmentPreview(currentValue = 0.5f, newValue = 0.50001f))
        assertTrue(shouldDispatchAdjustmentPreview(currentValue = 0f, newValue = 0.01f))
    }
}
