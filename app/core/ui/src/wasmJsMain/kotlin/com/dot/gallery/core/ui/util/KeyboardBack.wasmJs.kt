/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun Modifier.backClosesKeyboard(enabled: Boolean, onBack: () -> Unit): Modifier = this
