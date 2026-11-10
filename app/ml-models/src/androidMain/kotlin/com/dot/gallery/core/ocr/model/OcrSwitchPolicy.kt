/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr.model

/** The master switch can be on only while at least one enabled pack is ready. */
object OcrSwitchPolicy {
    fun canEnable(readyPackIds: Set<String>): Boolean = readyPackIds.isNotEmpty()

    /** The value to store: a request to turn on with no ready pack stays off. */
    fun effective(requested: Boolean, readyPackIds: Set<String>): Boolean = requested && canEnable(readyPackIds)

    /** Packs recognition uses: those the person enabled that are ready, in a stable order. */
    fun activePacks(enabled: Set<String>, readyPackIds: Set<String>): List<String> =
        enabled.filter { it in readyPackIds }.sorted()
}
