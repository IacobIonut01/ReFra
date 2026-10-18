/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.settings.ui

import com.dot.gallery.core.ml.ModelGroup
import com.dot.gallery.core.ml.SearchModel

data class AiModelsManagerState(
    /** `BuildConfig.ML_MODELS_BUNDLED` — controls install/download wording. */
    val isBundled: Boolean = false,
    val canInstallModels: Boolean = true,
    /** The engine currently powering Smart Search & Categories. */
    val activeSearchModel: SearchModel = SearchModel.DEFAULT,
    val groups: List<AiModelGroupUi> = ModelGroup.entries.map { AiModelGroupUi(group = it) },
)
