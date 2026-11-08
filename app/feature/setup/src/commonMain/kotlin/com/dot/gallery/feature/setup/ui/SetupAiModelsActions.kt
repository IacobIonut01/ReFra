/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.setup.ui

import com.dot.gallery.core.ml.ModelGroup

/** Platform hooks for the AI models page. */
data class SetupAiModelsActions(
    /** Kicks off the download/copy pipeline for one model group. */
    val onDownload: (ModelGroup) -> Unit = {},
    /** Opens a feature's preview page by its Intelligence Hub feature name. */
    val onOpenFeature: (String) -> Unit = {},
)
