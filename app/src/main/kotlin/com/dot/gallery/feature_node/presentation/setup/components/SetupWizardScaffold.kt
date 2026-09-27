/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.setup.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dot.gallery.R
import com.dot.gallery.feature_node.presentation.util.LocalHazeState
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.LocalHazeBlurStyle
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.blur.materials.HazeMaterials

/**
 * Provides the [SharedTransitionScope] of the setup wizard so common chrome (back button,
 * progress indicator, bottom action bar) can animate as shared elements while switching pages.
 * Null when the page is rendered outside the wizard orchestrator (e.g. previews).
 */
val LocalSetupSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/**
 * Provides the per-page [AnimatedVisibilityScope] (the `AnimatedContent` content scope) used to
 * drive shared element transitions for the wizard chrome.
 */
val LocalSetupAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Marks a [Modifier] as a shared element with the given [key], so an identical element on the
 * previous/next wizard page morphs into this one. No-op when the wizard scopes are absent.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun Modifier.setupSharedElement(key: String): Modifier {
    val sharedScope = LocalSetupSharedTransitionScope.current ?: return this
    val visibilityScope = LocalSetupAnimatedVisibilityScope.current ?: return this
    return with(sharedScope) {
        sharedElement(
            sharedContentState = rememberSharedContentState(key = key),
            animatedVisibilityScope = visibilityScope
        )
    }
}

/**
 * Like [setupSharedElement] but for elements whose content differs between pages (e.g. the
 * action button label). Animates the bounds while cross-fading content.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun Modifier.setupSharedBounds(key: String): Modifier {
    val sharedScope = LocalSetupSharedTransitionScope.current ?: return this
    val visibilityScope = LocalSetupAnimatedVisibilityScope.current ?: return this
    return with(sharedScope) {
        sharedBounds(
            sharedContentState = rememberSharedContentState(key = key),
            animatedVisibilityScope = visibilityScope
        )
    }
}

/**
 * Shared chrome for every setup wizard page: animated background, an optional top bar with a
 * back button + step progress indicator, a scrollable content column and a bottom action bar.
 *
 * The animated background is registered as a Haze source and a frosted [LocalHazeBlurStyle] is
 * provided, so translucent inputs/cards drawn on top can softly blur it (see how the cloud
 * setup text fields use [LocalHazeState] for a glassy look instead of a solid dark box).
 */
@Composable
fun SetupWizardScaffold(
    modifier: Modifier = Modifier,
    showBack: Boolean = true,
    onBack: () -> Unit = {},
    stepNumber: Int = 0,
    totalSteps: Int = 0,
    showProgress: Boolean = totalSteps > 0,
    title: String? = null,
    subtitle: String? = null,
    bottomBar: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    // When the wizard orchestrator is present it renders a single, continuous animated
    // background behind the AnimatedContent, so each page must not draw its own (which would
    // restart the infinite animation and flicker on every page switch).
    val backgroundHandledExternally = LocalSetupSharedTransitionScope.current != null
    val hazeState = remember { HazeState() }

    Box(modifier = modifier.fillMaxSize()) {
        if (!backgroundHandledExternally) {
            SetupAnimatedBackground(modifier = Modifier.hazeSource(hazeState))
        }

        CompositionLocalProvider(
            LocalHazeState provides hazeState,
            LocalHazeBlurStyle provides HazeMaterials.regular(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (showBack) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .setupSharedElement("setup-back-button")
                            .background(
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back_cd),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                } else {
                    Spacer(Modifier.size(1.dp))
                }
                if (showProgress && totalSteps > 0) {
                    StepProgressIndicator(
                        modifier = Modifier.setupSharedBounds("setup-progress"),
                        stepNumber = stepNumber,
                        totalSteps = totalSteps
                    )
                }
            }

            // Scrollable content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (title != null) {
                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .padding(top = 8.dp),
                        text = title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
                if (subtitle != null) {
                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp)
                            .padding(top = 8.dp, bottom = 8.dp),
                        text = subtitle,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
                Column(
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    content = content
                )
            }

            // Bottom action bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .setupSharedBounds("setup-bottom-bar")
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = bottomBar
            )
        }
        }
    }
}

/** Segmented step indicator: filled segments for completed/current steps. */
@Composable
private fun StepProgressIndicator(
    modifier: Modifier = Modifier,
    stepNumber: Int,
    totalSteps: Int
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        for (index in 1..totalSteps) {
            val active = index <= stepNumber
            val color by animateColorAsState(
                if (active) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceContainerHighest,
                label = "step-$index"
            )
            Box(
                modifier = Modifier
                    .height(6.dp)
                    .width(if (index == stepNumber) 22.dp else 12.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color)
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.setup_step_progress, stepNumber, totalSteps),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
