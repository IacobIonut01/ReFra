package com.dot.gallery

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guard: Compose Multiplatform resources (the composeResources values XML files) are not
 * Android resources. The CMP resource generator only unescapes \n, \t, \uXXXX and
 * \\, and its argument substitution only handles %N$s / %N$d — so Android-style
 * \' and \" render with a literal backslash and %% renders literally. Crowdin's
 * Android XML export writes those escapes, so this test fails the build if they
 * creep back into any composeResources catalog.
 *
 * androidMain/res/values* files are real AAPT resources and MUST keep \' — they
 * are excluded, as is everything under build/.
 */
class ComposeResourcesEscapeGuardTest {

    private fun repoRoot(): File {
        var dir = File(System.getProperty("user.dir")).absoluteFile
        repeat(8) {
            if (File(dir, "crowdin.yml").isFile || File(dir, "settings.gradle.kts").isFile) return dir
            dir = dir.parentFile ?: return dir
        }
        return dir
    }

    private fun isComposeResourcesXml(file: File): Boolean {
        val path = file.invariantSeparatorsPath
        if (!path.contains("/composeResources/")) return false
        if (!Regex("/values[^/]*/[^/]+\\.xml$").containsMatchIn(path)) return false
        val segments = path.split("/")
        if ("build" in segments) return false
        // androidMain/res is AAPT territory, never composeResources — belt & braces.
        val i = segments.indexOf("composeResources")
        if (i > 0 && segments[i - 1] == "res") return false
        return true
    }

    @Test
    fun composeResourcesHaveNoAndroidStyleEscapes() {
        val root = repoRoot()
        val offenders = mutableListOf<String>()
        var scanned = 0

        fun visit(dir: File) {
            dir.listFiles()?.forEach { child ->
                when {
                    child.isDirectory -> {
                        if (child.name !in SKIPPED_DIRS) visit(child)
                    }
                    child.isFile && isComposeResourcesXml(child) -> {
                        scanned++
                        val rel = child.relativeTo(root).invariantSeparatorsPath
                        var lastName = "?"
                        child.readLines().forEachIndexed { index, line ->
                            NAME_REGEX.find(line)?.let { lastName = it.groupValues[1] }
                            // \' or \" not itself escaped (a preceding backslash means \\
                            // is an escaped literal backslash and the quote is plain text).
                            QUOTE_ESCAPE_REGEX.findAll(line).forEach { m ->
                                offenders += "$rel:${index + 1}: $lastName: ${m.value}"
                            }
                            if (line.contains("%%")) {
                                offenders += "$rel:${index + 1}: $lastName: %%"
                            }
                        }
                    }
                }
            }
        }
        visit(root)

        assertTrue(
            "Scanned $scanned composeResources XML files. Found Android-style escapes " +
                "that Compose Multiplatform renders literally (use plain ' and \", single %):\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    private companion object {
        val NAME_REGEX = Regex("""name="([^"]+)"""")
        val QUOTE_ESCAPE_REGEX = Regex("""(?<!\\)\\['"]""")
        val SKIPPED_DIRS = setOf(".git", "build", ".gradle", "node_modules", ".idea", ".cxx")
    }
}
