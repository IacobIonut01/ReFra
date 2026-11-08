package com.dot.gallery.editor.desktop

import com.dot.gallery.editor.core.project.ProjectChecksum
import com.dot.gallery.editor.io.FileProjectStore
import java.io.File

/**
 * The desktop project home: `~/.refra-projects` — app-support style,
 * private to the user, never beside the media where a library cleanup
 * could delete it. Project files are named `<kind>-<checksum>.json` so
 * every source's last committed document has one stable slot, and the
 * recents index inside the same root maps files back to their source
 * references for the workbench's reopen menu.
 */
internal fun desktopProjectStore(): FileProjectStore =
    FileProjectStore(File(System.getProperty("user.home"), ".refra-projects"))

internal fun projectFileName(kind: String, reference: String): String =
    "$kind-${ProjectChecksum.of(reference)}.json"
