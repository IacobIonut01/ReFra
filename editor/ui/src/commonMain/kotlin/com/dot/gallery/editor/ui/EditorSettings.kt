package com.dot.gallery.editor.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.dot.gallery.editor.core.model.PhotoExportFormat

/**
 * The editors' user preferences — the rows of the Photo editor and Video
 * editor settings pages. Each value seeds or gates existing behaviour; none
 * of it enters a recipe or a project file.
 */
@Immutable
data class EditorSettings(
    /** The histogram's starting channel group. */
    val histogramMode: PhotoHistogramMode = PhotoHistogramMode.Rgb,
    /** Whether the histogram starts with its clipped-mass overlay. */
    val histogramClipping: Boolean = false,
    /** The straighten/perspective grid's starting state. */
    val straightenGrid: Boolean = true,
    /** Save copy's container for non-RAW photos; null follows the source. */
    val copyFormat: PhotoExportFormat? = null,
    /** A developed RAW's save container; null asks every time. */
    val rawFormat: PhotoExportFormat? = null,
    /** Dragged clips snap to the lane start and neighbouring clip edges. */
    val videoSnap: Boolean = true,
    /** Inserting on the main lane shifts later clips. */
    val videoRipple: Boolean = true,
    /** New audio lands on the selected empty audio track instead of a new one. */
    val videoFillEmptyAudioTracks: Boolean = true
)

/**
 * Durable storage for [EditorSettings]. The editor ships an in-memory store
 * by default; hosts provide a persistent one through [LocalEditorSettingsStore].
 */
interface EditorSettingsPersistence {
    fun load(): EditorSettings
    fun save(settings: EditorSettings)
}

/** The editors' observable settings — screens read [settings] and write through [update]. */
class EditorSettingsStore(
    private val persistence: EditorSettingsPersistence? = null
) {
    var settings by mutableStateOf(persistence?.load() ?: EditorSettings())
        private set

    fun update(transform: (EditorSettings) -> EditorSettings) {
        val next = transform(settings)
        if (next == settings) return
        settings = next
        persistence?.save(next)
    }
}

val LocalEditorSettingsStore =
    staticCompositionLocalOf<EditorSettingsStore?> { null }

/** The host's store, or one that lives as long as this composition. */
@Composable
internal fun rememberEditorSettingsStore(): EditorSettingsStore {
    val provided = LocalEditorSettingsStore.current
    val local = remember { EditorSettingsStore() }
    return provided ?: local
}
