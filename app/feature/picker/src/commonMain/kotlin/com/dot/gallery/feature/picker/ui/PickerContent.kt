/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.picker.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.EnhancedEncryption
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.core.LocalMediaSelector
import com.dot.gallery.core.image.thumbnail.LocalThumbnailMotion
import com.dot.gallery.core.image.thumbnail.rememberThumbnailMotionState
import com.dot.gallery.core.presentation.components.DragHandle
import com.dot.gallery.core.presentation.components.GalleryPageTopBar
import com.dot.gallery.core.presentation.components.GalleryPageTopBarAction
import com.dot.gallery.core.presentation.util.LocalHazeState
import com.dot.gallery.core.presentation.util.selectedMedia
import com.dot.gallery.core.ui.settings.rememberPreference
import com.dot.gallery.core.ui.util.AppBottomSheetState
import com.dot.gallery.core.ui.util.rememberAppBottomSheetState
import com.dot.gallery.core.ui.util.rememberPlatformToast
import com.dot.gallery.domain.model.Album
import com.dot.gallery.domain.model.Media
import com.dot.gallery.domain.model.MediaState
import com.dot.gallery.domain.model.Vault
import com.dot.gallery.domain.settings.SettingsKeys
import com.dot.gallery.domain.util.getUri
import com.dot.gallery.feature.picker.ui.components.PickerDock
import com.dot.gallery.feature.picker.ui.components.PickerInfoBanner
import com.dot.gallery.feature.picker.ui.components.PickerSectionTitle
import com.dot.gallery.feature.picker.ui.components.PickerSourceTile
import com.dot.gallery.feature.picker.ui.components.PickerVaultTile
import com.dot.gallery.ui.theme.Dimens
import com.dot.gallery.ui.theme.GalleryChrome
import com.dot.gallery.ui.theme.Spacing
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.feature.picker.generated.resources.Res
import com.dot.gallery.feature.picker.generated.resources.action_add
import com.dot.gallery.feature.picker.generated.resources.action_add_count
import com.dot.gallery.feature.picker.generated.resources.action_ok
import com.dot.gallery.feature.picker.generated.resources.add_media
import com.dot.gallery.feature.picker.generated.resources.albums
import com.dot.gallery.feature.picker.generated.resources.close
import com.dot.gallery.feature.picker.generated.resources.cloud
import com.dot.gallery.feature.picker.generated.resources.locked
import com.dot.gallery.feature.picker.generated.resources.locked_album_security_error
import com.dot.gallery.feature.picker.generated.resources.photos
import com.dot.gallery.feature.picker.generated.resources.picker_album_items
import com.dot.gallery.feature.picker.generated.resources.picker_cloud_notice
import com.dot.gallery.feature.picker.generated.resources.picker_empty_source
import com.dot.gallery.feature.picker.generated.resources.picker_empty_summary
import com.dot.gallery.feature.picker.generated.resources.picker_empty_title
import com.dot.gallery.feature.picker.generated.resources.picker_locked_title
import com.dot.gallery.feature.picker.generated.resources.picker_prepare_failed
import com.dot.gallery.feature.picker.generated.resources.picker_preparing
import com.dot.gallery.feature.picker.generated.resources.picker_preparing_generic
import com.dot.gallery.feature.picker.generated.resources.picker_preparing_title
import com.dot.gallery.feature.picker.generated.resources.picker_selected_count
import com.dot.gallery.feature.picker.generated.resources.picker_vault_count
import com.dot.gallery.feature.picker.generated.resources.picker_vault_intro
import com.dot.gallery.feature.picker.generated.resources.picker_vault_intro_title
import com.dot.gallery.feature.picker.generated.resources.picker_vault_notice
import com.dot.gallery.feature.picker.generated.resources.preview
import com.dot.gallery.feature.picker.generated.resources.security_private_folder
import com.dot.gallery.feature.picker.generated.resources.vault
import androidx.compose.foundation.lazy.grid.LazyGridState
import com.dot.gallery.core.presentation.components.galleryDetailTopBarHeight
import com.dot.gallery.core.presentation.components.rememberScrolledUnderTopBar
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.LocalHazePerformanceMode


/** Identity of a destination's grid: album and vault pages keep theirs across data updates. */
private fun PickerNavState.gridKey(): Any = when (this) {
    is PickerNavState.AlbumDetail -> "album/${album.id}"
    is PickerNavState.VaultDetail -> "vault/${vault.uuid}"
    else -> this
}

