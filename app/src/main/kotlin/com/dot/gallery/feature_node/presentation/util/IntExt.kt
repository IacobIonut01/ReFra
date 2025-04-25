package com.dot.gallery.feature_node.presentation.util

import androidx.compose.runtime.Stable
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

@Stable
fun Int.roundDpToPx(density: Density) = with(density) { dp.roundToPx() }

@Stable
fun Int.roundSpToPx(density: Density) = with(density) { sp.roundToPx() }

fun Float.normalize(
    minValue: Float,
    maxValue: Float = 1f,
    minNormalizedValue: Float = 0f,
    maxNormalizedValue: Float = 1f
): Float {
    return ((this - minValue) / (maxValue - minValue)).coerceIn(
        minNormalizedValue,
        maxNormalizedValue
    )
}

/**
 * Format this number with a '.' as thousands separator, e.g.
 *   999    -> "999"
 *   1000   -> "1.000"
 *   1000000-> "1.000.000"
 */
fun Long.toGroupedString(): String {
    if (this <= 999) return toString()
    val symbols = DecimalFormatSymbols(Locale.getDefault()).apply {
        groupingSeparator = '.'
        decimalSeparator = ','
    }
    val df = DecimalFormat("#,###", symbols)
    return df.format(this)
}

/** Delegate to Long version */
fun Int.toGroupedString(): String = this.toLong().toGroupedString()

/**
 * Scale this bitrate (in bits per second) to the highest unit (bps, kbps, Mbps, Gbps, Tbps)
 * and format with one decimal digit, e.g.:
 *  - 800       → "800 bps"
 *  - 1500      → "1.5 kbps"
 *  - 2_500_000 → "2.5 Mbps"
 */
fun Long.toBitrateString(): String {
    val units = arrayOf("bps", "kbps", "Mbps", "Gbps", "Tbps")
    var value = this.toDouble()
    var idx = 0
    while (value >= 1000 && idx < units.lastIndex) {
        value /= 1000
        idx++
    }
    // one decimal, but drop “.0” if integer
    val pattern = if (value % 1.0 == 0.0) "#0" else "#0.#"
    val dfs = DecimalFormatSymbols(Locale.getDefault()).apply {
        decimalSeparator = ','
        groupingSeparator = '.'
    }
    val df = DecimalFormat(pattern, dfs)
    return "${df.format(value)} ${units[idx]}"
}

fun Int.toBitrateString(): String = this.toLong().toBitrateString()