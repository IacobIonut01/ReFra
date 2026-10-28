/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.backup

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import java.io.IOException

class BackupSectionIoTest {

    @Test
    fun writerAndReaderRoundTripAndVerifyIntegrity() {
        val file = File.createTempFile("backup-section", ".jsonl")
        try {
            val descriptor = file.outputStream().use { output ->
                val writer = SectionWriter(output)
                writer.write(SettingsRecord.serializer(), SettingsRecord("dark_mode", SettingValue("b", "true")))
                writer.write(SettingsRecord.serializer(), SettingsRecord("theme_color_seed", SettingValue("s", "blue")))
                writer.finish().copy(file = file.name)
            }
            assertEquals(2, descriptor.count)
            assertEquals(file.length(), descriptor.bytes)

            val read = mutableListOf<SettingsRecord>()
            val count = runBlocking {
                SectionReader(file, descriptor).read(SettingsRecord.serializer()) { read += it }
            }
            assertEquals(2, count)
            assertEquals("dark_mode", read.first().name)

            file.writeText(file.readText().replace("dark_mode", "light_mode"))
            assertThrows(IOException::class.java) {
                runBlocking {
                    SectionReader(file, descriptor).read(SettingsRecord.serializer()) { }
                }
            }
        } finally {
            file.delete()
        }
    }
}
