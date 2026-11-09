/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.viewer.insight

enum class InsightTrigger {
    /** The pager settled on a photo and scanning is automatic. */
    AUTO,

    /** The pager settled but scanning only runs on request: show cached results, start nothing. */
    PASSIVE,

    /** The person tapped Scan. */
    MANUAL,
}
