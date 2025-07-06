package com.dot.gallery.feature_node.presentation.util

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.core.SheetDetent

object ViewScreenConstants {
    val BOTTOM_BAR_HEIGHT = 100.dp

    fun ImageOnly(height: () -> Dp = { BOTTOM_BAR_HEIGHT }) =
        SheetDetent("imageOnly") { _, _ ->
            height()
        }

    @Suppress("FunctionName")
    fun FullyExpanded(setHeight: (Dp) -> Unit) =
        SheetDetent("fully-expanded") { containerHeight, sheetHeight ->
            printWarning("Sheet height: $sheetHeight, container height: $containerHeight")
            setHeight(sheetHeight)
            sheetHeight
        }
}