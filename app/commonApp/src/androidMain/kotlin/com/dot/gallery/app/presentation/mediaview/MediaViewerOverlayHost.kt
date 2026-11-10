package com.dot.gallery.app.presentation.mediaview

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import com.dot.gallery.core.LocalMediaDistributor
import com.dot.gallery.domain.model.AlbumState
import com.dot.gallery.domain.model.Media
import com.dot.gallery.data.model.MediaMetadataState
import com.dot.gallery.domain.model.MediaState
import com.dot.gallery.domain.model.VaultState
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import com.dot.gallery.core.presentation.util.mediaFlow
import com.dot.gallery.cloud.ui.archive.CloudArchiveViewModel
import com.dot.gallery.cloud.ui.people.PersonDetailViewModel
import com.dot.gallery.core.presentation.vm.NavigationViewModel
import com.dot.gallery.feature.albums.ui.AlbumTimelineViewModel
import com.dot.gallery.feature.classifier.CategoryViewModel
import com.dot.gallery.feature.library.location.LocationsViewModel
import com.dot.gallery.feature.vault.privatefolder.PrivateFolderViewModel
import com.dot.gallery.feature.search.SearchViewModel
import com.dot.gallery.core.Constants.Target.TARGET_FAVORITES
import com.dot.gallery.core.Constants.Target.TARGET_TRASH
import com.dot.gallery.core.ViewerDismissBridge
import com.dot.gallery.domain.uri.path
import com.dot.gallery.domain.uri.query
import com.dot.gallery.injection.injectViewModel



internal data class ParsedMediaViewerRoute(
    val path: String,
    val mediaId: Long,
    val albumId: Long?,
    val target: String?,
    val slideshow: Boolean,
    val category: String?,
    val categoryId: Long?,
    val collectionId: Long?,
    val configId: Long?,
    val personId: String?,
    val city: String?,
    val country: String?,
    val latitude: Double?,
    val longitude: Double?,
)

