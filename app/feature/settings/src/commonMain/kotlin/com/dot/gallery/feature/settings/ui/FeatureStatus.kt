/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.settings.ui


/** The combined state of a feature's model groups, from the user's point of view. */
enum class FeatureStatus {
    /** At least one group is missing and can be downloaded or installed. */
    NOT_INSTALLED,
    DOWNLOADING,
    /** Bundled models are being copied into place. */
    PREPARING,
    READY,
    /** Missing models that this build cannot download, or a tier without published models. */
    UNAVAILABLE,
}
