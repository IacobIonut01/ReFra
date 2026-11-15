/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.data.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "smart_scan_runs",
    indices = [
        Index(value = ["status"]),
        Index(value = ["requestedAt"]),
        Index(value = ["leaseExpiresAt"])
    ]
)
data class SmartScanRunEntity(
    @PrimaryKey
    val runId: String,
    val trigger: SmartScanTrigger,
    @ColumnInfo(defaultValue = "0")
    val requestedFeatures: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val userVisible: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val fullRefresh: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val fullRefreshFeatures: Int = 0,
    val workId: String? = null,
    val currentPhase: SmartScanPhase? = null,
    @ColumnInfo(defaultValue = "'queued'")
    val status: SmartScanStatus = SmartScanStatus.QUEUED,
    val requestedAt: Long,
    val startedAt: Long? = null,
    val finishedAt: Long? = null,
    val updatedAt: Long = requestedAt,
    val leaseOwner: String? = null,
    val leaseExpiresAt: Long? = null,
    @ColumnInfo(defaultValue = "''")
    val sourceSnapshot: String = "",
    @ColumnInfo(defaultValue = "0")
    val totalMedia: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val processedMedia: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val succeededMedia: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val skippedMedia: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val failedMedia: Int = 0,
    val lastErrorCode: String? = null
)
