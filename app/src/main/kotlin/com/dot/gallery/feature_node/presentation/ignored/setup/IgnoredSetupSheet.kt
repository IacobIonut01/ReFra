package com.dot.gallery.feature_node.presentation.ignored.setup

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.FilterNone
import androidx.compose.material.icons.outlined.FolderCopy
import androidx.compose.material.icons.outlined.PhotoAlbum
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.dot.gallery.R
import com.dot.gallery.core.Constants
import com.dot.gallery.core.Settings.Album.rememberAlbumGridSize
import com.dot.gallery.core.presentation.components.DragHandle
import com.dot.gallery.core.presentation.components.NavigationBackButton
import com.dot.gallery.feature_node.domain.model.Album
import com.dot.gallery.feature_node.domain.model.AlbumState
import com.dot.gallery.feature_node.domain.model.IgnoredAlbum
import com.dot.gallery.feature_node.presentation.ignored.setup.components.ConfirmationCard
import com.dot.gallery.feature_node.presentation.ignored.setup.components.RegexExample
import com.dot.gallery.feature_node.presentation.ignored.setup.components.SectionHeader
import com.dot.gallery.feature_node.presentation.ignored.setup.components.SelectableAlbumItem
import com.dot.gallery.feature_node.presentation.ignored.setup.components.TypeOptionCard
import com.dot.gallery.feature_node.presentation.util.AppBottomSheetState
import com.dot.gallery.feature_node.presentation.util.PreviewHost
import com.dot.gallery.ui.core.icons.RegularExpression
import kotlinx.coroutines.launch
import com.dot.gallery.ui.core.Icons as GalleryIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IgnoredSetupSheet(
    sheetState: AppBottomSheetState,
    albumState: State<AlbumState>,
) {
    val vm = hiltViewModel<IgnoredSetupViewModel>()
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    LaunchedEffect(sheetState.isVisible, albumState.value.albumsWithBlacklisted) {
        if (sheetState.isVisible) {
            vm.updateAlbums(albumState.value.albumsWithBlacklisted)
        }
    }

    BackHandler(sheetState.isVisible && uiState.currentStep != SetupStep.TYPE_SELECTION) {
        vm.onAction(IgnoredSetupAction.NavigateBack)
    }

    if (sheetState.isVisible) {
        val density = LocalDensity.current
        val dragHandleAlpha by remember {
            derivedStateOf {
                val offset =
                    runCatching { sheetState.sheetState.requireOffset() }.getOrElse { Float.MAX_VALUE }
                val fadeThreshold = with(density) { 200.dp.toPx() }
                (offset / fadeThreshold).coerceIn(0f, 1f)
            }
        }

        ModalBottomSheet(
            sheetState = sheetState.sheetState,
            onDismissRequest = {
                scope.launch {
                    vm.reset()
                    sheetState.hide()
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            tonalElevation = 0.dp,
            dragHandle = { DragHandle(alpha = dragHandleAlpha) },
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
        ) {
            IgnoredSetupSheetContent(
                uiState = uiState,
                onAction = { action ->
                    when (action) {
                        is IgnoredSetupAction.Cancel -> {
                            scope.launch {
                                vm.reset()
                                sheetState.hide()
                            }
                        }

                        is IgnoredSetupAction.Confirm -> {
                            vm.onAction(action)
                            scope.launch {
                                sheetState.hide()
                            }
                        }

                        else -> vm.onAction(action)
                    }
                }
            )
        }
    }
}

@Composable
fun IgnoredSetupSheetContent(
    uiState: IgnoredSetupUiState,
    onAction: (IgnoredSetupAction) -> Unit
) {
    AnimatedContent(
        targetState = uiState.currentStep,
        transitionSpec = {
            if (targetState.ordinal > initialState.ordinal) {
                slideInHorizontally { it } + fadeIn() togetherWith
                        slideOutHorizontally { -it } + fadeOut()
            } else {
                slideInHorizontally { -it } + fadeIn() togetherWith
                        slideOutHorizontally { it } + fadeOut()
            }
        },
        label = "step_transition"
    ) { step ->
        when (step) {
            SetupStep.TYPE_SELECTION -> {
                TypeSelectionStepContent(
                    uiState = uiState,
                    onAction = onAction
                )
            }

            SetupStep.ALBUM_SELECTION -> {
                AlbumSelectionStepContent(
                    uiState = uiState,
                    onAction = onAction
                )
            }

            SetupStep.CONFIRMATION -> {
                ConfirmationStepContent(
                    uiState = uiState,
                    onAction = onAction
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypeSelectionStepContent(
    uiState: IgnoredSetupUiState,
    onAction: (IgnoredSetupAction) -> Unit
) {
    val albumsLabel = stringResource(R.string.setup_location_options_albums)
    val timelineLabel = stringResource(R.string.setup_location_options_timeline)
    val bothLabel = stringResource(R.string.setup_location_options_both)

    val locationOptions = remember(albumsLabel, timelineLabel, bothLabel) {
        mapOf(
            albumsLabel to IgnoredAlbum.ALBUMS_ONLY,
            timelineLabel to IgnoredAlbum.TIMELINE_ONLY,
            bothLabel to IgnoredAlbum.ALBUMS_AND_TIMELINE,
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.setup_location_title),
                        textAlign = TextAlign.Center
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .navigationBarsPadding(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { onAction(IgnoredSetupAction.Cancel) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
                Button(
                    onClick = { onAction(IgnoredSetupAction.NavigateNext) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.continue_string))
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                SectionHeader(
                    title = stringResource(R.string.setup_location_location_title),
                    subtitle = stringResource(R.string.setup_location_location_subtitle),
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            item {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    locationOptions.onEachIndexed { index, (option, optLocation) ->
                        SegmentedButton(
                            selected = uiState.location == optLocation,
                            onClick = { onAction(IgnoredSetupAction.SetLocation(optLocation)) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = locationOptions.size
                            )
                        ) {
                            Text(text = option, maxLines = 1)
                        }
                    }
                }
            }

            item {
                SectionHeader(
                    title = stringResource(R.string.setup_location_type_title),
                    subtitle = stringResource(R.string.setup_location_type_subtitle),
                    modifier = Modifier
                )
            }

            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TypeOptionCard(
                        icon = Icons.Outlined.PhotoAlbum,
                        title = stringResource(R.string.setup_location_types_single),
                        isSelected = uiState.type is IgnoredType.SINGLE,
                        onClick = {
                            onAction(
                                IgnoredSetupAction.SetType(
                                    IgnoredType.SINGLE(
                                        null
                                    )
                                )
                            )
                        }
                    )
                    TypeOptionCard(
                        icon = Icons.Outlined.FolderCopy,
                        title = stringResource(R.string.setup_location_types_multiple),
                        isSelected = uiState.type is IgnoredType.MULTIPLE,
                        onClick = {
                            onAction(
                                IgnoredSetupAction.SetType(
                                    IgnoredType.MULTIPLE(
                                        emptyList()
                                    )
                                )
                            )
                        }
                    )
                    TypeOptionCard(
                        icon = GalleryIcons.RegularExpression,
                        title = stringResource(R.string.setup_location_types_regex),
                        isSelected = uiState.type is IgnoredType.REGEX,
                        onClick = { onAction(IgnoredSetupAction.SetType(IgnoredType.REGEX(""))) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumSelectionStepContent(
    uiState: IgnoredSetupUiState,
    onAction: (IgnoredSetupAction) -> Unit
) {
    when (uiState.type) {
        is IgnoredType.SINGLE -> {
            SingleAlbumSelectionContent(
                uiState = uiState,
                onAction = onAction
            )
        }

        is IgnoredType.MULTIPLE -> {
            MultipleAlbumSelectionContent(
                uiState = uiState,
                onAction = onAction
            )
        }

        is IgnoredType.REGEX -> {
            RegexInputContent(
                uiState = uiState,
                onAction = onAction
            )
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun SingleAlbumSelectionContent(
    uiState: IgnoredSetupUiState,
    onAction: (IgnoredSetupAction) -> Unit
) {
    val selectedAlbum by remember(uiState.type) {
        derivedStateOf { (uiState.type as? IgnoredType.SINGLE)?.selectedAlbum }
    }
    val gridState = rememberLazyGridState()
    val columnSize by rememberAlbumGridSize()
    val columns = remember(columnSize) {
        Constants.albumCellsList[columnSize]
    }


    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.setup_type_selection_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = stringResource(R.string.setup_type_selection_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    NavigationBackButton(
                        forcedAction = { onAction(IgnoredSetupAction.NavigateBack) }
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .navigationBarsPadding(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { onAction(IgnoredSetupAction.NavigateBack) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.go_back))
                }
                Button(
                    onClick = { onAction(IgnoredSetupAction.NavigateNext) },
                    enabled = uiState.canProceed,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.continue_string))
                }
            }
        }
    ) { paddingValues ->
        LazyVerticalGrid(
            state = gridState,
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxWidth(),
            columns = columns,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            items(
                items = uiState.albums,
                key = { item -> item.toString() }
            ) { album ->
                val isSelected = selectedAlbum?.id == album.id
                val isDisabled = uiState.ignoredAlbums.any { it.id == album.id }

                SelectableAlbumItem(
                    album = album,
                    isSelected = isSelected,
                    isDisabled = isDisabled,
                    showCheckmark = true,
                    onClick = {
                        if (!isDisabled) {
                            onAction(IgnoredSetupAction.SelectAlbum(if (isSelected) null else album))
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun MultipleAlbumSelectionContent(
    uiState: IgnoredSetupUiState,
    onAction: (IgnoredSetupAction) -> Unit
) {
    val selectedAlbums by remember(uiState.selectedAlbums) {
        derivedStateOf { uiState.selectedAlbums }
    }
    val gridState = rememberLazyGridState()
    val columnSize by rememberAlbumGridSize()
    val columns = remember(columnSize) {
        Constants.albumCellsList[columnSize]
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.setup_type_multiple_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = stringResource(R.string.setup_type_multiple_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    NavigationBackButton(
                        forcedAction = { onAction(IgnoredSetupAction.NavigateBack) }
                    )
                },
                actions = {
                    if (selectedAlbums.isNotEmpty()) {
                        Text(
                            text = "${selectedAlbums.size} selected",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .background(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    RoundedCornerShape(16.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .navigationBarsPadding(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { onAction(IgnoredSetupAction.NavigateBack) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.go_back))
                }
                Button(
                    onClick = { onAction(IgnoredSetupAction.NavigateNext) },
                    enabled = uiState.canProceed,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.continue_string) + if (selectedAlbums.isNotEmpty()) " (${selectedAlbums.size})" else "")
                }
            }
        }
    ) { paddingValues ->
        LazyVerticalGrid(
            state = gridState,
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxWidth(),
            columns = columns,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(16.dp)
        ) {
            items(
                items = uiState.albums,
                key = { item -> item.toString() }
            ) { album ->
                val isSelected = selectedAlbums.any { it.id == album.id }
                val isDisabled = uiState.ignoredAlbums.any { it.id == album.id }

                SelectableAlbumItem(
                    album = album,
                    isSelected = isSelected,
                    isDisabled = isDisabled,
                    showCheckmark = true,
                    onClick = {
                        if (!isDisabled) {
                            onAction(IgnoredSetupAction.ToggleAlbum(album))
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RegexInputContent(
    uiState: IgnoredSetupUiState,
    onAction: (IgnoredSetupAction) -> Unit
) {
    val currentRegex = uiState.regex
    var localRegex by remember(currentRegex) { mutableStateOf(currentRegex) }
    var error by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    val invalidRegex = stringResource(R.string.setup_type_regex_error)
    val alreadyUsedRegex = stringResource(R.string.setup_type_regex_error_second)

    LaunchedEffect(localRegex) {
        val validRegex = try {
            localRegex.toRegex()
            true
        } catch (e: Exception) {
            false
        }
        error = !validRegex
        if (error) {
            errorMessage = invalidRegex
        } else {
            error = uiState.ignoredAlbums.any { it.wildcard == localRegex }
            if (error) errorMessage = alreadyUsedRegex
        }

        if (!error && localRegex.isNotEmpty()) {
            errorMessage = ""
            onAction(IgnoredSetupAction.SetRegex(localRegex))
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.setup_type_regex_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = stringResource(R.string.setup_type_regex_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    NavigationBackButton(
                        forcedAction = { onAction(IgnoredSetupAction.NavigateBack) }
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .navigationBarsPadding(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { onAction(IgnoredSetupAction.NavigateBack) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.go_back))
                }
                Button(
                    onClick = { onAction(IgnoredSetupAction.NavigateNext) },
                    enabled = localRegex.isNotEmpty() && !error,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.continue_string))
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = localRegex,
                    onValueChange = { localRegex = it },
                    label = { Text(stringResource(R.string.setup_type_regex_label)) },
                    placeholder = { Text(stringResource(R.string.setup_type_regex_label)) },
                    supportingText = if (error && localRegex.isNotEmpty()) {
                        {
                            Text(
                                text = errorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    } else null,
                    isError = error && localRegex.isNotEmpty(),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true,
                )
            }

            item {
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.large
                        )
                        .padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    text = stringResource(R.string.setup_type_regex_summary)
                )
            }

            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.setup_type_regex_example_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    RegexExample(
                        description = stringResource(R.string.setup_type_regex_example_first_title),
                        pattern = stringResource(R.string.setup_type_regex_first_subtitle)
                    )

                    RegexExample(
                        description = stringResource(R.string.setup_type_regex_example_second_title),
                        pattern = stringResource(R.string.setup_type_regex_example_second_subtitle)
                    )

                    RegexExample(
                        description = stringResource(R.string.setup_type_regex_example_third_title),
                        pattern = stringResource(R.string.setup_type_regex_example_third_subtitle)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfirmationStepContent(
    uiState: IgnoredSetupUiState,
    onAction: (IgnoredSetupAction) -> Unit
) {
    val albumsText = stringResource(R.string.albums)
    val timelineText = stringResource(R.string.timeline)
    val albumsAndTimelineText = stringResource(R.string.albums_and_timeline)

    val locationText by remember(
        uiState.location,
        albumsText,
        timelineText,
        albumsAndTimelineText
    ) {
        derivedStateOf {
            when (uiState.location) {
                IgnoredAlbum.ALBUMS_ONLY -> albumsText
                IgnoredAlbum.TIMELINE_ONLY -> timelineText
                IgnoredAlbum.ALBUMS_AND_TIMELINE -> albumsAndTimelineText
                else -> "Unknown"
            }
        }
    }

    val typeText by remember(uiState.type) {
        derivedStateOf {
            when (val type = uiState.type) {
                is IgnoredType.SINGLE -> type.selectedAlbum?.label ?: "Single Album"
                is IgnoredType.MULTIPLE -> "${type.selectedAlbums.size} albums"
                is IgnoredType.REGEX -> type.regex
            }
        }
    }

    val typeIcon by remember(uiState.type) {
        derivedStateOf {
            when (uiState.type) {
                is IgnoredType.SINGLE -> Icons.Outlined.PhotoAlbum
                is IgnoredType.MULTIPLE -> Icons.Outlined.FolderCopy
                is IgnoredType.REGEX -> GalleryIcons.RegularExpression
            }
        }
    }

    val matchedText by remember(uiState.matchedAlbums) {
        derivedStateOf {
            uiState.matchedAlbums.joinToString("\n") { it.label }.ifEmpty { "No matches" }
        }
    }
    val extraMatchedText by remember(uiState.matchedAlbums) {
        derivedStateOf {
            if (uiState.matchedAlbums.size > 10) "+${uiState.matchedAlbums.size - 10} more" else null
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.setup_confirmation_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = stringResource(R.string.setup_confirmation_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    NavigationBackButton(
                        forcedAction = { onAction(IgnoredSetupAction.NavigateBack) }
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .navigationBarsPadding(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { onAction(IgnoredSetupAction.NavigateBack) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.go_back))
                }
                Button(
                    onClick = { onAction(IgnoredSetupAction.Confirm) },
                    enabled = uiState.matchedAlbums.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.apply))
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                ConfirmationCard(
                    title = stringResource(R.string.setup_confirmation_where),
                    value = locationText,
                    icon = Icons.Outlined.FilterNone
                )
            }

            item {
                ConfirmationCard(
                    title = stringResource(R.string.setup_confirmation_who),
                    value = typeText,
                    icon = typeIcon
                )
            }

            item {
                ConfirmationCard(
                    title = stringResource(R.string.setup_confirmation_matched),
                    value = matchedText,
                    icon = Icons.Outlined.Checklist,
                    extra = extraMatchedText
                )
            }
        }
    }
}

// ========== Previews ==========

private val mockAlbums = listOf(
    Album(
        id = 1,
        label = "Camera",
        uri = Uri.EMPTY,
        pathToThumbnail = "/DCIM/Camera/photo.jpg",
        relativePath = "DCIM/Camera",
        timestamp = 0,
        count = 42
    ),
    Album(
        id = 2,
        label = "Screenshots",
        uri = Uri.EMPTY,
        pathToThumbnail = "",
        relativePath = "Pictures/Screenshots",
        timestamp = 0,
        count = 156
    ),
    Album(
        id = 3,
        label = "Downloads",
        uri = Uri.EMPTY,
        pathToThumbnail = "",
        relativePath = "Download",
        timestamp = 0,
        count = 23
    ),
    Album(
        id = 4,
        label = "WhatsApp Images",
        uri = Uri.EMPTY,
        pathToThumbnail = "",
        relativePath = "Pictures/WhatsApp",
        timestamp = 0,
        count = 500
    ),
    Album(
        id = 5,
        label = "Telegram",
        uri = Uri.EMPTY,
        pathToThumbnail = "",
        relativePath = "Pictures/Telegram",
        timestamp = 0,
        count = 89
    )
)

@Preview(showBackground = true, name = "Type Selection - Single")
@Composable
private fun TypeSelectionStepSinglePreview() {
    PreviewHost {
        IgnoredSetupSheetContent(
            uiState = IgnoredSetupUiState(
                currentStep = SetupStep.TYPE_SELECTION,
                location = IgnoredAlbum.ALBUMS_ONLY,
                type = IgnoredType.SINGLE(null)
            ),
            onAction = {}
        )
    }
}

@Preview(showBackground = true, name = "Type Selection - Multiple")
@Composable
private fun TypeSelectionStepMultiplePreview() {
    PreviewHost {
        IgnoredSetupSheetContent(
            uiState = IgnoredSetupUiState(
                currentStep = SetupStep.TYPE_SELECTION,
                location = IgnoredAlbum.TIMELINE_ONLY,
                type = IgnoredType.MULTIPLE(emptyList())
            ),
            onAction = {}
        )
    }
}

@Preview(showBackground = true, name = "Type Selection - Regex")
@Composable
private fun TypeSelectionStepRegexPreview() {
    PreviewHost {
        IgnoredSetupSheetContent(
            uiState = IgnoredSetupUiState(
                currentStep = SetupStep.TYPE_SELECTION,
                location = IgnoredAlbum.ALBUMS_AND_TIMELINE,
                type = IgnoredType.REGEX("")
            ),
            onAction = {}
        )
    }
}

@Preview(showBackground = true, name = "Single Album Selection")
@Composable
private fun SingleAlbumSelectionPreview() {
    PreviewHost {
        IgnoredSetupSheetContent(
            uiState = IgnoredSetupUiState(
                currentStep = SetupStep.ALBUM_SELECTION,
                type = IgnoredType.SINGLE(mockAlbums[0]),
                albums = mockAlbums,
                canProceed = true
            ),
            onAction = {}
        )
    }
}

@Preview(showBackground = true, name = "Multiple Album Selection")
@Composable
private fun MultipleAlbumSelectionPreview() {
    PreviewHost {
        IgnoredSetupSheetContent(
            uiState = IgnoredSetupUiState(
                currentStep = SetupStep.ALBUM_SELECTION,
                type = IgnoredType.MULTIPLE(mockAlbums.take(3)),
                albums = mockAlbums,
                canProceed = true
            ),
            onAction = {}
        )
    }
}

@Preview(showBackground = true, name = "Regex Input - Empty")
@Composable
private fun RegexInputEmptyPreview() {
    PreviewHost {
        IgnoredSetupSheetContent(
            uiState = IgnoredSetupUiState(
                currentStep = SetupStep.ALBUM_SELECTION,
                type = IgnoredType.REGEX(""),
                albums = mockAlbums,
                canProceed = false
            ),
            onAction = {}
        )
    }
}

@Preview(showBackground = true, name = "Regex Input - With Pattern")
@Composable
private fun RegexInputWithPatternPreview() {
    PreviewHost {
        IgnoredSetupSheetContent(
            uiState = IgnoredSetupUiState(
                currentStep = SetupStep.ALBUM_SELECTION,
                type = IgnoredType.REGEX("^Screenshot.*"),
                albums = mockAlbums,
                canProceed = true
            ),
            onAction = {}
        )
    }
}

@Preview(showBackground = true, name = "Confirmation - Single Album")
@Composable
private fun ConfirmationStepSinglePreview() {
    PreviewHost {
        IgnoredSetupSheetContent(
            uiState = IgnoredSetupUiState(
                currentStep = SetupStep.CONFIRMATION,
                location = IgnoredAlbum.ALBUMS_ONLY,
                type = IgnoredType.SINGLE(mockAlbums[0]),
                matchedAlbums = listOf(mockAlbums[0])
            ),
            onAction = {}
        )
    }
}

@Preview(showBackground = true, name = "Confirmation - Multiple Albums")
@Composable
private fun ConfirmationStepMultiplePreview() {
    PreviewHost {
        IgnoredSetupSheetContent(
            uiState = IgnoredSetupUiState(
                currentStep = SetupStep.CONFIRMATION,
                location = IgnoredAlbum.TIMELINE_ONLY,
                type = IgnoredType.MULTIPLE(mockAlbums.take(3)),
                matchedAlbums = mockAlbums.take(3)
            ),
            onAction = {}
        )
    }
}

@Preview(showBackground = true, name = "Confirmation - Regex")
@Composable
private fun ConfirmationStepRegexPreview() {
    PreviewHost {
        val matchedAlbums = listOf(
            Album(
                id = 1,
                label = "Screenshots",
                uri = Uri.EMPTY,
                pathToThumbnail = "",
                relativePath = "Pictures/Screenshots",
                timestamp = 0,
                count = 100
            ),
            Album(
                id = 2,
                label = "Screenshot_2024",
                uri = Uri.EMPTY,
                pathToThumbnail = "",
                relativePath = "Pictures/Screenshot_2024",
                timestamp = 0,
                count = 50
            ),
            Album(
                id = 3,
                label = "Screenshots_old",
                uri = Uri.EMPTY,
                pathToThumbnail = "",
                relativePath = "Pictures/Screenshots_old",
                timestamp = 0,
                count = 30
            )
        )
        IgnoredSetupSheetContent(
            uiState = IgnoredSetupUiState(
                currentStep = SetupStep.CONFIRMATION,
                location = IgnoredAlbum.ALBUMS_AND_TIMELINE,
                type = IgnoredType.REGEX("^Screenshot.*"),
                matchedAlbums = matchedAlbums
            ),
            onAction = {}
        )
    }
}

@Preview(showBackground = true, name = "Confirmation - No Matches")
@Composable
private fun ConfirmationStepNoMatchesPreview() {
    PreviewHost {
        IgnoredSetupSheetContent(
            uiState = IgnoredSetupUiState(
                currentStep = SetupStep.CONFIRMATION,
                location = IgnoredAlbum.ALBUMS_ONLY,
                type = IgnoredType.REGEX("^NonExistent.*"),
                matchedAlbums = emptyList()
            ),
            onAction = {}
        )
    }
}

@Preview(showBackground = true, name = "Type Selection - Landscape", widthDp = 800, heightDp = 400)
@Composable
private fun TypeSelectionLandscapePreview() {
    PreviewHost {
        IgnoredSetupSheetContent(
            uiState = IgnoredSetupUiState(
                currentStep = SetupStep.TYPE_SELECTION,
                location = IgnoredAlbum.ALBUMS_ONLY,
                type = IgnoredType.SINGLE(null)
            ),
            onAction = {}
        )
    }
}

@Preview(showBackground = true, name = "Album Selection - Tablet", widthDp = 1024, heightDp = 768)
@Composable
private fun AlbumSelectionTabletPreview() {
    PreviewHost {
        IgnoredSetupSheetContent(
            uiState = IgnoredSetupUiState(
                currentStep = SetupStep.ALBUM_SELECTION,
                type = IgnoredType.MULTIPLE(mockAlbums.take(2)),
                albums = mockAlbums,
                albumGridSize = 4,
                canProceed = true
            ),
            onAction = {}
        )
    }
}
