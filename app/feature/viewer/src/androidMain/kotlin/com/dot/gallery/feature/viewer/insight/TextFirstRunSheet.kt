/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.viewer.insight

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.presentation.components.ModalSheet
import com.dot.gallery.core.ui.util.AppBottomSheetState
import com.dot.gallery.feature.viewer.generated.resources.Res
import com.dot.gallery.feature.viewer.generated.resources.text_learn_more
import com.dot.gallery.feature.viewer.generated.resources.text_needs_pack_body
import com.dot.gallery.feature.viewer.generated.resources.text_needs_pack_body_offline
import com.dot.gallery.feature.viewer.generated.resources.text_needs_pack_card_title
import com.dot.gallery.feature.viewer.generated.resources.text_needs_pack_choose
import com.dot.gallery.feature.viewer.generated.resources.text_needs_pack_note
import com.dot.gallery.feature.viewer.generated.resources.text_needs_pack_subtitle
import com.dot.gallery.feature.viewer.generated.resources.text_needs_pack_title
import com.dot.gallery.feature.viewer.generated.resources.text_not_now
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * Shown when the person asks for text with no language pack ready. It explains packs and offers
 * Choose languages, which opens the pack list; nothing is downloaded or preselected from here.
 * [downloadsAllowed] is false in builds without internet access, where packs are added from files.
 */
@Composable
fun TextFirstRunSheet(
    sheetState: AppBottomSheetState,
    downloadsAllowed: Boolean,
    onChooseLanguages: () -> Unit,
    onLearnMore: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme
    val close: (() -> Unit) -> Unit = { then -> scope.launch { sheetState.hide(); then() } }
    ModalSheet(
        sheetState = sheetState,
        content = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(
                        Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(colors.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Outlined.Translate, contentDescription = null, tint = colors.onPrimaryContainer) }
                    Column {
                        Text(
                            stringResource(Res.string.text_needs_pack_title),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.semantics { heading() },
                        )
                        Text(
                            stringResource(Res.string.text_needs_pack_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(colors.surfaceContainerHigh).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(Res.string.text_needs_pack_card_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(if (downloadsAllowed) Res.string.text_needs_pack_body else Res.string.text_needs_pack_body_offline),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                    if (downloadsAllowed) {
                        Spacer(Modifier.size(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(18.dp), tint = colors.onSurfaceVariant)
                            Text(stringResource(Res.string.text_needs_pack_note), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        }
                    }
                }
                Button(
                    onClick = { close(onChooseLanguages) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) {
                    Icon(Icons.Outlined.Translate, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(Res.string.text_needs_pack_choose))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { close {} }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                        Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(Res.string.text_not_now))
                    }
                    FilledTonalButton(onClick = { close(onLearnMore) }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                        Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(Res.string.text_learn_more))
                    }
                }
            }
        },
    )
}
