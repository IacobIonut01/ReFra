package com.dot.gallery.feature_node.presentation.edit

import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.RotateRight
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Flip
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AdaptStrategy
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.layout.SupportingPaneScaffold
import androidx.compose.material3.adaptive.layout.SupportingPaneScaffoldDefaults
import androidx.compose.material3.adaptive.layout.SupportingPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberSupportingPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.toRoute
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.activity.compose.BackHandler
import androidx.navigation.compose.rememberNavController
import com.dot.gallery.R
import com.dot.gallery.core.Constants.Animation.enterAnimation
import com.dot.gallery.core.Constants.Animation.exitAnimation
import com.dot.gallery.core.decoder.RawDevelopParams
import com.dot.gallery.feature_node.domain.model.editor.Adjustment
import com.dot.gallery.feature_node.domain.model.editor.CropState
import com.dot.gallery.feature_node.domain.model.editor.DrawMode
import com.dot.gallery.feature_node.domain.model.editor.DrawType
import com.dot.gallery.feature_node.domain.model.editor.DevelopCategory
import com.dot.gallery.feature_node.domain.model.editor.EditorDestination
import com.dot.gallery.feature_node.domain.model.editor.EditorItems
import com.dot.gallery.feature_node.domain.model.editor.toEditorDestination
import com.dot.gallery.feature_node.domain.model.editor.ImageFilter
import com.dot.gallery.feature_node.domain.model.editor.PathProperties
import com.dot.gallery.feature_node.domain.model.editor.TextAnnotation
import com.dot.gallery.feature_node.presentation.edit.adjustments.varfilter.VariableFilterTypes
import com.dot.gallery.feature_node.presentation.edit.components.editor.EditorNavigator
import com.dot.gallery.feature_node.presentation.edit.components.editor.EditorSelector
import com.dot.gallery.feature_node.presentation.edit.components.editor.ImageViewer
import com.dot.gallery.feature_node.presentation.mediaview.components.media.CutoutState
import com.dot.gallery.feature_node.presentation.mediaview.components.media.ZoomablePagerImagePointTool
import com.dot.gallery.feature_node.presentation.edit.components.markup.TextMarkupOverlay
import com.dot.gallery.feature_node.presentation.mediaview.rememberedDerivedState
import com.dot.gallery.feature_node.presentation.util.LocalHazeState
import com.dot.gallery.feature_node.presentation.util.stableBlur
import com.dot.gallery.ui.theme.GalleryTheme
import com.smarttoolfactory.cropper.model.AspectRatio
import dev.chrisbanes.haze.hazeSource
import kotlin.math.roundToInt

