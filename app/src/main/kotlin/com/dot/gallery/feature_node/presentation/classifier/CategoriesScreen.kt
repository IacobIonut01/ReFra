package com.dot.gallery.feature_node.presentation.classifier

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.outlined.Scanner
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dokar.pinchzoomgrid.PinchZoomGridLayout
import com.dokar.pinchzoomgrid.rememberPinchZoomGridState
import com.dot.gallery.R
import com.dot.gallery.core.Constants.Animation.enterAnimation
import com.dot.gallery.core.Constants.Animation.exitAnimation
import com.dot.gallery.feature_node.presentation.common.components.MediaImage
import com.dot.gallery.feature_node.presentation.common.components.TwoLinedDateToolbarTitle
import com.dot.gallery.feature_node.presentation.library.NoCategories
import com.dot.gallery.feature_node.presentation.library.components.LibrarySmallItem
import com.dot.gallery.feature_node.presentation.util.Screen
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    navigateUp: () -> Unit,
    navigate: (String) -> Unit,
) {
    val viewModel = hiltViewModel<CategoriesViewModel>()
    val categories by viewModel.classifiedCategories.collectAsStateWithLifecycle()
    val categoriesWithMedia by viewModel.categoriesWithMedia.collectAsStateWithLifecycle()
    val classifiedCount by viewModel.classifiedMediaCount.collectAsStateWithLifecycle()

    val categoriesIsEmpty by remember {
        derivedStateOf { categories.isEmpty() }
    }

    var canScroll by rememberSaveable { mutableStateOf(true) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(
        state = rememberTopAppBarState(),
        canScroll = { canScroll }
    )

    val pinchState = rememberPinchZoomGridState(
        cellsList = listOf(GridCells.Fixed(3)),
        initialCellsIndex = 0
    )

    LaunchedEffect(pinchState.isZooming) {
        canScroll = !pinchState.isZooming
    }

    Box {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                TopAppBar(
                    title = {
                        TwoLinedDateToolbarTitle(
                            albumName = stringResource(R.string.categories),
                            dateHeader = stringResource(R.string.classified_media, classifiedCount)
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = navigateUp) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back_cd)
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior
                )
            }
        ) { paddings ->
            PinchZoomGridLayout(state = pinchState) {
                LazyVerticalGrid(
                    state = gridState,
                    modifier = Modifier
                        .fillMaxSize()
                        .animateContentSize(),
                    columns = gridCells,
                    contentPadding = remember(paddings) {
                        PaddingValues(
                            top = paddings.calculateTopPadding() + 24.dp,
                            bottom = paddings.calculateBottomPadding() + 128.dp,
                            start = 24.dp,
                            end = 24.dp
                        )
                    },
                    userScrollEnabled = canScroll,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {

                    items(
                        items = categoriesWithMedia,
                        key = { it.category!! }
                    ) { item ->
                        Column(
                            modifier = Modifier.animateItem(),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            MediaImage(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(16.dp)),
                                media = item,
                                selectedMedia = remember { mutableStateListOf() },
                                selectionState = remember { mutableStateOf(false) },
                                onItemClick = {
                                    navigate(Screen.CategoryViewScreen.category(item.category!!))
                                },
                                onItemLongClick = { },
                                canClick = true
                            )

                            Text(
                                text = item.category!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    item(
                        key = "Categories_scan",
                        span = { GridItemSpan(maxLineSpan) }
                    ) {
                        val isRunning by viewModel.isRunning.collectAsStateWithLifecycle()
                        val progress by viewModel.progress.collectAsStateWithLifecycle()

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItem()
                                .animateContentSize(),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {

                            if (categoriesIsEmpty) {
                                NoCategories {
                                    viewModel.startClassification()
                                }
                            }

                            if (!categoriesIsEmpty || isRunning) {
                                ScannerButton(
                                    isRunning = isRunning,
                                    indicatorCounter = progress,
                                    contentColor = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .animateItem()
                                        .combinedClickable(
                                            onLongClick = {
                                                if (isRunning) viewModel.stopClassification()
                                            },
                                            onClick = {
                                                if (!isRunning) viewModel.startClassification()
                                            }
                                        )
                                )
                            }

                            if (!categoriesIsEmpty && !isRunning) {
                                LibrarySmallItem(
                                    title = stringResource(R.string.delete_all_categories),
                                    subtitle = stringResource(R.string.delete_all_categories_summary),
                                    icon = Icons.Default.Delete,
                                    contentColor = MaterialTheme.colorScheme.error,
                                    modifier = Modifier
                                        .animateItem()
                                        .clickable(onClick = viewModel::deleteClassifications)
                                )
                            }

                            LibrarySmallItem(
                                title = stringResource(R.string.disclaimer),
                                subtitle = stringResource(R.string.disclaimer_classification),
                                icon = Icons.Default.Info,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                containerColor = Color.Transparent,
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun ScannerButton(
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onTertiaryContainer,
    indicatorCounter: Float = 0f,
    isRunning: Boolean = false
) {
    ListItem(
        colors = ListItemDefaults.colors(
            containerColor = contentColor.copy(alpha = 0.1f),
            headlineColor = contentColor
        ),
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .then(modifier),
        headlineContent = {
            val scanningMediaText = stringResource(R.string.scanning_media)
            val scanForNewCategoriesText = stringResource(R.string.scan_for_new_categories)
            val text = remember(isRunning) {
                if (isRunning) scanningMediaText else scanForNewCategoriesText
            }
            Text(
                modifier = Modifier
                    .then(if (isRunning) Modifier.padding(top = 8.dp) else Modifier),
                text = text,
                style = MaterialTheme.typography.labelLarge,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1
            )
        },
        leadingContent = {
            Icon(
                imageVector = Icons.Outlined.Scanner,
                tint = contentColor,
                contentDescription = stringResource(R.string.scan_for_new_categories)
            )
        },
        trailingContent = {
            AnimatedVisibility(
                visible = isRunning,
                enter = enterAnimation,
                exit = exitAnimation
            ) {
                Text(
                    text = remember(indicatorCounter) {
                        String.format(
                            Locale.getDefault(),
                            "%.1f",
                            indicatorCounter.coerceIn(0f..100f)
                        ) + "%"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 4.dp)
                )
            }
        },
        supportingContent = if (isRunning) {
            {
                Column(
                    modifier = Modifier.padding(vertical = 16.dp)
                ) {
                    AnimatedVisibility(
                        visible = indicatorCounter < 100f
                    ) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            progress = { (indicatorCounter / 100f).coerceAtLeast(0f) },
                            color = contentColor,
                        )
                    }

                    AnimatedVisibility(
                        visible = indicatorCounter == 100f
                    ) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = contentColor,
                        )
                    }
                }
            }
        } else null
    )
}