/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.picker.ui

/** Selection rules of the picker grid, kept pure so they can be tested without Compose. */
object PickerSelection {

    /**
     * Selection after tapping [id]. Multi-pick toggles it; single pick replaces the current
     * pick, and tapping the picked item again clears it.
     */
    fun tap(selected: Set<Long>, id: Long, allowMultiple: Boolean): Set<Long> = when {
        allowMultiple -> if (id in selected) selected - id else selected + id
        id in selected -> emptySet()
        else -> setOf(id)
    }

    /** Selection after a long press on [id]: it always ends up picked. */
    fun longPress(selected: Set<Long>, id: Long, allowMultiple: Boolean): Set<Long> = when {
        allowMultiple -> selected + id
        else -> setOf(id)
    }

    /** Selection after toggling a whole day: picks every item of it unless all already are. */
    fun toggleDay(selected: Set<Long>, dayIds: Collection<Long>): Set<Long> =
        if (dayIds.isNotEmpty() && selected.containsAll(dayIds)) selected - dayIds.toSet()
        else selected + dayIds
}
