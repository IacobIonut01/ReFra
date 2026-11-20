package com.dot.gallery.app.presentation.mediaview

import androidx.compose.animation.core.DeferredTransitionState
import androidx.compose.animation.core.ExperimentalDeferredTransitionApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import com.dot.gallery.core.MediaViewerOverlaySignals
import com.dot.gallery.core.presentation.util.Screen
import com.dot.gallery.cloud.ui.archive.CloudArchiveScreen
import com.dot.gallery.cloud.ui.people.PersonDetailScreen
import com.dot.gallery.app.presentation.collection.CollectionViewScreen
import com.dot.gallery.app.presentation.favorites.FavoriteScreen
import com.dot.gallery.app.presentation.location.LocationTimelineScreen
import com.dot.gallery.app.presentation.privatefolder.PrivateFolderScreen
import com.dot.gallery.app.presentation.search.SearchScreen
import com.dot.gallery.feature.vault.privatefolder.PrivateFolderViewModel
import com.dot.gallery.core.Constants.Target.TARGET_FAVORITES
import com.dot.gallery.core.Constants.Target.TARGET_TRASH
import com.dot.gallery.domain.uri.path
import java.util.UUID




@Stable
class MediaViewerOverlayController : MediaViewerOverlaySignals {
    var route by mutableStateOf<String?>(null)
        private set

    var retainedRoute by mutableStateOf<String?>(null)
        private set

    override var currentMediaId by mutableLongStateOf(-1L)
        private set

    // Bumped on every successful open() so a viewer session that reuses a composition still
    // gets a fresh ViewerDismissState.
    var openCount by mutableIntStateOf(0)
        private set

    // Identifies one viewer entry for saveable-state scoping; refreshed on every successful
    // open() so state saved by a previous entry is never restored into a new one.
    var entryKey by mutableStateOf("")
        private set

    override val visible: Boolean
        get() = route != null

    fun open(route: String): Boolean {
        if (!isMediaViewerRoute(route)) return false
        this.route = route
        retainedRoute = route
        currentMediaId = mediaViewerRouteMediaId(route)
        openCount++
        entryKey = UUID.randomUUID().toString()
        return true
    }

    fun updateCurrentMedia(mediaId: Long) {
        currentMediaId = mediaId
    }

    fun dismiss() {
        route = null
    }

    fun clearRetained() {
        if (route == null) retainedRoute = null
    }
}

@Composable
fun rememberMediaViewerOverlayController(): MediaViewerOverlayController =
    remember { MediaViewerOverlayController() }

internal fun isMediaViewerRoute(route: String): Boolean =
    route.substringBefore('?').let { path ->
        path == Screen.MediaViewScreen.route || path == "${Screen.MediaViewScreen.route}_search"
    }

internal fun mediaViewerRouteMediaId(route: String): Long =
    route.substringAfter("mediaId=", missingDelimiterValue = "")
        .substringBefore('&')
        .toLongOrNull()
        ?: -1L

internal fun canPresentMediaViewerOverlay(route: String, originRoute: String?): Boolean {
    if (!isMediaViewerRoute(route) || originRoute == null) return false
    val originPath = originRoute.substringBefore('?')
    if (originPath.startsWith(Screen.MediaViewScreen.route)) return false
    val args = parseMediaViewerRoute(route)
    return when {
        args.albumId == PrivateFolderViewModel.PRIVATE_FOLDER_ALBUM_ID ->
            originPath == Screen.PrivateFolderScreen.route
        args.albumId != null && args.albumId != -1L ->
            originPath == Screen.AlbumViewScreen.route
        args.target == TARGET_FAVORITES -> originPath == Screen.FavoriteScreen.route
        args.target == TARGET_TRASH -> originPath == Screen.TrashedScreen.route
        args.target == "cloud_archive" -> originPath == Screen.CloudArchiveScreen.route
        args.path.endsWith("_search") -> originPath == Screen.SearchScreen.route
        args.category != null || args.categoryId != null ->
            originPath.contains("category")
        args.collectionId != null -> originPath == Screen.CollectionViewScreen.route
        args.personId != null -> originPath == Screen.PersonDetailScreen.route
        args.city != null || args.country != null ->
            originPath == Screen.LocationTimelineScreen.route
        args.albumId == -1L -> true
        else -> false
    }
}
