package com.dot.gallery.feature_node.presentation.edit

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.core.graphics.scale
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.Media.UriMedia
import com.dot.gallery.feature_node.domain.model.editor.Adjustment
import com.dot.gallery.feature_node.domain.model.editor.DrawMode
import com.dot.gallery.feature_node.domain.model.editor.DrawType
import com.dot.gallery.feature_node.domain.model.editor.ImageFilter
import com.dot.gallery.feature_node.domain.model.editor.PathProperties
import com.dot.gallery.feature_node.domain.model.editor.SaveFormat
import com.dot.gallery.feature_node.domain.model.editor.VariableFilter
import com.dot.gallery.feature_node.domain.repository.MediaRepository
import com.dot.gallery.feature_node.domain.use_case.MediaHandleUseCase
import com.dot.gallery.feature_node.presentation.edit.adjustments.Markup
import com.dot.gallery.feature_node.presentation.edit.adjustments.varfilter.Rotate
import com.dot.gallery.feature_node.presentation.edit.adjustments.varfilter.VariableFilterTypes
import com.dot.gallery.feature_node.presentation.util.overlayBitmaps
import com.dot.gallery.feature_node.presentation.util.printDebug
import com.dot.gallery.feature_node.presentation.util.printError
import com.github.panpf.sketch.BitmapImage
import com.github.panpf.sketch.request.ImageRequest
import com.github.panpf.sketch.sketch
import com.github.panpf.sketch.util.Size
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

