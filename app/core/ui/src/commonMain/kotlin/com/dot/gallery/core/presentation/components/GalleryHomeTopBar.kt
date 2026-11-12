/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.dot.gallery.core.ui.generated.resources.Res
import com.dot.gallery.core.ui.generated.resources.refra_mark
import com.dot.gallery.ui.theme.GalleryChrome
import com.dot.gallery.ui.theme.Spacing
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToInt

const val GalleryHomeTopBarTag = "Gallery.HomeTopBar"
const val GalleryHomeBrandTag = "Gallery.HomeBrand"
const val GalleryHomeActionsTag = "Gallery.HomeActions"
const val GalleryHomeRevealTag = "Gallery.HomeReveal"

fun galleryHomeActionTag(key: String) = "Gallery.HomeAction.$key"

/**
 * A shortcut in the home action group, placed before the filter. Keep it in the list and flip
 * [visible] instead of removing it, so it can grow in and shrink out of the group in place.
 */
@Immutable
class GalleryHomeAction(
    val key: String,
    val icon: ImageVector,
    val contentDescription: String,
    val visible: Boolean = true,
    val onClick: () -> Unit,
)

/**
 * Home chrome keeps the imagery in charge: one brand/status control and a joined action group.
 * Slots let the app supply live backup progress without bringing a ViewModel into this surface.
 *
 * With a [revealState], [expandedStatus] follows the reveal fraction: the header can be dragged
 * open or closed, and the leading status, shortcuts and filter step aside while the detail is
 * visible. Without one, [expandedStatus] simply animates in and out when it is supplied.
 * [actions] sit at the start of the group, nearest the brand, in list order.
 */
