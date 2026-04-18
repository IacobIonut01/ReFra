package com.dot.gallery.feature_node.presentation.library

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import com.dot.gallery.core.util.SdkCompat
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.dokar.pinchzoomgrid.PinchZoomGridLayout
import com.dokar.pinchzoomgrid.rememberPinchZoomGridState
import com.dot.gallery.R
import com.dot.gallery.core.Constants.albumCellsList
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.Settings.Album.rememberAlbumGridSize
import com.dot.gallery.core.Settings.Misc.rememberAllowBlur
import com.dot.gallery.core.Settings.Misc.rememberNoClassification
import com.dot.gallery.core.navigate
import com.dot.gallery.feature_node.domain.model.MediaMetadataState
import com.dot.gallery.feature_node.domain.util.getUri
import com.dot.gallery.feature_node.presentation.library.components.LibrarySmallItem
import com.dot.gallery.feature_node.presentation.library.components.dashedBorder
import com.dot.gallery.feature_node.presentation.mediaview.rememberedDerivedState
import com.dot.gallery.feature_node.presentation.search.MainSearchBar
import com.dot.gallery.feature_node.presentation.util.GlideInvalidation
import com.dot.gallery.feature_node.presentation.util.LocalHazeState
import com.dot.gallery.feature_node.presentation.util.Screen
import com.dot.gallery.ui.core.icons.Encrypted
import com.dot.gallery.ui.theme.BlackScrim
import com.dot.gallery.ui.theme.WhiterBlackScrim
import com.dot.gallery.ui.theme.isDarkTheme
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.dot.gallery.ui.core.Icons as GalleryIcons

