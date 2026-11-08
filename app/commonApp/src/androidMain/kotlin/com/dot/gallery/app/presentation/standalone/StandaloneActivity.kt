/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.standalone

import android.app.KeyguardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import com.dot.gallery.injection.ComponentOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.dot.gallery.core.DefaultEventHandler
import com.dot.gallery.core.MediaDistributor
import com.dot.gallery.core.MediaHandler
import com.dot.gallery.core.MediaSelector
import com.dot.gallery.core.util.SetupMediaProviders
import com.dot.gallery.domain.model.UIEvent
import com.dot.gallery.domain.util.EventHandler
import com.dot.gallery.feature.exif.ui.MetadataViewContent
import com.dot.gallery.core.presentation.util.LocalHazeGlassEnabled
import com.dot.gallery.core.presentation.util.LocalHazeState
import com.dot.gallery.core.presentation.util.Screen
import com.dot.gallery.app.presentation.util.toggleOrientation
import com.dot.gallery.ui.theme.GalleryTheme
import dev.chrisbanes.haze.blur.LocalHazeBlurStyle
import dev.chrisbanes.haze.blur.materials.HazeMaterials
import dev.chrisbanes.haze.rememberHazeState
import androidx.appcompat.app.AppCompatActivity
import com.dot.gallery.core.util.or
import com.dot.gallery.ui.theme.AppThemeLocals
import com.dot.gallery.core.presentation.util.add
import com.dot.gallery.core.presentation.util.clear
import com.dot.gallery.core.Settings.Misc.rememberAllowBlur
import com.dot.gallery.core.Settings.Misc.rememberAllowGlass
import com.dot.gallery.core.ui.settings.LocalSettingsStore
import com.dot.gallery.domain.settings.DataStoreSettingsStore
import com.dot.gallery.domain.util.isVideo
import com.dot.gallery.app.presentation.mediaview.MediaViewScreenRoute
import com.dot.gallery.core.activeDataStore
import com.dot.gallery.injection.injectViewModel
import com.dot.gallery.feature.viewer.standalone.StandaloneViewModel


internal fun shouldShowStandaloneViewerUi(
    intentMimeType: String?,
    contentMimeType: String?,
): Boolean = listOfNotNull(intentMimeType, contentMimeType).none {
    it.startsWith("video/", ignoreCase = true)
}

open class StandaloneActivity : AppCompatActivity() {

    private val component get() = (application as ComponentOwner).component
    private val eventHandler: EventHandler = DefaultEventHandler()

    val mediaDistributor: MediaDistributor get() = component.mediaDistributor
    val mediaHandler: MediaHandler get() = component.mediaHandler
    val mediaSelector: MediaSelector get() = component.mediaSelector

    @OptIn(ExperimentalSharedTransitionApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        enableEdgeToEdge()
        showIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModelStore.clear()
        showIntent(intent)
    }

