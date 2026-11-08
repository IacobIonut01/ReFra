/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.backup

import kotlinx.serialization.Serializable

enum class BackupJobKind { EXPORT, RESTORE }

enum class BackupJobStatus { RUNNING, COMPLETED, PARTIAL, FAILED, CANCELLED }

@Serializable
data class BackupJobProgress(
    val section: BackupSection? = null,
    val current: Int = 0,
    val total: Int = 0,
    val phase: String = ""
)

/**
 * Durable, process-death-safe description of an export/restore run. It deliberately stores only
 * primitives (no Android URIs or passwords); the password lives in a Keystore-wrapped secret file.
 */
@Serializable
data class BackupJobSnapshot(
    val id: String,
    val kind: BackupJobKind,
    val status: BackupJobStatus,
    val createdAt: Long,
    val updatedAt: Long,
    val encrypted: Boolean = false,
    val source: String? = null,
    val destination: String? = null,
    val selection: BackupSelection = BackupSelection(),
    val progress: Map<BackupSection, BackupJobProgress> = emptyMap(),
    val sectionResults: Map<BackupSection, SectionRestoreResult> = emptyMap(),
    val error: String? = null,
    val issueCount: Int = 0,
    val pendingFavoriteCount: Int = 0,
    val exportedCount: Int = 0,
    val payloadBytes: Long = 0L
) {
    val isFinished: Boolean
        get() = status == BackupJobStatus.COMPLETED ||
            status == BackupJobStatus.PARTIAL ||
            status == BackupJobStatus.FAILED ||
            status == BackupJobStatus.CANCELLED
}
