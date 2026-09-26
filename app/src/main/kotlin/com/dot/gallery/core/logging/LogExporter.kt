/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.logging

import android.content.Context
import android.content.Intent
import android.net.Uri as AndroidUri
import androidx.core.content.FileProvider
import com.dot.gallery.BuildConfig
import kotlinx.serialization.json.Json
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Turns sanitized entries into share-ready artifacts: markdown clipboard text,
 * a zip diagnostics bundle (device info + JSONL logs + crash logs) shared via
 * FileProvider, and a prefilled GitHub bug-report URL.
 *
 * GitHub rejects new-issue URLs past roughly 8 KB, so inline bodies are capped
 * and full logs always travel via clipboard or the zip.
 */
object LogExporter {

    const val ISSUES_NEW_URL = "https://github.com/IacobIonut01/ReFra/issues/new"
    private const val ISSUE_TEMPLATE = "bug_report.md"
    private const val MAX_URL_LENGTH = 7500
    private const val MAX_INLINE_BODY_CHARS = 3000

    private val json = Json { encodeDefaults = false; ignoreUnknownKeys = true }

    fun clipboardText(
        context: Context,
        entries: List<LogEntry>,
        forGithub: Boolean = false,
    ): String = buildString {
        appendLine("### ReFra diagnostics")
        appendLine()
        appendLine("```")
        appendLine(DiagnosticInfo.asText(context))
        appendLine("```")
        appendLine()
        if (forGithub) {
            appendLine("<details><summary>Logs (${entries.size})</summary>")
            appendLine()
        }
        appendLine("```jsonl")
        entries.forEach { appendLine(it.toJson(json)) }
        appendLine("```")
        if (forGithub) appendLine("</details>")
    }

    fun entryText(entry: LogEntry): String = buildString {
        appendLine("```json")
        appendLine(entry.toJson(json))
        appendLine("```")
    }

    /**
     * Writes `refra-diagnostics-<ts>.zip` into `cacheDir/share/` containing
     * `device-info.txt`, `logs.jsonl` (already-sanitized entries) and any
     * crash logs. Old bundles are swept before writing.
     */
    fun shareBundle(
        context: Context,
        entries: List<LogEntry>,
        crashFiles: List<File> = emptyList(),
    ): File {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val out = File(dir, "refra-diagnostics-$stamp.zip")
        ZipOutputStream(BufferedOutputStream(FileOutputStream(out))).use { zip ->
            zip.putNextEntry(ZipEntry("device-info.txt"))
            zip.write(DiagnosticInfo.asText(context).toByteArray())
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("logs.jsonl"))
            entries.forEach { zip.write((it.toJson(json) + "\n").toByteArray()) }
            zip.closeEntry()

            crashFiles.forEach { f ->
                runCatching {
                    zip.putNextEntry(ZipEntry(f.name))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
        return out
    }

    fun shareUri(context: Context, file: File): AndroidUri =
        FileProvider.getUriForFile(context, BuildConfig.CONTENT_AUTHORITY, file)

    fun shareIntent(context: Context, file: File): Intent {
        val uri = shareUri(context, file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "ReFra diagnostics")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * Prefilled `issues/new` URL. [title] and [body] are URL-encoded; the total
     * URL is capped at [MAX_URL_LENGTH] with a truncation marker — callers put
     * the full payload on the clipboard instead of relying on the URL.
     */
    fun issueUrl(title: String, body: String): String {
        val encodedTitle = urlEncode(title)
        val encodedBody = urlEncode(body)
        val base = "$ISSUES_NEW_URL?template=$ISSUE_TEMPLATE&labels=bug&title=$encodedTitle&body="
        val room = (MAX_URL_LENGTH - base.length).coerceAtLeast(0)
        val bodyPart = if (encodedBody.length <= room) encodedBody
        else encodedBody.take(room - TRUNCATED_ENCODED.length) + TRUNCATED_ENCODED
        return base + bodyPart
    }

    /**
     * Issue body matching `bug_report.md`, with device info filled in and an
     * optional error entry + breadcrumbs embedded when under the inline cap.
     */
    fun issueBody(
        context: Context,
        entry: LogEntry? = null,
        description: String = "",
    ): String = buildString {
        appendLine("**Describe the bug**")
        appendLine(description.ifBlank { "<!-- what happened? -->" })
        appendLine()
        appendLine("**To Reproduce**")
        appendLine("1. ")
        appendLine()
        appendLine("**Expected behavior**")
        appendLine()
        appendLine("**Smartphone:**")
        DiagnosticInfo.snapshot(context).forEach { (k, v) ->
            when (k) {
                "Device" -> appendLine(" - Device: $v")
                "Android" -> appendLine(" - OS: $v")
                "App" -> appendLine(" - Version: $v")
            }
        }
        if (entry != null) {
            appendLine()
            appendLine("**Error (sanitized):**")
            appendLine("```json")
            val block = runCatching { entry.toJson(json) }.getOrDefault("")
            appendLine(block.take(MAX_INLINE_BODY_CHARS))
            appendLine("```")
            entry.crumbs?.takeIf { it.isNotEmpty() }?.let { crumbs ->
                appendLine()
                appendLine("**Breadcrumbs before the error:**")
                appendLine("```")
                crumbs.forEach { c -> appendLine("${c.ts} ${c.label} ${c.ctx.orEmpty()}") }
                appendLine("```")
            }
        }
        appendLine()
        appendLine("*Full logs were copied to the clipboard — paste them here if the file is not attached.*")
    }

    private const val TRUNCATED_ENCODED = "%0A%5Btruncated%5D"

    /**
     * JVM-friendly percent-encoding (`android.net.Uri.encode` is unavailable in
     * local unit tests). `+` is normalized to `%20` since it is only decoded as
     * a space inside `application/x-www-form-urlencoded` contexts.
     */
    internal fun urlEncode(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")
}
