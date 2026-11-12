/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ui.util

import androidx.compose.ui.Modifier

/**
 * Keeps the system's edge gestures, such as back on gesture navigation, off this element, so a
 * drag that starts on it near the screen edge reaches the app. A no-op where the platform has
 * no such gestures.
 */
expect fun Modifier.excludeSystemGestures(): Modifier
