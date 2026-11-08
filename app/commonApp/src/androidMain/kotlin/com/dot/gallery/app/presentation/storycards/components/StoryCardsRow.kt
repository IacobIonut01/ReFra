/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.storycards.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.PhotoAlbum
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.CarouselDefaults
import androidx.compose.material3.carousel.CarouselState
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.dot.gallery.domain.model.StoryCard
import com.dot.gallery.domain.model.StoryCardType
import com.dot.gallery.domain.util.getUri
import com.github.panpf.sketch.AsyncImage
import com.github.panpf.sketch.request.ComposableImageRequest
import com.github.panpf.sketch.resize.Precision
import com.dot.gallery.ui.theme.BlackScrim
import com.dot.gallery.core.presentation.util.storyCardSharedElement
import com.dot.gallery.core.ui.util.shimmerLoading
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.gallery_home_loading_stories
import com.dot.gallery.generated.resources.story_type_albums
import com.dot.gallery.generated.resources.story_type_categories
import com.dot.gallery.generated.resources.story_type_cloud_memories
import com.dot.gallery.generated.resources.story_type_favorites
import com.dot.gallery.generated.resources.story_type_locations
import com.dot.gallery.generated.resources.story_type_memories
import org.jetbrains.compose.resources.StringResource
import kotlin.math.roundToInt


const val StoryCardsRowTag = "StoryCards.Row"
const val StoryCardsLoadingRowTag = "StoryCards.LoadingRow"
private val StoryCardHeight = 220.dp

