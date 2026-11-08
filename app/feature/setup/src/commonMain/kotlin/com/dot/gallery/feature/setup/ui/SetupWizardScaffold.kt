/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.setup.ui

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.presentation.util.LocalHazeState
import com.dot.gallery.core.ui.settings.rememberPreference
import com.dot.gallery.core.ui.util.supportsBlur
import com.dot.gallery.domain.settings.SettingsKeys
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.LocalHazeBlurStyle
import dev.chrisbanes.haze.blur.materials.HazeMaterials
import dev.chrisbanes.haze.hazeSource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.dot.gallery.core.presentation.util.hazeEffectScaled
import com.dot.gallery.core.presentation.util.rememberBottomBarBlurStyle
import com.dot.gallery.ui.theme.GalleryGlass
import com.dot.gallery.feature.setup.generated.resources.setup_skip
import com.dot.gallery.feature.setup.generated.resources.setup_step_label
import com.dot.gallery.feature.setup.generated.resources.Res
import com.dot.gallery.feature.setup.generated.resources.back_cd
import com.dot.gallery.feature.setup.generated.resources.setup_step_progress


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
    val allowBlur by rememberPreference(SettingsKeys.allowBlur.withDefault(supportsBlur))

    Box(modifier = modifier.fillMaxSize()) {
        if (!backgroundHandledExternally) {
            SetupAnimatedBackground(modifier = Modifier.hazeSource(hazeState))
        }

        CompositionLocalProvider(
            LocalHazeState provides hazeState,
            LocalHazeBlurStyle provides HazeMaterials.regular(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ).then { blurEnabled(allowBlur) }
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
                            contentDescription = stringResource(Res.string.back_cd),
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
            text = stringResource(Res.string.setup_step_progress, stepNumber, totalSteps),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// =================================================================================================
// Setup v2 frame: blobs + frosted glass
// =================================================================================================

/** The expressive shape heading a setup step. */
data class SetupHero(
    val icon: ImageVector,
    val shape: Shape,
    val containerColor: Color,
    val contentColor: Color,
)

/**
 * Frame of a setup v2 step: glass back button, step dots and an optional Skip pill on top; an
 * expressive [hero] shape, "Step n of N · Name", a 32/40 [title] and [subtitle]; the step's
 * [content] (glass panels); and a transparent [bottomBar] that blurs the backdrop under it. The
 * blob background and its haze source live in the wizard ([LocalSetupHazeState]).
 */
@Composable
fun SetupStepScaffold(
    modifier: Modifier = Modifier,
    stepNumber: Int = 0,
    totalSteps: Int = 0,
    stepName: String? = null,
    hero: SetupHero? = null,
    title: String? = null,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    onSkip: (() -> Unit)? = null,
    centered: Boolean = false,
    bottomBar: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val hazeState = LocalSetupHazeState.current
    val horizontal = if (centered) Alignment.CenterHorizontally else Alignment.Start
    val textAlign = if (centered) TextAlign.Center else TextAlign.Start
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(top = 64.dp)
                .navigationBarsPadding()
                .padding(bottom = 112.dp)
                .fillMaxWidth()
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = 600.dp)
                .padding(horizontal = 16.dp),
            horizontalAlignment = horizontal,
        ) {
            // Entrance order: hero, step label, title, subtitle, then the page's own content.
            var order = 0
            if (hero != null) {
                order++
                Box(
                    modifier = Modifier
                        .padding(top = 8.dp, bottom = 16.dp)
                        .setupHeroMotion()
                        .size(96.dp)
                        .clip(hero.shape)
                        .background(hero.containerColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(hero.icon, contentDescription = null, tint = hero.contentColor, modifier = Modifier.size(40.dp))
                }
            }
            if (stepName != null) {
                Text(
                    text = stringResource(Res.string.setup_step_label, stepNumber, totalSteps, stepName),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.setupStaggered(order++),
                )
            }
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = textAlign,
                    modifier = Modifier
                        .setupStaggered(order++)
                        .padding(top = 4.dp)
                        .semantics { heading() },
                )
            }
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = textAlign,
                    modifier = Modifier
                        .setupStaggered(order++)
                        .padding(top = 8.dp, bottom = 20.dp),
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .setupStaggered(order),
                horizontalAlignment = horizontal,
                content = content,
            )
        }

        // Top chrome: round glass buttons only, no bar behind them.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(48.dp)) {
                if (onBack != null) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .setupSharedElement("setup-back-button")
                            .size(48.dp)
                            .background(GalleryGlass.chrome, CircleShape),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.back_cd),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                if (totalSteps > 0 && stepNumber > 0) {
                    SetupStepDots(
                        modifier = Modifier.setupSharedBounds("setup-progress"),
                        stepNumber = stepNumber,
                        totalSteps = totalSteps,
                    )
                }
            }
            Box(Modifier.heightIn(min = 48.dp).widthIn(min = 48.dp), contentAlignment = Alignment.CenterEnd) {
                if (onSkip != null) {
                    TextButton(
                        onClick = onSkip,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .background(GalleryGlass.chrome, RoundedCornerShape(24.dp)),
                    ) {
                        Text(stringResource(Res.string.setup_skip), color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }

        // Transparent bottom bar: progressive blur of the backdrop, no tint.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .then(
                    if (hazeState != null) Modifier.hazeEffectScaled(hazeState, rememberBottomBarBlurStyle())
                    else Modifier
                )
                .navigationBarsPadding()
                .setupSharedBounds("setup-bottom-bar")
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            bottomBar()
        }
    }
}

/** Back (or Not now) as a text button on the left, the primary action as a 56 dp pill on the right. */
@Composable
fun SetupBottomActions(
    primaryLabel: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    primaryEnabled: Boolean = true,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (secondaryLabel != null && onSecondary != null) {
            TextButton(onClick = onSecondary, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(secondaryLabel, style = MaterialTheme.typography.labelLarge)
            }
        }
        Spacer(Modifier.weight(1f))
        SetupPrimaryButton(label = primaryLabel, onClick = onPrimary, enabled = primaryEnabled)
    }
}

/** The 56 dp primary pill of a setup step, with a leading arrow. */
@Composable
fun SetupPrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(28.dp),
        contentPadding = PaddingValues(horizontal = 24.dp),
        modifier = modifier.heightIn(min = 56.dp),
    ) {
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

/** Step dots: done and current steps in primary, the current one stretched into a pill. */
@Composable
private fun SetupStepDots(stepNumber: Int, totalSteps: Int, modifier: Modifier = Modifier) {
    val description = stringResource(Res.string.setup_step_progress, stepNumber, totalSteps)
    Row(
        modifier = modifier.semantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (index in 1..totalSteps) {
            val color by animateColorAsState(
                if (index <= stepNumber) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceContainerHighest,
                label = "setup-dot-$index",
            )
            val width by animateDpAsState(if (index == stepNumber) 24.dp else 8.dp, label = "setup-dot-w-$index")
            Box(
                modifier = Modifier
                    .height(8.dp)
                    .width(width)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color)
            )
        }
    }
}
