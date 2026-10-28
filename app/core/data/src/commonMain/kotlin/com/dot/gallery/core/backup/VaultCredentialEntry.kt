/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.backup

import kotlinx.serialization.Serializable

/**
 * A vault's app-level credential. It is stored only inside password-encrypted archives, so a
 * restored vault keeps the same PIN/pattern/password. Plaintext archives omit this record and the
 * restored vault falls back to the device lock.
 *
 * [credential] is the existing `VaultPasswordManager` value
 * (`TYPE:pbkdf2:<saltHex>:<hashHex>`); it is never the vault media key.
 */
@Serializable
data class VaultCredentialEntry(
    val vaultUuid: String,
    val authType: String,
    val credential: String,
    /** Vault selector gate mode, when the archive carries credentials. */
    val gateMode: String? = null,
    val lockAll: Boolean = false
)
