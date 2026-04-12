package com.dot.gallery.feature_node.presentation.ignored.setup

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.core.presentation.components.DragHandle
import com.dot.gallery.feature_node.domain.model.Album
import com.dot.gallery.feature_node.domain.model.AlbumState
import com.dot.gallery.feature_node.domain.model.IgnoredAlbum
import com.dot.gallery.feature_node.presentation.ignored.setup.components.EditAlbumsStep
import com.dot.gallery.feature_node.presentation.ignored.setup.components.EditConfirmationStep
import com.dot.gallery.feature_node.presentation.ignored.setup.components.EditLocationStep
import com.dot.gallery.feature_node.presentation.ignored.setup.components.OptionsMenuStep
import com.dot.gallery.feature_node.presentation.util.AppBottomSheetState
import com.dot.gallery.feature_node.presentation.util.PreviewHost
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IgnoredOptionsSheet(
    sheetState: AppBottomSheetState,
    ignoredAlbum: IgnoredAlbum?,
    albumState: State<AlbumState>,
    onUpdate: (IgnoredAlbum) -> Unit,
    onDelete: (IgnoredAlbum) -> Unit
) {
    val vm = hiltViewModel<IgnoredOptionsViewModel>()
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    LaunchedEffect(sheetState.isVisible, ignoredAlbum) {
        if (sheetState.isVisible && ignoredAlbum != null) {
            vm.initialize(
                ignoredAlbum = ignoredAlbum,
                albums = albumState.value.albumsWithBlacklisted,
                onUpdate = onUpdate,
                onDelete = onDelete
            )
        } else if (!sheetState.isVisible) {
            vm.reset()
        }
    }

    LaunchedEffect(sheetState.isVisible, albumState.value.albumsWithBlacklisted) {
        if (sheetState.isVisible) {
            vm.updateAlbums(albumState.value.albumsWithBlacklisted)
        }
    }

    BackHandler(sheetState.isVisible && uiState.currentStep != OptionsStep.OPTIONS) {
        vm.onAction(IgnoredOptionsAction.NavigateBack)
    }

    if (sheetState.isVisible && ignoredAlbum != null) {
        val density = LocalDensity.current
        val dragHandleAlpha by remember {
            derivedStateOf {
                val offset = runCatching { sheetState.sheetState.requireOffset() }.getOrElse { Float.MAX_VALUE }
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
            IgnoredOptionsSheetContent(
                uiState = uiState,
                albumState = albumState,
                onAction = { action ->
                    when (action) {
                        is IgnoredOptionsAction.Cancel -> {
                            scope.launch {
                                vm.reset()
                                sheetState.hide()
                            }
                        }
                        is IgnoredOptionsAction.Confirm -> {
                            vm.onAction(action)
                            scope.launch {
                                vm.reset()
                                sheetState.hide()
                            }
                        }
                        is IgnoredOptionsAction.Delete -> {
                            vm.onAction(action)
                            scope.launch {
                                vm.reset()
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
fun IgnoredOptionsSheetContent(
    uiState: IgnoredOptionsUiState,
    albumState: State<AlbumState>,
    onAction: (IgnoredOptionsAction) -> Unit
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
        label = "options_step_transition"
    ) { step ->
        when (step) {
            OptionsStep.OPTIONS -> {
                OptionsMenuStep(
                    ignoredAlbum = uiState.ignoredAlbum ?: return@AnimatedContent,
                    albumState = albumState,
                    isWildcard = uiState.isWildcard,
                    isMultiple = uiState.isMultiple,
                    onEdit = { onAction(IgnoredOptionsAction.Edit) },
                    onDelete = { onAction(IgnoredOptionsAction.Delete) },
                    onCancel = { onAction(IgnoredOptionsAction.Cancel) }
                )
            }

            OptionsStep.EDIT_LOCATION -> {
                EditLocationStep(
                    location = uiState.editLocation,
                    onLocationChanged = { onAction(IgnoredOptionsAction.SetLocation(it)) },
                    onBack = { onAction(IgnoredOptionsAction.NavigateBack) },
                    onNext = { onAction(IgnoredOptionsAction.NavigateNext) }
                )
            }

            OptionsStep.EDIT_ALBUMS -> {
                EditAlbumsStep(
                    isWildcard = uiState.isWildcard,
                    isMultiple = uiState.isMultiple,
                    regex = uiState.editRegex,
                    selectedAlbums = uiState.editSelectedAlbums,
                    albumState = albumState,
                    onRegexChanged = { onAction(IgnoredOptionsAction.SetRegex(it)) },
                    onAlbumToggled = { album ->
                        if (uiState.isMultiple) {
                            onAction(IgnoredOptionsAction.ToggleAlbum(album))
                        } else {
                            onAction(IgnoredOptionsAction.SelectAlbum(album))
                        }
                    },
                    onBack = { onAction(IgnoredOptionsAction.NavigateBack) },
                    onNext = { onAction(IgnoredOptionsAction.NavigateNext) }
                )
            }

            OptionsStep.EDIT_CONFIRMATION -> {
                EditConfirmationStep(
                    location = uiState.editLocation,
                    isWildcard = uiState.isWildcard,
                    isMultiple = uiState.isMultiple,
                    regex = uiState.editRegex,
                    matchedAlbums = uiState.editMatchedAlbums,
                    onBack = { onAction(IgnoredOptionsAction.NavigateBack) },
                    onConfirm = { onAction(IgnoredOptionsAction.Confirm) }
                )
            }
        }
    }
}

// ========== Previews ==========

private val mockAlbums = listOf(
    Album(id = 1, label = "Camera", uri = Uri.EMPTY, pathToThumbnail = "", relativePath = "DCIM/Camera", timestamp = 0, count = 42),
    Album(id = 2, label = "Screenshots", uri = Uri.EMPTY, pathToThumbnail = "", relativePath = "Pictures/Screenshots", timestamp = 0, count = 156),
    Album(id = 3, label = "Downloads", uri = Uri.EMPTY, pathToThumbnail = "", relativePath = "Download", timestamp = 0, count = 23)
)

@Preview(showBackground = true, name = "Options Menu - Single Album")
@Composable
private fun OptionsMenuSingleAlbumPreview() {
    PreviewHost {
        val mockAlbumState = remember { mutableStateOf(AlbumState(albums = mockAlbums)) }
        IgnoredOptionsSheetContent(
            uiState = IgnoredOptionsUiState(
                currentStep = OptionsStep.OPTIONS,
                ignoredAlbum = IgnoredAlbum(
                    id = 1,
                    label = "Single #1",
                    location = IgnoredAlbum.ALBUMS_ONLY,
                    matchedAlbums = listOf("Camera")
                ),
                albums = mockAlbums
            ),
            albumState = mockAlbumState,
            onAction = {}
        )
    }
}

@Preview(showBackground = true, name = "Options Menu - Multiple Albums")
@Composable
private fun OptionsMenuMultipleAlbumsPreview() {
    PreviewHost {
        val mockAlbumState = remember { mutableStateOf(AlbumState(albums = mockAlbums)) }
        IgnoredOptionsSheetContent(
            uiState = IgnoredOptionsUiState(
                currentStep = OptionsStep.OPTIONS,
                ignoredAlbum = IgnoredAlbum(
                    id = 1,
                    label = "Multiple #1",
                    albumIds = listOf(1L, 2L, 3L),
                    location = IgnoredAlbum.TIMELINE_ONLY,
                    matchedAlbums = listOf("Camera", "Screenshots", "Downloads")
                ),
                albums = mockAlbums
            ),
            albumState = mockAlbumState,
            onAction = {}
        )
    }
}

@Preview(showBackground = true, name = "Options Menu - Regex")
@Composable
private fun OptionsMenuRegexPreview() {
    PreviewHost {
        val mockAlbumState = remember { mutableStateOf(AlbumState(albums = mockAlbums)) }
        IgnoredOptionsSheetContent(
            uiState = IgnoredOptionsUiState(
                currentStep = OptionsStep.OPTIONS,
                ignoredAlbum = IgnoredAlbum(
                    id = 1,
                    label = "Regex #1",
                    location = IgnoredAlbum.ALBUMS_AND_TIMELINE,
                    wildcard = "^Screenshot.*",
                    matchedAlbums = listOf("Screenshots", "Screenshots_old", "Screenshot_2024")
                ),
                albums = mockAlbums
            ),
            albumState = mockAlbumState,
            onAction = {}
        )
    }
}

@Preview(showBackground = true, name = "Edit Location - Albums Only")
@Composable
private fun EditLocationAlbumsOnlyPreview() {
    PreviewHost {
        val mockAlbumState = remember { mutableStateOf(AlbumState(albums = mockAlbums)) }
        IgnoredOptionsSheetContent(
            uiState = IgnoredOptionsUiState(
                currentStep = OptionsStep.EDIT_LOCATION,
                ignoredAlbum = IgnoredAlbum(id = 1, label = "Single #1"),
                editLocation = IgnoredAlbum.ALBUMS_ONLY,
                albums = mockAlbums
            ),
            albumState = mockAlbumState,
            onAction = {}
        )
    }
}

@Preview(showBackground = true, name = "Edit Location - Timeline Only")
@Composable
private fun EditLocationTimelineOnlyPreview() {
    PreviewHost {
        val mockAlbumState = remember { mutableStateOf(AlbumState(albums = mockAlbums)) }
        IgnoredOptionsSheetContent(
            uiState = IgnoredOptionsUiState(
                currentStep = OptionsStep.EDIT_LOCATION,
                ignoredAlbum = IgnoredAlbum(id = 1, label = "Single #1"),
                editLocation = IgnoredAlbum.TIMELINE_ONLY,
                albums = mockAlbums
            ),
            albumState = mockAlbumState,
            onAction = {}
        )
    }
}

@Preview(showBackground = true, name = "Edit Location - Both")
@Composable
private fun EditLocationBothPreview() {
    PreviewHost {
        val mockAlbumState = remember { mutableStateOf(AlbumState(albums = mockAlbums)) }
        IgnoredOptionsSheetContent(
            uiState = IgnoredOptionsUiState(
                currentStep = OptionsStep.EDIT_LOCATION,
                ignoredAlbum = IgnoredAlbum(id = 1, label = "Single #1"),
                editLocation = IgnoredAlbum.ALBUMS_AND_TIMELINE,
                albums = mockAlbums
            ),
            albumState = mockAlbumState,
            onAction = {}
        )
    }
}

@Preview(showBackground = true, name = "Edit Albums - Regex Empty")
@Composable
private fun EditAlbumsRegexEmptyPreview() {
    PreviewHost {
        val mockAlbumState = remember { mutableStateOf(AlbumState(albums = mockAlbums)) }
        IgnoredOptionsSheetContent(
            uiState = IgnoredOptionsUiState(
                currentStep = OptionsStep.EDIT_ALBUMS,
                ignoredAlbum = IgnoredAlbum(
                    id = 1,
                    label = "Regex #1",
                    wildcard = ""
                ),
                editRegex = "",
                albums = mockAlbums
            ),
            albumState = mockAlbumState,
            onAction = {}
        )
    }
}

@Preview(showBackground = true, name = "Edit Albums - Regex With Pattern")
@Composable
private fun EditAlbumsRegexWithPatternPreview() {
    PreviewHost {
        val mockAlbumState = remember { mutableStateOf(AlbumState(albums = mockAlbums)) }
        IgnoredOptionsSheetContent(
            uiState = IgnoredOptionsUiState(
                currentStep = OptionsStep.EDIT_ALBUMS,
                ignoredAlbum = IgnoredAlbum(
                    id = 1,
                    label = "Regex #1",
                    wildcard = "^Screenshot.*"
                ),
                editRegex = "^Screenshot.*",
                albums = mockAlbums
            ),
            albumState = mockAlbumState,
            onAction = {}
        )
    }
}

@Preview(showBackground = true, name = "Edit Albums - Multiple Selection")
@Composable
private fun EditAlbumsMultiplePreview() {
    PreviewHost {
        val mockAlbumState = remember { mutableStateOf(AlbumState(albums = mockAlbums)) }
        IgnoredOptionsSheetContent(
            uiState = IgnoredOptionsUiState(
                currentStep = OptionsStep.EDIT_ALBUMS,
                ignoredAlbum = IgnoredAlbum(
                    id = 1,
                    label = "Multiple #1",
                    albumIds = listOf(1L, 2L)
                ),
                editSelectedAlbums = mockAlbums.take(2),
                albums = mockAlbums
            ),
            albumState = mockAlbumState,
            onAction = {}
        )
    }
}

@Preview(showBackground = true, name = "Edit Confirmation - Single")
@Composable
private fun EditConfirmationSinglePreview() {
    PreviewHost {
        val mockAlbumState = remember { mutableStateOf(AlbumState(albums = mockAlbums)) }
        IgnoredOptionsSheetContent(
            uiState = IgnoredOptionsUiState(
                currentStep = OptionsStep.EDIT_CONFIRMATION,
                ignoredAlbum = IgnoredAlbum(id = 1, label = "Single #1"),
                editLocation = IgnoredAlbum.ALBUMS_ONLY,
                editMatchedAlbums = listOf(mockAlbums[0]),
                albums = mockAlbums
            ),
            albumState = mockAlbumState,
            onAction = {}
        )
    }
}

@Preview(showBackground = true, name = "Edit Confirmation - Multiple")
@Composable
private fun EditConfirmationMultiplePreview() {
    PreviewHost {
        val mockAlbumState = remember { mutableStateOf(AlbumState(albums = mockAlbums)) }
        IgnoredOptionsSheetContent(
            uiState = IgnoredOptionsUiState(
                currentStep = OptionsStep.EDIT_CONFIRMATION,
                ignoredAlbum = IgnoredAlbum(
                    id = 1,
                    label = "Multiple #1",
                    albumIds = listOf(1L, 2L, 3L)
                ),
                editLocation = IgnoredAlbum.TIMELINE_ONLY,
                editMatchedAlbums = mockAlbums,
                albums = mockAlbums
            ),
            albumState = mockAlbumState,
            onAction = {}
        )
    }
}

@Preview(showBackground = true, name = "Edit Confirmation - Regex")
@Composable
private fun EditConfirmationRegexPreview() {
    PreviewHost {
        val mockAlbumState = remember { mutableStateOf(AlbumState(albums = mockAlbums)) }
        val matchedAlbums = listOf(
            Album(id = 1, label = "Screenshots", uri = Uri.EMPTY, pathToThumbnail = "", relativePath = "Pictures/Screenshots", timestamp = 0, count = 100),
            Album(id = 2, label = "Screenshot_2024", uri = Uri.EMPTY, pathToThumbnail = "", relativePath = "Pictures/Screenshot_2024", timestamp = 0, count = 50),
            Album(id = 3, label = "Screenshots_old", uri = Uri.EMPTY, pathToThumbnail = "", relativePath = "Pictures/Screenshots_old", timestamp = 0, count = 30)
        )
        IgnoredOptionsSheetContent(
            uiState = IgnoredOptionsUiState(
                currentStep = OptionsStep.EDIT_CONFIRMATION,
                ignoredAlbum = IgnoredAlbum(
                    id = 1,
                    label = "Regex #1",
                    wildcard = "^Screenshot.*"
                ),
                editLocation = IgnoredAlbum.ALBUMS_AND_TIMELINE,
                editRegex = "^Screenshot.*",
                editMatchedAlbums = matchedAlbums,
                albums = mockAlbums
            ),
            albumState = mockAlbumState,
            onAction = {}
        )
    }
}
