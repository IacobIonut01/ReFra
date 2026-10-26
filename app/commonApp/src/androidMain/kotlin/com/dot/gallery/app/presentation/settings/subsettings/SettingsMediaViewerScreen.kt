/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.settings.subsettings

import android.graphics.Bitmap
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.core.ml.ModelGroup
import com.dot.gallery.core.ml.ModelStatus
import com.dot.gallery.feature.settings.subsettings.SmartFeaturesViewModel
import com.dot.gallery.injection.injectViewModel
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.composeunstyled.LocalTextStyle
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.Settings
import com.dot.gallery.core.navigate
import com.dot.gallery.core.util.HdrCapabilities
import com.dot.gallery.core.util.SdkCompat
import com.dot.gallery.core.Settings.Misc.rememberAllowBlur
import com.dot.gallery.core.Settings.Misc.rememberDateHeaderFormat
import com.dot.gallery.feature.settings.ui.EditAppEntry
import com.dot.gallery.feature.settings.ui.MediaViewerPreviews
import com.dot.gallery.feature.settings.ui.SettingsMediaViewerActions
import com.dot.gallery.feature.settings.ui.SettingsMediaViewerContent
import com.dot.gallery.feature.settings.ui.SettingsMediaViewerState
import com.dot.gallery.feature.help.domain.SettingsSearchRegistry
import com.dot.gallery.app.presentation.mediaview.TapNavigationPreview
import com.dot.gallery.app.presentation.mediaview.discoverVisualSearchTargets
import com.dot.gallery.core.presentation.util.LocalHazeState
import com.dot.gallery.core.presentation.util.Screen
import com.dot.gallery.app.presentation.util.getEditImageCapableApps
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import dev.chrisbanes.haze.blur.materials.HazeMaterials
import kotlinx.coroutines.launch
import com.dot.gallery.ui.core.icons.VisualSearch
import com.dot.gallery.domain.date.getDate
import com.dot.gallery.core.presentation.util.hazeEffectScaled
import com.dot.gallery.R
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.default_image_editor_builtin
import com.dot.gallery.generated.resources.image_sample_2

/**
 * App-side shell for the media-viewer settings: owns the platform-only seams
 * (HDR display probe, edit-app discovery via PackageManager, live previews backed
 * by drawable samples + haze) and delegates everything else to
 * [SettingsMediaViewerContent] in `:feature:settings`.
 */
@Composable
fun SettingsMediaViewerScreen(
    smartFeatures: SmartFeaturesViewModel = injectViewModel { smartFeaturesViewModel }
) {
    val context = LocalContext.current
    val cutoutStatus by smartFeatures.modelStatus(ModelGroup.CUTOUT).collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val eventHandler = LocalEventHandler.current

    val rawEditApps = remember(context, context::getEditImageCapableApps)
    val state = remember(context) {
        SettingsMediaViewerState(
            hdrDisplayCapable = HdrCapabilities.isHdrDisplay(context),
            supportsFavorites = SdkCompat.supportsFavorites,
            surfaceRebindDefault = SdkCompat.isSamsung,
            editApps = rawEditApps.map {
                EditAppEntry(
                    packageName = it.activityInfo.packageName,
                    label = it.loadLabel(context.packageManager).toString(),
                )
            },
            visualSearchTargets = context.discoverVisualSearchTargets(),
        )
    }
    val previews = remember(context, rawEditApps) {
        MediaViewerPreviews(
            dateHeader = { DateHeaderPreview(it) },
            tapNavigation = { TapNavigationPreview(enabled = it) },
            favoriteButton = { FavoriteButtonPreview(it) },
            editor = { current, _ -> EditorPreview(current, rawEditApps) },
            smoothing = { SmoothingPreview(disableSmoothing = it) },
            visualSearch = { enabled, icon, position ->
                VisualSearchPreview(enabled = enabled, icon = icon, position = position)
            },
            providerIcon = { packageName -> VisualSearchProviderIcon(packageName) },
        )
    }

    SettingsMediaViewerContent(
        state = state.copy(cutoutModelAvailable = cutoutStatus == ModelStatus.READY),
        previews = previews,
        actions = SettingsMediaViewerActions(
            onOpenSlideshow = { eventHandler.navigate(Screen.SlideshowSettingsScreen()) },
            onTapSidesChanged = {
                scope.launch { Settings.Misc.markTapSidesToNavigatePromptShown(context) }
            },
            searchSink = SettingsSearchRegistry::register,
            searchRoute = Screen.SettingsMediaViewerScreen(),
        ),
    )
}

