/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.albumtimeline.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AllInclusive
import androidx.compose.material.icons.outlined.Gif
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.dot.gallery.app.presentation.settings.subsettings.SlideshowSettingsScreen
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.Position
import com.dot.gallery.core.Settings
import com.dot.gallery.core.navigate
import com.dot.gallery.core.presentation.components.ExpressiveSheet
import com.dot.gallery.core.presentation.components.SheetActions
import com.dot.gallery.core.presentation.components.SheetButton
import com.dot.gallery.core.presentation.components.SheetButtonStyle
import com.dot.gallery.core.presentation.components.SheetHeader
import com.dot.gallery.core.presentation.components.SheetOptionRow
import com.dot.gallery.core.presentation.components.SheetRowTrailing
import com.dot.gallery.core.presentation.components.sheetGroupedShape
import com.dot.gallery.core.presentation.util.Screen
import com.dot.gallery.core.ui.util.AppBottomSheetState
import com.dot.gallery.domain.model.SlideshowTransition
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.slideshow
import com.dot.gallery.generated.resources.slideshow_include_gifs
import com.dot.gallery.generated.resources.slideshow_include_videos
import com.dot.gallery.generated.resources.slideshow_interval_value
import com.dot.gallery.generated.resources.slideshow_ken_burns
import com.dot.gallery.generated.resources.slideshow_loop
import com.dot.gallery.generated.resources.slideshow_more_settings
import com.dot.gallery.generated.resources.slideshow_random
import com.dot.gallery.generated.resources.slideshow_reverse
import com.dot.gallery.generated.resources.slideshow_start
import com.dot.gallery.generated.resources.slideshow_transition
import com.dot.gallery.generated.resources.slideshow_transition_fade
import com.dot.gallery.generated.resources.slideshow_transition_ken_burns
import com.dot.gallery.generated.resources.slideshow_transition_slide
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun SlideshowOptionsSheet(
    state: AppBottomSheetState,
    canStart: Boolean,
    onStart: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val eventHandler = LocalEventHandler.current

    var intervalSeconds by Settings.Slideshow.rememberIntervalSeconds()
    var randomOrder by Settings.Slideshow.rememberRandomOrder()
    var reverseOrder by Settings.Slideshow.rememberReverseOrder()
    var includeGifs by Settings.Slideshow.rememberIncludeGifs()
    var includeVideos by Settings.Slideshow.rememberIncludeVideos()
    var loop by Settings.Slideshow.rememberLoop()
    var transition by Settings.Slideshow.rememberTransition()
    var kenBurns by Settings.Slideshow.rememberKenBurns()

    ExpressiveSheet(sheetState = state, paneTitle = stringResource(Res.string.slideshow)) {
        SlideshowOptionsContent(
            options = SlideshowOptions(
                intervalSeconds = intervalSeconds,
                transition = transition,
                kenBurns = kenBurns,
                randomOrder = randomOrder,
                reverseOrder = reverseOrder,
                includeGifs = includeGifs,
                includeVideos = includeVideos,
                loop = loop,
            ),
            canStart = canStart,
            onChange = {
                intervalSeconds = it.intervalSeconds
                transition = it.transition
                kenBurns = it.kenBurns
                randomOrder = it.randomOrder
                reverseOrder = it.reverseOrder
                includeGifs = it.includeGifs
                includeVideos = it.includeVideos
                loop = it.loop
            },
            onStart = {
                scope.launch {
                    state.hide()
                    onStart()
                }
            },
            onMoreSettings = {
                scope.launch {
                    state.hide()
                    eventHandler.navigate(Screen.SlideshowSettingsScreen())
                }
            },
        )
    }
}

/** Everything the slideshow sheet edits. */
data class SlideshowOptions(
    val intervalSeconds: Int,
    val transition: SlideshowTransition,
    val kenBurns: Boolean,
    val randomOrder: Boolean,
    val reverseOrder: Boolean,
    val includeGifs: Boolean,
    val includeVideos: Boolean,
    val loop: Boolean,
)

