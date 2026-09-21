/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.mediaview

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.vector.ImageVector
import com.dot.gallery.BuildConfig
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.util.isImage
import com.dot.gallery.ui.core.Icons as GalleryIcons
import com.dot.gallery.ui.core.icons.GoogleLens
import com.dot.gallery.ui.core.icons.VisualSearch

/**
 * Visual-search providers are ordinary apps that accept `ACTION_SEND` image shares — there is no
 * dedicated intent, so discovery and the picker both run on the share-target query. A curated
 * catalog gives the well-known visual-search apps a friendly name, brand ordering and (for Lens)
 * the monochrome glyph; every other installed share target remains selectable under its app label.
 */
enum class KnownVisualSearchProvider(val packageName: String, val brandName: String) {
    GOOGLE_LENS("com.google.ar.lens", "Google Lens"),
    GOOGLE_APP("com.google.android.googlequicksearchbox", "Google"),
    BING("com.microsoft.bing", "Bing"),
    YANDEX("ru.yandex.searchplugin", "Yandex"),
    PINTEREST("com.pinterest", "Pinterest");

    companion object {
        fun forPackage(packageName: String): KnownVisualSearchProvider? =
            entries.firstOrNull { it.packageName == packageName }
    }
}

/** One installed activity that can receive an image share. */
data class VisualSearchTarget(
    val packageName: String,
    val activityName: String,
    val label: String,
    val known: KnownVisualSearchProvider?,
) {
    /** Brand name for known providers (the app label can be terse, e.g. "Lens"), else app label. */
    val displayName: String get() = known?.brandName ?: label
}

/** Button glyph: the true brand-colored Lens icon when Lens is selected, generic otherwise. */
val VisualSearchTarget.icon: ImageVector
    get() = if (known == KnownVisualSearchProvider.GOOGLE_LENS) {
        GalleryIcons.GoogleLens
    } else {
        GalleryIcons.VisualSearch
    }

/**
 * Whether the button glyph should be tinted to the surrounding content color. The Lens glyph
 * carries brand colors and renders untinted; the generic icon is monochrome and tinted.
 */
val VisualSearchTarget.tintIcon: Boolean
    get() = known != KnownVisualSearchProvider.GOOGLE_LENS

fun Context.discoverVisualSearchTargets(): List<VisualSearchTarget> {
    val intent = Intent(Intent.ACTION_SEND).apply { type = "image/*" }
    return packageManager.queryIntentActivities(intent, 0)
        .filter { it.activityInfo != null }
        .filterNot { it.activityInfo.packageName == BuildConfig.APPLICATION_ID }
        .distinctBy { it.activityInfo.packageName }
        .map { resolveInfo ->
            VisualSearchTarget(
                packageName = resolveInfo.activityInfo.packageName,
                activityName = resolveInfo.activityInfo.name,
                label = resolveInfo.loadLabel(packageManager).toString(),
                known = KnownVisualSearchProvider.forPackage(resolveInfo.activityInfo.packageName),
            )
        }
        .sortedWith(
            compareBy(
                // Lens leads, then the rest of the catalog in declaration order, then A–Z.
                { it.known != KnownVisualSearchProvider.GOOGLE_LENS },
                { it.known?.ordinal ?: Int.MAX_VALUE },
                { it.label.lowercase() },
            )
        )
}

/**
 * Picks the effective target: the stored package when it is still installed, otherwise the best
 * auto choice (the sorted list already puts Lens first). `null` when nothing is installed.
 */
fun resolveVisualSearchTarget(
    targets: List<VisualSearchTarget>,
    storedPackage: String,
): VisualSearchTarget? {
    if (targets.isEmpty()) return null
    if (storedPackage.isNotEmpty()) {
        targets.firstOrNull { it.packageName == storedPackage }?.let { return it }
    }
    return targets.first()
}

/**
 * Formats a visual-search provider is expected to decode natively. Anything else (RAW, TIFF, PSD,
 * JP2, JXL, SVG, …) is exotic and goes through the ask/always/never conversion policy.
 */
val VISUAL_SEARCH_SAFE_MIMES = setOf(
    "image/jpeg",
    "image/png",
    "image/webp",
    "image/gif",
    "image/bmp",
    "image/heic",
    "image/heif",
    "image/avif",
)

fun needsVisualSearchConvert(mimeType: String?, isImage: Boolean): Boolean =
    isImage && mimeType?.lowercase() !in VISUAL_SEARCH_SAFE_MIMES

val Media.needsVisualSearchConvert: Boolean
    get() = needsVisualSearchConvert(mimeType, isImage)
