/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.timeline.components

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.dot.gallery.cloud.sync.CloudUploadWorker
import com.dot.gallery.cloud.ui.backup.UploadDetailsUiState

internal class TimelineBackupPreviewProvider : PreviewParameterProvider<UploadDetailsUiState> {
    override val values = sequenceOf(
        UploadDetailsUiState(),
        UploadDetailsUiState(isWorkerRunning = true),
        UploadDetailsUiState(
            isWorkerRunning = true,
            phase = CloudUploadWorker.PHASE_UPLOADING,
            totalItems = 100,
            completedItems = 10,
        ),
        UploadDetailsUiState(
            isWorkerRunning = true,
            phase = CloudUploadWorker.PHASE_VERIFYING,
            totalItems = 100,
            checkedItems = 40,
        ),
        UploadDetailsUiState(
            isWorkerRunning = true,
            phase = CloudUploadWorker.PHASE_UPLOADING,
            totalItems = 10,
            completedItems = 4,
            failedItems = 2,
        ),
    )
}
