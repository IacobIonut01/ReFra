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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.R
import com.dot.gallery.core.ml.ModelStatus
import com.dot.gallery.core.presentation.components.NavigationBackButton
import com.dot.gallery.feature_node.presentation.settings.components.settings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIModelsManagerScreen(
    viewModel: SmartFeaturesViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val modelStatus by viewModel.modelStatus.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val downloadInfo by viewModel.downloadInfo.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    var showDeleteDialog by remember { mutableStateOf(false) }

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
        // Resolve all strings outside the non-composable settings{} DSL
        val descriptionText = stringResource(R.string.ai_models_description) + "\n\n" +
                stringResource(R.string.ai_models_privacy_description)
        val sourceHeader = stringResource(R.string.ai_models_source)
        val sourceUrl = "https://github.com/IacobIonut01/ReFra/tree/main/ml-models/src/main/assets"
        val sourceLabel = stringResource(R.string.ai_models_source_url)
        val filesHeader = stringResource(R.string.ai_models_files)

        // Action preference strings
        val actionTitle: String
        val actionSummary: String
        val actionEnabled: Boolean
        val actionClick: () -> Unit
        when (modelStatus) {
            ModelStatus.READY -> {
                val sizeStr = Formatter.formatFileSize(context, viewModel.installedSize)
                actionTitle = stringResource(R.string.ai_models_delete)
                actionSummary = stringResource(R.string.ai_models_ready_summary) + "\n" +
                        stringResource(R.string.ai_models_size, sizeStr)
                actionEnabled = true
                actionClick = { showDeleteDialog = true }
            }
            ModelStatus.DOWNLOADING, ModelStatus.COPYING -> {
                actionTitle = stringResource(R.string.ai_models_downloading)
                val speedStr = Formatter.formatFileSize(context, downloadInfo.speed)
                val downloadedStr = Formatter.formatFileSize(context, downloadInfo.downloadedBytes)
                val totalStr = Formatter.formatFileSize(context, downloadInfo.totalBytes)
                val etaStr = if (downloadInfo.speed > 0 && downloadInfo.totalBytes > 0) {
                    val remainingBytes = downloadInfo.totalBytes - downloadInfo.downloadedBytes
                    val remainingSecs = remainingBytes / downloadInfo.speed
                    formatDuration(remainingSecs)
                } else ""
                actionSummary = buildString {
                    append(downloadInfo.currentFile)
                    if (downloadInfo.totalBytes > 0) append(" · $downloadedStr / $totalStr")
                    if (downloadInfo.speed > 0) append(" · $speedStr/s")
                    if (etaStr.isNotEmpty()) append(" · $etaStr")
                }
                actionEnabled = true
                actionClick = { viewModel.cancelDownload() }
            }
            ModelStatus.ERROR -> {
                actionTitle = stringResource(R.string.ai_models_download)
                actionSummary = errorMessage ?: "Unknown error"
                actionEnabled = true
                actionClick = { viewModel.downloadModels() }
            }
            ModelStatus.NOT_INSTALLED -> {
                actionTitle = stringResource(R.string.ai_models_download)
                actionSummary = stringResource(R.string.ai_models_download_summary)
                actionEnabled = true
                actionClick = { viewModel.downloadModels() }
            }
        }

        // File infos (computed only when READY)
        val fileInfos = remember(modelStatus) {
            if (modelStatus == ModelStatus.READY) viewModel.getFileInfos() else emptyList()
        }

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

            // Feature previews
            item(key = "feature_previews") {
                Row(
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max)
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FeaturePreviewCard(
                        label = stringResource(R.string.ai_models_feature_search),
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    ) {
                        SearchPreview()
                    }
                    FeaturePreviewCard(
                        label = stringResource(R.string.ai_models_feature_categories),
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    ) {
                        CategoriesPreview()
                    }
                }
            }

            // Source (always visible, clickable)
            settings {
                Header(sourceHeader)

                Preference(
                    title = sourceLabel,
                    summary = sourceUrl,
                    onClick = {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            sourceUrl.toUri()
                        )
                        context.startActivity(intent)
                    }
                )
            }

            // Action button with integrated status + progress bar
            item(key = "action") {
                val isDownloading = modelStatus == ModelStatus.DOWNLOADING || modelStatus == ModelStatus.COPYING
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .clickable(enabled = actionEnabled) { actionClick() }
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = actionTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (isDownloading) {
                        LinearProgressIndicator(
                            progress = { downloadProgress / 100f },
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
            }

            // File details when installed (full SHA-256 + verified status)
            if (fileInfos.isNotEmpty()) {
                settings {
                    Header(filesHeader)

                    fileInfos.forEach { info ->
                        val fileSizeStr = Formatter.formatFileSize(context, info.size)
                        val verifiedStr = if (info.verified) "(Verified)" else "(Unverified)"
                        Preference(
                            title = info.name,
                            summary = "$fileSizeStr\nSHA-256: ${info.sha256}\n$verifiedStr"
                        )
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.ai_models_delete)) },
            text = { Text(stringResource(R.string.ai_models_delete_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteModels()
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
