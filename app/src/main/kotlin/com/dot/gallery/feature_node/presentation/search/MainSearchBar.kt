/*
 * SPDX-FileCopyrightText: 2023 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.search

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import com.dot.gallery.R
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.LocalMediaSelector
import com.dot.gallery.core.Settings.Misc.rememberAllowBlur
import com.dot.gallery.core.Settings.Misc.rememberAutoHideSearchBar
import com.dot.gallery.core.navigate
import com.dot.gallery.feature_node.presentation.util.LocalHazeState
import com.dot.gallery.feature_node.presentation.util.Screen
import dev.chrisbanes.haze.LocalHazeStyle
import dev.chrisbanes.haze.hazeEffect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun MainSearchBar(
    isScrolling: MutableState<Boolean>,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    menuItems: @Composable (RowScope.() -> Unit)? = null,
) = with(sharedTransitionScope) {
    val isSelectionActive by LocalMediaSelector.current.isSelectionActive.collectAsStateWithLifecycle()
    val hideSearchBarSetting by rememberAutoHideSearchBar()
    var shouldHide by remember { mutableStateOf(if (hideSearchBarSetting) isScrolling.value else false) }
    val alpha by animateFloatAsState(
        targetValue = if (shouldHide) 0f else if (isSelectionActive) 0.6f else 1f,
        label = "alpha"
    )

    LaunchedEffect(isScrolling.value) {
        snapshotFlow { isScrolling.value }
            .distinctUntilChanged()
            .collectLatest {
                shouldHide = if (hideSearchBarSetting) it else false
            }
    }

    val allowBlur by rememberAllowBlur()
    val surfaceContainer =
        MaterialTheme.colorScheme.surfaceContainer
    val backgroundModifier = remember(allowBlur) {
        if (!allowBlur) {
            Modifier.background(
                color = surfaceContainer,
                shape = RoundedCornerShape(100)
            )
        } else {
            Modifier
        }
    }

    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
        val adaptiveInfo = currentWindowAdaptiveInfo()
        val useMaxWidth = remember(adaptiveInfo.windowSizeClass) {
            !adaptiveInfo.windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
        }
        val widthModifier = remember(useMaxWidth) {
            if (useMaxWidth) {
                Modifier.fillMaxWidth()
            } else {
                Modifier
                    .wrapContentWidth()
                    .align(Alignment.TopEnd)
            }
        }
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .padding(top = 8.dp)
                .padding(horizontal = 32.dp)
                .zIndex(1f)
                .graphicsLayer {
                    this.alpha = alpha
                }
                .then(widthModifier),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val eventHandler = LocalEventHandler.current
            val weightModifier = remember(useMaxWidth) {
                if (useMaxWidth) {
                    Modifier.weight(1f)
                } else {
                    Modifier
                }
            }
            Row(
                modifier = Modifier
                    .sharedBounds(
                        sharedContentState = rememberSharedContentState(key = "search_screen_bounds"),
                        animatedVisibilityScope = animatedContentScope
                    )
                    .sharedElement(
                        sharedContentState = rememberSharedContentState(key = "search_screen"),
                        animatedVisibilityScope = animatedContentScope
                    )
                    .then(weightModifier)
                    .clip(RoundedCornerShape(100))
                    .then(backgroundModifier)
                    .hazeEffect(
                        state = LocalHazeState.current,
                        style = LocalHazeStyle.current
                    )
                    .clickable {
                        eventHandler.navigate(Screen.SearchScreen())
                    }
                    .padding(start = 16.dp, end = 4.dp)
                    .height(56.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = stringResource(R.string.search_media)
                )

                Text(
                    modifier = weightModifier.padding(end = 16.dp),
                    text = stringResource(R.string.search)
                )
            }

            menuItems?.invoke(this@Row)
        }
    }

}
