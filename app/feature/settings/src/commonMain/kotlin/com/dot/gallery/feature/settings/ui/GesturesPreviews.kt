/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.settings.ui

import androidx.compose.runtime.Composable

/**
 * Live preview slots for the gestures settings detail panes. The Android shell
 * supplies the real mocks; other platforms or docs scenes get empty slots — the
 * surrounding detail screen still renders.
 */
data class GesturesPreviews(
    val tapNavigation: @Composable (Boolean) -> Unit = {},
)