private sealed class PickerNavState {
    data class Tabs(val tabIndex: Int) : PickerNavState()
    data class AlbumDetail(val album: Album) : PickerNavState()
    data object PrivateFolder : PickerNavState()
    data object VaultChooser : PickerNavState()
    data class VaultDetail(val vault: Vault) : PickerNavState()
    data object CloudMedia : PickerNavState()
}

private const val TAB_PHOTOS = 0
private const val TAB_ALBUMS = 1

/** Dock height plus its 16 dp gap above the navigation bar, kept clear at the foot of grids. */
private val DockClearance = GalleryChrome.ToolbarButtonSize + Spacing.Small * 2 + Spacing.Medium * 2

/** Widest the floating dock grows on tablets and unfolded phones. */
private val DockMaxWidth = 400.dp

/**
 * The whole media-picker body — top bar, timeline/album/private-folder/cloud/
 * vault drill-ins, the floating source dock with the Add action, and the
 * preview overlay host. State in via [state], events out via [actions]; the app
 * shell supplies the two platform slots:
 * - [albumImage]: the album tile (`AlbumImage` lives in `:feature:albums`).
 * - [previewContent]: the standalone media viewer (`MediaViewScreenRoute`
 *   app-side) that previews the current selection. It receives the grid's
 *   shared-transition scopes so cells morph into the viewer, plus [onDismiss]
 *   to leave preview mode.
 *
 * In a short window (phone landscape) the dock moves into the top bar row so
 * the grid keeps its height.
 */
