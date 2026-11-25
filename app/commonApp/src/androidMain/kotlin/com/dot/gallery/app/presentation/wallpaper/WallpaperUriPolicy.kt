/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */
package com.dot.gallery.app.presentation.wallpaper

internal enum class CallerUriAccess {
    GRANTED,
    DENIED,
    UNKNOWN
}

internal object WallpaperUriPolicy {

    /**
     * Android before API 35 cannot reliably tell whether the launching app could read a URI,
     * so [CallerUriAccess.UNKNOWN] falls back to requiring the caller's read-grant flag. ReFra's
     * own FileProvider is never forwarded for external callers: they can name private files that
     * they were never granted, while ReFra can always redeem the outgoing grant itself.
     */
    fun shouldForward(
        scheme: String?,
        authority: String?,
        ownProviderAuthority: String?,
        callerIsSelf: Boolean,
        callerAccess: CallerUriAccess,
        incomingHasReadGrant: Boolean,
    ): Boolean {
        if (!scheme.equals(CONTENT_SCHEME, ignoreCase = true)) return false
        if (authority.isNullOrBlank()) return false
        if (callerIsSelf) return true
        if (ownProviderAuthority.isNullOrBlank()) return false
        if (isOwnProviderAuthority(authority, ownProviderAuthority)) return false
        return when (callerAccess) {
            CallerUriAccess.GRANTED -> true
            CallerUriAccess.DENIED -> false
            CallerUriAccess.UNKNOWN -> incomingHasReadGrant
        }
    }

    private fun isOwnProviderAuthority(authority: String, ownProviderAuthority: String): Boolean {
        if (authority.equals(ownProviderAuthority, ignoreCase = true)) return true
        val separator = authority.lastIndexOf('@')
        if (separator <= 0 || separator == authority.lastIndex) return false
        val userId = authority.substring(0, separator).toIntOrNull() ?: return false
        return userId >= 0 && authority.substring(separator + 1).equals(
            ownProviderAuthority,
            ignoreCase = true,
        )
    }

    private const val CONTENT_SCHEME = "content"
}
