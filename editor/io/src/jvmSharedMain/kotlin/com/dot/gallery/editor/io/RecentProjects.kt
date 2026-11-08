package com.dot.gallery.editor.io

import com.dot.gallery.editor.core.project.ProjectJson

/**
 * The desktop "reopen recent" index: one `recents.json` inside the
 * [FileProjectStore] root mapping each persisted project file back to the
 * media reference it edits — the workbench menu lists the entries, and
 * reopening the reference finds its project file by the same name the
 * host computed. Entries are newest-first, deduplicated by reference and
 * capped, so a long-lived install never grows the index unbounded; the
 * file itself rides the store's atomic write like any project document.
 *
 * The index is a convenience, never an authority: a missing or corrupt
 * index reads as empty rather than failing an editor open, and a stale
 * entry simply opens its source without a recipe.
 */
class RecentProjects(
    private val store: FileProjectStore,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES
) {

    /** One indexed project: a display name, its source and its file. */
    data class Entry(
        val name: String,
        val reference: String,
        val projectFile: String
    )

    /** Newest-first; a missing or corrupt index is an empty list. */
    @Synchronized
    fun list(): List<Entry> {
        val json = store.read(FILE) ?: return emptyList()
        val root = try {
            ProjectJson.parse(json) as? ProjectJson.Object
        } catch (error: IllegalArgumentException) {
            null
        } ?: return emptyList()
        return try {
            root.reqArray("items").values.mapNotNull { value ->
                val obj = value as? ProjectJson.Object ?: return@mapNotNull null
                Entry(
                    name = obj.reqString("name"),
                    reference = obj.reqString("reference"),
                    projectFile = obj.reqString("projectFile")
                )
            }
        } catch (error: IllegalArgumentException) {
            emptyList()
        }
    }

    /**
     * Moves [entry] to the front, replacing any same-reference entry —
     * reopening a project re-timestamps it rather than duplicating it.
     */
    @Synchronized
    fun record(entry: Entry) {
        val next = buildList {
            add(entry)
            list().forEach { existing ->
                if (existing.reference != entry.reference && size < maxEntries) {
                    add(existing)
                }
            }
        }
        store.write(FILE, encode(next))
    }

    /** Drops [reference]'s entry — an emptied project leaves the index. */
    @Synchronized
    fun remove(reference: String) {
        val remaining = list().filter { it.reference != reference }
        if (remaining.isEmpty()) {
            store.delete(FILE)
        } else {
            store.write(FILE, encode(remaining))
        }
    }

    @Synchronized
    fun clear() {
        store.delete(FILE)
    }

    private fun encode(entries: List<Entry>): String {
        val out = ProjectJson.Writer()
        out.beginObject()
        out.key("version").value(VERSION)
        out.key("items").beginArray()
        for (entry in entries) {
            out.beginObject()
            out.key("name").value(entry.name)
            out.key("reference").value(entry.reference)
            out.key("projectFile").value(entry.projectFile)
            out.endObject()
        }
        out.endArray()
        out.endObject()
        return out.toString()
    }

    private companion object {
        const val FILE = "recents.json"
        const val VERSION = 1
        const val DEFAULT_MAX_ENTRIES = 16
    }
}
