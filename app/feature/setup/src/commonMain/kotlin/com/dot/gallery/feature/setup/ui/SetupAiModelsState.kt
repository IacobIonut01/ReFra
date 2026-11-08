/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.setup.ui

import com.dot.gallery.core.ml.ModelGroup

/**
 * Display state for the AI models page.
 *
 * [isBundled] reports whether the installed APK packages the model assets (WithML: models
 * are copied rather than downloaded, so they show as ready); [hasInternet] reports whether
 * the app may reach the network — without it the download affordances collapse to a hint.
 */
data class SetupAiModelsState(
    val isBundled: Boolean = false,
    val hasInternet: Boolean = true,
    /** Which engine [search] reports on — the currently selected search model. */
    val searchGroup: ModelGroup = ModelGroup.SEARCH_CLIP,
    val search: SetupModelUi = SetupModelUi(),
    val cutout: SetupModelUi = SetupModelUi(),
    val faceDetect: SetupModelUi = SetupModelUi(),
    val faceRecognition: SetupModelUi = SetupModelUi(),
)
