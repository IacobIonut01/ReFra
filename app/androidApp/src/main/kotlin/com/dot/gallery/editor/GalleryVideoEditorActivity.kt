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
import com.dot.gallery.editor.android.VideoEditorActivity
import com.dot.gallery.ui.theme.AppThemeLocals
import com.dot.gallery.editor.ui.EditorUiPreferences
import com.dot.gallery.editor.ui.EditorWorkspaceTheme
import com.dot.gallery.editor.ui.LocalEditorSettingsRenderer
import com.dot.gallery.editor.ui.LocalEditorUiPreferences
import com.dot.gallery.ui.theme.GalleryTheme

/**
 * The Gallery-hosted entry point for the standalone video editor: the same
 * intent contract as [VideoEditorActivity] (exported, edit actions on
 * any video `content://` source, new-copy result) but themed with the
 * app theme and logged through the app's diagnostics pipeline.
 */
class GalleryVideoEditorActivity : VideoEditorActivity() {

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

    override fun reportError(error: Throwable) {
        printError(TAG, "Video editor error", error)
    }

    private companion object {
        const val TAG = "editor.video"
    }
}
