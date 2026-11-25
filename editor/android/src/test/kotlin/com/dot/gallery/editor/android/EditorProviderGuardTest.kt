package com.dot.gallery.editor.android

import org.junit.Assert.assertEquals
import org.junit.Test

class EditorProviderGuardTest {

    @Test
    fun userQualifiedAuthorityResolvesToProviderAuthority() {
        assertEquals(
            "com.dot.gallery.media_provider",
            EditorProviderGuard.withoutUserId("10@com.dot.gallery.media_provider"),
        )
    }

    @Test
    fun plainAndNonUserAuthoritiesStayUnchanged() {
        listOf(
            "com.dot.gallery.media_provider",
            "backup@media.example",
            "10@",
            "@com.dot.gallery.media_provider",
            "-1@com.dot.gallery.media_provider",
        ).forEach { authority ->
            assertEquals(authority, EditorProviderGuard.withoutUserId(authority))
        }
    }
}
