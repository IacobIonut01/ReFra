/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.setup.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.ml.ModelGroup
import com.dot.gallery.core.ml.ModelStatus
import com.dot.gallery.feature.setup.generated.resources.Res
import com.dot.gallery.feature.setup.generated.resources.setup_ai_download_rest
import com.dot.gallery.feature.setup.generated.resources.setup_ai_footnote
import com.dot.gallery.feature.setup.generated.resources.setup_ai_no_internet
import com.dot.gallery.feature.setup.generated.resources.setup_ai_ready
import com.dot.gallery.feature.setup.generated.resources.setup_ai_subtitle
import com.dot.gallery.feature.setup.generated.resources.setup_ai_title
import com.dot.gallery.feature.setup.generated.resources.setup_continue
import com.dot.gallery.feature.setup.generated.resources.setup_feature_cutout
import com.dot.gallery.feature.setup.generated.resources.setup_feature_cutout_desc
import com.dot.gallery.feature.setup.generated.resources.setup_feature_downloading
import com.dot.gallery.feature.setup.generated.resources.setup_feature_faces
import com.dot.gallery.feature.setup.generated.resources.setup_feature_faces_desc
import com.dot.gallery.feature.setup.generated.resources.setup_feature_not_installed
import com.dot.gallery.feature.setup.generated.resources.setup_feature_ready
import com.dot.gallery.feature.setup.generated.resources.setup_feature_search
import com.dot.gallery.feature.setup.generated.resources.setup_feature_search_desc
import com.dot.gallery.feature.setup.generated.resources.setup_not_now
import com.dot.gallery.feature.setup.generated.resources.setup_step_ai
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private data class SetupFeature(
    val name: String,
    val icon: ImageVector,
    val title: StringResource,
    val description: StringResource,
    val groups: List<Pair<ModelGroup, SetupModelUi>>,
)

/**
 * Step 4 · AI models: one row per feature with its state. A missing feature ends in a download
 * action, a ready or downloading one opens its preview page; "Download the rest" fetches every
 * missing model. Optional, so Not now and Skip move on.
 *
 * When the APK bundles the models ([SetupAiModelsState.isBundled]) every row reads Ready — the
 * local copy made on first launch counts as installed — and no download action is offered.
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
    val features = listOf(
        SetupFeature(
            "SMART_SEARCH", Icons.Outlined.ImageSearch,
            Res.string.setup_feature_search, Res.string.setup_feature_search_desc,
            listOf(state.searchGroup to state.search),
        ),
        SetupFeature(
            "FACE_GROUPING", Icons.Outlined.Face,
            Res.string.setup_feature_faces, Res.string.setup_feature_faces_desc,
            listOf(ModelGroup.FACE_DETECT to state.faceDetect, ModelGroup.FACE_RECOGNITION to state.faceRecognition),
        ),
        SetupFeature(
            "SUBJECT_CUTOUT", Icons.Outlined.ContentCut,
            Res.string.setup_feature_cutout, Res.string.setup_feature_cutout_desc,
            listOf(ModelGroup.CUTOUT to state.cutout),
        ),
    )
    val canDownload = state.hasInternet && !state.isBundled
    val missing = features.flatMap { it.groups }.filter { it.second.status == ModelStatus.NOT_INSTALLED }

    SetupStepScaffold(
        modifier = modifier,
        stepNumber = stepNumber,
        totalSteps = totalSteps,
        stepName = stringResource(Res.string.setup_step_ai),
        hero = SetupHero(
            icon = Icons.Outlined.AutoAwesome,
            shape = SetupHeroShape.AiModels.toShape(),
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
        title = stringResource(Res.string.setup_ai_title),
        subtitle = stringResource(Res.string.setup_ai_subtitle),
        onBack = onBack,
        onSkip = onSkip,
        bottomBar = {
            SetupBottomActions(
                primaryLabel = stringResource(Res.string.setup_continue),
                onPrimary = onNext,
                secondaryLabel = stringResource(Res.string.setup_not_now),
                onSecondary = onSkip,
            )
        },
    ) {
        SetupGlassPanel {
            features.forEachIndexed { index, feature ->
                FeatureRow(
                    feature = feature,
                    position = setupRowPosition(index, features.size),
                    canDownload = canDownload,
                    isBundled = state.isBundled,
                    onDownload = { feature.groups.forEach { (group, ui) -> if (ui.status == ModelStatus.NOT_INSTALLED) actions.onDownload(group) } },
                    onOpen = { actions.onOpenFeature(feature.name) },
                )
            }
        }
        if (canDownload && missing.size > 1) {
            FilledTonalButton(
                onClick = { missing.forEach { (group, _) -> actions.onDownload(group) } },
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .heightIn(min = 48.dp),
            ) {
                Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.setup_ai_download_rest))
            }
        }
        SetupNote(
            icon = Icons.Outlined.Info,
            text = stringResource(
                when {
                    state.isBundled -> Res.string.setup_ai_ready
                    !state.hasInternet -> Res.string.setup_ai_no_internet
                    else -> Res.string.setup_ai_footnote
                }
            ),
        )
    }
}

@Composable
private fun FeatureRow(
    feature: SetupFeature,
    position: SetupRowPosition,
    canDownload: Boolean,
    isBundled: Boolean,
    onDownload: () -> Unit,
    onOpen: () -> Unit,
) {
    val models = feature.groups.map { it.second }
    val ready = models.all {
        it.status == ModelStatus.READY || (isBundled && it.status == ModelStatus.COPYING)
    }
    val downloading = if (ready) emptyList() else models.filter {
        it.status == ModelStatus.DOWNLOADING || it.status == ModelStatus.COPYING
    }
    val status = when {
        ready -> stringResource(Res.string.setup_feature_ready)
        downloading.isNotEmpty() -> stringResource(
            Res.string.setup_feature_downloading,
            downloading.map { it.progress }.average().toInt(),
        )
        else -> stringResource(Res.string.setup_feature_not_installed)
    }
    val notInstalled = !ready && downloading.isEmpty()
    SetupGlassRow(
        title = stringResource(feature.title),
        summary = stringResource(feature.description),
        icon = feature.icon,
        status = status,
        statusColor = if (notInstalled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
        position = position,
        onClick = onOpen,
        trailing = {
            if (notInstalled && canDownload) {
                IconButton(onClick = onDownload) {
                    Icon(
                        Icons.Outlined.Download,
                        contentDescription = stringResource(Res.string.setup_ai_download_rest),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Icon(
                    Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}
