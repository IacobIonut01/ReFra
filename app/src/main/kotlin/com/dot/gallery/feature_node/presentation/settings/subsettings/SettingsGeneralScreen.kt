package com.dot.gallery.feature_node.presentation.settings.subsettings

import androidx.activity.compose.BackHandler
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import com.dot.gallery.R
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.Position
import com.dot.gallery.core.Settings
import com.dot.gallery.core.Settings.Misc.rememberAppLogoAlias
import com.dot.gallery.core.Settings.Misc.rememberAppNameAlias
import com.dot.gallery.core.Settings.Misc.rememberTrashConfirmationEnabled
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.core.navigate
import com.dot.gallery.core.util.SdkCompat
import com.dot.gallery.feature_node.presentation.settings.components.BaseSettingsScreen
import com.dot.gallery.feature_node.presentation.util.Screen
import com.dot.gallery.feature_node.presentation.settings.components.ChooserPreferenceDetailScreen
import com.dot.gallery.feature_node.presentation.settings.components.PreferenceOption
import com.dot.gallery.feature_node.presentation.settings.components.SwitchPreferenceDetailScreen
import com.dot.gallery.feature_node.presentation.settings.components.rememberPreference
import com.dot.gallery.feature_node.presentation.settings.components.rememberSwitchPreference
import com.dot.gallery.feature_node.presentation.util.changeAppAlias
import com.dot.gallery.feature_node.presentation.util.restartApplication
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val DETAIL_TRASH = "trash"
private const val DETAIL_TRASH_CONFIRM = "trash_confirm"
private const val DETAIL_SECURE = "secure"
private const val DETAIL_VIBRATIONS = "vibrations"
private const val DETAIL_APP_NAME = "app_name"
private const val DETAIL_APP_LOGO = "app_logo"
private const val DETAIL_VAULT_ENCRYPT = "vault_encrypt"

