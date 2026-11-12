/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.mediaview.components.video

import androidx.media3.common.Player
import com.dot.gallery.domain.settings.SettingsKeys
import com.dot.gallery.feature.viewer.components.video.effectiveVideoRepeatMode
import com.dot.gallery.feature.viewer.components.video.playOrReplay
import com.dot.gallery.feature.viewer.components.video.shouldPlayVideoOnce
import com.dot.gallery.feature.viewer.components.video.shouldRestartVideoBeforePlay
import com.dot.gallery.feature.viewer.components.video.shouldUseTextureVideoOutput
import com.dot.gallery.feature.viewer.components.video.videoRepeatMode
import com.dot.gallery.feature.viewer.components.video.videoResumePosition
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test


class VideoPlayerLoopPolicyTest {

    @Test
    fun loopVideosOnRepeatsTheVideo() {
        assertEquals(Player.REPEAT_MODE_ONE, videoRepeatMode(loopVideos = true))
    }

    @Test
    fun loopVideosOffStopsAtTheEnd() {
        assertEquals(Player.REPEAT_MODE_OFF, videoRepeatMode(loopVideos = false))
    }

    @Test
    fun loopVideosDefaultKeepsTheHistoricalLocalLoop() {
        assertTrue(SettingsKeys.loopVideos.default)
        assertEquals("loop_videos", SettingsKeys.loopVideos.name)
        assertEquals(Player.REPEAT_MODE_ONE, videoRepeatMode(SettingsKeys.loopVideos.default))
    }

    @Test
    fun slideshowTemporarilyOverridesAndThenRestoresConfiguredLoopMode() {
        assertEquals(
            Player.REPEAT_MODE_OFF,
            effectiveVideoRepeatMode(videoRepeatMode(loopVideos = true), slideshowActive = true)
        )
        assertEquals(
            Player.REPEAT_MODE_ONE,
            effectiveVideoRepeatMode(videoRepeatMode(loopVideos = true), slideshowActive = false)
        )
        assertEquals(
            Player.REPEAT_MODE_OFF,
            effectiveVideoRepeatMode(videoRepeatMode(loopVideos = false), slideshowActive = true)
        )
        assertEquals(
            Player.REPEAT_MODE_OFF,
            effectiveVideoRepeatMode(videoRepeatMode(loopVideos = false), slideshowActive = false)
        )
    }

    /**
     * The repeat decision takes only the global setting, so a cloud video cannot pick up a
     * per-account value: cloud and local videos get the same mode for the same setting.
     */
    @Test
    fun cloudVideosFollowTheGlobalSetting() {
        val local = listOf(true, false).map { videoRepeatMode(loopVideos = it) }
        val cloud = listOf(true, false).map { videoRepeatMode(loopVideos = it) }
        assertEquals(listOf(Player.REPEAT_MODE_ONE, Player.REPEAT_MODE_OFF), cloud)
        assertEquals(local, cloud)
    }

    @Test
    fun onlyAnEndedVideoRestartsBeforePlaying() {
        assertTrue(shouldRestartVideoBeforePlay(Player.STATE_ENDED))
        assertFalse(shouldRestartVideoBeforePlay(Player.STATE_READY))
        assertFalse(shouldRestartVideoBeforePlay(Player.STATE_BUFFERING))
        assertFalse(shouldRestartVideoBeforePlay(Player.STATE_IDLE))
    }

    @Test
    fun playOrReplaySeeksToStartWhenTheVideoEnded() {
        val calls = mutableListOf<String>()
        fakePlayer(Player.STATE_ENDED, calls).playOrReplay()
        assertEquals(listOf("seekTo(0)", "setPlayWhenReady(true)", "play"), calls)
    }

    @Test
    fun playOrReplayResumesInPlaceWhenTheVideoIsPaused() {
        val calls = mutableListOf<String>()
        fakePlayer(Player.STATE_READY, calls).playOrReplay()
        assertEquals(listOf("setPlayWhenReady(true)", "play"), calls)
    }

    @Test
    fun anEndedVideoResumesFromTheStartWhenReopened() {
        assertEquals(0L, videoResumePosition(Player.STATE_ENDED, currentPositionMs = 7_000L))
        assertEquals(3_200L, videoResumePosition(Player.STATE_READY, currentPositionMs = 3_200L))
    }

    @Test
    fun storyAndSlideshowModesPlayVideoOnce() {
        assertTrue(shouldPlayVideoOnce(slideshowActive = true, storyActive = false))
        assertTrue(shouldPlayVideoOnce(slideshowActive = false, storyActive = true))
        assertTrue(shouldPlayVideoOnce(slideshowActive = true, storyActive = true))
        assertFalse(shouldPlayVideoOnce(slideshowActive = false, storyActive = false))
    }

    @Test
    fun storyAndOverlayVideosUseCompositedTextureOutput() {
        assertTrue(shouldUseTextureVideoOutput(storyActive = true, compositedOutput = false))
        assertTrue(shouldUseTextureVideoOutput(storyActive = false, compositedOutput = true))
        assertFalse(shouldUseTextureVideoOutput(storyActive = false, compositedOutput = false))
    }

    /** Records the transport calls [playOrReplay] makes; every other [Player] call is unexpected. */
    private fun fakePlayer(playbackState: Int, calls: MutableList<String>): Player =
        Proxy.newProxyInstance(
            Player::class.java.classLoader,
            arrayOf(Player::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getPlaybackState" -> playbackState
                "seekTo" -> {
                    calls += "seekTo(${args?.joinToString()})"
                    null
                }
                "setPlayWhenReady" -> {
                    calls += "setPlayWhenReady(${args?.single()})"
                    null
                }
                "play" -> {
                    calls += "play"
                    null
                }
                else -> error("Unexpected Player.${method.name}")
            }
        } as Player
}
