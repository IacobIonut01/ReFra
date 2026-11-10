/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.viewer.insight

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.dot.gallery.core.insight.TextInsight

/**
 * Viewer-wide selection state. While [selection] is set the viewer is in selection mode: the top bar
 * and quick bar give way to the selection header and bar, and paging and swipe-to-dismiss are off.
 */
@Stable
class TextSelectionHolder {
    var insight by mutableStateOf<TextInsight?>(null)
        private set
    var selection by mutableStateOf<TextSelection?>(null)
        private set

    val active: Boolean get() = selection != null

    fun start(insight: TextInsight, selection: TextSelection = TextSelection.all(insight.lines.size)) {
        this.insight = insight
        this.selection = selection
    }

    fun update(selection: TextSelection) {
        if (insight != null) this.selection = selection
    }

    fun close() {
        insight = null
        selection = null
    }

    val selectedText: String
        get() {
            val lines = insight?.lines?.map { it.text } ?: return ""
            return selection?.text(lines).orEmpty()
        }
}

val LocalTextSelectionHolder = staticCompositionLocalOf<TextSelectionHolder?> { null }
