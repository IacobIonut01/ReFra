/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.insight

/**
 * Barcode symbologies, with the bit zxing-cpp uses for each in `BarcodeFormat`, so the native
 * reader can take a mask built from these directly.
 */
enum class CodeFormat(val bit: Int, val isLinear: Boolean, val displayName: String) {
    AZTEC(1 shl 0, false, "Aztec"),
    CODABAR(1 shl 1, true, "Codabar"),
    CODE_39(1 shl 2, true, "Code 39"),
    CODE_93(1 shl 3, true, "Code 93"),
    CODE_128(1 shl 4, true, "Code 128"),
    DATA_BAR(1 shl 5, true, "GS1 DataBar"),
    DATA_BAR_EXPANDED(1 shl 6, true, "GS1 DataBar Expanded"),
    DATA_MATRIX(1 shl 7, false, "Data Matrix"),
    EAN_8(1 shl 8, true, "EAN-8"),
    EAN_13(1 shl 9, true, "EAN-13"),
    ITF(1 shl 10, true, "ITF"),
    MAXICODE(1 shl 11, false, "MaxiCode"),
    PDF_417(1 shl 12, false, "PDF417"),
    QR_CODE(1 shl 13, false, "QR code"),
    UPC_A(1 shl 14, true, "UPC-A"),
    UPC_E(1 shl 15, true, "UPC-E"),
    MICRO_QR_CODE(1 shl 16, false, "Micro QR code"),
    RMQR_CODE(1 shl 17, false, "rMQR code"),
    DX_FILM_EDGE(1 shl 18, true, "DX film edge"),
    DATA_BAR_LIMITED(1 shl 19, true, "GS1 DataBar Limited");

    val isRetailProduct: Boolean
        get() = this == EAN_8 || this == EAN_13 || this == UPC_A || this == UPC_E

    val isQrFamily: Boolean
        get() = this == QR_CODE || this == MICRO_QR_CODE || this == RMQR_CODE

    companion object {
        fun fromBit(bit: Int): CodeFormat? = entries.firstOrNull { it.bit == bit }

        fun mask(formats: Collection<CodeFormat>): Int = formats.fold(0) { acc, f -> acc or f.bit }

        /** Groups the settings expose. The stored value is the group name. */
        val groups: Map<String, Set<CodeFormat>> = linkedMapOf(
            "qr" to setOf(QR_CODE, MICRO_QR_CODE, RMQR_CODE),
            "matrix" to setOf(DATA_MATRIX, AZTEC, PDF_417, MAXICODE),
            "product" to setOf(EAN_8, EAN_13, UPC_A, UPC_E),
            "linear" to setOf(
                CODABAR, CODE_39, CODE_93, CODE_128, ITF, DATA_BAR, DATA_BAR_EXPANDED,
                DATA_BAR_LIMITED,
            ),
        )

        val DEFAULT_GROUPS: Set<String> = groups.keys

        /** The reader mask for a set of stored group names; unknown names are ignored. */
        fun maskForGroups(groupNames: Set<String>): Int =
            mask(groupNames.flatMap { groups[it].orEmpty() })
    }
}
