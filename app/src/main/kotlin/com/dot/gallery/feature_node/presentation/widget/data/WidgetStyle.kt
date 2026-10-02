/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */
package com.dot.gallery.feature_node.presentation.widget.data

import kotlinx.serialization.Serializable

/**
 * How a media widget renders its picked photo on the home screen (#1269).
 *
 * - [IMAGE]: the photo in colour — the default; widgets configured before
 *   display styles existed decode to this value.
 * - [GRAYSCALE]: the photo desaturated to black & white. Applied at bind time
 *   so the cached bitmap stays canonical colour.
 * - [ICON]: [WidgetData.icon] (an emoji or short label) shown instead of the
 *   photo; the tap still deep-links to the picked photo, so the widget acts
 *   as a minimal shortcut.
 */
@Serializable
enum class WidgetDisplayStyle {
    IMAGE, GRAYSCALE, ICON
}

/**
 * Pure helpers for the widget icon text — kept free of Android types so the
 * rules run in plain JVM tests.
 */
object WidgetStyle {

    /**
     * Code-point ceiling for a stored widget icon. Eight covers the longest
     * practical emoji sequences (person + skin tone + ZWJ joins) while a
     * pasted paragraph can never become the widget face.
     */
    const val MAX_ICON_CODE_POINTS = 8

    /** Tap-to-fill suggestions offered on the style screen. */
    val SUGGESTED_ICONS = listOf("📷", "⭐", "❤️", "🏠", "🖼️", "📌", "🌅", "🎞️")

    /** Icon a just-created [WidgetDisplayStyle.ICON] widget starts with. */
    const val DEFAULT_ICON = "📷"

    /**
     * The icon text stored on a widget, or null when [raw] carries nothing
     * displayable. Strips control characters, trims whitespace, and truncates
     * at [MAX_ICON_CODE_POINTS] on a code-point boundary so a surrogate pair
     * is never split.
     */
    fun sanitizeIcon(raw: String?): String? {
        val cleaned = raw
            ?.filterNot { it.isISOControl() }
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return null
        if (cleaned.codePointCount(0, cleaned.length) <= MAX_ICON_CODE_POINTS) return cleaned
        return cleaned.substring(0, cleaned.offsetByCodePoints(0, MAX_ICON_CODE_POINTS))
    }
}
