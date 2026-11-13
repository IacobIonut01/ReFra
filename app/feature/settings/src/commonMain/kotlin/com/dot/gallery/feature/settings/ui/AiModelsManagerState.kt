/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.settings.ui

import com.dot.gallery.core.ml.FaceModel
import com.dot.gallery.core.ml.ModelGroup
import com.dot.gallery.core.ml.SearchModel

data class AiModelsManagerState(
    /** `BuildConfig.ML_MODELS_BUNDLED` — controls install/download wording. */
    val isBundled: Boolean = false,
    val canInstallModels: Boolean = true,
    /** The engine currently powering Smart Search & Categories. */
    val activeSearchModel: SearchModel = SearchModel.DEFAULT,
    /** The face grouping quality tier. */
    val activeFaceModel: FaceModel = FaceModel.DEFAULT,
    val groups: List<AiModelGroupUi> = ModelGroup.entries.map { AiModelGroupUi(group = it) },
    /** "Scan only while charging": background heavy scans wait for the charger. */
    val scanOnlyWhileCharging: Boolean = true,
    /** "Skip hidden albums" — the inverse of the include-ignored-albums setting. */
    val skipHiddenAlbums: Boolean = true,
    /**
     * Embeddings are usable for Categories: the active search model is installed, or a
     * configured handover server answers searches remotely.
     */
    val embeddingsReady: Boolean = false,
) {
    fun groupsOf(feature: IntelligenceFeature): List<ModelGroup> =
        feature.groups(activeSearchModel, activeFaceModel)

    fun summaryOf(feature: IntelligenceFeature): FeatureSummary =
        summarizeFeature(groupsOf(feature), groups, canInstallModels)
}
