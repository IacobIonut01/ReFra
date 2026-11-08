package com.dot.gallery.editor.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.dot.gallery.editor.core.model.ColorAdjustments
import com.dot.gallery.editor.core.model.PhotoLookId
import kotlin.random.Random

/**
 * A user-authored look: the colour grade and the finishing look (with its
 * blend) captured together, so applying it reproduces the grade in one
 * edit. Built-in looks are the fixed [PhotoLookId] presets; these are the
 * ones the user saves from the Looks tool.
 */
data class LookPreset(
    val id: String,
    val name: String,
    val color: ColorAdjustments = ColorAdjustments(),
    val lookId: PhotoLookId = PhotoLookId.None,
    val lookAmount: Float = 1f
)

/**
 * Durable storage for the user's presets. The editor ships an in-memory
 * store by default; Android hosts provide a preferences-backed one through
 * [LocalEditorLookPresetStore] so presets survive the session.
 */
interface LookPresetPersistence {
    fun load(): List<LookPreset>
    fun save(presets: List<LookPreset>)
}

/**
 * The user's saved looks — observable so the My-presets strip follows
 * create/rename/delete immediately.
 */
class EditorLookPresetStore(
    private val persistence: LookPresetPersistence? = null
) {
    var presets by mutableStateOf(persistence?.load().orEmpty())
        private set

    fun add(preset: LookPreset) {
        presets = presets + preset
        save()
    }

    fun rename(id: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        presets = presets.map { if (it.id == id) it.copy(name = trimmed) else it }
        save()
    }

    /** Re-captures a preset's grade (the "update with current" action). */
    fun update(
        id: String,
        color: ColorAdjustments,
        lookId: PhotoLookId,
        lookAmount: Float
    ) {
        presets = presets.map {
            if (it.id == id) {
                it.copy(color = color, lookId = lookId, lookAmount = lookAmount)
            } else {
                it
            }
        }
        save()
    }

    fun remove(id: String) {
        presets = presets.filterNot { it.id == id }
        save()
    }

    fun newId(): String =
        "preset-${Random.nextLong().toULong().toString(16)}"

    private fun save() {
        persistence?.save(presets)
    }
}

/** Null outside a host that can persist presets; the screen then owns an
 *  in-memory store for its own lifetime. */
val LocalEditorLookPresetStore =
    staticCompositionLocalOf<EditorLookPresetStore?> { null }
