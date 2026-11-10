/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.viewer.insight

import androidx.compose.runtime.staticCompositionLocalOf
import com.dot.gallery.core.ocr.engine.PpOcrV5Engine
import com.dot.gallery.core.ocr.model.OcrPackStore
import com.dot.gallery.core.ocr.repository.OcrRepository

/**
 * The app's OCR singletons, provided to the viewer by the host (null in previews and docs captures).
 * [repository] lets indexed photos show their lines at once, without reading them again.
 */
class OcrRuntime(val store: OcrPackStore, val engine: PpOcrV5Engine, val repository: OcrRepository? = null)

val LocalOcrRuntime = staticCompositionLocalOf<OcrRuntime?> { null }
