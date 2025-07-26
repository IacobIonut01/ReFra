/**
 * Copyright 2023 Viacheslav Barkov
 */

package com.dot.gallery.feature_node.presentation.search.util

import kotlin.math.sqrt

infix fun FloatArray.dot(other: FloatArray) =
    foldIndexed(0.0) { i, acc, cur -> acc + cur * other[i] }.toFloat()

fun normalizeL2(inputArray: FloatArray): FloatArray {
    var norm = 0.0f
    for (i in inputArray.indices) {
        norm += inputArray[i] * inputArray[i]
    }
    norm = sqrt(norm)
    return inputArray.map { it / norm }.toFloatArray()
}