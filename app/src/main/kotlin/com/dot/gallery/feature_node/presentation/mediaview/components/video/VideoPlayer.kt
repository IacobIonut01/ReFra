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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.dot.gallery.core.Constants.Animation.enterAnimation
import com.dot.gallery.core.Constants.Animation.exitAnimation
import com.dot.gallery.core.Settings.Misc.rememberAudioFocus
import com.dot.gallery.core.presentation.components.util.swipe
import com.dot.gallery.feature_node.data.data_source.KeychainHolder
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.util.getUri
import com.dot.gallery.feature_node.domain.util.isEncrypted
import com.dot.gallery.feature_node.presentation.util.printWarning
import io.sanghun.compose.video.RepeatMode
import io.sanghun.compose.video.uri.VideoPlayerMediaItem
import kotlinx.coroutines.delay
import java.io.File
import kotlin.time.Duration.Companion.seconds
import io.sanghun.compose.video.VideoPlayer as SanghunComposeVideoVideoPlayer

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
    var totalDuration by rememberSaveable(media) { mutableLongStateOf(0L) }
    val currentTime = rememberSaveable(media) { mutableLongStateOf(0L) }
    var bufferedPercentage by rememberSaveable(media) { mutableIntStateOf(0) }
    val isPlaying =
        rememberSaveable(playWhenReady.value, media) { mutableStateOf(playWhenReady.value) }
    var lastPlayingState by rememberSaveable(
        isPlaying.value,
        media
    ) { mutableStateOf(isPlaying.value) }
    val context = LocalContext.current
    val keychainHolder = remember {
        KeychainHolder(context)
    }
    var decryptedVideoFile: File? = remember(media) {
        if (media.isEncrypted) {
            createDecryptedVideoFile(keychainHolder, media)
        } else {
            null
        }
    }
    if (media.isEncrypted) {
        DisposableEffect(Unit) {
            if (decryptedVideoFile?.exists() == false) {
                decryptedVideoFile = createDecryptedVideoFile(keychainHolder, media)
            }
            onDispose {
                decryptedVideoFile?.delete()
            }
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

    val metadata = remember(media) {
        MediaMetadataRetriever().apply {
            if (media.isEncrypted) {
                decryptedVideoFile!!.inputStream().use {
                    setDataSource(it.fd)
                }
            } else {
                setDataSource(context, media.getUri())
            }
        }
    }
    val frameRate = remember(metadata) {
        metadata.extractMetadata(
            MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE
        )?.toFloat() ?: 60f
    }

    var exoPlayer by remember {
        mutableStateOf<ExoPlayer?>(null)
    }

    LaunchedEffect(exoPlayer, playWhenReady.value) {
        exoPlayer?.let { player ->
            if (player.playWhenReady != playWhenReady.value) {
                player.playWhenReady = playWhenReady.value
            }
        }
    }

    var showPlayer by remember {
        mutableStateOf(true)
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                showPlayer = true
            }
            if (event == Lifecycle.Event.ON_PAUSE) {
                showPlayer = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    LaunchedEffect(LocalConfiguration.current.orientation) {
        exoPlayer?.let {
            if (it.currentPosition != currentTime.longValue) {
                delay(100)
                it.seekTo(currentTime.longValue)
                if (lastPlayingState) {
                    it.play()
                }
            }
        }
    }

    if (showPlayer) {
        val audioFocus by rememberAudioFocus()
        SanghunComposeVideoVideoPlayer(
            mediaItems = remember(media) {
                if (media.isEncrypted) {
                    listOf(
                        VideoPlayerMediaItem.StorageMediaItem(
                            storageUri = Uri.fromFile(decryptedVideoFile),
                            mimeType = media.mimeType
                        )
                    )
                } else {
                    listOf(
                        VideoPlayerMediaItem.StorageMediaItem(
                            storageUri = media.getUri(),
                            mimeType = media.mimeType
                        )
                    )
                }
            },
            handleLifecycle = true,
            autoPlay = playWhenReady.value,
            usePlayerController = false,
            enablePip = false,
            handleAudioFocus = audioFocus,
            repeatMode = RepeatMode.ONE,
            playerInstance = {
                addListener(
                    object : Player.Listener {
                        override fun onEvents(player: Player, events: Player.Events) {
                            totalDuration = duration.coerceAtLeast(0L)
                        }

                        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                            super.onPlayWhenReadyChanged(playWhenReady, reason)
                            isPlaying.value = playWhenReady
                        }

                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            super.onIsPlayingChanged(isPlaying)
                            lastPlayingState = isPlaying
                        }
                    }
                )
                exoPlayer = this
            },
            modifier = Modifier
                .fillMaxSize()
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onItemClick,
                )
                .swipe(
                    onSwipeDown = onSwipeDown
                )
                .then(modifier),
        )
    }
    AnimatedVisibility(
        visible = exoPlayer != null,
        enter = enterAnimation,
        exit = exitAnimation
    ) {
        LaunchedEffect(isPlaying.value) {
            exoPlayer!!.playWhenReady = isPlaying.value
        }
        if (isPlaying.value) {
            LaunchedEffect(Unit) {
                while (true) {
                    bufferedPercentage = exoPlayer!!.bufferedPercentage
                    currentTime.longValue = exoPlayer!!.currentPosition
                    delay(1.seconds / 30)
                }
            }
        }
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