/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.help.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.Position
import com.dot.gallery.core.presentation.components.SheetIconBadge
import com.dot.gallery.core.presentation.components.sheetGroupedShape
import com.dot.gallery.core.presentation.components.sheetRowPosition
import com.dot.gallery.feature.help.domain.HelpRepository
import com.dot.gallery.feature.help.domain.HelpRoute
import com.dot.gallery.feature.help.domain.PreviewType
import com.dot.gallery.feature.help.domain.TutorialPage
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.feature.help.generated.resources.Res
import com.dot.gallery.feature.help.generated.resources.help_not_found
import com.dot.gallery.feature.help.generated.resources.help_open_settings
import com.dot.gallery.feature.help.generated.resources.help_quick_actions
import com.dot.gallery.feature.help.generated.resources.help_tips_fallback
import com.dot.gallery.core.presentation.components.GalleryPageTopBar
import com.dot.gallery.core.presentation.components.rememberScrolledUnderTopBar
import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.hazeSource

/**
 * Step-by-step detail for a single [com.dot.gallery.feature.help.domain.HelpTip].
 *
 * Live-component previews are host-rendered: [preview] maps a [PreviewType]
 * to the app-side mini composable (the real screens live app-side), so the
 * module stays platform-free. Pass `{}` to skip previews.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TutorialDetailContent(
    tipId: String,
    onNavigate: (HelpRoute) -> Unit,
    preview: @Composable (PreviewType) -> Unit = {},
) {
    val scrollState = rememberScrollState()
    // Tip previews render real app chrome that may blur LocalHazeState; the bar keeps its own state.
    val hazeState = rememberHazeState()

    val tip = remember(tipId) { HelpRepository.getTip(tipId) }

    if (tip == null) {
        Scaffold(
            topBar = { GalleryPageTopBar(title = stringResource(Res.string.help_tips_fallback)) }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(Res.string.help_not_found))
            }
        }
        return
    }

    Scaffold(
        topBar = {
            GalleryPageTopBar(
                title = stringResource(tip.title),
                hazeState = hazeState,
                scrolled = rememberScrolledUnderTopBar(scrollState),
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
                .verticalScroll(scrollState)
                .padding(
                    start = padding.calculateStartPadding(LocalLayoutDirection.current),
                    end = padding.calculateEndPadding(LocalLayoutDirection.current),
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding()
                )
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            TutorialHeader(
                icon = tip.icon.vector,
                subtitle = stringResource(tip.subtitle)
            )
            tip.pages.forEach { page ->
                TutorialPageContent(page = page, preview = preview)
            }

            if (tip.quickActions.isNotEmpty()) {
                Text(
                    text = stringResource(Res.string.help_quick_actions),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() }
                )
                tip.quickActions.forEach { action ->
                    FilledTonalButton(
                        onClick = { onNavigate(action.route) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = action.icon,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(action.label))
                    }
                }
            } else if (tip.deepLink != null) {
                Button(
                    onClick = { onNavigate(tip.deepLink) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(Res.string.help_open_settings))
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun TutorialPageContent(
    page: TutorialPage,
    preview: @Composable (PreviewType) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Page title
        Text(
            text = stringResource(page.title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() }
        )

        // Description
        Text(
            text = stringResource(page.description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Steps
        if (page.steps.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                page.steps.forEachIndexed { index, step ->
                    StepItem(
                        stepNumber = index + 1,
                        text = stringResource(step),
                        position = sheetRowPosition(index, page.steps.size)
                    )
                }
            }
        }

        // Accurate live-component preview (single frame or animated multi-step)
        when {
            page.previewSteps.isNotEmpty() -> {
                Spacer(Modifier.height(8.dp))
                MultiStepPreview(
                    steps = page.previewSteps,
                    captions = page.stepCaptions.map { stringResource(it) },
                    preview = preview,
                )
            }
            page.previewType != PreviewType.NONE -> {
                Spacer(Modifier.height(8.dp))
                preview(page.previewType)
            }
        }
    }
}

/**
 * Walks through [steps] frames automatically, cross-fading between the real
 * component previews. Shows the index-aligned [captions] entry when present.
 */
@Composable
private fun MultiStepPreview(
    steps: List<PreviewType>,
    captions: List<String>,
    preview: @Composable (PreviewType) -> Unit,
    modifier: Modifier = Modifier
) {
    val animation = rememberPreviewAnimation(stepCount = steps.size)
    val index = animation.currentStep.coerceIn(0, steps.lastIndex)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Crossfade(targetState = index, label = "help_multi_step") { frame ->
            preview(steps[frame])
        }
        captions.getOrNull(index)?.let { caption ->
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** Tip badge and one-line summary under the large title. */
@Composable
private fun TutorialHeader(
    icon: ImageVector?,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) SheetIconBadge(icon = icon)
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}

/** One numbered step, drawn as a row of a grouped list. */
@Composable
private fun StepItem(
    stepNumber: Int,
    text: String,
    position: Position,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(sheetGroupedShape(position))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$stepNumber",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}
