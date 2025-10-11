package com.dot.gallery.feature_node.domain.model

import kotlinx.serialization.Serializable

/**
 * Metadata stored (likely unencrypted) alongside a vault to indicate whether it is
 * transferable (portable) and to hold an integrity hash of the data key.
 * version: schema version (increment if structure changes)
 * transferable: if true, a separate Data Key (DK) is used to encrypt content and can be exported.
 * dkHash: Base64(SHA-256(dataKey)) for integrity / verification when importing.
 * uuid: vault UUID string for cross-checking folder naming vs metadata.
 */
@Serializable
data class VaultInfo(
    val version: Int = 1,
    val transferable: Boolean = false,
    val dkHash: String? = null,
    val uuid: String
)
