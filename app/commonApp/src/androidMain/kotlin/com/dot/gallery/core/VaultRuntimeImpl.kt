/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core

import android.net.Uri
import androidx.core.net.toUri
import com.dot.gallery.core.work.WorkSignals
import com.dot.gallery.core.work.isFinished
import com.dot.gallery.core.workers.VaultOperationWorker
import com.dot.gallery.core.workers.enqueueVaultOperation
import com.dot.gallery.core.workers.enqueueVaultOperationWithId
import com.dot.gallery.domain.model.Vault
import com.dot.gallery.core.vault.VaultOp
import com.dot.gallery.core.vault.VaultOpOutcome
import com.dot.gallery.core.vault.VaultRuntime
import java.util.UUID
import javax.inject.Inject
import com.dot.gallery.core.injection.Singleton
import dev.brewkits.kmpworkmanager.background.domain.BackgroundTaskScheduler
import dev.brewkits.kmpworkmanager.background.domain.TaskState
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonPrimitive

@Singleton
class VaultRuntimeImpl @Inject constructor(
    private val scheduler: BackgroundTaskScheduler,
) : VaultRuntime {

    override val vaultOpRunning: Flow<Boolean> = flow {
        while (currentCoroutineContext().isActive) {
            val running = scheduler.queryTasks(
                tags = setOf("VaultOp"),
                states = setOf(TaskState.Kind.RUNNING)
            ).isNotEmpty()
            emit(running)
            delay(1_000)
        }
    }

    override val vaultOpProgress: Flow<Float> =
        WorkSignals.progressFlow(VaultOperationWorker.PROGRESS_WORK_NAME)
            .map { it?.get(VaultOperationWorker.KEY_PROGRESS)?.jsonPrimitive?.floatOrNull ?: 0f }

    override suspend fun enqueueVaultOp(
        operation: VaultOp,
        media: List<Uri>,
        vault: Vault?,
        uniqueKey: String?,
        deleteOriginals: Boolean,
    ) {
        scheduler.enqueueVaultOperation(
            operation = operation.wireName,
            media = media,
            vault = vault,
            uniqueKey = uniqueKey ?: UUID.randomUUID().toString(),
            deleteOriginals = deleteOriginals,
        )
    }

    override suspend fun runVaultOp(
        operation: VaultOp,
        media: List<Uri>,
        vault: Vault?,
        uniqueKey: String,
        deleteOriginals: Boolean,
    ): VaultOpOutcome {
        // Large batches arrive as multiple tasks — a single WorkManager-sized input
        // payload can't hold hundreds of URIs (#1240). Await every chunk and merge.
        val ids = scheduler.enqueueVaultOperationWithId(
            operation = operation.wireName,
            media = media,
            vault = vault,
            uniqueKey = uniqueKey,
            deleteOriginals = deleteOriginals,
        )
        var succeeded = 0
        var failedChunks = 0
        var cancelled = false
        val leftovers = mutableListOf<Uri>()
        for (id in ids) {
            val info = WorkSignals.observe(id, VaultOperationWorker.WORKER_NAME)
                .filter { it.isFinished }
                .first()
            when (info.state) {
                is TaskState.Succeeded -> {
                    succeeded += info.output
                        ?.takeIf { it.hasKeyWithValueOfType<Int>(VaultOperationWorker.KEY_SUCCEEDED_COUNT) }
                        ?.getInt(VaultOperationWorker.KEY_SUCCEEDED_COUNT, 0) ?: 0
                    info.output?.getString(VaultOperationWorker.KEY_LEFTOVER_URIS)
                        ?.let { Json.decodeFromString<List<String>>(it).map { s -> s.toUri() } }
                        ?.let { leftovers += it }
                }
                is TaskState.Failed -> failedChunks++
                else -> cancelled = true
            }
        }
        return when {
            succeeded > 0 -> VaultOpOutcome.Succeeded(
                succeededCount = succeeded,
                leftoverUris = leftovers,
            )
            failedChunks > 0 -> VaultOpOutcome.Failed
            cancelled -> VaultOpOutcome.Cancelled
            else -> VaultOpOutcome.Failed
        }
    }

    private val VaultOp.wireName: String
        get() = when (this) {
            VaultOp.ENCRYPT -> VaultOperationWorker.OP_ENCRYPT
            VaultOp.DECRYPT -> VaultOperationWorker.OP_DECRYPT
            VaultOp.HIDE -> VaultOperationWorker.OP_HIDE
            VaultOp.MIGRATE -> VaultOperationWorker.OP_MIGRATE
        }
}
