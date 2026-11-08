package com.dot.gallery.editor.render.video

import com.dot.gallery.editor.core.model.VideoSequence
import com.dot.gallery.editor.core.time.EditorTime

/**
 * Runs a sequence through the shared decode/audio pipeline: [play] delivers
 * frames via [onFrame] and positions via [onPosition] (both may arrive from
 * a worker thread — the owner conflates them into state), paced by the
 * audio clock where the output exists and by a monotonic clock otherwise.
 * [play] returns when the sequence ends; coroutine cancellation pauses and
 * releases decoder and audio resources. A fresh [play] must only start
 * after a previous one has finished or been cancelled.
 *
 * [onLevels] delivers one [VideoLevels] per written audio block — at most
 * ~30 per second, since a block is 33 ms and the producer never runs more
 * than four blocks ahead of the device. A muted or audioless playback
 * still reports (zero levels), so a stale meter never survives a pause.
 */
interface VideoPlayback : AutoCloseable {

    suspend fun play(
        sequence: VideoSequence,
        from: EditorTime,
        muted: Boolean,
        onFrame: (VideoFrame) -> Unit,
        onPosition: (EditorTime) -> Unit,
        onLevels: (VideoLevels) -> Unit = {}
    )

    override fun close()
}
