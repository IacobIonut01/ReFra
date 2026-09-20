/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.classifier

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Scanner
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.R
import com.dot.gallery.core.Position
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.core.Settings.Misc.rememberNoClassification
import com.dot.gallery.core.ml.ModelStatus
import com.dot.gallery.feature_node.data.data_source.SmartScanStatus
import com.dot.gallery.feature_node.presentation.settings.components.SettingsItem
import com.dot.gallery.feature_node.presentation.settings.components.SwitchPreferenceDetailScreen
import com.dot.gallery.feature_node.presentation.settings.subsettings.label

@Composable
fun CategoriesSettingsScreen() {
    val viewModel = hiltViewModel<CategoriesViewModel>()

    val categoryScan by viewModel.categoryScan.collectAsStateWithLifecycle()
    val categoryWorkerProgress by viewModel.categoryWorkerProgress.collectAsStateWithLifecycle()
    val categoriesWithCount by viewModel.categoriesWithCount.collectAsStateWithLifecycle()
    val modelStatus by viewModel.modelStatus.collectAsStateWithLifecycle()
    val isModelReady = modelStatus == ModelStatus.READY

    val isCategoryWorkerRunning = categoryScan != null
    var noClassification by rememberNoClassification()

    val description = stringResource(R.string.disclaimer_classification)

    SwitchPreferenceDetailScreen(
        title = stringResource(R.string.categories_settings),
        isChecked = !noClassification,
        onCheckedChange = { noClassification = !it },
        switchLabel = stringResource(R.string.categorise_your_media),
        description = description,
        customContent = {
            Column {
                // Scanner button
                SettingsItem(
                    item = SettingsEntity.Preference(
                        title = if (isCategoryWorkerRunning)
                            stringResource(R.string.scanning_media)
                        else
                            stringResource(R.string.scan_for_new_categories),
                        summary = if (!isModelReady)
                            stringResource(R.string.ai_models_not_available)
                        else null,
                        icon = Icons.Outlined.Scanner,
                        screenPosition = if (categoriesWithCount.isNotEmpty() || isCategoryWorkerRunning)
                            Position.Top else Position.Alone
                    ),
                    modifier = Modifier
                        .alpha(if (isModelReady) 1f else 0.5f)
                        .combinedClickable(
                            enabled = isModelReady,
                            onLongClick = {
                                if (isCategoryWorkerRunning) viewModel.stopCategoryClassification()
                            },
                            onClick = {
                                if (!isCategoryWorkerRunning) viewModel.startCategoryClassification()
                            }
                        )
                )

                // Progress indicator when scanning
                AnimatedVisibility(visible = isCategoryWorkerRunning) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .animateContentSize()
                    ) {
                        if (categoryWorkerProgress < 100f) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                progress = { (categoryWorkerProgress / 100f).coerceAtLeast(0f) },
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                        categoryScan?.let { run ->
                            Text(
                                text = when (run.status) {
                                    SmartScanStatus.RUNNING ->
                                        run.currentPhase?.label() ?: run.status.label()
                                    else -> run.status.label()
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                            )
                        }
                    }
                }

                // Stop action while a scan is visible
                if (isCategoryWorkerRunning) {
                    SettingsItem(
                        item = SettingsEntity.Preference(
                            title = stringResource(R.string.categories_stop_scan),
                            summary = stringResource(R.string.categories_stop_scan_summary),
                            onClick = viewModel::stopCategoryClassification,
                            screenPosition = Position.Bottom
                        )
                    )
                }

                // Reset categories
                if (categoriesWithCount.isNotEmpty() && !isCategoryWorkerRunning) {
                    SettingsItem(
                        item = SettingsEntity.Preference(
                            title = stringResource(R.string.reset_categories),
                            summary = stringResource(R.string.reset_categories_summary),
                            icon = Icons.Default.Refresh,
                            onClick = viewModel::resetCategories,
                            screenPosition = Position.Bottom
                        ),
                        backgroundColor = MaterialTheme.colorScheme.errorContainer
                    )
                }
            }
        }
    )
}
