/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.main

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Report
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import com.dot.gallery.R
import com.dot.gallery.core.logging.AppLog
import com.dot.gallery.core.logging.LogExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Post-crash nudge: when developer mode collected an uncaught exception the
 * previous session and it was never surfaced, ask once whether to file it as a
 * GitHub issue. The crash log (already anonymized by [com.dot.gallery.core.logging.LogSanitizer])
 * is copied to the clipboard so it can be pasted into the prefilled issue.
 */
@Composable
fun CrashReportPrompt() {
    if (!AppLog.enabled) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current
    var crashFile by remember { mutableStateOf<File?>(null) }
    LaunchedEffect(Unit) {
        crashFile = withContext(Dispatchers.IO) { AppLog.unseenCrash() }
    }
    val file = crashFile ?: return
    val entry = remember(file) { AppLog.crashEntryForFile(file) }

    fun dismiss() {
        crashFile = null
        scope.launch(Dispatchers.IO) { AppLog.markCrashSeen(file) }
    }

    AlertDialog(
        onDismissRequest = { dismiss() },
        icon = {
            Icon(
                imageVector = Icons.Outlined.Report,
                contentDescription = null
            )
        },
        title = { Text(stringResource(R.string.dev_crash_prompt_title)) },
        text = { Text(stringResource(R.string.dev_crash_prompt_summary)) },
        confirmButton = {
            TextButton(onClick = {
                dismiss()
                scope.launch {
                    withContext(Dispatchers.IO) {
                        AppLog.markCrashReported(file)
                    }
                    entry?.let {
                        clipboard.setText(AnnotatedString(LogExporter.entryText(it)))
                    }
                    val title =
                        "[BUG] Crash: ${entry?.message?.take(50) ?: "uncaught exception"}"
                    uriHandler.openUri(
                        LogExporter.issueUrl(
                            title,
                            LogExporter.issueBody(context, entry)
                        )
                    )
                }
            }) {
                Text(stringResource(R.string.dev_action_report))
            }
        },
        dismissButton = {
            TextButton(onClick = { dismiss() }) {
                Text(stringResource(R.string.dev_crash_prompt_later))
            }
        }
    )
}
