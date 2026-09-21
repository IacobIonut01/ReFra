/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.mediaview.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dot.gallery.R
import com.dot.gallery.core.Settings
import com.dot.gallery.ui.core.Icons as GalleryIcons
import com.dot.gallery.ui.core.icons.VisualSearch

/**
 * Ask-mode prompt for formats a visual-search provider likely can't decode (RAW, TIFF, PSD, JP2,
 * JXL, SVG…). The original is never touched — conversion writes a cache-dir temp copy.
 *
 * [onConvert]/[onSendOriginal] receive `remember = true` when the user armed the "remember"
 * switch so the caller can persist `always`/`never` and skip the sheet next time.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisualSearchConvertSheet(
    mediaLabel: String,
    providerLabel: String,
    convertFormat: String,
    onFormatChange: (String) -> Unit,
    onConvert: (remember: Boolean) -> Unit,
    onSendOriginal: (remember: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    var rememberChoice by rememberSaveable { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(56.dp),
            ) {
                Icon(
                    imageVector = GalleryIcons.VisualSearch,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier
                        .padding(14.dp)
                        .size(28.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.visual_search_convert_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(
                    R.string.visual_search_convert_sheet_body,
                    mediaLabel,
                    providerLabel,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.visual_search_convert_format_header),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    Settings.Misc.VISUAL_SEARCH_FORMAT_JPEG to "JPEG",
                    Settings.Misc.VISUAL_SEARCH_FORMAT_PNG to "PNG",
                    Settings.Misc.VISUAL_SEARCH_FORMAT_WEBP to "WebP",
                ).forEach { (value, label) ->
                    FilterChip(
                        selected = convertFormat == value,
                        onClick = { onFormatChange(value) },
                        label = { Text(label) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.visual_search_convert_sheet_remember),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = rememberChoice,
                    onCheckedChange = { rememberChoice = it },
                )
            }
            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    onClick = { onSendOriginal(rememberChoice) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.visual_search_convert_sheet_original))
                }
                Button(
                    onClick = { onConvert(rememberChoice) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.visual_search_convert_sheet_convert))
                }
            }
        }
    }
}
