/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.classifier

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dokar.pinchzoomgrid.PinchZoomGridLayout
import com.dokar.pinchzoomgrid.rememberPinchZoomGridState
import com.dot.gallery.R
import com.dot.gallery.core.Constants.cellsList
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.Settings.Misc.rememberGridSize
import com.dot.gallery.core.navigate
import com.dot.gallery.core.presentation.components.DragHandle
import com.dot.gallery.core.presentation.components.EmptyMedia
import com.dot.gallery.core.presentation.components.NavigationBackButton
import kotlinx.coroutines.launch
import com.dot.gallery.feature_node.domain.model.Category
import com.dot.gallery.feature_node.domain.model.MediaMetadataState
import com.dot.gallery.feature_node.presentation.common.components.MediaGridView
import com.dot.gallery.feature_node.presentation.util.LocalHazeState
import com.dot.gallery.feature_node.presentation.util.Screen
import dev.chrisbanes.haze.LocalHazeStyle
import dev.chrisbanes.haze.hazeEffect

@OptIn(
    ExperimentalSharedTransitionApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class
)
@Composable
fun AddCategoryScreen(
    paddingValues: PaddingValues,
    isScrolling: MutableState<Boolean>,
    metadataState: State<MediaMetadataState>,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    onNavigateBack: () -> Unit
) {
    val viewModel = hiltViewModel<AddCategoryViewModel>()
    val eventHandler = LocalEventHandler.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val categoryName by viewModel.categoryName.collectAsStateWithLifecycle()
    val searchTerms by viewModel.searchTerms.collectAsStateWithLifecycle()
    val threshold by viewModel.threshold.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val previewMediaState by viewModel.previewMediaState.collectAsStateWithLifecycle()
    val previewCount by viewModel.previewCount.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    var lastCellIndex by rememberGridSize()
    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            initialValue = SheetValue.Hidden,
            skipHiddenState = false
        )
    )

    val density = LocalDensity.current
    val dragHandleAlpha by remember {
        derivedStateOf {
            val offset = runCatching { scaffoldState.bottomSheetState.requireOffset() }.getOrElse { Float.MAX_VALUE }
            val fadeThreshold = with(density) { 200.dp.toPx() }
            (offset / fadeThreshold).coerceIn(0f, 1f)
        }
    }

    val isValid = categoryName.isNotBlank() && searchTerms.isNotBlank()

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        state = rememberTopAppBarState(),
        flingAnimationSpec = null
    )

    Box(
        modifier = Modifier.padding(
            start = paddingValues.calculateStartPadding(LocalLayoutDirection.current),
            end = paddingValues.calculateEndPadding(LocalLayoutDirection.current)
        )
    ) {
        BottomSheetScaffold(
            scaffoldState = scaffoldState,
            sheetPeekHeight = 0.dp,
            sheetDragHandle = { DragHandle(alpha = dragHandleAlpha) },
            sheetContent = {
                Box(
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.best_match),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                val sheetDpCacheWindow = LazyLayoutCacheWindow(ahead = 200.dp, behind = 100.dp)
                val sheetPinchState = rememberPinchZoomGridState(
                    cellsList = cellsList,
                    initialCellsIndex = lastCellIndex,
                    gridState = rememberLazyGridState(cacheWindow = sheetDpCacheWindow)
                )
                PinchZoomGridLayout(state = sheetPinchState) {
                    MediaGridView(
                        mediaState = remember(previewMediaState) {
                            mutableStateOf(previewMediaState.copy(isLoading = false))
                        },
                        metadataState = metadataState,
                        allowSelection = false,
                        showSearchBar = false,
                        enableStickyHeaders = false,
                        paddingValues = PaddingValues(
                            bottom = 128.dp
                        ),
                        canScroll = true,
                        allowHeaders = false,
                        showMonthlyHeader = false,
                        isScrolling = isScrolling,
                        emptyContent = {
                            EmptyMedia(
                                title = stringResource(R.string.no_matching_photos)
                            )
                        },
                        sharedTransitionScope = sharedTransitionScope,
                        animatedContentScope = animatedContentScope
                    ) { media ->
                        eventHandler.navigate(
                            Screen.MediaViewScreen.idAndQuery(media.id)
                        )
                    }
                }
            }
        ) {
            Scaffold(
                modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                topBar = {
                    LargeTopAppBar(
                        modifier = Modifier.hazeEffect(
                            state = LocalHazeState.current,
                            style = LocalHazeStyle.current
                        ),
                        title = {
                            CategoryNameInput(
                                value = categoryName,
                                onValueChange = { viewModel.updateCategoryName(it) },
                                placeholder = stringResource(R.string.category_name_hint)
                            )
                        },
                        navigationIcon = {
                            NavigationBackButton(forcedAction = onNavigateBack)
                        },
                        scrollBehavior = scrollBehavior,
                        colors = TopAppBarDefaults.topAppBarColors(
                            scrolledContainerColor = MaterialTheme.colorScheme.surface,
                        ),
                    )
                },
                floatingActionButton = {
                    AnimatedVisibility(
                        visible = isValid && !isSaving,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        FloatingActionButton(
                            onClick = {
                                keyboardController?.hide()
                                viewModel.saveCategory(onNavigateBack)
                            },
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = stringResource(R.string.create),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .padding(top = innerPadding.calculateTopPadding())
                        .padding(
                            bottom = paddingValues.calculateBottomPadding() + 128.dp
                        )
                        .verticalScroll(rememberScrollState())
                ) {
                    CategoryInputControls(
                        searchTerms = searchTerms,
                        onSearchTermsChange = { viewModel.updateSearchTerms(it) },
                        threshold = threshold,
                        onThresholdChange = { viewModel.updateThreshold(it) },
                        isLoading = isLoading,
                        previewCount = previewCount,
                        onMatchingPhotosClick = {
                            if (previewCount > 0) {
                                scope.launch { scaffoldState.bottomSheetState.expand() }
                            }
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun CategoryNameInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .padding(end = 16.dp),
        placeholder = {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        },
        textStyle = MaterialTheme.typography.headlineSmall.copy(
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        ),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Words,
            imeAction = ImeAction.Next
        )
    )
}

@Composable
internal fun CategoryInputControls(
    searchTerms: String,
    onSearchTermsChange: (String) -> Unit,
    threshold: Float,
    onThresholdChange: (Float) -> Unit,
    isLoading: Boolean,
    previewCount: Int,
    onMatchingPhotosClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Search Terms Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.search_terms),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.search_terms_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = searchTerms,
                onValueChange = onSearchTermsChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = stringResource(R.string.search_terms_example),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                },
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    imeAction = ImeAction.Done
                )
            )
        }

        // Accuracy Threshold Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.accuracy_threshold),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (threshold == Category.DEFAULT_THRESHOLD) {
                        String.format(
                            LocalLocale.current.platformLocale,
                            "%.0f%% (%s)",
                            threshold * 100,
                            stringResource(R.string.recommended)
                        )
                    } else {
                        String.format(LocalLocale.current.platformLocale, "%.0f%%", threshold * 100)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Slider(
                value = threshold,
                onValueChange = onThresholdChange,
                valueRange = Category.MIN_THRESHOLD..Category.MAX_THRESHOLD,
                steps = 6,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.more_results),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.more_accurate),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Preview Count & Helper
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .clickable(enabled = previewCount > 0 && !isLoading) {
                    onMatchingPhotosClick()
                }
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.matching_photos),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else if (previewCount > 0) {
                    Text(
                        text = previewCount.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Text(
                text = if (previewCount > 0) {
                    stringResource(R.string.matching_photos_count, previewCount)
                } else {
                    stringResource(R.string.category_helper_text)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun CategoryEmptyContent(
    hasSearchTerms: Boolean,
    isLoading: Boolean
) {
    if (!hasSearchTerms || isLoading) {
        // Don't show anything — the helper text in the card above guides the user
    } else {
        EmptyMedia(
            title = stringResource(R.string.no_matching_photos)
        )
    }
}
