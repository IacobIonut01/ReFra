package com.dot.gallery.feature_node.presentation.settings.subsettings

import android.content.Intent
import android.text.format.Formatter
import androidx.core.net.toUri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.BuildConfig
import com.dot.gallery.R
import com.dot.gallery.core.ml.ModelFileInfo
import com.dot.gallery.core.ml.ModelGroup
import com.dot.gallery.core.ml.ModelStatus
import com.dot.gallery.core.presentation.components.NavigationBackButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIModelsManagerScreen(
    viewModel: SmartFeaturesViewModel = hiltViewModel()
) {
    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.ai_models_manager)) },
                navigationIcon = { NavigationBackButton() },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        val descriptionText = stringResource(R.string.ai_models_description) + "\n\n" +
                stringResource(R.string.ai_models_privacy_description)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = padding.calculateStartPadding(LocalLayoutDirection.current),
                end = padding.calculateEndPadding(LocalLayoutDirection.current),
                top = 16.dp + padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 16.dp
            )
        ) {
            // Description (includes privacy info)
            item(key = "description") {
                Text(
                    text = descriptionText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 16.dp)
                )
            }

            // Feature previews — a 2x2 grid (two fixed-height rows) so each model group is
            // represented. Fixed aspect-ratio cells avoid the IntrinsicSize double-measure pass
            // that made the list janky while the large app bar collapsed during scroll.
            item(key = "feature_previews") {
                Column(
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FeaturePreviewCard(
                            label = stringResource(R.string.ai_models_feature_search),
                            modifier = Modifier.weight(1f).aspectRatio(1f)
                        ) {
                            SearchPreview()
                        }
                        FeaturePreviewCard(
                            label = stringResource(R.string.ai_models_feature_categories),
                            modifier = Modifier.weight(1f).aspectRatio(1f)
                        ) {
                            CategoriesPreview()
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FeaturePreviewCard(
                            label = stringResource(R.string.ai_models_feature_cutout),
                            modifier = Modifier.weight(1f).aspectRatio(1f)
                        ) {
                            CutoutPreview()
                        }
                        FeaturePreviewCard(
                            label = stringResource(R.string.ai_models_feature_faces),
                            modifier = Modifier.weight(1f).aspectRatio(1f)
                        ) {
                            FacesPreview()
                        }
                    }
                }
            }

            // Two independent feature groups, each with its own status/download/delete + source.
            item(key = "group_search") {
                ModelGroupSection(
                    viewModel = viewModel,
                    group = ModelGroup.SEARCH,
                    title = stringResource(R.string.ai_models_group_search_title),
                    description = stringResource(R.string.ai_models_group_search_desc),
                    downloadSummary = stringResource(R.string.ai_models_group_search_download_summary),
                    readySummary = stringResource(R.string.ai_models_ready_summary),
                    sourceLabel = stringResource(R.string.ai_models_source_url),
                    sourceUrl = "https://github.com/IacobIonut01/ReFra/tree/main/ml-models/src/main/assets"
                )
            }
            item(key = "group_cutout") {
                ModelGroupSection(
                    viewModel = viewModel,
                    group = ModelGroup.CUTOUT,
                    title = stringResource(R.string.ai_models_group_cutout_title),
                    description = stringResource(R.string.ai_models_group_cutout_desc),
                    downloadSummary = stringResource(R.string.ai_models_group_cutout_download_summary),
                    readySummary = stringResource(R.string.ai_models_group_cutout_ready_summary),
                    sourceLabel = stringResource(R.string.ai_models_source_cutout_url),
                    sourceUrl = "https://huggingface.co/Acly/MobileSAM"
                )
            }
            item(key = "group_face_detect") {
                ModelGroupSection(
                    viewModel = viewModel,
                    group = ModelGroup.FACE_DETECT,
                    title = stringResource(R.string.ai_models_group_face_detect_title),
                    description = stringResource(R.string.ai_models_group_face_detect_desc),
                    downloadSummary = stringResource(R.string.ai_models_group_face_detect_download_summary),
                    readySummary = stringResource(R.string.ai_models_group_face_detect_ready_summary),
                    sourceLabel = stringResource(R.string.ai_models_source_face_detect_url),
                    sourceUrl = "https://github.com/onnx/models/tree/main/validated/vision/body_analysis/ultraface"
                )
            }
            item(key = "group_face_recognition") {
                ModelGroupSection(
                    viewModel = viewModel,
                    group = ModelGroup.FACE_RECOGNITION,
                    title = stringResource(R.string.ai_models_group_face_recognition_title),
                    description = stringResource(R.string.ai_models_group_face_recognition_desc),
                    downloadSummary = stringResource(R.string.ai_models_group_face_recognition_download_summary),
                    readySummary = stringResource(R.string.ai_models_group_face_recognition_ready_summary),
                    sourceLabel = stringResource(R.string.ai_models_source_face_recognition_url),
                    sourceUrl = "https://huggingface.co/garavv/arcface-onnx"
                )
            }
        }
    }
}

