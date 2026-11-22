package com.dot.gallery.editor.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class GrowingSheetBodyTest {

    private fun body() = GrowingSheetBody(480f).apply { updateLimits(480f, 700f) }

    @Test
    fun opensAtTheBaseHeight() {
        assertEquals(480f, body().targetPx)
    }

    @Test
    fun scrollingTheListGrowsItToTheWindowLimit() {
        val body = body()
        body.onScrolledFromTop(true)
        assertEquals(700f, body.targetPx)
    }

    @Test
    fun scrollingBackToTheTopKeepsItGrownUntilTheSheetCloses() {
        val body = body()
        body.onScrolledFromTop(true)
        body.onScrolledFromTop(false)
        assertEquals(700f, body.targetPx)
        body.reset()
        assertEquals(480f, body.targetPx)
    }

    @Test
    fun aWindowShorterThanTheBaseNeverShrinksIt() {
        val body = GrowingSheetBody(480f).apply { updateLimits(480f, 300f) }
        body.onScrolledFromTop(true)
        assertEquals(480f, body.targetPx)
    }
}
