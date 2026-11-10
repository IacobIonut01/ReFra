/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr.catalog

import kotlinx.serialization.Serializable

/** How an artifact reaches the device: downloaded (or picked from a file), or shipped inside the app. */
@Serializable
enum class OcrArtifactDelivery { DOWNLOAD, BUNDLED }
