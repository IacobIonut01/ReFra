/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.logging

import android.content.Context
import android.os.Build
import android.os.StatFs
import android.text.format.Formatter
import com.dot.gallery.BuildConfig

/**
 * Anonymized device/app snapshot prepended to every exported bundle and bug
 * report — app version, build flavor, OS, device, ABI, free storage. Never
 * includes locale, accounts, or any user identifier.
 */
object DiagnosticInfo {

    fun snapshot(context: Context): List<Pair<String, String>> {
        val freeBytes = runCatching { StatFs(context.filesDir.path).availableBytes }.getOrNull()
        return buildList {
            add("App" to "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            add("Build" to listOfNotNull(
                BuildConfig.FLAVOR.takeIf { it.isNotBlank() },
                BuildConfig.BUILD_TYPE
            ).joinToString("-"))
            add("Device" to "${Build.MANUFACTURER} ${Build.MODEL}")
            add("Android" to "${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            add("ABI" to Build.SUPPORTED_ABIS.joinToString(", "))
            if (freeBytes != null) {
                add("Free storage" to Formatter.formatShortFileSize(context, freeBytes))
            }
        }
    }

    fun asText(context: Context): String =
        snapshot(context).joinToString("\n") { (k, v) -> "$k: $v" }
}
