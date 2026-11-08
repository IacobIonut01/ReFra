package com.dot.gallery.editor.core.time

// Checked Long arithmetic for common Kotlin: no java.lang.Math dependency, so
// the same code runs on Android, desktop and any future KMP target.

internal fun checkedAdd(a: Long, b: Long): Long {
    val result = a + b
    if ((a xor result) < 0L && (b xor result) < 0L) {
        throw ArithmeticException("Long overflow: $a + $b")
    }
    return result
}

internal fun checkedSubtract(a: Long, b: Long): Long {
    val result = a - b
    if ((a xor b) < 0L && (a xor result) < 0L) {
        throw ArithmeticException("Long overflow: $a - $b")
    }
    return result
}

internal fun checkedMultiply(a: Long, b: Long): Long {
    if (a == 0L || b == 0L) return 0L
    if ((a == Long.MIN_VALUE && b == -1L) || (b == Long.MIN_VALUE && a == -1L)) {
        throw ArithmeticException("Long overflow: $a * $b")
    }
    val result = a * b
    if (result / b != a) {
        throw ArithmeticException("Long overflow: $a * $b")
    }
    return result
}
