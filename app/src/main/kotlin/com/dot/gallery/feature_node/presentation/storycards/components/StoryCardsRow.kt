/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.storycards.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.PhotoAlbum
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.dot.gallery.core.Settings.Misc.rememberAllowBlur
import com.dot.gallery.feature_node.domain.model.StoryCard
import com.dot.gallery.feature_node.domain.model.StoryCardType
import com.dot.gallery.feature_node.domain.util.getUri
import com.dot.gallery.feature_node.presentation.util.GlideInvalidation
import com.dot.gallery.ui.theme.BlackScrim
import com.dot.gallery.ui.theme.WhiterBlackScrim
import com.dot.gallery.ui.theme.isDarkTheme

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun StoryCardsRow(
    cards: List<StoryCard>,
    onCardClick: (index: Int, card: StoryCard) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp)
) {
    if (cards.isEmpty()) return

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(
            items = cards,
            key = { _, card -> card.id }
        ) { index, card ->
            StoryCardItem(
                card = card,
                onClick = { onCardClick(index, card) }
            )
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun StoryCardItem(
    card: StoryCard,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDarkTheme = isDarkTheme()
    val allowBlur by rememberAllowBlur()
    val followTheme = remember(allowBlur) { !allowBlur }
    val gradientColor by animateColorAsState(
        if (followTheme) {
            if (isDarkTheme) BlackScrim else WhiterBlackScrim
        } else BlackScrim,
    )

    Box(
        modifier = modifier
            .width(148.dp)
            .height(220.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
    ) {
        if (card.thumbnailMedia != null) {
            GlideImage(
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                model = card.thumbnailMedia.getUri(),
                contentDescription = card.title,
                requestBuilderTransform = {
                    it.signature(GlideInvalidation.signature(card.thumbnailMedia))
                }
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

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            gradientColor
                        )
                    )
                )
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = card.title,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis,
                maxLines = 2
            )
            if (card.subtitle != null) {
                Text(
                    text = card.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Type indicator badge
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .background(
                    color = Color.Black.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(4.dp)
        ) {
            Icon(
                imageVector = card.type.icon,
                contentDescription = card.type.name,
                modifier = Modifier.size(16.dp),
                tint = Color.White
            )
        }
    }
}

private val StoryCardType.icon: ImageVector
    get() = when (this) {
        StoryCardType.MEMORIES -> Icons.Outlined.History
        StoryCardType.ALBUMS -> Icons.Outlined.PhotoAlbum
        StoryCardType.CATEGORIES -> Icons.Outlined.ImageSearch
        StoryCardType.LOCATIONS -> Icons.Outlined.LocationOn
        StoryCardType.FAVORITES -> Icons.Outlined.Favorite
    }
