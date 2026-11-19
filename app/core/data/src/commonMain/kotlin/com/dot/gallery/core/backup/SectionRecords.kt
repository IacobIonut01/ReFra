/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.backup

import kotlinx.serialization.Serializable

/*
 * One serializable record type per line in `sections/<section>.jsonl`.
 * Records reference media/albums through portable refs (see MediaRefs.kt) and never carry
 * device-local ids as authoritative identity. Ids that only link records inside the archive
 * (category/collection/group/section ids) are kept as `sourceId` so restore can remap them.
 */

// ── SETTINGS ────────────────────────────────────────────────────────────────────────────────────

@Serializable
data class SettingsRecord(val name: String, val value: SettingValue)
