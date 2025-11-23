package com.dot.gallery.feature_node.presentation.settings.subsettings

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.work.WorkManager
import com.dot.gallery.R
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.Position
import com.dot.gallery.core.Settings
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.core.navigate
import com.dot.gallery.core.workers.forceMetadataCollect
import com.dot.gallery.feature_node.presentation.settings.components.BaseSettingsScreen
import com.dot.gallery.feature_node.presentation.settings.components.rememberPreference
import com.dot.gallery.feature_node.presentation.settings.components.rememberSwitchPreference
import com.dot.gallery.feature_node.presentation.util.Screen

@Composable
fun SettingsSmartFeaturesScreen() {
    @Composable
    fun settings(): SnapshotStateList<SettingsEntity> {
        val context = LocalContext.current
        val handler = LocalEventHandler.current
        var noClassification by Settings.Misc.rememberNoClassification()
        val noClassificationPref = rememberPreference(
            noClassification,
            title = stringResource(R.string.categories),
            summary = stringResource(R.string.categorise_your_media),
            onClick = {
                handler.navigate(Screen.CategoriesScreen())
            },
            screenPosition = Position.Alone
        )

        val databaseHeader = remember(context) {
            SettingsEntity.Header(
                title = context.getString(R.string.database)
            )
        }

        val refreshMetadataPref = rememberPreference(
            title = stringResource(R.string.refresh_metadata),
            summary = stringResource(R.string.refresh_metadata_summary),
            onClick = {
                Toast.makeText(
                    context,
                    context.getString(R.string.metadata_refresh_toast),
                    Toast.LENGTH_SHORT
                ).show()
                WorkManager.getInstance(context).forceMetadataCollect()
            },
            screenPosition = Position.Alone
        )

        return remember(noClassificationPref, databaseHeader, refreshMetadataPref) {
            mutableStateListOf(
                noClassificationPref, databaseHeader, refreshMetadataPref
            )
        }
    }

    BaseSettingsScreen(
        title = stringResource(R.string.ai_category),
        settingsList = settings(),
    )
}