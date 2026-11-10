/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.domain.uri


/** Unicode NFKC normalization — `java.text.Normalizer` on Android, JS `String.normalize` on wasmJs. */
expect fun String.normalizeNfkc(): String

/** Unicode NFC normalization (canonical composition). */
expect fun String.normalizeNfc(): String

/** Unicode NFD normalization (canonical decomposition). */
expect fun String.normalizeNfd(): String
