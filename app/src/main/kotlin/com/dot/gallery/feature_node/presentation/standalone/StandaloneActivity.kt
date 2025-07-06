/*
 * SPDX-FileCopyrightText: 2023 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.standalone

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.core.MediaDistributor
import com.dot.gallery.core.MediaHandler
import com.dot.gallery.core.MediaSelector
import com.dot.gallery.core.util.SetupMediaProviders
import com.dot.gallery.feature_node.domain.util.EventHandler
import com.dot.gallery.feature_node.presentation.mediaview.MediaViewScreen
import com.dot.gallery.feature_node.presentation.util.LocalHazeState
import com.dot.gallery.feature_node.presentation.util.toggleOrientation
import com.dot.gallery.ui.theme.GalleryTheme
import dagger.hilt.android.AndroidEntryPoint
import dev.chrisbanes.haze.LocalHazeStyle
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.chrisbanes.haze.rememberHazeState
import javax.inject.Inject

@AndroidEntryPoint
class StandaloneActivity : ComponentActivity() {

    @Inject
    lateinit var eventHandler: EventHandler
    @Inject
    lateinit var mediaDistributor: MediaDistributor
    @Inject
    lateinit var mediaHandler: MediaHandler
    @Inject
    lateinit var mediaSelector: MediaSelector

    @OptIn(ExperimentalSharedTransitionApi::class, ExperimentalHazeMaterialsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        enableEdgeToEdge()
        val action = intent.action.toString()
        val isSecure = action.lowercase().contains("secure")
        val clipData = intent.clipData
        val uriList = mutableSetOf<Uri>()
        intent.data?.let(uriList::add)
        if (clipData != null) {
            for (i in 0 until clipData.itemCount) {
                uriList.add(clipData.getItemAt(i).uri)
            }
        }
        setShowWhenLocked(isSecure)
        setContent {
            GalleryTheme {
                val hazeState = rememberHazeState()
                CompositionLocalProvider(
                    LocalHazeState provides hazeState,
                    LocalHazeStyle provides HazeMaterials.thin(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    SetupMediaProviders(
                        eventHandler = eventHandler,
                        mediaDistributor = mediaDistributor,
                        mediaHandler = mediaHandler,
                        mediaSelector = mediaSelector
                    ) {
                        Scaffold(
                            modifier = Modifier.hazeSource(LocalHazeState.current)
                        ) { paddingValues ->
                            val viewModel = hiltViewModel<StandaloneViewModel>()
                            LaunchedEffect(Unit) {
                                viewModel.reviewMode = action.lowercase().contains("review")
                                viewModel.dataList = uriList.toList()
                            }
                            val vaults = viewModel.vaults.collectAsStateWithLifecycle()
                            val mediaState = viewModel.mediaState.collectAsStateWithLifecycle()
                            val staticState by remember { mutableStateOf(true) }
                            SharedTransitionLayout {
                                AnimatedContent(staticState, label = "standalone") { staticState ->
                                    if (staticState) {
                                        MediaViewScreen(
                                            toggleRotate = ::toggleOrientation,
                                            paddingValues = paddingValues,
                                            isStandalone = true,
                                            mediaId = viewModel.mediaId,
                                            mediaState = mediaState,
                                            addMedia = viewModel::addMedia,
                                            vaultState = vaults,
                                            sharedTransitionScope = this@SharedTransitionLayout,
                                            animatedContentScope = this
                                        )
                                    }
                                }
                            }
                        }
                        BackHandler {
                            finish()
                        }
                    }
                }
            }
        }
    }

}