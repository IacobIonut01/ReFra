/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.mediaview.components.media

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.dot.gallery.core.ml.CutoutHelper.CutoutResult
import com.dot.gallery.core.ml.CutoutHelper.CutoutSession
import com.dot.gallery.core.ml.CutoutHelper.PromptPoint
import com.dot.gallery.core.ml.ModelGroup
import com.dot.gallery.core.ml.ModelManager
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.presentation.util.printError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

private const val TAG = "ui.suggestion"

/**
 * Owns the *background subject detection* that runs while an image is on screen and, if a plausible
 * subject is found, offers it as a one-tap "cut out" suggestion.
 *
 * Lifecycle contract (this is the whole point of the class):
 * - Detection runs on a private [Dispatchers.Default] scope, never on the main thread.
 * - Exactly one detection is in flight at a time; [detect] cancels any previous run first.
 * - The heavy [CutoutSession] (ONNX encoder + embeddings + source bitmap) is **always** released:
 *     - if detection is cancelled or finds nothing, the session is closed in the run's `finally`;
 *     - a produced-but-unconsumed suggestion is closed by [reset]/[dispose];
 *     - a suggestion the user accepts is handed to the caller via [consume], which transfers
 *       ownership (the caller's [CutoutState] then closes it on dismiss).
 * - The detected [CutoutResult] bitmap is only used after [consume]; on discard it is recycled.
 *
 * All disposal is idempotent ([CutoutSession.close] is mutex-guarded and no-ops when already closed),
 * so overlapping cancel/close paths cannot double-free or leak.
 */
@Stable
class SubjectSuggestionState {

    data class Suggestion(
        val session: CutoutSession,
        val result: CutoutResult,
        val seed: PromptPoint
    )

    var suggestion by mutableStateOf<Suggestion?>(null)
        private set

    var isDetecting by mutableStateOf(false)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var detectJob: Job? = null

    /** Restart background detection for [media]. Cheaply no-ops (after cleanup) when nothing is found. */
    fun detect(
        context: Context,
        media: Media,
        modelManager: ModelManager,
        debounceMs: Long = 450L
    ) {
        reset()
        detectJob = scope.launch {
            var session: CutoutSession? = null
            var handedOff = false
            try {
                // Debounce so fast swiping doesn't kick off (and immediately cancel) expensive work.
                delay(debounceMs)
                if (!modelManager.isReady(ModelGroup.CUTOUT)) return@launch

                isDetecting = true
                val newSession = CutoutSession(context, media, modelManager)
                session = newSession

                if (!newSession.initAndRunEncoder()) return@launch
                ensureActive()

                // Seed with a single centre point — the most common location of the main subject.
                val seed = PromptPoint(
                    x = newSession.widthOrig / 2f,
                    y = newSession.heightOrig / 2f,
                    isPositive = true
                )
                val result = newSession.runDecoder(listOf(seed)) ?: return@launch
                ensureActive()

                if (isPlausibleSubject(result, newSession)) {
                    handedOff = true
                    suggestion = Suggestion(newSession, result, seed)
                } else {
                    // Not a useful suggestion — drop the mask bitmap; session closed in finally.
                    result.bitmap.recycle()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                printError(TAG, "Subject detection failed", e)
            } finally {
                if (!handedOff) session?.close()
                isDetecting = false
            }
        }
    }

    /**
     * Accept the current suggestion, transferring ownership of the session + result bitmap to the
     * caller. Returns null when there is nothing to accept.
     */
    fun consume(): Suggestion? {
        val accepted = suggestion
        suggestion = null
        detectJob = null
        return accepted
    }

    /** Cancel any in-flight detection and release an unconsumed suggestion (session + bitmap). */
    fun reset() {
        detectJob?.cancel()
        detectJob = null
        isDetecting = false
        val pending = suggestion ?: return
        suggestion = null
        scope.launch {
            pending.session.close()
            if (!pending.result.bitmap.isRecycled) pending.result.bitmap.recycle()
        }
    }

    /** Tear down for good when the owning composable leaves composition. */
    fun dispose() {
        detectJob?.cancel()
        detectJob = null
        val pending = suggestion
        suggestion = null
        isDetecting = false
        scope.launch {
            pending?.session?.close()
            pending?.result?.bitmap?.let { if (!it.isRecycled) it.recycle() }
        }.invokeOnCompletion { scope.cancel() }
    }

    private fun isPlausibleSubject(result: CutoutResult, session: CutoutSession): Boolean {
        val subjectArea = result.originalBounds.width().toLong() * result.originalBounds.height().toLong()
        val total = session.widthOrig.toLong() * session.heightOrig.toLong()
        if (total <= 0L || subjectArea <= 0L) return false
        val fraction = subjectArea.toFloat() / total.toFloat()
        // Ignore specks and near-full-frame masks (usually background/no clear subject).
        return fraction in 0.03f..0.9f
    }
}

@Composable
fun rememberSubjectSuggestionState(): SubjectSuggestionState {
    val state = remember { SubjectSuggestionState() }
    DisposableEffect(state) {
        onDispose { state.dispose() }
    }
    return state
}
