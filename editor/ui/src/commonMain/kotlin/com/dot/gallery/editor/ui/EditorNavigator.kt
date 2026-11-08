package com.dot.gallery.editor.ui

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.dot.gallery.editor.core.model.PhotoEdits
import com.dot.gallery.editor.ui.generated.resources.Res
import com.dot.gallery.editor.ui.generated.resources.editor_navigator
import com.dot.gallery.editor.ui.generated.resources.editor_navigator_hint
import org.jetbrains.compose.resources.stringResource
import kotlin.math.min
import kotlin.math.roundToInt

internal const val TAG_NAVIGATOR = "editor.navigator"

private val NAV_WIDTH = 104.dp
private val NAV_HEIGHT = 88.dp

/**
 * The on-canvas minimap: the *same* edited image as the viewport (runtime
 * shader when the canvas uses it, the preview bitmap otherwise — never an
 * untransformed original) inside the floating panel material, with the
 * true visible rectangle stroked over it. A tap or drag on the minimap
 * recentres the viewport on that image point; the pan clamps so the image
 * can never be pushed off screen.
 *
 * The interactive field is a fixed 104×88 dp box — large enough to hit —
 * with the image aspect-fitted inside it. Pointer positions are mapped
 * through that image rect, so taps on the matte clamp to the nearest edge
 * instead of producing a nonsense fraction.
 *
 * [canvasSizePx]/[imageSizePx] are the live canvas box and the displayed
 * output frame — the same values the fit maths run against, so the drawn
 * rectangle is exact. A GPU draw that returns false reports through
 * [onGpuFrameFailed], the same owner failure path the main canvas uses.
 */
@Composable
internal fun EditorNavigator(
    canvasSizePx: Size,
    imageSizePx: Size,
    viewport: EditorViewportState,
    bitmap: ImageBitmap?,
    gpuRenderer: PhotoShaderRenderer?,
    gpuEdits: PhotoEdits?,
    onGpuFrameFailed: (PhotoShaderRenderer) -> Unit,
    modifier: Modifier = Modifier
) {
    if (imageSizePx.width <= 0f || imageSizePx.height <= 0f) return
    val density = LocalDensity.current
    // Aspect-fit the output frame inside the fixed 104×88 dp field.
    val imageDpSize = with(density) {
        val navW = NAV_WIDTH.toPx()
        val navH = NAV_HEIGHT.toPx()
        val scale = min(navW / imageSizePx.width, navH / imageSizePx.height)
        androidx.compose.ui.unit.DpSize(
            (imageSizePx.width * scale).toDp(),
            (imageSizePx.height * scale).toDp()
        )
    }
    val currentViewport by rememberUpdatedState(viewport)
    val currentCanvas by rememberUpdatedState(canvasSizePx)
    val currentImage by rememberUpdatedState(imageSizePx)
    val currentOnGpuFrameFailed by rememberUpdatedState(onGpuFrameFailed)

    /** The aspect-fitted image rect (px) inside this fixed-size box. */
    fun imageRect(boxSize: Size): Rect {
        if (boxSize.width <= 0f || boxSize.height <= 0f) return Rect.Zero
        val scale = min(
            boxSize.width / currentImage.width,
            boxSize.height / currentImage.height
        )
        val w = currentImage.width * scale
        val h = currentImage.height * scale
        return Rect(
            left = (boxSize.width - w) / 2f,
            top = (boxSize.height - h) / 2f,
            right = (boxSize.width - w) / 2f + w,
            bottom = (boxSize.height - h) / 2f + h
        )
    }

    fun recenter(local: Offset, imageRect: Rect) {
        if (imageRect.width <= 0f || imageRect.height <= 0f) return
        // Matte taps clamp onto the nearest edge — the fraction stays sane.
        val fx = ((local.x - imageRect.left) / imageRect.width).coerceIn(0f, 1f)
        val fy = ((local.y - imageRect.top) / imageRect.height).coerceIn(0f, 1f)
        val pan = navigatorPanForCenter(
            canvasWidth = currentCanvas.width,
            canvasHeight = currentCanvas.height,
            imageWidth = currentImage.width,
            imageHeight = currentImage.height,
            zoom = currentViewport.zoom,
            fx = fx,
            fy = fy
        )
        currentViewport.panX = pan.x
        currentViewport.panY = pan.y
    }

    val rectColor = MaterialTheme.colorScheme.primary
    EditorFloatingPanel(
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.testTag(TAG_NAVIGATOR)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(Res.string.editor_navigator),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.Start)
                    .padding(bottom = 4.dp)
            )
            val a11y = stringResource(Res.string.editor_navigator_hint)
            Box(
                modifier = Modifier
                    .size(NAV_WIDTH, NAV_HEIGHT)
                    .clip(RoundedCornerShape(6.dp))
                    .semantics { contentDescription = a11y }
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            recenter(offset, imageRect(size.toSize()))
                        }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            recenter(change.position, imageRect(size.toSize()))
                        }
                    }
                    .drawWithContent {
                        drawContent()
                        val imgRect = imageRect(size)
                        if (imgRect.width <= 0f || imgRect.height <= 0f) {
                            return@drawWithContent
                        }
                        val visible = visibleImageRect(
                            canvasWidth = canvasSizePx.width,
                            canvasHeight = canvasSizePx.height,
                            imageWidth = imageSizePx.width,
                            imageHeight = imageSizePx.height,
                            zoom = viewport.zoom,
                            panX = viewport.panX,
                            panY = viewport.panY
                        )
                        val stroke = 1.5.dp.toPx()
                        val rect = Rect(
                            left = imgRect.left + visible.left * imgRect.width +
                                stroke / 2f,
                            top = imgRect.top + visible.top * imgRect.height +
                                stroke / 2f,
                            right = imgRect.left + visible.right * imgRect.width -
                                stroke / 2f,
                            bottom = imgRect.top + visible.bottom * imgRect.height -
                                stroke / 2f
                        )
                        if (rect.width > 0f && rect.height > 0f) {
                            drawRect(
                                color = rectColor,
                                topLeft = rect.topLeft,
                                size = rect.size,
                                style = Stroke(width = stroke)
                            )
                        }
                    }
            ) {
                // The aspect-fitted image rect — pointer mapping and the
                // visible-rect stroke use the same geometry.
                Box(
                    modifier = Modifier
                        .size(imageDpSize)
                        .align(Alignment.Center)
                ) {
                    when {
                        gpuRenderer != null && gpuEdits != null -> {
                            val renderer by rememberUpdatedState(gpuRenderer)
                            val edits by rememberUpdatedState(gpuEdits)
                            Spacer(
                                Modifier
                                    .matchParentSize()
                                    .drawBehind {
                                        // Same draw path as the canvas's
                                        // ShaderImage; a false lands on the
                                        // owner's shared failure path.
                                        if (!renderer.draw(this, edits)) {
                                            currentOnGpuFrameFailed(renderer)
                                        }
                                    }
                            )
                        }
                        bitmap != null -> {
                            Spacer(
                                Modifier
                                    .matchParentSize()
                                    .drawBehind {
                                        drawImage(
                                            bitmap,
                                            dstSize = IntSize(
                                                size.width.roundToInt(),
                                                size.height.roundToInt()
                                            )
                                        )
                                    }
                            )
                        }
                    }
                }
            }
        }
    }
}
