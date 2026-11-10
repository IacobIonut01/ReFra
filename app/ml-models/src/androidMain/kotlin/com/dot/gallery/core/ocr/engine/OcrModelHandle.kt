/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr.engine

import java.nio.ByteBuffer

/**
 * Where ONNX Runtime reads a verified model from: a file in app storage, or a read-only mapping of a
 * picked document (never copied to storage). [close] releases the mapping once the session exists.
 */
sealed interface OcrModelHandle : AutoCloseable {
    data class Path(val path: String) : OcrModelHandle {
        override fun close() = Unit
    }

    class Buffer(val buffer: ByteBuffer, private val onClose: () -> Unit) : OcrModelHandle {
        override fun close() = onClose()
    }
}
