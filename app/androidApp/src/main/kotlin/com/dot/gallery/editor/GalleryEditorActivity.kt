package com.dot.gallery.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.dot.gallery.core.activeDataStore
import com.dot.gallery.core.logging.printError
import com.dot.gallery.core.ui.settings.LocalSettingsStore
import com.dot.gallery.core.ui.settings.rememberPreference
import com.dot.gallery.core.ui.util.supportsBlur
import com.dot.gallery.domain.settings.DataStoreSettingsStore
import com.dot.gallery.domain.settings.SettingsKeys
import com.dot.gallery.editor.android.EditorActivity
import com.dot.gallery.ui.theme.AppThemeLocals
import com.dot.gallery.editor.ui.EditorHostTools
import com.dot.gallery.editor.ui.EditorUiPreferences
import com.dot.gallery.editor.ui.EditorWorkspaceTheme
import com.dot.gallery.editor.ui.LocalEditorSettingsRenderer
import com.dot.gallery.editor.ui.LocalEditorUiPreferences
import com.dot.gallery.ui.theme.GalleryTheme

/**
 * The Gallery-hosted entry point for the standalone photo editor: the same
 * intent contract as [EditorActivity] (exported, `ACTION_EDIT` on JPEG/PNG
 * `content://` sources, new-copy result) but themed with the app theme and
 * logged through the app's diagnostics pipeline. The legacy in-app editor
 * stays untouched; migrating it is a later milestone.
 */
class GalleryEditorActivity : EditorActivity() {

    @Composable
    override fun EditorHostTheme(content: @Composable () -> Unit) {
        val settingsStore = remember {
            DataStoreSettingsStore(applicationContext.activeDataStore)
        }
        CompositionLocalProvider(LocalSettingsStore provides settingsStore) {
            AppThemeLocals {
                GalleryTheme(darkTheme = true) {
                    EditorWorkspaceTheme {
                        val allowBlur by rememberPreference(
                            SettingsKeys.allowBlur.withDefault(supportsBlur)
                        )
                        CompositionLocalProvider(
                            LocalEditorUiPreferences provides
                                EditorUiPreferences(blurEnabled = allowBlur),
                            // Settings pages render with the app's settings DSL.
                            LocalEditorSettingsRenderer provides GalleryEditorSettingsRenderer
                        ) {
                            content()
                        }
                    }
                }
            }
        }
    }

    override fun editorHostTools(): EditorHostTools =
        galleryEditorHostTools(this, ::openInExternalEditor)

    override fun reportError(error: Throwable) {
        printError(TAG, "Photo editor error", error)
    }

    private companion object {
        const val TAG = "editor.photo"
    }
}