@Composable
private fun VisualSearchProviderIcon(packageName: String) {
    val context = LocalContext.current
    val appIcon = remember(packageName) {
        runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()
    }
    if (appIcon != null) {
        Image(
            painter = rememberDrawablePainter(appIcon),
            contentDescription = null,
            modifier = Modifier.size(28.dp),
        )
    } else {
        Icon(
            imageVector = com.dot.gallery.ui.core.Icons.VisualSearch,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DateHeaderPreview(isChecked: Boolean) {
    val dateHeaderFormat by rememberDateHeaderFormat()
    val currentMillis = remember { System.currentTimeMillis() / 1000 }
    val textStyle = LocalTextStyle.current
    val allowBlur by rememberAllowBlur()
    val followTheme = remember(allowBlur) { !allowBlur }
    val contentColor by animateColorAsState(
        targetValue = if (followTheme) MaterialTheme.colorScheme.onSurface else Color.White,
        label = "contentColor"
    )
    val surfaceContainer = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f)
    val backgroundModifier = remember(surfaceContainer) {
        Modifier.background(color = surfaceContainer, shape = CircleShape)
    }

    val currentDate = remember(currentMillis, dateHeaderFormat, textStyle) {
        buildAnnotatedString {
            val date = currentMillis.getDate(dateHeaderFormat)
            if (date.isNotEmpty()) {
                val top = date.substringBefore("\n")
                val bottom = date.substringAfter("\n")
                withStyle(
                    style = textStyle.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    ).toSpanStyle()
                ) {
                    appendLine(top)
                }
                withStyle(
                    style = textStyle.copy(
                        fontWeight = FontWeight.Normal,
                        fontSize = 14.sp
                    ).toSpanStyle()
                ) {
                    append(bottom)
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(128.dp)
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
    ) {
        Image(
            painter = painterResource(Res.drawable.image_sample_2),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .blur(32.dp),
            contentScale = ContentScale.Crop
        )
        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .background(color = Color.Black.copy(alpha = 0.1f))
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .clip(CircleShape)
                    .then(backgroundModifier)
                    .hazeEffectScaled(
                        state = LocalHazeState.current,
                        style = HazeMaterials.ultraThin(
                            containerColor = surfaceContainer
                        )
                    ),
                onClick = { }
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.height(48.dp)
                )
            }
            if (isChecked) {
                Text(
                    text = currentDate,
                    style = MaterialTheme.typography.titleSmall,
                    color = contentColor,
                    textAlign = TextAlign.Center
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            IconButton(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .clip(CircleShape)
                    .then(backgroundModifier)
                    .hazeEffectScaled(
                        state = LocalHazeState.current,
                        style = HazeMaterials.ultraThin(
                            containerColor = surfaceContainer
                        )
                    ),
                onClick = { }
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.height(48.dp)
                )
            }
        }
    }
}

@Composable
private fun FavoriteButtonPreview(isChecked: Boolean) {
    val iconTint = MaterialTheme.colorScheme.onSurface
    val surfaceContainer = MaterialTheme.colorScheme.surfaceContainer

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(128.dp)
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
    ) {
        Image(
            painter = painterResource(Res.drawable.image_sample_2),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .blur(8.dp),
            contentScale = ContentScale.Crop
        )
        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .background(color = Color.Black.copy(alpha = 0.1f))
        )
        // Bottom floating action pill matching MediaViewQuickBottomBar
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(100))
                    .background(surfaceContainer.copy(alpha = 0.85f))
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Share
                Box(Modifier.size(32.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Share, null, Modifier.size(18.dp), tint = iconTint)
                }
                // Copy to Clipboard
                Box(Modifier.size(32.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.ContentCopy, null, Modifier.size(18.dp), tint = iconTint)
                }
                // Favorite (conditionally shown based on setting)
                if (isChecked) {
                    Box(Modifier.size(32.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Favorite, null, Modifier.size(18.dp), tint = iconTint)
                    }
                }
                // Edit
                Box(Modifier.size(32.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Edit, null, Modifier.size(18.dp), tint = iconTint)
                }
                // Trash
                Box(Modifier.size(32.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.DeleteOutline, null, Modifier.size(18.dp), tint = iconTint)
                }
            }
        }
    }
}

