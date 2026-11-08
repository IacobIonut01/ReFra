/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.ui.space

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.Position
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.core.ui.components.BaseSettingsScreen
import com.dot.gallery.core.presentation.util.add
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.cloud_free_space
import com.dot.gallery.generated.resources.cloud_free_space_1y
import com.dot.gallery.generated.resources.cloud_free_space_30d
import com.dot.gallery.generated.resources.cloud_free_space_60d
import com.dot.gallery.generated.resources.cloud_free_space_6m
import com.dot.gallery.generated.resources.cloud_free_space_90d
import com.dot.gallery.generated.resources.cloud_free_space_auto
import com.dot.gallery.generated.resources.cloud_free_space_auto_frequency
import com.dot.gallery.generated.resources.cloud_free_space_auto_summary
import com.dot.gallery.generated.resources.cloud_free_space_cutoff
import com.dot.gallery.generated.resources.cloud_free_space_description
import com.dot.gallery.generated.resources.cloud_free_space_every_3d
import com.dot.gallery.generated.resources.cloud_free_space_every_day
import com.dot.gallery.generated.resources.cloud_free_space_every_week
import com.dot.gallery.generated.resources.cloud_free_space_keep_favorites
import com.dot.gallery.generated.resources.cloud_free_space_keep_favorites_summary
import com.dot.gallery.generated.resources.cloud_free_space_never
import com.dot.gallery.generated.resources.cloud_free_space_remove_count
import com.dot.gallery.generated.resources.cloud_free_space_scan
import com.dot.gallery.generated.resources.cloud_free_space_scanning



