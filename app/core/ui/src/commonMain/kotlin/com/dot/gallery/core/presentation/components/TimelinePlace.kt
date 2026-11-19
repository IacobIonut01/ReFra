/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components

import androidx.compose.runtime.Immutable


/** A place named in the timeline: the city a photo was taken in, with its country for lookups. */
@Immutable
data class TimelinePlace(val city: String, val country: String)