@Suppress("LABEL_NAME_CLASH")
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalSharedTransitionApi::class,
    ExperimentalComposeUiApi::class
)
@Composable
fun PickerContent(
    state: PickerState,
    actions: PickerActions,
    albumImage: @Composable (album: Album, onClick: (Album) -> Unit) -> Unit,
    previewContent: @Composable (
        sharedTransitionScope: SharedTransitionScope,
        animatedVisibilityScope: AnimatedVisibilityScope,
        mediaState: State<MediaState<Media>>,
        mediaId: Long,
        onDismiss: () -> Unit,
    ) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val selector = LocalMediaSelector.current
    val selectedMedia = selector.selectedMedia.collectAsStateWithLifecycle()
    val toast = rememberPlatformToast()
    val pickerPrepareFailedText = stringResource(Res.string.picker_prepare_failed)
    val addMediaContentDescription = stringResource(Res.string.add_media)

    var selectedTabIndex by remember { mutableIntStateOf(TAB_PHOTOS) }
    var selectedAlbum by remember { mutableStateOf<Album?>(null) }
    var showPreview by remember { mutableStateOf(false) }
    val securitySheetState = rememberAppBottomSheetState()

    val onAlbumClickWithLock: (Album) -> Unit = { album ->
        if (album.isLocked) {
            actions.onLockedAlbum(
                album,
                { selectedAlbum = album },
                { scope.launch { securitySheetState.show() } },
            )
        } else {
            selectedAlbum = album
        }
    }

    PickerSecurityInfoSheet(sheetState = securitySheetState)

    var showPrivateFolder by remember { mutableStateOf(false) }
    var showCloud by remember { mutableStateOf(false) }
    var showVaultChooser by remember { mutableStateOf(false) }
    var selectedPickerVault by remember { mutableStateOf<Vault?>(null) }

    // Progress of decrypting/downloading selected vault/cloud items on "Add"
    var materializing by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    val privateFolderUri by rememberPreference(SettingsKeys.privateFolderUri)

    // After the app-side gate is cleared, reveal the vaults: a single vault
    // authenticates directly, multiple show the chooser.
    fun revealVaults() {
        val vaults = state.vaultState.value.vaults
        when {
            vaults.isEmpty() -> {}
            vaults.size == 1 -> {
                val vault = vaults.first()
                actions.onVault(vault) {
                    showVaultChooser = false
                    selectedPickerVault = vault
                }
            }
            else -> showVaultChooser = true
        }
    }

    // Leaves every drill-in and lands on [tab]; the dock's source switch.
    fun openTab(tab: Int) {
        selectedPickerVault = null
        showVaultChooser = false
        showCloud = false
        showPrivateFolder = false
        selectedAlbum = null
        selectedTabIndex = tab
    }

    val navState: PickerNavState = when {
        selectedPickerVault != null -> PickerNavState.VaultDetail(selectedPickerVault!!)
        showVaultChooser -> PickerNavState.VaultChooser
        showCloud -> PickerNavState.CloudMedia
        showPrivateFolder -> PickerNavState.PrivateFolder
        selectedAlbum != null -> PickerNavState.AlbumDetail(selectedAlbum!!)
        else -> PickerNavState.Tabs(selectedTabIndex)
    }

    // Resolving the flow also points the app-side ViewModel's lazy at this album —
    // the original fed it through `mediaVM.albumId` from a LaunchedEffect.
    val albumId = selectedAlbum?.id ?: -1L
    val mediaFlow = remember(albumId) { state.mediaStateFor(albumId) }

    // Observable per-vault media flow, rebuilt (and re-subscribed) whenever the open vault changes.
    val vaultMediaFlow = remember(selectedPickerVault) {
        state.vaultMediaStateFor(selectedPickerVault)
    }
    val currentVaultMediaState by vaultMediaFlow.collectAsStateWithLifecycle()

    // The flow above only holds the currently-open vault. Selection is shared across all sources,
    // so accumulate media from every vault visited this session (keyed by its unique encrypted-file
    // URI) — otherwise picks from a previously-opened vault would be dropped on Add. Cross-vault
    // decryption is safe: KeychainHolder resolves the vault + key from each file's own path.
    val visitedVaultMedia = remember { mutableStateMapOf<String, Media>() }
    LaunchedEffect(currentVaultMediaState.media) {
        currentVaultMediaState.media.forEach { visitedVaultMedia[it.getUri().toString()] = it }
    }
    val vaultMediaForSelection = visitedVaultMedia.values.toList()

    val collectedMediaState by mediaFlow.collectAsStateWithLifecycle()
    val privateFolderState by state.privateFolderMediaState.collectAsStateWithLifecycle()
    val cloudState by state.cloudMediaState.collectAsStateWithLifecycle()
    // Selection is shared across sources (MediaSelector). Resolve ids against the union of every
    // loaded source (including all vaults visited this session) so cross-source picks are honored.
    val allSourceMedia = remember(
        collectedMediaState.media, privateFolderState.media,
        cloudState.media, vaultMediaForSelection
    ) {
        (collectedMediaState.media + privateFolderState.media +
                cloudState.media + vaultMediaForSelection)
            .distinctBy { it.id }
    }

    val selectionCount = selectedMedia.value.size
    val addLabel = if (state.allowSelection && selectionCount > 0) {
        stringResource(Res.string.action_add_count, selectionCount)
    } else stringResource(Res.string.action_add)

    val selectedMediaList = allSourceMedia.selectedMedia(selectedMedia)

    fun confirmPick() {
        if (selectedMedia.value.isEmpty() || materializing != null) return
        scope.launch {
            materializing = 0 to selectedMediaList.size
            val delivered = try {
                actions.onPickConfirmed(selectedMediaList) { done, total ->
                    materializing = done to total
                }
            } finally {
                materializing = null
            }
            if (!delivered) {
                toast(pickerPrepareFailedText)
            }
        }
    }

    val navigateUp: () -> Unit = {
        when (navState) {
            is PickerNavState.PrivateFolder -> showPrivateFolder = false
            is PickerNavState.CloudMedia -> showCloud = false
            is PickerNavState.VaultChooser -> showVaultChooser = false
            is PickerNavState.VaultDetail -> selectedPickerVault = null
            is PickerNavState.AlbumDetail -> selectedAlbum = null
            is PickerNavState.Tabs -> actions.onClose()
        }
    }

    val emptyTitle = stringResource(Res.string.picker_empty_title)
    val emptySourceTitle = stringResource(Res.string.picker_empty_source)
    val emptySummary = stringResource(Res.string.picker_empty_summary)

    SharedTransitionLayout {
        AnimatedContent(
            targetState = showPreview,
            transitionSpec = {
                (fadeIn() + slideInVertically { it / 3 })
                    .togetherWith(fadeOut() + slideOutVertically { -it / 3 })
            },
            label = "previewTransition"
        ) { isPreview ->
            val previewScope = this@AnimatedContent
            if (!isPreview) {
                val hazeState = LocalHazeState.current
                // One grid state per destination, so the bar knows when the shown grid has
                // scrolled under it.
                val gridStates = remember { mutableMapOf<Any, LazyGridState>() }
                val gridStateFor: (PickerNavState) -> LazyGridState = { nav ->
                    gridStates.getOrPut(nav.gridKey()) { LazyGridState() }
                }
                val barScrolled = rememberScrolledUnderTopBar(gridStateFor(navState))
                val topBarHeight = galleryDetailTopBarHeight()
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                ) {
                    val compactHeight = maxHeight < GalleryChrome.DetailMinWindowHeight
                    val navigationBarBottom = WindowInsets.navigationBars.asPaddingValues()
                        .calculateBottomPadding()
                    val bottomClearance =
                        navigationBarBottom + if (compactHeight) Spacing.Medium else DockClearance
                    // The media grid stays edge to edge like the gallery timeline; card grids inset.
                    val mediaGridPadding = PaddingValues(top = topBarHeight, bottom = bottomClearance)
                    val gridPadding = PaddingValues(
                        start = Spacing.ScreenHorizontal,
                        end = Spacing.ScreenHorizontal,
                        top = topBarHeight,
                        bottom = bottomClearance,
                    )
                    val dock: @Composable (Modifier) -> Unit = { modifier ->
                        PickerDock(
                            modifier = modifier,
                            photosSelected = navState == PickerNavState.Tabs(TAB_PHOTOS),
                            photosLabel = stringResource(Res.string.photos),
                            albumsLabel = stringResource(Res.string.albums),
                            onPhotos = { openTab(TAB_PHOTOS) },
                            onAlbums = { openTab(TAB_ALBUMS) },
                            addLabel = addLabel,
                            addEnabled = selectionCount > 0 && materializing == null,
                            onAdd = ::confirmPick,
                            addContentDescription = addMediaContentDescription,
                        )
                    }

                    // The content scrolls under the bar, which turns into the blur bar once it does.
                    Box(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .hazeSource(hazeState)
                            ) {
                                PickerNavContent(
                                    navState = navState,
                                    state = state,
                                    gridStateFor = gridStateFor,
                                    gridPadding = gridPadding,
                                    mediaGridPadding = mediaGridPadding,
                                    mediaFlow = mediaFlow,
                                    vaultMediaFlow = vaultMediaFlow,
                                    emptyTitle = emptyTitle,
                                    emptySourceTitle = emptySourceTitle,
                                    emptySummary = emptySummary,
                                    showPrivateFolderSource = privateFolderUri.isNotEmpty(),
                                    onAlbumClick = onAlbumClickWithLock,
                                    onPrivateFolderClick = {
                                        actions.onPrivateFolder { showPrivateFolder = true }
                                    },
                                    onCloudClick = { showCloud = true },
                                    onVaultSourceClick = { actions.onVaultGate { revealVaults() } },
                                    onVaultClick = { vault ->
                                        actions.onVault(vault) {
                                            showVaultChooser = false
                                            selectedPickerVault = vault
                                        }
                                    },
                                    albumImage = albumImage,
                                    sharedTransitionScope = this@SharedTransitionLayout,
                                    animatedVisibilityScope = previewScope,
                                )
                            }
                            if (!compactHeight) {
                                dock(
                                    Modifier
                                        .align(Alignment.BottomCenter)
                                        .navigationBarsPadding()
                                        .padding(
                                            start = Spacing.ScreenHorizontal,
                                            end = Spacing.ScreenHorizontal,
                                            bottom = Spacing.Medium,
                                        )
                                        .widthIn(max = DockMaxWidth)
                                        .fillMaxWidth()
                                )
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                // The compact dock shares this row; keep it on the page colour at rest.
                                .then(
                                    if (compactHeight && !barScrolled) {
                                        Modifier.background(MaterialTheme.colorScheme.surface)
                                    } else Modifier
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CompositionLocalProvider(LocalHazePerformanceMode provides HazePerformanceMode.Performance) {
                                PickerTopBar(
                                    modifier = Modifier.weight(1f),
                                    navState = navState,
                                    title = state.title,
                                    allowSelection = state.allowSelection,
                                    selectionCount = selectionCount,
                                    onNavigate = navigateUp,
                                    onPreview = { showPreview = true },
                                    hazeState = hazeState,
                                    scrolled = barScrolled,
                                )
                            }
                            if (compactHeight) {
                                dock(
                                    Modifier
                                        .windowInsetsPadding(WindowInsets.statusBars)
                                        .padding(end = Spacing.ScreenHorizontal)
                                )
                            }
                        }
                    }
                }
            } else {
                val previewMediaList = selectedMediaList
                if (previewMediaList.isNotEmpty()) {
                    val previewMediaState = remember(previewMediaList) {
                        mutableStateOf(
                            MediaState(
                                media = previewMediaList,
                                isLoading = false
                            )
                        )
                    }
                    previewContent(
                        this@SharedTransitionLayout,
                        this@AnimatedContent,
                        previewMediaState,
                        previewMediaList.first().id,
                    ) { showPreview = false }
                } else {
                    LaunchedEffect(Unit) { showPreview = false }
                }
            }
        }
    }

    materializing?.let { (done, total) ->
        PickerPreparingDialog(done = done, total = total)
    }

    // Registered before the drill-in handlers so going up a level wins over clearing the picks.
    BackHandler(selectedMedia.value.isNotEmpty() && !showPreview) {
        selector.clearSelection()
    }

    BackHandler(selectedPickerVault != null) {
        selectedPickerVault = null
    }

    BackHandler(showVaultChooser) {
        showVaultChooser = false
    }

    BackHandler(showCloud) {
        showCloud = false
    }

    BackHandler(showPrivateFolder) {
        showPrivateFolder = false
    }

    BackHandler(selectedAlbum != null) {
        selectedAlbum = null
    }
}

