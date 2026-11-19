/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.backup

import kotlinx.serialization.Serializable


@Serializable
data class TimelineSettingsRecord(
    /** JSON-encoded `MediaOrder`, matching the Room converter's representation. */
    val timelineMediaOrder: String,
    val albumMediaOrder: String,
    val groupTimelineByMonth: Boolean,
    val groupTimelineByYear: Boolean,
    val groupTimelineInAlbums: Boolean
)
