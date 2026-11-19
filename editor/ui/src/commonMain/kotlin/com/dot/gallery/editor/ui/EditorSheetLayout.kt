@file:OptIn(ExperimentalMaterial3Api::class)

package com.dot.gallery.editor.ui

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredHeightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The drag state for [EditorSheetLayout] — a drop-in for the compact
 * photo sheets' Material3 `SheetState`, backed by foundation's
 * [AnchoredDraggableState] instead.
 *
 * Material3's `BottomSheetScaffold` reads `SheetState.requireOffset()`
 * unguarded inside its own placement whenever the sheet rests at
 * PartiallyExpanded, so any pass that measures before the sheet's anchors
 * initialise (lookahead, a restored or recreated state) crashes with
 * "The offset was read before being initialized". This state never
 * exposes that read: [offsetOrNull] reports null until the first measure
 * anchors the sheet, and commands issued earlier are kept as the target
 * the anchors settle on.
 */
@Stable
internal class EditorSheetState(
    initialValue: SheetValue,
    val enabledValues: Set<SheetValue>,
) {
    init {
        require(SheetValue.Expanded in enabledValues) {
            "Expanded must be one of the enabled values."
        }
        require(initialValue in enabledValues) {
            "The initial value must be one of the enabled values."
        }
    }

    internal val draggable = AnchoredDraggableState(initialValue)

    /**
     * The value the sheet last settled at — the same mapping Material3's
     * `SheetState.currentValue` uses (the draggable's settled value, not
     * the closest anchor passed mid-drag).
     */
    val currentValue: SheetValue
        get() = draggable.settledValue

    /**
     * The anchor the sheet is heading to: the drag target while a
     * gesture or animation runs, otherwise the closest anchor to the
     * offset. Before the first measure this is the recorded initial
     * value.
     */
    val targetValue: SheetValue
        get() = draggable.targetValue

    /**
     * The sheet's top edge in pixels relative to the layout, or null
     * before the first measure anchors it. Safe to read in composition,
     * effects and placement — it never throws.
     */
    val offsetOrNull: Float?
        get() = draggable.offset.takeUnless { it.isNaN() }

    /** Whether an expand/collapse animation is currently running. */
    val isAnimationRunning: Boolean
        get() = draggable.isAnimationRunning

    /**
     * The spec expand/show animations run with — the theme's spatial
     * motion spec, assigned by [EditorSheetLayout] like Material3's
     * `SheetState.showMotionSpec`.
     */
    internal var showMotionSpec: AnimationSpec<Float> =
        AnchoredDraggableDefaults.SnapAnimationSpec

    /**
     * The spec collapse animations run with — the theme's fast effects
     * spec, assigned by [EditorSheetLayout] like Material3's
     * `SheetState.hideMotionSpec`.
     */
    internal var hideMotionSpec: AnimationSpec<Float> =
        AnchoredDraggableDefaults.SnapAnimationSpec

    /**
     * Animate to [SheetValue.Expanded]. Before the sheet's first layout
     * the request is kept as the pending target instead of throwing.
     * A no-op when Expanded is not in [enabledValues].
     */
    suspend fun expand() = animateToIfEnabled(SheetValue.Expanded, showMotionSpec)

    /**
     * Animate to [SheetValue.PartiallyExpanded]. Before the sheet's
     * first layout the request is kept as the pending target instead of
     * throwing. A no-op when PartiallyExpanded is not in [enabledValues].
     */
    suspend fun partialExpand() =
        animateToIfEnabled(SheetValue.PartiallyExpanded, hideMotionSpec)

    /**
     * Animate to [SheetValue.Hidden]. Before the sheet's first layout
     * the request is kept as the pending target instead of throwing.
     * A no-op when Hidden is not in [enabledValues].
     */
    suspend fun hide() = animateToIfEnabled(SheetValue.Hidden, hideMotionSpec)

    private suspend fun animateToIfEnabled(value: SheetValue, spec: AnimationSpec<Float>) {
        if (value !in enabledValues) return
        draggable.animateTo(value, spec)
    }

    /**
     * Run the anchored drag's fling for a nested-scroll gesture — the
     * same settle Material3's sheet connection performs. Returns the
     * velocity the sheet consumed.
     */
    internal suspend fun anchoredDragFling(
        flingBehavior: FlingBehavior,
        initialVelocity: Float,
    ): Float {
        if (draggable.anchors.size == 0) return 0f
        var consumedVelocity = 0f
        draggable.anchoredDrag {
            val scrollScope =
                object : ScrollScope {
                    override fun scrollBy(pixels: Float): Float {
                        val newOffset = newOffsetForDelta(pixels)
                        if (newOffset.isNaN()) return 0f
                        val consumed = newOffset - (offsetOrNull ?: 0f)
                        dragTo(newOffset)
                        return consumed
                    }
                }
            consumedVelocity = with(flingBehavior) { scrollScope.performFling(initialVelocity) }
        }
        return consumedVelocity
    }

    private fun newOffsetForDelta(delta: Float): Float {
        val anchors = draggable.anchors
        if (anchors.size == 0) return Float.NaN
        return ((offsetOrNull ?: 0f) + delta).coerceIn(
            anchors.minPosition(),
            anchors.maxPosition(),
        )
    }
}