@Composable
private fun PickerTopBar(
    navState: PickerNavState,
    title: String,
    allowSelection: Boolean,
    selectionCount: Int,
    onNavigate: () -> Unit,
    onPreview: () -> Unit,
    hazeState: HazeState,
    scrolled: Boolean,
    modifier: Modifier = Modifier,
) {
    val isRoot = navState is PickerNavState.Tabs
    val barTitle = when (navState) {
        is PickerNavState.Tabs -> if (allowSelection && selectionCount > 0) {
            stringResource(Res.string.picker_selected_count, selectionCount)
        } else title
        is PickerNavState.AlbumDetail -> navState.album.label
        is PickerNavState.PrivateFolder -> stringResource(Res.string.security_private_folder)
        is PickerNavState.CloudMedia -> stringResource(Res.string.cloud)
        is PickerNavState.VaultChooser -> stringResource(Res.string.vault)
        is PickerNavState.VaultDetail -> navState.vault.name
    }
    GalleryPageTopBar(
        modifier = modifier,
        title = barTitle,
        count = (navState as? PickerNavState.AlbumDetail)?.album?.count?.toString(),
        onBack = onNavigate,
        navigationIcon = if (isRoot) Icons.Outlined.Close else Icons.AutoMirrored.Filled.ArrowBack,
        navigationContentDescription = if (isRoot) stringResource(Res.string.close) else null,
        hazeState = hazeState,
        scrolled = scrolled,
        actions = if (selectionCount > 0) {
            {
                GalleryPageTopBarAction(
                    icon = Icons.Outlined.Visibility,
                    contentDescription = stringResource(Res.string.preview),
                    onClick = onPreview,
                )
            }
        } else null,
    )
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun PickerNavContent(
    navState: PickerNavState,
    state: PickerState,
    gridStateFor: (PickerNavState) -> LazyGridState,
    gridPadding: PaddingValues,
    mediaGridPadding: PaddingValues,
    mediaFlow: StateFlow<MediaState<Media>>,
    vaultMediaFlow: StateFlow<MediaState<Media>>,
    emptyTitle: String,
    emptySourceTitle: String,
    emptySummary: String,
    showPrivateFolderSource: Boolean,
    onAlbumClick: (Album) -> Unit,
    onPrivateFolderClick: () -> Unit,
    onCloudClick: () -> Unit,
    onVaultSourceClick: () -> Unit,
    onVaultClick: (Vault) -> Unit,
    albumImage: @Composable (album: Album, onClick: (Album) -> Unit) -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    AnimatedContent(
        targetState = navState,
        transitionSpec = {
            val target = targetState
            val initial = initialState
            when {
                target is PickerNavState.Tabs && initial is PickerNavState.Tabs -> {
                    val direction = if (target.tabIndex > initial.tabIndex) 1 else -1
                    (slideInHorizontally { direction * it } + fadeIn())
                        .togetherWith(slideOutHorizontally { -direction * it } + fadeOut())
                }
                initial is PickerNavState.Tabs -> {
                    (slideInHorizontally { it } + fadeIn())
                        .togetherWith(slideOutHorizontally { -it / 3 } + fadeOut())
                }
                target is PickerNavState.Tabs -> {
                    (slideInHorizontally { -it / 3 } + fadeIn())
                        .togetherWith(slideOutHorizontally { it } + fadeOut())
                }
                else -> fadeIn() togetherWith fadeOut()
            }.using(SizeTransform(clip = false))
        },
        label = "contentAnimation"
    ) { nav ->
        when (nav) {
            is PickerNavState.AlbumDetail -> PickerMediaContent(
                mediaState = mediaFlow,
                metadataState = state.metadataState,
                allowSelection = state.allowSelection,
                contentPadding = mediaGridPadding,
                gridState = gridStateFor(nav),
                emptyTitle = emptySourceTitle,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
            )
            is PickerNavState.PrivateFolder -> PickerMediaContent(
                mediaState = state.privateFolderMediaState,
                metadataState = state.metadataState,
                allowSelection = state.allowSelection,
                contentPadding = mediaGridPadding,
                gridState = gridStateFor(nav),
                emptyTitle = emptySourceTitle,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
            )
            is PickerNavState.CloudMedia -> PickerMediaContent(
                mediaState = state.cloudMediaState,
                metadataState = state.metadataState,
                allowSelection = state.allowSelection,
                contentPadding = mediaGridPadding,
                gridState = gridStateFor(nav),
                emptyTitle = emptySourceTitle,
                header = {
                    PickerInfoBanner(
                        modifier = Modifier.padding(horizontal = Spacing.ScreenHorizontal),
                        icon = Icons.Outlined.Download,
                        text = stringResource(Res.string.picker_cloud_notice),
                    )
                },
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
            )
            is PickerNavState.VaultDetail -> PickerMediaContent(
                mediaState = vaultMediaFlow,
                metadataState = state.metadataState,
                allowSelection = state.allowSelection,
                contentPadding = mediaGridPadding,
                gridState = gridStateFor(nav),
                emptyTitle = emptySourceTitle,
                header = {
                    PickerInfoBanner(
                        modifier = Modifier.padding(horizontal = Spacing.ScreenHorizontal),
                        icon = Icons.Outlined.EnhancedEncryption,
                        text = stringResource(Res.string.picker_vault_notice),
                    )
                },
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
            )
            is PickerNavState.VaultChooser -> PickerVaultChooserGrid(
                vaults = state.vaultState.value.vaults,
                contentPadding = gridPadding,
                gridState = gridStateFor(nav),
                onVaultClick = onVaultClick,
            )
            is PickerNavState.Tabs -> when (nav.tabIndex) {
                TAB_PHOTOS -> PickerMediaContent(
                    mediaState = mediaFlow,
                    metadataState = state.metadataState,
                    allowSelection = state.allowSelection,
                    contentPadding = mediaGridPadding,
                    gridState = gridStateFor(nav),
                    emptyTitle = emptyTitle,
                    emptySummary = emptySummary,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                )
                else -> PickerAlbumsGrid(
                    albums = state.albumsState.value.albums.filter { it.id != -1L },
                    contentPadding = gridPadding,
                    gridState = gridStateFor(nav),
                    onAlbumClick = onAlbumClick,
                    showPrivateFolder = showPrivateFolderSource,
                    onPrivateFolderClick = onPrivateFolderClick,
                    showCloud = state.cloudAvailable,
                    onCloudClick = onCloudClick,
                    vaultCount = state.vaultState.value.vaults.size,
                    onVaultClick = onVaultSourceClick,
                    albumImage = albumImage,
                )
            }
        }
    }
}

@Composable
private fun PickerPreparingDialog(done: Int, total: Int) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 312.dp)
                    .padding(Spacing.Large),
                verticalArrangement = Arrangement.spacedBy(Spacing.Medium)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Download,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(Res.string.picker_preparing_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (total > 0) {
                        stringResource(Res.string.picker_preparing, done, total)
                    } else stringResource(Res.string.picker_preparing_generic),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (total > 0) {
                    LinearProgressIndicator(
                        progress = { done.toFloat() / total },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerSecurityInfoSheet(
    sheetState: AppBottomSheetState
) {
    val scope = rememberCoroutineScope()
    if (sheetState.isVisible) {
        ModalBottomSheet(
            sheetState = sheetState.sheetState,
            onDismissRequest = {
                scope.launch { sheetState.hide() }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 0.dp,
            dragHandle = { DragHandle() },
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(start = Spacing.Large, end = Spacing.Large, top = Spacing.Small, bottom = Spacing.ExtraLarge)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.errorContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = stringResource(Res.string.locked),
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                Text(
                    text = stringResource(Res.string.picker_locked_title),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(Res.string.locked_album_security_error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.Small),
                    onClick = { scope.launch { sheetState.hide() } },
                ) {
                    Text(text = stringResource(Res.string.action_ok))
                }
            }
        }
    }
}

@Composable
private fun PickerAlbumsGrid(
    albums: List<Album>,
    contentPadding: PaddingValues,
    onAlbumClick: (Album) -> Unit,
    showPrivateFolder: Boolean,
    onPrivateFolderClick: () -> Unit,
    showCloud: Boolean,
    onCloudClick: () -> Unit,
    vaultCount: Int,
    onVaultClick: () -> Unit,
    albumImage: @Composable (album: Album, onClick: (Album) -> Unit) -> Unit,
    gridState: LazyGridState = rememberLazyGridState(),
) {
    // album covers (RenderImage) load the cheap MOTION tier while scrolling.
    val thumbnailMotion = rememberThumbnailMotionState({ gridState.isScrollInProgress })
    val showVault = vaultCount > 0
    CompositionLocalProvider(LocalThumbnailMotion provides thumbnailMotion) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(Dimens.Album()),
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (showPrivateFolder || showVault || showCloud) {
                item(key = "sources", span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        modifier = Modifier.padding(top = Spacing.Small),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.Small)
                    ) {
                        if (showPrivateFolder) {
                            PickerSourceTile(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Outlined.Shield,
                                label = stringResource(Res.string.security_private_folder),
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                onClick = onPrivateFolderClick,
                            )
                        }
                        if (showVault) {
                            PickerSourceTile(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Outlined.EnhancedEncryption,
                                label = stringResource(Res.string.vault),
                                meta = pluralStringResource(Res.plurals.picker_vault_count, vaultCount, vaultCount),
                                showLock = true,
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                onClick = onVaultClick,
                            )
                        }
                        if (showCloud) {
                            PickerSourceTile(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Outlined.Cloud,
                                label = stringResource(Res.string.cloud),
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                onClick = onCloudClick,
                            )
                        }
                        // Keep tile widths stable when a source isn't set up.
                        repeat(listOf(showPrivateFolder, showVault, showCloud).count { !it }) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            if (albums.isNotEmpty()) {
                item(key = "albums_header", span = { GridItemSpan(maxLineSpan) }) {
                    PickerSectionTitle(title = stringResource(Res.string.albums))
                }
            }
            items(
                items = albums,
                key = { it.toString() }
            ) { album ->
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                    Box(modifier = Modifier.aspectRatio(1f)) {
                        albumImage(album, onAlbumClick)
                    }
                    Column(modifier = Modifier.padding(horizontal = 4.dp)) {
                        Text(
                            text = album.label,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1
                        )
                        Text(
                            text = if (album.isLocked) stringResource(Res.string.locked)
                            else pluralStringResource(
                                Res.plurals.picker_album_items,
                                album.count.toInt(),
                                album.count.toInt()
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerVaultChooserGrid(
    vaults: List<Vault>,
    contentPadding: PaddingValues,
    onVaultClick: (Vault) -> Unit,
    gridState: LazyGridState = rememberLazyGridState(),
) {
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(Dimens.Album()),
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "intro", span = { GridItemSpan(maxLineSpan) }) {
            PickerSectionTitle(
                title = stringResource(Res.string.picker_vault_intro_title),
                description = stringResource(Res.string.picker_vault_intro),
            )
        }
        itemsIndexed(
            items = vaults,
            key = { _, vault -> vault.uuid.toString() }
        ) { index, vault ->
            PickerVaultTile(
                name = vault.name,
                containerColor = if (index == 0) MaterialTheme.colorScheme.tertiaryContainer
                else MaterialTheme.colorScheme.primaryContainer,
                contentColor = if (index == 0) MaterialTheme.colorScheme.onTertiaryContainer
                else MaterialTheme.colorScheme.onPrimaryContainer,
                onClick = { onVaultClick(vault) },
            )
        }
    }
}