/**
 * A self-contained management card for one [ModelGroup]: title + description, a combined
 * status/action button (download · cancel · delete) with progress, the model source link, and the
 * per-file SHA-256 details once installed. Each section observes only its own group so the two
 * features download and uninstall independently.
 */
@Composable
private fun ModelGroupSection(
    viewModel: SmartFeaturesViewModel,
    group: ModelGroup,
    title: String,
    description: String,
    downloadSummary: String,
    readySummary: String,
    sourceLabel: String,
    sourceUrl: String
) {
    val context = LocalContext.current
    val status by viewModel.modelStatus(group).collectAsStateWithLifecycle()
    val progress by viewModel.downloadProgress(group).collectAsStateWithLifecycle()
    val info by viewModel.downloadInfo(group).collectAsStateWithLifecycle()
    val error by viewModel.errorMessage(group).collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf(false) }
    val isBundled = BuildConfig.ML_MODELS_BUNDLED
    // Offline flavor strips android.permission.INTERNET at build time; bundled builds can still
    // (re)install from the shipped assets, so the action resolver keys on canInstallModels.
    val action = resolveModelManagementAction(status, viewModel.canInstallModels)

    val actionTitle: String
    val actionSummary: String
    val actionClick: (() -> Unit)?
    when (action) {
        ModelManagementAction.DELETE -> {
            val sizeStr = Formatter.formatFileSize(context, viewModel.installedSize(group))
            actionTitle = stringResource(R.string.ai_models_delete)
            actionSummary = readySummary + "\n" + stringResource(R.string.ai_models_size, sizeStr)
            actionClick = { showDeleteDialog = true }
        }
        ModelManagementAction.CANCEL_DOWNLOAD -> {
            actionTitle = stringResource(R.string.ai_models_cancel_download)
            val speedStr = Formatter.formatFileSize(context, info.speed)
            val downloadedStr = Formatter.formatFileSize(context, info.downloadedBytes)
            val totalStr = Formatter.formatFileSize(context, info.totalBytes)
            val etaStr = if (info.speed > 0 && info.totalBytes > 0) {
                formatDuration((info.totalBytes - info.downloadedBytes) / info.speed)
            } else ""
            actionSummary = buildString {
                append(info.currentFile)
                if (info.totalBytes > 0) append(" · $downloadedStr / $totalStr")
                if (info.speed > 0) append(" · $speedStr/s")
                if (etaStr.isNotEmpty()) append(" · $etaStr")
            }
            actionClick = { viewModel.cancelDownload(group) }
        }
        ModelManagementAction.DOWNLOAD -> {
            actionTitle = stringResource(
                if (isBundled) R.string.ai_models_install else R.string.ai_models_download
            )
            actionSummary = error ?: if (isBundled) {
                stringResource(R.string.ai_models_install_summary)
            } else {
                downloadSummary
            }
            actionClick = { viewModel.downloadModels(group) }
        }
        ModelManagementAction.COPYING -> {
            actionTitle = stringResource(R.string.ai_models_preparing)
            actionSummary = stringResource(R.string.ai_models_preparing_summary)
            actionClick = null
        }
        ModelManagementAction.INSTALLED_OFFLINE -> {
            val sizeStr = Formatter.formatFileSize(context, viewModel.installedSize(group))
            actionTitle = stringResource(R.string.ai_models_installed)
            actionSummary = readySummary + "\n" + stringResource(R.string.ai_models_size, sizeStr) +
                    "\n" + stringResource(R.string.ai_models_offline_installed_summary)
            actionClick = null
        }
        ModelManagementAction.UNAVAILABLE_OFFLINE -> {
            actionTitle = stringResource(R.string.ai_models_offline_unavailable)
            actionSummary = listOfNotNull(
                error,
                stringResource(R.string.ai_models_offline_unavailable_summary)
            ).joinToString("\n")
            actionClick = null
        }
    }

    // SHA-256 hashing large model files (e.g. ~130 MB ArcFace) is far too slow for the main
    // thread, so it runs off-thread via the suspend getFileInfos and updates the UI when ready.
    val fileInfos by produceState(initialValue = emptyList<ModelFileInfo>(), status) {
        value = if (status == ModelStatus.READY) viewModel.getFileInfos(group) else emptyList()
    }
    val isDownloading = status == ModelStatus.DOWNLOADING || status == ModelStatus.COPYING
    val actionAlpha = if (action.enabled || action == ModelManagementAction.COPYING) 1f else 0.5f

    Column(
        modifier = Modifier
            .widthIn(max = 600.dp)
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Combined status + action button
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(actionAlpha)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(enabled = action.enabled) { actionClick?.invoke() }
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = actionTitle,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (isDownloading) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            }
            Text(
                text = actionSummary,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Source (clickable)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.ai_models_source),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = sourceLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        context.startActivity(Intent(Intent.ACTION_VIEW, sourceUrl.toUri()))
                    }
            )
        }

        // File details when installed (full SHA-256 + verified status)
        if (fileInfos.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.ai_models_files),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                fileInfos.forEach { fileInfo ->
                    val fileSizeStr = Formatter.formatFileSize(context, fileInfo.size)
                    val verifiedStr = if (fileInfo.verified) "(Verified)" else "(Unverified)"
                    Text(
                        text = "${fileInfo.name}\n$fileSizeStr · SHA-256: ${fileInfo.sha256}\n$verifiedStr",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.ai_models_delete)) },
            text = {
                Text(
                    stringResource(
                        if (isBundled) R.string.ai_models_delete_confirm_bundled
                        else R.string.ai_models_delete_confirm
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteModels(group)
                    }
                ) {
                    Text(
                        stringResource(R.string.action_confirm),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

private fun formatDuration(seconds: Long): String {
    return when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
        else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
    }
}

@Composable
private fun FeaturePreviewCard(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
        )
    }
}

