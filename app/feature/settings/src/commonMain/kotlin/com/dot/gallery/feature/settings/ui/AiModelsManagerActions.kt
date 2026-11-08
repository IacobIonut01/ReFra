/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.settings.ui

import com.dot.gallery.core.ml.FaceModel
import com.dot.gallery.core.ml.ModelFileInfo
import com.dot.gallery.core.ml.ModelGroup
import com.dot.gallery.core.ml.SearchModel

data class AiModelsManagerActions(
    val onDownload: (ModelGroup) -> Unit = {},
    val onCancelDownload: (ModelGroup) -> Unit = {},
    val onDelete: (ModelGroup) -> Unit = {},
    /** Switches the engine powering Smart Search & Categories (schedules re-index). */
    val onSelectSearchModel: (SearchModel) -> Unit = {},
    /** Switches the face grouping tier (re-groups everyone once its models are installed). */
    val onSelectFaceModel: (FaceModel) -> Unit = {},
    val onScanOnlyWhileChargingChange: (Boolean) -> Unit = {},
    val onSkipHiddenAlbumsChange: (Boolean) -> Unit = {},
    /** Opens a feature's preview page from the hub. */
    val onOpenFeature: (IntelligenceFeature) -> Unit = {},
    /** Starts a people scan once face grouping is installed. */
    val onScanForPeople: () -> Unit = {},
    /** Leaves a preview opened from setup or People without downloading. Null hides "Not now". */
    val onNotNow: (() -> Unit)? = null,
    /**
     * Loads per-file details (name, size, SHA-256) for an installed group. Runs
     * off-thread in the app shell — SHA-256 hashing ~130 MB files is too slow
     * for the main thread.
     */
    val loadFileInfos: suspend (ModelGroup) -> List<ModelFileInfo> = { emptyList() },
    /** Opens a model source URL — defaults to `LocalUriHandler` when null. */
    val onOpenUrl: ((String) -> Unit)? = null,
)
