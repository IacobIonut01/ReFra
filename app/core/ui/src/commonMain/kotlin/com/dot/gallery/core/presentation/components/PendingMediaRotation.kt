/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components

import androidx.compose.runtime.Immutable


@Immutable
data class PendingMediaRotation(val degrees: Int, val phase: Phase) {
    enum class Phase { Queued, Processing, Done, Failed }
}