/** Resting rounding of a story card; the viewer's flight morphs it away on open and back on dismiss. */
private val StoryCardCornerRadius = 28.dp
fun storyCardTag(id: Long) = "StoryCards.Card.$id"
fun storyCardTitleTag(id: Long) = "StoryCards.Title.$id"
fun storyCardTypeTag(id: Long) = "StoryCards.Type.$id"
fun storyCardScrimTag(id: Long) = "StoryCards.Scrim.$id"

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
@Composable
fun StoryCardsRow(
    cards: List<StoryCard>?,
    onCardClick: (card: StoryCard) -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    carouselState: CarouselState? = null,
) {
    if (cards == null) {
        LoadingStoryCardsRow(modifier = modifier, contentPadding = contentPadding)
        return
    }
    if (cards.isEmpty()) return

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth().height(StoryCardHeight).testTag(StoryCardsRowTag),
    ) {
        if (cards.size <= 2) {
            val direction = LocalLayoutDirection.current
            val available = (
                maxWidth - contentPadding.calculateStartPadding(direction) -
                    contentPadding.calculateEndPadding(direction) - 8.dp * (cards.size - 1)
                ).coerceAtLeast(0.dp)
            val itemWidth = (available / cards.size)
                .coerceAtMost(if (cards.size == 1) 400.dp else 280.dp)
            Row(
                modifier = Modifier.fillMaxSize().padding(contentPadding),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                cards.forEach { card ->
                    key(card.id) {
                        StoryCardItem(
                            card = card,
                            onClick = { onCardClick(card) },
                            modifier = storySharedModifier(card.id, sharedTransitionScope, animatedVisibilityScope)
                                .width(itemWidth)
                                .height(StoryCardHeight)
                                .testTag(storyCardTag(card.id)),
                        )
                    }
                }
            }
        } else {
            val state = carouselState ?: rememberCarouselState { cards.size }
            HorizontalMultiBrowseCarousel(
                state = state,
                modifier = Modifier.fillMaxSize(),
                preferredItemWidth = 188.dp,
                itemSpacing = 8.dp,
                flingBehavior = CarouselDefaults.multiBrowseFlingBehavior(state),
                contentPadding = contentPadding,
            ) { index ->
                val card = cards[index]
                val itemDrawInfo = carouselItemDrawInfo
                key(card.id) {
                    StoryCardItem(
                        card = card,
                        onClick = { onCardClick(card) },
                        captionBounds = { itemDrawInfo.maskRect },
                        modifier = storySharedModifier(card.id, sharedTransitionScope, animatedVisibilityScope)
                            .maskClip(RoundedCornerShape(StoryCardCornerRadius))
                            .testTag(storyCardTag(card.id)),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun storySharedModifier(
    cardId: Long,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
): Modifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
    with(sharedTransitionScope) {
        Modifier.storyCardSharedElement(
            cardId = cardId,
            animatedVisibilityScope = animatedVisibilityScope,
            cornerRadius = StoryCardCornerRadius,
        )
    }
} else Modifier

@Composable
private fun LoadingStoryCardsRow(modifier: Modifier, contentPadding: PaddingValues) {
    val loadingDescription = stringResource(Res.string.gallery_home_loading_stories)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(StoryCardHeight)
            .testTag(StoryCardsLoadingRowTag)
            .semantics {
                contentDescription = loadingDescription
                progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
            }
            .padding(contentPadding)
            .clipToBounds(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(188.dp, 120.dp, 56.dp).forEach { width ->
            Box(
                Modifier.width(width).height(StoryCardHeight)
                    .clip(RoundedCornerShape(StoryCardCornerRadius))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .shimmerLoading(),
            )
        }
    }
}

@Composable
private fun StoryCardItem(
    card: StoryCard,
    onClick: () -> Unit,
    captionBounds: (() -> Rect)? = null,
    modifier: Modifier = Modifier
) {
    val typeLabel = stringResource(card.type.displayNameRes)
    val cardDescription = listOfNotNull(typeLabel, card.title, card.subtitle).joinToString(". ")
    val captionHeight = remember { mutableIntStateOf(0) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(StoryCardCornerRadius))
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = cardDescription
            }
    ) {
        val thumb = card.thumbnailMedia
        if (thumb != null) {
            AsyncImage(
                request = ComposableImageRequest(thumb.getUri().toString()) {
                    resize(width = 300, height = 440, precision = Precision.LESS_PIXELS)
                    crossfade(false)
                },
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                contentDescription = null,
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = card.type.icon,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }

        // The scrim belongs to the entire image, not the mask-constrained caption layout.
        Box(
            Modifier.matchParentSize()
                .testTag(storyCardScrimTag(card.id))
                .drawBehind {
                    val visibleWidth = captionBounds?.invoke()?.width ?: size.width
                    val scrimAlpha = captionVisibility(visibleWidth, CaptionHiddenWidth.toPx(), CaptionShownWidth.toPx())
                    if (scrimAlpha > 0f) {
                        val textHeight = captionHeight.intValue.takeIf { it > 0 }?.toFloat() ?: 80.dp.toPx()
                        val start = (size.height - textHeight - 48.dp.toPx()).coerceAtLeast(0f)
                        drawRect(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0f),
                                0.3f to BlackScrim,
                                1f to BlackScrim,
                                startY = start,
                                endY = size.height,
                            ),
                            alpha = scrimAlpha,
                        )
                    }
                },
        )
        Box(
            Modifier.align(Alignment.TopCenter)
                .fillMaxWidth()
                .carouselOverlayBounds(captionBounds, hiddenWidth = 40.dp, shownWidth = 64.dp)
                .padding(12.dp),
        ) {
            Box(
                modifier = Modifier.size(32.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .testTag(storyCardTypeTag(card.id)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = card.type.icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .carouselOverlayBounds(captionBounds)
                .onSizeChanged { captionHeight.intValue = it.height }
                .padding(16.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = card.title,
                modifier = Modifier.testTag(storyCardTitleTag(card.id)),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Medium,
                overflow = TextOverflow.Ellipsis,
                maxLines = 2
            )
            val subtitle = card.subtitle
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

    }
}

private val CaptionHiddenWidth = 72.dp
private val CaptionShownWidth = 136.dp

/** 0 when the visible card is narrower than [hidden], 1 from [shown], eased in between. */
private fun captionVisibility(visible: Float, hidden: Float, shown: Float): Float {
    val t = ((visible - hidden) / (shown - hidden)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/**
 * Carousel images mask around their centre, so overlays follow the visible mask. They are measured
 * once at the full card width: re-measuring against the shrinking mask made titles re-wrap and
 * ellipsize on every frame. The mask clips them instead, a soft trailing edge hides the cut, and
 * they fade out as the card narrows.
 */
private fun Modifier.carouselOverlayBounds(
    maskBounds: (() -> Rect)?,
    hiddenWidth: Dp = CaptionHiddenWidth,
    shownWidth: Dp = CaptionShownWidth,
): Modifier = this
    .layout { measurable, constraints ->
        val placeable = measurable.measure(constraints.copy(minWidth = 0))
        layout(constraints.maxWidth, placeable.height) {
            val mask = maskBounds?.invoke()
            val left = mask?.left ?: 0f
            val visible = mask?.width ?: constraints.maxWidth.toFloat()
            val alpha = captionVisibility(visible, hiddenWidth.toPx(), shownWidth.toPx())
            placeable.placeWithLayer(x = left.roundToInt(), y = 0) {
                this.alpha = alpha
                // A slight drift toward the visible edge reads as the caption tucking away.
                translationX = (1f - alpha) * -8.dp.toPx()
            }
        }
    }
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val mask = maskBounds?.invoke() ?: return@drawWithContent
        val fade = 24.dp.toPx()
        // This node is placed at the mask's left edge, so the visible width is the local edge.
        val edge = mask.width
        if (edge < size.width) {
            drawRect(
                Brush.horizontalGradient(
                    0f to Color.Transparent,
                    1f to Color.Black,
                    startX = edge - fade,
                    endX = edge,
                ),
                topLeft = Offset(edge - fade, 0f),
                size = Size(size.width - edge + fade, size.height),
                blendMode = BlendMode.DstOut,
            )
        }
    }

private val StoryCardType.icon: ImageVector
    get() = when (this) {
        StoryCardType.MEMORIES -> Icons.Outlined.History
        StoryCardType.ALBUMS -> Icons.Outlined.PhotoAlbum
        StoryCardType.CATEGORIES -> Icons.Outlined.ImageSearch
        StoryCardType.LOCATIONS -> Icons.Outlined.LocationOn
        StoryCardType.FAVORITES -> Icons.Outlined.Favorite
        StoryCardType.CLOUD_MEMORIES -> Icons.Outlined.Cloud
    }

private val StoryCardType.displayNameRes: StringResource
    get() = when (this) {
        StoryCardType.MEMORIES -> Res.string.story_type_memories
        StoryCardType.ALBUMS -> Res.string.story_type_albums
        StoryCardType.CATEGORIES -> Res.string.story_type_categories
        StoryCardType.LOCATIONS -> Res.string.story_type_locations
        StoryCardType.FAVORITES -> Res.string.story_type_favorites
        StoryCardType.CLOUD_MEMORIES -> Res.string.story_type_cloud_memories
    }
