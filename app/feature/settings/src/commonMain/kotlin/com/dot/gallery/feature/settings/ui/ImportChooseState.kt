/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.settings.ui


/** What step 1 of the restore wizard is waiting on. */
sealed interface ImportChooseState {
    data object NoFile : ImportChooseState
    data object Reading : ImportChooseState
    data class NeedsPassword(val wrongPassword: Boolean) : ImportChooseState
}
