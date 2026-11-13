/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.settings.ui

data class SettingsGesturesState(
    /** Whether the subject cutout model is installed — gates the "Cut out subject" long-press option. */
    val cutoutModelAvailable: Boolean = true,
)
