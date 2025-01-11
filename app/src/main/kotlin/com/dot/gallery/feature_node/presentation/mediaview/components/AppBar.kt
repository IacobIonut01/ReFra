/*
 * SPDX-FileCopyrightText: 2023 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.mediaview.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.Constants
import com.dot.gallery.core.Constants.Animation.enterAnimation
import com.dot.gallery.core.Constants.Animation.exitAnimation
import com.dot.gallery.ui.theme.BlackScrim

@Composable
fun MediaViewAppBar(
    modifier: Modifier = Modifier,
    showUI: Boolean,
    showInfo: Boolean,
    showDate: Boolean,
    currentDate: String,
    paddingValues: PaddingValues,
    onGoBack: () -> Unit,
    onShowInfo: () -> Unit
) {
    AnimatedVisibility(
        visible = showUI,
        enter = enterAnimation(Constants.DEFAULT_TOP_BAR_ANIMATION_DURATION),
        exit = exitAnimation(Constants.DEFAULT_TOP_BAR_ANIMATION_DURATION)
    ) {
        Row(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        colors = listOf(BlackScrim, Color.Transparent)
                    )
                )
                .padding(top = paddingValues.calculateTopPadding())
                .padding(start = 5.dp, end = if (showInfo) 8.dp else 16.dp)
                .padding(vertical = 8.dp)
                .then(modifier)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onGoBack) {
                Image(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    colorFilter = ColorFilter.tint(Color.White),
                    contentDescription = "Go back",
                    modifier = Modifier
                        .height(48.dp)
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                AnimatedVisibility(
                    visible = showDate,
                    enter = enterAnimation,
                    exit = exitAnimation
                ) {
                    Text(
                        text = currentDate.uppercase(),
                        modifier = Modifier,
                        style = MaterialTheme.typography.titleSmall,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White,
                        textAlign = TextAlign.End
                    )
                }
                AnimatedVisibility(
                    visible = showInfo,
                    enter = enterAnimation,
                    exit = exitAnimation
                ) {
                    IconButton(
                        onClick = onShowInfo
                    ) {
                        Image(
                            imageVector = Icons.Outlined.Info,
                            colorFilter = ColorFilter.tint(Color.White),
                            contentDescription = "info",
                            modifier = Modifier
                                .height(48.dp)
                        )
                    }
                }
            }
        }
    }
}