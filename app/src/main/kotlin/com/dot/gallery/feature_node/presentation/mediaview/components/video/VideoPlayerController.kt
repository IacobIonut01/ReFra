/*
 * SPDX-FileCopyrightText: 2023 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.mediaview.components.video

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeMute
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.PauseCircleFilled
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.media3.exoplayer.ExoPlayer
import com.dot.gallery.R
import com.dot.gallery.feature_node.domain.model.PlaybackSpeed
import com.dot.gallery.feature_node.presentation.util.formatMinSec
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerController(
    paddingValues: PaddingValues,
    player: ExoPlayer,
    isPlaying: MutableState<Boolean>,
    currentTime: MutableLongState,
    totalTime: Long,
    buffer: Int,
    toggleRotate: () -> Unit,
    frameRate: Float
) {
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .zIndex(10f)
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 16.dp)
                .padding(bottom = paddingValues.calculateBottomPadding() + 80.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.End
        ) {
            var isMuted by rememberSaveable { mutableStateOf(player.volume == 0f) }
            var currentVolume by rememberSaveable { mutableFloatStateOf(player.volume) }

            // Keep player volume in sync when configuration changes / media swaps
            LaunchedEffect(LocalConfiguration.current, player.currentMediaItem, isMuted) {
                player.volume = if (isMuted) 0f else currentVolume
            }

            // Playback speed / menu
            var auto by rememberSaveable { mutableStateOf(false) }
            var showMenu by rememberSaveable { mutableStateOf(false) }
            var playbackSpeed by rememberSaveable { mutableFloatStateOf(1f) }
            val ctx = LocalResources.current
            val playbackSpeeds = remember(frameRate) {
                listOf(
                    PlaybackSpeed(1f / (frameRate / 30f), ctx.getString(R.string.auto), true),
                    PlaybackSpeed(0.125f, "0.125x"),
                    PlaybackSpeed(0.25f, "0.25x"),
                    PlaybackSpeed(0.5f, "0.5x"),
                    PlaybackSpeed(1f, "1x"),
                    PlaybackSpeed(2f, "2x")
                )
            }
            LaunchedEffect(playbackSpeed) {
                player.setPlaybackSpeed(playbackSpeed)
                showMenu = false
            }

            // --- Scrubbing logic (flicker-free) ---
            // Separate slider value from currentTime while user is interacting.
            var isScrubbing by rememberSaveable { mutableStateOf(false) }
            var wasPlayingBeforeScrub by remember { mutableStateOf(false) }
            var sliderValue by rememberSaveable { mutableFloatStateOf(currentTime.longValue.toFloat()) }

            // Update slider position from playback ONLY when not scrubbing.
            LaunchedEffect(currentTime.longValue, isScrubbing) {
                if (!isScrubbing) {
                    sliderValue = currentTime.longValue.toFloat()
                }
            }

            Box(contentAlignment = Alignment.TopEnd) {
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    playbackSpeeds.forEach { speed ->
                        DropdownMenuItem(
                            modifier = Modifier.padding(end = 16.dp),
                            onClick = {
                                playbackSpeed = speed.speed
                                auto = speed.isAuto
                            },
                            leadingIcon = {
                                RadioButton(
                                    selected = (playbackSpeed == speed.speed && !speed.isAuto) ||
                                            (speed.isAuto && auto),
                                    onClick = {
                                        playbackSpeed = speed.speed
                                        auto = speed.isAuto
                                    }
                                )
                            },
                            text = { Text(text = speed.label) }
                        )
                    }
                }
                IconButton(onClick = { showMenu = !showMenu }) {
                    Icon(
                        imageVector = Icons.Outlined.Speed,
                        tint = Color.White,
                        contentDescription = stringResource(R.string.change_playback_speed_cd)
                    )
                }
            }

            IconButton(
                onClick = {
                    if (isMuted) {
                        player.volume = currentVolume
                        isMuted = false
                    } else {
                        currentVolume = player.volume
                        player.volume = 0f
                        isMuted = true
                    }
                }
            ) {
                Icon(
                    imageVector = if (isMuted) Icons.AutoMirrored.Outlined.VolumeMute else Icons.AutoMirrored.Outlined.VolumeUp,
                    tint = Color.White,
                    contentDescription = stringResource(R.string.toggle_audio_cd)
                )
            }

            IconButton(onClick = { toggleRotate() }) {
                Icon(
                    imageVector = Icons.Outlined.ScreenRotation,
                    tint = Color.White,
                    contentDescription = stringResource(R.string.rotate_screen_cd)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Current time (uses sliderValue for instant feedback while scrubbing)
                Text(
                    modifier = Modifier.width(52.dp),
                    text = sliderValue.toLong().formatMinSec(),
                    fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                val trackModifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(100))

                Box(Modifier.weight(1f)) {
                    // Buffered track (disabled slider)
                    val disabledColors = SliderDefaults.colors(
                        disabledThumbColor = Color.Transparent,
                        disabledInactiveTrackColor = Color.DarkGray.copy(alpha = 0.4f),
                        disabledActiveTrackColor = Color.Gray.copy(alpha = 0.8f),
                        disabledActiveTickColor = Color.Transparent
                    )
                    Slider(
                        modifier = Modifier.fillMaxWidth(),
                        value = buffer.toFloat(),
                        enabled = false,
                        onValueChange = {},
                        thumb = {
                            SliderDefaults.Thumb(
                                interactionSource = remember { MutableInteractionSource() },
                                thumbSize = DpSize(0.dp, 0.dp),
                                colors = disabledColors,
                                enabled = false
                            )
                        },
                        track = {
                            SliderDefaults.Track(
                                modifier = trackModifier,
                                sliderState = it,
                                colors = disabledColors,
                                drawStopIndicator = null,
                                drawTick = { _, _ -> },
                                enabled = false,
                                thumbTrackGapSize = 0.dp
                            )
                        },
                        valueRange = 0f..100f,
                        colors = disabledColors
                    )

                    // Active (position) slider
                    val activeColors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        activeTickColor = Color.Transparent,
                        inactiveTrackColor = Color.Transparent
                    )
                    Slider(
                        modifier = Modifier.fillMaxWidth(),
                        value = sliderValue.coerceIn(0f, (totalTime).coerceAtLeast(0L).toFloat()),
                        onValueChange = { newVal ->
                            if (!isScrubbing) {
                                isScrubbing = true
                                wasPlayingBeforeScrub = isPlaying.value
                                // Pause playback while scrubbing to avoid fighting updates (optional)
                                if (player.isPlaying) {
                                    player.pause()
                                }
                            }
                            sliderValue = newVal
                        },
                        onValueChangeFinished = {
                            scope.launch {
                                val target = sliderValue.toLong().coerceIn(0L, totalTime)
                                if (player.currentPosition != target) {
                                    player.seekTo(target)
                                }
                                // Immediately reflect the seek in shared state for external UI
                                currentTime.longValue = target
                                isScrubbing = false
                                if (wasPlayingBeforeScrub) {
                                    player.playWhenReady = true
                                    player.play()
                                    isPlaying.value = true
                                } else {
                                    player.playWhenReady = false
                                    isPlaying.value = false
                                }
                            }
                        },
                        valueRange = 0f..(if (totalTime > 0) totalTime.toFloat() else 0f),
                        colors = activeColors,
                        thumb = {
                            SliderDefaults.Thumb(
                                interactionSource = remember { MutableInteractionSource() },
                                thumbSize = DpSize(2.dp, 18.dp),
                                colors = activeColors
                            )
                        },
                        track = {
                            SliderDefaults.Track(
                                modifier = trackModifier,
                                sliderState = it,
                                colors = activeColors,
                                drawStopIndicator = null,
                                drawTick = { _, _ -> },
                                thumbTrackGapSize = 0.dp
                            )
                        }
                    )
                }

                // Total time
                Text(
                    modifier = Modifier.width(52.dp),
                    text = totalTime.formatMinSec(),
                    fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Center Play/Pause button
        IconButton(
            onClick = {
                val newState = !isPlaying.value
                isPlaying.value = newState
                if (newState) {
                    player.playWhenReady = true
                    player.play()
                } else {
                    player.pause()
                }
            },
            modifier = Modifier
                .align(Alignment.Center)
                .size(64.dp)
        ) {
            if (isPlaying.value && player.isPlaying) {
                Image(
                    modifier = Modifier.fillMaxSize(),
                    imageVector = Icons.Filled.PauseCircleFilled,
                    contentDescription = stringResource(R.string.pause_video),
                    colorFilter = ColorFilter.tint(Color.White)
                )
            } else {
                Image(
                    modifier = Modifier.fillMaxSize(),
                    imageVector = Icons.Filled.PlayCircleFilled,
                    contentDescription = stringResource(R.string.play_video),
                    colorFilter = ColorFilter.tint(Color.White)
                )
            }
        }
    }
}