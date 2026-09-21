/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.setup.pages

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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.BuildConfig
import com.dot.gallery.R
import com.dot.gallery.core.ml.ModelGroup
import com.dot.gallery.core.ml.ModelStatus
import com.dot.gallery.core.presentation.components.SetupButton
import com.dot.gallery.feature_node.presentation.settings.subsettings.SmartFeaturesViewModel
import com.dot.gallery.feature_node.presentation.setup.components.SetupSectionCard
import com.dot.gallery.feature_node.presentation.setup.components.SetupWizardScaffold

@Composable
fun SetupAiModelsPage(
    stepNumber: Int,
    totalSteps: Int,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    viewModel: SmartFeaturesViewModel = hiltViewModel()
) {
    val modelStatus by viewModel.modelStatus(ModelGroup.SEARCH).collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress(ModelGroup.SEARCH).collectAsStateWithLifecycle()
    val cutoutStatus by viewModel.modelStatus(ModelGroup.CUTOUT).collectAsStateWithLifecycle()
    val cutoutProgress by viewModel.downloadProgress(ModelGroup.CUTOUT).collectAsStateWithLifecycle()
    val faceDetectStatus by viewModel.modelStatus(ModelGroup.FACE_DETECT).collectAsStateWithLifecycle()
    val faceDetectProgress by viewModel.downloadProgress(ModelGroup.FACE_DETECT).collectAsStateWithLifecycle()
    val faceRecogStatus by viewModel.modelStatus(ModelGroup.FACE_RECOGNITION).collectAsStateWithLifecycle()
    val faceRecogProgress by viewModel.downloadProgress(ModelGroup.FACE_RECOGNITION).collectAsStateWithLifecycle()
    val isBundled = BuildConfig.ML_MODELS_BUNDLED
    val hasInternet = viewModel.hasInternetPermission
    val ready = modelStatus == ModelStatus.READY
    val downloading = modelStatus == ModelStatus.DOWNLOADING || modelStatus == ModelStatus.COPYING

    SetupWizardScaffold(
        showBack = true,
        onBack = onBack,
        stepNumber = stepNumber,
        totalSteps = totalSteps,
        title = stringResource(R.string.setup_ai_title),
        subtitle = stringResource(R.string.setup_ai_subtitle),
        bottomBar = {
            // When the models are ready, bundled, or actively downloading there is something
            // to continue with (downloads keep running in the background). Otherwise the only
            // meaningful action is to skip for now, so a single button is shown.
            val canContinue = ready || downloading || isBundled
            if (canContinue) {
                SetupButton(
                    text = stringResource(R.string.setup_continue),
                    applyHorizontalPadding = false,
                    applyBottomPadding = false,
                    applyInsets = false,
                    applyNavigationPadding = false,
                    onClick = onNext
                )
            } else {
                SetupButton(
                    text = stringResource(R.string.setup_skip_for_now),
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
            SetupSectionCard(title = stringResource(R.string.ai_models_manager)) {
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
                                text = stringResource(R.string.setup_ai_ready),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    downloading -> {
                        Text(
                            text = stringResource(R.string.ai_models_downloading),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        LinearProgressIndicator(
                            progress = { downloadProgress / 100f },
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
                            text = stringResource(R.string.setup_ai_no_internet),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    else -> {
                        DownloadRow(onClick = { viewModel.downloadModels(ModelGroup.SEARCH) })
                    }
                }
            }

            FeaturesCard()

            // Subject Cutout is an optional extra: it uses a separate model set (MobileSAM) and can
            // be added independently of the core search/categories models.
            CutoutExtraCard(
                status = cutoutStatus,
                progress = cutoutProgress,
                isBundled = isBundled,
                hasInternet = hasInternet,
                onDownload = { viewModel.downloadModels(ModelGroup.CUTOUT) }
            )

            // Faces & People is another optional extra: a small detector powers editor face-blur
            // and a larger recognizer enables on-device People grouping.
            FacesExtraCard(
                detectStatus = faceDetectStatus,
                detectProgress = faceDetectProgress,
                recogStatus = faceRecogStatus,
                recogProgress = faceRecogProgress,
                isBundled = isBundled,
                hasInternet = hasInternet,
                onDownloadDetect = { viewModel.downloadModels(ModelGroup.FACE_DETECT) },
                onDownloadRecog = { viewModel.downloadModels(ModelGroup.FACE_RECOGNITION) }
            )
        }
    }
}

@Composable
private fun FacesExtraCard(
    detectStatus: ModelStatus,
    detectProgress: Float,
    recogStatus: ModelStatus,
    recogProgress: Float,
    isBundled: Boolean,
    hasInternet: Boolean,
    onDownloadDetect: () -> Unit,
    onDownloadRecog: () -> Unit
) {
    SetupSectionCard(title = stringResource(R.string.setup_ai_faces_title)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FeatureRow(
                icon = Icons.Outlined.Face,
                title = stringResource(R.string.setup_ai_faces_blur_title),
                description = stringResource(R.string.setup_ai_faces_blur_desc)
            )
            ModelStatusView(
                status = detectStatus,
                progress = detectProgress,
                isBundled = isBundled,
                hasInternet = hasInternet,
                readyText = stringResource(R.string.setup_ai_faces_blur_ready),
                downloadTitle = stringResource(R.string.setup_ai_faces_blur_download),
                downloadSubtitle = stringResource(R.string.setup_ai_faces_blur_size),
                downloadIcon = Icons.Outlined.Face,
                onDownload = onDownloadDetect
            )
            FeatureRow(
                icon = Icons.Outlined.Groups,
                title = stringResource(R.string.setup_ai_faces_people_title),
                description = stringResource(R.string.setup_ai_faces_people_desc)
            )
            ModelStatusView(
                status = recogStatus,
                progress = recogProgress,
                isBundled = isBundled,
                hasInternet = hasInternet,
                readyText = stringResource(R.string.setup_ai_faces_people_ready),
                downloadTitle = stringResource(R.string.setup_ai_faces_people_download),
                downloadSubtitle = stringResource(R.string.setup_ai_faces_people_size),
                downloadIcon = Icons.Outlined.Groups,
                onDownload = onDownloadRecog
            )
        }
    }
}

/** Renders the ready / downloading / no-internet / download states for a single model group. */
@Composable
private fun ModelStatusView(
    status: ModelStatus,
    progress: Float,
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
    val ready = status == ModelStatus.READY || (isBundled && status == ModelStatus.COPYING)
    val downloading = status == ModelStatus.DOWNLOADING || status == ModelStatus.COPYING
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
                text = stringResource(R.string.ai_models_downloading),
                style = MaterialTheme.typography.bodyMedium
            )
            LinearProgressIndicator(
                progress = { progress / 100f },
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
                text = stringResource(R.string.setup_ai_no_internet),
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
    status: ModelStatus,
    progress: Float,
    isBundled: Boolean,
    hasInternet: Boolean,
    onDownload: () -> Unit
) {
    val ready = status == ModelStatus.READY || (isBundled && status == ModelStatus.COPYING)
    val downloading = status == ModelStatus.DOWNLOADING || status == ModelStatus.COPYING
    SetupSectionCard(title = stringResource(R.string.setup_ai_extra_title)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FeatureRow(
                icon = Icons.Outlined.ContentCut,
                title = stringResource(R.string.setup_ai_extra_cutout_title),
                description = stringResource(R.string.setup_ai_extra_cutout_desc)
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
                            text = stringResource(R.string.setup_ai_extra_cutout_ready),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                downloading -> {
                    Text(
                        text = stringResource(R.string.ai_models_downloading),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    LinearProgressIndicator(
                        progress = { progress / 100f },
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
                        text = stringResource(R.string.setup_ai_no_internet),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> {
                    DownloadRow(
                        onClick = onDownload,
                        title = stringResource(R.string.setup_ai_extra_cutout_download),
                        subtitle = stringResource(R.string.setup_ai_extra_cutout_size),
                        icon = Icons.Outlined.ContentCut
                    )
                }
            }
        }
    }
}

@Composable
private fun FeaturesCard() {
    SetupSectionCard(title = stringResource(R.string.setup_ai_features_title)) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            FeatureRow(
                icon = Icons.Outlined.Category,
                title = stringResource(R.string.setup_ai_feature_categories_title),
                description = stringResource(R.string.setup_ai_feature_categories_desc)
            )
            FeatureRow(
                icon = Icons.Outlined.TravelExplore,
                title = stringResource(R.string.setup_ai_feature_search_title),
                description = stringResource(R.string.setup_ai_feature_search_desc)
            )
            FeatureRow(
                icon = Icons.Outlined.Lock,
                title = stringResource(R.string.setup_ai_feature_private_title),
                description = stringResource(R.string.setup_ai_feature_private_desc)
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
    title: String = stringResource(R.string.setup_ai_download),
    subtitle: String = stringResource(R.string.setup_ai_download_size),
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
