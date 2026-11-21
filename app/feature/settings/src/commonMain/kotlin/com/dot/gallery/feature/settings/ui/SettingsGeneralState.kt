/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.settings.ui

import androidx.compose.foundation.lazy.LazyListState
import com.dot.gallery.domain.settings.LAUNCH_ROUTE_LIBRARY
import com.dot.gallery.domain.settings.LAUNCH_ROUTE_TIMELINE

data class SettingsGeneralState(
    /** `SdkCompat.supportsTrash` on Android; hides the trash section when false. */
    val supportsTrash: Boolean = true,
    val listState: LazyListState? = null,
    /**
     * Route strings fed by the app shell (`Screen.*()`), so the stored launch-screen value
     * always matches a real navigation route.
     */
    val timelineRoute: String = LAUNCH_ROUTE_TIMELINE,
    val libraryRoute: String = LAUNCH_ROUTE_LIBRARY,
)
