/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.classifier.components

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ImageSearch
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.dot.gallery.R
import com.dot.gallery.core.Settings.Misc.rememberAllowBlur
import com.dot.gallery.feature_node.domain.model.LocationMedia
import com.dot.gallery.feature_node.domain.util.getUri
import com.dot.gallery.feature_node.presentation.library.CategoryMedia
import com.dot.gallery.feature_node.presentation.search.SearchMediaItem
import com.dot.gallery.feature_node.presentation.util.GlideInvalidation
import com.dot.gallery.ui.theme.BlackScrim
import com.dot.gallery.ui.theme.WhiterBlackScrim
import com.dot.gallery.ui.theme.isDarkTheme
import kotlinx.collections.immutable.ImmutableList

/**
 * A horizontal carousel of category recommendations for the search screen.
 * Matches the LibraryScreen category carousel style.
 */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun CategoryCarousel(
    categories: ImmutableList<CategoryMedia>,
    onCategoryClick: (CategoryMedia) -> Unit,
    modifier: Modifier = Modifier,
    title: String? = stringResource(R.string.browse_categories),
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp)
) {
    if (categories.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(contentPadding)
            )
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(
                items = categories,
                key = { "category_${it.category.id}" }
            ) { categoryMedia ->
                val isDarkTheme = isDarkTheme()
                val allowBlur by rememberAllowBlur()
                val followTheme = remember(allowBlur) { !allowBlur }
                val gradientColor by animateColorAsState(
                    if (followTheme) {
                        if (isDarkTheme) BlackScrim else WhiterBlackScrim
                    } else BlackScrim,
                )
                Box(
                    modifier = Modifier
                        .width(164.dp)
                        .height(256.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .clickable { onCategoryClick(categoryMedia) },
                ) {
                    if (categoryMedia.thumbnailMedia != null) {
                        GlideImage(
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            model = categoryMedia.thumbnailMedia.getUri(),
                            contentDescription = categoryMedia.category.name,
                            requestBuilderTransform = {
                                it.signature(GlideInvalidation.signature(categoryMedia.thumbnailMedia))
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
                                imageVector = Icons.Outlined.ImageSearch,
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
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = categoryMedia.category.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1
                        )
                        Text(
                            text = stringResource(
                                R.string.category_media_count,
                                categoryMedia.category.mediaCount
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

/**
 * A horizontal carousel of location recommendations for the search screen.
 * Matches the LibraryScreen location carousel style.
 */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun LocationCarousel(
    locations: ImmutableList<LocationMedia>,
    onLocationClick: (LocationMedia) -> Unit,
    modifier: Modifier = Modifier,
    title: String? = stringResource(R.string.locations),
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp)
) {
    if (locations.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(contentPadding)
            )
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(
                items = locations,
                key = { it.location }
            ) { locationMedia ->
                val isDarkTheme = isDarkTheme()
                val allowBlur by rememberAllowBlur()
                val followTheme = remember(allowBlur) { !allowBlur }
                val gradientColor by animateColorAsState(
                    if (followTheme) {
                        if (isDarkTheme) BlackScrim else WhiterBlackScrim
                    } else BlackScrim,
                )
                Box(
                    modifier = Modifier
                        .width(164.dp)
                        .height(256.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .clickable { onLocationClick(locationMedia) },
                ) {
                    GlideImage(
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        model = locationMedia.media.getUri(),
                        contentDescription = locationMedia.location,
                        requestBuilderTransform = {
                            it.signature(GlideInvalidation.signature(locationMedia.media))
                        }
                    )
                    Text(
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
                            .padding(24.dp),
                        text = locationMedia.location,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 2
                    )
                }
            }
        }
    }
}

/**
 * A generic horizontal carousel for dynamic search items (MIME types, lens models, media modes, etc.).
 * Uses the same visual style as CategoryCarousel / LocationCarousel.
 */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun SearchCarousel(
    items: ImmutableList<SearchMediaItem>,
    onItemClick: (SearchMediaItem) -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp)
) {
    if (items.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(contentPadding)
            )
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(
                items = items,
                key = { "search_${it.key}" }
            ) { item ->
                val isDarkTheme = isDarkTheme()
                val allowBlur by rememberAllowBlur()
                val followTheme = remember(allowBlur) { !allowBlur }
                val gradientColor by animateColorAsState(
                    if (followTheme) {
                        if (isDarkTheme) BlackScrim else WhiterBlackScrim
                    } else BlackScrim,
                )
                Box(
                    modifier = Modifier
                        .width(164.dp)
                        .height(256.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .clickable { onItemClick(item) },
                ) {
                    if (item.media != null) {
                        GlideImage(
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            model = item.media.getUri(),
                            contentDescription = item.label,
                            requestBuilderTransform = {
                                it.signature(GlideInvalidation.signature(item.media))
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
                                imageVector = Icons.Outlined.ImageSearch,
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
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1
                        )
                        if (item.count > 0) {
                            Text(
                                text = stringResource(
                                    R.string.category_media_count,
                                    item.count
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}
