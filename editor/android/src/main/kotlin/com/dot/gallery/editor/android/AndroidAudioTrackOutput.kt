package com.dot.gallery.editor.android

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import com.dot.gallery.editor.render.video.VideoAudioOutput
import com.dot.gallery.editor.videocodec.PlaybackHeadCounter
import java.io.IOException
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

/**
 * AudioTrack [VideoAudioOutput]: PCM16 stereo at 48 kHz, MODE_STREAM.
 * `playedFrames` is [AudioTrack.getPlaybackHeadPosition] — a 32-bit frame
 * counter — extended to 64 bits by [PlaybackHeadCounter], with a base
 * captured at [start]/[pauseAndFlush]. Writes use
 * [AudioTrack.WRITE_NON_BLOCKING] and wait on a short cancellable delay when
 * the buffer is full; audio is never dropped silently.
 *
 * Audio focus is requested once per playback session at [start] through
 * [AudioManager]; a denial throws (explicit failure, not muted playback) and
 * any loss — permanent, transient or duck — invokes [onFocusLost] so the
 * controller pauses. Focus is released on [close].
 *
 * One instance is one playback session: [start] after [close] is rejected —
 * the engine creates a fresh sink per play, which is also why a new output's
 * `playedFrames` reads zero.
 */
class AndroidAudioTrackOutput(
    context: Context,
    private val onError: (Throwable) -> Unit,
    private val onFocusLost: () -> Unit
) : VideoAudioOutput {

    private val appContext = context.applicationContext
    private val focusRequest =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setOnAudioFocusChangeListener { change ->
                    if (change == AudioManager.AUDIOFOCUS_LOSS ||
                        change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ||
                        change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK
                    ) {
                        onFocusLost()
                    }
                }
                .build()
        } else {
            null
        }

    @Volatile
    private var track: AudioTrack? = null

    @Volatile
    private var closed = false

    @Volatile
    private var lastTotal = 0L

    // playbackHeadPosition is unsigned 32-bit and restarts on flush; all
    // wrap/base bookkeeping is one lock inside the counter.
    private val counter = PlaybackHeadCounter()

    override val playedFrames: Long
        get() {
            val out = track ?: return lastTotal
            val total = counter.update(
                out.playbackHeadPosition.toLong() and 0xFFFF_FFFFL
            )
            lastTotal = total
            return total
        }

    override fun start(sampleRate: Int, channels: Int) {
        check(!closed) { "audio output is closed" }
        check(track == null) { "audio output already started" }
        requestFocus()
        val channelMask = if (channels == 1) {
            AudioFormat.CHANNEL_OUT_MONO
        } else {
            AudioFormat.CHANNEL_OUT_STEREO
        }
        val minBytes = AudioTrack.getMinBufferSize(
            sampleRate, channelMask, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBytes <= 0) {
            releaseFocus()
            throw IOException("AudioTrack.getMinBufferSize failed: $minBytes")
        }
        val created = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(channelMask)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(minBytes, BUFFER_BYTES))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (e: Exception) {
            releaseFocus()
            throw IOException("cannot create AudioTrack", e)
        }
        if (created.state != AudioTrack.STATE_INITIALIZED) {
            created.release()
            releaseFocus()
            throw IOException("AudioTrack failed to initialize")
        }
        try {
            created.play()
        } catch (e: Exception) {
            created.release()
            releaseFocus()
            throw IOException("AudioTrack play() failed", e)
        }
        counter.reset(created.playbackHeadPosition.toLong() and 0xFFFF_FFFFL)
        track = created
    }

    override suspend fun write(samples: ShortArray, offset: Int, count: Int) {
        val out = track ?: throw IOException("audio output is not started")
        var i = offset
        val end = offset + count
        while (i < end) {
            coroutineContext.ensureActive()
            if (closed) return
            val written = out.write(
                samples, i, end - i, AudioTrack.WRITE_NON_BLOCKING
            )
            when {
                written > 0 -> i += written
                written == 0 -> delay(SLEEP_MS)
                else -> throw IOException("AudioTrack.write failed: $written")
            }
        }
    }

    /**
     * Pauses and discards buffered audio — the name is the contract: the
     * track is left paused (never restarted), and the counter is rebased so
     * [playedFrames] does not rewind to zero.
     */
    override fun pauseAndFlush() {
        track?.let {
            try {
                val before = playedFrames
                it.pause()
                it.flush()
                val position =
                    it.playbackHeadPosition.toLong() and 0xFFFF_FFFFL
                counter.rebaseTo(position, before)
            } catch (e: IllegalStateException) {
                // Released underneath us — close() owns the final state.
                onError(e)
            }
        }
    }

    override fun close() {
        closed = true
        track?.let {
            try {
                it.pause()
                it.flush()
            } catch (e: IllegalStateException) {
                onError(e)
            }
            try {
                it.release()
            } catch (e: Exception) {
                onError(e)
            }
        }
        track = null
        releaseFocus()
    }

    private fun requestFocus() {
        val manager = appContext.getSystemService(Context.AUDIO_SERVICE)
            as? AudioManager ?: throw IOException("no AudioManager")
        val granted = if (focusRequest != null) {
            manager.requestAudioFocus(focusRequest)
        } else {
            @Suppress("DEPRECATION")
            manager.requestAudioFocus(
                null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN
            )
        }
        if (granted != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            throw IOException("audio focus denied")
        }
    }

    private fun releaseFocus() {
        val manager = appContext.getSystemService(Context.AUDIO_SERVICE)
            as? AudioManager ?: return
        if (focusRequest != null) {
            manager.abandonAudioFocusRequest(focusRequest)
        } else {
            @Suppress("DEPRECATION")
            manager.abandonAudioFocus(null)
        }
    }

    private companion object {
        /** ~85 ms of stereo 16-bit 48 kHz, at least the platform minimum. */
        const val BUFFER_BYTES = 48_000 * 4 / 12
        const val SLEEP_MS = 1L
    }
}
