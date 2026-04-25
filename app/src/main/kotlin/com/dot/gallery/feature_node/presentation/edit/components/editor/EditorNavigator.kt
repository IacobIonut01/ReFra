package com.dot.gallery.feature_node.presentation.edit.components.editor

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.dot.gallery.core.Constants.Animation.navigateInAnimation
import com.dot.gallery.core.Constants.Animation.navigateUpAnimation
import com.dot.gallery.feature_node.domain.model.editor.Adjustment
import com.dot.gallery.feature_node.domain.model.editor.DrawMode
import com.dot.gallery.feature_node.domain.model.editor.DrawType
import com.dot.gallery.feature_node.domain.model.editor.EditorDestination
import com.dot.gallery.feature_node.domain.model.editor.EditorItems
import com.dot.gallery.feature_node.domain.model.editor.ImageFilter
import com.dot.gallery.feature_node.domain.model.editor.MarkupItems
import com.dot.gallery.feature_node.domain.model.editor.PathProperties
import com.dot.gallery.feature_node.presentation.edit.adjustments.varfilter.VariableFilterTypes
import com.dot.gallery.feature_node.presentation.edit.components.adjustment.AdjustScrubber
import com.dot.gallery.feature_node.presentation.edit.components.colour.ColourSection
import com.dot.gallery.feature_node.presentation.edit.components.colour.toVariableFilterType
import com.dot.gallery.feature_node.presentation.edit.components.filters.FiltersSelector
import com.dot.gallery.feature_node.presentation.edit.components.lighting.LightingSection
import com.dot.gallery.feature_node.presentation.edit.components.lighting.toVariableFilterType
import com.dot.gallery.feature_node.presentation.edit.components.markup.MarkupSelector
import com.dot.gallery.feature_node.presentation.edit.components.markup.MarkupToolSelector
import com.dot.gallery.feature_node.domain.model.editor.TextAnnotation
import kotlin.math.roundToInt

