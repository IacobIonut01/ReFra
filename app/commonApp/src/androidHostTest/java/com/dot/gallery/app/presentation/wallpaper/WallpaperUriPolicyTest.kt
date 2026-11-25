package com.dot.gallery.app.presentation.wallpaper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperUriPolicyTest {

    @Test
    fun rejectsOwnFileProviderForExternalCaller() {
        assertFalse(
            WallpaperUriPolicy.shouldForward(
                scheme = "content",
                authority = OWN_AUTHORITY,
                ownProviderAuthority = OWN_AUTHORITY,
                callerIsSelf = false,
                callerAccess = CallerUriAccess.GRANTED,
                incomingHasReadGrant = true,
            )
        )
    }

    @Test
    fun rejectsUserQualifiedOwnFileProviderForExternalCaller() {
        assertFalse(
            WallpaperUriPolicy.shouldForward(
                scheme = "content",
                authority = "10@$OWN_AUTHORITY",
                ownProviderAuthority = OWN_AUTHORITY,
                callerIsSelf = false,
                callerAccess = CallerUriAccess.UNKNOWN,
                incomingHasReadGrant = true,
            )
        )
    }

    @Test
    fun allowsOwnFileProviderForInternalLaunch() {
        assertTrue(
            WallpaperUriPolicy.shouldForward(
                scheme = "content",
                authority = OWN_AUTHORITY,
                ownProviderAuthority = OWN_AUTHORITY,
                callerIsSelf = true,
                callerAccess = CallerUriAccess.GRANTED,
                incomingHasReadGrant = true,
            )
        )
    }

    @Test
    fun rejectsFileUriEvenWithGrant() {
        assertFalse(
            WallpaperUriPolicy.shouldForward(
                scheme = "file",
                authority = "",
                ownProviderAuthority = OWN_AUTHORITY,
                callerIsSelf = false,
                callerAccess = CallerUriAccess.GRANTED,
                incomingHasReadGrant = true,
            )
        )
    }

    @Test
    fun legacyCallerNeedsIncomingReadGrant() {
        assertFalse(
            WallpaperUriPolicy.shouldForward(
                scheme = "content",
                authority = "media",
                ownProviderAuthority = OWN_AUTHORITY,
                callerIsSelf = false,
                callerAccess = CallerUriAccess.UNKNOWN,
                incomingHasReadGrant = false,
            )
        )
        assertTrue(
            WallpaperUriPolicy.shouldForward(
                scheme = "content",
                authority = "media",
                ownProviderAuthority = OWN_AUTHORITY,
                callerIsSelf = false,
                callerAccess = CallerUriAccess.UNKNOWN,
                incomingHasReadGrant = true,
            )
        )
    }

    @Test
    fun verifiedCallerAccessDoesNotNeedLegacyFlag() {
        assertTrue(
            WallpaperUriPolicy.shouldForward(
                scheme = "content",
                authority = "media",
                ownProviderAuthority = OWN_AUTHORITY,
                callerIsSelf = false,
                callerAccess = CallerUriAccess.GRANTED,
                incomingHasReadGrant = false,
            )
        )
    }

    @Test
    fun verifiedCallerDenialWinsOverLegacyFlag() {
        assertFalse(
            WallpaperUriPolicy.shouldForward(
                scheme = "content",
                authority = "media",
                ownProviderAuthority = OWN_AUTHORITY,
                callerIsSelf = false,
                callerAccess = CallerUriAccess.DENIED,
                incomingHasReadGrant = true,
            )
        )
    }

    @Test
    fun missingOwnAuthorityFailsClosedForExternalCaller() {
        assertFalse(
            WallpaperUriPolicy.shouldForward(
                scheme = "content",
                authority = "media",
                ownProviderAuthority = "",
                callerIsSelf = false,
                callerAccess = CallerUriAccess.GRANTED,
                incomingHasReadGrant = true,
            )
        )
    }

    @Test
    fun launchTokenIsSingleUseAndBoundToUri() {
        val uri = "content://$OWN_AUTHORITY/shared_cache/media/photo.jpg"
        val token = WallpaperLaunchTokens.issue(uri)

        assertFalse(WallpaperLaunchTokens.consume("content://$OWN_AUTHORITY/shared_cache/media/other.jpg", token))
        assertTrue(WallpaperLaunchTokens.consume(uri, token))
        assertFalse(WallpaperLaunchTokens.consume(uri, token))
    }

    private companion object {
        const val OWN_AUTHORITY = "com.dot.gallery.media_provider"
    }
}
