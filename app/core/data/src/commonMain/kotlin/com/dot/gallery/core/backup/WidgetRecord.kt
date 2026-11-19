/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.backup

import kotlinx.serialization.Serializable


@Serializable
data class WidgetRecord(
    /** Original SharedPreferences key (`widget_<appWidgetId>`). */
    val name: String = "",
    /** Original `WidgetData` JSON; restored only when the host widget id still exists. */
    val payload: String
)
