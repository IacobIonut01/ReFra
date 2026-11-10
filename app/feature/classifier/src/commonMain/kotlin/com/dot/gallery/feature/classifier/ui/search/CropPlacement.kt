/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.classifier.ui.search

/** Where a text-match cell draws its photo: the image size and top-left offset, in pixels. */
internal data class CropPlacement(val width: Float, val height: Float, val x: Float, val y: Float)
