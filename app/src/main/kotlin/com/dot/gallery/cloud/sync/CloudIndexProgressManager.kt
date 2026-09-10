/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.sync

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.dot.gallery.R
import com.dot.gallery.core.Resource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks the progress of caching ("indexing") each cloud account's remote media into Room —
 * the background prefetch performed by [com.dot.gallery.cloud.di.CloudProviderInitializer] that
 * makes an account's photos appear in the timeline/albums.
 *
 * Exposes a reactive [state] for in-app UI (the Backup & Sync dashboard) and mirrors the same
 * progress into a SILENT, low-importance, ongoing notification so the user can monitor indexing
 * from the shade without being interrupted. The notification is removed automatically once every
 * account finishes indexing.
 */
@Singleton
class CloudIndexProgressManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    /** Live indexing status for a single cloud account. */
    data class AccountIndex(
        val configId: Long,
        val label: String,
        val indexedCount: Int
    )

    data class IndexState(
        val active: Map<Long, AccountIndex> = emptyMap()
    ) {
        val isIndexing: Boolean get() = active.isNotEmpty()
        val totalIndexed: Int get() = active.values.sumOf { it.indexedCount }
        val accountCount: Int get() = active.size
        /** Label of one currently-indexing account (the first), for a compact one-line summary. */
        val primaryLabel: String get() = active.values.firstOrNull()?.label ?: ""
    }

    private val _state = MutableStateFlow(IndexState())
    val state: StateFlow<IndexState> = _state.asStateFlow()

    init {
        cancelNotification()
    }

    /** Marks [configId] as started indexing and (re)posts the notification. */
    @Synchronized
    fun start(configId: Long, label: String) {
        _state.value = IndexState(
            _state.value.active + (configId to AccountIndex(configId, label, 0))
        )
        postNotification()
    }

    /** Publishes the running indexed count for [configId]. */
    @Synchronized
    fun update(configId: Long, indexedCount: Int, label: String) {
        _state.value = IndexState(
            _state.value.active + (configId to AccountIndex(configId, label, indexedCount))
        )
        postNotification()
    }

    /** Marks [configId] as finished; cancels the notification when nothing is left indexing. */
    @Synchronized
    fun finish(configId: Long) {
        _state.value = IndexState(_state.value.active - configId)
        if (_state.value.isIndexing) postNotification() else cancelNotification()
    }

    private fun ensureChannel(): String {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_INDEX) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_INDEX,
                    context.getString(R.string.cloud_index_channel),
                    NotificationManager.IMPORTANCE_LOW
                ).apply { setShowBadge(false) }
            )
        }
        return CHANNEL_INDEX
    }

    private fun hasPostPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun postNotification() {
        if (!hasPostPermission()) return
        val state = _state.value
        if (!state.isIndexing) return
        val channelId = ensureChannel()
        val text = if (state.accountCount > 1) {
            context.getString(R.string.cloud_index_progress_multi, state.accountCount, state.totalIndexed)
        } else {
            context.getString(R.string.cloud_index_progress_text, state.primaryLabel, state.totalIndexed)
        }
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_cloud_upload)
            .setContentTitle(context.getString(R.string.cloud_index_notification_title))
            .setContentText(text)
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setTimeoutAfter(INDEX_NOTIFICATION_TIMEOUT_MILLIS)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_INDEX, notification)
        }
    }

    private fun cancelNotification() {
        runCatching {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_INDEX)
        }
    }

    private companion object {
        const val CHANNEL_INDEX = "cloud_index_progress"
        const val NOTIFICATION_ID_INDEX = 91003
        const val INDEX_NOTIFICATION_TIMEOUT_MILLIS = 5 * 60_000L
    }
}

internal fun shouldStartCloudIndex(effectiveOffline: Boolean): Boolean = !effectiveOffline

internal suspend fun <T> fetchCloudIndexPage(
    timeoutMillis: Long,
    fetch: suspend () -> T
): T = withTimeout(timeoutMillis) { fetch() }

internal suspend fun <T> fetchAllCloudIndexPages(
    pageSize: Int,
    maxPages: Int,
    fetchPage: suspend (page: Int, pageSize: Int) -> Resource<List<T>>,
    onPage: suspend (items: List<T>, total: Int) -> Unit = { _, _ -> }
): Result<Int> {
    require(pageSize > 0)
    require(maxPages > 0)
    var total = 0
    repeat(maxPages) { page ->
        when (val resource = fetchPage(page, pageSize)) {
            is Resource.Error -> return Result.failure(
                IllegalStateException(resource.message ?: "Cloud index page ${page + 1} failed")
            )
            is Resource.Success -> {
                val items = resource.data.orEmpty()
                total += items.size
                onPage(items, total)
                if (items.size < pageSize) return Result.success(total)
            }
        }
    }
    return Result.failure(IllegalStateException("Cloud index exceeded $maxPages pages"))
}
