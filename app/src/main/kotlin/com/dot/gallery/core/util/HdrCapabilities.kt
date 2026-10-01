/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.util

import android.content.Context
import android.media.MediaCodecList
import android.os.Build
import android.view.Display

/**
 * Detects whether the current display can actually render HDR content, so the app can skip all HDR
 * work — the window [android.content.pm.ActivityInfo.COLOR_MODE_HDR] toggle, the gain-map probe in
 * the media viewer, and the per-image gain-map decode in the HEIC region decoder — on SDR-only
 * devices. HDR rendering (gain maps / [android.graphics.Gainmap]) requires API 34, so this always
 * reports false below that.
 */
object HdrCapabilities {

    /**
     * Desired HDR headroom cap applied via `Window.setDesiredHdrHeadroom` (API 35+) when HDR is on,
     * keeping highlights from over-brightening and washing out the surrounding SDR UI. A value of
     * `0f` means "no preference" (system default) and is used when HDR is off.
     */
    const val DESIRED_HDR_HEADROOM = 3f

    /** The platform MIME for Dolby Vision video tracks (`MediaCodecList` decoder lookup). */
    private const val MIME_DOLBY_VISION = "video/dolby-vision"

    @Volatile
    private var cached: Boolean? = null

    @Volatile
    private var cachedHdrTypes: Set<Int>? = null

    @Volatile
    private var cachedDolbyVisionDecoder: Boolean? = null

    /**
     * True when the current display advertises HDR support. The result is cached after the first
     * query (display HDR capability is effectively constant for the app's lifetime).
     */
    fun isHdrDisplay(context: Context): Boolean {
        cached?.let { return it }
        val result = compute(context)
        cached = result
        return result
    }

    /**
     * The HDR types the current display advertises — a subset of
     * [android.view.Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION],
     * `HDR_TYPE_HDR10`, `HDR_TYPE_HDR10_PLUS` and `HDR_TYPE_HLG`. Empty on SDR displays.
     * Available since API 26, unlike [isHdrDisplay] which is gated on the API 34
     * `isScreenHdr` flag. Cached like [isHdrDisplay].
     */
    fun displayHdrTypes(context: Context): Set<Int> {
        cachedHdrTypes?.let { return it }
        @Suppress("DEPRECATION") // no replacement API; still the only HDR-types accessor
        val types = runCatching {
            context.display.hdrCapabilities?.supportedHdrTypes?.toSet()
        }.getOrNull().orEmpty()
        cachedHdrTypes = types
        return types
    }

    /**
     * Whether the current display advertises Dolby Vision
     * ([android.view.Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION]).
     */
    fun hasDolbyVisionDisplay(context: Context): Boolean =
        displayHdrTypes(context).contains(Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION)

    /**
     * Whether the device ships a decoder for `video/dolby-vision` (typically a `c2.dolby.*` or
     * `OMX.dolby.*` codec). Without one, Dolby Vision can only be rendered through the stream's
     * backward-compatible base layer — profiles without one (e.g. profile 5) cannot be decoded
     * correctly at all. Cached; the codec list is constant for the process lifetime.
     */
    fun hasDolbyVisionDecoder(): Boolean {
        cachedDolbyVisionDecoder?.let { return it }
        val result = runCatching {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
                !info.isEncoder && info.supportedTypes.any { it.equals(MIME_DOLBY_VISION, ignoreCase = true) }
            }
        }.getOrDefault(false)
        cachedDolbyVisionDecoder = result
        return result
    }

    private fun compute(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
        return runCatching { context.resources.configuration.isScreenHdr }.getOrDefault(false)
    }
}
