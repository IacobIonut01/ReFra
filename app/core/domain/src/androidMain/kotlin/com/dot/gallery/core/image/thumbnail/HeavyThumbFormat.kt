/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.image.thumbnail

import android.content.ContentResolver
import android.net.Uri
import com.dot.gallery.core.decoder.format.ImageFormatSniffer
import com.dot.gallery.core.decoder.glide.HeifSniffer
import com.dot.gallery.core.decoder.glide.HeifUriProbe
import com.dot.gallery.core.decoder.glide.RawMime
import com.dot.gallery.core.util.and
import com.dot.gallery.core.util.or
import com.dot.gallery.domain.util.isJp2
import com.dot.gallery.domain.util.isJxl
import com.dot.gallery.domain.util.isPsd


/**
 * formats the platform thumbnailer cannot serve (`ContentResolver.loadThumbnail` throws)
 * and the app decodes in software — the expensive decodes [LocalThumbnailStore] exists to
 * amortize across Glide disk-cache evictions and process death.
 */
enum class HeavyThumbFormat { JXL, PSD, JP2, TIFF, RAW, HEIF_SW }