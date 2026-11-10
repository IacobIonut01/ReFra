/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr.model

/**
 * Whether packs may be downloaded. Builds without network access (`OFFLINE=true`, no INTERNET
 * permission) never schedule a download; they only accept picked files.
 */
data class OcrInstallPolicy(val downloadsAllowed: Boolean) {
    companion object {
        fun of(offlineBuild: Boolean, hasInternetPermission: Boolean) =
            OcrInstallPolicy(downloadsAllowed = !offlineBuild && hasInternetPermission)
    }
}
