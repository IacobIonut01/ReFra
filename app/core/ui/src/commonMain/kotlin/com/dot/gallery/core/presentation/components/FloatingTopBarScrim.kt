package com.dot.gallery.core.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp



private val TopBarScrimFade = 72.dp

/**
 * A fixed top gradient scrim painted behind the floating search bar zone.
 *
 * Screens hosting the floating search bar
 * let grid content scroll underneath the bar; without a scrim the bar actions and
 * status icons lose contrast against arbitrary media. This overlays the
 * same surfaceColorAtElevation(3.dp) -> Transparent brush the sticky date header
 * uses so the two blend seamlessly once a header pins.
 *
 * Must be emitted as a sibling *after* the scrolling content inside a Box so it
 * paints over the grid but stays under the Scaffold's topBar slot.
 *
 * @param visibility read while drawing; pass a scroll-driven value (see [topScrimVisibility])
 * so the scrim only appears once content actually scrolls under the bar.
 * @param barZoneHeight the animated height of the floating bar zone
 * (search bar + insets). The scrim extends [TopBarScrimFade] below it, matching
 * the sticky header scrim's falloff. Pass 0.dp to hide.
 */
@Composable
fun FloatingTopBarScrim(
    barZoneHeight: Dp,
    modifier: Modifier = Modifier,
    visibility: Density.() -> Float = { 1f },
) {
    if (barZoneHeight <= 0.dp) return
    Box(
        modifier = modifier
            .fillMaxWidth()
            // Read while drawing so scrolling never recomposes the scrim.
            .graphicsLayer { alpha = visibility() }
            .height(barZoneHeight + TopBarScrimFade)
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
                        Color.Transparent,
                    )
                )
            )
    )
}

/** Distance over which the top scrim fades in once content starts scrolling under the bar. */
val TopBarScrimScrollFade = 48.dp

/**
 * 0 at the very top of a lazy container, 1 once its first item has scrolled [fadePx] under the
 * bar or any later item leads. Pass the container's first visible index and its offset.
 */
fun topScrimVisibility(firstVisibleIndex: Int, firstVisibleOffset: Int, fadePx: Float): Float =
    if (firstVisibleIndex > 0) 1f else (firstVisibleOffset / fadePx).coerceIn(0f, 1f)