/**
 * Create and [remember] an [EditorSheetState]. Plain `remember`, not
 * saveable: the state is cheap to recreate and a restore would hand the
 * layout a fresh draggable whose offset is unset all over again.
 */
@Composable
internal fun rememberEditorSheetState(
    initialValue: SheetValue,
    enabledValues: Set<SheetValue>,
): EditorSheetState =
    remember(initialValue, enabledValues) {
        EditorSheetState(initialValue, enabledValues)
    }

/** The sheet's maximum width — the same cap Material3's scaffold applies. */
private val EditorSheetMaxWidth = 640.dp

/**
 * The drag distance past which a fling-less release settles to the next
 * anchor — Material3's sheet positional threshold.
 */
private val EditorSheetPositionalThreshold = 56.dp

/**
 * A bottom-anchored sheet over [content], built on foundation's
 * [AnchoredDraggableState]. Replaces `BottomSheetScaffold` for the
 * compact photo sheets: the scaffold reads the sheet offset unguarded in
 * its own placement, while this layout owns measure and placement and
 * only ever reads the offset through [EditorSheetState.offsetOrNull].
 *
 * The sheet is measured with the layout's full height available and
 * placed at its drag offset; [content] always gets the full size. The
 * composable draws no material of its own — the sheet content carries
 * its own surface, exactly like the transparent scaffolds it replaces.
 */
