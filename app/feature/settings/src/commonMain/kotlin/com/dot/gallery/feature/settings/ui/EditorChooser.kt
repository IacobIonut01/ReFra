/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.ui.components.PreferencePreviewStage
import com.dot.gallery.core.ui.components.PreferencePreviewTile

/** One tappable editor choice: [value] is what `onSelect` reports (a sentinel or package name). */
data class EditorChoice(val value: String, val label: String)

/**
 * Two-column grid of editor choices for the Editors settings page. Each tile is a
 * [PreferencePreviewTile] — the whole tile is the radio-button target — with the app
 * icon in a circle above a wrapping label. [icon] draws the icon for a choice value;
 * the platform side resolves package names (and the built-in sentinels) to artwork.
 */
@Composable
fun EditorChooser(
    options: List<EditorChoice>,
    selected: String,
    onSelect: (String) -> Unit,
    icon: @Composable (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    PreferencePreviewStage(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEach { choice ->
                        PreferencePreviewTile(
                            selected = selected == choice.value,
                            label = choice.label,
                            onClick = { onSelect(choice.value) },
                            canvasHeight = 96.dp,
                            expandCanvas = true,
                            labelMaxLines = Int.MAX_VALUE,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .testTag("EditorChoice.${choice.value}"),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                icon(choice.value)
                            }
                        }
                    }
                    if (row.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
