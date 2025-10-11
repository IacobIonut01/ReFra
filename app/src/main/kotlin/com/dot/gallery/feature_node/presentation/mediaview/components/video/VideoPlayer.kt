/*
 * SPDX-FileCopyrightText: 2023 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.mediaview.components.video

import android.app.Activity
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.rememberPresentationState
import com.dot.gallery.core.Constants.Animation.enterAnimation
import com.dot.gallery.core.Constants.Animation.exitAnimation
import com.dot.gallery.core.Settings.Misc.rememberAudioFocus
import com.dot.gallery.core.Settings.Misc.rememberVideoAutoplay
import com.dot.gallery.core.presentation.components.util.swipe
import com.dot.gallery.feature_node.data.data_source.KeychainHolder
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.util.getUri
import com.dot.gallery.feature_node.domain.util.isEncrypted
import com.dot.gallery.feature_node.presentation.util.printWarning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.time.Duration.Companion.seconds

@Stable
@androidx.annotation.OptIn(UnstableApi::class)
@NonRestartableComposable
@Composable
fun <T : Media> VideoPlayer(
    media: T,
    modifier: Modifier = Modifier,
    playWhenReady: State<Boolean>,
    videoController: @Composable (ExoPlayer, MutableState<Boolean>, MutableLongState, Long, Int, Float) -> Unit,
    onItemClick: () -> Unit,
    onSwipeDown: () -> Unit
) {
    // Use stable primitive key (media.id) so state survives when a new Media instance equal in id is provided after config change.
    val mediaIdKey = media.id
    var totalDuration by rememberSaveable(mediaIdKey) { mutableLongStateOf(0L) }
    val currentTime = rememberSaveable(mediaIdKey) { mutableLongStateOf(0L) }
    var bufferedPercentage by rememberSaveable(mediaIdKey) { mutableIntStateOf(0) }
    val isPlaying =
        rememberSaveable(playWhenReady.value, mediaIdKey) { mutableStateOf(playWhenReady.value) }
    var lastPlayingState by rememberSaveable(
        isPlaying.value,
        mediaIdKey
    ) { mutableStateOf(isPlaying.value) }
    val context = LocalContext.current
    val keychainHolder = remember {
        KeychainHolder(context)
    }
    // Asynchronous decryption state
    var decryptedVideoFile by remember(media) { mutableStateOf<File?>(null) }
    var isDecrypting by remember(media) { mutableStateOf(false) }
    var decryptFailed by remember(media) { mutableStateOf(false) }
    LaunchedEffect(media) {
        if (media.isEncrypted) {
            isDecrypting = true
            decryptFailed = false
            decryptedVideoFile = try {
                withContext(Dispatchers.IO) {
                    createDecryptedVideoFile(keychainHolder, media)
                }
            } catch (t: Throwable) {
                printWarning("Video decrypt failed: ${t.message}")
                decryptFailed = true
                null
            } finally {
                isDecrypting = false
            }
        } else {
            decryptedVideoFile = null
        }
    }
    DisposableEffect(media) {
        onDispose {
            decryptedVideoFile?.delete()
        }
    }

    LaunchedEffect(isPlaying.value) {
        (context as? Activity)?.let { activity ->
            if (isPlaying.value) {
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        } ?: printWarning("Couldn't mark the screen as always on. Context is not an activity.")
    }

    val frameRate by remember(media, decryptedVideoFile, isDecrypting) {
        mutableFloatStateOf(
            if (!media.isEncrypted) {
                try {
                    MediaMetadataRetriever().use { r ->
                        r.setDataSource(context, media.getUri())
                        r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                            ?.toFloat() ?: 60f
                    }
                } catch (_: Exception) {
                    60f
                }
            } else if (!isDecrypting && decryptedVideoFile != null) {
                try {
                    MediaMetadataRetriever().use { r ->
                        decryptedVideoFile!!.inputStream().use { r.setDataSource(it.fd) }
                        r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                            ?.toFloat() ?: 60f
                    }
                } catch (_: Exception) {
                    60f
                }
            } else 60f
        )
    }

    var exoPlayer by remember {
        mutableStateOf<ExoPlayer?>(null)
    }
    val presentationState = rememberPresentationState(exoPlayer)
    DisposableEffect(Unit) {
        onDispose {
            try {
                exoPlayer?.release()
            } catch (_: Throwable) {
            }
            exoPlayer = null
        }
    }
    val canAutoPlay by rememberVideoAutoplay()
    LaunchedEffect(
        canAutoPlay,
        playWhenReady,
        exoPlayer,
        exoPlayer?.playWhenReady,
        isPlaying.value
    ) {
        if (!playWhenReady.value && canAutoPlay) {
            isPlaying.value = false
        }
        exoPlayer?.playWhenReady = isPlaying.value
        if (isPlaying.value) {
            exoPlayer?.play()
        } else {
            exoPlayer?.pause()
        }
    }

    var showPlayer by remember { mutableStateOf(true) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                showPlayer = true
            }
            if (event == Lifecycle.Event.ON_PAUSE) {
                // Capture current position explicitly on pause (ensures latest frame even if play loop not running)
                exoPlayer?.let { currentTime.longValue = it.currentPosition }
                showPlayer = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    var initialSeekApplied by rememberSaveable(media) { mutableStateOf(false) }

    var playerReady by rememberSaveable { mutableStateOf(false) }
    val readyForPlayback =
        !media.isEncrypted || (decryptedVideoFile != null && !isDecrypting && !decryptFailed)
    if (showPlayer && readyForPlayback) {
        val audioFocus by rememberAudioFocus()
        var lastAudioFocus by remember { mutableStateOf<Boolean?>(null) }
        // Build or reuse ExoPlayer instance. Rebuild when toggling from focus ON -> OFF to ensure
        // underlying AudioAttributes are applied without requesting system audio focus.
        val context = LocalContext.current
        LaunchedEffect(media, decryptedVideoFile, audioFocus) {
            val turningFocusOff = lastAudioFocus == true && !audioFocus
            lastAudioFocus = audioFocus

            // Capture state if we are about to rebuild
            val currentPosition = exoPlayer?.currentPosition ?: currentTime.longValue
            val wasPlaying = exoPlayer?.isPlaying == true
            val currentVolume = exoPlayer?.volume ?: 1f

            if (turningFocusOff) {
                try {
                    exoPlayer?.release()
                } catch (_: Throwable) {
                }
                exoPlayer = null
            }

            val player = exoPlayer ?: ExoPlayer.Builder(context).build().also { exoPlayer = it }

            val usage = if (audioFocus) C.USAGE_NOTIFICATION else C.USAGE_MEDIA
            val contentType = if (audioFocus) C.AUDIO_CONTENT_TYPE_SONIFICATION else C.AUDIO_CONTENT_TYPE_MOVIE

            val attrs = AudioAttributes.Builder()
                .setUsage(usage)
                .setContentType(contentType)
                .build()

            // If we're reusing a player and only toggling OFF->ON (audioFocus true) we can just re-apply attributes.
            player.setAudioAttributes(attrs, /* handleAudioFocus = */ true)
            player.repeatMode = Player.REPEAT_MODE_ONE

            // Prepare media item if needed
            val uri = if (media.isEncrypted) Uri.fromFile(decryptedVideoFile!!) else media.getUri()
            val item = MediaItem.Builder()
                .setUri(uri)
                .setMimeType(media.mimeType)
                .build()
            val needsPrepare =
                player.currentMediaItem == null || player.currentMediaItem?.localConfiguration?.uri != item.localConfiguration?.uri
            if (needsPrepare) {
                initialSeekApplied = false // new item, reset flag
                player.setMediaItem(item)
                player.prepare()
            }

            // Restore state after rebuild (when turning focus off) without triggering unwanted focus request
            if (turningFocusOff) {
                player.volume = currentVolume
                if (currentPosition > 0) player.seekTo(currentPosition)
                player.playWhenReady = wasPlaying
                if (wasPlaying) player.play() else player.pause()
                // Orientation rebuild path may already satisfy initial seek
                if (currentTime.longValue == currentPosition) initialSeekApplied = true
            }

            player.addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    totalDuration = player.duration.coerceAtLeast(0L)
                    if (player.playbackState == Player.STATE_READY) {
                        playerReady = true
                        if (!initialSeekApplied && currentTime.longValue > 0) {
                            // Apply saved position exactly once after ready
                            player.seekTo(currentTime.longValue)
                            if (lastPlayingState) player.play()
                            initialSeekApplied = true
                        }
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        playerReady = true
                        if (!initialSeekApplied && currentTime.longValue > 0) {
                            player.seekTo(currentTime.longValue)
                            if (lastPlayingState) player.play()
                            initialSeekApplied = true
                        }
                    }
                }

                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    isPlaying.value = playWhenReady
                }

                override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                    lastPlayingState = isPlayingNow
                }
            })
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onItemClick,
                )
                .swipe(onSwipeDown = onSwipeDown)
                .then(modifier)
        ) {
            PlayerSurface(
                player = exoPlayer,
                modifier = Modifier
                    .align(Alignment.Center)
                    .resizeWithContentScale(
                        contentScale = ContentScale.Fit,
                        sourceSizeDp = presentationState.videoSizeDp
                    )
            )
        }
    }
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = exoPlayer != null,
            enter = enterAnimation,
            exit = exitAnimation
        ) {
            if (isPlaying.value) {
                LaunchedEffect(Unit) {
                    while (true) {
                        bufferedPercentage = exoPlayer!!.bufferedPercentage
                        currentTime.longValue = exoPlayer!!.currentPosition
                        delay(1.seconds / 30)
                    }
                }
            }
            if (exoPlayer != null) {
                videoController(
                    exoPlayer!!,
                    isPlaying,
                    currentTime,
                    totalDuration,
                    bufferedPercentage,
                    frameRate
                )
            }
        }
        val showLoading =
            (media.isEncrypted && (isDecrypting || decryptedVideoFile == null)) || (exoPlayer != null && !playerReady)
        if (showLoading && !decryptFailed) {
            Box(Modifier.align(Alignment.Center)) {
                CircularProgressIndicator(Modifier.size(64.dp))
                // Placeholder percentage until streaming incremental decrypt implemented
                if (isDecrypting) {
                    Text(
                        text = "…",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
        if (decryptFailed) {
            val scope = rememberCoroutineScope()
            Text(
                text = "Decrypt failed. Tap to retry.",
                modifier = Modifier
                    .align(Alignment.Center)
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            scope.launch {
                                // Reset and retry
                                decryptedVideoFile = null
                                isDecrypting = true
                                decryptFailed = false
                                decryptedVideoFile = try {
                                    withContext(Dispatchers.IO) {
                                        createDecryptedVideoFile(
                                            keychainHolder,
                                            media
                                        )
                                    }
                                } catch (_: Throwable) {
                                    null
                                }
                                isDecrypting = false
                                decryptFailed = decryptedVideoFile == null
                            }
                        }
                    )
            )
        }
    }
}