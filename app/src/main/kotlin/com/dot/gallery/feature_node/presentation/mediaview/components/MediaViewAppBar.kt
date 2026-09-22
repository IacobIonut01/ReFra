/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.mediaview.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MotionPhotosOn
import androidx.compose.material.icons.outlined.MotionPhotosPaused
import androidx.compose.material.icons.outlined.ScreenRotationAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dot.gallery.R
import com.dot.gallery.core.Constants.Animation.enterAnimation
import com.dot.gallery.core.Constants.Animation.exitAnimation
import com.dot.gallery.core.Constants.DEFAULT_TOP_BAR_ANIMATION_DURATION
import com.dot.gallery.feature_node.presentation.mediaview.LocalMediaViewerVisualPolicy
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.util.isVideo
import com.dot.gallery.feature_node.presentation.mediaview.rememberedDerivedState
import com.dot.gallery.feature_node.presentation.util.LocalHazeState
import com.dot.gallery.feature_node.presentation.util.hazeEffectScaled
import com.dot.gallery.ui.theme.BlackScrim
import com.dot.gallery.ui.theme.WhiterBlackScrim
import com.dot.gallery.ui.theme.isDarkTheme
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun MediaViewAppBar(
    modifier: Modifier = Modifier,
    showUI: Boolean,
    showInfo: Boolean,
    showDate: Boolean,
    isLocked: Boolean,
    currentMedia: Media?,
    currentDate: AnnotatedString,
    // While true, a subtle indeterminate horizontal indicator is shown directly under the
    // top-center date (e.g. while a cloud/remote full-size original downloads for subsampling).
    showLoadingIndicator: Boolean = false,
    paddingValues: PaddingValues,
    showRotationHelper: State<Boolean>,
    // 1f = fully visible, 0f = fully hidden. Driven by info-sheet expansion progress so the
    // extras (rotate, lock, motion pill) fade out gradually and are gone at full expand (#963).
    topExtrasAlpha: () -> Float = { 1f },
    isMotionPhoto: Boolean = false,
    isMotionPlaying: Boolean = false,
    onToggleMotionPhoto: () -> Unit = {},
    // While a rotation write is in flight the confirm chip morphs into a busy state (spinner +
    // stage label) and is not tappable.
    rotationInProgress: Boolean = false,
    rotationStageLabel: String? = null,
    rotateImage: () -> Unit,
    onGoBack: () -> Unit,
    onShowInfo: () -> Unit,
    onLock: () -> Unit,
    isImageDark: Boolean = false,
    autoContrast: Boolean = false,
    // Optional second action rendered inside the back button's pill (visual search).
    visualSearchButton: @Composable ((followTheme: Boolean) -> Unit)? = null,
    // While non-null the visual-search prepare is in flight — shows a labeled progress chip
    // under the bar whose X (or tap) cancels the job.
    visualSearchProgress: Int? = null,
    visualSearchStageLabel: String? = null,
    onCancelVisualSearch: () -> Unit = {},
    castButton: @Composable ((followTheme: Boolean) -> Unit)? = null,
    castBanner: @Composable (() -> Unit)? = null
) {
    val visualPolicy = LocalMediaViewerVisualPolicy.current
    val isDarkTheme = isDarkTheme()
    val isVideo by rememberedDerivedState(currentMedia) {
        currentMedia?.isVideo ?: false
    }
    val followTheme = remember(visualPolicy, isVideo, isDarkTheme, autoContrast, isImageDark) {
        if (autoContrast) !isImageDark
        // Chrome follows the theme only while the viewer background does — a forced-dark
        // background on a light theme must keep the scrim/icon tint dark.
        else !visualPolicy.usesDarkBackground(isDarkTheme) && !isVideo
    }
    AnimatedVisibility(
        visible = showUI,
        enter = enterAnimation(DEFAULT_TOP_BAR_ANIMATION_DURATION),
        exit = exitAnimation(DEFAULT_TOP_BAR_ANIMATION_DURATION)
    ) {
        val gradientColor by animateColorAsState(
            when {
                autoContrast -> if (isImageDark) BlackScrim else WhiterBlackScrim
                followTheme -> if (isDarkTheme) BlackScrim else WhiterBlackScrim
                else -> BlackScrim
            },
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(gradientColor, Color.Transparent)
                        )
                    )
                    .padding(top = paddingValues.calculateTopPadding())
                    .padding(start = 4.dp, end = if (showInfo) 4.dp else 16.dp)
                    .padding(vertical = 8.dp)
                    .then(modifier)
                    .fillMaxWidth(),
            ) {
                val surfaceContainer by animateColorAsState(
                    targetValue = when {
                        autoContrast && !isImageDark -> Color.White.copy(0.4f)
                        autoContrast -> Color.Black.copy(0.3f)
                        followTheme -> MaterialTheme.colorScheme.surfaceContainer.copy(0.5f)
                        else -> Color.Black.copy(0.3f)
                    },
                    label = "AppBarSurfaceContainer"
                )
                val backgroundModifier = if (!visualPolicy.allowBlur) {
                    Modifier.background(
                        color = surfaceContainer,
                        shape = CircleShape
                    )
                } else Modifier
                val contentColor by animateColorAsState(
                    targetValue = when {
                        autoContrast -> if (isImageDark) Color.White else Color.Black
                        followTheme -> MaterialTheme.colorScheme.onSurface
                        else -> Color.White
                    },
                    label = "AppBarContentColor"
                )

                CompositionLocalProvider(LocalContentColor provides contentColor) {
                Row(
                    modifier = modifier
                        .align(Alignment.CenterStart)
                        .padding(horizontal = 8.dp)
                        .clip(CircleShape)
                        .then(backgroundModifier)
                        .hazeEffectScaled(
                            state = LocalHazeState.current,
                            style = HazeMaterials.ultraThin(
                                containerColor = surfaceContainer
                            )
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onGoBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.back_cd),
                            tint = contentColor,
                            modifier = Modifier.height(48.dp)
                        )
                    }
                    if (visualSearchButton != null) {
                        // Hairline divider so the widened pill still reads as two targets.
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(24.dp)
                                .background(contentColor.copy(alpha = 0.2f))
                        )
                        visualSearchButton(followTheme)
                    }
                }

                this@Column.AnimatedVisibility(
                    modifier = Modifier.align(Alignment.Center),
                    visible = showDate,
                    enter = enterAnimation,
                    exit = exitAnimation
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = currentDate,
                            style = MaterialTheme.typography.titleSmall,
                            color = contentColor,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .combinedClickable(
                                    onLongClick = { onLock() },
                                    onClick = { /* no-op */ },
                                    interactionSource = null,
                                    indication = null
                                )
                        )
                        this@Column.AnimatedVisibility(
                            visible = showLoadingIndicator,
                            enter = enterAnimation,
                            exit = exitAnimation
                        ) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .width(64.dp)
                                    .height(2.dp),
                                color = contentColor.copy(alpha = 0.7f),
                                trackColor = contentColor.copy(alpha = 0.15f),
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(horizontal = 8.dp)
                        .clip(CircleShape)
                        .then(backgroundModifier)
                        .hazeEffectScaled(
                            state = LocalHazeState.current,
                            style = HazeMaterials.ultraThin(
                                containerColor = surfaceContainer
                            )
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    castButton?.invoke(followTheme)

                    this@Column.AnimatedVisibility(
                        visible = showInfo,
                        enter = enterAnimation,
                        exit = exitAnimation
                    ) {
                        IconButton(onClick = onShowInfo) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = stringResource(R.string.media_info),
                                tint = contentColor,
                                modifier = Modifier.height(48.dp)
                            )
                        }
                    }
                }
                }
            }

            castBanner?.invoke()

            AnimatedVisibility(
                visible = isLocked,
                enter = enterAnimation,
                exit = exitAnimation,
                modifier = Modifier.graphicsLayer { alpha = topExtrasAlpha() }
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = stringResource(R.string.locked),
                    modifier = Modifier.height(20.dp)
                )
            }
            AnimatedVisibility(
                visible = isMotionPhoto && !isLocked,
                enter = enterAnimation,
                exit = exitAnimation,
                modifier = Modifier.graphicsLayer { alpha = topExtrasAlpha() }
            ) {
                Row(
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(0.7f),
                            shape = CircleShape
                        )
                        .clip(CircleShape)
                        .clickable(onClick = onToggleMotionPhoto)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (isMotionPlaying) Icons.Outlined.MotionPhotosPaused
                            else Icons.Outlined.MotionPhotosOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.motion_photo_pill),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            AnimatedVisibility(
                visible = showRotationHelper.value && !isLocked,
                enter = enterAnimation,
                exit = exitAnimation,
                modifier = Modifier.graphicsLayer { alpha = topExtrasAlpha() }
            ) {
                Row(
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(0.7f),
                            shape = CircleShape
                        )
                        .clip(CircleShape)
                        .clickable(enabled = !rotationInProgress, onClick = rotateImage)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .animateContentSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (rotationInProgress) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.ScreenRotationAlt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                    Text(
                        text = if (rotationInProgress) rotationStageLabel.orEmpty()
                            else stringResource(R.string.rotate),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
            AnimatedVisibility(
                visible = visualSearchStageLabel != null && !isLocked,
                enter = enterAnimation,
                exit = exitAnimation,
                modifier = Modifier.graphicsLayer { alpha = topExtrasAlpha() }
            ) {
                Row(
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(0.7f),
                            shape = CircleShape
                        )
                        .clip(CircleShape)
                        .clickable(onClick = onCancelVisualSearch)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .animateContentSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    Text(
                        text = if (visualSearchProgress != null)
                            "${visualSearchStageLabel.orEmpty()} $visualSearchProgress%"
                        else visualSearchStageLabel.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.action_cancel),
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}