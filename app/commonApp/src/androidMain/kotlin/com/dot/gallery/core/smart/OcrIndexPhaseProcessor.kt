/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.smart

import android.content.Context
import com.dot.gallery.core.Settings
import com.dot.gallery.core.activeDataStore
import com.dot.gallery.core.logging.printWarn
import com.dot.gallery.core.ocr.engine.OcrRequestPriority
import com.dot.gallery.core.ocr.engine.PpOcrV5Engine
import com.dot.gallery.core.ocr.engine.retryWhenPreempted
import com.dot.gallery.core.ocr.model.OcrPackStore
import com.dot.gallery.core.ocr.model.OcrSwitchPolicy
import com.dot.gallery.core.ocr.repository.OcrRepository
import com.dot.gallery.data.InternalDatabase
import com.dot.gallery.data.entity.MediaFeature
import com.dot.gallery.data.entity.MediaFeatureStateEntity
import com.dot.gallery.data.entity.MediaFeatureStatus
import com.dot.gallery.data.entity.SmartScanPhase
import com.dot.gallery.data.repository.MediaRepository
import com.dot.gallery.domain.model.UriMedia
import com.dot.gallery.domain.settings.DataStoreSettingsStore
import com.dot.gallery.domain.settings.SettingsKeys
import com.dot.gallery.domain.util.isEncrypted
import com.dot.gallery.feature.viewer.insight.decodeForTextRecognition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import javax.inject.Inject

/**
 * Which media the text indexer reads: library photos only. Vault media is never a candidate (it is
 * neither in the MediaStore mirror nor in the cloud cache, and is filtered here as well), and cloud
 * photos only for providers the person chose to index on this device.
 */
internal fun ocrIndexCandidate(media: UriMedia, indexableProviders: Set<String>): Boolean =
    !media.isEncrypted && media.mimeType.startsWith("image/") && media.duration == null &&
        cloudIndexingAllowed(media.uri.toString(), indexableProviders)

/**
 * The OCR_INDEX phase: reads library photos with the person's packs at background priority, on the
 * same charging and idle constraints as the other heavy phases, and stores the text for Search and
 * for the viewer. Runs only while Text recognition and Find photos by their text are on with a ready
 * pack; otherwise it reports Blocked and reads nothing.
 */
