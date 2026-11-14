/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.mediaview

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression for the viewer quick-action capsule staying chopped after a chrome hide/show:
 * the sheet state is rebuilt on every nav-inset frame, an unmeasured state reports progress 1,
 * and the bar dropped a full height for that frame leaving its blur layer clipped there.
 */
class MeasuredSheetProgressTest {

    @Test
    fun unmeasuredSheetReportsNoProgress() {
        assertEquals(0f, measuredSheetProgress(offsetPx = 0f, progress = 1f), 0f)
        assertEquals(0f, measuredSheetProgress(offsetPx = 0f, progress = 0.4f), 0f)
    }

    @Test
    fun measuredSheetPassesProgressThrough() {
        assertEquals(1f, measuredSheetProgress(offsetPx = 120f, progress = 1f), 0f)
        assertEquals(0.25f, measuredSheetProgress(offsetPx = 120f, progress = 0.25f), 0f)
    }
}
