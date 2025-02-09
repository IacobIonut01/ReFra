package com.dot.gallery.feature_node.presentation.edit.components.editor

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.Constants.Animation.enterAnimation
import com.dot.gallery.core.Constants.Animation.exitAnimation
import com.dot.gallery.feature_node.domain.model.editor.CropState
import com.dot.gallery.feature_node.domain.model.editor.DrawMode
import com.dot.gallery.feature_node.domain.model.editor.PathProperties
import com.dot.gallery.feature_node.presentation.edit.components.markup.MarkupPainter
import com.dot.gallery.feature_node.presentation.util.rememberBitmapPainter
import com.dot.gallery.feature_node.presentation.util.resizeBitmap
import com.dot.gallery.feature_node.presentation.util.safeSystemGesturesPadding
import com.github.panpf.zoomimage.ZoomImage
import com.github.panpf.zoomimage.compose.rememberZoomState
import com.smarttoolfactory.cropper.ImageCropper
import com.smarttoolfactory.cropper.model.AspectRatio
import com.smarttoolfactory.cropper.model.OutlineType
import com.smarttoolfactory.cropper.model.RectCropShape
import com.smarttoolfactory.cropper.settings.CropDefaults
import com.smarttoolfactory.cropper.settings.CropOutlineProperty


@Composable
fun ImageViewer(
    modifier: Modifier = Modifier,
    currentImage: Bitmap?,
    previewMatrix: ColorMatrix?,
    previewRotation: Float,
    cropState: CropState,
    showMarkup: Boolean,
    paths: List<Pair<Path, PathProperties>>,
    currentPosition: Offset,
    previousPosition: Offset,
    drawMode: DrawMode,
    currentPath: Path,
    currentPathProperty: PathProperties,
    isSupportingPanel: Boolean,
    onLongClick: (() -> Unit)? = null,
    onCropStart: () -> Unit = {},
    onCropSuccess: (Bitmap) -> Unit,
    addPath: (Path, PathProperties) -> Unit,
    clearPathsUndone: () -> Unit,
    setCurrentPosition: (Offset) -> Unit,
    setPreviousPosition: (Offset) -> Unit,
    setCurrentPath: (Path) -> Unit,
    setCurrentPathProperty: (PathProperties) -> Unit,
    applyDrawing: (Bitmap, () -> Unit) -> Unit
) {

    val resizedBitmap by remember(currentImage) {
        derivedStateOf {
            currentImage?.let { resizeBitmap(it, 2048, 2048) }
        }
    }

    Box(
        modifier = modifier
            .then(if (!isSupportingPanel) Modifier.padding(top = 16.dp) else Modifier)
            .safeSystemGesturesPadding(onlyLeft = isSupportingPanel)
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                shape = RoundedCornerShape(16.dp)
            )
            .clip(RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            modifier = Modifier.fillMaxSize(),
            visible = resizedBitmap != null && !cropState.showCropper,
            enter = enterAnimation,
            exit = exitAnimation
        ) {
            val painter by rememberBitmapPainter(resizedBitmap!!)
            val zoomState = rememberZoomState()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationZ = previewRotation
                    },
                contentAlignment = Alignment.Center
            ) {
                if (!showMarkup) {
                    ZoomImage(
                        zoomState = zoomState,
                        modifier = Modifier.fillMaxSize(),
                        painter = painter,
                        contentDescription = null,
                        scrollBar = null,
                        colorFilter = previewMatrix?.let { ColorFilter.colorMatrix(it) },
                        onLongPress = { onLongClick?.invoke() }
                    )
                } else {
                    MarkupPainter(
                        painter = painter,
                        paths = paths,
                        addPath = addPath,
                        clearPathsUndone = clearPathsUndone,
                        currentPosition = currentPosition,
                        setCurrentPosition = setCurrentPosition,
                        previousPosition = previousPosition,
                        setPreviousPosition = setPreviousPosition,
                        drawMode = drawMode,
                        currentPath = currentPath,
                        setCurrentPath = setCurrentPath,
                        currentPathProperty = currentPathProperty,
                        setCurrentPathProperty = setCurrentPathProperty,
                        currentImage = currentImage,
                        applyDrawing = applyDrawing
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = cropState.showCropper,
            enter = enterAnimation,
            exit = exitAnimation
        ) {
            val cropProperties = remember {
                CropDefaults.properties(
                    cropOutlineProperty = CropOutlineProperty(
                        outlineType = OutlineType.RoundedRect,
                        cropOutline = RectCropShape(
                            id = 0,
                            title = OutlineType.RoundedRect.name
                        )
                    ),
                    overlayRatio = 1f
                )
            }
            AnimatedContent(
                targetState = (cropProperties.aspectRatio != AspectRatio.Original),
                transitionSpec = { fadeIn(tween(100)) togetherWith fadeOut(tween(100)) },
                modifier = modifier.fillMaxWidth(),
                label = "cropper",
            ) { fixedAspectRatio ->
                ImageCropper(
                    imageBitmap = remember(resizedBitmap) { resizedBitmap!!.asImageBitmap() },
                    contentDescription = null,
                    cropStyle = CropDefaults.style(
                        handleColor = MaterialTheme.colorScheme.tertiary,
                        strokeWidth = 1.dp
                    ),
                    cropProperties = cropProperties.copy(fixedAspectRatio = fixedAspectRatio),
                    crop = cropState.isCropping,
                    onCropStart = onCropStart,
                    onCropSuccess = { image ->
                        onCropSuccess(image.asAndroidBitmap())
                    },
                )
            }
        }
    }
}