class OcrIndexPhaseProcessor @Inject constructor(
    repository: MediaRepository,
    database: InternalDatabase,
    private val store: OcrPackStore,
    private val ocrRepository: OcrRepository,
    private val engine: PpOcrV5Engine,
    private val appContext: Context,
) : MediaPhaseProcessor(repository, database) {
    private val settings by lazy { DataStoreSettingsStore(appContext.activeDataStore) }

    override val phase = SmartScanPhase.OCR_INDEX

    override val revision: String
        get() = runBlocking(Dispatchers.IO) { revisionFor(readSettings()) }

    override suspend fun process(context: SmartScanPhaseContext): SmartScanPhaseResult {
        val initial = readSettings()
        if (!initial.enabled || !initial.indexLibrary) return SmartScanPhaseResult.Blocked("ocr_off")
        if (!initial.active) return SmartScanPhaseResult.Blocked("ocr_pack_unavailable")
        val packIds = initial.packs
        val resultRevision = revisionFor(initial)
        val scanDao = database.getSmartScanDao()
        val now = System.currentTimeMillis()
        val states = scanDao.getFeatureStates(MediaFeature.OCR).associateBy { it.mediaId }
        val indexableProviders = Settings.SmartFeatures.indexOnDeviceProviders(appContext).first()
        val pool = media().filter { ocrIndexCandidate(it, indexableProviders) }
        // Rows whose photo left the library (deleted, moved to the vault, provider removed) go too.
        val poolIds = pool.mapTo(hashSetOf()) { it.id }
        val stale = states.keys.filterNot(poolIds::contains)
        if (stale.isNotEmpty()) ocrRepository.delete(stale)

        val pending = mutableListOf<MediaFeatureStateEntity>()
        val candidates = mutableListOf<UriMedia>()
        for (item in pool) {
            val source = OcrRepository.sourceRevision(item)
            val decision = prepareFeatureWork(
                existing = states[item.id],
                mediaId = item.id,
                feature = MediaFeature.OCR,
                sourceRevision = source,
                resultRevision = resultRevision,
                fullRefresh = context.fullRefresh,
                now = now,
                terminalOutputPresent = states[item.id]?.status == MediaFeatureStatus.SKIPPED ||
                    ocrRepository.isCurrent(item.id, source, resultRevision),
            )
            decision.stateToPersist?.let(pending::add)
            if (decision.shouldProcess) candidates += item
        }
        if (pending.isNotEmpty()) scanDao.upsertFeatureStates(pending)

        var succeeded = 0
        var skipped = 0
        var failed = 0
        progress(context, candidates.size, 0, 0, 0, 0)
        for ((index, item) in candidates.withIndex()) {
            currentCoroutineContext().ensureActive()
            // Turning the switch off or changing packs stops the run; the next run picks up the rest.
            val current = readSettings(reloadLinks = false)
            if (!current.active || revisionFor(current) != resultRevision) {
                return SmartScanPhaseResult.Partial(
                    "ocr_settings_changed",
                    SmartScanProgress(candidates.size, index, succeeded, skipped, failed),
                )
            }
            val owner = "${context.owner}:ocr:${item.id}"
            val attemptAt = System.currentTimeMillis()
            if (scanDao.claimFeatureLease(item.id, MediaFeature.OCR, context.runId, owner, attemptAt, attemptAt + ITEM_LEASE_MILLIS) != 1) {
                skipped++
                progress(context, candidates.size, index + 1, succeeded, skipped, failed)
                continue
            }
            try {
                // The viewer pre-empts background reads; read this photo again once it is done rather
                // than ending the run. If the viewer keeps reading, stop here and let the next run resume.
                val status = retryWhenPreempted(PREEMPTED_ATTEMPTS, PREEMPTED_BACKOFF_MILLIS) {
                    withTimeout(OCR_ITEM_TIMEOUT_MILLIS) {
                        val bitmap = decodeForTextRecognition(appContext, item)
                        if (bitmap == null) {
                            MediaFeatureStatus.SKIPPED
                        } else {
                            try {
                                val document = engine.recognize(bitmap, packIds.toSet(), OcrRequestPriority.BACKGROUND)
                                ocrRepository.persist(item, resultRevision, packIds, document)
                                MediaFeatureStatus.SUCCEEDED
                            } finally {
                                bitmap.recycle()
                            }
                        }
                    }
                }
                if (status == null) {
                    // Matches this item's lease owner ("<owner>:ocr:<id>"), the only OCR lease held now.
                    scanDao.releaseFeatureLeases(context.runId, "${context.owner}:ocr", System.currentTimeMillis())
                    return SmartScanPhaseResult.Partial(
                        "ocr_preempted",
                        SmartScanProgress(candidates.size, index, succeeded, skipped, failed),
                    )
                }
                scanDao.finishFeature(
                    item.id, MediaFeature.OCR, owner, status, resultRevision, System.currentTimeMillis(),
                    lastErrorCode = if (status == MediaFeatureStatus.SKIPPED) "ocr_decode_unavailable" else null,
                )
                if (status == MediaFeatureStatus.SUCCEEDED) succeeded++ else skipped++
            } catch (e: CancellationException) {
                if (e !is TimeoutCancellationException) throw e
                finishFailure(item, owner, resultRevision, "ocr_timeout")
                failed++
            } catch (e: Exception) {
                printWarn(TAG, "Reading a photo for the text index failed", mapOf("media" to item.id.toString(), "error" to e.toString()))
                finishFailure(item, owner, resultRevision, "ocr_inference_failed")
                failed++
            }
            progress(context, candidates.size, index + 1, succeeded, skipped, failed)
            yield()
        }
        val summary = SmartScanProgress(candidates.size, candidates.size, succeeded, skipped, failed)
        return when {
            failed == candidates.size && candidates.isNotEmpty() ->
                SmartScanPhaseResult.Failed("ocr_index_failed", retryable = true, summary)
            failed > 0 -> SmartScanPhaseResult.Partial("ocr_index_partially_failed", summary)
            else -> SmartScanPhaseResult.Completed(summary)
        }
    }

    private suspend fun readSettings(reloadLinks: Boolean = true): OcrIndexSettings = withContext(Dispatchers.IO) {
        // A background run has no viewer or settings screen open, so load the persisted file links here.
        if (reloadLinks) store.setLinks(settings.observe(SettingsKeys.ocrExternalModelUris).first())
        val packs = OcrSwitchPolicy.activePacks(settings.observe(SettingsKeys.ocrPacks).first(), store.readyPackIds())
        OcrIndexSettings(
            enabled = settings.observe(SettingsKeys.ocrEnabled).first(),
            indexLibrary = settings.observe(SettingsKeys.ocrIndexLibrary).first(),
            packs = packs,
        )
    }

    // Stored rows carry this revision and the viewer looks them up with the same
    // store.revision(packs), so an indexed photo shows its lines without being read again.
    private fun revisionFor(settings: OcrIndexSettings): String =
        if (settings.active) store.revision(settings.packs) else "ocr-off"

    private suspend fun finishFailure(media: UriMedia, owner: String, revision: String, errorCode: String) {
        val now = System.currentTimeMillis()
        database.getSmartScanDao().finishFeature(
            media.id, MediaFeature.OCR, owner, MediaFeatureStatus.FAILED, revision, now,
            nextRetryAt = now + RETRY_DELAY_MILLIS,
            lastErrorCode = errorCode,
        )
    }

    private companion object {
        const val TAG = "ml.ocr"
        const val OCR_ITEM_TIMEOUT_MILLIS = 120_000L
        const val PREEMPTED_ATTEMPTS = 10
        const val PREEMPTED_BACKOFF_MILLIS = 3_000L
    }
}
