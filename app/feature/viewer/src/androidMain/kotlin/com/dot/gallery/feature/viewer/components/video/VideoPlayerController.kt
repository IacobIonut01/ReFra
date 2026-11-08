/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.viewer.components.video

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import com.dot.gallery.feature.viewer.generated.resources.Res
import com.dot.gallery.feature.viewer.generated.resources.auto
import com.dot.gallery.feature.viewer.generated.resources.change_playback_speed_cd
import com.dot.gallery.feature.viewer.generated.resources.change_subtitle_track_cd
import com.dot.gallery.feature.viewer.generated.resources.close
import com.dot.gallery.feature.viewer.generated.resources.more_options_cd
import com.dot.gallery.feature.viewer.generated.resources.pause_video
import com.dot.gallery.feature.viewer.generated.resources.play_video
import com.dot.gallery.feature.viewer.generated.resources.rotate_screen_cd
import com.dot.gallery.feature.viewer.generated.resources.toggle_audio_cd
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import com.dot.gallery.domain.model.PlaybackSpeed
import com.dot.gallery.feature.viewer.LocalMediaViewerVisualPolicy
import com.dot.gallery.core.presentation.util.LocalHazeState
import com.dot.gallery.core.presentation.util.rememberGestureNavigationEnabled
import com.dot.gallery.ui.theme.isDarkTheme
import dev.chrisbanes.haze.blur.materials.HazeMaterials
import kotlin.math.roundToInt
import com.dot.gallery.domain.util.volume
import com.dot.gallery.core.presentation.util.hazeEffectScaled
import com.dot.gallery.domain.date.formatMinSec


@UnstableApi
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
                .windowInsetsPadding(
                    WindowInsets.navigationBars
                        .union(WindowInsets.displayCutout)
                        .only(WindowInsetsSides.Horizontal)
                )
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
            val autoLabel = stringResource(Res.string.auto)
            val playbackSpeeds = remember(frameRate, autoLabel) {
                listOf(
                    PlaybackSpeed(1f / (frameRate / 30f), autoLabel, true),
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
            // "Animate media items" off — popup appears instantly, colour shifts snap.
            val animationsEnabled = LocalMediaViewerVisualPolicy.current.animationsEnabled
            val colorSpec = if (animationsEnabled) spring<Color>() else snap()
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
                animationSpec = colorSpec,
                label = "VideoOptionsSurfaceContainer"
            )
            val contentColor by animateColorAsState(
                targetValue = when {
                    autoContrast -> if (isBottomDark) Color.White else Color.Black
                    followTheme -> MaterialTheme.colorScheme.onSurface
                    else -> Color.White
                },
                animationSpec = colorSpec,
                label = "VideoOptionsContentColor"
            )

            Box(contentAlignment = Alignment.BottomEnd) {
                AnimatedContent(
                    targetState = showMoreOptions,
                    transitionSpec = {
                        if (animationsEnabled) {
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
                        } else {
                            EnterTransition.None togetherWith ExitTransition.None
                        }
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
                            .hazeEffectScaled(
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
                                    contentDescription = stringResource(Res.string.close)
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
                                    contentDescription = stringResource(Res.string.change_subtitle_track_cd)
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
                                        contentDescription = stringResource(Res.string.change_playback_speed_cd)
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
                                    contentDescription = stringResource(Res.string.toggle_audio_cd)
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
                                    contentDescription = stringResource(Res.string.rotate_screen_cd)
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
                                .hazeEffectScaled(
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
                                    contentDescription = stringResource(Res.string.more_options_cd)
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

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Display-only tracks + thumb, drawn manually — the two stacked
                    // m3 Sliders used before broke on material3 1.5.0-alpha (the
                    // position track rendered fully active regardless of value).
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(18.dp)
                    ) {
                        val trackHeight = 12.dp.toPx()
                        val thumbWidth = 2.dp.toPx()
                        val trackTop = (size.height - trackHeight) / 2f
                        val trackCorner = CornerRadius(trackHeight / 2f, trackHeight / 2f)
                        val thumbTop = (size.height - 18.dp.toPx()) / 2f

                        // Unbuffered remainder
                        drawRoundRect(
                            color = Color.DarkGray.copy(alpha = 0.4f),
                            topLeft = Offset(0f, trackTop),
                            size = Size(size.width, trackHeight),
                            cornerRadius = trackCorner
                        )
                        // Buffered portion
                        val bufferedWidth =
                            size.width * (buffer / 100f).coerceIn(0f, 1f)
                        if (bufferedWidth > 0f) {
                            drawRoundRect(
                                color = Color.Gray.copy(alpha = 0.8f),
                                topLeft = Offset(0f, trackTop),
                                size = Size(bufferedWidth, trackHeight),
                                cornerRadius = trackCorner
                            )
                        }
                        // Played portion + thumb
                        val max = totalTime.coerceAtLeast(0L).toFloat()
                        val fraction =
                            if (max > 0f) (sliderValue / max).coerceIn(0f, 1f) else 0f
                        if (fraction > 0f) {
                            drawRoundRect(
                                color = Color.White,
                                topLeft = Offset(0f, trackTop),
                                size = Size(size.width * fraction, trackHeight),
                                cornerRadius = trackCorner
                            )
                        }
                        val thumbX = (size.width * fraction - thumbWidth / 2f)
                            .coerceIn(0f, size.width - thumbWidth)
                        drawRoundRect(
                            color = Color.White,
                            topLeft = Offset(thumbX, thumbTop),
                            size = Size(thumbWidth, 18.dp.toPx()),
                            cornerRadius = CornerRadius(thumbWidth / 2f, thumbWidth / 2f)
                        )
                    }

                    // Sensitivity-aware scrubbing gesture overlay
                    Box(
                        Modifier
                            .matchParentSize()
                            .semantics {
                                val rangeEnd = totalTime.coerceAtLeast(0L).toFloat()
                                if (rangeEnd > 0f) {
                                    progressBarRangeInfo = ProgressBarRangeInfo(
                                        current = sliderValue.coerceIn(0f, rangeEnd) / rangeEnd,
                                        range = 0f..1f
                                    )
                                    setProgress { p ->
                                        val target =
                                            (p.coerceIn(0f, 1f) * rangeEnd).toLong()
                                        sliderValue = target.toFloat()
                                        player.seekTo(target)
                                        currentTime.longValue = target
                                        true
                                    }
                                }
                            }
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
                    contentDescription = stringResource(Res.string.pause_video),
                    colorFilter = ColorFilter.tint(Color.White)
                )
            } else {
                Image(
                    modifier = Modifier.fillMaxSize(),
                    imageVector = Icons.Filled.PlayCircleFilled,
                    contentDescription = stringResource(Res.string.play_video),
                    colorFilter = ColorFilter.tint(Color.White)
                )
            }
        }
    }
}