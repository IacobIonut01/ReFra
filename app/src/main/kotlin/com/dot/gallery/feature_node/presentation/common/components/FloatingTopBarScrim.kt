package com.dot.gallery.feature_node.presentation.common.components

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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val TopBarScrimFade = 72.dp

/**
 * A fixed top gradient scrim painted behind the floating search bar zone.
 *
 * Screens hosting [com.dot.gallery.feature_node.presentation.search.MainSearchBar]
 * let grid content scroll underneath the bar; without a scrim the bar actions and
 * status icons lose contrast against arbitrary media (#1182). This overlays the
 * same surfaceColorAtElevation(3.dp) -> Transparent brush the sticky date header
 * uses so the two blend seamlessly once a header pins.
 *
 * Must be emitted as a sibling *after* the scrolling content inside a Box so it
 * paints over the grid but stays under the Scaffold's topBar slot.
 *
 * @param barZoneHeight the animated height of the floating bar zone
 * (search bar + insets). The scrim extends [TopBarScrimFade] below it, matching
 * the sticky header scrim's falloff. Pass 0.dp to hide.
 */
@Composable
fun FloatingTopBarScrim(
    barZoneHeight: Dp,
    modifier: Modifier = Modifier,
) {
    if (barZoneHeight <= 0.dp) return
    Box(
        modifier = modifier
            .fillMaxWidth()
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
