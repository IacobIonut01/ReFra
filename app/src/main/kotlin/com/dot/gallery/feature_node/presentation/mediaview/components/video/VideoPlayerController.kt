/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.mediaview.components.video

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeMute
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.PauseCircleFilled
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.SubtitlesOff
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import com.dot.gallery.R
import com.dot.gallery.feature_node.domain.model.PlaybackSpeed
import com.dot.gallery.feature_node.presentation.util.LocalHazeState
import com.dot.gallery.feature_node.presentation.util.formatMinSec
import com.dot.gallery.feature_node.presentation.util.rememberGestureNavigationEnabled
import com.dot.gallery.ui.theme.isDarkTheme
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import kotlin.math.roundToInt

@UnstableApi
@OptIn(ExperimentalMaterial3Api::class, UnstableApi::class, ExperimentalHazeMaterialsApi::class)
@Composable
fun VideoPlayerController(
    paddingValues: PaddingValues,
    player: ExoPlayer,
    isPlaying: MutableState<Boolean>,
    currentTime: MutableLongState,
    totalTime: Long,
    buffer: Int,
    toggleRotate: () -> Unit,
    frameRate: Float,
    onCastSeek: ((Double) -> Unit)? = null,
    onCastPlayPause: ((Boolean) -> Unit)? = null,
    onCastVolume: ((Double) -> Unit)? = null,
    onCastSpeed: ((Double) -> Unit)? = null,
    anySubtitleSelected: Boolean = false,
    onSubtitleClick: () -> Unit = {},
    onInteraction: () -> Unit = {},
    isBottomDark: Boolean = false,
    autoContrast: Boolean = false,
) {

    val isGestureEnabled = rememberGestureNavigationEnabled()
    val extraNavPadding = remember(isGestureEnabled) {
        if (!isGestureEnabled) 32.dp else 0.dp
    }

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
                .padding(bottom = paddingValues.calculateBottomPadding() + 80.dp + extraNavPadding)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.End
        ) {
            var isMuted by rememberSaveable { mutableStateOf(player.volume == 0f) }
            var currentVolume by rememberSaveable { mutableFloatStateOf(player.volume) }

            // Keep player volume in sync when configuration changes / media swaps
            val isCasting = onCastVolume != null
            LaunchedEffect(LocalConfiguration.current, player.currentMediaItem, isMuted, isCasting) {
                if (isCasting) {
                    player.volume = 0f
                } else {
                    player.volume = if (isMuted) 0f else currentVolume
                }
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
                onCastSpeed?.invoke(playbackSpeed.toDouble())
                showMenu = false
            }

            // --- Scrubbing logic (flicker-free) ---
            // Separate slider value from currentTime while user is interacting.
            var isScrubbing by rememberSaveable { mutableStateOf(false) }
            var wasPlayingBeforeScrub by remember { mutableStateOf(false) }
            var sliderValue by rememberSaveable { mutableFloatStateOf(currentTime.longValue.toFloat()) }
            var sensitivityFactor by remember { mutableFloatStateOf(1f) }

            // Update slider position from playback ONLY when not scrubbing.
            LaunchedEffect(currentTime.longValue, isScrubbing) {
                if (!isScrubbing) {
                    sliderValue = currentTime.longValue.toFloat()
                }
            }

            // "More options" button that expands into a blurred popup
            var showMoreOptions by rememberSaveable { mutableStateOf(false) }
            val hazeState = LocalHazeState.current
            val isDarkTheme = isDarkTheme()
            val followTheme = remember(autoContrast, isBottomDark) {
                if (autoContrast) !isBottomDark else false
            }
            val surfaceContainer by animateColorAsState(
                targetValue = when {
                    autoContrast && !isBottomDark -> Color.White.copy(0.5f)
                    autoContrast -> Color.Black.copy(0.5f)
                    followTheme -> MaterialTheme.colorScheme.surfaceContainer.copy(
                        if (isDarkTheme) 0.5f else 0.8f
                    )
                    else -> Color.Black.copy(0.5f)
                },
                label = "VideoOptionsSurfaceContainer"
            )
            val contentColor by animateColorAsState(
                targetValue = when {
                    autoContrast -> if (isBottomDark) Color.White else Color.Black
                    followTheme -> MaterialTheme.colorScheme.onSurface
                    else -> Color.White
                },
                label = "VideoOptionsContentColor"
            )

            Box(contentAlignment = Alignment.BottomEnd) {
                AnimatedContent(
                    targetState = showMoreOptions,
                    transitionSpec = {
                        (fadeIn(tween(200)) + scaleIn(
                            tween(250),
                            initialScale = 0.8f,
                            transformOrigin = TransformOrigin(1f, 1f)
                        )).togetherWith(
                            fadeOut(tween(150)) + scaleOut(
                                tween(200),
                                targetScale = 0.8f,
                                transformOrigin = TransformOrigin(1f, 1f)
                            )
                        ).using(SizeTransform(clip = false))
                    },
                    contentAlignment = Alignment.BottomEnd,
                    label = "MoreOptionsTransition"
                ) { expanded ->
                    if (expanded) {
                        val isLandscape = LocalConfiguration.current.orientation ==
                                Configuration.ORIENTATION_LANDSCAPE
                        val panelModifier = Modifier
                            .clip(RoundedCornerShape(28.dp))
                            .background(surfaceContainer)
                            .hazeEffect(
                                state = hazeState,
                                style = HazeMaterials.regular(
                                    containerColor = surfaceContainer
                                )
                            )
                            .padding(8.dp)
                        val optionButtons: @Composable () -> Unit = {
                            // Collapse button
                            IconButton(onClick = {
                                showMoreOptions = false
                                onInteraction()
                            }) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    tint = contentColor,
                                    contentDescription = stringResource(R.string.close)
                                )
                            }

                            // Subtitle track picker
                            IconButton(onClick = {
                                showMoreOptions = false
                                onSubtitleClick()
                                onInteraction()
                            }) {
                                Icon(
                                    imageVector = if (anySubtitleSelected) Icons.Outlined.Subtitles else Icons.Outlined.SubtitlesOff,
                                    tint = contentColor,
                                    contentDescription = stringResource(R.string.change_subtitle_track_cd)
                                )
                            }

                            // Playback speed
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
                                IconButton(onClick = {
                                    showMenu = !showMenu
                                    onInteraction()
                                }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Speed,
                                        tint = contentColor,
                                        contentDescription = stringResource(R.string.change_playback_speed_cd)
                                    )
                                }
                            }

                            // Volume toggle
                            IconButton(
                                onClick = {
                                    onInteraction()
                                    if (onCastVolume != null) {
                                        isMuted = !isMuted
                                        onCastVolume.invoke(if (isMuted) 0.0 else currentVolume.toDouble())
                                    } else {
                                        if (isMuted) {
                                            player.volume = currentVolume
                                            isMuted = false
                                        } else {
                                            currentVolume = player.volume
                                            player.volume = 0f
                                            isMuted = true
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (isMuted) Icons.AutoMirrored.Outlined.VolumeMute else Icons.AutoMirrored.Outlined.VolumeUp,
                                    tint = contentColor,
                                    contentDescription = stringResource(R.string.toggle_audio_cd)
                                )
                            }

                            // Rotate screen
                            IconButton(onClick = {
                                showMoreOptions = false
                                toggleRotate()
                                onInteraction()
                            }) {
                                Icon(
                                    imageVector = Icons.Outlined.ScreenRotation,
                                    tint = contentColor,
                                    contentDescription = stringResource(R.string.rotate_screen_cd)
                                )
                            }
                        }
                        if (isLandscape) {
                            Row(
                                modifier = panelModifier,
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(0.dp),
                                content = { optionButtons() }
                            )
                        } else {
                            Column(
                                modifier = panelModifier,
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(0.dp),
                                content = { optionButtons() }
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(surfaceContainer)
                                .hazeEffect(
                                    state = hazeState,
                                    style = HazeMaterials.regular(
                                        containerColor = surfaceContainer
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            IconButton(onClick = {
                                showMoreOptions = true
                                onInteraction()
                            }) {
                                Icon(
                                    imageVector = Icons.Outlined.MoreVert,
                                    tint = contentColor,
                                    contentDescription = stringResource(R.string.more_options_cd)
                                )
                            }
                        }
                    }
                }
            }

            if (isScrubbing && sensitivityFactor < 0.95f) {
                val precisionLevel = (1f / sensitivityFactor).roundToInt().coerceAtLeast(2)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${precisionLevel}× fine",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .background(
                                Color.Black.copy(alpha = 0.6f),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
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

                    // Active (position) slider — display only, gestures handled by overlay
                    val activeColors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        activeTickColor = Color.Transparent,
                        inactiveTrackColor = Color.Transparent
                    )
                    Slider(
                        modifier = Modifier.fillMaxWidth(),
                        value = sliderValue.coerceIn(0f, (totalTime).coerceAtLeast(0L).toFloat()),
                        onValueChange = {},
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

                    // Sensitivity-aware scrubbing gesture overlay
                    Box(
                        Modifier
                            .matchParentSize()
                            .pointerInput(totalTime) {
                                val maxValue = totalTime.coerceAtLeast(0L).toFloat()
                                if (maxValue <= 0f) return@pointerInput
                                val sensitivityRefPx = 200.dp.toPx()

                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    down.consume()

                                    val startY = down.position.y
                                    val trackWidth = size.width.toFloat()

                                    isScrubbing = true
                                    wasPlayingBeforeScrub = isPlaying.value
                                    if (player.isPlaying) player.pause()
                                    player.setSeekParameters(SeekParameters.EXACT)

                                    // Seek to tap position
                                    val tapFraction = (down.position.x / trackWidth).coerceIn(0f, 1f)
                                    sliderValue = tapFraction * maxValue
                                    player.seekTo(sliderValue.toLong())

                                    var prevX = down.position.x
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull() ?: break
                                        if (!change.pressed) {
                                            change.consume()
                                            break
                                        }
                                        change.consume()

                                        val upwardOffset = (startY - change.position.y).coerceAtLeast(0f)
                                        val sensitivity = 1f / (1f + upwardOffset / sensitivityRefPx)
                                        sensitivityFactor = sensitivity

                                        val deltaX = change.position.x - prevX
                                        prevX = change.position.x
                                        val deltaFraction = deltaX / trackWidth
                                        val valueDelta = deltaFraction * maxValue * sensitivity

                                        sliderValue = (sliderValue + valueDelta).coerceIn(0f, maxValue)
                                        player.seekTo(sliderValue.toLong())
                                    }

                                    // Finish scrubbing
                                    player.setSeekParameters(SeekParameters.EXACT)
                                    val target = sliderValue.toLong().coerceIn(0L, totalTime)
                                    player.seekTo(target)
                                    currentTime.longValue = target
                                    onCastSeek?.invoke(target.toDouble() / 1000.0)
                                    isScrubbing = false
                                    sensitivityFactor = 1f
                                    if (wasPlayingBeforeScrub) {
                                        player.playWhenReady = true
                                        player.play()
                                        isPlaying.value = true
                                    } else {
                                        player.playWhenReady = false
                                        isPlaying.value = false
                                    }
                                }
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
                onCastPlayPause?.invoke(newState)
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