@Composable
internal fun EditorSheetLayout(
    state: EditorSheetState,
    peekHeight: Dp,
    swipeEnabled: Boolean,
    sheet: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val peekHeightPx = with(density) { peekHeight.toPx() }
    val positionalThresholdPx = with(density) { EditorSheetPositionalThreshold.toPx() }
    val positionalThreshold: (Float) -> Float = remember(positionalThresholdPx) {
        { positionalThresholdPx }
    }
    // Material3 animates the sheet with the theme's motion scheme: the
    // spatial spec for expansion and flings, the fast effects spec for
    // collapse. The state carries them so programmatic calls made away
    // from this layout animate identically.
    val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val fastSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    SideEffect {
        state.showMotionSpec = spatialSpec
        state.hideMotionSpec = fastSpec
    }
    val flingBehavior = AnchoredDraggableDefaults.flingBehavior(
        state = state.draggable,
        positionalThreshold = positionalThreshold,
        animationSpec = spatialSpec,
    )
    // Material3 gates the sheet's nested-scroll connection on the same
    // flag as the drag itself: while swiping is disabled the sheet must
    // not consume scroll deltas either.
    val nestedScroll = if (swipeEnabled) {
        Modifier.nestedScroll(
            remember(state.draggable, flingBehavior) {
                EditorSheetNestedScrollConnection(state, flingBehavior)
            }
        )
    } else {
        Modifier
    }

    Layout(
        contents = listOf(
            content,
            {
                Column(
                    Modifier
                        .widthIn(max = EditorSheetMaxWidth)
                        .fillMaxWidth()
                        .requiredHeightIn(min = peekHeight)
                        .then(nestedScroll)
                        .anchoredDraggable(
                            state = state.draggable,
                            orientation = Orientation.Vertical,
                            enabled = swipeEnabled,
                            flingBehavior = flingBehavior,
                        )
                ) {
                    sheet()
                }
            }
        ),
        modifier = modifier,
    ) { (contentMeasurables, sheetMeasurables), constraints ->
        val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val contentPlaceables = contentMeasurables.map { it.measure(looseConstraints) }
        val layoutWidth =
            if (constraints.hasBoundedWidth) constraints.maxWidth
            else contentPlaceables.maxOfOrNull { it.width } ?: 0
        val layoutHeight =
            if (constraints.hasBoundedHeight) constraints.maxHeight
            else contentPlaceables.maxOfOrNull { it.height } ?: 0
        val sheetPlaceables = sheetMeasurables.map { measurable ->
            measurable.measure(
                looseConstraints.copy(
                    maxWidth = layoutWidth,
                    maxHeight = layoutHeight,
                )
            )
        }
        val layoutHeightPx = layoutHeight.toFloat()
        val sheetHeightPx =
            sheetPlaceables.maxOfOrNull { it.height }?.toFloat() ?: 0f

        // Hidden parks the sheet's top edge on the layout's bottom,
        // PartiallyExpanded lifts it by the peek, Expanded clears the
        // sheet's measured height — the same anchors the M3 sheet built,
        // restricted to the state's enabled values.
        val newAnchors = DraggableAnchors {
            if (SheetValue.Hidden in state.enabledValues) {
                SheetValue.Hidden at layoutHeightPx
            }
            if (
                SheetValue.PartiallyExpanded in state.enabledValues &&
                peekHeightPx > 0f && peekHeightPx != sheetHeightPx
            ) {
                SheetValue.PartiallyExpanded at (layoutHeightPx - peekHeightPx)
            }
            if (SheetValue.Expanded in state.enabledValues && sheetHeightPx > 0f) {
                SheetValue.Expanded at max(layoutHeightPx - sheetHeightPx, 0f)
            }
        }
        // Keep the logical state across resizes the way Material3 does:
        // a Partial target prefers Partial then Expanded then Hidden, an
        // Expanded target stays Expanded (or falls to Hidden), a Hidden
        // target stays Hidden — each only while the anchor exists.
        val newTarget = when (val oldTarget = state.targetValue) {
            SheetValue.Hidden ->
                if (newAnchors.hasPositionFor(SheetValue.Hidden)) {
                    SheetValue.Hidden
                } else {
                    oldTarget
                }

            SheetValue.PartiallyExpanded -> when {
                newAnchors.hasPositionFor(SheetValue.PartiallyExpanded) ->
                    SheetValue.PartiallyExpanded
                newAnchors.hasPositionFor(SheetValue.Expanded) -> SheetValue.Expanded
                newAnchors.hasPositionFor(SheetValue.Hidden) -> SheetValue.Hidden
                else -> oldTarget
            }

            SheetValue.Expanded ->
                if (newAnchors.hasPositionFor(SheetValue.Expanded)) {
                    SheetValue.Expanded
                } else if (newAnchors.hasPositionFor(SheetValue.Hidden)) {
                    SheetValue.Hidden
                } else {
                    oldTarget
                }
        }
        // Anchors initialise the drag offset inside measure, so a
        // programmatic target recorded before layout lands on the first
        // pass. Lookahead passes reuse the anchors once set — the same
        // first-initialisation rule Material3's draggableAnchors node
        // applies — and placement never reads an uninitialised offset.
        // An empty anchor set (a zero peek over an empty sheet) leaves
        // the offset unset: the sheet parks at the bottom edge and
        // offsetOrNull stays null until real anchors arrive.
        if (newAnchors.size > 0 &&
            (!isLookingAhead || state.draggable.anchors.size == 0)
        ) {
            state.draggable.updateAnchors(newAnchors, newTarget)
        }

        layout(layoutWidth, layoutHeight) {
            contentPlaceables.forEach { it.placeRelative(0, 0) }
            val offsetPx = sheetOffsetPx(state, layoutHeightPx, isLookingAhead)
            val sheetWidth = sheetPlaceables.maxOf { it.width }
            val sheetX = max(0, (layoutWidth - sheetWidth) / 2)
            sheetPlaceables.forEach {
                it.placeRelative(sheetX, offsetPx.roundToInt())
            }
        }
    }
}

