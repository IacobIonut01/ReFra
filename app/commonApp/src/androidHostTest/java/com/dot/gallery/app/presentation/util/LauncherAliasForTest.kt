package com.dot.gallery.app.presentation.util

import com.dot.gallery.domain.settings.ALIAS_GALLERY
import com.dot.gallery.domain.settings.ALIAS_GALLERY_MONO
import com.dot.gallery.domain.settings.ALIAS_MONO
import com.dot.gallery.domain.settings.ALIAS_REFRA
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherAliasForTest {

    @Test
    fun `every name and logo combination maps to a distinct declared alias`() {
        val names = listOf(ALIAS_REFRA, ALIAS_GALLERY)
        val logos = listOf(ALIAS_REFRA, ALIAS_GALLERY, ALIAS_MONO, ALIAS_GALLERY_MONO)
        val resolved = names.flatMap { name ->
            logos.map { logo -> launcherAliasFor(name, logo) }
        }
        resolved.forEach { alias ->
            assertTrue("$alias is not in LAUNCHER_ALIASES", alias in LAUNCHER_ALIASES)
        }
        // Every combination must reach its own alias, otherwise two picker states
        // would enable the same launcher entry.
        assertEquals(LAUNCHER_ALIASES.size, resolved.distinct().size)
        assertEquals(LAUNCHER_ALIASES.toSet(), resolved.toSet())
    }

    @Test
    fun `every launcher alias is declared in the manifest`() {
        val manifest = manifestFile() ?: return
        val declared = Regex("""android:name="(?:com\.dot\.gallery\.)?\.?(Launcher_[A-Za-z_]+)"""")
            .findAll(manifest.readText())
            .map { it.groupValues[1] }
            .toSet()
        LAUNCHER_ALIASES.forEach { alias ->
            assertTrue("$alias missing from AndroidManifest.xml", alias in declared)
        }
    }

    private fun manifestFile(): File? {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(8) {
            val candidate = File(dir, "app/androidApp/src/main/AndroidManifest.xml")
            if (candidate.isFile) return candidate
            dir = dir.parentFile ?: return null
        }
        return null
    }
}
