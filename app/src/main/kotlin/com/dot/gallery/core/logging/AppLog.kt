/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.logging

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.dot.gallery.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.BufferedWriter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Opt-in local diagnostics store. Everything is gated behind [enabled] — when
 * developer mode is off every public call returns after one volatile boolean
 * check, so collection truly does not happen unless the user unlocked it.
 *
 * Storage is JSON Lines under `filesDir/logs/`: one `session-<ts>.jsonl` file
 * per app session plus `crash-<ts>.log` for uncaught exceptions. A single
 * coroutine drains [writeChannel] and owns the open writer; nothing else
 * touches the files.
 *
 * Attribution: [currentScreen] is fed by navigation and stamped on each entry;
 * [scope] is passed explicitly, resolved from [LogScope] in [logScoped], or
 * derived from a dotted tag prefix (`cloud.immich` → scope `cloud.immich`).
 */
object AppLog {

    private const val LOGS_DIR = "logs"
    private const val SESSION_PREFIX = "session-"
    private const val SESSION_SUFFIX = ".jsonl"
    private const val CRASH_PREFIX = "crash-"
    private const val REPORTED_SUFFIX = ".reported"
    private const val SEEN_SUFFIX = ".seen"

    private const val MAX_SESSIONS = 10
    private const val MAX_TOTAL_BYTES = 10L * 1024 * 1024
    private const val MAX_SESSION_AGE_MS = 14L * 24 * 3600 * 1000
    private const val MAX_CRASH_AGE_MS = 30L * 24 * 3600 * 1000
    private const val MAX_CRASH_FILES = 20
    private const val RECENT_CAP = 200
    private const val CRUMB_CAP = 50
    private const val CRUMBS_ON_ERROR = 20
    private const val PRE_INIT_CAP = 500
    private const val READ_CAP = 5000
    private const val WRITE_BATCH = 200

    @Volatile
    var enabled: Boolean = false
        private set

    @Volatile
    var minLevel: LogLevel = LogLevel.DEBUG

    @Volatile
    var breadcrumbsEnabled: Boolean = true

    /** Route name (sans arguments) of the currently visible screen. */
    @Volatile
    var currentScreen: String? = null

    @Volatile
    private var initialized = false