internal fun shouldShowSourceSubsampling(
    hasAppliedAdjustments: Boolean,
    usesProxyPreview: Boolean,
): Boolean = !hasAppliedAdjustments && !usesProxyPreview

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun EditScreen2(
    hasOriginalBackup: Boolean = false,
    isReverting: Boolean = false,
    canOverride: Boolean = false,
    canSave: Boolean = true,
    isChanged: Boolean = false,
    isSaving: Boolean = false,
    saveProgress: Float? = null,
    isProcessing: Boolean = false,
    currentImage: Bitmap?,
    targetImage: Bitmap?,
    targetUri: Uri?,
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
    commitFilter: () -> Unit = {},
    removeLast: () -> Unit,
    onCropRect: (RectF) -> Unit,
    addPath: (Path, PathProperties) -> Unit,
    clearPathsUndone: () -> Unit,
    setCurrentPosition: (Offset) -> Unit,
    setPreviousPosition: (Offset) -> Unit,
    setDrawMode: (DrawMode) -> Unit,
    setDrawType: (DrawType) -> Unit,
    setCurrentPath: (Path) -> Unit,
    setCurrentPathProperty: (PathProperties) -> Unit,
    applyDrawing: (Bitmap, (Boolean) -> Unit) -> Unit,
    undoLastPath: () -> Unit,
    redoLastPath: () -> Unit,
    clearDrawing: () -> Unit = {},
    onRevertToOriginal: () -> Unit = {},
    canUndo: Boolean = false,
    canRedo: Boolean = false,
    onRedo: () -> Unit = {},
    filterIntensity: Float = 1f,
    onFilterIntensityChange: (Float) -> Unit = {},
    activeFilterName: String? = null,
    vignetteIntensity: Float = 0f,
    blurRadius: Float = 0f,
    sharpnessValue: Float = 0f,
    previewRotation90: Float = 0f,
    previewFlipH: Boolean = false,
    onRotate90: () -> Unit = {},
    onFlipH: () -> Unit = {},
    pendingFaceRegions: List<android.graphics.RectF> = emptyList(),
    onFaceRegionsConsumed: () -> Unit = {},
    onDetectFaces: () -> Unit = {},
    faceDetectAvailable: Boolean = false,
    isDetectingFaces: Boolean = false,
    isRawEdit: Boolean = false,
    rawDevelopParams: RawDevelopParams? = null,
    onRawDevelopChange: (RawDevelopParams) -> Unit = {},
    rawThumbnailProvider: (suspend (RawDevelopParams) -> android.graphics.Bitmap?)? = null,
    cutoutAvailable: Boolean = false,
    cutoutState: CutoutState? = null,
    onCutoutAddPoint: (Float, Float, Boolean) -> Unit = { _, _, _ -> },
    onCutoutToolChange: (ZoomablePagerImagePointTool) -> Unit = {},
    onCutoutStart: (backgroundRemoval: Boolean) -> Unit = {},
    onCutoutUndo: () -> Unit = {},
    onCutoutRedo: () -> Unit = {},
    onCutoutReset: () -> Unit = {},
    onCutoutApply: () -> Unit = {},
    onCutoutCancel: () -> Unit = {},
    onCutoutCopy: () -> Unit = {},
    onCutoutShare: () -> Unit = {},
) = GalleryTheme(darkTheme = true, ignoreUserPreference = true) {
    val context = LocalContext.current
    val navigator = rememberSupportingPaneScaffoldNavigator(
        adaptStrategies = SupportingPaneScaffoldDefaults.adaptStrategies(
            supportingPaneAdaptStrategy = AdaptStrategy.Hide
        )
    )
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val visibleEntries by navController.visibleEntries.collectAsState()

    // Track if we're in actual drawing mode (MarkupDraw), not the Markup tool-picker tab
    val isMarkupDrawing by rememberedDerivedState {
        navBackStackEntry?.destination?.hasRoute<EditorDestination.MarkupDraw>() == true
    }

    // Track if we're in any detail mode (adjust scrubber, markup draw)
    val isInDetailMode by rememberedDerivedState {
        isMarkupDrawing ||
        navBackStackEntry?.destination?.hasRoute<EditorDestination.AdjustDetail>() == true
    }
    val usesProxyPreview by rememberedDerivedState {
        isProcessing || visibleEntries.any { entry ->
            entry.destination.hasRoute<EditorDestination.AdjustDetail>() ||
                entry.destination.hasRoute<EditorDestination.MarkupDraw>() ||
                entry.destination.hasRoute<EditorDestination.Filters>() ||
                entry.destination.hasRoute<EditorDestination.Develop>()
        }
    }

    // Text annotation state
    var textAnnotations by remember { mutableStateOf<List<TextAnnotation>>(emptyList()) }
    var showTextOverlay by remember { mutableStateOf(false) }
    var selectedTextIndex by remember { mutableIntStateOf(-1) }

    var requestMarkupApply by remember { mutableStateOf(false) }

    // Auto-apply markup when leaving drawing mode
    var wasDrawing by remember { mutableStateOf(false) }
    LaunchedEffect(isMarkupDrawing) {
        if (wasDrawing && !isMarkupDrawing && (paths.isNotEmpty() || textAnnotations.isNotEmpty())) {
            navController.navigate(EditorDestination.MarkupDraw) { launchSingleTop = true }
            requestMarkupApply = true
        }
        wasDrawing = isMarkupDrawing
    }

    var showRevertDialog by remember { mutableStateOf(false) }

    // Visible tabs depend on the media: RAW hides Lighting/Colour/Effects behind the Develop tab.
    val visibleTabs = remember(isRawEdit, cutoutAvailable) { EditorItems.visibleItems(isRawEdit, cutoutAvailable) }

    // Track which tab is currently selected for the tab bar highlight
    var selectedTab by remember(isRawEdit, cutoutAvailable) {
        mutableStateOf<EditorItems?>(
            when {
                isRawEdit -> EditorItems.WhiteBalance
                cutoutAvailable -> EditorItems.Smart
                else -> EditorItems.Lighting
            }
        )
    }
    val showingEditorScreen by rememberedDerivedState {
        navBackStackEntry?.destination?.hasRoute<EditorDestination.Editor>() == true
    }

    // RAW loads asynchronously: the NavHost may have already started on Lighting before isRawEdit
    // flipped true. Once it does, jump to the first develop tab so content matches the selected tab.
    LaunchedEffect(isRawEdit) {
        if (isRawEdit && navBackStackEntry?.destination?.hasRoute<EditorDestination.Develop>() != true) {
            selectedTab = EditorItems.WhiteBalance
            runCatching {
                navController.navigate(EditorDestination.Develop(DevelopCategory.WhiteBalance)) {
                    popUpTo(EditorDestination.Editor) { inclusive = false }
                    launchSingleTop = true
                }
            }
        }
    }

    // True while the interactive cut-out mode is active (takes over the image surface).
    val isCutoutEditing by rememberedDerivedState {
        navBackStackEntry?.destination?.hasRoute<EditorDestination.CutoutEdit>() == true
    }
    // Which Smart tool launched the session (Cutout vs Background Removal). Both share the workings.
    val cutoutBackgroundRemoval by rememberedDerivedState {
        runCatching { navBackStackEntry?.toRoute<EditorDestination.CutoutEdit>()?.backgroundRemoval }
            .getOrNull() == true
    }
    // Entering encodes + auto-selects the subject; leaving bakes the mask into the editor pipeline
    // (no explicit save/apply button — exit commits, mirroring the Markup tool).
    var wasCutoutEditing by remember { mutableStateOf(false) }
    LaunchedEffect(isCutoutEditing) {
        if (isCutoutEditing && !wasCutoutEditing) onCutoutStart(cutoutBackgroundRemoval)
        if (!isCutoutEditing && wasCutoutEditing) onCutoutApply()
        wasCutoutEditing = isCutoutEditing
    }

    // Determine if we're on a top-level tab (not in a detail view)
    val isOnTopLevelTab by rememberedDerivedState {
        showingEditorScreen ||
        navBackStackEntry?.destination?.hasRoute<EditorDestination.Develop>() == true ||
        navBackStackEntry?.destination?.hasRoute<EditorDestination.Markup>() == true ||
        navBackStackEntry?.destination?.hasRoute<EditorDestination.Lighting>() == true ||
        navBackStackEntry?.destination?.hasRoute<EditorDestination.Colour>() == true ||
        navBackStackEntry?.destination?.hasRoute<EditorDestination.Effects>() == true ||
        navBackStackEntry?.destination?.hasRoute<EditorDestination.Smart>() == true ||
        navBackStackEntry?.destination?.hasRoute<EditorDestination.More>() == true ||
        navBackStackEntry?.destination?.hasRoute<EditorDestination.Filters>() == true
    }

    var cropState by rememberSaveable { mutableStateOf(CropState(showCropper = true)) }

    // Grid overlay state
    var showGridOverlay by remember { mutableStateOf(false) }

    // Pre-enable cropper: visible on all top-level tabs, hidden during detail/adjust/markup-draw
    // modes and the cut-out mode (which takes over the image surface with its own painter).
    val shouldShowCropper by rememberedDerivedState {
        isOnTopLevelTab && !isCutoutEditing
    }
    LaunchedEffect(shouldShowCropper) {
        cropState = cropState.copy(showCropper = shouldShowCropper)
    }

    val animatedBlurRadius by animateDpAsState(
        if (isSaving || isReverting || isProcessing || cropState.isCropping || requestMarkupApply) 50.dp else 0.dp,
        label = "animatedBlurRadius"
    )

    // 3-dot menu state
    var showMenu by remember { mutableStateOf(false) }

    // Aspect ratio state for crop
    var selectedAspectRatio by remember { mutableStateOf(AspectRatio.Original) }
    var showAspectMenu by remember { mutableStateOf(false) }

    val onRequestTextInput: () -> Unit = { showTextOverlay = true }

    // Apply (or cancel) markup on a back press while still in drawing mode, so the
    // MarkupPainter is still composed to consume the request. A raw back gesture
    // otherwise disposes the painter first, leaving requestMarkupApply with nothing
    // to handle it and the blur/loading overlay stuck forever (#955).
    BackHandler(enabled = isMarkupDrawing && !isSaving) {
        if (paths.isNotEmpty() || textAnnotations.isNotEmpty()) {
            requestMarkupApply = true
        } else {
            clearDrawing()
            navController.popBackStack()
        }
    }

    // Safety net: if an apply was requested but we're no longer in drawing mode
    // (the painter is already gone and can't handle it), clear the flag so the
    // loading/blur overlay can never hang indefinitely (#955).
    LaunchedEffect(requestMarkupApply, isMarkupDrawing) {
        if (requestMarkupApply && !isMarkupDrawing && paths.isEmpty() && textAnnotations.isEmpty()) {
            requestMarkupApply = false
        }
    }

    // Back while cutting out exits to the Smart selector; the exit effect bakes the mask.
    BackHandler(enabled = isCutoutEditing && !isSaving) {
        navController.popBackStack()
    }

    // Toolbar close and system back share the same top-level exit policy. Detail destinations still
    // pop locally, so leaving the editor itself is the only action that can trigger the dirty prompt.
    BackHandler(enabled = !isMarkupDrawing && !isCutoutEditing && !isSaving) {
        if (showingEditorScreen || isOnTopLevelTab) onClose() else navController.popBackStack()
    }

    // Top-bar undo/redo drive the recipe and are hidden while cutting out (the cut-out selection has
    // its own undo/redo in the markup-style bottom bar).
    val effectiveCanUndo = canUndo && !isCutoutEditing
    val effectiveCanRedo = canRedo && !isCutoutEditing
    val effectiveUndo: () -> Unit = removeLast
    val effectiveRedo: () -> Unit = onRedo

    Box {
        Column(
            modifier = Modifier
                .hazeSource(LocalHazeState.current)
                .fillMaxSize()
                .then(
                    if (isSaving || isReverting || isProcessing || cropState.isCropping || requestMarkupApply)
                    // Snap the animated radius to buckets so the ramp reuses a few blur shaders
                    // instead of compiling a new GPU pipeline on every animation frame.
                        Modifier.stableBlur(animatedBlurRadius, step = 10.dp)
                    else Modifier
                )
                .background(Color.Black)
                .systemBarsPadding()
        ) {
            // ═══════════════════════════════════════════════════
            // TOP BAR — hidden during detail modes (markup draw, crop, adjust scrubber) and the
            // cut-out mode (which uses its own header Copy/Share + markup-style bottom bar).
            // ═══════════════════════════════════════════════════
            AnimatedVisibility(
                visible = !isInDetailMode && !isCutoutEditing,
                enter = enterAnimation,
                exit = exitAnimation
            ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left cluster: Close + Undo + Redo
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = {
                            if (showingEditorScreen || isOnTopLevelTab) onClose()
                            else navController.popBackStack()
                        },
                        enabled = !isSaving,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.close),
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Undo/Redo — drive the recipe normally, or the cut-out selection while editing.
                    AnimatedVisibility(
                        visible = effectiveCanUndo,
                        enter = enterAnimation,
                        exit = exitAnimation
                    ) {
                        IconButton(
                            onClick = effectiveUndo,
                            enabled = !isSaving,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.Undo,
                                contentDescription = stringResource(R.string.editor_undo),
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = effectiveCanRedo,
                        enter = enterAnimation,
                        exit = exitAnimation
                    ) {
                        IconButton(
                            onClick = effectiveRedo,
                            enabled = !isSaving,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.Redo,
                                contentDescription = stringResource(R.string.editor_redo),
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                // Right cluster: Save pill + 3-dot menu
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Button(
                        onClick = onSaveCopy,
                        enabled = isChanged && canSave && !isMarkupDrawing && !isProcessing && !isSaving,
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            disabledContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f),
                            disabledContentColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.38f)
                        )
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.save_copy),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                    // Only show 3-dot menu if there are actions available
                    val hasMenuActions = (isChanged && canOverride) || isChanged || hasOriginalBackup
                    AnimatedVisibility(
                        visible = hasMenuActions,
                        enter = enterAnimation,
                        exit = exitAnimation
                    ) {
                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            enabled = !isMarkupDrawing && !isProcessing && !isSaving,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.MoreVert,
                                contentDescription = stringResource(R.string.editor_more_options),
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            if (isChanged && canOverride) {
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(stringResource(R.string.override))
                                            Text(
                                                text = stringResource(R.string.editor_save_subtitle),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    onClick = {
                                        showMenu = false
                                        onOverride()
                                    }
                                )
                            }
                            if (isChanged) {
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(stringResource(R.string.save_copy))
                                            Text(
                                                text = stringResource(R.string.editor_save_copy_subtitle),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    onClick = {
                                        showMenu = false
                                        onSaveCopy()
                                    }
                                )
                            }
                            if (hasOriginalBackup) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.revert_to_original)) },
                                    onClick = {
                                        showMenu = false
                                        showRevertDialog = true
                                    }
                                )
                            }
                        }
                    }
                    } // end AnimatedVisibility for 3-dot menu
                }
            }
            } // end AnimatedVisibility for top bar

            // ═══════════════════════════════════════════════════
            // CROP TOOLBAR — hidden in detail modes
            // ═══════════════════════════════════════════════════
            AnimatedVisibility(
                visible = !isInDetailMode,
                enter = enterAnimation,
                exit = exitAnimation
            ) {
              if (isCutoutEditing) {
                // Cut-out mode: the crop toolbar is replaced with subject Copy / Share actions.
                val cutoutHasResult = cutoutState?.hasResult == true
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = onCutoutCopy,
                        enabled = cutoutHasResult,
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.cutout_copy),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    FilledTonalButton(
                        onClick = onCutoutShare,
                        enabled = cutoutHasResult,
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.cutout_share),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
              } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Grid/Straighten icon
                    IconButton(
                        onClick = { showGridOverlay = !showGridOverlay },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (showGridOverlay)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = Color.White
                        ),
                        shape = CircleShape,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.GridOn,
                            contentDescription = stringResource(R.string.editor_grid),
                            tint = if (showGridOverlay)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                Color.White,
                            modifier = Modifier.size(32.dp).padding(6.dp)
                        )
                    }

                    // Right cluster: Aspect ratio, Flip, Rotate
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box {
                            IconButton(
                                onClick = { showAspectMenu = true },
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = Color.White
                                ),
                                shape = CircleShape,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.AspectRatio,
                                    contentDescription = stringResource(R.string.editor_aspect_ratio),
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp).padding(6.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = showAspectMenu,
                                onDismissRequest = { showAspectMenu = false }
                            ) {
                                data class RatioOption(val label: String, val ratio: AspectRatio)
                                val options = listOf(
                                    RatioOption(stringResource(R.string.aspect_freeform), AspectRatio.Original),
                                    RatioOption(stringResource(R.string.aspect_square), AspectRatio(1f)),
                                    RatioOption("5:4", AspectRatio(5f / 4f)),
                                    RatioOption("4:3", AspectRatio(4f / 3f)),
                                    RatioOption("3:2", AspectRatio(3f / 2f)),
                                    RatioOption("16:9", AspectRatio(16f / 9f)),
                                    RatioOption("4:5", AspectRatio(4f / 5f)),
                                    RatioOption("3:4", AspectRatio(3f / 4f)),
                                    RatioOption("2:3", AspectRatio(2f / 3f)),
                                    RatioOption("9:16", AspectRatio(9f / 16f))
                                )
                                options.forEach { option ->
                                    val isSelected = selectedAspectRatio == option.ratio
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(option.label)
                                                if (isSelected) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.Check,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            selectedAspectRatio = option.ratio
                                            showAspectMenu = false
                                        }
                                    )
                                }
                            }
                        }
                        IconButton(
                            onClick = onFlipH,
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = Color.White
                            ),
                            shape = CircleShape,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Flip,
                                contentDescription = stringResource(R.string.editor_mirror),
                                tint = Color.White,
                                modifier = Modifier.size(32.dp).padding(6.dp)
                            )
                        }
                        IconButton(
                            onClick = onRotate90,
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = Color.White
                            ),
                            shape = CircleShape,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.RotateRight,
                                contentDescription = stringResource(R.string.editor_rotate_90),
                                tint = Color.White,
                                modifier = Modifier.size(32.dp).padding(6.dp)
                            )
                        }
                        Button(
                            onClick = {
                                cropState = cropState.copy(isCropping = true)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Crop,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.editor_crop),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
              }
            }

            // ═══════════════════════════════════════════════════
            // IMAGE AREA — fills available space
            // ═══════════════════════════════════════════════════
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                SupportingPaneScaffold(
                    directive = navigator.scaffoldDirective,
                    value = navigator.scaffoldValue,
                    modifier = Modifier
                        .animateContentSize()
                        .fillMaxSize(),
                    mainPane = {
                        ImageViewer(
                            modifier = Modifier.fillMaxSize(),
                            currentImage = currentImage,
                            sourceUri = targetUri,
                            // Show the original via tile subsampling (true 100% zoom) only while the
                            // image is pristine and no detail tool is previewing against the proxy.
                            // Once edits are baked, the proxy keeps the on-screen result correct.
                            showSourceSubsampling = shouldShowSourceSubsampling(
                                hasAppliedAdjustments = appliedAdjustments.isNotEmpty(),
                                usesProxyPreview = usesProxyPreview,
                            ),
                            previewMatrix = previewMatrix,
                            previewRotation = previewRotation,
                            cropState = cropState,
                            cropAspectRatio = selectedAspectRatio,
                            showGridOverlay = showGridOverlay,
                            showMarkup = isMarkupDrawing,
                            showCutout = isCutoutEditing,
                            cutoutState = cutoutState,
                            onCutoutAddPoint = onCutoutAddPoint,
                            paths = paths,
                            pathsUndone = pathsUndone,
                            currentPosition = currentPosition,
                            previousPosition = previousPosition,
                            drawMode = drawMode,
                            currentPath = currentPath,
                            currentPathProperty = currentPathProperty,
                            isSupportingPanel = navigator.scaffoldValue[SupportingPaneScaffoldRole.Supporting] == PaneAdaptedValue.Expanded,
                            onCropRect = {
                                onCropRect(it)
                                cropState = cropState.copy(isCropping = false)
                            },
                            addPath = addPath,
                            clearPathsUndone = clearPathsUndone,
                            setCurrentPosition = setCurrentPosition,
                            setPreviousPosition = setPreviousPosition,
                            setCurrentPath = setCurrentPath,
                            setCurrentPathProperty = setCurrentPathProperty,
                            applyDrawing = applyDrawing,
                            onNavigateBack = { navController.popBackStack() },
                            requestApply = requestMarkupApply,
                            onApplyHandled = { requestMarkupApply = false },
                            textAnnotations = textAnnotations,
                            onTextAnnotationsChange = { textAnnotations = it },
                            selectedTextIndex = selectedTextIndex,
                            onSelectedTextIndexChange = { selectedTextIndex = it },
                            pendingFaceRegions = pendingFaceRegions,
                            onFaceRegionsConsumed = onFaceRegionsConsumed,
                            vignetteIntensity = vignetteIntensity,
                            blurRadius = blurRadius,
                            sharpnessValue = sharpnessValue,
                            previewRotation90 = previewRotation90,
                            previewFlipH = previewFlipH
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
                                drawMode = drawMode,
                                setDrawMode = setDrawMode,
                                drawType = drawType,
                                setDrawType = setDrawType,
                                currentPathProperty = currentPathProperty,
                                setCurrentPathProperty = setCurrentPathProperty,
                                filterIntensity = filterIntensity,
                                onFilterIntensityChange = onFilterIntensityChange,
                                activeFilterName = activeFilterName,
                                isSupportingPanel = true,
                                onRequestTextInput = onRequestTextInput,
                                textAnnotations = textAnnotations,
                                onTextAnnotationsChange = { textAnnotations = it },
                                selectedTextIndex = selectedTextIndex,
                                onDetectFaces = onDetectFaces,
                                faceDetectAvailable = faceDetectAvailable,
                                isDetectingFaces = isDetectingFaces,
                                startDestination = if (isRawEdit) EditorDestination.Develop(DevelopCategory.WhiteBalance) else if (cutoutAvailable) EditorDestination.Smart else EditorDestination.Lighting,
                                rawDevelopParams = if (isRawEdit) rawDevelopParams else null,
                                onRawDevelopChange = onRawDevelopChange,
                                rawThumbnailProvider = rawThumbnailProvider,
                                cutoutState = cutoutState,
                                onCutoutToolChange = onCutoutToolChange,
                                onCutoutReset = onCutoutReset,
                            )
                        }
                    }
                )

            }

            // Spacing between image and bottom content
            Spacer(modifier = Modifier.height(12.dp))

            // ═══════════════════════════════════════════════════
            // BOTTOM SECTION — tool content + tab bar + markup controls
            // ═══════════════════════════════════════════════════
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(
                        animationSpec = spring(
                            stiffness = Spring.StiffnessHigh,
                            visibilityThreshold = IntSize.VisibilityThreshold
                        )
                    )
            ) {
                // Tool content area (shown when on a tab, NOT the Editor home)
                AnimatedVisibility(
                    visible = navigator.scaffoldValue[SupportingPaneScaffoldRole.Supporting] == PaneAdaptedValue.Hidden
                            && !showingEditorScreen,
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
                        drawMode = drawMode,
                        setDrawMode = setDrawMode,
                        drawType = drawType,
                        setDrawType = setDrawType,
                        currentPathProperty = currentPathProperty,
                        setCurrentPathProperty = setCurrentPathProperty,
                        filterIntensity = filterIntensity,
                        onFilterIntensityChange = onFilterIntensityChange,
                        activeFilterName = activeFilterName,
                        isSupportingPanel = false,
                        onRequestTextInput = onRequestTextInput,
                        textAnnotations = textAnnotations,
                        onTextAnnotationsChange = { textAnnotations = it },
                        selectedTextIndex = selectedTextIndex,
                        onDetectFaces = onDetectFaces,
                        faceDetectAvailable = faceDetectAvailable,
                        isDetectingFaces = isDetectingFaces,
                        startDestination = if (isRawEdit) EditorDestination.Develop(DevelopCategory.WhiteBalance) else if (cutoutAvailable) EditorDestination.Smart else EditorDestination.Lighting,
                        rawDevelopParams = if (isRawEdit) rawDevelopParams else null,
                        onRawDevelopChange = onRawDevelopChange,
                        rawThumbnailProvider = rawThumbnailProvider,
                        cutoutState = cutoutState,
                        onCutoutToolChange = onCutoutToolChange,
                        onCutoutReset = onCutoutReset,
                    )
                }

                when {
                    isMarkupDrawing -> {
                        // Markup bottom bar: X | ↶ Reset ↷ | ✓
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // X cancel button
                            IconButton(
                                onClick = {
                                    clearDrawing()
                                    textAnnotations = emptyList()
                                    selectedTextIndex = -1
                                    navController.popBackStack()
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        shape = CircleShape
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = stringResource(R.string.editor_cancel_markup),
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // Center: ↶ "Markup" ↷  (+ "Add Text" when in text mode)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val undoEnabled by rememberedDerivedState(paths) { paths.isNotEmpty() }
                                IconButton(
                                    onClick = undoLastPath,
                                    enabled = undoEnabled
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.Undo,
                                        contentDescription = stringResource(R.string.editor_undo),
                                        tint = if (undoEnabled) Color.White else Color.White.copy(alpha = 0.3f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                if (drawMode == DrawMode.Text) {
                                    TextButton(onClick = onRequestTextInput) {
                                        Icon(
                                            imageVector = Icons.Outlined.Add,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = stringResource(R.string.editor_add_text),
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = Color.White
                                        )
                                    }
                                } else {
                                    Text(
                                        text = stringResource(R.string.markup),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = Color.White
                                    )
                                }

                                val redoEnabled by rememberedDerivedState(pathsUndone) { pathsUndone.isNotEmpty() }
                                IconButton(
                                    onClick = redoLastPath,
                                    enabled = redoEnabled
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.Redo,
                                        contentDescription = stringResource(R.string.editor_redo),
                                        tint = if (redoEnabled) Color.White else Color.White.copy(alpha = 0.3f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            // ✓ apply button
                            IconButton(
                                onClick = { requestMarkupApply = true },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = CircleShape
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Check,
                                    contentDescription = stringResource(R.string.editor_apply_markup),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }

                    navBackStackEntry?.destination?.hasRoute<EditorDestination.AdjustDetail>() == true -> {
                        // Adjust detail bottom bar: "Done" centered pill
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Button(
                                onClick = { navController.popBackStack() },
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            ) {
                                Text(
                                    text = stringResource(R.string.editor_done),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }

                    isCutoutEditing -> {
                        // Markup-style bottom bar for cut-out: X (discard) | ↶ Reset ↷ | ✓ (apply).
                        // Include/Exclude live in the NavHost controls above; Copy/Share in the header.
                        val canUndoCutout = cutoutState?.canUndo == true
                        val canRedoCutout = cutoutState?.canRedo == true
                        val hasResult = cutoutState?.hasResult == true
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // X — discard the selection and leave.
                            IconButton(
                                onClick = {
                                    onCutoutCancel()
                                    navController.popBackStack()
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        shape = CircleShape
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = stringResource(R.string.close),
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // Center: ↶ Reset ↷
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                IconButton(onClick = onCutoutUndo, enabled = canUndoCutout) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.Undo,
                                        contentDescription = stringResource(R.string.editor_undo),
                                        tint = if (canUndoCutout) Color.White else Color.White.copy(alpha = 0.3f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                TextButton(onClick = onCutoutReset, enabled = hasResult) {
                                    Text(
                                        text = stringResource(R.string.cutout_reset),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = if (hasResult) Color.White else Color.White.copy(alpha = 0.3f)
                                    )
                                }
                                IconButton(onClick = onCutoutRedo, enabled = canRedoCutout) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.Redo,
                                        contentDescription = stringResource(R.string.editor_redo),
                                        tint = if (canRedoCutout) Color.White else Color.White.copy(alpha = 0.3f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            // ✓ — commit (the exit effect bakes the mask into the pipeline).
                            IconButton(
                                onClick = { navController.popBackStack() },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = CircleShape
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Check,
                                    contentDescription = stringResource(R.string.editor_done),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }

                    else -> {
                        // Tab bar — visible on top-level tabs
                        // Sync selectedTab when returning from a tool
                        LaunchedEffect(navBackStackEntry) {
                            val dest = navBackStackEntry?.destination
                            val tab = when {
                                dest?.hasRoute<EditorDestination.Develop>() == true -> {
                                    val category = runCatching {
                                        navBackStackEntry?.toRoute<EditorDestination.Develop>()?.category
                                    }.getOrNull()
                                    EditorItems.entries.firstOrNull { it.developCategory == category }
                                }
                                dest?.hasRoute<EditorDestination.Lighting>() == true -> EditorItems.Lighting
                                dest?.hasRoute<EditorDestination.Filters>() == true -> EditorItems.Filters
                                dest?.hasRoute<EditorDestination.Markup>() == true -> EditorItems.Markup
                                dest?.hasRoute<EditorDestination.Colour>() == true -> EditorItems.Colour
                                dest?.hasRoute<EditorDestination.Effects>() == true -> EditorItems.Effects
                                dest?.hasRoute<EditorDestination.Smart>() == true -> EditorItems.Smart
                                dest?.hasRoute<EditorDestination.CutoutEdit>() == true -> EditorItems.Smart
                                dest?.hasRoute<EditorDestination.More>() == true -> EditorItems.More
                                else -> null
                            }
                            if (tab != null) selectedTab = tab
                        }
                        EditorSelector(
                            modifier = Modifier.fillMaxWidth(),
                            selectedItem = selectedTab,
                            items = visibleTabs,
                            isSupportingPanel = false,
                            onItemClick = { editorItem ->
                                // Commit filter when leaving the Filters section
                                if (selectedTab == EditorItems.Filters && editorItem != EditorItems.Filters) {
                                    commitFilter()
                                }
                                selectedTab = editorItem
                                navController.navigate(editorItem.toEditorDestination()) {
                                    popUpTo(EditorDestination.Editor) { inclusive = false }
                                    launchSingleTop = true
                                }
                            }
                        )
                    }
                }
            }
        }

        // Loading overlay
        AnimatedVisibility(
            visible = isSaving || isReverting || isProcessing || requestMarkupApply,
            enter = enterAnimation,
            exit = exitAnimation
        ) {
            Box(
                modifier = Modifier
                    .background(color = Color.Black.copy(alpha = 0.4f))
                    .fillMaxSize()
                    .pointerInput(isSaving, isReverting, isProcessing, requestMarkupApply) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent().changes.forEach { it.consume() }
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                val progress = saveProgress
                if (isSaving && progress != null) {
                    val animatedProgress by animateFloatAsState(
                        targetValue = progress.coerceIn(0f, 1f),
                        label = "saveProgress"
                    )
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier.size(56.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${(animatedProgress * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                    }
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Text markup overlay
        if (showTextOverlay) {
            BackHandler { showTextOverlay = false }
            TextMarkupOverlay(
                onDone = { text, color ->
                    selectedTextIndex = textAnnotations.size
                    textAnnotations = textAnnotations + TextAnnotation(
                        text = text,
                        color = color,
                        position = Offset(0.1f, 0.45f)
                    )
                    showTextOverlay = false
                },
                onRemove = {
                    showTextOverlay = false
                }
            )
        }

        // Revert dialog
        if (showRevertDialog) {
            AlertDialog(
                onDismissRequest = { showRevertDialog = false },
                title = { Text(stringResource(R.string.revert_to_original)) },
                text = { Text(stringResource(R.string.revert_to_original_confirmation)) },
                confirmButton = {
                    Button(
                        onClick = {
                            showRevertDialog = false
                            onRevertToOriginal()
                        }
                    ) {
                        Text(stringResource(R.string.action_revert))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRevertDialog = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            )
        }

        // Registered last so no nested editor/back handler can close or mutate the session mid-save.
        BackHandler(enabled = isSaving || isReverting) {}
    }
}
