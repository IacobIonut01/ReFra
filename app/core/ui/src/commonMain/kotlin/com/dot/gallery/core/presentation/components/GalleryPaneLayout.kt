/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.lerp
import com.dot.gallery.ui.theme.GalleryChrome
import kotlin.math.roundToInt

/** True while the gallery shares the window with the viewer pane. */
val LocalGalleryDetailPane = staticCompositionLocalOf { false }

/**
 * True while primary content sits inside an inset, rounded pane (split viewer or floating rail).
 * The pane already owns the status bar inset, so screens must not apply it a second time.
 */
val LocalGalleryPaneChrome = staticCompositionLocalOf { false }

/**
 * True while the floating rail sits on the start edge: it already keeps clear of the cutout and
 * side navigation bar there, so content must not reserve that space again.
 */
val LocalGalleryRailEdge = staticCompositionLocalOf { false }

const val GalleryMasterPaneTag = "Gallery.MasterPane"
const val GalleryDetailPaneTag = "Gallery.DetailPane"

private val PaneSpring = spring<Float>(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)

/**
 * Both slots keep their composition when the window changes size or the panes split. Only their
 * constraints and placement change, so the gallery retains its scroll position and the viewer
 * retains its pager and zoom state.
 *
 * [split] keeps the pane geometry alive (including the viewer's exit fade), while [splitRequested]
 * drives the animation: the gallery narrows as the viewer slides in beside it, and widens again as
 * soon as dismissal starts. The viewer keeps its target width while sliding, so it never reflows.
 *
 * When [onMasterFractionChange] is set, a handle in the gutter resizes the panes live; the new
 * fraction is reported once the drag settles. Double-tapping the handle restores the default.
 */
@Composable
fun GalleryPaneLayout(
    split: Boolean,
    masterContent: @Composable BoxScope.() -> Unit,
    detailContent: @Composable BoxScope.() -> Unit,
    modifier: Modifier = Modifier,
    splitRequested: Boolean = split,
    masterFraction: Float = GalleryChrome.DefaultMasterPaneFraction,
    onMasterFractionChange: ((Float) -> Unit)? = null,
) {
    val target = if (split && splitRequested) 1f else 0f
    val progress = remember { Animatable(target) }
    LaunchedEffect(target) { progress.animateTo(target, PaneSpring) }
    val animating by remember { derivedStateOf { progress.value > 0f } }
    val paneMode = split || animating

    var fraction by remember(masterFraction) { mutableFloatStateOf(masterFraction) }
    // Live drag width in px; null outside a drag so the persisted fraction stays authoritative.
    var dragMasterPx by remember { mutableStateOf<Float?>(null) }
    var windowWidthPx by remember { mutableFloatStateOf(0f) }
    val layoutDirection = LocalLayoutDirection.current

    val paneShape = RoundedCornerShape(GalleryChrome.PaneCornerRadius)
    val masterModifier = if (paneMode) {
        Modifier
            .windowInsetsPadding(WindowInsets.statusBars)
            .clip(paneShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
    } else Modifier
    val detailModifier = if (paneMode) {
        Modifier
            .windowInsetsPadding(WindowInsets.statusBars)
            .clip(paneShape)
    } else Modifier

    CompositionLocalProvider(LocalGalleryDetailPane provides split) {
        Layout(
            modifier = modifier.fillMaxSize(),
            content = {
                CompositionLocalProvider(LocalGalleryPaneChrome provides (paneMode || LocalGalleryPaneChrome.current)) {
                    Box(
                        modifier = masterModifier.testTag(GalleryMasterPaneTag),
                        content = masterContent,
                    )
                }
                Box(
                    modifier = detailModifier.testTag(GalleryDetailPaneTag),
                    content = detailContent,
                )
                if (paneMode && onMasterFractionChange != null) {
                    GalleryPaneHandle(
                        modifier = Modifier.graphicsLayer { alpha = progress.value },
                        fractionPercent = {
                            val w = windowWidthPx
                            val m = dragMasterPx
                            if (w <= 0f) (fraction * 100).roundToInt()
                            else ((m ?: (w * fraction)) / w * 100).roundToInt()
                        },
                        onDrag = { delta ->
                            val w = windowWidthPx
                            if (w > 0f) {
                                val signed = if (layoutDirection == LayoutDirection.Rtl) -delta else delta
                                val start = dragMasterPx ?: currentMasterPx(w, fraction, this)
                                dragMasterPx = (start + signed).coerceIn(minMasterPx(w, this), maxMasterPx(w, this))
                            }
                        },
                        onDragStopped = {
                            val w = windowWidthPx
                            val m = dragMasterPx
                            if (w > 0f && m != null) {
                                fraction = (m / w).coerceIn(0f, 1f)
                                onMasterFractionChange(fraction)
                            }
                            dragMasterPx = null
                        },
                        onSetFraction = { requested ->
                            fraction = requested
                            onMasterFractionChange(requested)
                        },
                        onReset = {
                            dragMasterPx = null
                            fraction = GalleryChrome.DefaultMasterPaneFraction
                            onMasterFractionChange(fraction)
                        },
                    )
                }
            },
        ) { measurables, constraints ->
            val width = constraints.maxWidth
            val height = constraints.maxHeight
            windowWidthPx = width.toFloat()
            val master = measurables[0]
            val detail = measurables[1]
            val handle = measurables.getOrNull(2)
            if (!paneMode) {
                val full = Constraints.fixed(width, height)
                val masterPlaceable = master.measure(full)
                val detailPlaceable = detail.measure(full)
                return@Layout layout(width, height) {
                    masterPlaceable.placeRelative(0, 0)
                    detailPlaceable.placeRelative(0, 0)
                }
            }
            val gap = GalleryChrome.PaneGap.roundToPx()
            val targetMaster = (dragMasterPx ?: currentMasterPx(width.toFloat(), fraction, this))
                .roundToInt()
            val p = progress.value
            val masterWidth = lerp(width.toFloat(), targetMaster.toFloat(), p).roundToInt()
            val detailWidth = (width - targetMaster - gap).coerceAtLeast(0)
            val masterPlaceable = master.measure(Constraints.fixed(masterWidth, height))
            val detailPlaceable = detail.measure(Constraints.fixed(detailWidth, height))
            val handlePlaceable = handle?.measure(
                Constraints.fixed(GalleryChrome.PaneHandleTouchWidth.roundToPx(), height),
            )
            layout(width, height) {
                masterPlaceable.placeRelative(0, 0)
                // The viewer follows the gallery's edge, so it slides instead of resizing.
                detailPlaceable.placeRelative(masterWidth + gap, 0)
                handlePlaceable?.placeRelative(
                    masterWidth + gap / 2 - handlePlaceable.width / 2,
                    0,
                )
            }
        }
    }
}

private fun currentMasterPx(width: Float, fraction: Float, density: Density): Float =
    with(density) { galleryMasterPaneWidth(width.toDp(), fraction).toPx() }

private fun minMasterPx(width: Float, density: Density): Float =
    with(density) { galleryMasterPaneMinWidth(width.toDp()).toPx() }

private fun maxMasterPx(width: Float, density: Density): Float =
    with(density) { galleryMasterPaneMaxWidth(width.toDp()).toPx() }