@Composable
fun GalleryHomeTopBar(
    onBrandClick: () -> Unit,
    brandDescription: String,
    menu: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    actions: List<GalleryHomeAction> = emptyList(),
    filter: (@Composable () -> Unit)? = null,
    status: (@Composable () -> Unit)? = null,
    expandedStatus: (@Composable () -> Unit)? = null,
    revealState: GalleryHeaderRevealState? = null,
    statusBarInsets: WindowInsets = WindowInsets.statusBars,
) {
    val controlColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val revealing = revealState != null && revealState.enabled
    // Leading status and filter fade out over the first half of the reveal.
    val chromeAlpha = { if (revealing) (1f - revealState!!.fraction * 2f).coerceIn(0f, 1f) else 1f }
    val dragModifier = if (revealing) {
        val draggableState = rememberDraggableState { delta -> revealState!!.dragBy(delta) }
        Modifier.draggable(
            state = draggableState,
            orientation = Orientation.Vertical,
            onDragStopped = { velocity -> revealState!!.settle(velocity) },
        )
    } else Modifier
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(dragModifier)
            .windowInsetsPadding(statusBarInsets)
            .padding(
                horizontal = Spacing.ScreenHorizontal,
                vertical = GalleryChrome.ToolbarVerticalPadding,
            )
            .testTag(GalleryHomeTopBarTag),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedContent(
                targetState = status,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .graphicsLayer {
                        val a = chromeAlpha()
                        alpha = a
                        scaleX = lerp(0.9f, 1f, a)
                        scaleY = lerp(0.9f, 1f, a)
                        transformOrigin = TransformOrigin(0f, 0.5f)
                    },
                contentKey = { it != null },
                contentAlignment = Alignment.CenterStart,
                transitionSpec = {
                    (fadeIn() + scaleIn(initialScale = 0.85f))
                        .togetherWith(fadeOut() + scaleOut(targetScale = 0.85f))
                        .using(SizeTransform(clip = false))
                },
                label = "homeLeadingControl",
            ) { currentStatus ->
                if (currentStatus != null) {
                    currentStatus()
                } else {
                    IconButton(
                        onClick = onBrandClick,
                        modifier = Modifier
                            .size(GalleryChrome.ToolbarButtonSize)
                            .clip(CircleShape)
                            .background(controlColor)
                            .testTag(GalleryHomeBrandTag),
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.refra_mark),
                            contentDescription = brandDescription,
                            // Reuse the launcher's padded monochrome mark at its original geometry.
                            modifier = Modifier.size(24.dp).graphicsLayer {
                                scaleX = 2.35f
                                scaleY = 2.35f
                            },
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.width(Spacing.Small))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(GalleryChrome.ToolbarCornerRadius))
                    .testTag(GalleryHomeActionsTag),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.Hairline),
            ) {
                val stepAside = revealing && revealState!!.expanded
                actions.forEach { action ->
                    key(action.key) {
                        // The segment keeps its fill while it resizes so the capsule never
                        // shows a hole; only the icon fades and scales.
                        AnimatedVisibility(
                            visible = action.visible && !stepAside,
                            enter = expandHorizontally(expandFrom = Alignment.End),
                            exit = shrinkHorizontally(shrinkTowards = Alignment.End),
                        ) {
                            IconButton(
                                onClick = action.onClick,
                                modifier = Modifier
                                    .background(controlColor)
                                    .size(GalleryChrome.ToolbarButtonSize)
                                    .testTag(galleryHomeActionTag(action.key)),
                            ) {
                                // A shortcut that changes role (Select into Close) keeps its
                                // place and crossfades between the two icons.
                                Crossfade(
                                    targetState = action.icon,
                                    animationSpec = tween(ActionFadeMillis * 2),
                                    modifier = Modifier.animateEnterExit(
                                        enter = fadeIn(tween(ActionFadeMillis, delayMillis = ActionFadeMillis / 2)) +
                                            scaleIn(initialScale = 0.6f),
                                        exit = fadeOut(tween(ActionFadeMillis)) +
                                            scaleOut(targetScale = 0.6f),
                                    ),
                                    label = "homeActionIcon",
                                ) { icon ->
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = action.contentDescription,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
                val filterVisible = filter != null && !stepAside
                AnimatedVisibility(
                    visible = filterVisible,
                    enter = fadeIn() + expandHorizontally(expandFrom = Alignment.Start),
                    exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.Start),
                ) {
                    Box(Modifier.background(controlColor)) { filter?.invoke() }
                }
                Box(Modifier.background(controlColor)) { menu() }
            }
        }
        if (revealState != null) {
            val showReveal by remember(revealState) { derivedStateOf { revealState.fraction > 0f } }
            if (revealState.enabled && showReveal && expandedStatus != null) {
                RevealedStatus(revealState, expandedStatus)
            }
        } else {
            // Keep the last content so the exit animation still has something to draw.
            val lastExpanded = remember { mutableStateOf(expandedStatus) }
            if (expandedStatus != null) lastExpanded.value = expandedStatus
            AnimatedVisibility(
                visible = expandedStatus != null,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
            ) {
                Box(Modifier.padding(top = Spacing.Small)) { lastExpanded.value?.invoke() }
            }
        }
    }
}

private const val ActionFadeMillis = 150

/**
 * Lays the status out at full size but reports only the revealed share of its height, so the
 * content below moves with the finger. The status itself slides and fades in from the toolbar.
 */
@Composable
private fun RevealedStatus(
    state: GalleryHeaderRevealState,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clipToBounds()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
                val gap = Spacing.Small.roundToPx()
                val full = placeable.height + gap
                state.revealHeightPx = full.toFloat()
                val visible = (full * state.fraction).roundToInt()
                layout(placeable.width, visible) {
                    placeable.placeWithLayer(0, gap + visible - full) {
                        val f = state.fraction
                        alpha = f
                        scaleX = lerp(0.96f, 1f, f)
                        scaleY = lerp(0.96f, 1f, f)
                        transformOrigin = TransformOrigin(0.5f, 0f)
                    }
                }
            }
            .testTag(GalleryHomeRevealTag),
    ) {
        content()
    }
}
