/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.presentation.components.GalleryPageTopBar
import com.dot.gallery.core.presentation.util.hazeEffectScaled
import com.dot.gallery.core.presentation.util.rememberBottomBarBlurStyle
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/** One button of the [WizardScaffold] bottom bar. */
data class WizardAction(
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
)

/**
 * The kit's Wizard template: top bar, a 4 dp segmented step bar, "Step n of N · Name" with a
 * 32/40 title, the step's [content], and a transparent bottom bar with a text [secondary]
 * (Back / Cancel) and a filled [primary] action. The bottom bar blurs the content under it and
 * the body keeps about 112 dp clear above it. Children lay themselves out like settings rows
 * ([settingsContentWidth], or [SettingsItem]'s own paddings). A null [step] hides the step header (progress and
 * result pages).
 */
@Composable
fun WizardScaffold(
    title: String,
    modifier: Modifier = Modifier,
    step: Int? = null,
    stepCount: Int = 0,
    stepName: String? = null,
    heading: String? = null,
    primary: WizardAction? = null,
    secondary: WizardAction? = null,
    onBack: (() -> Unit)? = null,
    scrollState: ScrollState = rememberScrollState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    // The page's only haze source; both transparent bars blur it.
    val hazeState = rememberHazeState()
    val scrolled by remember(scrollState) { derivedStateOf { scrollState.value > 0 } }
    val hasBottomBar = primary != null || secondary != null
    Scaffold(
        modifier = modifier.semantics { paneTitle = title },
        topBar = {
            Column {
                GalleryPageTopBar(title = title, onBack = onBack, hazeState = hazeState, scrolled = scrolled)
                if (step != null && stepCount > 0) {
                    WizardStepBar(step = step, stepCount = stepCount)
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(hazeState)
                    .verticalScroll(scrollState)
                    .padding(top = padding.calculateTopPadding()),
                // Children size themselves like settings rows (settingsContentWidth).
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (step != null && stepName != null) {
                    Text(
                        text = stepName,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .settingsContentWidth()
                            .padding(start = 8.dp, top = 16.dp),
                    )
                }
                if (heading != null) {
                    Text(
                        text = heading,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .settingsContentWidth()
                            .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 20.dp)
                            .semantics { heading() },
                    )
                } else {
                    Spacer(Modifier.height(8.dp))
                }
                content()
                Spacer(
                    Modifier
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .height(if (hasBottomBar) 112.dp else 24.dp)
                )
            }
            if (hasBottomBar) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .hazeEffectScaled(hazeState, rememberBottomBarBlurStyle())
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .wrapContentWidth(Alignment.CenterHorizontally)
                        .widthIn(max = 632.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (secondary != null) {
                        TextButton(
                            onClick = secondary.onClick,
                            enabled = secondary.enabled,
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) { Text(secondary.label, style = MaterialTheme.typography.labelLarge) }
                    }
                    Spacer(Modifier.weight(1f))
                    if (primary != null) {
                        Button(
                            onClick = primary.onClick,
                            enabled = primary.enabled,
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) { Text(primary.label, style = MaterialTheme.typography.labelLarge) }
                    }
                }
            }
        }
    }
}

/** The 4 dp segmented progress bar under a wizard's top bar. */
@Composable
fun WizardStepBar(step: Int, stepCount: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .wrapContentWidth(Alignment.CenterHorizontally)
            .widthIn(max = 632.dp)
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(step.toFloat(), 0f..stepCount.toFloat(), stepCount)
            },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(stepCount) { index ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (index < step) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainerHighest
                    )
            )
        }
    }
}
