/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.backup

import kotlinx.serialization.Serializable

/** Outcome of restoring one [BackupSection]. */
@Serializable
data class SectionRestoreResult(
    val section: BackupSection,
    val restored: Int = 0,
    val skipped: Int = 0,
    /** Items that matched an existing row and were merged into it. */
    val merged: Int = 0,
    /** Items that could not be applied because the destination already held conflicting data. */
    val conflicts: Int = 0
) {
    val total: Int get() = restored + skipped + conflicts
}
