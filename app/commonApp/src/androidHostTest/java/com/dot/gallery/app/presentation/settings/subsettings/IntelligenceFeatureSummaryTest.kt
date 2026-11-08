/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.settings.subsettings

import com.dot.gallery.core.ml.FaceModel
import com.dot.gallery.core.ml.ModelGroup
import com.dot.gallery.core.ml.ModelStatus
import com.dot.gallery.core.ml.SearchModel
import com.dot.gallery.feature.settings.ui.AiModelGroupUi
import com.dot.gallery.feature.settings.ui.FeatureStatus
import com.dot.gallery.feature.settings.ui.IntelligenceFeature
import com.dot.gallery.feature.settings.ui.approxBytesOf
import com.dot.gallery.feature.settings.ui.summarizeFeature
import org.junit.Assert.assertEquals
import org.junit.Test

class IntelligenceFeatureSummaryTest {
    private val faceGroups = IntelligenceFeature.FACE_GROUPING.groups(SearchModel.CLIP, FaceModel.STANDARD)

    @Test
    fun faceGroupingIsReadyOnlyWhenBothModelsAre() {
        val partial = listOf(
            AiModelGroupUi(ModelGroup.FACE_DETECT, ModelStatus.READY, installedSize = 10),
            AiModelGroupUi(ModelGroup.FACE_RECOGNITION, ModelStatus.NOT_INSTALLED),
        )
        val summary = summarizeFeature(faceGroups, partial, canInstallModels = true)
        assertEquals(FeatureStatus.NOT_INSTALLED, summary.status)
        assertEquals(listOf(ModelGroup.FACE_RECOGNITION), summary.missingGroups)
        assertEquals(approxBytesOf(ModelGroup.FACE_RECOGNITION), summary.missingBytes)
        assertEquals(10L, summary.installedBytes)
    }

    @Test
    fun downloadsWinAndReportTheirProgress() {
        val states = listOf(
            AiModelGroupUi(ModelGroup.FACE_DETECT, ModelStatus.READY),
            AiModelGroupUi(ModelGroup.FACE_RECOGNITION, ModelStatus.DOWNLOADING, progress = 40f),
        )
        val summary = summarizeFeature(faceGroups, states, canInstallModels = true)
        assertEquals(FeatureStatus.DOWNLOADING, summary.status)
        assertEquals(0.4f, summary.progress, 0.001f)
    }

    @Test
    fun missingModelsWithoutAWayToInstallThemAreUnavailable() {
        val summary = summarizeFeature(listOf(ModelGroup.CUTOUT), emptyList(), canInstallModels = false)
        assertEquals(FeatureStatus.UNAVAILABLE, summary.status)
    }

    @Test
    fun unpublishedFaceTiersHaveNoGroupsAndAreUnavailable() {
        val groups = IntelligenceFeature.FACE_GROUPING.groups(SearchModel.CLIP, FaceModel.ACCURATE)
        assertEquals(FeatureStatus.UNAVAILABLE, summarizeFeature(groups, emptyList(), true).status)
    }

    @Test
    fun smartSearchFollowsTheActiveEngine() {
        assertEquals(
            listOf(ModelGroup.SEARCH_EG2),
            IntelligenceFeature.SMART_SEARCH.groups(SearchModel.EMBEDDING_GEMMA_2, FaceModel.STANDARD)
        )
    }
}
