package com.dot.gallery.feature_node.presentation.edit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import com.dot.gallery.R
import com.dot.gallery.core.ml.CutoutHelper
import com.dot.gallery.core.ml.ModelGroup
import com.dot.gallery.feature_node.presentation.edit.adjustments.Cutout
import com.dot.gallery.feature_node.presentation.edit.adjustments.FlattenBackground
import com.dot.gallery.feature_node.presentation.mediaview.components.media.CutoutState
import com.dot.gallery.feature_node.presentation.mediaview.components.media.ZoomablePagerImagePointTool
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import com.dot.gallery.feature_node.presentation.edit.bake.EditReplay
import com.dot.gallery.feature_node.presentation.edit.bake.NativeHeifEncoder
import com.dot.gallery.feature_node.presentation.edit.bake.NativeImageEncoder
import com.dot.gallery.feature_node.presentation.edit.bake.TiledBakeEngine
import com.dot.gallery.feature_node.presentation.edit.components.develop.RawSaveFormat
import com.dot.gallery.core.util.ext.overrideImageStreaming
import com.dot.gallery.core.util.ext.restoreMediaTimestamp
import com.dot.gallery.core.util.ext.saveImageStreaming
import com.dot.gallery.core.util.ext.selectedModifiedTimestamp
import com.dot.gallery.core.EditBackupManager
import com.dot.gallery.core.MediaHandler
import com.dot.gallery.core.Settings
import com.dot.gallery.core.decoder.NativeRawDecoder
import com.dot.gallery.core.decoder.RawDevelopParams
import com.dot.gallery.core.decoder.RawDevelopStore
import com.dot.gallery.core.decoder.RawOrientation
import com.dot.gallery.core.decoder.RawRegionDecoder
import com.dot.gallery.core.decoder.RawThumbnailCache
import com.dot.gallery.core.decoder.format.ImageReencoder
import com.dot.gallery.core.decoder.format.SourceQualityProbe
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.Media.UriMedia
import com.dot.gallery.feature_node.domain.util.isRaw
import com.dot.gallery.feature_node.domain.model.editor.Adjustment
import com.dot.gallery.feature_node.domain.model.editor.DrawMode
import com.dot.gallery.feature_node.domain.model.editor.DrawType
import com.dot.gallery.feature_node.domain.model.editor.ImageFilter
import com.dot.gallery.feature_node.domain.model.editor.MarkupBrush
import com.dot.gallery.feature_node.domain.model.editor.PathProperties
import com.dot.gallery.feature_node.domain.model.editor.SuggestionPreset
import com.dot.gallery.feature_node.domain.model.editor.VariableFilter
import com.dot.gallery.feature_node.domain.repository.MediaRepository
import com.dot.gallery.feature_node.presentation.edit.adjustments.Flip
import com.dot.gallery.feature_node.presentation.edit.adjustments.Markup
import com.dot.gallery.feature_node.presentation.edit.adjustments.MatrixAdjustment
import com.dot.gallery.feature_node.presentation.edit.adjustments.Rotate90CW
import com.dot.gallery.feature_node.presentation.edit.adjustments.varfilter.Rotate
import com.dot.gallery.feature_node.presentation.edit.adjustments.varfilter.Denoise
import com.dot.gallery.feature_node.presentation.edit.adjustments.varfilter.Sharpness
import com.dot.gallery.feature_node.presentation.edit.adjustments.varfilter.Vignette
import com.dot.gallery.feature_node.presentation.edit.adjustments.varfilter.VariableFilterTypes
import com.dot.gallery.feature_node.presentation.util.applyColorMatrix
import com.dot.gallery.feature_node.presentation.util.resizeBitmap
import com.dot.gallery.core.workers.EditBackupWorker
import com.dot.gallery.core.workers.revertEditBackup
import com.dot.gallery.feature_node.presentation.util.printDebug
import com.dot.gallery.feature_node.presentation.util.printError
import com.github.panpf.sketch.sketch
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

internal fun updatedEditorRecipe(
    adjustments: List<Adjustment>,
    adjustment: Adjustment,
): List<Adjustment> {
    val matches: (Adjustment) -> Boolean = when (adjustment) {
        is VariableFilter -> { existing -> existing.name.equals(adjustment.name, ignoreCase = true) }
        is ImageFilter -> { existing -> existing is ImageFilter }
        else -> return adjustments + adjustment
    }
    val existingIndex = adjustments.indexOfFirst(matches)
    val remaining = adjustments.filterNot(matches)
    val isDefault = adjustment is VariableFilter &&
            kotlin.math.abs(adjustment.value - adjustment.defaultValue) < 1e-4f
    if (isDefault) return remaining
    if (existingIndex < 0) return adjustments + adjustment
    return remaining.toMutableList().apply {
        add(existingIndex.coerceAtMost(size), adjustment)
    }
}

