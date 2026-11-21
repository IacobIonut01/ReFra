/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.ui.settings

import com.dot.gallery.cloud.core.CloudLibraryScope
import com.dot.gallery.data.dao.PendingDownloadInfo
import com.dot.gallery.data.entity.CloudServerConfigEntity

/**
 * Every callback [CloudProviderSettingsContent] needs. [updateConfig] is the
 * in-place entity transform the ViewModel applies to this account;
 * [pendingDownloadStats] is resolved lazily while the download confirmation
 * dialog is open, exactly like the production `produceState` load.
 */
data class CloudProviderSettingsContentActions(
    val updateConfig: (CloudServerConfigEntity.() -> CloudServerConfigEntity) -> Unit = {},
    val syncNow: () -> Unit = {},
    val triggerDownload: () -> Unit = {},
    val pendingDownloadStats: suspend () -> PendingDownloadInfo = { PendingDownloadInfo(0, 0L) },
    val removeDownloadedCopies: () -> Unit = {},
    val deleteServer: () -> Unit = {},
    val setLibraryScope: (CloudLibraryScope) -> Unit = {},
)