    @OptIn(ExperimentalSharedTransitionApi::class)
    private fun showIntent(intent: Intent) {
        val action = intent.action.toString()
        // A review can be "secure" in two ways:
        //  - an explicit ACTION_REVIEW_SECURE action, or
        //  - a plain ACTION_REVIEW launched while the device is locked. Google
        //    Camera's secure session uses the latter: it sends
        //    com.android.camera.action.REVIEW (no "secure" in the action) from
        //    over the keyguard, so action-name matching alone misses it and the
        //    keyguard would prompt for unlock before showing the photo.
        val keyguardManager = getSystemService(KeyguardManager::class.java)
        val isSecure = action.lowercase().contains("secure") ||
                keyguardManager?.isKeyguardLocked == true
        val clipData = intent.clipData
        val uriList = mutableSetOf<Uri>()
        intent.data?.let(uriList::add)
        if (clipData != null) {
            for (i in 0 until clipData.itemCount) {
                uriList.add(clipData.getItemAt(i).uri)
            }
        }
        val contentMimeType = uriList.firstOrNull()?.let { uri ->
            runCatching { contentResolver.getType(uri) }.getOrNull()
        }
        val initialUiVisible = shouldShowStandaloneViewerUi(intent.type, contentMimeType)
        // Occlude (and wake over) the lock screen when the review is secure.
        // This is only enabled when launched while locked, so a normal review
        // that is later locked mid-viewing never bypasses the keyguard.
        setShowWhenLocked(isSecure)
        setTurnScreenOn(isSecure)
        setContent {
            val settingsStore = remember { DataStoreSettingsStore(activeDataStore) }
            CompositionLocalProvider(LocalSettingsStore provides settingsStore) {
            AppThemeLocals {
                GalleryTheme {
                val allowBlur by rememberAllowBlur()
                val allowGlass by rememberAllowGlass()
                val hazeState = rememberHazeState()
                val viewModel =
                    injectViewModel<StandaloneViewModel> {
                        standaloneViewModel(
                            action.contains("REVIEW", true),
                            isSecure,
                            uriList.toList()
                        )
                    }
                CompositionLocalProvider(
                    LocalHazeState provides hazeState,
                    LocalHazeBlurStyle provides HazeMaterials.thin(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ).then { blurEnabled(allowBlur) },
                    LocalHazeGlassEnabled provides (allowBlur && allowGlass)
                ) {
                    // Standalone has no NavHost, so the "view all metadata" button's navigate()
                    // call was dropped and nothing happened (/). We intercept that route
                    // here and show the metadata screen inside the AnimatedContent below; when set
                    // (mediaUri to isVideo) the metadata screen is shown instead of the image.
                    var metadataArgs by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
                    LaunchedEffect(eventHandler) {
                        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                            eventHandler.updaterFlow.collect { event ->
                                when (event) {
                                    is UIEvent.NavigationUpEvent -> {
                                        // Back out of the metadata screen to the image first; only
                                        // finish the activity when the image itself is showing.
                                        if (metadataArgs != null) metadataArgs = null else finish()
                                    }
                                    is UIEvent.NavigationRouteEvent -> {
                                        val route = event.route
                                        if (route.startsWith(Screen.MetadataViewScreen.route)) {
                                            val parsed = Uri.parse(route)
                                            metadataArgs =
                                                (parsed.getQueryParameter("mediaUri") ?: "") to
                                                        (parsed.getQueryParameter("isVideo")?.toBoolean() ?: false)
                                        }
                                    }
                                    else -> Unit
                                }
                            }
                        }
                    }
                    SetupMediaProviders(
                        eventHandler = eventHandler,
                        mediaDistributor = mediaDistributor,
                        mediaHandler = mediaHandler,
                        mediaSelector = mediaSelector
                    ) {
                        Scaffold { paddingValues ->
                            val vaults = viewModel.vaults.collectAsStateWithLifecycle()
                            val mediaState = viewModel.mediaState.collectAsStateWithLifecycle()
                            val albumsState = viewModel.albumsState.collectAsStateWithLifecycle()
                            val metadataState =
                                viewModel.metadataState.collectAsStateWithLifecycle()
                            val mediaId by viewModel.mediaId.collectAsStateWithLifecycle()
                            SharedTransitionLayout {
                                AnimatedContent(
                                    targetState = metadataArgs,
                                    label = "standalone"
                                ) { args ->
                                    if (args == null) {
                                        MediaViewScreenRoute(
                                            toggleRotate = ::toggleOrientation,
                                            paddingValues = paddingValues,
                                            isStandalone = true,
                                            initialUiVisible = initialUiVisible,
                                            mediaId = mediaId,
                                            allowBlur = allowBlur,
                                            mediaState = mediaState,
                                            vaultState = vaults,
                                            albumsState = albumsState,
                                            metadataState = metadataState,
                                            sharedTransitionScope = this@SharedTransitionLayout,
                                            animatedContentScope = this
                                        )
                                    } else {
                                        val (uri, isVideo) = args
                                        val metadataViewViewModel = injectViewModel { metadataViewViewModel }
                                        val metadataViewState by metadataViewViewModel.state.collectAsStateWithLifecycle()
                                        LaunchedEffect(uri, isVideo) {
                                            metadataViewViewModel.loadMetadata(uri, isVideo)
                                        }
                                        MetadataViewContent(state = metadataViewState)
                                    }
                                }
                            }
                        }
                        BackHandler {
                            // Return to the image from the metadata screen; otherwise close.
                            if (metadataArgs != null) metadataArgs = null else finish()
                        }
                    }
                }
            }
            }
            }
        }
    }

}