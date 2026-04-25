package com.dot.gallery.feature_node.presentation.edit.components.editor

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.Constants.Animation.enterAnimation
import com.dot.gallery.core.Constants.Animation.exitAnimation
import com.dot.gallery.feature_node.domain.model.editor.CropState
import com.dot.gallery.feature_node.domain.model.editor.DrawMode
import com.dot.gallery.feature_node.domain.model.editor.PathProperties
import com.dot.gallery.feature_node.domain.model.editor.TextAnnotation
import com.dot.gallery.feature_node.presentation.edit.components.markup.MarkupPainter
import com.dot.gallery.feature_node.presentation.util.resizeBitmap
import com.dot.gallery.feature_node.presentation.util.safeSystemGesturesPadding
import com.github.panpf.zoomimage.GlideZoomAsyncImage
import com.github.panpf.zoomimage.compose.glide.ExperimentalGlideComposeApi
import com.smarttoolfactory.cropper.ImageCropper
import com.smarttoolfactory.cropper.model.AspectRatio
import com.smarttoolfactory.cropper.model.OutlineType
import com.smarttoolfactory.cropper.model.RectCropShape
import com.smarttoolfactory.cropper.settings.CropDefaults
import com.smarttoolfactory.cropper.settings.CropOutlineProperty


@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun ImageViewer(
    modifier: Modifier = Modifier,
    currentImage: Bitmap?,
    previewMatrix: ColorMatrix?,
    previewRotation: Float,
    cropState: CropState,
    cropAspectRatio: AspectRatio = AspectRatio.Original,
    showGridOverlay: Boolean = false,
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
    onCropRect: (RectF) -> Unit,
    addPath: (Path, PathProperties) -> Unit,
    clearPathsUndone: () -> Unit,
    setCurrentPosition: (Offset) -> Unit,
    setPreviousPosition: (Offset) -> Unit,
    setCurrentPath: (Path) -> Unit,
    setCurrentPathProperty: (PathProperties) -> Unit,
    applyDrawing: (Bitmap, () -> Unit) -> Unit,
    onNavigateBack: () -> Unit = {},
    requestApply: Boolean = false,
    onApplyHandled: () -> Unit = {},
    textAnnotations: List<TextAnnotation> = emptyList(),
    onTextAnnotationsChange: (List<TextAnnotation>) -> Unit = {},
    selectedTextIndex: Int = -1,
    onSelectedTextIndexChange: (Int) -> Unit = {},
    vignetteIntensity: Float = 0f,
    blurRadius: Float = 0f,
    sharpnessValue: Float = 0f,
    previewRotation90: Float = 0f,
    previewFlipH: Boolean = false,
) {

    val resizedBitmap = remember(currentImage) {
        currentImage?.let { resizeBitmap(it, 2048, 2048) }
    }

    // Compose the effective preview matrix: existing previewMatrix + sharpness contrast boost
    val effectiveMatrix = remember(previewMatrix, sharpnessValue) {
        val base = previewMatrix
        if (sharpnessValue <= 0f) base
        else {
            val t = ((sharpnessValue - 3f) / 6f).coerceIn(0f, 1f)
            val contrast = 1f + t * 0.4f
            val translate = (-0.5f * contrast + 0.5f) * 255f
            val sharpMatrix = ColorMatrix(floatArrayOf(
                contrast, 0f, 0f, 0f, translate,
                0f, contrast, 0f, 0f, translate,
                0f, 0f, contrast, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            ))
            if (base != null) {
                sharpMatrix.timesAssign(base)
                sharpMatrix
            } else sharpMatrix
        }
    }


    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerLowest
    val animatedCornerRadius by animateDpAsState(
        targetValue = if (showMarkup) 0.dp else 16.dp,
        animationSpec = tween(350),
        label = "cornerRadius"
    )
    val animatedBgAlpha by animateFloatAsState(
        targetValue = if (showMarkup) 0f else 1f,
        animationSpec = tween(350),
        label = "bgAlpha"
    )
    val animatedTopPadding by animateDpAsState(
        targetValue = if (showMarkup || isSupportingPanel) 0.dp else 16.dp,
        animationSpec = tween(350),
        label = "topPadding"
    )

    Box(
        modifier = modifier
            .padding(top = animatedTopPadding)
            .then(
                if (!showMarkup) Modifier.safeSystemGesturesPadding(onlyLeft = isSupportingPanel)
                else Modifier
            )
            .background(
                color = surfaceColor.copy(alpha = animatedBgAlpha),
                shape = RoundedCornerShape(animatedCornerRadius)
            )
            .clip(RoundedCornerShape(animatedCornerRadius))
            .clipToBounds(),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            modifier = Modifier.fillMaxSize(),
            visible = resizedBitmap != null && !cropState.showCropper,
            enter = enterAnimation,
            exit = exitAnimation
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationZ = previewRotation + previewRotation90
                        scaleX = if (previewFlipH) -1f else 1f
                    },
                contentAlignment = Alignment.Center
            ) {
                if (!showMarkup) {
                    GlideZoomAsyncImage(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (blurRadius > 0f) Modifier.blur(
                                    radiusX = (blurRadius * 2f).dp,
                                    radiusY = (blurRadius * 2f).dp
                                ) else Modifier
                            ),
                        model = resizedBitmap!!,
                        contentDescription = null,
                        scrollBar = null,
                        colorFilter = effectiveMatrix?.let { ColorFilter.colorMatrix(it) },
                        onLongPress = { onLongClick?.invoke() }
                    )
                    // Vignette overlay — separate composable so it doesn't interfere with colorFilter
                    if (vignetteIntensity > 0f) {
                        val bmp = currentImage
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val imgW = bmp?.width?.toFloat() ?: size.width
                            val imgH = bmp?.height?.toFloat() ?: size.height
                            val scale = minOf(size.width / imgW, size.height / imgH)
                            val contentW = imgW * scale
                            val contentH = imgH * scale
                            val contentCx = size.width / 2f
                            val contentCy = size.height / 2f
                            val contentRadius = maxOf(contentW, contentH) / 2f * 1.2f

                            val alpha = (vignetteIntensity * 200f / 255f).coerceIn(0f, 1f)
                            drawRect(
                                brush = Brush.radialGradient(
                                    colorStops = arrayOf(
                                        0.0f to Color.Transparent,
                                        0.5f to Color.Transparent,
                                        1.0f to Color.Black.copy(alpha = alpha)
                                    ),
                                    center = Offset(contentCx, contentCy),
                                    radius = contentRadius,
                                    tileMode = TileMode.Clamp
                                )
                            )
                        }
                    }
                } else {
                    MarkupPainter(
                        bitmap = resizedBitmap!!,
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
                        applyDrawing = applyDrawing,
                        onNavigateBack = onNavigateBack,
                        requestApply = requestApply,
                        onApplyHandled = onApplyHandled,
                        textAnnotations = textAnnotations,
                        onTextAnnotationsChange = onTextAnnotationsChange,
                        selectedTextIndex = selectedTextIndex,
                        onSelectedTextIndexChange = onSelectedTextIndexChange
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = cropState.showCropper && currentImage != null,
            enter = enterAnimation,
            exit = exitAnimation
        ) {
            val bitmap = currentImage ?: return@AnimatedVisibility
            val previewBitmap = remember(bitmap) {
                resizeBitmap(bitmap, 2048, 2048).asImageBitmap()
            }
            AnimatedContent(
                targetState = cropAspectRatio,
                transitionSpec = {
                    fadeIn(tween(100)) togetherWith fadeOut(tween(100))
                },
                modifier = modifier.fillMaxWidth(),
                label = "cropper",
            ) { targetRatio ->
                val props = remember(targetRatio) {
                    CropDefaults.properties(
                        cropOutlineProperty = CropOutlineProperty(
                            outlineType = OutlineType.RoundedRect,
                            cropOutline = RectCropShape(
                                id = 0,
                                title = OutlineType.RoundedRect.name
                            )
                        ),
                        aspectRatio = targetRatio,
                        overlayRatio = 1f,
                        fixedAspectRatio = targetRatio != AspectRatio.Original
                    )
                }
                val effectiveColorFilter = previewMatrix?.let { ColorFilter.colorMatrix(it) }
                ImageCropper(
                    modifier = Modifier.graphicsLayer {
                        if (effectiveColorFilter != null) {
                            // API 31+ RenderEffect for color matrix on cropper content
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                renderEffect = android.graphics.RenderEffect.createColorFilterEffect(
                                    android.graphics.ColorMatrixColorFilter(
                                        android.graphics.ColorMatrix(previewMatrix.values)
                                    )
                                ).asComposeRenderEffect()
                            }
                        }
                    },
                    imageBitmap = previewBitmap,
                    contentDescription = null,
                    cropStyle = CropDefaults.style(
                        drawGrid = showGridOverlay,
                        handleColor = MaterialTheme.colorScheme.tertiary,
                        strokeWidth = 1.dp
                    ),
                    cropProperties = props,
                    crop = cropState.isCropping,
                    onCropStart = onCropStart,
                    onCropSuccess = { },
                    onCropRect = onCropRect,
                )
            }
        }
    }
}