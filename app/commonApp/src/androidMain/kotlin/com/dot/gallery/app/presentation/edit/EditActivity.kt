package com.dot.gallery.app.presentation.edit

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import org.jetbrains.compose.resources.stringResource
import androidx.core.view.WindowCompat
import com.dot.gallery.core.presentation.components.CutoutSaveChoice
import com.dot.gallery.core.presentation.components.CutoutSaveChoiceSheet
import com.dot.gallery.core.presentation.components.OverwriteFallbackSheet
import com.dot.gallery.feature.editor.components.develop.RawExportSheet
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.feature.editor.adjustments.Crop
import com.dot.gallery.core.decoder.RawDevelopParams
import com.dot.gallery.core.presentation.util.LocalHazeGlassEnabled
import com.dot.gallery.core.presentation.util.LocalHazeState
import com.dot.gallery.core.ui.settings.LocalSettingsStore
import com.dot.gallery.domain.settings.DataStoreSettingsStore
import com.dot.gallery.core.ui.util.launchWriteRequest
import com.dot.gallery.core.ui.util.rememberActivityResult
import com.dot.gallery.core.ui.util.writeRequest
import com.dot.gallery.ui.theme.GalleryTheme
import androidx.compose.runtime.Composable
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.LocalHazeBlurStyle
import dev.chrisbanes.haze.blur.materials.HazeMaterials
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.dot.gallery.core.util.or
import com.dot.gallery.feature.editor.adjustments.Markup
import com.dot.gallery.ui.theme.AppThemeLocals
import com.dot.gallery.core.Settings.Misc.rememberAllowBlur
import com.dot.gallery.core.Settings.Misc.rememberAllowGlass
import com.dot.gallery.core.activeDataStore
import com.dot.gallery.core.logging.printError
import com.dot.gallery.injection.injectViewModel
import com.dot.gallery.feature.editor.EditViewModel
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.action_cancel
import com.dot.gallery.generated.resources.action_discard
import com.dot.gallery.generated.resources.action_revert
import com.dot.gallery.generated.resources.edit_load_failed
import com.dot.gallery.generated.resources.edit_load_failed_restore_summary
import com.dot.gallery.generated.resources.edit_load_failed_restore_title
import com.dot.gallery.generated.resources.edit_override_failed
import com.dot.gallery.generated.resources.edit_override_permission_denied
import com.dot.gallery.generated.resources.edit_restore_failed
import com.dot.gallery.generated.resources.edit_revert_failed
import com.dot.gallery.generated.resources.edit_save_copy_failed
import com.dot.gallery.generated.resources.editor_unsaved_message
import com.dot.gallery.generated.resources.editor_unsaved_title
import com.dot.gallery.generated.resources.keep_editing
import com.dot.gallery.generated.resources.save_copy
import com.dot.gallery.core.util.getString



@Composable
internal fun editHazeBlurStyle(allowBlur: Boolean): HazeBlurStyle =
    HazeMaterials.thin(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ).then { blurEnabled(allowBlur) }

class EditActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            val settingsStore = remember { DataStoreSettingsStore(activeDataStore) }
            CompositionLocalProvider(LocalSettingsStore provides settingsStore) {
            AppThemeLocals {
                GalleryTheme(
                darkTheme = true
            ) {
                val allowBlur by rememberAllowBlur()
                val allowGlass by rememberAllowGlass()
                val hazeState = rememberHazeState()
                CompositionLocalProvider(
                    LocalHazeState provides hazeState,
                    LocalHazeBlurStyle provides editHazeBlurStyle(allowBlur),
                    LocalHazeGlassEnabled provides (allowBlur && allowGlass)
                ) {
                    LaunchedEffect(Unit) {
                        enableEdgeToEdge(
                            statusBarStyle = SystemBarStyle.auto(
                                Color.TRANSPARENT,
                                Color.TRANSPARENT,
                            ) { true },
                            navigationBarStyle = SystemBarStyle.auto(
                                Color.TRANSPARENT,
                                Color.TRANSPARENT,
                            ) { true }
                        )
                    }
                    val viewModel = injectViewModel<EditViewModel> {editViewModel}

                    LaunchedEffect(intent.data) {
                        intent.data?.let {
                            viewModel.setSourceData(this@EditActivity, it)
                        }
                    }
                    var showRestorePrompt by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        viewModel.loadFailed.collect { hasBackup ->
                            when (hasBackup) {
                                true -> showRestorePrompt = true
                                false -> {
                                    viewModel.clearLoadFailure()
                                    Toast.makeText(
this@EditActivity,
    getString(Res.string.edit_load_failed),
                                        Toast.LENGTH_LONG
                                    ).show()
                                    finish()
                                }
                                null -> Unit
                            }
                        }
                    }
                    val currentImage by viewModel.currentBitmap.collectAsStateWithLifecycle()
                    val targetImage by viewModel.targetBitmap.collectAsStateWithLifecycle()
                    val uri by viewModel.uri.collectAsStateWithLifecycle()
                    val mediaUri by viewModel.mediaUri.collectAsStateWithLifecycle()
                    val canOverride by viewModel.canOverride.collectAsStateWithLifecycle()
                    val hasOriginalBackup by viewModel.hasOriginalBackup.collectAsStateWithLifecycle()
                    val isReverting by viewModel.isReverting.collectAsStateWithLifecycle()
                    val appliedAdjustments by viewModel.appliedAdjustments.collectAsStateWithLifecycle()
                    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
                    val saveProgress by viewModel.saveProgress.collectAsStateWithLifecycle()
                    val previewMatrix by viewModel.previewMatrix.collectAsStateWithLifecycle()
                    val previewRotation by viewModel.previewRotation.collectAsStateWithLifecycle()

                    // Markup states
                    val paths by viewModel.paths.collectAsStateWithLifecycle()
                    val pathsUndone by viewModel.pathsUndone.collectAsStateWithLifecycle()
                    val currentPosition by viewModel.currentPosition.collectAsStateWithLifecycle()
                    val previousPosition by viewModel.previousPosition.collectAsStateWithLifecycle()
                    val drawMode by viewModel.drawMode.collectAsStateWithLifecycle()
                    val drawType by viewModel.drawType.collectAsStateWithLifecycle()
                    val currentPath by viewModel.currentPath.collectAsStateWithLifecycle()
                    val currentPathProperty by viewModel.currentPathProperty.collectAsStateWithLifecycle()
                    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
                    val canRedo by viewModel.canRedo.collectAsStateWithLifecycle()
                    val filterIntensity by viewModel.filterIntensity.collectAsStateWithLifecycle()
                    val activeFilter by viewModel.activeFilter.collectAsStateWithLifecycle()
                    val hasPendingFilter by viewModel.hasPendingFilter.collectAsStateWithLifecycle()
                    val vignetteIntensity by viewModel.previewVignette.collectAsStateWithLifecycle()
                    val blurRadius by viewModel.previewBlur.collectAsStateWithLifecycle()
                    val sharpnessValue by viewModel.previewSharpness.collectAsStateWithLifecycle()
                    val isProcessing by viewModel.isProcessing.collectAsStateWithLifecycle()
                    val previewRotation90 by viewModel.previewRotation90.collectAsStateWithLifecycle()
                    val previewFlipH by viewModel.previewFlipH.collectAsStateWithLifecycle()
                    val pendingFaceRegions by viewModel.pendingFaceRegions.collectAsStateWithLifecycle()
                    val isDetectingFaces by viewModel.isDetectingFaces.collectAsStateWithLifecycle()
                    val isRawEdit by viewModel.isRawEdit.collectAsStateWithLifecycle()
                    val rawDevelopParams by viewModel.rawDevelopParams.collectAsStateWithLifecycle()

                    val scope = rememberCoroutineScope { Dispatchers.IO }

                    var showOverwriteFallback by remember { mutableStateOf(false) }
                    var showRawExportSheet by remember { mutableStateOf(false) }
                    var showDirtyExitPrompt by remember { mutableStateOf(false) }
                    // A cut-out (background-removal) edit produces transparency: before saving we ask
                    // the user for PNG vs flatten. `cutoutSavePendingOverride` records whether the
                    // triggering action was an overwrite (true) or a copy (false).
                    var showCutoutSaveChoice by remember { mutableStateOf(false) }
                    var cutoutSavePendingOverride by remember { mutableStateOf(false) }

                    val onSaveCopyFailed: () -> Unit = {
                        printError("Failed to save copy")
                        runOnUiThread {
                            Toast.makeText(
this@EditActivity,
    getString(Res.string.edit_save_copy_failed),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }

                    val performSaveCopy: (Boolean, Int?) -> Unit = { forcePng, flattenColor ->
                        viewModel.saveCopy(
                            forcePng = forcePng,
                            flattenColor = flattenColor,
                            onSuccess = { finish() },
                            onFail = onSaveCopyFailed
                        )
                    }

                    val doSaveCopy: () -> Unit = {
                        if (viewModel.hasTransparentEdit) {
                            cutoutSavePendingOverride = false
                            showCutoutSaveChoice = true
                        } else {
                            performSaveCopy(false, null)
                        }
                    }

                    if (showRawExportSheet) {
                        RawExportSheet(
                            allowTiff = appliedAdjustments.isEmpty() && !hasPendingFilter,
                            onFormatSelected = { format ->
                                showRawExportSheet = false
                                viewModel.saveRawCopy(
                                    format = format,
                                    onSuccess = { finish() },
                                    onFail = onSaveCopyFailed
                                )
                            },
                            onDismiss = { showRawExportSheet = false }
                        )
                    }

                    val performOverride: (Int?) -> Unit = { flattenColor ->
                        viewModel.saveOverride(
                            flattenColor = flattenColor,
                            onNeedsCopyFallback = {
                                // Source format has no encoder — offer a copy instead.
                                runOnUiThread { showOverwriteFallback = true }
                            },
                            onSuccess = {
                                finish()
                            },
                            onFail = {
                                printError("Failed to save override")
                                runOnUiThread {
                                    Toast.makeText(
this@EditActivity,
    getString(Res.string.edit_override_failed),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        )
                    }

                    val doOverride: () -> Unit = {
                        if (viewModel.hasTransparentEdit) {
                            cutoutSavePendingOverride = true
                            showCutoutSaveChoice = true
                        } else {
                            performOverride(null)
                        }
                    }

                    if (showOverwriteFallback) {
                        OverwriteFallbackSheet(
                            onCreateCopy = {
                                showOverwriteFallback = false
                                // A transparent edit forces a PNG copy; other non-encodable sources
                                // fall back to their default lossless PNG copy.
                                performSaveCopy(viewModel.hasTransparentEdit, null)
                            },
                            onDismiss = { showOverwriteFallback = false }
                        )
                    }

                    if (showCutoutSaveChoice) {
                        CutoutSaveChoiceSheet(
                            onChoice = { choice ->
                                showCutoutSaveChoice = false
                                when (choice) {
                                    CutoutSaveChoice.TRANSPARENT_PNG -> {
                                        // PNG holds alpha; overwrite-in-place of an alpha-less source
                                        // isn't possible, so route override to a PNG copy.
                                        if (cutoutSavePendingOverride) showOverwriteFallback = true
                                        else performSaveCopy(true, null)
                                    }
                                    CutoutSaveChoice.FLATTEN_WHITE -> {
                                        if (cutoutSavePendingOverride) performOverride(android.graphics.Color.WHITE)
                                        else performSaveCopy(false, android.graphics.Color.WHITE)
                                    }
                                    CutoutSaveChoice.FLATTEN_BLACK -> {
                                        if (cutoutSavePendingOverride) performOverride(android.graphics.Color.BLACK)
                                        else performSaveCopy(false, android.graphics.Color.BLACK)
                                    }
                                }
                            },
                            onDismiss = { showCutoutSaveChoice = false }
                        )
                    }
                    val overrideRequest = rememberActivityResult(
                        onResultCanceled = {
                            Toast.makeText(
this@EditActivity,
    getString(Res.string.edit_override_permission_denied),
                                Toast.LENGTH_LONG
                            ).show()
                        },
                        onResultOk = doOverride
                    )

                    val doRevert: () -> Unit = {
                        viewModel.revertToOriginal(
                            onSuccess = {
                                finish()
                            },
                            onFail = {
                                printError("Failed to revert to original")
                                runOnUiThread {
                                    Toast.makeText(
this@EditActivity,
    getString(Res.string.edit_revert_failed),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        )
                    }
                    val revertRequest = rememberActivityResult(
                        onResultCanceled = {
                            Toast.makeText(
this@EditActivity,
    getString(Res.string.edit_override_permission_denied),
                                Toast.LENGTH_LONG
                            ).show()
                        },
                        onResultOk = doRevert
                    )

                    // Restore-on-load-failure: write the backed-up original back over the (corrupted)
                    // file, then reload the editor with the restored image instead of finishing.
                    val doRestoreAndReload: () -> Unit = {
                        viewModel.revertToOriginal(
                            onSuccess = {
                                intent.data?.let { data ->
                                    viewModel.setSourceData(this@EditActivity, data, forceReload = true)
                                }
                            },
                            onFail = {
                                printError("Failed to restore original after load failure")
                                runOnUiThread {
                                    Toast.makeText(
this@EditActivity,
    getString(Res.string.edit_restore_failed),
                                        Toast.LENGTH_LONG
                                    ).show()
                                    finish()
                                }
                            }
                        )
                    }
                    val restoreRequest = rememberActivityResult(
                        onResultCanceled = {
                            Toast.makeText(
this@EditActivity,
    getString(Res.string.edit_override_permission_denied),
                                Toast.LENGTH_LONG
                            ).show()
                            finish()
                        },
                        onResultOk = doRestoreAndReload
                    )

                    val hasUnsavedChanges = appliedAdjustments.isNotEmpty() || hasPendingFilter ||
                        (isRawEdit && rawDevelopParams != null && rawDevelopParams != RawDevelopParams.AUTO)
                    val requestEditorClose: () -> Unit = {
                        if (!isSaving) {
                            if (hasUnsavedChanges) showDirtyExitPrompt = true else finish()
                        }
                    }

                    if (showDirtyExitPrompt) {
                        AlertDialog(
                            onDismissRequest = { showDirtyExitPrompt = false },
                            title = { Text(stringResource(Res.string.editor_unsaved_title)) },
                            text = { Text(stringResource(Res.string.editor_unsaved_message)) },
                            confirmButton = {
                                TextButton(onClick = {
                                    showDirtyExitPrompt = false
                                    if (isRawEdit) showRawExportSheet = true else doSaveCopy()
                                }) {
                                    Text(stringResource(Res.string.save_copy))
                                }
                            },
                            dismissButton = {
                                Row {
                                    TextButton(onClick = { showDirtyExitPrompt = false }) {
                                        Text(stringResource(Res.string.keep_editing))
                                    }
                                    TextButton(onClick = {
                                        showDirtyExitPrompt = false
                                        finish()
                                    }) {
                                        Text(stringResource(Res.string.action_discard))
                                    }
                                }
                            },
                        )
                    }

                    if (showRestorePrompt) {
                        AlertDialog(
                            onDismissRequest = {
                                showRestorePrompt = false
                                viewModel.clearLoadFailure()
                                finish()
                            },
                            title = { Text(stringResource(Res.string.edit_load_failed_restore_title)) },
                            text = { Text(stringResource(Res.string.edit_load_failed_restore_summary)) },
                            confirmButton = {
                                TextButton(onClick = {
                                    showRestorePrompt = false
                                    viewModel.clearLoadFailure()
                                    scope.launch {
                                        // The write grant must target the canonical media URI
                                        // actually written to, not the (possibly alias) intent URI.
                                        (mediaUri ?: uri)?.let { u ->
                                            restoreRequest.launchWriteRequest(
                                                u.writeRequest(contentResolver),
                                                doRestoreAndReload
                                            )
                                        }
                                    }
                                }) {
                                    Text(stringResource(Res.string.action_revert))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = {
                                    showRestorePrompt = false
                                    viewModel.clearLoadFailure()
                                    finish()
                                }) {
                                    Text(stringResource(Res.string.action_cancel))
                                }
                            }
                        )
                    }


                    EditScreen2(
                        hasOriginalBackup = hasOriginalBackup,
                        isReverting = isReverting,
                        canOverride = canOverride,
                        // RAW is always saveable (developing to a copy is valid even at defaults).
                        isChanged = appliedAdjustments.isNotEmpty() || hasPendingFilter || isRawEdit,
                        isSaving = isSaving,
                        saveProgress = saveProgress,
                        isProcessing = isProcessing,
                        currentImage = currentImage,
                        targetImage = targetImage ?: currentImage,
                        targetUri = uri,
                        previewMatrix = previewMatrix,
                        previewRotation = previewRotation,
                        appliedAdjustments = appliedAdjustments,
                        currentPosition = currentPosition,
                        paths = paths,
                        pathsUndone = pathsUndone,
                        previousPosition = previousPosition,
                        drawMode = drawMode,
                        drawType = drawType,
                        currentPathProperty = currentPathProperty,
                        currentPath = currentPath,
                        onClose = requestEditorClose,
                        onOverride = {
                            scope.launch {
                                (mediaUri ?: uri)?.let { uri ->
                                    overrideRequest.launchWriteRequest(
                                        uri.writeRequest(contentResolver),
                                        doOverride
                                    )
                                }
                            }
                        },
                        onSaveCopy = {
                            // RAW opens a format chooser (JPEG/PNG/TIFF); other media save directly.
                            if (isRawEdit) showRawExportSheet = true else doSaveCopy()
                        },
                        onAdjustItemLongClick = viewModel::removeKind,
                        onAdjustmentChange = viewModel::applyAdjustment,
                        onAdjustmentPreview = viewModel::previewAdjustment,
                        onToggleFilter = viewModel::toggleFilter,
                        commitFilter = viewModel::commitFilter,
                        removeLast = viewModel::removeLast,
                        onCropRect = { normalizedRect ->
                            viewModel.applyAdjustment(Crop(normalizedRect))
                        },
                        addPath = viewModel::addPath,
                        clearPathsUndone = viewModel::clearPathsUndone,
                        setCurrentPosition = viewModel::setCurrentPosition,
                        setPreviousPosition = viewModel::setPreviousPosition,
                        setDrawMode = viewModel::setDrawMode,
                        setDrawType = viewModel::setDrawType,
                        setCurrentPath = viewModel::setCurrentPath,
                        setCurrentPathProperty = viewModel::setCurrentPathProperty,
                        applyDrawing = viewModel::applyDrawing,
                        undoLastPath = viewModel::undoLastPath,
                        redoLastPath = viewModel::redoLastPath,
                        clearDrawing = viewModel::clearDrawingBoard,
                        onRevertToOriginal = {
                            scope.launch {
                                (mediaUri ?: uri)?.let { uri ->
                                    revertRequest.launchWriteRequest(
                                        uri.writeRequest(contentResolver),
                                        doRevert
                                    )
                                }
                            }
                        },
                        canUndo = canUndo,
                        canRedo = canRedo,
                        onRedo = viewModel::redoLast,
                        filterIntensity = filterIntensity,
                        onFilterIntensityChange = viewModel::setFilterIntensity,
                        activeFilterName = activeFilter?.name,
                        vignetteIntensity = vignetteIntensity,
                        blurRadius = blurRadius,
                        sharpnessValue = sharpnessValue,
                        previewRotation90 = previewRotation90,
                        previewFlipH = previewFlipH,
                        onRotate90 = viewModel::applyRotate90,
                        onFlipH = viewModel::applyFlipH,
                        pendingFaceRegions = pendingFaceRegions,
                        onFaceRegionsConsumed = viewModel::consumeFaceRegions,
                        onDetectFaces = viewModel::detectFacesForMarkup,
                        faceDetectAvailable = viewModel.faceDetectAvailable,
                        isDetectingFaces = isDetectingFaces,
                        isRawEdit = isRawEdit,
                        rawDevelopParams = rawDevelopParams,
                        onRawDevelopChange = viewModel::updateRawDevelop,
                        rawThumbnailProvider = viewModel::rawOptionThumbnail,
                        cutoutAvailable = viewModel.cutoutAvailable,
                        cutoutState = viewModel.cutoutState,
                        onCutoutAddPoint = viewModel::addCutoutPoint,
                        onCutoutToolChange = viewModel::setCutoutTool,
                        onCutoutStart = viewModel::startCutout,
                        onCutoutUndo = viewModel::undoCutout,
                        onCutoutRedo = viewModel::redoCutout,
                        onCutoutReset = viewModel::resetCutout,
                        onCutoutApply = viewModel::applyCutoutAsEdit,
                        onCutoutCancel = viewModel::cancelCutout,
                        onCutoutCopy = viewModel::cutoutCopy,
                        onCutoutShare = viewModel::cutoutShare,
                    )
                }
            }
            }
            }
        }
    }

    companion object {

        fun launchEditor(context: Context, uri: Uri) {
            context.startActivity(Intent(context, EditActivity::class.java).apply { data = uri })
        }
    }

}