@Composable
private fun SearchPreview() {
    val cellColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val matchColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
    val headerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Search bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(48.dp, 5.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(headerColor)
            )
        }
        Spacer(Modifier.height(2.dp))
        // Photo grid — best matches first (sequential at top)
        repeat(3) { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                repeat(4) { col ->
                    val index = row * 4 + col
                    val isMatch = index < 4
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isMatch) matchColor else cellColor)
                    ) {
                        if (isMatch) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(2.dp)
                                    .size(6.dp)
                                    .clip(RoundedCornerShape(1.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoriesPreview() {
    val cardColors = listOf(
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.secondaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer,
        MaterialTheme.colorScheme.surfaceVariant,
    )
    val categories = listOf("Nature", "Food", "People", "Travel", "Pets")
    val fadeColor = MaterialTheme.colorScheme.surfaceContainer
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, fadeColor),
                        startX = size.width * 0.75f,
                        endX = size.width
                    ),
                    blendMode = BlendMode.SrcAtop
                )
            }
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            categories.forEachIndexed { idx, label ->
                MiniCategoryCard(
                    name = label,
                    backgroundColor = cardColors[idx % cardColors.size]
                )
            }
        }
    }
}

@Composable
private fun MiniCategoryCard(
    name: String,
    backgroundColor: Color,
) {
    Box(
        modifier = Modifier
            .width(56.dp)
            .aspectRatio(164f / 256f)
            .clip(RoundedCornerShape(10.dp))
            .background(backgroundColor)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))
                    )
                )
                .padding(horizontal = 4.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 7.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun FacesPreview() {
    val tile = MaterialTheme.colorScheme.surfaceContainerHighest
    val faceColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.75f)
    val mosaicColor = MaterialTheme.colorScheme.primary
    val boxColor = MaterialTheme.colorScheme.primary
    val mosaicAlphas = remember { listOf(0.3f, 0.55f, 0.7f, 0.45f, 0.6f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Detected face with a detection box (face detection model)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(6.dp))
                    .background(tile),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize(0.5f)
                        .clip(CircleShape)
                        .background(faceColor)
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp)
                        .border(1.5.dp, boxColor, RoundedCornerShape(4.dp))
                )
            }
            // Blurred/mosaic face (privacy blur)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(6.dp))
                    .background(tile),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize(0.5f)
                        .clip(CircleShape)
                        .background(faceColor)
                )
                Column(
                    modifier = Modifier
                        .matchParentSize()
                        .padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    repeat(3) { r ->
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            repeat(3) { c ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(1.dp))
                                        .background(
                                            mosaicColor.copy(
                                                alpha = mosaicAlphas[(r * 3 + c) % mosaicAlphas.size]
                                            )
                                        )
                                )
                            }
                        }
                    }
                }
            }
        }
        // Grouping: overlapping mini avatars implying "same person" clustering (recognition)
        Box(
            modifier = Modifier
                .height(14.dp)
                .width(32.dp)
        ) {
            repeat(3) { i ->
                Box(
                    modifier = Modifier
                        .offset(x = (i * 9).dp)
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(faceColor)
                        .border(2.dp, tile, CircleShape)
                )
            }
        }
    }
}