/**
 * The sheet's placement offset in pixels. The live offset wins once it
 * exists; before that — and always in lookahead, where Material3 places
 * at the target anchor — the recorded target's anchor stands in, then
 * the settled value's, then the bottom edge.
 */
private fun sheetOffsetPx(
    state: EditorSheetState,
    layoutHeightPx: Float,
    isLookingAhead: Boolean,
): Float {
    fun anchoredOrNull(value: SheetValue): Float? =
        state.draggable.anchors.positionOf(value).takeUnless { it.isNaN() }

    return if (isLookingAhead) {
        anchoredOrNull(state.targetValue)
            ?: anchoredOrNull(state.currentValue)
            ?: state.offsetOrNull
            ?: layoutHeightPx
    } else {
        state.offsetOrNull
            ?: anchoredOrNull(state.targetValue)
            ?: anchoredOrNull(state.currentValue)
            ?: layoutHeightPx
    }
}

/**
 * Material3's `ConsumeSwipeWithinBottomSheetBoundsNestedScrollConnection`
 * for [EditorSheetState]: an upward drag moves the sheet before the
 * content sees it, a downward drag moves it only with what the content
 * leaves over, and flings settle through the sheet's own fling behavior.
 * Every read of the drag offset is NaN-safe — before the first layout
 * the connection simply declines the delta.
 */
private class EditorSheetNestedScrollConnection(
    private val state: EditorSheetState,
    private val flingBehavior: FlingBehavior,
) : NestedScrollConnection {

    /**
     * Set once a gesture has moved the sheet: the rest of that gesture —
     * drag remainder and fling — belongs to the sheet alone, so the
     * card's own scroller never sees the overshoot that expanded it.
     * A fresh gesture starts with the flag clear and scrolls content
     * normally; the fling dispatch at gesture end resets it.
     */
    private var gestureMovedSheet = false

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        val delta = available.y
        if (
            delta >= 0f ||
            source != NestedScrollSource.UserInput ||
            state.offsetOrNull == null
        ) {
            return Offset.Zero
        }
        val consumed = state.draggable.dispatchRawDelta(delta)
        if (consumed != 0f) gestureMovedSheet = true
        return if (gestureMovedSheet) Offset(0f, delta) else Offset(0f, consumed)
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        if (
            source != NestedScrollSource.UserInput ||
            available.y == 0f ||
            state.offsetOrNull == null
        ) {
            return Offset.Zero
        }
        val sheetConsumed = state.draggable.dispatchRawDelta(available.y)
        return if (gestureMovedSheet) {
            Offset(0f, available.y)
        } else {
            Offset(0f, sheetConsumed)
        }
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (gestureMovedSheet) {
            gestureMovedSheet = false
            state.anchoredDragFling(flingBehavior, available.y)
            return available
        }
        val toFling = available.y
        val currentOffset = state.offsetOrNull ?: return Velocity.Zero
        val anchors = state.draggable.anchors
        if (anchors.size == 0) return Velocity.Zero
        return if (toFling < 0f && currentOffset > anchors.minPosition()) {
            state.anchoredDragFling(flingBehavior, toFling)
            // The sheet settles with a tween, so consume the whole fling.
            available
        } else {
            Velocity.Zero
        }
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        gestureMovedSheet = false
        if (state.draggable.anchors.size == 0) return Velocity.Zero
        val consumedBySheet = state.anchoredDragFling(flingBehavior, available.y)
        return Velocity(consumed.x, consumedBySheet)
    }
}
