/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Lets one back gesture made while the software keyboard is open close the keyboard and run
 * [onBack], instead of the keyboard taking the first gesture on its own. Apply it to the text
 * field that owns the keyboard; it does nothing while the keyboard is closed or [enabled] is
 * false, so the screen's normal back handling runs then. A no-op where the platform has no
 * software keyboard.
 */
@Composable
expect fun Modifier.backClosesKeyboard(enabled: Boolean = true, onBack: () -> Unit): Modifier
