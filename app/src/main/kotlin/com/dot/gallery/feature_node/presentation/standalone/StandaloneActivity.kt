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
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.feature_node.presentation.mediaview.MediaViewScreen
import com.dot.gallery.feature_node.presentation.util.toggleOrientation
import com.dot.gallery.ui.theme.GalleryTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class StandaloneActivity : ComponentActivity() {

    @OptIn(ExperimentalSharedTransitionApi::class)
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
                Scaffold { paddingValues ->
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
                                    navigateUp = { finish() },
                                    toggleRotate = ::toggleOrientation,
                                    paddingValues = paddingValues,
                                    isStandalone = true,
                                    mediaId = viewModel.mediaId,
                                    mediaState = mediaState,
                                    handler = viewModel.handler,
                                    addMedia = viewModel::addMedia,
                                    vaultState = vaults,
                                    navigate = {},
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