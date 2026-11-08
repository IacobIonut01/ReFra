/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.settings.ui

import com.dot.gallery.core.ml.FaceModel
import com.dot.gallery.core.ml.ModelGroup
import com.dot.gallery.core.ml.ModelStatus
import com.dot.gallery.core.ml.SearchModel

/** A user-facing capability of the Intelligence Hub, backed by one or more model groups. */
enum class IntelligenceFeature {
    FACE_GROUPING,
    SMART_SEARCH,
    SUBJECT_CUTOUT;

    /** Model groups this feature needs under the selected search engine and face tier. */
    fun groups(searchModel: SearchModel, faceModel: FaceModel): List<ModelGroup> = when (this) {
        FACE_GROUPING -> faceModel.groups
        SMART_SEARCH -> listOf(searchModel.group)
        SUBJECT_CUTOUT -> listOf(ModelGroup.CUTOUT)
    }

    companion object {
        fun fromName(name: String?): IntelligenceFeature =
            entries.firstOrNull { it.name == name } ?: FACE_GROUPING
    }
}

/** Approximate download size of a model group, shown before anything is downloaded. */
fun approxBytesOf(group: ModelGroup): Long = when (group) {
    ModelGroup.SEARCH_CLIP -> SearchModel.CLIP.approxSizeBytes
    ModelGroup.SEARCH_EG2 -> SearchModel.EMBEDDING_GEMMA_2.approxSizeBytes
    ModelGroup.CUTOUT -> 44L * 1_048_576L
    ModelGroup.FACE_DETECT -> 1_363_149L
    ModelGroup.FACE_RECOGNITION -> 130L * 1_048_576L
}

/** The combined state of a feature's model groups, from the user's point of view. */
enum class FeatureStatus {
    /** At least one group is missing and can be downloaded or installed. */
    NOT_INSTALLED,
    DOWNLOADING,
    /** Bundled models are being copied into place. */
    PREPARING,
    READY,
    /** Missing models that this build cannot download, or a tier without published models. */
    UNAVAILABLE,
}

data class FeatureSummary(
    val status: FeatureStatus,
    /** Download progress of the groups still downloading, 0..1. */
    val progress: Float = 0f,
    /** Bytes the missing groups will download. */
    val missingBytes: Long = 0L,
    /** Bytes the installed groups take on disk. */
    val installedBytes: Long = 0L,
    /** Groups that still need to be downloaded or installed. */
    val missingGroups: List<ModelGroup> = emptyList(),
)

/**
 * Folds the per-group states of [groups] into one feature status. Downloads win over preparation,
 * which wins over missing groups; a feature is ready only when every group is.
 */
fun summarizeFeature(
    groups: List<ModelGroup>,
    states: List<AiModelGroupUi>,
    canInstallModels: Boolean,
): FeatureSummary {
    if (groups.isEmpty()) return FeatureSummary(FeatureStatus.UNAVAILABLE)
    val ui = groups.map { group -> states.firstOrNull { it.group == group } ?: AiModelGroupUi(group = group) }
    val missing = ui.filter { it.status != ModelStatus.READY }
    val downloading = ui.filter { it.status == ModelStatus.DOWNLOADING }
    val status = when {
        downloading.isNotEmpty() -> FeatureStatus.DOWNLOADING
        ui.any { it.status == ModelStatus.COPYING } -> FeatureStatus.PREPARING
        missing.isEmpty() -> FeatureStatus.READY
        canInstallModels -> FeatureStatus.NOT_INSTALLED
        else -> FeatureStatus.UNAVAILABLE
    }
    return FeatureSummary(
        status = status,
        progress = if (downloading.isEmpty()) 0f
            else (downloading.sumOf { it.progress.toDouble() } / downloading.size / 100.0).toFloat().coerceIn(0f, 1f),
        missingBytes = missing.sumOf { approxBytesOf(it.group) },
        installedBytes = ui.filter { it.status == ModelStatus.READY }.sumOf { it.installedSize },
        missingGroups = missing.map { it.group },
    )
}
