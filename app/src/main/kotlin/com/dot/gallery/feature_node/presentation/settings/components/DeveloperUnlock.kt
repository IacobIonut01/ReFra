/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.settings.components

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import com.dot.gallery.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Detects a 5-second press-and-hold (the developer-mode easter egg).
 *
 * [onProgress] is fed 0→1 while held so callers can render a countdown
 * indicator. After the first second a toast explains what is happening. When
 * the hold completes, the up event is consumed so an ancestor `clickable` does
 * not also fire on finger release; short holds leave the event untouched.
 *
 * The countdown runs in a sibling coroutine signalled through a channel:
 * [awaitEachGesture] is a restricted-suspension scope, so coroutines cannot be
 * launched from inside the gesture block itself.
 */
@Composable
fun Modifier.holdToUnlock(
    enabled: Boolean,
    onUnlocked: () -> Unit,
    onProgress: (Float) -> Unit,
    holdMillis: Long = 5_000L,
): Modifier {
    val context = LocalContext.current
    val holdMessage = stringResource(R.string.dev_unlock_hold)
    val alreadyMessage = stringResource(R.string.dev_unlock_already)
    val currentOnUnlocked by rememberUpdatedState(onUnlocked)
    val currentOnProgress by rememberUpdatedState(onProgress)
    return pointerInput(enabled, holdMillis) {
        coroutineScope {
            val signals = Channel<Boolean>(Channel.UNLIMITED)
            var unlocked = false

            launch {
                var holdJob: Job? = null
                for (isDown in signals) {
                    if (isDown) {
                        unlocked = false
                        holdJob?.cancel()
                        holdJob = launch {
                            delay(TOAST_DELAY_MS)
                            if (enabled) {
                                Toast.makeText(context, alreadyMessage, Toast.LENGTH_SHORT).show()
                                return@launch
                            }
                            Toast.makeText(context, holdMessage, Toast.LENGTH_SHORT).show()
                            var elapsed = TOAST_DELAY_MS
                            while (elapsed < holdMillis) {
                                delay(TICK_MS)
                                elapsed += TICK_MS
                                currentOnProgress((elapsed.toFloat() / holdMillis).coerceAtMost(1f))
                            }
                            unlocked = true
                            currentOnUnlocked()
                        }
                    } else {
                        holdJob?.cancel()
                        currentOnProgress(0f)
                    }
                }
            }

            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                signals.trySend(true)
                val up = waitForUpOrCancellation()
                signals.trySend(false)
                if (unlocked) up?.consume()
            }
        }
    }
}

private const val TOAST_DELAY_MS = 900L
private const val TICK_MS = 50L