@OptIn(
    ExperimentalSharedTransitionApi::class, ExperimentalHazeMaterialsApi::class,
    ExperimentalGlideComposeApi::class
)
@Composable
fun LibraryScreen(
    paddingValues: PaddingValues,
    isScrolling: MutableState<Boolean>,
    metadataState: State<MediaMetadataState>,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
) {
    val eventHandler = LocalEventHandler.current
    val viewModel = hiltViewModel<LibraryViewModel>()
    var lastCellIndex by rememberAlbumGridSize()

    val pinchState = rememberPinchZoomGridState(
        cellsList = albumCellsList,
        initialCellsIndex = lastCellIndex
    )

    LaunchedEffect(pinchState.isZooming) {
        withContext(Dispatchers.IO) {
            lastCellIndex = albumCellsList.indexOf(pinchState.currentCells)
        }
    }

    val locations by viewModel.locations.collectAsStateWithLifecycle()

    val indicatorState by viewModel.indicatorState.collectAsStateWithLifecycle()

    // New category system
    val topCategories by viewModel.topCategories.collectAsStateWithLifecycle()
    val totalCategoryCount by viewModel.totalCategoryCount.collectAsStateWithLifecycle()
    val noCategoriesFound by rememberedDerivedState { topCategories.isEmpty() }

    // Locations
    val noLocationsFound by rememberedDerivedState { locations.isEmpty() }
    val totalLocationsCount by rememberedDerivedState { locations.size }
    val topLocations by rememberedDerivedState { locations.take(10) }

    var noClassification by rememberNoClassification()

    Scaffold(
        modifier = Modifier.padding(
            start = paddingValues.calculateStartPadding(LocalLayoutDirection.current),
            end = paddingValues.calculateEndPadding(LocalLayoutDirection.current)
        ),
        topBar = {
            MainSearchBar(
                isScrolling = isScrolling,
                sharedTransitionScope = sharedTransitionScope,
                animatedContentScope = animatedContentScope
            ) {
                val tertiaryContainer = MaterialTheme.colorScheme.tertiaryFixed
                val onTertiaryContainer = MaterialTheme.colorScheme.onTertiaryFixed
                val allowBlur by rememberAllowBlur()
                val settingsInteractionSource = remember { MutableInteractionSource() }
                val isPressed = settingsInteractionSource.collectIsPressedAsState()
                val cornerRadius by animateDpAsState(
                    targetValue = if (isPressed.value) 32.dp else 16.dp,
                    label = "cornerRadius"
                )

                val settingsBackgroundModifier = remember(allowBlur) {
                    if (!allowBlur) {
                        Modifier.background(
                            color = tertiaryContainer,
                            shape = RoundedCornerShape(cornerRadius)
                        )
                    } else {
                        Modifier
                    }
                }

                IconButton(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(cornerRadius))
                        .then(settingsBackgroundModifier)
                        .hazeEffect(
                            state = LocalHazeState.current,
                            style = HazeMaterials.regular(
                                containerColor = tertiaryContainer
                            )
                        ),
                    interactionSource = settingsInteractionSource,
                    onClick = { eventHandler.navigate(Screen.SettingsScreen()) }
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = stringResource(R.string.settings_title),
                        tint = onTertiaryContainer
                    )
                }
            }
        }
    ) { it ->
        PinchZoomGridLayout(
            state = pinchState,
            modifier = Modifier.hazeSource(LocalHazeState.current)
        ) {
            LaunchedEffect(gridState.isScrollInProgress) {
                isScrolling.value = gridState.isScrollInProgress
            }
            LazyVerticalGrid(
                state = gridState,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .fillMaxSize(),
                columns = gridCells,
                contentPadding = PaddingValues(
                    top = it.calculateTopPadding(),
                    bottom = paddingValues.calculateBottomPadding() + 128.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(
                    span = { GridItemSpan(maxLineSpan) },
                    key = "headerButtons"
                ) {
                    Column(
                        modifier = Modifier
                            .animateItem()
                            .fillMaxWidth()
                            .pinchItem(key = "headerButtons")
                            .padding(horizontal = 16.dp)
                            .padding(top = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (SdkCompat.supportsTrash || SdkCompat.supportsFavorites) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(
                                    16.dp,
                                    Alignment.CenterHorizontally
                                )
                            ) {
                                if (SdkCompat.supportsTrash) {
                                    LibrarySmallItem(
                                        title = stringResource(R.string.trash),
                                        icon = Icons.Outlined.DeleteOutline,
                                        contentColor = MaterialTheme.colorScheme.primary,
                                        useIndicator = true,
                                        indicatorCounter = indicatorState.trashCount,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                eventHandler.navigate(Screen.TrashedScreen.route)
                                            }
                                    )
                                }
                                if (SdkCompat.supportsFavorites) {
                                    LibrarySmallItem(
                                        title = stringResource(R.string.favorites),
                                        icon = Icons.Outlined.FavoriteBorder,
                                        contentColor = MaterialTheme.colorScheme.error,
                                        useIndicator = true,
                                        indicatorCounter = indicatorState.favoriteCount,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                eventHandler.navigate(Screen.FavoriteScreen.route)
                                            }
                                    )
                                }
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(
                                16.dp,
                                Alignment.CenterHorizontally
                            )
                        ) {
                            LibrarySmallItem(
                                title = stringResource(R.string.vault),
                                icon = GalleryIcons.Encrypted,
                                contentColor = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        eventHandler.navigate(Screen.VaultScreen())
                                    },
                                contentDescription = stringResource(R.string.vault)
                            )
                            LibrarySmallItem(
                                title = stringResource(R.string.ignored),
                                icon = Icons.Outlined.VisibilityOff,
                                contentColor = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        eventHandler.navigate(Screen.IgnoredScreen())
                                    }
                            )
                        }
                    }
                }

                // Locations section
                if (!noLocationsFound) {
                    item(
                        span = { GridItemSpan(maxLineSpan) },
                        key = "LocationsHeader"
                    ) {
                        Column(
                            modifier = Modifier
                                .animateItem()
                                .pinchItem(key = "LocationsHeader")
                                .padding(horizontal = 16.dp)
                                .padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            LibrarySmallItem(
                                title = stringResource(R.string.locations),
                                icon = null,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                containerColor = MaterialTheme.colorScheme.surface,
                                useIndicator = true,
                                indicatorCounter = totalLocationsCount,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        eventHandler.navigate(Screen.LocationsScreen())
                                    }
                            )
                        }
                    }
                    // Locations carousel
                    item(
                        span = { GridItemSpan(maxLineSpan) },
                        key = "LocationsList"
                    ) {
                        LazyRow(
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .padding(top = 8.dp)
                                .clip(RoundedCornerShape(16.dp)),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(
                                items = locations,
                                key = { it.toString() }
                            ) { (media, location) ->
                                with(sharedTransitionScope) {
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
                                            .clickable {
                                                val gpsLocationNameCity =
                                                    location.substringBefore(",")
                                                val gpsLocationNameCountry =
                                                    location.substringAfterLast(", ")
                                                eventHandler.navigate(
                                                    Screen.LocationTimelineScreen.location(
                                                        gpsLocationNameCity = gpsLocationNameCity,
                                                        gpsLocationNameCountry = gpsLocationNameCountry
                                                    )
                                                )
                                            },
                                    ) {
                                        GlideImage(
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop,
                                            model = media.getUri(),
                                            contentDescription = location,
                                            requestBuilderTransform = {
                                                it.signature(GlideInvalidation.signature(media))
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
                                            text = location,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color.White,
                                            fontWeight = FontWeight.SemiBold,
                                            textAlign = TextAlign.Center,
                                            overflow = TextOverflow.MiddleEllipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (!noClassification) {
                    if (!noCategoriesFound) {
                        // "See all categories" header below carousel
                        item(
                            span = { GridItemSpan(maxLineSpan) },
                            key = "CategoriesHeader"
                        ) {
                            Column(
                                modifier = Modifier
                                    .animateItem()
                                    .pinchItem(key = "CategoriesHeader")
                                    .padding(horizontal = 16.dp)
                                    .padding(top = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                LibrarySmallItem(
                                    title = stringResource(R.string.categories),
                                    icon = null,
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    useIndicator = true,
                                    indicatorCounter = totalCategoryCount,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            eventHandler.navigate(Screen.CategoriesScreen())
                                        }
                                )
                            }
                        }
                        // Categories carousel first
                        item(
                            span = { GridItemSpan(maxLineSpan) },
                            key = "CategoriesList"
                        ) {
                            LazyRow(
                                modifier = Modifier
                                    .padding(horizontal = 16.dp)
                                    .padding(top = 8.dp)
                                    .clip(RoundedCornerShape(16.dp)),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(
                                    items = topCategories,
                                    key = { categoryMedia -> "category_${categoryMedia.category.id}" }
                                ) { (category, thumbnailMedia) ->
                                    with(sharedTransitionScope) {
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
                                                .combinedClickable(
                                                    onClick = {
                                                        eventHandler.navigate(
                                                            Screen.CategoryViewScreen.categoryId(
                                                                category.id
                                                            )
                                                        )
                                                    },
                                                    onLongClick = {
                                                        eventHandler.navigate(
                                                            Screen.EditCategoryScreen.categoryId(
                                                                category.id
                                                            )
                                                        )
                                                    }
                                                ),
                                        ) {
                                            if (thumbnailMedia != null) {
                                                GlideImage(
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop,
                                                    model = thumbnailMedia.getUri(),
                                                    contentDescription = category.name,
                                                    requestBuilderTransform = {
                                                        it.signature(
                                                            GlideInvalidation.signature(
                                                                thumbnailMedia
                                                            )
                                                        )
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
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                            alpha = 0.5f
                                                        )
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
                                                    text = category.name,
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
                                                        category.mediaCount
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
                    if (noCategoriesFound) {
                        item(
                            span = { GridItemSpan(maxLineSpan) },
                            key = "NoCategories"
                        ) {
                            NoCategories(
                                modifier = Modifier
                                    .animateItem()
                                    .pinchItem(key = "NoCategories")
                                    .padding(16.dp)
                            ) {
                                eventHandler.navigate(Screen.CategoriesScreen())
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NoCategories(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val brush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.secondary,
            MaterialTheme.colorScheme.tertiary,
        )
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .dashedBorder(
                brush = brush,
                shape = RoundedCornerShape(16.dp),
                gapLength = 8.dp
            )
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = rememberVectorPainter(image = Icons.Outlined.ImageSearch),
            contentDescription = null,
            modifier = Modifier
                .size(64.dp)
                .drawWithContent {
                    with(drawContext.canvas.nativeCanvas) {
                        val checkPoint = saveLayer(null, null)
                        drawContent()
                        drawRect(
                            brush = brush,
                            blendMode = BlendMode.SrcIn
                        )
                        restoreToCount(checkPoint)
                    }
                }
        )
        Text(
            text = stringResource(R.string.categorise_your_media),
            style = MaterialTheme.typography.titleMedium.copy(brush = brush),
        )
    }
}