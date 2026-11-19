/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.backup


/** What kind of archive a picked file is. */
enum class BackupFormat {
    /** Legacy schema v1/v2 plaintext ZIP. */
    LEGACY_ZIP,
    /** Legacy schema v1/v2 whole-archive encrypted stream. */
    LEGACY_ENCRYPTED,
    /** Schema v3 archive with a plaintext preview header. */
    SECTIONED
}
