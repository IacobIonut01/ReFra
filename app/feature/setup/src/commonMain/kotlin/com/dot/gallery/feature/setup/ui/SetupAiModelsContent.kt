/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.setup.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.ml.ModelGroup
import com.dot.gallery.core.ml.ModelStatus
import com.dot.gallery.core.presentation.components.SetupButton
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.feature.setup.generated.resources.Res
import com.dot.gallery.feature.setup.generated.resources.ai_models_downloading
import com.dot.gallery.feature.setup.generated.resources.ai_models_manager
import com.dot.gallery.feature.setup.generated.resources.setup_ai_download
import com.dot.gallery.feature.setup.generated.resources.setup_ai_download_size
import com.dot.gallery.feature.setup.generated.resources.setup_ai_extra_cutout_desc
import com.dot.gallery.feature.setup.generated.resources.setup_ai_extra_cutout_download
import com.dot.gallery.feature.setup.generated.resources.setup_ai_extra_cutout_ready
import com.dot.gallery.feature.setup.generated.resources.setup_ai_extra_cutout_size
import com.dot.gallery.feature.setup.generated.resources.setup_ai_extra_cutout_title
import com.dot.gallery.feature.setup.generated.resources.setup_ai_extra_title
import com.dot.gallery.feature.setup.generated.resources.setup_ai_faces_blur_desc
import com.dot.gallery.feature.setup.generated.resources.setup_ai_faces_blur_download
import com.dot.gallery.feature.setup.generated.resources.setup_ai_faces_blur_ready
import com.dot.gallery.feature.setup.generated.resources.setup_ai_faces_blur_size
import com.dot.gallery.feature.setup.generated.resources.setup_ai_faces_blur_title
import com.dot.gallery.feature.setup.generated.resources.setup_ai_faces_people_desc
import com.dot.gallery.feature.setup.generated.resources.setup_ai_faces_people_download
import com.dot.gallery.feature.setup.generated.resources.setup_ai_faces_people_ready
import com.dot.gallery.feature.setup.generated.resources.setup_ai_faces_people_size
import com.dot.gallery.feature.setup.generated.resources.setup_ai_faces_people_title
import com.dot.gallery.feature.setup.generated.resources.setup_ai_faces_title
import com.dot.gallery.feature.setup.generated.resources.setup_ai_feature_categories_desc
import com.dot.gallery.feature.setup.generated.resources.setup_ai_feature_categories_title
import com.dot.gallery.feature.setup.generated.resources.setup_ai_feature_private_desc
import com.dot.gallery.feature.setup.generated.resources.setup_ai_feature_private_title
import com.dot.gallery.feature.setup.generated.resources.setup_ai_feature_search_desc
import com.dot.gallery.feature.setup.generated.resources.setup_ai_feature_search_title
import com.dot.gallery.feature.setup.generated.resources.setup_ai_features_title
import com.dot.gallery.feature.setup.generated.resources.setup_ai_no_internet
import com.dot.gallery.feature.setup.generated.resources.setup_ai_ready
import com.dot.gallery.feature.setup.generated.resources.setup_ai_subtitle
import com.dot.gallery.feature.setup.generated.resources.setup_ai_title
import com.dot.gallery.feature.setup.generated.resources.setup_continue
import com.dot.gallery.feature.setup.generated.resources.setup_skip_for_now


/**
 * AI models page of the setup wizard: install state per model group, download actions,
 * and the feature rundown. Model downloads are fired via [SetupAiModelsActions.onDownload]
 * and run in the background app-side, so advancing past the page never blocks on them.
 */