@Composable
private fun EditorPreview(
    currentEditor: String,
    editApps: List<android.content.pm.ResolveInfo>
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Auto-scroll to selected item when selection changes
    val allEditors = remember(editApps) {
        val list = mutableListOf(Settings.Misc.EDITOR_BUILTIN)
        editApps.forEach { list.add(it.activityInfo.packageName) }
        list
    }
    val selectedIndex = remember(currentEditor, allEditors) {
        allEditors.indexOf(currentEditor).coerceAtLeast(0)
    }
    LaunchedEffect(selectedIndex) {
        // Estimate scroll position: each card ~120dp + 12dp spacing
        val targetPx = (selectedIndex * 132 * context.resources.displayMetrics.density).toInt()
        scrollState.animateScrollTo(
            (targetPx - 100).coerceAtLeast(0)
        )
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp)
                .horizontalScroll(scrollState)
                .padding(horizontal = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            EditorOptionCard(
                label = stringResource(Res.string.default_image_editor_builtin),
                selected = currentEditor == Settings.Misc.EDITOR_BUILTIN,
                icon = {
                    Image(
                        painter = rememberDrawablePainter(
                            drawable = AppCompatResources.getDrawable(context, R.mipmap.ic_launcher_round)
                        ),
                        contentDescription = stringResource(Res.string.default_image_editor_builtin),
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                    )
                }
            )
            editApps.forEach { app ->
                val packageName = app.activityInfo.packageName
                val appLabel = remember(app) { app.loadLabel(context.packageManager).toString() }
                val appIcon = remember(app) {
                    try { app.loadIcon(context.packageManager).toBitmap().asImageBitmap() }
                    catch (_: Exception) { null }
                }
                if (appIcon != null) {
                    EditorOptionCard(
                        label = appLabel,
                        selected = currentEditor == packageName,
                        icon = {
                            Image(
                                bitmap = appIcon,
                                contentDescription = appLabel,
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                            )
                        }
                    )
                }
            }
        }
        // Soft fade-out gradient on left edge
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(32.dp, 160.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                            Color.Transparent
                        )
                    )
                )
        )
        // Soft fade-out gradient on right edge
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(32.dp, 160.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    )
                )
        )
    }
}

@Composable
private fun EditorOptionCard(
    label: String,
    selected: Boolean,
    icon: @Composable () -> Unit
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.outlineVariant
    val containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    else Color.Transparent

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .border(width = 2.dp, color = borderColor, shape = RoundedCornerShape(16.dp))
            .background(containerColor)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        icon()
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.size(24.dp)
        )
    }
}

/**
 * Preview for the "Disable smoothing" setting. Draws a tiny pixel-art sample scaled up so the
 * difference between nearest-neighbor (crisp, [disableSmoothing] = true) and bilinear (smoothed,
 * [disableSmoothing] = false) filtering is clearly visible — the same effect applied in the viewer.
 */
@Composable
private fun SmoothingPreview(disableSmoothing: Boolean) {
    val sample = remember { createSmoothingSampleBitmap() }
    val filterQuality = if (disableSmoothing) FilterQuality.None else FilterQuality.Low
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(120.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawImage(
                image = sample,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(sample.width, sample.height),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                filterQuality = filterQuality
            )
        }
    }
}

/**
 * Builds a small (pixel-art) scene with a sun, sky gradient and two mountains. The curves and
 * diagonals make the smoothing/no-smoothing difference obvious when the bitmap is scaled up.
 */
