package com.dot.gallery.feature_node.presentation.mediaview

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
import com.dot.gallery.core.Constants.Target.TARGET_FAVORITES
import com.dot.gallery.core.Constants.Target.TARGET_TRASH
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.navigate
import com.dot.gallery.core.navigateUp
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.presentation.privatefolder.PrivateFolderViewModel
import com.dot.gallery.feature_node.presentation.util.Screen

@Stable
class MediaViewerOverlayController {
    var route by mutableStateOf<String?>(null)
        private set

    var retainedRoute by mutableStateOf<String?>(null)
        private set

    var currentMediaId by mutableLongStateOf(-1L)
        private set

    // Bumped on every successful open() so a viewer session that reuses a composition still
    // gets a fresh ViewerDismissState.
    var openCount by mutableIntStateOf(0)
        private set

    val visible: Boolean
        get() = route != null

    fun open(route: String): Boolean {
        if (!isMediaViewerRoute(route)) return false
        this.route = route
        retainedRoute = route
        currentMediaId = mediaViewerRouteMediaId(route)
        openCount++
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

/**
 * Bridges a viewer's swipe-down dismiss gesture to the overlay host.
 *
 * The drag offset itself is applied to a card box *inside* the viewer content (never to the
 * container or the gesture-handling node), so [transitionState] only drives the plain
 * enter/exit fades. On commit the return flight is driven manually: a root-level [flight]
 * layer morphs the media thumbnail from the release bounds to the source cell's live
 * [cellBounds] while the overlay fades out underneath — the shared element's bounds animation
 * is bypassed entirely for the dismiss direction (and on the enter direction, where a manual
 * cell→fullscreen flight replaces it), because its deferred handoff bookkeeping cannot track a
 * fullscreen element whose match partner lives behind a translating mosaic grid.
 */
@OptIn(ExperimentalDeferredTransitionApi::class)
@Stable
class ViewerDismissBridge {
    internal val transitionState = DeferredTransitionState(initialState = false)

    /**
     * Key of the shared element suppressed while a dismiss drag is armed. Both the source cell
     * and the viewer element drop out of the match, so the media renders in place inside the
     * offset container instead of chasing the cell through the bounds animation mid-gesture.
     */
    var suppressedElementKey by mutableStateOf<Any?>(null)
        internal set

    /** Root-space bounds of shared-element source cells/cards, keyed by element key. */
    internal val cellBounds = mutableStateMapOf<Any, Rect>()

    /** The manual flight currently morphing media bounds — dismiss (release→cell) or the
     * overlay's enter (cell→fullscreen). */
    var flight by mutableStateOf<ViewerDismissFlight?>(null)
        internal set
}

/**
 * A manually driven shared-element flight. [bounds] is the live morph rect in root space and
 * [progress] (0→1) drives the thumbnail's content-scale interpolation so the flight lands
 * exactly on the destination's rendering.
 *
 * For a committed dismiss ([isEnter] = false) bounds animate release→cell with Fit→Crop. For
 * an overlay open ([isEnter] = true) bounds animate cell→fullscreen with Crop→Fit — the
 * framework's own bounds morph can't be trusted for the deferred enter (its DeferredAnimation
 * is recreated once the transition's currentState already reads the target, so it initializes
 * at the end bounds and the media pops in instead of morphing).
 */
@Stable
class ViewerDismissFlight(
    val key: Any,
    val media: Media,
    bounds: Rect,
    val isEnter: Boolean = false,
) {
    var bounds by mutableStateOf(bounds)
        internal set

    var progress by mutableFloatStateOf(0f)
        internal set
}

val LocalMediaViewerOverlayController = staticCompositionLocalOf<MediaViewerOverlayController?> { null }

/** The active overlay host's dismiss bridge, provided to source cells so they can suppress their
 * shared element while it is the armed drag's match target. Null outside overlay hosts. */
val LocalViewerDismissBridge = staticCompositionLocalOf<ViewerDismissBridge?> { null }

/**
 * Route navigation that first gets the media viewer out of the way so the destination is
 * actually revealed. Provided by `MediaViewScreen`: when the viewer is presented as an
 * overlay above the NavHost the destination is pushed underneath and the overlay is then
 * dismissed; when the viewer is a standalone back-stack destination its entry is popped
 * first so it does not linger hidden under the new screen. `null` outside the viewer —
 * read it through [rememberMediaViewerNavigate], which falls back to plain navigation.
 */
val LocalMediaViewerNavigate = compositionLocalOf<((String) -> Unit)?> { null }

internal enum class ViewerExitStep { Navigate, PopViewer, DismissOverlay }

/**
 * Ordered steps for navigating from inside a media viewer to another destination.
 * Overlay mode pushes the destination underneath the NavHost before dropping the overlay
 * so the push is revealed, never hidden; standalone mode pops the viewer's own back-stack
 * entry first so it does not linger under the new screen.
 */
internal fun mediaViewerExitSteps(isOverlay: Boolean): List<ViewerExitStep> =
    if (isOverlay) {
        listOf(ViewerExitStep.Navigate, ViewerExitStep.DismissOverlay)
    } else {
        listOf(ViewerExitStep.PopViewer, ViewerExitStep.Navigate)
    }

/**
 * The [LocalMediaViewerNavigate] implementation for a media viewer. [onDismissRequest] is
 * the overlay host's dismiss callback — non-null when the viewer floats above the NavHost
 * (`MediaViewerOverlayHost`), `null` when the viewer is a standalone destination.
 */
@Composable
fun rememberViewerExitNavigate(onDismissRequest: (() -> Unit)?): (String) -> Unit {
    val eventHandler = LocalEventHandler.current
    val latestDismissRequest by rememberUpdatedState(onDismissRequest)
    return remember(eventHandler) {
        { route: String ->
            val dismiss = latestDismissRequest
            mediaViewerExitSteps(isOverlay = dismiss != null).forEach { step ->
                when (step) {
                    ViewerExitStep.Navigate -> eventHandler.navigate(route)
                    ViewerExitStep.PopViewer -> eventHandler.navigateUp()
                    ViewerExitStep.DismissOverlay -> dismiss?.invoke()
                }
            }
        }
    }
}

/**
 * Navigation for composables hosted inside the media viewer (sheets, rows, buttons):
 * closes the viewer when one is open so the destination is revealed, and falls back to
 * plain navigation everywhere else.
 */
@Composable
fun rememberMediaViewerNavigate(): (String) -> Unit {
    val eventHandler = LocalEventHandler.current
    val viewerNavigate = LocalMediaViewerNavigate.current
    return remember(eventHandler, viewerNavigate) {
        viewerNavigate ?: { route: String -> eventHandler.navigate(route) }
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