@HiltViewModel
class EditViewModel @Inject constructor(
    private val repository: MediaRepository,
    private val mediaHandler: MediaHandleUseCase,
) : ViewModel() {

    private val _isEditingActive = MutableStateFlow(false)
    val isEditingActive = _isEditingActive.asStateFlow()

    private val _originalBitmap = MutableStateFlow<Bitmap?>(null)
    val originalBitmap = _originalBitmap.asStateFlow()

    private val _targetBitmap = MutableStateFlow(originalBitmap.value)
    val targetBitmap = _targetBitmap.asStateFlow()

    private val _previewMatrix = MutableStateFlow<ColorMatrix?>(null)
    val previewMatrix = _previewMatrix.asStateFlow()

    private val _previewRotation = MutableStateFlow(0f)
    val previewRotation = _previewRotation.asStateFlow()

    private val bitmaps = mutableStateListOf<Pair<Bitmap, Adjustment?>>()

    private val _currentBitmap = MutableStateFlow<Bitmap?>(null)
    val currentBitmap = _currentBitmap.asStateFlow()

    private val _appliedAdjustments = MutableStateFlow<List<Adjustment>>(emptyList())
    val appliedAdjustments = _appliedAdjustments.asStateFlow()

    private val activeMedia = MutableStateFlow<UriMedia?>(null)

    private val _isSaving = MutableStateFlow(true)
    val isSaving = _isSaving.asStateFlow()

    private val _canOverride = MutableStateFlow(false)
    val canOverride = _canOverride.asStateFlow()

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


    val mutex = Mutex()

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
        when (type) {
            DrawType.Stylus -> {
                setCurrentPathProperty(
                    _currentPathProperty.value.copy(
                        strokeWidth = 20f,
                        color = _currentPathProperty.value.color.copy(alpha = 1f),
                        strokeCap = StrokeCap.Round
                    )
                )
            }

            DrawType.Highlighter -> {
                setCurrentPathProperty(
                    _currentPathProperty.value.copy(
                        strokeWidth = 30f,
                        color = _currentPathProperty.value.color.copy(alpha = 0.4f),
                        strokeCap = StrokeCap.Square
                    )
                )
            }

            DrawType.Marker -> {
                setCurrentPathProperty(
                    _currentPathProperty.value.copy(
                        strokeWidth = 40f,
                        color = _currentPathProperty.value.color.copy(alpha = 1f),
                        strokeCap = StrokeCap.Round
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

    private fun clearDrawingBoard() {
        _paths.value = emptyList()
        _pathsUndone.value = emptyList()
        _currentPath.value = Path()
        _currentPathProperty.value = PathProperties()
        _drawMode.value = DrawMode.Draw
    }

    fun setSourceData(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _uri.value = uri
            val mediaList =
                repository.getMediaListByUris(listOf(uri), reviewMode = false).firstOrNull()?.data
                    ?: emptyList()
            _canOverride.value = mediaList.isNotEmpty()
            if (mediaList.isNotEmpty()) {
                activeMedia.value = mediaList.first()
            } else {
                activeMedia.value = Media.createFromUri(context, uri)
            }

            setOriginalBitmap(context)
        }
    }

    private fun setOriginalBitmap(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val request = ImageRequest(context, activeMedia.value?.uri.toString()) {
                size(Size.Origin)
                setExtra(
                    key = "mediaKey",
                    value = activeMedia.value.toString(),
                )
            }
            val result = context.sketch.execute(request)
            val bitmap = (result.image as? BitmapImage)?.bitmap
            _originalBitmap.value = bitmap
            _targetBitmap.value = bitmap
            if (_currentBitmap.value == null) {
                _currentBitmap.value = bitmap
            }
            bitmaps.add(0, bitmap!! to null)
            _isSaving.value = false
        }
    }

    fun removeLast() {
        viewModelScope.launch(Dispatchers.IO) {
            val adjustments = _appliedAdjustments.value
            if (adjustments.isNotEmpty()) {
                _appliedAdjustments.value = adjustments.dropLast(1)
            }

            // Remove last bitmap
            bitmaps.removeAt(bitmaps.lastIndex)

            // Set current bitmap to the last bitmap
            _currentBitmap.value = bitmaps.lastOrNull()?.first
            _targetBitmap.value = currentBitmap.value
        }
    }

    fun removeKind(variableFilterTypes: VariableFilterTypes) {
        viewModelScope.launch(Dispatchers.IO) {
            val filters = _appliedAdjustments.value.toMutableList()
            filters.removeAll { it.name == variableFilterTypes.name }
            bitmaps.removeAll { it.second?.name == variableFilterTypes.name }
            _appliedAdjustments.value = filters
            _currentBitmap.value = bitmaps.lastOrNull()?.first
            _targetBitmap.value = currentBitmap.value
        }
    }

    fun applyAdjustment(adjustment: Adjustment) {
        viewModelScope.launch(Dispatchers.IO) {
            printDebug("Applying adjustment: $adjustment")
            val filters = _appliedAdjustments.value
            _appliedAdjustments.value = filters + adjustment
            _currentBitmap.value?.let {
                if (adjustment is ImageFilter) {
                    _targetBitmap.value = bitmaps.lastOrNull()?.first
                }
                if (adjustment is VariableFilter) {
                    _targetBitmap.value = bitmaps.toMutableList().apply {
                        removeAll { change -> change.second?.name == adjustment.name }
                    }.lastOrNull()?.first
                }
                val newBitmap: Bitmap =
                    if (adjustment is VariableFilter || adjustment is ImageFilter) {
                        adjustment.apply(targetBitmap.value!!)
                    } else adjustment.apply(bitmaps.lastOrNull()?.first ?: it)
                _currentBitmap.value = newBitmap
                if (adjustment !is ImageFilter) {
                    _targetBitmap.value = newBitmap
                }
                bitmaps.add(newBitmap to adjustment)
                if (adjustment is VariableFilter && adjustment.colorMatrix() != null) {
                    _previewMatrix.value = null
                }
                if (adjustment is Rotate) {
                    _previewRotation.value = 0f
                }
            } ?: printError("Current bitmap is null")
        }
    }

    private var applyDrawingJob: Job? = null

    fun applyDrawing(graphicsImage: Bitmap, onFinish: () -> Unit) {
        applyDrawingJob?.cancel()
        applyDrawingJob = viewModelScope.launch(Dispatchers.IO) {
            mutex.withLock {
                currentBitmap.value?.let { currentImage ->
                    try {
                        val newWidth = currentImage.width
                        val newHeight = currentImage.height
                        if (newWidth > 0 && newHeight > 0) {
                            val finalBitmap = overlayBitmaps(
                                currentImage,
                                graphicsImage.scale(newWidth, newHeight)
                            )
                            if (!currentImage.sameAs(finalBitmap)) {
                                applyAdjustment(Markup(finalBitmap))
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    clearDrawingBoard()
                    onFinish()
                }
            }
        }

    }

    fun toggleFilter(filter: ImageFilter) {
        viewModelScope.launch(Dispatchers.IO) {
            val filters = _appliedAdjustments.value.toMutableList()
            if (filters.any { it is ImageFilter }) {
                filters.removeAll { it is ImageFilter }
                bitmaps.removeAll { it.second is ImageFilter }

                _appliedAdjustments.value = filters
                if (filter.name != "None") {
                    applyAdjustment(filter)
                } else {
                    _currentBitmap.value = bitmaps.lastOrNull()?.first
                    _targetBitmap.value = currentBitmap.value
                }
            } else {
                applyAdjustment(filter)
            }
        }
    }

    fun previewAdjustment(adjustment: Adjustment) {
        viewModelScope.launch(Dispatchers.IO) {
            if (adjustment is VariableFilter) {
                if (adjustment.colorMatrix() != null) {
                    val bitmap = bitmaps.toMutableList().apply {
                        removeAll { it.second?.name == adjustment.name }
                    }.lastOrNull()?.first
                    _currentBitmap.value = bitmap
                    _previewMatrix.value = adjustment.colorMatrix()
                } else if (adjustment is Rotate) {
                    _previewRotation.value = adjustment.value
                }
            } else {
                _targetBitmap.value?.let {
                    val newBitmap = adjustment.apply(it)
                    _currentBitmap.value = newBitmap
                } ?: printError("Current bitmap is null")
            }
        }
    }

    fun saveCopy(
        saveFormat: SaveFormat = SaveFormat.PNG,
        onSuccess: () -> Unit = {},
        onFail: () -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _isSaving.value = true
            delay(500)
            val media = activeMedia.value!!
            _currentBitmap.value?.let { bitmap ->
                try {
                    if (mediaHandler.saveImage(
                            bitmap = bitmap,
                            format = saveFormat.format,
                            relativePath = Environment.DIRECTORY_PICTURES + "/Edited",
                            displayName = media.label,
                            mimeType = saveFormat.mimeType
                        ) != null
                    ) {
                        onSuccess().also { _isSaving.value = false }
                    } else {
                        onFail().also { _isSaving.value = false }
                    }
                } catch (_: Exception) {
                    _isSaving.value = false
                    onFail().also { _isSaving.value = false }
                }
            } ?: onFail().also { _isSaving.value = false }
        }
    }

    fun saveOverride(
        saveFormat: SaveFormat = SaveFormat.PNG,
        onSuccess: () -> Unit = {},
        onFail: () -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _isSaving.value = true
            delay(500)
            val media = activeMedia.value!!
            _currentBitmap.value?.let { bitmap ->
                try {
                    if (mediaHandler.overrideImage(
                            uri = media.uri,
                            bitmap = bitmap,
                            format = saveFormat.format,
                            relativePath = Environment.DIRECTORY_PICTURES + "/Edited",
                            displayName = media.label,
                            mimeType = saveFormat.mimeType
                        )
                    ) {
                        onSuccess().also { _isSaving.value = false }
                    } else {
                        onFail().also { _isSaving.value = false }
                    }
                } catch (e: Exception) {
                    onFail().also { _isSaving.value = false }
                }
            } ?: onFail().also { _isSaving.value = false }
        }
    }
}
