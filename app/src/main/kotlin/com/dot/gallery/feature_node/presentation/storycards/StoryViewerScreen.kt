/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.storycards

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.Settings.Misc.rememberAllowBlur
import com.dot.gallery.core.Settings.Misc.rememberStoryViewerAutoAdvance
import com.dot.gallery.core.Settings.Misc.rememberStoryViewerDuration
import com.dot.gallery.feature_node.domain.model.StoryCard
import com.dot.gallery.feature_node.domain.util.getUri
import com.dot.gallery.feature_node.presentation.mediaview.components.actionbuttons.FavoriteButton
import com.dot.gallery.feature_node.presentation.mediaview.components.actionbuttons.ShareButton
import com.dot.gallery.feature_node.presentation.mediaview.components.media.BlurredMediaBackground
import com.dot.gallery.feature_node.presentation.mediaview.rememberedDerivedState
import com.dot.gallery.feature_node.presentation.util.LocalHazeState
import com.github.panpf.sketch.PainterState
import com.github.panpf.sketch.rememberAsyncImagePainter
import com.github.panpf.sketch.rememberAsyncImageState
import com.github.panpf.sketch.request.ComposableImageRequest
import com.github.panpf.sketch.resize.Precision
import com.dot.gallery.feature_node.presentation.util.rememberWindowInsetsController
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import kotlinx.coroutines.launch

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun StoryViewerScreen(
    cards: List<StoryCard>?,
    initialCardId: Long = -1L,
    onDismiss: () -> Unit
) {
    // Force light status bar icons (white) on dark background, restore on exit
    val windowInsetsController = rememberWindowInsetsController()
    DisposableEffect(Unit) {
        val previousLight = windowInsetsController.isAppearanceLightStatusBars
        windowInsetsController.isAppearanceLightStatusBars = false
        onDispose {
            windowInsetsController.isAppearanceLightStatusBars = previousLight
        }
    }

    // null = still loading, show spinner
    if (cards == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color.White)
        }
        return
    }

    // Loaded but empty — dismiss via side effect (not during composition)
    if (cards.isEmpty()) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }

    // Resolve initial page; -1 means the target card hasn't loaded yet
    val targetIndex by rememberedDerivedState(cards, initialCardId) {
        if (initialCardId == -1L) 0
        else cards.indexOfFirst { it.id == initialCardId }
    }

    val pagerState = rememberPagerState(
        initialPage = targetIndex.coerceAtLeast(0),
        pageCount = { cards.size }
    )

    // When the target card appears after initial load, scroll to it
    LaunchedEffect(targetIndex) {
        if (targetIndex > 0 && pagerState.currentPage != targetIndex) {
            pagerState.scrollToPage(targetIndex)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            key = { index -> cards[index].id },
            beyondViewportPageCount = 0,
        ) { page ->
            val card by rememberedDerivedState(cards, page) {
                cards[page]
            }
            val isCurrentPage by rememberedDerivedState(pagerState.currentPage) {
                pagerState.currentPage == page
            }
            StoryCardViewer(
                card = card,
                isCurrentPage = isCurrentPage,
                onDismiss = onDismiss
            )
        }
    }
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
private fun StoryCardViewer(
    card: StoryCard,
    isCurrentPage: Boolean,
    onDismiss: () -> Unit
) {
    val mediaList = card.mediaList
    if (mediaList.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text("No media", color = Color.White)
        }
        return
    }

    var currentMediaIndex by rememberSaveable { mutableIntStateOf(0) }
    val autoAdvance by rememberStoryViewerAutoAdvance()
    val durationStr by rememberStoryViewerDuration()
    val durationMs = rememberSaveable(durationStr) {
        (durationStr.toIntOrNull() ?: 5) * 1000L
    }
    var isPaused by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val allowBlur by rememberAllowBlur()
    val hazeState = LocalHazeState.current

    val progress = remember { Animatable(0f) }

    // Auto-advance timer
    LaunchedEffect(currentMediaIndex, isCurrentPage, isPaused, autoAdvance) {
        if (!isCurrentPage || isPaused || !autoAdvance) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = durationMs.toInt(),
                easing = LinearEasing
            )
        )
        // Advance to next media or dismiss
        if (currentMediaIndex < mediaList.lastIndex) {
            currentMediaIndex++
        } else {
            onDismiss()
        }
    }

    // Reset progress when paused/resumed
    LaunchedEffect(isPaused) {
        if (isPaused) {
            progress.stop()
        }
    }

    val currentMedia by rememberedDerivedState(mediaList, currentMediaIndex) {
        mediaList[currentMediaIndex.coerceIn(0, mediaList.lastIndex)]
    }
    val blurContainerColor = remember {
        Color.Black.copy(alpha = 0.5f)
    }
    val fallbackContainerColor = remember {
        Color.Black.copy(alpha = 0.4f)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        val width = size.width
                        if (offset.x < width / 3f) {
                            if (currentMediaIndex > 0) {
                                currentMediaIndex--
                                scope.launch { progress.snapTo(0f) }
                            }
                        } else if (offset.x > width * 2f / 3f) {
                            if (currentMediaIndex < mediaList.lastIndex) {
                                currentMediaIndex++
                                scope.launch { progress.snapTo(0f) }
                            } else {
                                onDismiss()
                            }
                        }
                    },
                    onLongPress = {
                        isPaused = true
                    },
                    onPress = {
                        awaitRelease()
                        if (isPaused) {
                            isPaused = false
                        }
                    }
                )
            }
    ) {
        // Blurred background behind media (like ZoomablePagerImage)
        BlurredMediaBackground(
            media = currentMedia,
            uiEnabled = true
        )

        // Media display (haze source for blur)
        val mediaUri = remember(currentMedia) {
            currentMedia.getUri().toString()
        }
        val previewPainter = rememberAsyncImagePainter(
            request = ComposableImageRequest(mediaUri) {
                resize(width = 600, height = 600, precision = Precision.LESS_PIXELS)
                crossfade(false)
            },
            contentScale = ContentScale.Fit
        )
        val fullImageState = rememberAsyncImageState()
        val fullPainter = rememberAsyncImagePainter(
            request = ComposableImageRequest(mediaUri) {},
            state = fullImageState,
            contentScale = ContentScale.Fit
        )
        val isFullImageLoaded by rememberedDerivedState(currentMedia) {
            fullImageState.painterState is PainterState.Success
        }
        val activePainter = remember(isFullImageLoaded) {
            if (isFullImageLoaded) fullPainter else previewPainter
        }
        Image(
            painter = activePainter,
            contentDescription = currentMedia.label,
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState),
            contentScale = ContentScale.Fit,
        )

        // Top gradient overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.6f),
                            Color.Transparent
                        )
                    )
                )
        )

        // ── Top: Progress segments + back button + title + pause ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // Segmented progress bar (no end tip — just rounded segments)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                mediaList.forEachIndexed { index, _ ->
                    val segmentProgress by rememberedDerivedState(currentMediaIndex, index, progress.value) {
                        when {
                            index < currentMediaIndex -> 1f
                            index == currentMediaIndex -> progress.value
                            else -> 0f
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.3f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(segmentProgress)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.White)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Back button + centered title + pause button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back button — circular with blur, white tint
                val backBgModifier = if (allowBlur) {
                    Modifier
                        .clip(CircleShape)
                        .hazeEffect(
                            state = hazeState,
                            style = HazeMaterials.ultraThin(containerColor = blurContainerColor)
                        )
                } else {
                    Modifier.background(fallbackContainerColor, CircleShape)
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .then(backBgModifier)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                // Centered title + subtitle
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = card.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                    if (card.subtitle != null) {
                        Text(
                            text = card.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }
                }

                // Pause button — rounded with blur
                if (autoAdvance) {
                    val pauseBgModifier = if (allowBlur) {
                        Modifier
                            .clip(CircleShape)
                            .hazeEffect(
                                state = hazeState,
                                style = HazeMaterials.ultraThin(containerColor = blurContainerColor)
                            )
                    } else {
                        Modifier.background(fallbackContainerColor, CircleShape)
                    }
                    IconButton(
                        onClick = { isPaused = !isPaused },
                        modifier = Modifier
                            .then(pauseBgModifier)
                    ) {
                        Icon(
                            imageVector = if (isPaused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                            contentDescription = if (isPaused) "Resume" else "Pause",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                } else {
                    // Spacer matching back button width for centering
                    Spacer(Modifier.size(48.dp))
                }
            }
        }

        // ── Bottom: Action buttons + counter chip ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.5f)
                        )
                    )
                )
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Action buttons row (share, favorite, etc.)
            val actionBgModifier = if (allowBlur) {
                Modifier
                    .clip(RoundedCornerShape(100))
                    .hazeEffect(
                        state = hazeState,
                        style = HazeMaterials.ultraThin(containerColor = blurContainerColor)
                    )
            } else {
                Modifier.background(fallbackContainerColor, RoundedCornerShape(100))
            }
            Row(
                modifier = Modifier
                    .then(actionBgModifier)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ShareButton(
                    media = currentMedia,
                    enabled = true
                )
                FavoriteButton(
                    media = currentMedia,
                    enabled = true
                )
            }

            // Item counter chip with blur
            val chipBgModifier = if (allowBlur) {
                Modifier
                    .clip(RoundedCornerShape(100))
                    .hazeEffect(
                        state = hazeState,
                        style = HazeMaterials.ultraThin(containerColor = blurContainerColor)
                    )
            } else {
                Modifier.background(fallbackContainerColor, RoundedCornerShape(100))
            }
            Text(
                text = "${currentMediaIndex + 1} / ${mediaList.size}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier
                    .then(chipBgModifier)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}
