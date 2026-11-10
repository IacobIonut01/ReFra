/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.core.capabilities

import com.dot.gallery.cloud.core.MediaCapabilityProvider
import com.dot.gallery.domain.model.Media

/**
 * Providers that search the text their server recognized in photos (Immich OCR). Results are the
 * account's own assets; servers without OCR answer with an error, which callers treat as no hits.
 */
interface TextSearchCapableProvider : MediaCapabilityProvider {
    suspend fun textSearch(query: String): Result<List<Media>>
}
