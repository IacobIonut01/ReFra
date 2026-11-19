/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ui.mediaview

/**
 * Where a configured quick action can live in the viewer chrome. An action occupies
 * exactly one placement — [ViewerActionBarConfig.sanitized] keeps the top-bar slots authoritative.
 */
enum class ViewerActionPlacement {
    /** The quick-actions pill under the media. */
    BOTTOM,

    /** The slot inside the top bar's leading pill, next to Back. */
    TOP_START,

    /** The slot inside the top bar's trailing pill, next to Info. */
    TOP_END,
}