internal fun parseMediaViewerRoute(route: String): ParsedMediaViewerRoute {
    val query = route.substringAfter('?', missingDelimiterValue = "")
        .split('&')
        .mapNotNull { parameter ->
            val separator = parameter.indexOf('=')
            if (separator < 0) null else {
                val key = parameter.substring(0, separator)
                val value = URLDecoder.decode(
                    parameter.substring(separator + 1),
                    StandardCharsets.UTF_8.name(),
                )
                key to value
            }
        }
        .toMap()
    return ParsedMediaViewerRoute(
        path = route.substringBefore('?'),
        mediaId = query["mediaId"]?.toLongOrNull() ?: -1L,
        albumId = query["albumId"]?.toLongOrNull(),
        target = query["target"],
        slideshow = query["slideshow"].toBoolean(),
        category = query["category"],
        categoryId = query["categoryId"]?.toLongOrNull(),
        collectionId = query["collectionId"]?.toLongOrNull(),
        configId = query["configId"]?.toLongOrNull(),
        personId = query["personId"],
        city = query["gpsLocationNameCity"],
        country = query["gpsLocationNameCountry"],
        latitude = query["latitude"]?.toDoubleOrNull(),
        longitude = query["longitude"]?.toDoubleOrNull(),
    )
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MediaViewerOverlayHost(
    route: String,
    controller: MediaViewerOverlayController,
    navController: NavHostController,
    paddingValues: PaddingValues,
    allowBlur: Boolean,
    toggleRotate: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    dismissBridge: ViewerDismissBridge,
) {
    val args = remember(route) { parseMediaViewerRoute(route) }
    val originEntry = navController.currentBackStackEntry ?: return
    val navigationViewModel = injectViewModel<NavigationViewModel> {navigationViewModel}
    val metadataState = navigationViewModel.metadataState.collectAsStateWithLifecycle()
    val albumsState = navigationViewModel.albumsState.collectAsStateWithLifecycle()
    val vaultState = navigationViewModel.vaultState.collectAsStateWithLifecycle()

    when {
        args.albumId != null -> {
            val mediaState = when (args.albumId) {
                PrivateFolderViewModel.PRIVATE_FOLDER_ALBUM_ID ->
                    injectViewModel<PrivateFolderViewModel>(viewModelStoreOwner = originEntry) {privateFolderViewModel}
                        .mediaState.collectAsStateWithLifecycle()
                -1L -> navigationViewModel.timelineMediaState.collectAsStateWithLifecycle()
                else -> injectViewModel<AlbumTimelineViewModel>(viewModelStoreOwner = originEntry) {albumTimelineViewModel(it)}
                    .mediaState.collectAsStateWithLifecycle()
            }
            OverlayMediaViewer(
                args = args,
                controller = controller,
                mediaState = mediaState,
                metadataState = metadataState,
                albumsState = albumsState,
                vaultState = vaultState,
                paddingValues = paddingValues,
                allowBlur = allowBlur,
                toggleRotate = toggleRotate,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                dismissBridge = dismissBridge,
            )
        }
        args.target != null -> {
            val mediaState = when (args.target) {
                TARGET_FAVORITES -> navigationViewModel.favoriteMediaState.collectAsStateWithLifecycle()
                TARGET_TRASH -> navigationViewModel.trashedMediaState.collectAsStateWithLifecycle()
                "cloud_archive" -> injectViewModel<CloudArchiveViewModel>(viewModelStoreOwner = originEntry) {cloudArchiveViewModel}
                    .mediaState.collectAsStateWithLifecycle()
                else -> navigationViewModel.timelineMediaState.collectAsStateWithLifecycle()
            }
            OverlayMediaViewer(
                args = args,
                controller = controller,
                mediaState = mediaState,
                metadataState = metadataState,
                albumsState = albumsState,
                vaultState = vaultState,
                paddingValues = paddingValues,
                allowBlur = allowBlur,
                toggleRotate = toggleRotate,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                dismissBridge = dismissBridge,
            )
        }
        args.path.endsWith("_search") -> {
            val searchState = injectViewModel<SearchViewModel> {searchViewModel}
                .searchResultsState.collectAsStateWithLifecycle()
            val mediaState = rememberUpdatedState(searchState.value.viewerResults)
            OverlayMediaViewer(
                args = args,
                controller = controller,
                mediaState = mediaState,
                metadataState = metadataState,
                albumsState = albumsState,
                vaultState = vaultState,
                paddingValues = paddingValues,
                allowBlur = allowBlur,
                toggleRotate = toggleRotate,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                dismissBridge = dismissBridge,
            )
        }
        args.categoryId != null -> {
            val viewModel = injectViewModel<CategoryViewModel>(viewModelStoreOwner = originEntry) {categoryViewModel}
            LaunchedEffect(args.categoryId) { viewModel.setCategoryId(args.categoryId) }
            val mediaState = viewModel.mediaByCategoryId.collectAsStateWithLifecycle(MediaState())
            OverlayMediaViewer(
                args = args,
                controller = controller,
                mediaState = mediaState,
                metadataState = metadataState,
                albumsState = albumsState,
                vaultState = vaultState,
                paddingValues = paddingValues,
                allowBlur = allowBlur,
                toggleRotate = toggleRotate,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                dismissBridge = dismissBridge,
            )
        }
        args.category != null -> {
            val viewModel = injectViewModel<CategoryViewModel>(viewModelStoreOwner = originEntry) {categoryViewModel}
            LaunchedEffect(args.category) { viewModel.category = args.category }
            val mediaState = viewModel.mediaByCategory.collectAsStateWithLifecycle(MediaState())
            OverlayMediaViewer(
                args = args,
                controller = controller,
                mediaState = mediaState,
                metadataState = metadataState,
                albumsState = albumsState,
                vaultState = vaultState,
                paddingValues = paddingValues,
                allowBlur = allowBlur,
                toggleRotate = toggleRotate,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                dismissBridge = dismissBridge,
            )
        }
        args.collectionId != null -> {
            val distributor = LocalMediaDistributor.current
            val mediaFlow = remember(args.collectionId) {
                distributor.collectionMediaFlow(args.collectionId)
            }
            val mediaState = mediaFlow.collectAsStateWithLifecycle()
            OverlayMediaViewer(
                args = args,
                controller = controller,
                mediaState = mediaState,
                metadataState = metadataState,
                albumsState = albumsState,
                vaultState = vaultState,
                paddingValues = paddingValues,
                allowBlur = allowBlur,
                toggleRotate = toggleRotate,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                dismissBridge = dismissBridge,
            )
        }
        args.personId != null -> {
            val mediaState = injectViewModel<PersonDetailViewModel>(viewModelStoreOwner = originEntry) {personDetailViewModel(it)}
                .mediaState.collectAsStateWithLifecycle()
            OverlayMediaViewer(
                args = args,
                controller = controller,
                mediaState = mediaState,
                metadataState = metadataState,
                albumsState = albumsState,
                vaultState = vaultState,
                paddingValues = paddingValues,
                allowBlur = allowBlur,
                toggleRotate = toggleRotate,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                dismissBridge = dismissBridge,
            )
        }
        args.city != null || args.country != null -> {
            val viewModel = injectViewModel<LocationsViewModel>(
                viewModelStoreOwner = originEntry,
                key = "LocationViewModel"
            ) {
                locationsViewModel(
                    args.city.orEmpty(),
                    args.country.orEmpty(),
                    args.latitude,
                    args.longitude,
                )
            }
            val mediaState = viewModel.mediaState.collectAsStateWithLifecycle()
            OverlayMediaViewer(
                args = args,
                controller = controller,
                mediaState = mediaState,
                metadataState = metadataState,
                albumsState = albumsState,
                vaultState = vaultState,
                paddingValues = paddingValues,
                allowBlur = allowBlur,
                toggleRotate = toggleRotate,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                dismissBridge = dismissBridge,
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun <T : Media> OverlayMediaViewer(
    args: ParsedMediaViewerRoute,
    controller: MediaViewerOverlayController,
    mediaState: State<MediaState<out T>>,
    metadataState: State<MediaMetadataState>,
    albumsState: State<AlbumState>,
    vaultState: State<VaultState>,
    paddingValues: PaddingValues,
    allowBlur: Boolean,
    toggleRotate: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    dismissBridge: ViewerDismissBridge,
) {
    MediaViewScreenRoute(
        toggleRotate = toggleRotate,
        paddingValues = paddingValues,
        mediaId = args.mediaId,
        target = args.target,
        mediaState = mediaState,
        metadataState = metadataState,
        albumsState = albumsState,
        vaultState = vaultState,
        slideshow = args.slideshow,
        allowBlur = allowBlur,
        sharedTransitionScope = sharedTransitionScope,
        animatedContentScope = animatedVisibilityScope,
        onDismissRequest = controller::dismiss,
        onCurrentMediaChange = controller::updateCurrentMedia,
        viewerSessionKey = controller.openCount,
        dismissBridge = dismissBridge,
    )
}
