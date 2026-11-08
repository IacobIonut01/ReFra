/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.settings.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Animation
import androidx.compose.material.icons.outlined.GifBox
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.ZoomOutMap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.Position
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.core.ui.components.BaseSettingsScreen
import com.dot.gallery.core.ui.settings.rememberPreference
import com.dot.gallery.domain.model.SlideshowTransition
import com.dot.gallery.domain.settings.MAX_SLIDESHOW_INTERVAL
import com.dot.gallery.domain.settings.MIN_SLIDESHOW_INTERVAL
import com.dot.gallery.domain.settings.SettingsKeys
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.feature.settings.generated.resources.Res
import com.dot.gallery.feature.settings.generated.resources.action_ok
import com.dot.gallery.feature.settings.generated.resources.slideshow
import com.dot.gallery.feature.settings.generated.resources.slideshow_content_header
import com.dot.gallery.feature.settings.generated.resources.slideshow_include_gifs
import com.dot.gallery.feature.settings.generated.resources.slideshow_include_videos
import com.dot.gallery.feature.settings.generated.resources.slideshow_interval
import com.dot.gallery.feature.settings.generated.resources.slideshow_ken_burns
import com.dot.gallery.feature.settings.generated.resources.slideshow_ken_burns_summary
import com.dot.gallery.feature.settings.generated.resources.slideshow_loop
import com.dot.gallery.feature.settings.generated.resources.slideshow_random
import com.dot.gallery.feature.settings.generated.resources.slideshow_reverse
import com.dot.gallery.feature.settings.generated.resources.slideshow_seconds_suffix
import com.dot.gallery.feature.settings.generated.resources.slideshow_transition
import com.dot.gallery.feature.settings.generated.resources.slideshow_transition_fade
import com.dot.gallery.feature.settings.generated.resources.slideshow_transition_ken_burns
import com.dot.gallery.feature.settings.generated.resources.slideshow_transition_slide

@Composable
fun SlideshowSettingsContent(
    searchRoute: String? = null,
    searchSink: ((String, List<String>) -> Unit)? = null,
) {
    val listState = rememberLazyListState()

    var intervalSeconds by rememberPreference(SettingsKeys.slideshowIntervalSeconds)
    var randomOrder by rememberPreference(SettingsKeys.slideshowRandomOrder)
    var reverseOrder by rememberPreference(SettingsKeys.slideshowReverseOrder)
    var includeGifs by rememberPreference(SettingsKeys.slideshowIncludeGifs)
    var includeVideos by rememberPreference(SettingsKeys.slideshowIncludeVideos)
    var loop by rememberPreference(SettingsKeys.slideshowLoop)
    var transition by rememberPreference(SettingsKeys.slideshowTransition)
    var kenBurns by rememberPreference(SettingsKeys.slideshowKenBurns)

    var showTransitionDialog by rememberSaveable { mutableStateOf(false) }

    @Composable
    fun transitionLabel(value: SlideshowTransition): String = stringResource(
        when (value) {
            SlideshowTransition.FADE -> Res.string.slideshow_transition_fade
            SlideshowTransition.SLIDE -> Res.string.slideshow_transition_slide
            SlideshowTransition.KEN_BURNS -> Res.string.slideshow_transition_ken_burns
        }
    )

    @Composable
    fun settings(): SnapshotStateList<SettingsEntity> {
        val playbackHeader = SettingsEntity.Header(title = stringResource(Res.string.slideshow))

        val intervalPref = SettingsEntity.SeekPreference(
            title = stringResource(Res.string.slideshow_interval),
            icon = Icons.Outlined.Timer,
            currentValue = intervalSeconds.toFloat(),
            minValue = MIN_SLIDESHOW_INTERVAL.toFloat(),
            maxValue = MAX_SLIDESHOW_INTERVAL.toFloat(),
            step = 0,
            valueMultiplier = 1,
            seekSuffix = stringResource(Res.string.slideshow_seconds_suffix),
            onSeek = { intervalSeconds = it.roundToInt() },
            screenPosition = Position.Top
        )

        val transitionPref = SettingsEntity.Preference(
            title = stringResource(Res.string.slideshow_transition),
            icon = Icons.Outlined.Animation,
            summary = transitionLabel(transition),
            onClick = { showTransitionDialog = true },
            screenPosition = Position.Middle
        )

        val kenBurnsPref = SettingsEntity.SwitchPreference(
            title = stringResource(Res.string.slideshow_ken_burns),
            icon = Icons.Outlined.ZoomOutMap,
            summary = stringResource(Res.string.slideshow_ken_burns_summary),
            isChecked = kenBurns,
            onCheck = { kenBurns = it },
            screenPosition = Position.Middle
        )

        val randomPref = SettingsEntity.SwitchPreference(
            title = stringResource(Res.string.slideshow_random),
            icon = Icons.Outlined.Shuffle,
            isChecked = randomOrder,
            onCheck = { randomOrder = it },
            screenPosition = Position.Middle
        )

        val reversePref = SettingsEntity.SwitchPreference(
            title = stringResource(Res.string.slideshow_reverse),
            icon = Icons.Outlined.SwapVert,
            isChecked = reverseOrder,
            enabled = !randomOrder,
            onCheck = { reverseOrder = it },
            screenPosition = Position.Middle
        )

        val loopPref = SettingsEntity.SwitchPreference(
            title = stringResource(Res.string.slideshow_loop),
            icon = Icons.Outlined.Repeat,
            isChecked = loop,
            onCheck = { loop = it },
            screenPosition = Position.Bottom
        )

        val contentHeader =
            SettingsEntity.Header(title = stringResource(Res.string.slideshow_content_header))

        val includeGifsPref = SettingsEntity.SwitchPreference(
            title = stringResource(Res.string.slideshow_include_gifs),
            icon = Icons.Outlined.GifBox,
            isChecked = includeGifs,
            onCheck = { includeGifs = it },
            screenPosition = Position.Top
        )

        val includeVideosPref = SettingsEntity.SwitchPreference(
            title = stringResource(Res.string.slideshow_include_videos),
            icon = Icons.Outlined.Movie,
            isChecked = includeVideos,
            onCheck = { includeVideos = it },
            screenPosition = Position.Bottom
        )

        return remember(
            intervalPref, transitionPref, kenBurnsPref, randomPref, reversePref,
            loopPref, includeGifsPref, includeVideosPref
        ) {
            mutableStateListOf(
                playbackHeader,
                intervalPref,
                transitionPref,
                kenBurnsPref,
                randomPref,
                reversePref,
                loopPref,
                contentHeader,
                includeGifsPref,
                includeVideosPref
            )
        }
    }

    BaseSettingsScreen(
        title = stringResource(Res.string.slideshow),
        settingsList = settings(),
        listState = listState,
        searchRoute = searchRoute,
        searchSink = searchSink,
    )

    if (showTransitionDialog) {
        AlertDialog(
            onDismissRequest = { showTransitionDialog = false },
            title = { Text(stringResource(Res.string.slideshow_transition)) },
            text = {
                Column {
                    SlideshowTransition.entries.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = transition == option,
                                    onClick = {
                                        transition = option
                                        showTransitionDialog = false
                                    }
                                )
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = transition == option,
                                onClick = {
                                    transition = option
                                    showTransitionDialog = false
                                }
                            )
                            Text(
                                text = transitionLabel(option),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTransitionDialog = false }) {
                    Text(stringResource(Res.string.action_ok))
                }
            }
        )
    }
}
