/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.insight

import java.nio.ByteBuffer

/**
 * An 8-bit luminance image in a direct buffer owned by a [FramePool]. Only [width]×[height] bytes
 * at the start of [buffer] are valid.
 */
class LuminanceFrame internal constructor(val buffer: ByteBuffer) {
    var width: Int = 0
        internal set
    var height: Int = 0
        internal set
}
