package com.dot.gallery.feature_node.presentation.edit

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.layout.SupportingPaneScaffold
import androidx.compose.material3.adaptive.layout.SupportingPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberSupportingPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dot.gallery.R
import com.dot.gallery.core.Constants.Animation.enterAnimation
import com.dot.gallery.core.Constants.Animation.exitAnimation
import com.dot.gallery.feature_node.domain.model.editor.Adjustment
import com.dot.gallery.feature_node.domain.model.editor.CropState
import com.dot.gallery.feature_node.domain.model.editor.DrawMode
import com.dot.gallery.feature_node.domain.model.editor.DrawType
import com.dot.gallery.feature_node.domain.model.editor.EditorDestination
import com.dot.gallery.feature_node.domain.model.editor.ImageFilter
import com.dot.gallery.feature_node.domain.model.editor.PathProperties
import com.dot.gallery.feature_node.presentation.edit.adjustments.varfilter.VariableFilterTypes
import com.dot.gallery.feature_node.presentation.edit.components.editor.EditorNavigator
import com.dot.gallery.feature_node.presentation.edit.components.editor.ImageViewer
import com.dot.gallery.feature_node.presentation.mediaview.rememberedDerivedState
import com.dot.gallery.feature_node.presentation.util.getEditImageCapableApps
import com.dot.gallery.feature_node.presentation.util.goBack
import com.dot.gallery.ui.theme.GalleryTheme

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun EditScreen2(
    canOverride: Boolean = false,
    canSave: Boolean = true,
    isChanged: Boolean = false,
    isSaving: Boolean = false,
    currentImage: Bitmap?,
    targetImage: Bitmap?,
    targetUri: Uri?,
    originalImage: Bitmap? = null,
    previewMatrix: ColorMatrix? = null,
    previewRotation: Float = 0f,
    appliedAdjustments: List<Adjustment> = emptyList(),
    currentPosition: Offset,
    paths: List<Pair<Path, PathProperties>>,
    pathsUndone: List<Pair<Path, PathProperties>>,
    previousPosition: Offset,
    drawMode: DrawMode,
    drawType: DrawType,
    currentPathProperty: PathProperties,
    currentPath: Path,
    onClose: () -> Unit,
    onOverride: () -> Unit,
    onSaveCopy: () -> Unit,
    onAdjustItemLongClick: (VariableFilterTypes) -> Unit,
    onAdjustmentChange: (Adjustment) -> Unit,
    onAdjustmentPreview: (Adjustment) -> Unit,
    onToggleFilter: (ImageFilter) -> Unit,
    removeLast: () -> Unit,
    onCropSuccess: (Bitmap) -> Unit,
    addPath: (Path, PathProperties) -> Unit,
    clearPathsUndone: () -> Unit,
    setCurrentPosition: (Offset) -> Unit,
    setPreviousPosition: (Offset) -> Unit,
    setDrawMode: (DrawMode) -> Unit,
    setDrawType: (DrawType) -> Unit,
    setCurrentPath: (Path) -> Unit,
    setCurrentPathProperty: (PathProperties) -> Unit,
    applyDrawing: (Bitmap, () -> Unit) -> Unit,
    undoLastPath: () -> Unit,
    redoLastPath: () -> Unit
) = GalleryTheme(darkTheme = true, ignoreUserPreference = true) {
    val context = LocalContext.current
    val navigator = rememberSupportingPaneScaffoldNavigator()
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()

    val showMarkup by rememberedDerivedState {
        navBackStackEntry?.destination?.route?.contains("markup", true) == true
    }

    var cropState by rememberSaveable { mutableStateOf(CropState()) }
    LaunchedEffect(navBackStackEntry) {
        cropState = cropState.copy(
            showCropper = navBackStackEntry?.destination?.hasRoute<EditorDestination.Crop>() == true
        )
    }

    val animatedBlurRadius by animateDpAsState(
        if (isSaving || cropState.isCropping) 50.dp else 0.dp,
        label = "animatedBlurRadius"
    )

    Box {
        Scaffold(
            modifier = Modifier
                .animateContentSize()
                .fillMaxSize()
                .then(if (isSaving || cropState.isCropping) Modifier.blur(animatedBlurRadius) else Modifier),
            containerColor = Color.Black,
            contentColor = Color.White,
            bottomBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(
                            animationSpec = spring(
                                stiffness = Spring.StiffnessHigh,
                                visibilityThreshold = IntSize.VisibilityThreshold
                            )
                        )
                        .systemBarsPadding(),
                ) {
                    AnimatedVisibility(
                        visible = navigator.scaffoldValue[SupportingPaneScaffoldRole.Supporting] == PaneAdaptedValue.Hidden,
                        enter = enterAnimation,
                        exit = exitAnimation
                    ) {
                        EditorNavigator(
                            modifier = Modifier
                                .animateContentSize()
                                .fillMaxWidth(),
                            navController = navController,
                            appliedAdjustments = appliedAdjustments,
                            targetImage = targetImage,
                            targetUri = targetUri,
                            onAdjustItemLongClick = onAdjustItemLongClick,
                            onAdjustmentChange = onAdjustmentChange,
                            onAdjustmentPreview = onAdjustmentPreview,
                            onToggleFilter = onToggleFilter,
                            startCropping = {
                                cropState = cropState.copy(isCropping = true)
                            },
                            drawMode = drawMode,
                            setDrawMode = setDrawMode,
                            drawType = drawType,
                            setDrawType = setDrawType,
                            currentPathProperty = currentPathProperty,
                            setCurrentPathProperty = setCurrentPathProperty,
                        )
                    }
                    val showingEditorScreen = remember(navBackStackEntry) {
                        navBackStackEntry?.destination?.hasRoute<EditorDestination.Editor>() == true
                    }
                    Row(
                        modifier = Modifier
                            .animateContentSize()
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row {
                            IconButton(
                                onClick = {
                                    if (showingEditorScreen) onClose() else {
                                        context.goBack {
                                            navController.popBackStack()
                                        }
                                    }
                                }
                            ) {
                                val icon =
                                    if (showingEditorScreen) Icons.Outlined.Close else Icons.Outlined.ChevronLeft
                                Icon(
                                    imageVector = icon,
                                    contentDescription = icon.name,
                                    tint = LocalContentColor.current
                                )
                            }

                            val showingExternalEditorScreen by rememberedDerivedState {
                                navBackStackEntry?.destination?.hasRoute<EditorDestination.ExternalEditor>() == true
                            }

                            val editApps = remember(context, context::getEditImageCapableApps)

                            AnimatedVisibility(
                                visible = !showingExternalEditorScreen && (editApps.isNotEmpty() || isChanged) && !showMarkup,
                                enter = enterAnimation,
                                exit = exitAnimation
                            ) {
                                IconButton(
                                    onClick = {
                                        if (isChanged) {
                                            removeLast()
                                        } else {
                                            navController.navigate(EditorDestination.ExternalEditor)
                                        }
                                    }
                                ) {
                                    val icon =
                                        if (isChanged) Icons.AutoMirrored.Outlined.Undo else Icons.AutoMirrored.Outlined.OpenInNew
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = icon.name,
                                        tint = LocalContentColor.current
                                    )
                                }
                            }

                            AnimatedVisibility(
                                visible = showMarkup,
                                enter = enterAnimation,
                                exit = exitAnimation
                            ) {
                                Row {
                                    VerticalDivider(
                                        modifier = Modifier
                                            .height(24.dp)
                                            .align(Alignment.CenterVertically)
                                            .padding(horizontal = 4.dp)
                                    )
                                    val undoEnabled by rememberedDerivedState(paths) { paths.isNotEmpty() }
                                    IconButton(
                                        onClick = {
                                            undoLastPath()
                                        },
                                        enabled = undoEnabled
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Outlined.Undo,
                                            contentDescription = "Undo",
                                            tint = LocalContentColor.current
                                        )
                                    }
                                    val redoEnabled by rememberedDerivedState(pathsUndone) { pathsUndone.isNotEmpty() }
                                    IconButton(
                                        onClick = {
                                            redoLastPath()
                                        },
                                        enabled = redoEnabled
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Outlined.Redo,
                                            contentDescription = "Redo",
                                            tint = LocalContentColor.current
                                        )
                                    }
                                }
                            }
                        }

                        AnimatedVisibility(
                            visible = isChanged,
                            enter = enterAnimation,
                            exit = exitAnimation
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                ElevatedButton(
                                    shape = RoundedCornerShape(
                                        topStart = 100f,
                                        bottomStart = 100f,
                                        topEnd = 10f,
                                        bottomEnd = 10f
                                    ),
                                    onClick = onOverride,
                                    enabled = canOverride && canSave
                                ) {
                                    Text(stringResource(R.string.override))
                                }
                                Button(
                                    shape = RoundedCornerShape(
                                        topStart = 10f,
                                        bottomStart = 10f,
                                        topEnd = 100f,
                                        bottomEnd = 100f
                                    ),
                                    onClick = onSaveCopy,
                                    enabled = canSave
                                ) {
                                    Text(stringResource(R.string.save_copy))
                                }
                            }
                        }

                        AnimatedVisibility(
                            modifier = Modifier.padding(end = 16.dp),
                            visible = !isChanged && showingEditorScreen,
                            enter = enterAnimation,
                            exit = exitAnimation
                        ) {
                            Text(
                                text = stringResource(R.string.up_to_date),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            SupportingPaneScaffold(
                directive = navigator.scaffoldDirective,
                value = navigator.scaffoldValue,
                modifier = Modifier
                    .padding(innerPadding)
                    .animateContentSize()
                    .fillMaxSize(),
                mainPane = {
                    ImageViewer(
                        modifier = Modifier.fillMaxSize(),
                        currentImage = currentImage,
                        previewMatrix = previewMatrix,
                        previewRotation = previewRotation,
                        cropState = cropState,
                        showMarkup = showMarkup,
                        paths = paths,
                        currentPosition = currentPosition,
                        previousPosition = previousPosition,
                        drawMode = drawMode,
                        currentPath = currentPath,
                        currentPathProperty = currentPathProperty,
                        isSupportingPanel = navigator.scaffoldValue[SupportingPaneScaffoldRole.Supporting] == PaneAdaptedValue.Expanded,
                        onCropSuccess = {
                            onCropSuccess(it)
                            cropState = cropState.copy(isCropping = false)
                        },
                        addPath = addPath,
                        clearPathsUndone = clearPathsUndone,
                        setCurrentPosition = setCurrentPosition,
                        setPreviousPosition = setPreviousPosition,
                        setCurrentPath = setCurrentPath,
                        setCurrentPathProperty = setCurrentPathProperty,
                        applyDrawing = applyDrawing
                    )
                },
                supportingPane = {
                    AnimatedPane(modifier = Modifier) {
                        EditorNavigator(
                            modifier = Modifier.animateContentSize(),
                            navController = navController,
                            appliedAdjustments = appliedAdjustments,
                            targetImage = targetImage,
                            targetUri = targetUri,
                            onAdjustItemLongClick = onAdjustItemLongClick,
                            onAdjustmentChange = onAdjustmentChange,
                            onAdjustmentPreview = onAdjustmentPreview,
                            onToggleFilter = onToggleFilter,
                            startCropping = {
                                cropState = cropState.copy(isCropping = true)
                            },
                            drawMode = drawMode,
                            setDrawMode = setDrawMode,
                            drawType = drawType,
                            setDrawType = setDrawType,
                            currentPathProperty = currentPathProperty,
                            setCurrentPathProperty = setCurrentPathProperty,
                            isSupportingPanel = true
                        )
                    }
                }
            )
        }

        AnimatedVisibility(
            visible = isSaving,
            enter = enterAnimation,
            exit = exitAnimation
        ) {
            Box(
                modifier = Modifier
                    .background(
                        color = Color.Black.copy(alpha = 0.4f)
                    )
                    .fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
