package com.dot.gallery.editor.ui.video

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import com.dot.gallery.editor.core.time.EditorTime
import com.dot.gallery.editor.render.video.VideoEditorState

/**
 * Live session state, provided by [VideoEditorScreen] as a `() -> state`
 * read handle. Gesture capture reads through it because a just-issued
 * play()/pause() can lag one recomposition behind the pointer down — the
 * recorded `wasPlaying` intent must be the truth at the instant the finger
 * lands, not a stale snapshot.
 */
internal val LocalVideoLiveState =
    staticCompositionLocalOf<() -> VideoEditorState> {
        { VideoEditorState() }
    }

/**
 * One scrub gesture's live intent, owned by the pointer-input coroutine.
 * `wasPlaying` and the entry position are captured at pointer-down on the
 * Initial pass, before the first seek's pause can poison the snapshot —
 * the release then knows whether to resume playback even when nothing
 * recomposed during the gesture. A cancel restores the entry position and
 * still resumes.
 */
internal class ScrubGesture(
    private val isPlaying: () -> Boolean,
    private val pause: () -> Unit,
    private val resume: () -> Unit,
    private val seek: (EditorTime) -> Unit
) {
    private var startPosition: EditorTime? = null
    private var wasPlaying = false
    private var inFlight = false
    private var paused = false

    fun onDown(target: EditorTime) {
        if (inFlight) return
        inFlight = true
        startPosition = target
        wasPlaying = isPlaying()
        if (wasPlaying) {
            paused = true
            pause()
        }
        seek(target)
    }

    fun onMove(target: EditorTime) {
        if (inFlight) seek(target)
    }

    fun onUp() {
        if (!inFlight) return
        inFlight = false
        if (paused) {
            paused = false
            resume()
        }
    }

    fun onCancel() {
        if (!inFlight) return
        inFlight = false
        startPosition?.let(seek)
        startPosition = null
        if (paused) {
            paused = false
            resume()
        }
    }
}

/**
 * Observes raw pointer input on the Initial pass and drives [gesture]:
 * down captures intent and seeks, moves keep seeking, release commits and
 * possibly resumes, a pointer cancel (or coroutine teardown mid-gesture)
 * restores the entry position. Nothing here consumes events, so taps also
 * reach sibling handlers and the surface never steals drags from clips —
 * apply it to the ruler only.
 */
internal fun Modifier.scrubGestureInput(
    gesture: ScrubGesture,
    targetAt: (Offset) -> EditorTime
): Modifier = pointerInput(gesture, targetAt) {
    awaitEachGesture {
        val down = awaitFirstDown(
            requireUnconsumed = false,
            pass = PointerEventPass.Initial
        )
        gesture.onDown(targetAt(down.position))
        try {
            var ended = false
            while (!ended) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val tracked = event.changes.firstOrNull { it.id == down.id }
                    ?: continue
                if (tracked.pressed) {
                    gesture.onMove(targetAt(tracked.position))
                } else {
                    if (tracked.previousPressed && !tracked.isConsumed) {
                        gesture.onUp()
                    } else {
                        gesture.onCancel()
                    }
                    ended = true
                }
            }
        } finally {
            // Coroutine teardown (dispose mid-gesture) must not leak the
            // paused-for-scrub state.
            gesture.onCancel()
        }
    }
}
