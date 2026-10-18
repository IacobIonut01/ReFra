/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.settings.ui

import com.dot.gallery.core.ml.ModelFileInfo
import com.dot.gallery.core.ml.ModelGroup
import com.dot.gallery.core.ml.SearchModel

data class AiModelsManagerActions(
    val onDownload: (ModelGroup) -> Unit = {},
    val onCancelDownload: (ModelGroup) -> Unit = {},
    val onDelete: (ModelGroup) -> Unit = {},
    /** Switches the engine powering Smart Search & Categories (schedules re-index). */
    val onSelectSearchModel: (SearchModel) -> Unit = {},
    /**
     * Loads per-file details (name, size, SHA-256) for an installed group. Runs
     * off-thread in the app shell — SHA-256 hashing ~130 MB files is too slow
     * for the main thread.
     */
    val loadFileInfos: suspend (ModelGroup) -> List<ModelFileInfo> = { emptyList() },
    /** Opens a model source URL — defaults to `LocalUriHandler` when null. */
    val onOpenUrl: ((String) -> Unit)? = null,
)