@Composable
private fun CutoutPreview() {
    val checkerLight = Color.LightGray.copy(alpha = 0.2f)
    val checkerDark = Color.LightGray.copy(alpha = 0.4f)
    val shapeColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f)
    val dotColor = Color(0xFF4CAF50)

    val infiniteTransition = rememberInfiniteTransition(label = "glowTransitionPreview")
    val glowRadius by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowRadiusPreview"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .clip(RoundedCornerShape(12.dp))
            .drawBehind {
                // Draw checkerboard background
                val sizeVal = 8.dp.toPx()
                val cols = (size.width / sizeVal).toInt() + 1
                val rows = (size.height / sizeVal).toInt() + 1
                for (r in 0 until rows) {
                    for (c in 0 until cols) {
                        val color = if ((r + c) % 2 == 0) checkerLight else checkerDark
                        drawRect(
                            color = color,
                            topLeft = Offset(c * sizeVal, r * sizeVal),
                            size = androidx.compose.ui.geometry.Size(sizeVal, sizeVal)
                        )
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Draw subject (a circle representing a person/object) with white glow border
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(shapeColor, shape = CircleShape)
                .border(glowRadius.dp, Color.White, shape = CircleShape)
                .shadow(2.dp, shape = CircleShape)
        )
        // Draw the prompt point marker (green dot)
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(dotColor, shape = CircleShape)
                .border(1.dp, Color.White, shape = CircleShape)
        )
    }
}
