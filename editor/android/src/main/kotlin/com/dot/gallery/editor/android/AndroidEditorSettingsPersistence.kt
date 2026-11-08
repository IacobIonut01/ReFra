package com.dot.gallery.editor.android

import android.content.Context
import com.dot.gallery.editor.core.model.PhotoExportFormat
import com.dot.gallery.editor.ui.EditorSettings
import com.dot.gallery.editor.ui.EditorSettingsPersistence
import com.dot.gallery.editor.ui.PhotoHistogramMode
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Preferences-backed storage for the editors' settings pages. One key per
 * setting; an unreadable or unknown value falls back to its default, and a
 * failed write is reported but never blocks the editor.
 */
class AndroidEditorSettingsPersistence(context: Context) : EditorSettingsPersistence {

    private val preferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun load(): EditorSettings {
        val defaults = EditorSettings()
        return runCatching {
            EditorSettings(
                histogramMode = enumOrNull<PhotoHistogramMode>(preferences.getString(KEY_HISTOGRAM, null))
                    ?: defaults.histogramMode,
                histogramClipping = preferences.getBoolean(KEY_CLIPPING, defaults.histogramClipping),
                straightenGrid = preferences.getBoolean(KEY_GRID, defaults.straightenGrid),
                copyFormat = enumOrNull<PhotoExportFormat>(preferences.getString(KEY_COPY_FORMAT, null)),
                rawFormat = enumOrNull<PhotoExportFormat>(preferences.getString(KEY_RAW_FORMAT, null)),
                videoSnap = preferences.getBoolean(KEY_SNAP, defaults.videoSnap),
                videoRipple = preferences.getBoolean(KEY_RIPPLE, defaults.videoRipple),
                videoFillEmptyAudioTracks =
                    preferences.getBoolean(KEY_FILL_TRACKS, defaults.videoFillEmptyAudioTracks)
            )
        }.onFailure { log("read", it) }.getOrDefault(defaults)
    }

    override fun save(settings: EditorSettings) {
        runCatching {
            preferences.edit()
                .putString(KEY_HISTOGRAM, settings.histogramMode.name)
                .putBoolean(KEY_CLIPPING, settings.histogramClipping)
                .putBoolean(KEY_GRID, settings.straightenGrid)
                .putString(KEY_COPY_FORMAT, settings.copyFormat?.name)
                .putString(KEY_RAW_FORMAT, settings.rawFormat?.name)
                .putBoolean(KEY_SNAP, settings.videoSnap)
                .putBoolean(KEY_RIPPLE, settings.videoRipple)
                .putBoolean(KEY_FILL_TRACKS, settings.videoFillEmptyAudioTracks)
                .apply()
        }.onFailure { log("write", it) }
    }

    private inline fun <reified T : Enum<T>> enumOrNull(name: String?): T? =
        name?.let { value -> enumValues<T>().firstOrNull { it.name == value } }

    private fun log(what: String, error: Throwable) {
        Logger.getLogger(TAG).log(Level.WARNING, "editor settings $what failed", error)
    }

    private companion object {
        const val TAG = "editor.settings"
        const val PREFS = "editor_settings"
        const val KEY_HISTOGRAM = "histogram_mode"
        const val KEY_CLIPPING = "histogram_clipping"
        const val KEY_GRID = "straighten_grid"
        const val KEY_COPY_FORMAT = "copy_format"
        const val KEY_RAW_FORMAT = "raw_format"
        const val KEY_SNAP = "video_snap"
        const val KEY_RIPPLE = "video_ripple"
        const val KEY_FILL_TRACKS = "video_fill_empty_audio_tracks"
    }
}