@Composable
fun FreeUpSpaceContent(
    state: FreeUpSpaceUiState,
    onKeepFavorites: (Boolean) -> Unit,
    onCutoffDays: (Int) -> Unit,
    onAutoEnabled: (Boolean) -> Unit,
    onAutoIntervalDays: (Int) -> Unit,
    onScan: () -> Unit,
    onBeginDeletion: () -> Unit,
    onCancelDeletion: () -> Unit,
    initialScrollIndex: Int = 0,
) {
    // "Never" (sentinel -1) is listed first and is the default so nothing is ever
    // removed until the user explicitly opts into an age range.
    val cutoffOptions = listOf(FreeUpSpaceViewModel.NEVER_CUTOFF, 30, 60, 90, 180, 365)
    val keepFavoritesTitle = stringResource(Res.string.cloud_free_space_keep_favorites)
    val keepFavoritesSummary = stringResource(Res.string.cloud_free_space_keep_favorites_summary)
    val cutoffHeader = stringResource(Res.string.cloud_free_space_cutoff)
    val cutoffLabels = mapOf(
        FreeUpSpaceViewModel.NEVER_CUTOFF to stringResource(Res.string.cloud_free_space_never),
        30 to stringResource(Res.string.cloud_free_space_30d),
        60 to stringResource(Res.string.cloud_free_space_60d),
        90 to stringResource(Res.string.cloud_free_space_90d),
        180 to stringResource(Res.string.cloud_free_space_6m),
        365 to stringResource(Res.string.cloud_free_space_1y),
    )
    val scanningTitle = stringResource(Res.string.cloud_free_space_scanning)
    val scanTitle = stringResource(Res.string.cloud_free_space_scan)
    val autoTitle = stringResource(Res.string.cloud_free_space_auto)
    val autoSummary = stringResource(Res.string.cloud_free_space_auto_summary)
    val frequencyHeader = stringResource(Res.string.cloud_free_space_auto_frequency)
    val intervalOptions = listOf(1, 3, 7)
    val intervalLabels = mapOf(
        1 to stringResource(Res.string.cloud_free_space_every_day),
        3 to stringResource(Res.string.cloud_free_space_every_3d),
        7 to stringResource(Res.string.cloud_free_space_every_week),
    )
    val optionsEnabled = state.preferencesLoaded && !state.isScanning && !state.isDeleting
    val resourceStrings = listOf(
        keepFavoritesTitle,
        keepFavoritesSummary,
        cutoffHeader,
        scanningTitle,
        scanTitle,
        autoTitle,
        autoSummary,
        frequencyHeader,
    ) + cutoffLabels.values + intervalLabels.values

    val settingsList = remember(
        state.keepFavorites,
        state.cutoffDays,
        state.autoEnabled,
        state.autoIntervalDays,
        state.preferencesLoaded,
        state.isScanning,
        state.isDeleting,
        resourceStrings,
    ) {
        buildList {
            // Options
            add(SettingsEntity.Header(title = ""))
            add(
                SettingsEntity.SwitchPreference(
                    title = keepFavoritesTitle,
                    summary = keepFavoritesSummary,
                    enabled = optionsEnabled,
                    isChecked = state.keepFavorites,
                    onCheck = { onKeepFavorites(it) },
                    screenPosition = Position.Alone
                )
            )

            // Cutoff period
            add(SettingsEntity.Header(title = cutoffHeader))
            cutoffOptions.forEachIndexed { index, days ->
                val pos = when {
                    cutoffOptions.size == 1 -> Position.Alone
                    index == 0 -> Position.Top
                    index == cutoffOptions.lastIndex -> Position.Bottom
                    else -> Position.Middle
                }
                add(
                    SettingsEntity.Preference(
                        title = cutoffLabels.getValue(days),
                        rightText = if (state.cutoffDays == days) "✓" else null,
                        enabled = optionsEnabled,
                        onClick = { onCutoffDays(days) },
                        screenPosition = pos
                    )
                )
            }

            // Automatic removal
            add(SettingsEntity.Header(title = frequencyHeader))
            add(
                SettingsEntity.SwitchPreference(
                    title = autoTitle,
                    summary = autoSummary,
                    enabled = optionsEnabled,
                    isChecked = state.autoEnabled,
                    onCheck = { onAutoEnabled(it) },
                    screenPosition = if (state.autoEnabled) Position.Top else Position.Alone
                )
            )
            if (state.autoEnabled) {
                intervalOptions.forEachIndexed { index, days ->
                    add(
                        SettingsEntity.Preference(
                            title = intervalLabels.getValue(days),
                            rightText = if (state.autoIntervalDays == days) "✓" else null,
                            enabled = optionsEnabled,
                            onClick = { onAutoIntervalDays(days) },
                            screenPosition = if (index == intervalOptions.lastIndex) {
                                Position.Bottom
                            } else {
                                Position.Middle
                            }
                        )
                    )
                }
            }

            // Scan action
            add(SettingsEntity.Header(title = ""))
            add(
                SettingsEntity.Preference(
                    title = if (state.isScanning) scanningTitle else scanTitle,
                    enabled = optionsEnabled,
                    onClick = { onScan() },
                    screenPosition = Position.Alone
                )
            )
        }.toMutableStateList()
    }

    BaseSettingsScreen(
        title = stringResource(Res.string.cloud_free_space),
        settingsList = settingsList,
        listState = rememberLazyListState(initialFirstVisibleItemIndex = initialScrollIndex),
        topContent = {
            FreeUpSpaceSummaryCard(
                description = stringResource(Res.string.cloud_free_space_description),
                itemCount = state.backedUpItems.size,
                isScanning = state.isScanning,
            )
        },
        bottomContent = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp)
            ) {
                if (state.message.isNotEmpty()) {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                if (state.backedUpItems.isNotEmpty()) {
                    Button(
                        onClick = onBeginDeletion,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        enabled = !state.isDeleting,
                    ) {
                        if (state.isDeleting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(
                                Icons.Outlined.CleaningServices,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(stringResource(Res.string.cloud_free_space_remove_count, state.backedUpItems.size))
                    }
                }
                state.error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    )
}

/**
 * Summary card that opens Free up space: a round badge, what the page does and, once a scan
 * found backed-up items, how many can leave the phone. A progress line shows while scanning.
 */
@Composable
internal fun FreeUpSpaceSummaryCard(
    description: String,
    itemCount: Int,
    isScanning: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.CleaningServices, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        if (itemCount > 0) {
            Text(
                text = stringResource(Res.string.cloud_free_space_remove_count, itemCount),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        if (isScanning) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}