@Composable
fun SettingsGeneralScreen() {
    var detailKey by rememberSaveable { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var trashCanEnabled by Settings.Misc.rememberTrashEnabled()
    var trashConfirmationEnabled by rememberTrashConfirmationEnabled()
    var secureMode by Settings.Misc.rememberSecureMode()
    var allowVibrations by Settings.Misc.rememberAllowVibrations()
    var appNameAlias by rememberAppNameAlias()
    var appLogoAlias by rememberAppLogoAlias()
    var vaultEncryptBehavior by Settings.Vault.rememberVaultEncryptBehavior()

    when (detailKey) {
        DETAIL_TRASH -> {
            BackHandler { detailKey = null }
            SwitchPreferenceDetailScreen(
                title = stringResource(R.string.settings_trash_title),
                isChecked = trashCanEnabled,
                onCheckedChange = { trashCanEnabled = it },
                description = stringResource(R.string.trash_enabled_description),
            )
        }
        DETAIL_TRASH_CONFIRM -> {
            BackHandler { detailKey = null }
            SwitchPreferenceDetailScreen(
                title = stringResource(R.string.settings_trash_confirmation_title),
                isChecked = trashConfirmationEnabled,
                onCheckedChange = { trashConfirmationEnabled = it },
                description = if (trashCanEnabled) {
                    stringResource(R.string.trash_confirmation_description)
                } else {
                    stringResource(R.string.trash_confirmation_requires_trash_description)
                },
                enabled = trashCanEnabled,
            )
        }
        DETAIL_SECURE -> {
            BackHandler { detailKey = null }
            SwitchPreferenceDetailScreen(
                title = stringResource(R.string.secure_mode_title),
                isChecked = secureMode,
                onCheckedChange = { secureMode = it },
                description = stringResource(R.string.secure_mode_description),
                preview = { checked -> SecureModePreview(checked) },
            )
        }
        DETAIL_VIBRATIONS -> {
            BackHandler { detailKey = null }
            SwitchPreferenceDetailScreen(
                title = stringResource(R.string.allow_vibrations),
                isChecked = allowVibrations,
                onCheckedChange = { allowVibrations = it },
                description = stringResource(R.string.allow_vibrations_description),
            )
        }
        DETAIL_APP_NAME -> {
            BackHandler { detailKey = null }
            ChooserPreferenceDetailScreen(
                title = stringResource(R.string.change_app_name),
                description = stringResource(R.string.app_name_description),
                preview = { AppNamePreview(appNameAlias, appLogoAlias) },
                options = listOf(
                    PreferenceOption(Settings.Misc.ALIAS_REFRA, Settings.Misc.ALIAS_REFRA, appNameAlias == Settings.Misc.ALIAS_REFRA),
                    PreferenceOption(Settings.Misc.ALIAS_GALLERY, Settings.Misc.ALIAS_GALLERY, appNameAlias == Settings.Misc.ALIAS_GALLERY),
                ),
                onOptionSelected = {
                    appNameAlias = it
                    context.changeAppAlias(it, appLogoAlias)
                    scope.launch {
                        delay(300)
                        context.restartApplication()
                    }
                },
            )
        }
        DETAIL_APP_LOGO -> {
            BackHandler { detailKey = null }
            ChooserPreferenceDetailScreen(
                title = stringResource(R.string.change_app_logo),
                description = stringResource(R.string.app_logo_description),
                preview = { AppLogoPreview(appLogoAlias) },
                options = listOf(
                    PreferenceOption(Settings.Misc.ALIAS_REFRA, Settings.Misc.ALIAS_REFRA, appLogoAlias == Settings.Misc.ALIAS_REFRA),
                    PreferenceOption(Settings.Misc.ALIAS_GALLERY, Settings.Misc.ALIAS_GALLERY, appLogoAlias == Settings.Misc.ALIAS_GALLERY),
                    PreferenceOption(Settings.Misc.ALIAS_MONO, Settings.Misc.ALIAS_MONO, appLogoAlias == Settings.Misc.ALIAS_MONO),
                ),
                onOptionSelected = {
                    appLogoAlias = it
                    context.changeAppAlias(appNameAlias, it)
                    scope.launch {
                        delay(300)
                        context.restartApplication()
                    }
                },
            )
        }
        DETAIL_VAULT_ENCRYPT -> {
            BackHandler { detailKey = null }
            ChooserPreferenceDetailScreen(
                title = stringResource(R.string.vault_encrypt_behavior),
                description = stringResource(R.string.vault_encrypt_behavior_summary),
                options = listOf(
                    PreferenceOption(Settings.Vault.ENCRYPT_ASK, stringResource(R.string.vault_encrypt_ask), vaultEncryptBehavior == Settings.Vault.ENCRYPT_ASK),
                    PreferenceOption(Settings.Vault.ENCRYPT_DELETE, stringResource(R.string.vault_encrypt_delete), vaultEncryptBehavior == Settings.Vault.ENCRYPT_DELETE),
                    PreferenceOption(Settings.Vault.ENCRYPT_KEEP, stringResource(R.string.vault_encrypt_keep), vaultEncryptBehavior == Settings.Vault.ENCRYPT_KEEP),
                ),
                onOptionSelected = { vaultEncryptBehavior = it },
            )
        }
        else -> {
            GeneralListScreen(
                trashCanEnabled = trashCanEnabled,
                onTrashChange = { trashCanEnabled = it },
                trashConfirmationEnabled = trashConfirmationEnabled,
                onTrashConfirmChange = { trashConfirmationEnabled = it },
                secureMode = secureMode,
                onSecureChange = { secureMode = it },
                allowVibrations = allowVibrations,
                onVibrationsChange = { allowVibrations = it },
                appNameAlias = appNameAlias,
                appLogoAlias = appLogoAlias,
                vaultEncryptBehavior = vaultEncryptBehavior,
                onDetailClick = { detailKey = it },
                listState = listState,
            )
        }
    }
}

@Composable
private fun GeneralListScreen(
    trashCanEnabled: Boolean,
    onTrashChange: (Boolean) -> Unit,
    trashConfirmationEnabled: Boolean,
    onTrashConfirmChange: (Boolean) -> Unit,
    secureMode: Boolean,
    onSecureChange: (Boolean) -> Unit,
    allowVibrations: Boolean,
    onVibrationsChange: (Boolean) -> Unit,
    appNameAlias: String,
    appLogoAlias: String,
    vaultEncryptBehavior: String,
    onDetailClick: (String) -> Unit,
    listState: LazyListState,
) {
    @Composable
    fun settings(): SnapshotStateList<SettingsEntity> {
        val res = LocalResources.current
        val eventHandler = LocalEventHandler.current

        val trashSectionPref = remember(res) {
            SettingsEntity.Header(title = res.getString(R.string.trash))
        }

        val openTrashPref = rememberPreference(
            title = stringResource(R.string.settings_open_trash),
            summary = stringResource(R.string.settings_open_trash_summary),
            onClick = { eventHandler.navigate(Screen.TrashedScreen()) },
            screenPosition = Position.Top,
        )

        val trashCanEnabledPref = rememberSwitchPreference(
            trashCanEnabled,
            title = stringResource(R.string.settings_trash_title),
            summary = stringResource(R.string.settings_trash_summary),
            isChecked = trashCanEnabled,
            onCheck = onTrashChange,
            onClick = { onDetailClick(DETAIL_TRASH) },
            screenPosition = Position.Middle
        )

        val trashConfirmationEnabledPref = rememberSwitchPreference(
            trashConfirmationEnabled,
            trashCanEnabled,
            title = stringResource(R.string.settings_trash_confirmation_title),
            summary = stringResource(
                if (trashCanEnabled) {
                    R.string.settings_trash_confirmation_summary
                } else {
                    R.string.settings_trash_confirmation_requires_trash
                }
            ),
            enabled = trashCanEnabled,
            isChecked = trashConfirmationEnabled,
            onCheck = onTrashConfirmChange,
            onClick = { onDetailClick(DETAIL_TRASH_CONFIRM) },
            screenPosition = Position.Bottom
        )

        val otherSectionPref = remember(res) {
            SettingsEntity.Header(title = res.getString(R.string.other))
        }

        val secureModePref = rememberSwitchPreference(
            secureMode,
            title = stringResource(R.string.secure_mode_title),
            summary = stringResource(R.string.secure_mode_summary),
            isChecked = secureMode,
            onCheck = onSecureChange,
            onClick = { onDetailClick(DETAIL_SECURE) },
            screenPosition = Position.Top
        )

        val allowVibrationsPref = rememberSwitchPreference(
            allowVibrations,
            title = stringResource(R.string.allow_vibrations),
            summary = stringResource(R.string.allow_vibrations_summary),
            isChecked = allowVibrations,
            onCheck = onVibrationsChange,
            onClick = { onDetailClick(DETAIL_VIBRATIONS) },
            screenPosition = Position.Middle
        )

        val appNamePref = rememberPreference(
            appNameAlias,
            title = stringResource(R.string.change_app_name),
            summary = stringResource(R.string.change_app_name_summary),
            onClick = { onDetailClick(DETAIL_APP_NAME) },
            screenPosition = Position.Middle
        )

        val appLogoPref = rememberPreference(
            appLogoAlias,
            title = stringResource(R.string.change_app_logo),
            summary = stringResource(R.string.change_app_logo_summary),
            onClick = { onDetailClick(DETAIL_APP_LOGO) },
            screenPosition = Position.Bottom
        )

        val vaultSectionPref = remember(res) {
            SettingsEntity.Header(title = res.getString(R.string.vault))
        }

        val vaultEncryptBehaviorSummary = when (vaultEncryptBehavior) {
            Settings.Vault.ENCRYPT_DELETE -> stringResource(R.string.vault_encrypt_delete)
            Settings.Vault.ENCRYPT_KEEP -> stringResource(R.string.vault_encrypt_keep)
            else -> stringResource(R.string.vault_encrypt_ask)
        }
        val vaultEncryptPref = rememberPreference(
            vaultEncryptBehavior,
            title = stringResource(R.string.vault_encrypt_behavior),
            summary = vaultEncryptBehaviorSummary,
            onClick = { onDetailClick(DETAIL_VAULT_ENCRYPT) },
            screenPosition = Position.Alone
        )

        return remember(
            openTrashPref, trashCanEnabledPref, trashConfirmationEnabledPref,
            secureModePref, allowVibrationsPref, appNamePref, appLogoPref,
            vaultEncryptPref
        ) {
            mutableStateListOf<SettingsEntity>().apply {
                if (SdkCompat.supportsTrash) {
                    add(trashSectionPref)
                    add(openTrashPref)
                    add(trashCanEnabledPref)
                    add(trashConfirmationEnabledPref)
                }
                add(otherSectionPref)
                add(secureModePref)
                add(allowVibrationsPref)
                add(appNamePref)
                add(appLogoPref)
                add(vaultSectionPref)
                add(vaultEncryptPref)
            }
        }
    }

    BaseSettingsScreen(
        title = stringResource(R.string.settings_general),
        settingsList = settings(),
        listState = listState,
        searchRoute = Screen.SettingsGeneralScreen(),
    )
}

@Composable
private fun SecureModePreview(isChecked: Boolean) {
    val overlayAlpha by animateFloatAsState(if (isChecked) 1f else 0f, label = "overlay")
    val cellColor = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .height(140.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(3.dp)).background(cellColor))
            repeat(2) {
                Row(Modifier.weight(1f).fillMaxWidth(), Arrangement.spacedBy(4.dp)) {
                    repeat(3) {
                        Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(cellColor))
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.9f * overlayAlpha),
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.95f * overlayAlpha)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            if (overlayAlpha > 0f) {
                Icon(
                    imageVector = Icons.Filled.Shield,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = overlayAlpha)
                )
            }
        }
    }
}