@Composable
fun SetupAiModelsContent(
    state: SetupAiModelsState,
    actions: SetupAiModelsActions,
    stepNumber: Int,
    totalSteps: Int,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ready = state.search.status == ModelStatus.READY
    val downloading = state.search.status == ModelStatus.DOWNLOADING ||
        state.search.status == ModelStatus.COPYING

    SetupWizardScaffold(
        modifier = modifier,
        showBack = true,
        onBack = onBack,
        stepNumber = stepNumber,
        totalSteps = totalSteps,
        title = stringResource(Res.string.setup_ai_title),
        subtitle = stringResource(Res.string.setup_ai_subtitle),
        bottomBar = {
            // When the models are ready, bundled, or actively downloading there is something
            // to continue with (downloads keep running in the background). Otherwise the only
            // meaningful action is to skip for now, so a single button is shown.
            val canContinue = ready || downloading || state.isBundled
            if (canContinue) {
                SetupButton(
                    text = stringResource(Res.string.setup_continue),
                    applyHorizontalPadding = false,
                    applyBottomPadding = false,
                    applyInsets = false,
                    applyNavigationPadding = false,
                    onClick = onNext
                )
            } else {
                SetupButton(
                    text = stringResource(Res.string.setup_skip_for_now),
                    applyHorizontalPadding = false,
                    applyBottomPadding = false,
                    applyInsets = false,
                    applyNavigationPadding = false,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    onClick = onSkip
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SetupSectionCard(title = stringResource(Res.string.ai_models_manager)) {
                when {
                    ready -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                modifier = Modifier.padding(start = 8.dp),
                                text = stringResource(Res.string.setup_ai_ready),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    downloading -> {
                        Text(
                            text = stringResource(Res.string.ai_models_downloading),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        LinearProgressIndicator(
                            progress = { state.search.progress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    }
                    !state.hasInternet && !state.isBundled -> {
                        Text(
                            text = stringResource(Res.string.setup_ai_no_internet),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    else -> {
                        DownloadRow(onClick = { actions.onDownload(state.searchGroup) })
                    }
                }
            }

            FeaturesCard()

            // Subject Cutout is an optional extra: it uses a separate model set (MobileSAM) and can
            // be added independently of the core search/categories models.
            CutoutExtraCard(
                model = state.cutout,
                isBundled = state.isBundled,
                hasInternet = state.hasInternet,
                onDownload = { actions.onDownload(ModelGroup.CUTOUT) }
            )

            // Faces & People is another optional extra: a small detector powers editor face-blur
            // and a larger recognizer enables on-device People grouping.
            FacesExtraCard(
                detect = state.faceDetect,
                recognition = state.faceRecognition,
                isBundled = state.isBundled,
                hasInternet = state.hasInternet,
                onDownloadDetect = { actions.onDownload(ModelGroup.FACE_DETECT) },
                onDownloadRecog = { actions.onDownload(ModelGroup.FACE_RECOGNITION) }
            )
        }
    }
}

@Composable
private fun FacesExtraCard(
    detect: SetupModelUi,
    recognition: SetupModelUi,
    isBundled: Boolean,
    hasInternet: Boolean,
    onDownloadDetect: () -> Unit,
    onDownloadRecog: () -> Unit
) {
    SetupSectionCard(title = stringResource(Res.string.setup_ai_faces_title)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FeatureRow(
                icon = Icons.Outlined.Face,
                title = stringResource(Res.string.setup_ai_faces_blur_title),
                description = stringResource(Res.string.setup_ai_faces_blur_desc)
            )
            ModelStatusView(
                model = detect,
                isBundled = isBundled,
                hasInternet = hasInternet,
                readyText = stringResource(Res.string.setup_ai_faces_blur_ready),
                downloadTitle = stringResource(Res.string.setup_ai_faces_blur_download),
                downloadSubtitle = stringResource(Res.string.setup_ai_faces_blur_size),
                downloadIcon = Icons.Outlined.Face,
                onDownload = onDownloadDetect
            )
            FeatureRow(
                icon = Icons.Outlined.Groups,
                title = stringResource(Res.string.setup_ai_faces_people_title),
                description = stringResource(Res.string.setup_ai_faces_people_desc)
            )
            ModelStatusView(
                model = recognition,
                isBundled = isBundled,
                hasInternet = hasInternet,
                readyText = stringResource(Res.string.setup_ai_faces_people_ready),
                downloadTitle = stringResource(Res.string.setup_ai_faces_people_download),
                downloadSubtitle = stringResource(Res.string.setup_ai_faces_people_size),
                downloadIcon = Icons.Outlined.Groups,
                onDownload = onDownloadRecog
            )
        }
    }
}

/** Renders the ready / downloading / no-internet / download states for a single model group. */
@Composable
private fun ModelStatusView(
    model: SetupModelUi,
    isBundled: Boolean,
    hasInternet: Boolean,
    readyText: String,
    downloadTitle: String,
    downloadSubtitle: String,
    downloadIcon: ImageVector,
    onDownload: () -> Unit
) {
    // Bundled builds treat an in-flight COPYING as ready, but a user-deleted group
    // (NOT_INSTALLED) must show the install row again (issue #1229).
    val ready = model.status == ModelStatus.READY || (isBundled && model.status == ModelStatus.COPYING)
    val downloading = model.status == ModelStatus.DOWNLOADING || model.status == ModelStatus.COPYING
    when {
        ready -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    modifier = Modifier.padding(start = 8.dp),
                    text = readyText,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        downloading -> {
            Text(
                text = stringResource(Res.string.ai_models_downloading),
                style = MaterialTheme.typography.bodyMedium
            )
            LinearProgressIndicator(
                progress = { model.progress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )
        }
        !hasInternet && !isBundled -> {
            Text(
                text = stringResource(Res.string.setup_ai_no_internet),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        else -> {
            DownloadRow(
                onClick = onDownload,
                title = downloadTitle,
                subtitle = downloadSubtitle,
                icon = downloadIcon
            )
        }
    }
}

@Composable
private fun CutoutExtraCard(
    model: SetupModelUi,
    isBundled: Boolean,
    hasInternet: Boolean,
    onDownload: () -> Unit
) {
    val ready = model.status == ModelStatus.READY || (isBundled && model.status == ModelStatus.COPYING)
    val downloading = model.status == ModelStatus.DOWNLOADING || model.status == ModelStatus.COPYING
    SetupSectionCard(title = stringResource(Res.string.setup_ai_extra_title)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FeatureRow(
                icon = Icons.Outlined.ContentCut,
                title = stringResource(Res.string.setup_ai_extra_cutout_title),
                description = stringResource(Res.string.setup_ai_extra_cutout_desc)
            )
            when {
                ready -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            modifier = Modifier.padding(start = 8.dp),
                            text = stringResource(Res.string.setup_ai_extra_cutout_ready),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                downloading -> {
                    Text(
                        text = stringResource(Res.string.ai_models_downloading),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    LinearProgressIndicator(
                        progress = { model.progress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                }
                !hasInternet && !isBundled -> {
                    Text(
                        text = stringResource(Res.string.setup_ai_no_internet),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> {
                    DownloadRow(
                        onClick = onDownload,
                        title = stringResource(Res.string.setup_ai_extra_cutout_download),
                        subtitle = stringResource(Res.string.setup_ai_extra_cutout_size),
                        icon = Icons.Outlined.ContentCut
                    )
                }
            }
        }
    }
}

@Composable
private fun FeaturesCard() {
    SetupSectionCard(title = stringResource(Res.string.setup_ai_features_title)) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            FeatureRow(
                icon = Icons.Outlined.Category,
                title = stringResource(Res.string.setup_ai_feature_categories_title),
                description = stringResource(Res.string.setup_ai_feature_categories_desc)
            )
            FeatureRow(
                icon = Icons.Outlined.TravelExplore,
                title = stringResource(Res.string.setup_ai_feature_search_title),
                description = stringResource(Res.string.setup_ai_feature_search_desc)
            )
            FeatureRow(
                icon = Icons.Outlined.Lock,
                title = stringResource(Res.string.setup_ai_feature_private_title),
                description = stringResource(Res.string.setup_ai_feature_private_desc)
            )
        }
    }
}

@Composable
private fun FeatureRow(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(22.dp)
            )
        }
        Column(modifier = Modifier.padding(start = 14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DownloadRow(
    onClick: () -> Unit,
    title: String = stringResource(Res.string.setup_ai_download),
    subtitle: String = stringResource(Res.string.setup_ai_download_size),
    icon: ImageVector = Icons.Outlined.AutoAwesome
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(22.dp)
        )
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
        }
    }
}
