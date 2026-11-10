/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.data

import java.io.File

/** What sits at a database path, judged from the file header alone. */
enum class DatabaseFileState {
    /** No file, or an empty one: any driver can create it. */
    ABSENT,

    /** A plain SQLite file, readable without a key. */
    PLAINTEXT,

    /** Anything else: a SQLCipher file, or bytes no driver can open without its key. */
    NOT_PLAINTEXT;

    companion object {
        private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

        fun of(file: File): DatabaseFileState {
            if (!file.exists() || file.length() == 0L) return ABSENT
            val header = ByteArray(SQLITE_HEADER.size)
            val read = file.inputStream().use { input ->
                var total = 0
                while (total < header.size) {
                    val n = input.read(header, total, header.size - total)
                    if (n < 0) break
                    total += n
                }
                total
            }
            return if (read == header.size && header.contentEquals(SQLITE_HEADER)) PLAINTEXT else NOT_PLAINTEXT
        }

        /** The sidecar files SQLite keeps next to [db]. */
        fun companions(db: File): List<File> =
            listOf("-wal", "-shm", "-journal").map { File(db.absolutePath + it) }

        /**
         * Moves [db] and its sidecar files to `<name>.undecryptable`, replacing an earlier
         * copy, so a fresh database can be created at [db]. Returns the moved file.
         */
        fun moveAside(db: File): File {
            val target = File(db.parentFile, "${db.name}.undecryptable")
            (listOf(target) + companions(target)).forEach { it.delete() }
            if (db.exists() && !db.renameTo(target)) {
                db.copyTo(target, overwrite = true)
                db.delete()
            }
            companions(db).zip(companions(target)).forEach { (from, to) ->
                if (from.exists() && !from.renameTo(to)) from.delete()
            }
            return target
        }
    }
}
