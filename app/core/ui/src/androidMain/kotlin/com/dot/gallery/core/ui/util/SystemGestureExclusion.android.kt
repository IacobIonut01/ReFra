/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ui.util

import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.toSize

// The no-argument overload uses boundsInRoot, which clips the rect to every ancestor, so an
// element that reaches past its parent towards the screen edge loses exactly that part.
actual fun Modifier.excludeSystemGestures(): Modifier =
    systemGestureExclusion { coordinates -> Rect(Offset.Zero, coordinates.size.toSize()) }