    @Volatile
    private var flagResolved = false

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
    }

    private val logScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeChannel = Channel<LogEntry>(Channel.UNLIMITED)
    private var writerJob: Job? = null
    private var writer: BufferedWriter? = null

    private lateinit var logsDir: File
    private var sessionFile: File? = null

    val sessionId: String =
        SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())

    private val crumbLock = Any()
    private val crumbs = ArrayDeque<Breadcrumb>()

    private val preInitLock = Any()
    private val preInitBuffer = ArrayDeque<LogEntry>()

    private val _recentEntries = MutableStateFlow<List<LogEntry>>(emptyList())
    val recentEntries = _recentEntries.asStateFlow()

    private val _appendFlow = MutableSharedFlow<LogEntry>(extraBufferCapacity = 64)
    val appendFlow: SharedFlow<LogEntry> = _appendFlow

    fun init(context: Context) {
        if (initialized) return
        initDir(File(context.applicationContext.filesDir, LOGS_DIR))
    }

    @VisibleForTesting
    fun initDir(dir: File) {
        if (initialized) return
        logsDir = dir
        logsDir.mkdirs()
        pruneOldFiles()
        sessionFile = File(logsDir, "$SESSION_PREFIX$sessionId$SESSION_SUFFIX")
        initialized = true
        startWriter()
    }

    /** Set by whoever owns the developer-mode preference flow (GalleryApp). */
    fun setEnabled(value: Boolean) {
        enabled = value
        flagResolved = true
        if (value && initialized) {
            startWriter()
            sessionStart()
            synchronized(preInitLock) {
                while (preInitBuffer.isNotEmpty()) {
                    emit(preInitBuffer.removeFirst())
                }
            }
        }
    }

    fun log(
        level: LogLevel,
        tag: String,
        message: String,
        throwable: Throwable? = null,
        ctx: Map<String, String>? = null,
        scope: String? = null,
    ) {
        if (!initialized) return
        if (flagResolved && !enabled) return
        if (level != LogLevel.EVENT && level != LogLevel.CRASH && level.ordinal < minLevel.ordinal) return
        val entry = LogEntry(
            ts = System.currentTimeMillis(),
            level = level.name,
            tag = LogSanitizer.sanitize(tag),
            message = LogSanitizer.sanitize(message),
            ctx = ctx?.let(LogSanitizer::sanitizeContext),
            stackTrace = throwable?.let { LogSanitizer.sanitize(it.stackTraceToString()) },
            session = sessionId,
            screen = currentScreen,
            scope = scope ?: scopeFromTag(tag) ?: currentAmbientLogScope(),
            crumbs = if (level == LogLevel.ERROR || level == LogLevel.CRASH) {
                snapshotCrumbs(CRUMBS_ON_ERROR).ifEmpty { null }
            } else null,
        )
        if (!flagResolved) {
            synchronized(preInitLock) {
                if (preInitBuffer.size < PRE_INIT_CAP) preInitBuffer.addLast(entry)
            }
            return
        }
        emit(entry)
    }

    /** Resolves the ambient [LogScope] for suspend callers. */
    suspend fun logScoped(
        level: LogLevel,
        tag: String,
        message: String,
        throwable: Throwable? = null,
        ctx: Map<String, String>? = null,
    ) = log(level, tag, message, throwable, ctx, scope = currentLogScope())

    fun breadcrumb(label: String, ctx: Map<String, String>? = null, scope: String? = null) {
        if (!initialized || !enabled || !breadcrumbsEnabled) return
        val crumb = Breadcrumb(
            ts = System.currentTimeMillis(),
            label = LogSanitizer.sanitize(label),
            ctx = ctx?.let(LogSanitizer::sanitizeContext),
        )
        synchronized(crumbLock) {
            crumbs.addLast(crumb)
            while (crumbs.size > CRUMB_CAP) crumbs.removeFirst()
        }
        emit(
            LogEntry(
                ts = crumb.ts,
                level = LogLevel.EVENT.name,
                tag = "event",
                message = crumb.label,
                ctx = crumb.ctx,
                session = sessionId,
                screen = currentScreen,
                scope = scope,
            )
        )
    }

    fun setScreen(route: String?) {
        currentScreen = route
    }

    private fun sessionStart() {
        emit(
            LogEntry(
                ts = System.currentTimeMillis(),
                level = LogLevel.EVENT.name,
                tag = "session",
                message = "App session started",
                ctx = mapOf(
                    "version" to "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    "sdk" to android.os.Build.VERSION.SDK_INT.toString(),
                ),
                session = sessionId,
                screen = currentScreen,
            )
        )
    }

    private fun snapshotCrumbs(limit: Int = CRUMB_CAP): List<Breadcrumb> =
        synchronized(crumbLock) { crumbs.toList().takeLast(limit) }

    private fun emit(entry: LogEntry) {
        _recentEntries.update { (it + entry).takeLast(RECENT_CAP) }
        _appendFlow.tryEmit(entry)
        writeChannel.trySend(entry)
    }

    private fun startWriter() {
        if (writerJob?.isActive == true || !initialized) return
        writerJob = logScope.launch {
            while (true) {
                val first = writeChannel.receive()
                val w = ensureWriter() ?: continue
                if (enabled) w.appendLine(first.toJson(json))
                var drained = 0
                while (drained < WRITE_BATCH) {
                    val e = writeChannel.tryReceive().getOrNull() ?: break
                    if (enabled) w.appendLine(e.toJson(json))
                    drained++
                }
                runCatching { w.flush() }
            }
        }
    }

    private fun ensureWriter(): BufferedWriter? {
        writer?.let { return it }
        val file = sessionFile ?: return null
        return runCatching {
            file.parentFile?.mkdirs()
            file.bufferedWriter(charset = Charsets.UTF_8).also { writer = it }
        }.getOrNull()
    }

    private fun closeWriter() {
        runCatching { writer?.flush() }
        runCatching { writer?.close() }
        writer = null
    }

    // --- Reading ---

    fun sessionFiles(): List<File> =
        logsDir.listFiles { f -> f.name.startsWith(SESSION_PREFIX) && f.name.endsWith(SESSION_SUFFIX) }
            ?.sortedByDescending { it.name } ?: emptyList()

    fun crashFiles(): List<File> =
        logsDir.listFiles { f -> f.name.startsWith(CRASH_PREFIX) && f.name.endsWith(".log") }
            ?.sortedByDescending { it.name } ?: emptyList()

    /**
     * Parsed entries across all session files plus crash files rendered as
     * CRASH entries, newest first, capped at [max]. Kept in-memory entries are
     * deliberately not merged — the writer flushes every batch, so lag is
     * invisible, and live appends arrive through [appendFlow].
     */
    fun readEntries(max: Int = READ_CAP): List<LogEntry> {
        if (!initialized) return emptyList()
        val out = ArrayList<LogEntry>(256)
        for (file in sessionFiles()) {
            if (out.size >= max) break
            runCatching {
                file.useLines(Charsets.UTF_8) { lines ->
                    lines.forEach { line ->
                        if (out.size >= max) return@forEach
                        LogEntry.fromJson(json, line)?.let(out::add)
                    }
                }
            }
        }
        crashFiles().mapNotNullTo(out, ::crashEntryFor)
        out.sortByDescending { it.ts }
        return out.take(max)
    }

    private fun crashEntryFor(file: File): LogEntry? = runCatching {
        val text = file.readText(Charsets.UTF_8)
        val firstTrace = text.lineSequence()
            .firstOrNull { it.contains("Exception") || it.contains("Error") }
            ?: "Uncaught exception"
        LogEntry(
            ts = file.name.removePrefix(CRASH_PREFIX).removeSuffix(".log").toLongOrNull()
                ?: file.lastModified(),
            level = LogLevel.CRASH.name,
            tag = "crash",
            message = firstTrace.trim(),
            stackTrace = text,
            session = null,
            screen = null,
            scope = null,
        )
    }.getOrNull()

    fun groupedByArea(entries: List<LogEntry>): List<AreaGroup> =
        entries.groupBy { it.screen ?: it.scope.orEmpty() }
            .map { (key, list) ->
                AreaGroup(
                    id = key.ifEmpty { "ungrouped" },
                    kind = when {
                        key.isEmpty() -> AreaGroup.Kind.UNGROUPED
                        isScopeId(key) -> if (isBackgroundScope(key)) AreaGroup.Kind.BACKGROUND else AreaGroup.Kind.SCOPE
                        else -> AreaGroup.Kind.SCREEN
                    },
                    entries = list,
                )
            }
            .sortedWith(
                compareByDescending<AreaGroup> { it.errorCount > 0 }
                    .thenByDescending { it.lastTs }
            )

    /** `cloud.immich`-style dotted tags double as the scope when none is passed. */
    private fun scopeFromTag(tag: String): String? =
        if (tag.contains('.') && tag.substringBefore('.') in SCOPE_PREFIXES) tag else null

    private fun isScopeId(key: String): Boolean =
        key.substringBefore('.') in SCOPE_PREFIXES

    private fun isBackgroundScope(key: String): Boolean =
        key.substringBefore('.') in BACKGROUND_PREFIXES

    // --- Crash lifecycle ---

    /** Synchronous crash write for [CrashCapture]. No coroutines — the process is dying. */
    fun writeCrashSync(thread: Thread, throwable: Throwable) {
        if (!enabled || !initialized) return
        runCatching {
            val file = File(logsDir, "$CRASH_PREFIX${System.currentTimeMillis()}.log")
            file.bufferedWriter(Charsets.UTF_8).use { w ->
                w.appendLine("ReFra ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                w.appendLine("Thread: ${thread.name}")
                w.appendLine("Screen: ${currentScreen ?: "unknown"}")
                w.appendLine()
                w.appendLine(LogSanitizer.sanitize(throwable.stackTraceToString()))
                w.appendLine()
                w.appendLine("--- breadcrumbs ---")
                snapshotCrumbs().forEach { c ->
                    w.appendLine("${c.ts} ${c.label} ${c.ctx.orEmpty()}")
                }
                w.appendLine("--- recent entries ---")
                recentEntries.value.takeLast(50).forEach { e ->
                    runCatching { w.appendLine(e.toJson(json)) }
                }
            }
        }
    }

    /** Newest crash file the user hasn't been nudged about yet (no `.seen` sidecar). */
    fun unseenCrash(): File? =
        crashFiles().firstOrNull { !File(logsDir, it.name + SEEN_SUFFIX).exists() }

    /** Newest crash file not yet reported upstream (no `.reported` sidecar). */
    fun unreportedCrash(): File? =
        crashFiles().firstOrNull { !File(logsDir, it.name + REPORTED_SUFFIX).exists() }

    fun markCrashSeen(file: File) {
        runCatching { File(logsDir, file.name + SEEN_SUFFIX).createNewFile() }
    }

    fun markCrashReported(file: File) {
        runCatching { File(logsDir, file.name + REPORTED_SUFFIX).createNewFile() }
    }

    fun crashEntryForFile(file: File): LogEntry? = crashEntryFor(file)

    // --- Maintenance ---

    data class Stats(
        val totalBytes: Long,
        val sessionCount: Int,
        val crashCount: Int,
        val unreportedCrash: Boolean,
    )

    fun stats(): Stats {
        if (!initialized) return Stats(0, 0, 0, false)
        val files = logsDir.listFiles()?.filter { it.isFile } ?: emptyList()
        return Stats(
            totalBytes = files.sumOf { it.length() },
            sessionCount = files.count { it.name.startsWith(SESSION_PREFIX) },
            crashCount = files.count { it.name.startsWith(CRASH_PREFIX) && it.name.endsWith(".log") },
            unreportedCrash = unreportedCrash() != null,
        )
    }

    /** Deletes all session and crash files and resets in-memory state. The log stream continues. */
    fun clearLogs() {
        closeWriter()
        runCatching {
            logsDir.listFiles()?.forEach { it.delete() }
        }
        sessionFile = File(logsDir, "$SESSION_PREFIX${sessionId}$SESSION_SUFFIX")
        _recentEntries.value = emptyList()
        synchronized(crumbLock) { crumbs.clear() }
        if (enabled) sessionStart()
    }

    /** Deletes the whole log store. Used when developer mode is disabled. */
    fun wipe() {
        closeWriter()
        runCatching { logsDir.deleteRecursively() }
        logsDir.mkdirs()
        sessionFile = File(logsDir, "$SESSION_PREFIX$sessionId$SESSION_SUFFIX")
        _recentEntries.value = emptyList()
        synchronized(crumbLock) { crumbs.clear() }
        synchronized(preInitLock) { preInitBuffer.clear() }
    }

    fun shutdown() {
        closeWriter()
        writerJob?.cancel()
        writerJob = null
    }

    @VisibleForTesting
    fun resetForTests() {
        shutdown()
        initialized = false
        flagResolved = false
        enabled = false
        minLevel = LogLevel.DEBUG
        breadcrumbsEnabled = true
        currentScreen = null
        sessionFile = null
        _recentEntries.value = emptyList()
        synchronized(crumbLock) { crumbs.clear() }
        synchronized(preInitLock) { preInitBuffer.clear() }
    }

    private fun pruneOldFiles() {
        runCatching {
            val now = System.currentTimeMillis()
            val sessions = sessionFiles()
            sessions.drop(MAX_SESSIONS).forEach { it.delete() }
            sessions.filter { now - it.lastModified() > MAX_SESSION_AGE_MS }.forEach { it.delete() }

            val crashes = crashFiles()
            crashes.drop(MAX_CRASH_FILES).forEach { it.delete() }
            crashes.filter { now - it.lastModified() > MAX_CRASH_AGE_MS }.forEach { it.delete() }

            var total = logsDir.listFiles()?.sumOf { it.length() } ?: 0L
            for (file in sessionFiles().sortedBy { it.name }) {
                if (total <= MAX_TOTAL_BYTES) break
                total -= file.length()
                file.delete()
            }
        }
    }

    private val SCOPE_PREFIXES = setOf(
        "app", "cloud", "decode", "media-ops", "db", "ml", "network", "playback",
        "editor", "ui", "vault", "worker", "job", "service",
    )
    private val BACKGROUND_PREFIXES = setOf("worker", "job", "service")
}