private fun createSmoothingSampleBitmap(): androidx.compose.ui.graphics.ImageBitmap {
    val n = 20
    val bitmap = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)

    val skyTop = 0xFF3A6EA5.toInt()
    val skyBottom = 0xFFBFE3F2.toInt()
    val sun = 0xFFFFD34E.toInt()
    val mountainBack = 0xFF6D8C5A.toInt()
    val mountainFront = 0xFF3F6034.toInt()

    val sunCx = n * 0.72f
    val sunCy = n * 0.30f
    val sunR = n * 0.16f

    for (y in 0 until n) {
        for (x in 0 until n) {
            var color = lerpArgb(skyTop, skyBottom, y / (n - 1f))

            val dx = x - sunCx
            val dy = y - sunCy
            if (dx * dx + dy * dy <= sunR * sunR) {
                color = sun
            }

            val backLine = n * 0.62f - (x - n * 0.2f) * 0.35f
            if (y >= backLine) color = mountainBack

            val frontLine = n * 0.95f - kotlin.math.abs(x - n * 0.55f) * 0.9f
            if (y >= frontLine) color = mountainFront

            bitmap.setPixel(x, y, color)
        }
    }
    return bitmap.asImageBitmap()
}

private fun lerpArgb(start: Int, end: Int, fraction: Float): Int {
    val f = fraction.coerceIn(0f, 1f)
    val a = ((start ushr 24 and 0xFF) + (((end ushr 24 and 0xFF) - (start ushr 24 and 0xFF)) * f)).toInt()
    val r = ((start ushr 16 and 0xFF) + (((end ushr 16 and 0xFF) - (start ushr 16 and 0xFF)) * f)).toInt()
    val g = ((start ushr 8 and 0xFF) + (((end ushr 8 and 0xFF) - (start ushr 8 and 0xFF)) * f)).toInt()
    val b = ((start and 0xFF) + (((end and 0xFF) - (start and 0xFF)) * f)).toInt()
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}

/**
 * Mock of the viewer chrome showing where the visual-search button lands: the top-left stadium
 * next to Back when [position] is top, or inside the bottom quick-actions pill when bottom.
 */
@Composable
private fun VisualSearchPreview(enabled: Boolean, icon: ImageVector, position: String) {
    val iconTint = MaterialTheme.colorScheme.onSurface
    val surfaceContainer = MaterialTheme.colorScheme.surfaceContainer

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(128.dp)
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
    ) {
        Image(
            painter = painterResource(Res.drawable.image_sample_2),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .blur(8.dp),
            contentScale = ContentScale.Crop
        )
        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .background(color = Color.Black.copy(alpha = 0.1f))
        )
        if (enabled && position == Settings.Misc.VISUAL_SEARCH_POSITION_TOP) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 12.dp, start = 12.dp)
                    .clip(RoundedCornerShape(100))
                    .background(surfaceContainer.copy(alpha = 0.85f))
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        null,
                        Modifier.size(18.dp),
                        tint = iconTint
                    )
                }
                Box(
                    Modifier
                        .width(1.dp)
                        .height(18.dp)
                        .background(iconTint.copy(alpha = 0.2f))
                )
                Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                    Icon(icon, null, Modifier.size(18.dp), tint = iconTint)
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(100))
                    .background(surfaceContainer.copy(alpha = 0.85f))
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(Modifier.size(32.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Share, null, Modifier.size(18.dp), tint = iconTint)
                }
                if (enabled && position == Settings.Misc.VISUAL_SEARCH_POSITION_BOTTOM) {
                    Box(Modifier.size(32.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                        Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Box(Modifier.size(32.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.ContentCopy, null, Modifier.size(18.dp), tint = iconTint)
                }
                Box(Modifier.size(32.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Edit, null, Modifier.size(18.dp), tint = iconTint)
                }
                Box(Modifier.size(32.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.DeleteOutline, null, Modifier.size(18.dp), tint = iconTint)
                }
            }
        }
    }
}
