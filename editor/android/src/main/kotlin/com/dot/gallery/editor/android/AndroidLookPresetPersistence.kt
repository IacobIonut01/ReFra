package com.dot.gallery.editor.android

import android.content.Context
import com.dot.gallery.editor.core.model.ColorAdjustments
import com.dot.gallery.editor.core.model.PhotoLookId
import com.dot.gallery.editor.ui.LookPreset
import com.dot.gallery.editor.ui.LookPresetPersistence
import java.util.logging.Level
import java.util.logging.Logger
import org.json.JSONArray
import org.json.JSONObject

/**
 * Preferences-backed storage for the editor's user looks. One JSON array
 * under a single key; a corrupt entry is skipped rather than taken down
 * the whole strip, and a failed write is reported but never blocks the
 * editor.
 */
class AndroidLookPresetPersistence(context: Context) : LookPresetPersistence {

    private val preferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun load(): List<LookPreset> {
        val raw = runCatching { preferences.getString(KEY, null) }
            .onFailure { log("read", it) }
            .getOrNull() ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        LookPreset(
                            id = item.getString("id"),
                            name = item.getString("name"),
                            color = ColorAdjustments(
                                exposureStops = item.getDouble("exposure").toFloat(),
                                contrast = item.getDouble("contrast").toFloat(),
                                saturation = item.getDouble("saturation").toFloat(),
                                highlights = item.getDouble("highlights").toFloat(),
                                shadows = item.getDouble("shadows").toFloat(),
                                whites = item.getDouble("whites").toFloat(),
                                blacks = item.getDouble("blacks").toFloat()
                            ),
                            lookId = runCatching {
                                PhotoLookId.valueOf(item.getString("look"))
                            }.getOrDefault(PhotoLookId.None),
                            lookAmount = item.getDouble("amount").toFloat()
                        )
                    )
                }
            }
        }.onFailure { log("parse", it) }.getOrDefault(emptyList())
    }

    override fun save(presets: List<LookPreset>) {
        val array = JSONArray()
        for (preset in presets) {
            array.put(
                JSONObject().apply {
                    put("id", preset.id)
                    put("name", preset.name)
                    put("exposure", preset.color.exposureStops.toDouble())
                    put("contrast", preset.color.contrast.toDouble())
                    put("saturation", preset.color.saturation.toDouble())
                    put("highlights", preset.color.highlights.toDouble())
                    put("shadows", preset.color.shadows.toDouble())
                    put("whites", preset.color.whites.toDouble())
                    put("blacks", preset.color.blacks.toDouble())
                    put("look", preset.lookId.name)
                    put("amount", preset.lookAmount.toDouble())
                }
            )
        }
        runCatching {
            preferences.edit().putString(KEY, array.toString()).apply()
        }.onFailure { log("write", it) }
    }

    private fun log(step: String, error: Throwable) {
        Logger.getLogger(TAG).log(Level.WARNING, "look presets $step failed", error)
    }

    private companion object {
        const val TAG = "editor.lookPresets"
        const val PREFS = "editor_look_presets"
        const val KEY = "presets"
    }
}
