/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.util

import com.dot.gallery.core.location.MapAppearance
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sinh
import kotlin.math.tan

/**
 * Generates a static map tile URL for a given lat/lng.
 * Adds the CARTO basemap key when available.
 */
/** App installs the CARTO basemap key from its BuildConfig at startup. */
object StaticMapDefaults { var apiKey: String = "" }