@HiltViewModel
class EditViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: MediaRepository,
    private val mediaHandler: MediaHandler,
    private val editBackupManager: EditBackupManager,
    private val workManager: WorkManager,
    private val modelManager: com.dot.gallery.core.ml.ModelManager,
) : ViewModel() {

    /** Normalized face rects awaiting conversion into markup regions by the painter. */
    private val _pendingFaceRegions = MutableStateFlow<List<android.graphics.RectF>>(emptyList())
    val pendingFaceRegions = _pendingFaceRegions.asStateFlow()

    private val _isDetectingFaces = MutableStateFlow(false)
    val isDetectingFaces = _isDetectingFaces.asStateFlow()
    private var faceDetectionJob: Job? = null

    /** True when the on-device face detector model is installed (gates the "Blur faces" action). */
    val faceDetectAvailable: Boolean
        get() = modelManager.isReady(com.dot.gallery.core.ml.ModelGroup.FACE_DETECT)

    /** Run face detection on the current image; results are emitted via [pendingFaceRegions]. */
    fun detectFacesForMarkup() {
        if (!faceDetectAvailable) return
        val bmp = lastRealBitmap() ?: return
        faceDetectionJob?.cancel()
        faceDetectionJob = viewModelScope.launch(Dispatchers.IO) {
            _isDetectingFaces.value = true
            val helper = com.dot.gallery.core.ml.FaceHelper(modelManager)
            try {
                val faces = helper.detect(bmp)
                _pendingFaceRegions.value = faces.map {
                    android.graphics.RectF(it.left, it.top, it.right, it.bottom)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                helper.close()
                _isDetectingFaces.value = false
            }
        }
    }

    fun consumeFaceRegions() {
        _pendingFaceRegions.value = emptyList()
    }

    // ── Cutout tool (on-device MobileSAM) ─────────────────────────────────────
    /** True when the MobileSAM models are installed (gates the Cutout editor tab). */
    val cutoutAvailable: Boolean
        get() = modelManager.isReady(ModelGroup.CUTOUT)

    /** Interactive cut-out session state (session, prompt points, mask result, tool, history). */
    val cutoutState = CutoutState()

    /** True while a cut-out edit (background removal) is part of the recipe → transparency present. */
    val hasTransparentEdit: Boolean
        get() = _appliedAdjustments.value.any { it is Cutout }

    fun setCutoutTool(tool: ZoomablePagerImagePointTool) {
        cutoutState.activeTool = tool
    }

    /** Records which Smart tool opened the session (Cutout vs Background Removal). */
    private var cutoutBackgroundRemoval = false

    /**
     * Enter the interactive cut-out: encode the current editor proxy and auto-select the most likely
     * subject (a centred seed, kept only when it looks like a real subject). The user then refines
     * with include/exclude taps. [backgroundRemoval] records which Smart tool launched the session;
     * both share the same interactive workings.
     */
    fun startCutout(backgroundRemoval: Boolean) {
        if (cutoutState.session != null || cutoutState.isProcessing) return
        val base = _targetBitmap.value ?: return
        val media = activeMedia.value ?: return
        cutoutBackgroundRemoval = backgroundRemoval
        cutoutState.activeTool = ZoomablePagerImagePointTool.ADD
        viewModelScope.launch(Dispatchers.Default) {
            cutoutState.isProcessing = true
            try {
                val session = CutoutHelper.CutoutSession(context, media, modelManager)
                if (!session.initAndRunEncoder(base)) {
                    session.close()
                    toastCutout(R.string.cutout_init_failed)
                    return@launch
                }
                // Auto pre-select: seed a single centre point (the usual subject location).
                val seed = CutoutHelper.PromptPoint(
                    x = session.widthOrig / 2f,
                    y = session.heightOrig / 2f,
                    isPositive = true
                )
                val result = session.runDecoder(listOf(seed))
                if (result != null && isPlausibleSubject(result, session)) {
                    cutoutState.initSession(session, listOf(seed))
                    cutoutState.updateResult(result, null)
                } else {
                    // No confident subject — keep the encoded session ready for manual taps.
                    result?.bitmap?.recycle()
                    cutoutState.initSession(session, emptyList())
                }
            } finally {
                cutoutState.isProcessing = false
            }
        }
    }

    /** Rejects specks and near-full-frame masks that usually mean "no clear subject". */
    private fun isPlausibleSubject(
        result: CutoutHelper.CutoutResult,
        session: CutoutHelper.CutoutSession
    ): Boolean {
        val subjectArea = result.originalBounds.width().toLong() * result.originalBounds.height().toLong()
        val total = session.widthOrig.toLong() * session.heightOrig.toLong()
        if (total <= 0L || subjectArea <= 0L) return false
        val fraction = subjectArea.toFloat() / total.toFloat()
        return fraction in 0.03f..0.9f
    }

    /**
     * Place a prompt point (in working-bitmap pixel coordinates). Normally the session already
     * exists (auto pre-select encoded it on entry); the first point is only encoded here as a
     * fallback when auto-encode failed. Mirrors the media viewer's refine loop.
     */
    fun addCutoutPoint(x: Float, y: Float, isPositive: Boolean) {
        val session = cutoutState.session
        if (session == null) {
            if (cutoutState.isProcessing) return
            val base = _targetBitmap.value ?: return
            val media = activeMedia.value ?: return
            viewModelScope.launch(Dispatchers.Default) {
                cutoutState.isProcessing = true
                try {
                    val newSession = CutoutHelper.CutoutSession(context, media, modelManager)
                    val ok = newSession.initAndRunEncoder(base)
                    if (ok) {
                        val point = CutoutHelper.PromptPoint(x = x, y = y, isPositive = true)
                        val points = listOf(point)
                        val result = newSession.runDecoder(points)
                        if (result != null) {
                            cutoutState.initSession(newSession, points)
                            cutoutState.updateResult(result, null)
                        } else {
                            newSession.close()
                            toastCutout(R.string.cutout_no_object)
                        }
                    } else {
                        newSession.close()
                        toastCutout(R.string.cutout_init_failed)
                    }
                } finally {
                    cutoutState.isProcessing = false
                }
            }
        } else {
            val newPoint = CutoutHelper.PromptPoint(x = x, y = y, isPositive = isPositive)
            val previousPoints = cutoutState.promptPoints
            val updatedPoints = cutoutState.promptPoints + newPoint
            cutoutState.pushPoints(updatedPoints)
            viewModelScope.launch(Dispatchers.Default) {
                cutoutState.isProcessing = true
                try {
                    val res = session.runDecoder(updatedPoints)
                    val newCache = cutoutState.result?.let { Pair(previousPoints, it) }
                    cutoutState.updateResult(res, newCache)
                } finally {
                    cutoutState.isProcessing = false
                }
            }
        }
    }

    fun undoCutout() = navigateCutout(-1)
    fun redoCutout() = navigateCutout(1)

    private fun navigateCutout(delta: Int) {
        val pts = cutoutState.navigateHistory(delta) ?: return
        val session = cutoutState.session ?: return
        viewModelScope.launch(Dispatchers.Default) {
            cutoutState.isProcessing = true
            try {
                val res = session.runDecoder(pts.second)
                val newCache = cutoutState.result?.let { Pair(pts.first, it) }
                cutoutState.updateResult(res, newCache)
            } finally {
                cutoutState.isProcessing = false
            }
        }
    }

    fun resetCutout() {
        cutoutState.clearPoints()
    }

    fun cancelCutout() {
        cutoutState.dismiss()
    }

    /** Bake the current mask into the recipe as a background-removal [Cutout] adjustment. */
    fun applyCutoutAsEdit() {
        val result = cutoutState.result ?: return
        val base = _targetBitmap.value ?: return
        val mask = Bitmap.createBitmap(base.width, base.height, Bitmap.Config.ARGB_8888)
        Canvas(mask).drawBitmap(
            result.bitmap,
            null,
            Rect(
                result.originalBounds.left,
                result.originalBounds.top,
                result.originalBounds.right,
                result.originalBounds.bottom
            ),
            null
        )
        cutoutState.dismiss()
        applyAdjustment(Cutout(mask))
    }

    fun cutoutCopy() = exportCutout { CutoutHelper.copyToClipboard(context, it) }
    fun cutoutShare() = exportCutout { CutoutHelper.shareCutout(context, it) }
    fun cutoutSaveNew() = exportCutout { CutoutHelper.saveToGallery(context, it) }

    private fun exportCutout(action: suspend (Bitmap) -> Unit) {
        val bmp = cutoutState.result?.bitmap ?: return
        viewModelScope.launch(Dispatchers.IO) { action(bmp) }
    }

    private suspend fun toastCutout(resId: Int) = withContext(Dispatchers.Main) {
        Toast.makeText(context, context.getString(resId), Toast.LENGTH_SHORT).show()
    }

    private val _originalBitmap = MutableStateFlow<Bitmap?>(null)
    val originalBitmap = _originalBitmap.asStateFlow()

    private val _targetBitmap = MutableStateFlow(originalBitmap.value)
    val targetBitmap = _targetBitmap.asStateFlow()

    private val _previewMatrix = MutableStateFlow<ColorMatrix?>(null)
    val previewMatrix = _previewMatrix.asStateFlow()

    private val _previewRotation = MutableStateFlow(0f)
    val previewRotation = _previewRotation.asStateFlow()

    private val _previewRotation90 = MutableStateFlow(0f)
    val previewRotation90 = _previewRotation90.asStateFlow()

    private val _previewFlipH = MutableStateFlow(false)
    val previewFlipH = _previewFlipH.asStateFlow()

    private val bitmaps = mutableStateListOf<Pair<Bitmap?, Adjustment?>>()

    private val _currentBitmap = MutableStateFlow<Bitmap?>(null)
    val currentBitmap = _currentBitmap.asStateFlow()

    private val _appliedAdjustments = MutableStateFlow<List<Adjustment>>(emptyList())
    val appliedAdjustments = _appliedAdjustments.asStateFlow()

    private val activeMedia = MutableStateFlow<UriMedia?>(null)
    private val sourceInitializationMutex = Mutex()
    private var initializedSource: Uri? = null
    private val saveGuard = EditorSaveGuard()

    private val _isSaving = MutableStateFlow(true)
    val isSaving = _isSaving.asStateFlow()

    /**
     * Save/bake progress in 0..1 while a full-resolution tiled bake streams, or `null` when the
     * current work has no measurable progress (indeterminate — e.g. the whole-bitmap fallback or the
     * initial load). The UI shows a determinate ring + percentage when non-null.
     */
    private val _saveProgress = MutableStateFlow<Float?>(null)
    val saveProgress = _saveProgress.asStateFlow()

    /**
     * One-shot signal emitted when the source image can't be loaded/decoded for editing. The
     * emitted [Boolean] is `true` when a recoverable original backup exists for this media, so the
     * activity can offer to restore it instead of just closing. The activity observes this so a
     * failed load never leaves the editor stuck on the loading overlay (which is gated on
     * [isSaving]).
     */
    private val _loadFailed = MutableStateFlow<Boolean?>(null)
    val loadFailed = _loadFailed.asStateFlow()

    /**
     * Emits [loadFailed] with whether a recoverable original backup exists for the active media, so
     * the activity can prompt the user to restore it when the current (possibly corrupted) file
     * can't be opened.
     */
    private suspend fun emitLoadFailed() {
        val hasBackup = activeMedia.value?.id?.let { editBackupManager.hasOriginalBackup(it) } ?: false
        _loadFailed.value = hasBackup
    }

    fun clearLoadFailure() {
        _loadFailed.value = null
    }

    private val _canOverride = MutableStateFlow(false)
    val canOverride = _canOverride.asStateFlow()

    private val _hasOriginalBackup = MutableStateFlow(false)
    val hasOriginalBackup = _hasOriginalBackup.asStateFlow()

    private val _isReverting = MutableStateFlow(false)
    val isReverting = _isReverting.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing = _isProcessing.asStateFlow()

    // ── RAW develop integration ───────────────────────────────────────────────
    /** Cached RAW bytes (read once), EXIF-derived orientation, and the tone-neutral base bitmap. */
    private var rawBytes: ByteArray? = null
    private var rawUserFlip: Int = -1
    private var rawBaseBitmap: Bitmap? = null

    /** The live develop recipe when editing a RAW (null for non-RAW media). */
    private val _rawDevelopParams = MutableStateFlow<RawDevelopParams?>(null)
    val rawDevelopParams = _rawDevelopParams.asStateFlow()

    /** True while the current edit session is a RAW being developed in-editor. */
    private val _isRawEdit = MutableStateFlow(false)
    val isRawEdit = _isRawEdit.asStateFlow()

    /** True when the RAW develop recipe deviates from the neutral default (drives the Save pill). */
    val isRawModified: Boolean
        get() = _isRawEdit.value && _rawDevelopParams.value?.let { it != RawDevelopParams.AUTO } == true

    private var rawToneJob: Job? = null
    private val rawDevelopMutex = Mutex()
    private var rawDevelopGeneration = 0L

    private data class SaveSnapshot(
        val media: UriMedia,
        val adjustments: List<Adjustment>,
        val proxyOriginal: Bitmap?,
        val liveResult: Bitmap?,
        val isRawEdit: Boolean,
        val rawBytes: ByteArray?,
        val rawParams: RawDevelopParams?,
        val rawUserFlip: Int,
    )

    private fun captureSaveSnapshot(flattenColor: Int? = null): SaveSnapshot? {
        val media = activeMedia.value ?: return null
        val flatten = flattenColor?.let(::FlattenBackground)
        val liveResult = lastRealBitmap()
        return SaveSnapshot(
            media = media,
            adjustments = _appliedAdjustments.value.toList() + listOfNotNull(flatten),
            proxyOriginal = _originalBitmap.value,
            liveResult = flatten?.apply(liveResult ?: return null) ?: liveResult,
            isRawEdit = _isRawEdit.value,
            rawBytes = rawBytes,
            rawParams = _rawDevelopParams.value,
            rawUserFlip = rawUserFlip,
        )
    }

    private val _uri = MutableStateFlow<Uri?>(null)
    val uri = _uri.asStateFlow()

    private val _paths = MutableStateFlow<List<Pair<Path, PathProperties>>>(emptyList())
    val paths = _paths.asStateFlow()

    private val _pathsUndone = MutableStateFlow<List<Pair<Path, PathProperties>>>(emptyList())
    val pathsUndone = _pathsUndone.asStateFlow()

    private val _currentPosition = MutableStateFlow(Offset.Unspecified)
    val currentPosition = _currentPosition.asStateFlow()

    private val _previousPosition = MutableStateFlow(Offset.Unspecified)
    val previousPosition = _previousPosition.asStateFlow()

    private val _drawMode = MutableStateFlow(DrawMode.Draw)
    val drawMode = _drawMode.asStateFlow()

    private val _drawType = MutableStateFlow(DrawType.Stylus)
    val drawType = _drawType.asStateFlow()

    private val _currentPath = MutableStateFlow(Path())
    val currentPath = _currentPath.asStateFlow()

    private val _currentPathProperty = MutableStateFlow(PathProperties())
    val currentPathProperty = _currentPathProperty.asStateFlow()

    private val _selectedPreset = MutableStateFlow<SuggestionPreset?>(null)
    val selectedPreset = _selectedPreset.asStateFlow()

    private val redoStack = mutableStateListOf<Pair<Bitmap?, Adjustment?>>()
    private val _redoAdjustments = MutableStateFlow<List<Adjustment>>(emptyList())

    private val _canUndo = MutableStateFlow(false)
    val canUndo = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo = _canRedo.asStateFlow()

    private val _filterIntensity = MutableStateFlow(1f)
    val filterIntensity = _filterIntensity.asStateFlow()

    private val _previewVignette = MutableStateFlow(0f)
    val previewVignette = _previewVignette.asStateFlow()

    private val _previewBlur = MutableStateFlow(0f)
    val previewBlur = _previewBlur.asStateFlow()

    private val _previewSharpness = MutableStateFlow(0f)
    val previewSharpness = _previewSharpness.asStateFlow()

    private val _activeFilterFlow = MutableStateFlow<ImageFilter?>(null)
    val activeFilter = _activeFilterFlow.asStateFlow()
    private val _hasPendingFilter = MutableStateFlow(false)
    val hasPendingFilter = _hasPendingFilter.asStateFlow()
    private var _activeFilter: ImageFilter?
        get() = _activeFilterFlow.value
        set(value) { _activeFilterFlow.value = value }
    private var _previewJob: Job? = null
    private var _intensityJob: Job? = null

    private fun updateUndoRedoState() {
        _canUndo.value = _appliedAdjustments.value.isNotEmpty()
        _canRedo.value = _redoAdjustments.value.isNotEmpty()
    }

    private fun Adjustment.isMatrixBased(): Boolean = when (this) {
        is VariableFilter -> colorMatrix() != null
        is ImageFilter -> colorMatrix() != null
        else -> false
    }

    private fun Adjustment.getColorMatrix(): ColorMatrix? = when (this) {
        is VariableFilter -> colorMatrix()
        is ImageFilter -> colorMatrix()
        else -> null
    }

    /** Find the last real bitmap in the bitmaps stack */
    private fun lastRealBitmap(): Bitmap? =
        bitmaps.lastOrNull { it.first != null }?.first

    /** Recompute the composed matrix from all trailing matrix-only entries */
    private fun recomputeComposedMatrix() {
        val trailing = bitmaps.takeLastWhile { it.first == null }
        if (trailing.isEmpty()) {
            _previewMatrix.value = null
            return
        }
        val composed = identityColorMatrix()
        for ((_, adj) in trailing) {
            adj?.getColorMatrix()?.let { composed.timesAssign(it) }
        }
        _previewMatrix.value = composed
    }

    /** Get a temporary bitmap with composed matrix applied (for previews, doesn't modify state) */
    private fun bitmapWithComposedMatrix(): Bitmap? {
        val base = lastRealBitmap() ?: return null
        val matrix = _previewMatrix.value ?: return base
        return applyColorMatrix(base, matrix.values)
    }

    /** Bake the composed matrix into a real bitmap checkpoint */
    private suspend fun flattenComposedMatrix() {
        val matrix = _previewMatrix.value ?: return
        val base = lastRealBitmap() ?: return
        val trailing = bitmaps.takeLastWhile { it.first == null }
        if (trailing.isEmpty()) return
        val flattened = applyColorMatrix(base, matrix.values)
        // Remove all trailing null-bitmap entries and replace with one real bitmap
        repeat(trailing.size) { bitmaps.removeAt(bitmaps.lastIndex) }
        bitmaps.add(flattened to null) // null adjustment = flatten checkpoint
        _currentBitmap.value = flattened
        _targetBitmap.value = flattened
        // Clear preview on Main so the UI renders the flattened bitmap first
        withContext(Dispatchers.Main) {
            _previewMatrix.value = null
        }
    }

    /**
     * Builds the re-encode config from settings, folding in a best-effort estimate of the source's
     * original quality (JPEG only) so overwrites in AUTO mode match the original fidelity.
     */
    private fun reencodeConfigForSource(media: UriMedia? = activeMedia.value): ImageReencoder.ReencodeConfig {
        val detected = media?.let { m ->
            runCatching {
                val prefix = context.contentResolver.openInputStream(m.uri)?.use { input ->
                    val buf = ByteArray(256 * 1024)
                    val read = input.read(buf)
                    if (read <= 0) null else buf.copyOf(read)
                }
                prefix?.let { SourceQualityProbe.detect(it, m.mimeType) }
            }.getOrNull()
        }
        return Settings.Misc.getReencodeConfig(context, detected)
    }

    private fun clearRedoStack() {
        redoStack.clear()
        _redoAdjustments.value = emptyList()
        updateUndoRedoState()
    }

    private val mutex = Mutex()

    fun addPath(path: Path, properties: PathProperties) {
        _paths.value += path to properties
    }

    fun clearPathsUndone() {
        _pathsUndone.value = emptyList()
    }

    fun setCurrentPosition(offset: Offset) {
        _currentPosition.value = offset
    }

    fun setPreviousPosition(offset: Offset) {
        _previousPosition.value = offset
    }

    fun setDrawMode(mode: DrawMode) {
        setCurrentPathProperty(
            _currentPathProperty.value.copy(
                eraseMode = mode == DrawMode.Erase
            )
        )
        _drawMode.value = mode
    }

    fun setDrawType(type: DrawType) {
        if (_drawMode.value == DrawMode.Draw && _drawType.value == type) return
        when (type) {
            DrawType.Stylus -> {
                setCurrentPathProperty(
                    _currentPathProperty.value.copy(
                        strokeWidth = 20f,
                        color = _currentPathProperty.value.color.copy(alpha = 1f),
                        strokeCap = StrokeCap.Round,
                        brush = MarkupBrush.Solid,
                        fillRegion = false
                    )
                )
            }

            DrawType.Highlighter -> {
                setCurrentPathProperty(
                    _currentPathProperty.value.copy(
                        strokeWidth = 30f,
                        color = _currentPathProperty.value.color.copy(alpha = 0.4f),
                        strokeCap = StrokeCap.Square,
                        brush = MarkupBrush.Solid,
                        fillRegion = false
                    )
                )
            }

            DrawType.Marker -> {
                setCurrentPathProperty(
                    _currentPathProperty.value.copy(
                        strokeWidth = 40f,
                        color = _currentPathProperty.value.color.copy(alpha = 1f),
                        strokeCap = StrokeCap.Round,
                        brush = MarkupBrush.Solid,
                        fillRegion = false
                    )
                )
            }

            DrawType.Blur -> {
                setCurrentPathProperty(
                    _currentPathProperty.value.copy(
                        strokeWidth = 60f,
                        strokeCap = StrokeCap.Round,
                        brush = MarkupBrush.Blur,
                        fillRegion = false
                    )
                )
            }

            DrawType.Mosaic -> {
                setCurrentPathProperty(
                    _currentPathProperty.value.copy(
                        strokeWidth = 60f,
                        strokeCap = StrokeCap.Round,
                        brush = MarkupBrush.Mosaic,
                        fillRegion = false
                    )
                )
            }
        }
        _drawType.value = type
    }

    fun setCurrentPath(path: Path) {
        _currentPath.value = path
    }

    fun setCurrentPathProperty(properties: PathProperties) {
        _currentPathProperty.value = properties
    }

    fun setSelectedPreset(preset: SuggestionPreset?) {
        _selectedPreset.value = preset
        if (preset != null) {
            // Compose preset preview with existing stacked matrix adjustments
            val trailing = bitmaps.toList().takeLastWhile { it.first == null }
            val composed = identityColorMatrix()
            for ((_, adj) in trailing) {
                adj?.getColorMatrix()?.let { composed.timesAssign(it) }
            }
            composed.timesAssign(preset.colorMatrix())
            _previewMatrix.value = composed
        } else {
            recomputeComposedMatrix()
        }
    }

    fun undoLastPath() {
        val paths = _paths.value
        if (paths.isNotEmpty()) {
            val lastPath = paths.last()
            _paths.value = paths.dropLast(1)
            _pathsUndone.value += lastPath
        }
    }

    fun redoLastPath() {
        val pathsUndone = _pathsUndone.value
        if (pathsUndone.isNotEmpty()) {
            val lastPath = pathsUndone.last()
            _pathsUndone.value = pathsUndone.dropLast(1)
            _paths.value += lastPath
        }
    }

    fun clearDrawingBoard() {
        faceDetectionJob?.cancel()
        faceDetectionJob = null
        _pendingFaceRegions.value = emptyList()
        _isDetectingFaces.value = false
        _paths.value = emptyList()
        _pathsUndone.value = emptyList()
        _currentPath.value = Path()
        _currentPathProperty.value = PathProperties()
        _drawMode.value = DrawMode.Draw
        _drawType.value = DrawType.Stylus
    }

    fun setSourceData(context: Context, uri: Uri, forceReload: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            sourceInitializationMutex.withLock {
                // The activity composition is recreated on configuration changes while this ViewModel
                // survives. Do not reinstall the same base: RAW's installBase clears its recipe and the
                // normal path would prepend another base beneath the existing recipe.
                if (!forceReload && initializedSource == uri) return@withLock
                initializedSource = null
                _loadFailed.value = null
                _isSaving.value = true
                _saveProgress.value = null
                rawToneJob?.cancel()
                rawBytes = null
                rawBaseBitmap = null
                rawUserFlip = -1
                _rawDevelopParams.value = null
                _isRawEdit.value = false
                try {
                    _uri.value = uri
                    val mediaList =
                        repository.getMediaListByUris(listOf(uri), reviewMode = false, onlyMatching = true).firstOrNull()?.data
                            ?: emptyList()
                    _canOverride.value = mediaList.isNotEmpty()
                    if (mediaList.isNotEmpty()) {
                        activeMedia.value = mediaList.first()
                        _hasOriginalBackup.value = editBackupManager.hasOriginalBackup(mediaList.first().id)
                    } else {
                        activeMedia.value = Media.createFromUri(context, uri)
                        _hasOriginalBackup.value = false
                    }

                    if (setOriginalBitmap(context)) initializedSource = uri
                } catch (e: Exception) {
                    initializedSource = null
                    printError("Editor failed to load source: ${e.message}")
                    _isSaving.value = false
                    emitLoadFailed()
                }
            }
        }
    }

    private suspend fun setOriginalBitmap(context: Context): Boolean {
        return try {
            // RAW: demosaic a bounded base and develop it in-editor instead of loading the
            // embedded JPEG preview. Falls through to the Glide path when native RAW is
            // unavailable or the decode fails.
            if (setupRawBase()) return true
            val mediaUri = activeMedia.value?.uri
                ?: throw IllegalStateException("No media uri to load")
            // Decode a memory-safe PROXY (bounded to the screen) for interactive editing instead of
            // the full-resolution original — decoding a huge image into a single bitmap is what used
            // to OOM/stall the editor on the loading screen. Full resolution is preserved at save
            // time by replaying the adjustment recipe onto the original (see bakeFullRes).
            val proxyDim = proxyMaxDim()
            val result = Glide.with(context)
                .asBitmap()
                .load(mediaUri)
                .downsample(DownsampleStrategy.AT_MOST)
                .override(proxyDim, proxyDim)
                .skipMemoryCache(true)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .submit()
                .get()
            // Guarantee the proxy bound even if a custom format decoder ignored the override.
            val bitmap = if (result.width > proxyDim || result.height > proxyDim) {
                resizeBitmap(result, proxyDim, proxyDim)
            } else {
                result
            }
            installBase(bitmap)
            true
        } catch (e: Exception) {
            printError("Editor failed to decode bitmap: ${e.message}")
            _isSaving.value = false
            emitLoadFailed()
            false
        }
    }

    /** Longest-edge cap for the interactive editing proxy, derived from the device screen. */
    private fun proxyMaxDim(): Int {
        val metrics = context.resources.displayMetrics
        return maxOf(metrics.widthPixels, metrics.heightPixels).coerceIn(1080, 2560)
    }

    // ── RAW develop ───────────────────────────────────────────────────────────

    /**
     * When the active media is a native-decodable RAW, reads the bytes once, demosaics a bounded
     * tone-neutral base, applies the stored develop recipe, and installs it as the editor base.
     * Returns false (so the caller falls back to the embedded-preview path) for non-RAW media or
     * when native RAW is unavailable / the decode fails.
     */
    private suspend fun setupRawBase(): Boolean {
        val media = activeMedia.value ?: return false
        if (!media.isRaw || !NativeRawDecoder.isAvailable) return false
        val bytes = runCatching {
            context.contentResolver.openInputStream(media.uri)?.use { it.readBytes() }
        }.getOrNull() ?: return false
        rawBytes = bytes
        rawUserFlip = RawOrientation.libRawUserFlip(bytes)
        val params = RawDevelopStore.paramsFor(media.id)
        val base = NativeRawDecoder.demosaic(bytes, rawProxyParams(params).baseOnly, rawUserFlip)
            ?: return false
        val bounded = boundProxy(base)
        rawBaseBitmap = bounded
        _rawDevelopParams.value = params
        _isRawEdit.value = true
        val developed = developWithTone(bounded, params)
        installBase(developed)
        return true
    }

    /** Auto half-size the proxy demosaic for very large sensors to bound decode time/memory. */
    private fun rawProxyParams(params: RawDevelopParams): RawDevelopParams {
        val size = rawBytes?.let { NativeRawDecoder.getSize(it) } ?: return params
        val large = size.width.toLong() * size.height.toLong() > RawRegionDecoder.AUTO_HALFSIZE_PIXELS
        return if (large && !params.halfSize) params.copy(halfSize = true) else params
    }

    /** Downscale a demosaiced bitmap to the interactive proxy cap when it exceeds it. */
    private fun boundProxy(bmp: Bitmap): Bitmap {
        val proxyDim = proxyMaxDim()
        return if (bmp.width > proxyDim || bmp.height > proxyDim) resizeBitmap(bmp, proxyDim, proxyDim) else bmp
    }

    /** Apply the tone stage to a base bitmap (no-op copy when the recipe has no tone). */
    private fun developWithTone(base: Bitmap, params: RawDevelopParams): Bitmap =
        if (params.hasTone) NativeRawDecoder.applyTone(base, params) ?: base else base

    /** Reset the editor's stack to a fresh single-checkpoint base (used on load / develop change). */
    private fun installBase(base: Bitmap) {
        _originalBitmap.value = base
        _targetBitmap.value = base
        _currentBitmap.value = base
        bitmaps.clear()
        bitmaps.add(base to null)
        _appliedAdjustments.value = emptyList()
        clearRedoStack()
        _activeFilter = null
        _filterIntensity.value = 1f
        _hasPendingFilter.value = false
        _previewMatrix.value = null
        _isSaving.value = false
        updateUndoRedoState()
    }

    /**
     * Live develop update. Tone-only changes re-tone the cached base instantly (debounced, no
     * re-demosaic); base changes (white balance, exposure, demosaic algo, colour space, highlight,
     * noise reduction, half-size) re-demosaic with a brief spinner. Either way the recorded
     * crop/filter/markup recipe is replayed on top so prior edits are preserved.
     */
    fun updateRawDevelop(newParams: RawDevelopParams) {
        val old = _rawDevelopParams.value ?: return
        val generation = ++rawDevelopGeneration
        _rawDevelopParams.value = newParams
        activeMedia.value?.id?.let { RawDevelopStore.update(it, newParams) }
        rawToneJob?.cancel()
        if (newParams.sharesBaseWith(old)) {
            rawToneJob = viewModelScope.launch(Dispatchers.Default) {
                delay(40) // debounce rapid slider ticks
                if (!isActive) return@launch
                _isProcessing.value = true
                try {
                    rawDevelopMutex.withLock {
                        if (generation == rawDevelopGeneration) regenerateDeveloped(newParams)
                    }
                } finally {
                    if (generation == rawDevelopGeneration) _isProcessing.value = false
                }
            }
        } else {
            rawToneJob = viewModelScope.launch(Dispatchers.IO) {
                delay(180) // debounce so dragging a base slider (exposure/WB) doesn't thrash decodes
                if (!isActive) return@launch
                _isProcessing.value = true
                try {
                    rawDevelopMutex.withLock {
                        val bytes = rawBytes
                        if (bytes != null) {
                            val base = NativeRawDecoder.demosaic(
                                bytes,
                                rawProxyParams(newParams).baseOnly,
                                rawUserFlip
                            )
                            val latest = _rawDevelopParams.value
                            if (base != null &&
                                (generation == rawDevelopGeneration || latest?.sharesBaseWith(newParams) == true)
                            ) {
                                rawBaseBitmap = boundProxy(base)
                            } else if (base != null && !base.isRecycled) {
                                base.recycle()
                            }
                        }
                        if (generation == rawDevelopGeneration) regenerateDeveloped(newParams)
                    }
                } finally {
                    if (generation == rawDevelopGeneration) _isProcessing.value = false
                }
            }
        }
    }

    /** Reset the develop recipe to neutral. */
    fun resetRawDevelop() {
        if (!_isRawEdit.value) return
        activeMedia.value?.id?.let { RawDevelopStore.reset(it) }
        updateRawDevelop(RawDevelopParams.AUTO)
    }

    /** Re-tone the cached base and rebuild the stack with the recorded adjustments replayed. */
    private suspend fun regenerateDeveloped(params: RawDevelopParams) {
        val base = rawBaseBitmap ?: return
        val developed = developWithTone(base, params)
        mutex.withLock {
            commitFilterAndWait()
            rebuildStackWithNewBase(developed)
            clearRedoStack()
        }
    }

    /**
     * Swap the editor base to [developed] and replay the recorded adjustments as fresh checkpoints
     * so undo/redo and the applied-adjustment recipe survive a develop change.
     */
    private suspend fun rebuildStackWithNewBase(developed: Bitmap) {
        flattenComposedMatrix() // bake any pending matrix preview so every entry is a real bitmap
        rebuildStack(developed, _appliedAdjustments.value, updateOriginal = true)
    }

    private suspend fun rebuildStack(
        base: Bitmap,
        adjustments: List<Adjustment>,
        updateOriginal: Boolean = false,
    ) {
        val newStack = mutableListOf<Pair<Bitmap?, Adjustment?>>(base to null)
        var current = base
        for (adjustment in adjustments) {
            current = adjustment.apply(current)
            newStack.add(current to adjustment)
        }
        withContext(Dispatchers.Main) {
            bitmaps.clear()
            bitmaps.addAll(newStack)
            if (updateOriginal) _originalBitmap.value = base
            _appliedAdjustments.value = adjustments
            _currentBitmap.value = current
            _targetBitmap.value = current
            _previewMatrix.value = null
            _previewRotation.value = 0f
            _previewRotation90.value = 0f
            _previewFlipH.value = false
            clearGpuPreviewEffects()
            updateUndoRedoState()
        }
    }

    private suspend fun rebuildStack(adjustments: List<Adjustment>) {
        _originalBitmap.value?.let { rebuildStack(it, adjustments) }
    }

    /**
     * Accurate cached thumbnail of the current RAW developed with [params] (used by the Develop tab
     * to preview each option). Returns null for non-RAW sessions or when the decode fails.
     */
    suspend fun rawOptionThumbnail(params: RawDevelopParams): Bitmap? {
        val bytes = rawBytes ?: return null
        val id = activeMedia.value?.id ?: return null
        return RawThumbnailCache.getOrCompute(id, bytes, rawUserFlip, params)
    }

    override fun onCleared() {
        super.onCleared()
        cutoutState.dismiss()
        rawToneJob?.cancel()
        RawThumbnailCache.clear()
    }

    /**
     * True when replaying the recorded recipe on the proxy original reproduces the live proxy result
     * exactly. The fidelity guard shared by every full-res save path: a `false` means the recipe is
     * incomplete and we must fail safe rather than write a different-looking file.
     */
    private fun recipeIsFaithful(snapshot: SaveSnapshot): Boolean {
        val proxyOriginal = snapshot.proxyOriginal ?: return true
        val liveResult = snapshot.liveResult ?: return true
        return EditReplay.matchesLiveResult(proxyOriginal, snapshot.adjustments, liveResult)
    }

    /** The native scanline (JPEG/PNG) streaming format for [writeFormat], or null. */
    private fun streamFormatFor(
        writeFormat: ImageReencoder.ImageWriteFormat
    ): TiledBakeEngine.StreamFormat? = when (writeFormat) {
        ImageReencoder.ImageWriteFormat.JPEG -> TiledBakeEngine.StreamFormat.JPEG
        ImageReencoder.ImageWriteFormat.PNG -> TiledBakeEngine.StreamFormat.PNG
        else -> null
    }

    /** The native tiled HEIF grid format id for [writeFormat], or null. */
    private fun heifFormatFor(writeFormat: ImageReencoder.ImageWriteFormat): Int? = when (writeFormat) {
        ImageReencoder.ImageWriteFormat.HEIC -> NativeHeifEncoder.FORMAT_HEIC
        ImageReencoder.ImageWriteFormat.AVIF -> NativeHeifEncoder.FORMAT_AVIF
        else -> null
    }

    /**
     * Returns a writer that streams the full-res result straight into a native tiled/scanline
     * encoder writing to a file descriptor — never holding the whole output bitmap — or `null` when
     * no native encoder applies. Requires: a supported format, the native lib available, the recipe
     * faithful (proxy parity), and a geometry-free, normal-EXIF, tiled-decodable source.
     */
    private fun streamingWriter(
        snapshot: SaveSnapshot,
        writeFormat: ImageReencoder.ImageWriteFormat,
        config: ImageReencoder.ReencodeConfig,
    ): ((Int) -> Boolean)? {
        if (snapshot.isRawEdit) return null // RAW has no source encoder / tiled decode; use bakeFullRes
        val mediaUri = snapshot.media.uri
        val adjustments = snapshot.adjustments
        if (!recipeIsFaithful(snapshot)) return null
        if (!TiledBakeEngine.isStreamEligible(context, mediaUri, adjustments)) return null

        val onProgress: (Float) -> Unit = { _saveProgress.value = it }

        streamFormatFor(writeFormat)?.let { fmt ->
            if (NativeImageEncoder.isAvailable) {
                return { fd ->
                    TiledBakeEngine.bakeToStream(
                        context, mediaUri, adjustments, fmt, config.effectiveLossyQuality, fd, onProgress
                    )
                }
            }
        }
        heifFormatFor(writeFormat)?.let { hfmt ->
            if (NativeHeifEncoder.isAvailable) {
                return { fd ->
                    TiledBakeEngine.bakeToHeif(
                        context, mediaUri, adjustments, hfmt, config.effectiveLossyQuality, fd, onProgress
                    )
                }
            }
        }
        return null
    }

    private suspend fun bakeFullRes(context: Context, snapshot: SaveSnapshot): Bitmap? {
        // RAW: demosaic the full recipe (base + tone) at full resolution, then replay the
        // crop/filter/markup recipe on top. RAW isn't tiled-decodable, so this is the only path.
        if (snapshot.isRawEdit) {
            val bytes = snapshot.rawBytes ?: return null
            val params = snapshot.rawParams ?: return null
            val src = NativeRawDecoder.demosaic(bytes, params, snapshot.rawUserFlip) ?: return null
            return try {
                EditReplay.replay(src, snapshot.adjustments)
            } catch (e: Exception) {
                printError("Full-res RAW bake replay failed: ${e.message}")
                if (!src.isRecycled) src.recycle()
                null
            }
        }
        val mediaUri = snapshot.media.uri
        val adjustments = snapshot.adjustments
        if (!recipeIsFaithful(snapshot)) {
            printError("Full-res bake aborted: recorded recipe does not match the live proxy result")
            return null
        }
        // Prefer the memory-bounded tiled bake (never decodes the whole source into one bitmap).
        // It returns null for recipes/sources it can't guarantee, in which case we fall back to the
        // verified whole-image replay below.
        try {
            TiledBakeEngine.bake(context, mediaUri, adjustments)?.let { return it }
        } catch (e: Exception) {
            printError("Tiled bake failed, falling back to whole-image replay: ${e.message}")
        }
        val fullRes = try {
            Glide.with(context)
                .asBitmap()
                .load(mediaUri)
                .skipMemoryCache(true)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .submit()
                .get()
        } catch (e: Exception) {
            printError("Full-res decode for bake failed: ${e.message}")
            return null
        }
        return try {
            EditReplay.replay(fullRes, adjustments)
        } catch (e: Exception) {
            printError("Full-res bake replay failed: ${e.message}")
            if (!fullRes.isRecycled) fullRes.recycle()
            null
        }
    }

    fun removeLast() {
        if (_isProcessing.value) return
        _isProcessing.value = true
        viewModelScope.launch(Dispatchers.IO) {
            mutex.withLock {
                try {
                    commitFilterAndWait()
                    val adjustments = _appliedAdjustments.value
                    if (adjustments.isNotEmpty()) {
                        val removedAdj = adjustments.last()
                        _appliedAdjustments.value = adjustments.dropLast(1)

                        // Push to redo stack
                        if (bitmaps.isNotEmpty()) {
                            val removedEntry = bitmaps.last()
                            redoStack.add(removedEntry)
                            _redoAdjustments.value = _redoAdjustments.value + removedAdj
                            bitmaps.removeAt(bitmaps.lastIndex)
                        }

                        // Update current bitmap to last real bitmap
                        _currentBitmap.value = lastRealBitmap()
                        _targetBitmap.value = _currentBitmap.value
                        _previewMatrix.value = null

                        // If we undid a filter, check if there's a previous filter underneath
                        if (removedAdj is ImageFilter) {
                            val prevFilter = _appliedAdjustments.value
                                .filterIsInstance<ImageFilter>()
                                .lastOrNull()
                            _activeFilter = if (prevFilter != null && prevFilter.name != "None") prevFilter else null
                            _filterIntensity.value = 1f
                        }

                        updateUndoRedoState()
                    }
                } finally {
                    _isProcessing.value = false
                }
            }
        }
    }

    fun redoLast() {
        if (_isProcessing.value) return
        _isProcessing.value = true
        viewModelScope.launch(Dispatchers.IO) {
            mutex.withLock {
                try {
                    val redoAdjs = _redoAdjustments.value
                    if (redoAdjs.isNotEmpty() && redoStack.isNotEmpty()) {
                        val restoredAdj = redoAdjs.last()
                        val restoredEntry = redoStack.last()
                        _redoAdjustments.value = redoAdjs.dropLast(1)
                        redoStack.removeAt(redoStack.lastIndex)

                        _appliedAdjustments.value = _appliedAdjustments.value + restoredAdj
                        bitmaps.add(restoredEntry)
                        _currentBitmap.value = lastRealBitmap()
                        _targetBitmap.value = _currentBitmap.value
                        _previewMatrix.value = null
                        updateUndoRedoState()
                    }
                } finally {
                    _isProcessing.value = false
                }
            }
        }
    }

    fun setFilterIntensity(intensity: Float) {
        _filterIntensity.value = intensity.coerceIn(0f, 1f)
        if (_activeFilter == null) return
        _hasPendingFilter.value = recipeWithPendingFilter() != _appliedAdjustments.value
        if (_hasPendingFilter.value) {
            renderPendingFilterPreview(delayMillis = 32L)
        } else {
            _previewJob?.cancel()
            _currentBitmap.value = lastRealBitmap()
        }
    }

    private fun identityColorMatrix(): ColorMatrix = ColorMatrix(floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    ))

    private fun lerpColorMatrix(from: ColorMatrix, to: ColorMatrix?, t: Float): ColorMatrix {
        if (to == null) return from
        val result = FloatArray(20)
        for (i in 0 until 20) {
            result[i] = from.values[i] + t * (to.values[i] - from.values[i])
        }
        return ColorMatrix(result)
    }

    fun removeKind(variableFilterTypes: VariableFilterTypes) {
        if (_isProcessing.value) return
        _isProcessing.value = true
        viewModelScope.launch(Dispatchers.IO) {
            mutex.withLock {
                try {
                    commitFilterAndWait()
                    val adjustments = _appliedAdjustments.value.filterNot {
                        it.name.equals(variableFilterTypes.name, ignoreCase = true)
                    }
                    rebuildStack(adjustments)
                    clearRedoStack()
                } finally {
                    _isProcessing.value = false
                }
            }
        }
    }

    fun applyAdjustment(adjustment: Adjustment) {
        if (_isProcessing.value) return
        _isProcessing.value = true
        viewModelScope.launch(Dispatchers.IO) {
            mutex.withLock {
                try {
                    commitFilterAndWait()
                    applyAdjustmentAndWait(adjustment)
                } finally {
                    _isProcessing.value = false
                }
            }
        }
    }

    private suspend fun applyAdjustmentAndWait(adjustment: Adjustment) {
        _isProcessing.value = true
        printDebug("Applying adjustment: $adjustment")
        val filters = _appliedAdjustments.value
        clearRedoStack()

        // Update applied-adjustments list (dedup same-kind for VariableFilter / ImageFilter).
        // adjustmentsWithout is the list with any previous entry of this kind removed; it is
        // also what we fall back to when the new adjustment turns out to be a no-op (#957/#961).
        val updatedRecipe = updatedEditorRecipe(filters, adjustment)
        val replacesExistingAdjustment = when (adjustment) {
            is VariableFilter -> filters.any { it.name.equals(adjustment.name, ignoreCase = true) } ||
                    kotlin.math.abs(adjustment.value - adjustment.defaultValue) < 1e-4f
            is ImageFilter -> filters.any { it is ImageFilter }
            else -> false
        }
        if (replacesExistingAdjustment) {
            rebuildStack(updatedRecipe)
            _isProcessing.value = false
            return
        }
        val adjustmentsWithout = filters

        // Always create a new bitmap (original behaviour)
        _currentBitmap.value?.let {
            if (adjustment is ImageFilter) {
                bitmaps.removeAll { entry -> entry.second is ImageFilter }
                _targetBitmap.value = bitmaps.lastOrNull()?.first
            }
            if (adjustment is VariableFilter) {
                bitmaps.removeAll { entry -> entry.second?.name.equals(adjustment.name, ignoreCase = true) }
                _targetBitmap.value = bitmaps.lastOrNull()?.first
            }

            val baseBitmap =
                if (adjustment is VariableFilter || adjustment is ImageFilter)
                    _targetBitmap.value ?: _originalBitmap.value ?: it
                else
                    bitmaps.lastOrNull()?.first ?: it

            val newBitmap = adjustment.apply(baseBitmap)
            // No-op guard: drop adjustments that produce no visible change so the tool icon
            // stops showing as "modified" (#957) and identical actions don't stack on the
            // undo/revert history (#961). A variable filter scrubbed back to its default
            // value is treated as a no-op directly: pixel comparison alone is unreliable
            // because some filters aren't perfectly pixel-identical at their default and,
            // when this is the only filter, the base falls back to the already-filtered
            // bitmap. Falling back to adjustmentsWithout removes the filter entirely.
            if (newBitmap.sameAs(baseBitmap)) {
                if (newBitmap !== baseBitmap && !newBitmap.isRecycled) newBitmap.recycle()
                _appliedAdjustments.value = adjustmentsWithout
                withContext(Dispatchers.Main) {
                    _currentBitmap.value = baseBitmap
                    _previewMatrix.value = null
                    if (adjustment is Rotate) _previewRotation.value = 0f
                    if (adjustment is Rotate90CW) _previewRotation90.value = 0f
                    if (adjustment is Flip) _previewFlipH.value = false
                    clearGpuPreviewEffects()
                }
                updateUndoRedoState()
                _isProcessing.value = false
                return
            }
            _appliedAdjustments.value = adjustmentsWithout + adjustment
            _currentBitmap.value = newBitmap
            if (adjustment !is ImageFilter) {
                _targetBitmap.value = newBitmap
            }
            bitmaps.add(newBitmap to adjustment)
            // Clear previews on Main after bitmap is set, so the UI
            // renders the new bitmap before the preview overlay disappears
            withContext(Dispatchers.Main) {
                _previewMatrix.value = null
                if (adjustment is Rotate) {
                    _previewRotation.value = 0f
                }
                if (adjustment is Rotate90CW) {
                    _previewRotation90.value = 0f
                }
                if (adjustment is Flip) {
                    _previewFlipH.value = false
                }
                clearGpuPreviewEffects()
            }
        } ?: printError("Current bitmap is null")

        updateUndoRedoState()
        _isProcessing.value = false
    }

    fun applyRotate90() {
        // Instant GPU preview, then bake
        _previewRotation90.value += 90f
        applyAdjustment(Rotate90CW(90f))
    }

    fun applyFlipH() {
        // Instant GPU preview, then bake
        _previewFlipH.value = !_previewFlipH.value
        applyAdjustment(Flip(horizontal = true))
    }

    fun applyDrawing(graphicsImage: Bitmap, onFinish: (Boolean) -> Unit) {
        if (_isProcessing.value) {
            onFinish(false)
            return
        }
        _isProcessing.value = true
        viewModelScope.launch(Dispatchers.IO) {
            mutex.withLock {
                try {
                    commitFilterAndWait()
                    // Flatten any pending matrix adjustments before markup
                    flattenComposedMatrix()
                    val currentImage = lastRealBitmap()
                    if (currentImage == null) {
                        withContext(Dispatchers.Main) { onFinish(false) }
                        return@withLock
                    }
                    if (currentImage.width > 0 && currentImage.height > 0) {
                        // graphicsImage is a transparent overlay captured at the markup canvas
                        // resolution. Markup.apply composes it against the base for the no-op
                        // check and stores the raw overlay so the bake engine can re-composite
                        // it onto the full-resolution original (scaling as needed).
                        applyAdjustmentAndWait(Markup(graphicsImage))
                    }
                    clearDrawingBoard()
                    withContext(Dispatchers.Main) { onFinish(true) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    printError("Failed to apply markup: ${e.message}")
                    withContext(Dispatchers.Main) { onFinish(false) }
                } finally {
                    _isProcessing.value = false
                }
            }
        }
    }

    fun toggleFilter(filter: ImageFilter) {
        _intensityJob?.cancel()
        _activeFilter = filter.takeUnless { it.name == "None" }
        _filterIntensity.value = 1f
        _hasPendingFilter.value = recipeWithPendingFilter() != _appliedAdjustments.value
        _previewMatrix.value = null
        clearGpuPreviewEffects()
        if (_hasPendingFilter.value) {
            renderPendingFilterPreview()
        } else {
            _currentBitmap.value = lastRealBitmap()
        }
    }

    private fun effectiveFilterAdjustment(): Adjustment? {
        val filter = _activeFilter ?: return null
        val intensity = _filterIntensity.value
        if (filter.name == "None" || intensity <= 0f) return null
        val matrix = if (intensity < 1f) {
            lerpColorMatrix(identityColorMatrix(), filter.colorMatrix(), intensity)
        } else {
            filter.colorMatrix()
        }
        // Record the *effective* operation so the full-res bake reproduces it exactly:
        // an intensity-blended (or full) matrix becomes a MatrixAdjustment; a non-matrix
        // filter is resolution-independent via its own apply() and is recorded as-is.
        return if (matrix != null) {
            MatrixAdjustment(matrix.values.copyOf(), filter.name)
        } else {
            filter
        }
    }

    private fun recipeWithPendingFilter(): List<Adjustment> {
        val current = _appliedAdjustments.value
        return effectiveFilterAdjustment()?.let { updatedEditorRecipe(current, it) }
            ?: current.filterNot { it is ImageFilter }
    }

    private fun renderPendingFilterPreview(delayMillis: Long = 0L) {
        val base = _originalBitmap.value ?: return
        val recipe = recipeWithPendingFilter()
        _previewJob?.cancel()
        _previewJob = viewModelScope.launch(Dispatchers.Default) {
            if (delayMillis > 0L) delay(delayMillis)
            val result = EditReplay.replay(base, recipe)
            try {
                withContext(Dispatchers.Main) {
                    if (_hasPendingFilter.value) {
                        _currentBitmap.value = result
                    } else if (result !== base && !result.isRecycled) {
                        result.recycle()
                    }
                }
            } catch (e: CancellationException) {
                if (result !== base && !result.isRecycled) result.recycle()
                throw e
            }
        }
    }

    /**
     * Bake the currently previewed filter into a real bitmap.
     * Called when navigating away from the Filters section.
     */
    fun commitFilter() {
        if (!_hasPendingFilter.value || _isProcessing.value) return
        _isProcessing.value = true
        viewModelScope.launch(Dispatchers.IO) {
            mutex.withLock {
                try {
                    commitFilterAndWait()
                } finally {
                    _isProcessing.value = false
                }
            }
        }
    }

    private suspend fun commitFilterAndWait() {
        if (!_hasPendingFilter.value) return
        _previewJob?.cancel()
        _intensityJob?.cancel()
        val adjustment = effectiveFilterAdjustment()
        val adjustments = recipeWithPendingFilter()
        if (adjustment == null) _activeFilter = null
        rebuildStack(adjustments)
        _previewMatrix.value = null
        _hasPendingFilter.value = false
        clearRedoStack()
    }

    private fun clearGpuPreviewEffects() {
        _previewVignette.value = 0f
        _previewBlur.value = 0f
        _previewSharpness.value = 0f
    }

    /**
     * Cached downscaled copy of a base bitmap used to keep non-matrix
     * filter previews (Posterize, Edges, Borders) responsive while scrubbing.
     */
    private var previewBaseCache: Pair<Bitmap, Bitmap>? = null

    private fun previewBaseFor(base: Bitmap): Bitmap {
        previewBaseCache?.let { (original, scaled) ->
            if (original === base) return scaled
        }
        val scaled = if (base.width > 1280 || base.height > 1280) {
            resizeBitmap(base, 1280, 1280)
        } else base
        previewBaseCache = base to scaled
        return scaled
    }

    fun previewAdjustment(adjustment: Adjustment) {
        _previewJob?.cancel()
        if (adjustment is VariableFilter) {
            val adjustments = _appliedAdjustments.value
            val existingIndex = adjustments.indexOfFirst {
                it.name.equals(adjustment.name, ignoreCase = true)
            }
            if (existingIndex in 0 until adjustments.lastIndex) {
                val base = _originalBitmap.value ?: return
                val recipe = updatedEditorRecipe(adjustments, adjustment)
                _previewMatrix.value = null
                clearGpuPreviewEffects()
                _previewJob = viewModelScope.launch(Dispatchers.Default) {
                    delay(48)
                    val result = EditReplay.replay(base, recipe)
                    try {
                        withContext(Dispatchers.Main) { _currentBitmap.value = result }
                    } catch (e: CancellationException) {
                        if (result !== base && !result.isRecycled) result.recycle()
                        throw e
                    }
                }
                return
            }
        }
        when {
            adjustment is Vignette -> {
                // Show base bitmap without existing vignette, then overlay GPU preview
                val baseBitmap = bitmaps.toList()
                    .filter { !it.second?.name.equals(adjustment.name, ignoreCase = true) }
                    .lastOrNull()?.first
                _currentBitmap.value = baseBitmap ?: lastRealBitmap()
                _previewVignette.value = adjustment.value
                _previewBlur.value = 0f
                _previewSharpness.value = 0f
            }
            adjustment is Denoise -> {
                val baseBitmap = bitmaps.toList()
                    .filter { !it.second?.name.equals(adjustment.name, ignoreCase = true) }
                    .lastOrNull()?.first
                _currentBitmap.value = baseBitmap ?: lastRealBitmap()
                _previewBlur.value = adjustment.value
                _previewVignette.value = 0f
                _previewSharpness.value = 0f
            }
            adjustment is Sharpness -> {
                val baseBitmap = bitmaps.toList()
                    .filter { !it.second?.name.equals(adjustment.name, ignoreCase = true) }
                    .lastOrNull()?.first
                _currentBitmap.value = baseBitmap ?: lastRealBitmap()
                _previewSharpness.value = adjustment.value
                _previewVignette.value = 0f
                _previewBlur.value = 0f
            }
            adjustment is Rotate -> {
                _previewRotation.value = adjustment.value
            }
            adjustment is VariableFilter && adjustment.colorMatrix() != null -> {
                // Show base bitmap (without this adjustment)
                // and apply only this adjustment's colorMatrix as GPU filter
                val baseBitmap = bitmaps.toList()
                    .filter { !it.second?.name.equals(adjustment.name, ignoreCase = true) }
                    .lastOrNull()?.first
                _currentBitmap.value = baseBitmap ?: lastRealBitmap()
                _previewMatrix.value = adjustment.colorMatrix()
                clearGpuPreviewEffects()
            }
            adjustment is VariableFilter -> {
                // Non-matrix variable filters (Posterize, Edges, Borders): render apply()
                // on a downscaled copy of the base bitmap so scrubbing stays responsive.
                val baseBitmap = bitmaps.toList()
                    .filter { !it.second?.name.equals(adjustment.name, ignoreCase = true) }
                    .lastOrNull()?.first ?: lastRealBitmap()
                _previewMatrix.value = null
                clearGpuPreviewEffects()
                if (baseBitmap == null || adjustment.value == adjustment.defaultValue) {
                    _currentBitmap.value = baseBitmap
                } else {
                    _previewJob = viewModelScope.launch(Dispatchers.Default) {
                        // Debounce rapid scrubber ticks: a newer tick cancels this job
                        // during the delay, so only the latest value is rendered. This
                        // prevents out-of-order results from flickering on screen.
                        delay(48)
                        if (!isActive) return@launch
                        val preview = previewBaseFor(baseBitmap)
                        val result = adjustment.apply(preview)
                        if (!isActive) return@launch
                        withContext(Dispatchers.Main) {
                            _currentBitmap.value = result
                        }
                    }
                }
            }
            else -> {
                clearGpuPreviewEffects()
            }
        }
    }

    private fun launchSingleFlightSave(
        onSuccess: () -> Unit,
        onFail: () -> Unit,
        operation: suspend () -> Boolean,
    ) {
        // Acquire and publish the blocked UI state synchronously, closing the tap/edit race before
        // the IO coroutine starts. A concurrent request is consumed rather than queued.
        if (!saveGuard.tryAcquire()) return
        _isSaving.value = true
        _saveProgress.value = null
        viewModelScope.launch(Dispatchers.IO) {
            val succeeded = try {
                mutex.withLock { commitFilterAndWait() }
                operation()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                printError("Editor save failed: ${e.message}")
                false
            } finally {
                // Every exit path (success, failure or cancellation) restores the complete save UI
                // state and opens the gate for a deliberate retry.
                _saveProgress.value = null
                _isSaving.value = false
                saveGuard.release()
            }
            if (succeeded) onSuccess() else onFail()
        }
    }

    private suspend fun applyModifiedDatePolicy(uri: Uri, media: Media.UriMedia, mimeType: String) {
        val updateModifiedDate = Settings.Album.updateModifiedDate(context).firstOrNull() ?: false
        selectedModifiedTimestamp(
            updateModifiedDate = updateModifiedDate,
            sourceDateModified = media.timestamp,
            currentTimeSeconds = System.currentTimeMillis() / 1000L
        )?.let { timestamp -> context.restoreMediaTimestamp(uri, mimeType, timestamp) }
    }

    fun saveCopy(
        forcePng: Boolean = false,
        flattenColor: Int? = null,
        onSuccess: () -> Unit = {},
        onFail: () -> Unit = {}
    ) {
        launchSingleFlightSave(onSuccess, onFail) {
            // Flatten pending previews before taking one immutable recipe/source snapshot for every
            // branch of this save. UI input is blocked while this operation owns the save guard.
            flattenComposedMatrix()
            val snapshot = captureSaveSnapshot(flattenColor) ?: return@launchSingleFlightSave false
            val output = EditorOutputPolicy.copy(
                sourceMime = snapshot.media.mimeType,
                sourceLabel = snapshot.media.label,
                forcePng = forcePng,
            )
            val writeFormat = output.writeFormat
            val config = reencodeConfigForSource(snapshot.media)
            val relativePath = Environment.DIRECTORY_PICTURES + "/Edited"

            // Fast path: stream the full-res result straight into a native tiled/scanline encoder.
            val streamWriter = streamingWriter(snapshot, writeFormat, config)
            if (streamWriter != null) {
                val streamedUri = context.contentResolver.saveImageStreaming(
                    mimeType = writeFormat.mimeType,
                    relativePath = relativePath,
                    displayName = output.displayName,
                    write = streamWriter,
                )
                if (streamedUri != null) {
                    applyModifiedDatePolicy(streamedUri, snapshot.media, writeFormat.mimeType)
                    return@launchSingleFlightSave true
                }
                _saveProgress.value = null
            }

            val bitmap = bakeFullRes(context, snapshot) ?: return@launchSingleFlightSave false
            try {
                val savedUri = mediaHandler.saveImage(
                    bitmap = bitmap,
                    writeFormat = writeFormat,
                    config = config,
                    relativePath = relativePath,
                    displayName = output.displayName,
                    mimeType = writeFormat.mimeType,
                )
                if (savedUri != null) applyModifiedDatePolicy(savedUri, snapshot.media, writeFormat.mimeType)
                savedUri != null
            } finally {
                if (!bitmap.isRecycled) bitmap.recycle()
            }
        }
    }

    /**
     * Saves a developed RAW copy into `Pictures/Edited` in the chosen [format], leaving the original
     * RAW untouched. JPEG/PNG bake the full editor recipe (develop + crop/filters/markup) at full
     * resolution; TIFF (8/16-bit) is streamed straight from LibRaw at full bit depth and reflects
     * the develop recipe only (the UI restricts TIFF to sessions without post-develop adjustments).
     */
    fun saveRawCopy(
        format: RawSaveFormat,
        onSuccess: () -> Unit = {},
        onFail: () -> Unit = {},
    ) {
        launchSingleFlightSave(onSuccess, onFail) {
            flattenComposedMatrix()
            val snapshot = captureSaveSnapshot() ?: return@launchSingleFlightSave false
            val bytes = snapshot.rawBytes ?: return@launchSingleFlightSave false
            val params = snapshot.rawParams ?: return@launchSingleFlightSave false
            val displayName = EditorOutputPolicy.rawCopy(snapshot.media.label, format)
            val relativePath = Environment.DIRECTORY_PICTURES + "/Edited"

            if (format.isTiff) {
                val savedUri = context.contentResolver.saveImageStreaming(
                    mimeType = format.mimeType,
                    relativePath = relativePath,
                    displayName = displayName,
                ) { fd ->
                    NativeRawDecoder.exportTiff(
                        bytes,
                        params,
                        fd,
                        bits = format.bits,
                        userFlip = snapshot.rawUserFlip,
                    )
                }
                if (savedUri != null) applyModifiedDatePolicy(savedUri, snapshot.media, format.mimeType)
                return@launchSingleFlightSave savedUri != null
            }

            // JPEG/PNG: bake the full recipe (develop + crop/filters/markup) onto the full-res image.
            val writeFormat = if (format == RawSaveFormat.JPEG) {
                ImageReencoder.ImageWriteFormat.JPEG
            } else {
                ImageReencoder.ImageWriteFormat.PNG
            }
            val config = reencodeConfigForSource(snapshot.media)
            val bitmap = bakeFullRes(context, snapshot) ?: return@launchSingleFlightSave false
            try {
                val savedUri = mediaHandler.saveImage(
                    bitmap = bitmap,
                    writeFormat = writeFormat,
                    config = config,
                    relativePath = relativePath,
                    displayName = displayName,
                    mimeType = writeFormat.mimeType,
                )
                if (savedUri != null) applyModifiedDatePolicy(savedUri, snapshot.media, writeFormat.mimeType)
                savedUri != null
            } finally {
                if (!bitmap.isRecycled) bitmap.recycle()
            }
        }
    }

    /**
     * Overwrite the source file in place, re-encoded in its own format. When the source format has
     * no Android encoder (RAW/TIFF/PSD/JP2/SVG/animated), the original is left untouched and
     * [onNeedsCopyFallback] is invoked so the UI can offer to create a copy instead.
     */
    fun saveOverride(
        flattenColor: Int? = null,
        onNeedsCopyFallback: () -> Unit = {},
        onSuccess: () -> Unit = {},
        onFail: () -> Unit = {}
    ) {
        if (_isSaving.value) return
        val initialMedia = activeMedia.value ?: return
        if (ImageReencoder.formatForMime(initialMedia.mimeType, initialMedia.label) == null) {
            // Source format can't be re-encoded in place — defer to the copy fallback flow.
            onNeedsCopyFallback()
            return
        }
        launchSingleFlightSave(onSuccess, onFail) {
            flattenComposedMatrix()
            val snapshot = captureSaveSnapshot(flattenColor) ?: return@launchSingleFlightSave false
            val media = snapshot.media
            val writeFormat = ImageReencoder.formatForMime(media.mimeType, media.label)
                ?: return@launchSingleFlightSave false
            val config = reencodeConfigForSource(media)

            // Fast path: encode to a cache staging file, then replace only after a successful,
            // non-empty encode. The source remains readable throughout the encode.
            val streamWriter = streamingWriter(snapshot, writeFormat, config)
            if (streamWriter != null) {
                val backedUp = editBackupManager.backupOriginal(
                    mediaId = media.id,
                    uri = media.uri,
                    mimeType = media.mimeType,
                )
                if (!backedUp) return@launchSingleFlightSave false
                val stagingFile = runCatching {
                    java.io.File.createTempFile("edit_override_", ".tmp", context.cacheDir)
                }.getOrNull() ?: return@launchSingleFlightSave false
                if (context.contentResolver.overrideImageStreaming(media.uri, stagingFile, streamWriter)) {
                    applyModifiedDatePolicy(media.uri, media, writeFormat.mimeType)
                    _hasOriginalBackup.value = true
                    evictImageCaches(media.uri)
                    return@launchSingleFlightSave true
                }
                _saveProgress.value = null
            }

            val bitmap = bakeFullRes(context, snapshot) ?: return@launchSingleFlightSave false
            try {
                // Backup original before overriding (preserves the first original).
                val backedUp = editBackupManager.backupOriginal(
                    mediaId = media.id,
                    uri = media.uri,
                    mimeType = media.mimeType,
                )
                if (!backedUp) return@launchSingleFlightSave false

                val overridden = mediaHandler.overrideImage(
                    uri = media.uri,
                    bitmap = bitmap,
                    writeFormat = writeFormat,
                    config = config,
                    relativePath = Environment.DIRECTORY_PICTURES + "/Edited",
                    displayName = media.label,
                    mimeType = writeFormat.mimeType,
                )
                if (overridden) {
                    applyModifiedDatePolicy(media.uri, media, writeFormat.mimeType)
                    _hasOriginalBackup.value = true
                    evictImageCaches(media.uri)
                }
                overridden
            } finally {
                if (!bitmap.isRecycled) bitmap.recycle()
            }
        }
    }

    /**
     * Remove all Sketch memory-cache entries for [uri]. Cache keys are built from the request URI
     * (plus size/extras), so filtering by the URI string catches the viewer's preview + full-res
     * painters as well as the zoomimage subsampling tiles regardless of the (possibly stale)
     * mediaVersion extra derived from the not-yet-refreshed Media object.
     */
    private fun evictImageCaches(uri: Uri) {
        runCatching {
            val uriString = uri.toString()
            val memoryCache = context.sketch.memoryCache
            memoryCache.keys()
                .filter { it.contains(uriString) }
                .forEach { memoryCache.remove(it) }
        }.onFailure { printError("Failed to evict image caches: ${it.message}") }
    }

    fun revertToOriginal(
        onSuccess: () -> Unit = {},
        onFail: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isReverting.value = true
            val media = activeMedia.value
            if (media == null) {
                _isReverting.value = false
                onFail()
                return@launch
            }
            try {
                val workId = workManager.revertEditBackup(media.id)
                workManager.getWorkInfoByIdFlow(workId).collect { info ->
                    if (info == null) return@collect
                    when (info.state) {
                        WorkInfo.State.SUCCEEDED -> {
                            val success = info.outputData.getBoolean(
                                EditBackupWorker.KEY_SUCCESS, false
                            )
                            if (success) {
                                _hasOriginalBackup.value = false
                                _isReverting.value = false
                                // Revert restores the original bytes onto the same URI, so drop the
                                // now-stale edited bitmap from the memory cache too (#1004).
                                evictImageCaches(media.uri)
                                onSuccess()
                            } else {
                                _isReverting.value = false
                                onFail()
                            }
                            return@collect
                        }
                        WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                            _isReverting.value = false
                            onFail()
                            return@collect
                        }
                        else -> { /* ENQUEUED, RUNNING, BLOCKED – keep waiting */ }
                    }
                }
            } catch (e: Exception) {
                printError("Failed to revert: ${e.message}")
                _isReverting.value = false
                onFail()
            }
        }
    }
}