@Composable
fun EditorNavigator(
    modifier: Modifier = Modifier,
    navController: NavHostController,
    appliedAdjustments: List<Adjustment>,
    targetImage: Bitmap?,
    targetUri: Uri?,
    onAdjustItemLongClick: (VariableFilterTypes) -> Unit = {},
    onAdjustmentChange: (Adjustment) -> Unit = {},
    onAdjustmentPreview: (Adjustment) -> Unit = {},
    onToggleFilter: (ImageFilter) -> Unit = {},
    drawMode: DrawMode,
    setDrawMode: (DrawMode) -> Unit,
    drawType: DrawType,
    setDrawType: (DrawType) -> Unit,
    currentPathProperty: PathProperties,
    setCurrentPathProperty: (PathProperties) -> Unit,
    filterIntensity: Float = 1f,
    onFilterIntensityChange: (Float) -> Unit = {},
    activeFilterName: String? = null,
    isSupportingPanel: Boolean = false,
    onRequestTextInput: () -> Unit = {},
    textAnnotations: List<TextAnnotation> = emptyList(),
    onTextAnnotationsChange: (List<TextAnnotation>) -> Unit = {},
    selectedTextIndex: Int = -1,
) {

    NavHost(
        modifier = modifier,
        navController = navController,
        startDestination = EditorDestination.Lighting,
        enterTransition = { navigateInAnimation },
        exitTransition = { navigateUpAnimation },
        popEnterTransition = { navigateInAnimation },
        popExitTransition = { navigateUpAnimation }
    ) {
        composable<EditorDestination.Editor> {
            if (isSupportingPanel) {
                EditorSelector(
                    isSupportingPanel = true,
                    onItemClick = { editorItem ->
                        val dest = when (editorItem) {
                            EditorItems.Lighting -> EditorDestination.Lighting
                            EditorItems.Filters -> EditorDestination.Filters
                            EditorItems.Markup -> EditorDestination.Markup
                            EditorItems.Colour -> EditorDestination.Colour
                            EditorItems.More -> EditorDestination.More
                        }
                        navController.navigate(dest)
                    }
                )
            }
            // Phone layout: tab bar is outside NavHost, so Editor destination is empty
        }

        // Lighting tab
        composable<EditorDestination.Lighting> {
            LightingSection(
                appliedAdjustments = appliedAdjustments,
                isSupportingPanel = isSupportingPanel,
                onItemClick = { tool ->
                    navController.navigate(
                        EditorDestination.AdjustDetail(tool.toVariableFilterType())
                    )
                },
                onLongItemClick = { tool ->
                    onAdjustItemLongClick(tool.toVariableFilterType())
                }
            )
        }

        // Colour tab
        composable<EditorDestination.Colour> {
            ColourSection(
                appliedAdjustments = appliedAdjustments,
                isSupportingPanel = isSupportingPanel,
                onItemClick = { tool ->
                    navController.navigate(
                        EditorDestination.AdjustDetail(tool.toVariableFilterType())
                    )
                },
                onLongItemClick = { tool ->
                    onAdjustItemLongClick(tool.toVariableFilterType())
                }
            )
        }

        // More tab → directly show external editors
        composable<EditorDestination.More> {
            ExternalEditor(
                currentUri = targetUri,
                isSupportingPanel = isSupportingPanel
            )
        }

        // Shared detail scrubber for all variable filters
        composable<EditorDestination.AdjustDetail> {
            val params = it.toRoute<EditorDestination.AdjustDetail>()
            val isRotate = params.adjustment == VariableFilterTypes.Rotate

            AdjustScrubber(
                modifier = Modifier.padding(bottom = 16.dp),
                adjustment = params.adjustment,
                displayValue = { value ->
                    (value * if (isRotate) 1f else 100f).roundToInt()
                        .toString() + if (isRotate) "°" else ""
                },
                onAdjustmentChange = onAdjustmentChange,
                onAdjustmentPreview = onAdjustmentPreview,
                appliedAdjustments = appliedAdjustments,
                isSupportingPanel = isSupportingPanel
            )
        }

        composable<EditorDestination.Filters> {
            FiltersSelector(
                bitmap = targetImage!!,
                onClick = onToggleFilter,
                appliedAdjustments = appliedAdjustments,
                activeFilterName = activeFilterName,
                isSupportingPanel = isSupportingPanel,
                filterIntensity = filterIntensity,
                onFilterIntensityChange = onFilterIntensityChange
            )
        }

        composable<EditorDestination.Markup> {
            MarkupToolSelector(
                isSupportingPanel = isSupportingPanel,
                onToolClick = { item ->
                    when (item) {
                        MarkupItems.Stylus -> {
                            setDrawMode(DrawMode.Draw)
                            setDrawType(DrawType.Stylus)
                        }
                        MarkupItems.Highlighter -> {
                            setDrawMode(DrawMode.Draw)
                            setDrawType(DrawType.Highlighter)
                        }
                        MarkupItems.Marker -> {
                            setDrawMode(DrawMode.Draw)
                            setDrawType(DrawType.Marker)
                        }
                        MarkupItems.Text -> {
                            setDrawMode(DrawMode.Text)
                            onRequestTextInput()
                        }
                        MarkupItems.Eraser -> {
                            setDrawMode(DrawMode.Erase)
                        }
                        MarkupItems.Pan -> {
                            setDrawMode(DrawMode.Touch)
                        }
                    }
                    navController.navigate(EditorDestination.MarkupDraw) {
                        popUpTo(EditorDestination.Markup) { inclusive = false }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable<EditorDestination.MarkupDraw> {
            MarkupSelector(
                drawMode = drawMode,
                setDrawMode = setDrawMode,
                drawType = drawType,
                setDrawType = setDrawType,
                isSupportingPanel = isSupportingPanel,
                currentPathProperty = currentPathProperty,
                setCurrentPathProperty = setCurrentPathProperty,
                onRequestTextInput = onRequestTextInput,
                textAnnotations = textAnnotations,
                onTextAnnotationsChange = onTextAnnotationsChange,
                selectedTextIndex = selectedTextIndex
            )
        }

    }
}
