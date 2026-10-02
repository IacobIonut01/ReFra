/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.widget

import com.dot.gallery.feature_node.presentation.widget.data.WidgetData
import com.dot.gallery.feature_node.presentation.widget.data.WidgetDisplayStyle
import com.dot.gallery.feature_node.presentation.widget.data.WidgetStyle
import com.dot.gallery.feature_node.presentation.widget.data.WidgetType
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetStyleTest {

    private val json = Json { ignoreUnknownKeys = true }

    // ── sanitizeIcon ──

    @Test
    fun sanitizeIconNullAndBlankYieldNull() {
        assertNull(WidgetStyle.sanitizeIcon(null))
        assertNull(WidgetStyle.sanitizeIcon(""))
        assertNull(WidgetStyle.sanitizeIcon("   "))
    }

    @Test
    fun sanitizeIconTrimsWhitespace() {
        assertEquals("📷", WidgetStyle.sanitizeIcon("  📷  "))
    }

    @Test
    fun sanitizeIconStripsControlCharacters() {
        assertEquals("⭐", WidgetStyle.sanitizeIcon("⭐\u0000\u0007"))
        assertEquals("⭐", WidgetStyle.sanitizeIcon("\n⭐\n"))
    }

    @Test
    fun sanitizeIconKeepsShortText() {
        assertEquals("AB", WidgetStyle.sanitizeIcon("AB"))
    }

    @Test
    fun sanitizeIconCapsAtMaxCodePoints() {
        // 10 ASCII chars → 8, split on a code-point boundary
        assertEquals("ABCDEFGH", WidgetStyle.sanitizeIcon("ABCDEFGHIJ"))
        // A surrogate pair counts once — the cap must not split it
        val twoEmoji = "📷📷"
        assertEquals(twoEmoji, WidgetStyle.sanitizeIcon(twoEmoji))
    }

    @Test
    fun sanitizeIconKeepsZwjSequence() {
        // Family emoji = 4 codepoints (👨 + ZWJ + 👩 + ZWJ + 👧 ≈ 7) — under the cap
        val family = "👨‍👩‍👧"
        assertEquals(family, WidgetStyle.sanitizeIcon(family))
    }

    // ── WidgetData persistence compatibility ──

    @Test
    fun widgetDataDecodesLegacyPayloadWithoutStyle() {
        // JSON written by versions before display styles existed.
        val legacy = """
            {"widgetId":3,"type":"SINGLE","mediaUris":["content://media/external/images/media/10"],"mediaIds":[10]}
        """.trimIndent()
        val data = json.decodeFromString<WidgetData>(legacy)
        assertEquals(WidgetDisplayStyle.IMAGE, data.displayStyle)
        assertNull(data.icon)
    }

    @Test
    fun widgetDataRoundTripsIconStyle() {
        val data = WidgetData(
            widgetId = 7,
            type = WidgetType.SINGLE,
            mediaUris = listOf("content://media/external/images/media/10"),
            mediaIds = listOf(10L),
            displayStyle = WidgetDisplayStyle.ICON,
            icon = "📷"
        )
        val decoded = json.decodeFromString<WidgetData>(json.encodeToString(data))
        assertEquals(WidgetDisplayStyle.ICON, decoded.displayStyle)
        assertEquals("📷", decoded.icon)
    }

    @Test
    fun widgetDataRoundTripsGrayscaleStyle() {
        val data = WidgetData(
            widgetId = 8,
            type = WidgetType.GRID,
            mediaUris = listOf("a", "b"),
            displayStyle = WidgetDisplayStyle.GRAYSCALE
        )
        val decoded = json.decodeFromString<WidgetData>(json.encodeToString(data))
        assertEquals(WidgetDisplayStyle.GRAYSCALE, decoded.displayStyle)
        assertNull(decoded.icon)
    }
}