/** Stateless body of [SlideshowOptionsSheet]. */
@Composable
fun ColumnScope.SlideshowOptionsContent(
    options: SlideshowOptions,
    canStart: Boolean,
    onChange: (SlideshowOptions) -> Unit,
    onStart: () -> Unit,
    onMoreSettings: () -> Unit,
) {
    SheetHeader(title = stringResource(Res.string.slideshow))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 1.dp)
            .clip(sheetGroupedShape(Position.Top))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text(
            text = stringResource(Res.string.slideshow_interval_value, options.intervalSeconds),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Slider(
            value = options.intervalSeconds.toFloat(),
            onValueChange = { onChange(options.copy(intervalSeconds = it.toInt())) },
            valueRange = Settings.Slideshow.MIN_INTERVAL.toFloat()..Settings.Slideshow.MAX_INTERVAL.toFloat(),
        )
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
            .clip(sheetGroupedShape(Position.Middle))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(Res.string.slideshow_transition),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TransitionChip(Res.string.slideshow_transition_fade, options.transition == SlideshowTransition.FADE) {
                onChange(options.copy(transition = SlideshowTransition.FADE))
            }
            TransitionChip(Res.string.slideshow_transition_slide, options.transition == SlideshowTransition.SLIDE) {
                onChange(options.copy(transition = SlideshowTransition.SLIDE))
            }
            TransitionChip(Res.string.slideshow_transition_ken_burns, options.transition == SlideshowTransition.KEN_BURNS) {
                onChange(options.copy(transition = SlideshowTransition.KEN_BURNS))
            }
        }
    }
    val switches = listOf(
        SwitchSpec(Res.string.slideshow_ken_burns, Icons.Outlined.ZoomIn, options.kenBurns) { onChange(options.copy(kenBurns = !options.kenBurns)) },
        SwitchSpec(Res.string.slideshow_random, Icons.Outlined.Shuffle, options.randomOrder) { onChange(options.copy(randomOrder = !options.randomOrder)) },
        SwitchSpec(Res.string.slideshow_reverse, Icons.Outlined.SwapVert, options.reverseOrder && !options.randomOrder, enabled = !options.randomOrder) {
            onChange(options.copy(reverseOrder = !options.reverseOrder))
        },
        SwitchSpec(Res.string.slideshow_include_gifs, Icons.Outlined.Gif, options.includeGifs) { onChange(options.copy(includeGifs = !options.includeGifs)) },
        SwitchSpec(Res.string.slideshow_include_videos, Icons.Outlined.Videocam, options.includeVideos) { onChange(options.copy(includeVideos = !options.includeVideos)) },
        SwitchSpec(Res.string.slideshow_loop, Icons.Outlined.Repeat, options.loop) { onChange(options.copy(loop = !options.loop)) },
    )
    switches.forEachIndexed { index, spec ->
        SheetOptionRow(
            title = stringResource(spec.label),
            icon = spec.icon,
            position = if (index == switches.lastIndex) Position.Bottom else Position.Middle,
            trailing = SheetRowTrailing.Switch(spec.checked),
            enabled = spec.enabled,
            onClick = spec.onToggle,
        )
    }
    SheetActions {
        SheetButton(
            text = stringResource(Res.string.slideshow_more_settings),
            onClick = onMoreSettings,
            style = SheetButtonStyle.Tonal,
            modifier = Modifier.weight(1f),
        )
        SheetButton(
            text = stringResource(Res.string.slideshow_start),
            onClick = onStart,
            enabled = canStart,
            icon = Icons.Outlined.PlayArrow,
            modifier = Modifier.weight(1f),
        )
    }
}

private class SwitchSpec(
    val label: StringResource,
    val icon: ImageVector,
    val checked: Boolean,
    val enabled: Boolean = true,
    val onToggle: () -> Unit,
)

@Composable
private fun TransitionChip(labelRes: StringResource, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(stringResource(labelRes)) },
        shape = RoundedCornerShape(12.dp),
        border = null,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    )
}