@Composable
private fun AppNamePreview(currentAlias: String, logoAlias: String) {
    val context = LocalContext.current
    val iconRes = when (logoAlias) {
        Settings.Misc.ALIAS_GALLERY -> R.mipmap.ic_launcher_gallery_round
        Settings.Misc.ALIAS_MONO -> R.mipmap.ic_launcher_mono_round
        else -> R.mipmap.ic_launcher_round
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        listOf(Settings.Misc.ALIAS_REFRA, Settings.Misc.ALIAS_GALLERY).forEach { alias ->
            val selected = currentAlias == alias
            val borderColor = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant
            val containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else Color.Transparent

            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .border(width = 2.dp, color = borderColor, shape = RoundedCornerShape(16.dp))
                    .background(containerColor)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Image(
                    painter = rememberDrawablePainter(
                        drawable = AppCompatResources.getDrawable(context, iconRes)
                    ),
                    contentDescription = alias,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                )
                Text(
                    text = alias,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun AppLogoPreview(currentAlias: String) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        listOf(Settings.Misc.ALIAS_REFRA, Settings.Misc.ALIAS_GALLERY, Settings.Misc.ALIAS_MONO).forEach { alias ->
            val selected = currentAlias == alias
            val borderColor = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant
            val containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else Color.Transparent
            val iconRes = when (alias) {
                Settings.Misc.ALIAS_GALLERY -> R.mipmap.ic_launcher_gallery_round
                Settings.Misc.ALIAS_MONO -> R.mipmap.ic_launcher_mono_round
                else -> R.mipmap.ic_launcher_round
            }

            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .border(width = 2.dp, color = borderColor, shape = RoundedCornerShape(16.dp))
                    .background(containerColor)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Image(
                    painter = rememberDrawablePainter(
                        drawable = AppCompatResources.getDrawable(context, iconRes)
                    ),
                    contentDescription = alias,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                )
                Text(
                    text = alias,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}