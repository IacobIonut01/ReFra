/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.settings.ui

import com.dot.gallery.core.ml.ModelGroup


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
