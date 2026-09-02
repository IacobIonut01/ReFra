/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.backup

import kotlinx.serialization.Serializable

/**
 * Top-level manifest describing the contents of a ReFra backup archive.
 *
 * The backup is a ZIP archive containing this manifest (as `manifest.json`) plus,
 * for each vault, the fully decrypted media files under `vaults/<uuid>/<mediaId>`.
 */
@Serializable
data class BackupManifest(
    val schemaVersion: Int = SCHEMA_VERSION,
    val appVersionName: String = "",
    val appVersionCode: Long = 0L,
    val exportedAt: Long = 0L,
    val settings: Map<String, SettingValue> = emptyMap(),
    val localFavorites: List<LocalFavoriteEntry> = emptyList(),
    val cloudFavorites: List<CloudFavoriteEntry> = emptyList(),
    val cloudConfigs: List<CloudConfigEntry> = emptyList(),
    val vaults: List<VaultEntry> = emptyList(),
) {
    companion object {
        const val MIN_SUPPORTED_SCHEMA_VERSION = 1
        const val SCHEMA_VERSION = 2
        const val MANIFEST_NAME = "manifest.json"
        const val VAULTS_DIR = "vaults"

        fun requireSupportedSchema(schemaVersion: Int) {
            if (schemaVersion !in MIN_SUPPORTED_SCHEMA_VERSION..SCHEMA_VERSION) {
                throw UnsupportedBackupSchemaException(schemaVersion)
            }
        }
    }
}

class UnsupportedBackupSchemaException(val schemaVersion: Int) : Exception(
    "Unsupported backup schema $schemaVersion; supported versions are " +
        "${BackupManifest.MIN_SUPPORTED_SCHEMA_VERSION}-${BackupManifest.SCHEMA_VERSION}"
)

/** Stable backup-local identity for records owned by a source cloud account. */
fun backupSourceAccountId(providerType: String, sourceConfigId: Long): String =
    "$providerType:$sourceConfigId"

/** Source-account to destination-database mapping built during import. */
data class BackupAccountMappings(
    val bySourceAccount: Map<String, Long> = emptyMap(),
    val destinationIdsByProvider: Map<String, Set<Long>> = emptyMap()
) {
    fun mergedWith(other: BackupAccountMappings): BackupAccountMappings = BackupAccountMappings(
        bySourceAccount = other.bySourceAccount + bySourceAccount,
        destinationIdsByProvider = (destinationIdsByProvider.keys + other.destinationIdsByProvider.keys)
            .associateWith { provider ->
                destinationIdsByProvider[provider].orEmpty() +
                    other.destinationIdsByProvider[provider].orEmpty()
            }
    )

    fun resolveDestination(sourceAccountId: String, providerType: String): Long? =
        bySourceAccount[sourceAccountId] ?: destinationIdsByProvider[providerType]?.singleOrNull()
}

internal data class BackupCloudAccountIdentity(
    val providerType: String,
    val serverUrl: String,
    val username: String
)

internal fun backupCloudAccountIdentity(
    providerType: String,
    serverUrl: String,
    username: String?
): BackupCloudAccountIdentity = BackupCloudAccountIdentity(
    providerType = providerType,
    serverUrl = serverUrl.trim().trimEnd('/'),
    username = username.orEmpty()
)

internal fun <T> findUnambiguousCloudAccount(
    target: BackupCloudAccountIdentity,
    existing: List<T>,
    identity: (T) -> BackupCloudAccountIdentity
): T? = existing.filter { identity(it) == target }.singleOrNull()

/** A cloud favorite awaiting the first indexed row for its destination account. */
@Serializable
internal data class PendingCloudFavorite(
    val providerType: String,
    val serverConfigId: Long,
    val remoteId: String
)

internal data class PendingFavoriteApplyResult(
    val applied: Set<PendingCloudFavorite>,
    val remaining: Set<PendingCloudFavorite>
)

internal suspend fun applyPendingFavorites(
    pending: Set<PendingCloudFavorite>,
    providerType: String,
    serverConfigId: Long,
    apply: suspend (PendingCloudFavorite) -> Boolean
): PendingFavoriteApplyResult {
    val applicable = pending.filter {
        it.providerType == providerType && it.serverConfigId == serverConfigId
    }
    val applied = applicable.filterTo(mutableSetOf()) { apply(it) }
    return PendingFavoriteApplyResult(applied = applied, remaining = pending - applied)
}

/**
 * A single DataStore preference value, tagged with its primitive type so it can be
 * faithfully reconstructed on import.
 *
 * [type] codes: b=Boolean, i=Int, l=Long, f=Float, d=Double, s=String, ss=Set<String>.
 * For `ss`, [value] holds a JSON-encoded list of strings.
 */
@Serializable
data class SettingValue(
    val type: String,
    val value: String
)

/** Primitive type expected for a portable user preference. */
internal enum class PortableSettingType(val code: String) {
    BOOLEAN("b"),
    INT("i"),
    LONG("l"),
    FLOAT("f"),
    DOUBLE("d"),
    STRING("s"),
    STRING_SET("ss")
}

internal data class DecodedPortableSetting(
    val type: PortableSettingType,
    val value: Any
)

/**
 * Explicit allowlist for preferences that represent portable user choices.
 *
 * Anything not listed here is deliberately excluded. In particular, this prevents backups from
 * carrying vault credentials and lockout state, setup/operational markers, search history, or
 * keys introduced later without an explicit portability review.
 */
internal object PortableBackupSettings {
    private val booleanNames = setOf(
        "hide_timeline_on_album",
        "album_group_by_date",
        "merge_albums_by_name",
        "album_sections_enabled",
        "pinned_albums_as_grid",
        "show_media_type_albums",
        "update_modified_date",
        "slideshow_random_order",
        "slideshow_reverse_order",
        "slideshow_include_gifs",
        "slideshow_include_videos",
        "slideshow_loop",
        "slideshow_ken_burns",
        "smart_features_include_ignored_albums",
        "enable_trashcan",
        "enable_trashcan_confirmation",
        "force_theme",
        "dark_mode",
        "amoled_mode",
        "secure_mode",
        "timeline_group_by_month",
        "timeline_group_by_year",
        "group_similar_media",
        "group_raw_jpg",
        "group_edited_copies",
        "group_burst_sequences",
        "group_cloud_local",
        "use_system_font",
        "allow_blur",
        "auto_contrast",
        "disable_smoothing",
        "long_press_cutout",
        "old_navbar",
        "allow_vibrations",
        "auto_hide_searchbar",
        "auto_hide_navigationbar",
        "full_brightness_view",
        "tap_sides_to_navigate",
        "auto_hide_on_video_play",
        "no_classification",
        "video_autoplay",
        "video_surface_rebind",
        "shared_elements",
        "media_view_date_header",
        "selection_titles",
        "show_favorite_button",
        "show_searchbar_favorite_button",
        "show_filter_button",
        "favorites_group_by_date",
        "timeline_group_by_date",
        "vault_group_by_date",
        "cloud_archive_group_by_date",
        "location_group_by_date",
        "allow_gif_animation",
        "story_viewer_auto_advance",
        "sandboxed_decode",
        "cloud_offline_cache_on_view",
        "cloud_offline_cache_wifi_only"
    )

    private val intNames = setOf(
        "slideshow_interval_seconds",
        "reencode_lossy_quality",
        "reencode_jxl_effort",
        "cloud_offline_budget_mb"
    )

    private val stringNames = setOf(
        "album_last_sort_obj",
        "album_last_view_obj",
        "album_media_sort_obj",
        "slideshow_transition",
        "library_shortcuts_layout",
        "theme_color_seed",
        "reencode_quality_mode",
        "frame_export_format",
        "date_header_format",
        "extended_date_header_format",
        "exif_date_format",
        "extended_date_format",
        "default_date_format",
        "weekly_date_format",
        "selection_sheet_config",
        "app_name_alias",
        "app_logo_alias",
        "favorite_icon_position",
        "timeline_group_method",
        "albums_group_method",
        "favorites_group_method",
        "vault_group_method",
        "cloud_archive_group_method",
        "location_group_method",
        "map_appearance",
        "timeline_layout_type",
        "default_image_editor",
        "story_cards_config",
        "story_viewer_duration_seconds",
        "metadata_isolation_mode",
        "vault_encrypt_behavior"
    )

    private val allowedTypes: Map<String, PortableSettingType> = buildMap {
        booleanNames.forEach { put(it, PortableSettingType.BOOLEAN) }
        intNames.forEach { put(it, PortableSettingType.INT) }
        stringNames.forEach { put(it, PortableSettingType.STRING) }
    }

    fun typeFor(name: String): PortableSettingType? = allowedTypes[name]

    fun encode(name: String, value: Any): SettingValue? = when (typeFor(name)) {
        PortableSettingType.BOOLEAN -> (value as? Boolean)?.let { SettingValue("b", it.toString()) }
        PortableSettingType.INT -> (value as? Int)?.let { SettingValue("i", it.toString()) }
        PortableSettingType.LONG -> (value as? Long)?.let { SettingValue("l", it.toString()) }
        PortableSettingType.FLOAT -> (value as? Float)?.takeIf { it.isFinite() }
            ?.let { SettingValue("f", it.toString()) }
        PortableSettingType.DOUBLE -> (value as? Double)?.takeIf { it.isFinite() }
            ?.let { SettingValue("d", it.toString()) }
        PortableSettingType.STRING -> (value as? String)?.let { SettingValue("s", it) }
        PortableSettingType.STRING_SET -> null
        null -> null
    }

    fun decode(name: String, setting: SettingValue): DecodedPortableSetting? {
        val expectedType = typeFor(name) ?: return null
        if (setting.type != expectedType.code) return null
        val decoded = when (expectedType) {
            PortableSettingType.BOOLEAN -> when (setting.value) {
                "true" -> true
                "false" -> false
                else -> return null
            }
            PortableSettingType.INT -> setting.value.toIntOrNull() ?: return null
            PortableSettingType.LONG -> setting.value.toLongOrNull() ?: return null
            PortableSettingType.FLOAT -> setting.value.toFloatOrNull()?.takeIf { it.isFinite() }
                ?: return null
            PortableSettingType.DOUBLE -> setting.value.toDoubleOrNull()?.takeIf { it.isFinite() }
                ?: return null
            PortableSettingType.STRING -> setting.value
            PortableSettingType.STRING_SET -> return null
        }
        return DecodedPortableSetting(expectedType, decoded)
    }
}

/**
 * A local (MediaStore) favorite. Local favorites live in the system MediaStore
 * (IS_FAVORITE) and are not stored by the app, so they are matched back to device
 * media on import by relative path + display name (with a size fallback).
 */
@Serializable
data class LocalFavoriteEntry(
    val displayName: String,
    val relativePath: String,
    val path: String,
    val size: Long,
    val timestamp: Long
)

/** A cloud favorite, identified by its owning account, provider, and remote id. */
@Serializable
data class CloudFavoriteEntry(
    val providerType: String,
    val remoteId: String,
    /** Stable identity within the source backup. Preferred over the device-local database id. */
    val sourceAccountId: String = "",
    /** Legacy schema-v1 account reference. Never use directly as a destination database id. */
    val serverConfigId: Long = 0L
)

/** A cloud server configuration. Mirrors [com.dot.gallery.cloud.data.entity.CloudServerConfigEntity]. */
@Serializable
data class CloudConfigEntry(
    val providerType: String,
    val serverUrl: String,
    /** Source database id retained only to link records inside this backup. */
    val sourceConfigId: Long = 0L,
    /** Stable identity used to associate favorites with this source account during import. */
    val sourceAccountId: String = "",
    /** Credentials are omitted from new backups because Android Keystore ciphertext is not portable. */
    val requiresReauthentication: Boolean = true,
    val apiKey: String? = null,
    val username: String? = null,
    val encryptedPassword: String? = null,
    val displayName: String = "",
    val isActive: Boolean = true,
    val lastConnected: Long = 0L,
    val syncEnabled: Boolean = false,
    val wifiOnly: Boolean = true,
    val syncIntervalMinutes: Int = 360,
    val syncFolders: String = "",
    val cellularPhotos: Boolean = false,
    val cellularVideos: Boolean = false,
    val requireCharging: Boolean = false,
    val syncAlbums: Boolean = false,
    val showBackupTotalProgress: Boolean = true,
    val showBackupDetailProgress: Boolean = false,
    val notifyBackupFailures: Boolean = true,
    val autoUrlSwitch: Boolean = false,
    val localWifiSsid: String = "",
    val localServerUrl: String = "",
    val externalUrls: String = "[]",
    val loadPreviewImage: Boolean = true,
    val loadOriginalImage: Boolean = false,
    val autoPlayVideos: Boolean = true,
    val loopVideos: Boolean = false,
    val forceOriginalVideo: Boolean = false,
    val verboseLogging: Boolean = false,
    val syncRemoteDeletions: Boolean = false,
    val preferRemoteImages: Boolean = false,
    val readOnlyMode: Boolean = false
)

/** A vault and the metadata of all media it contains. Binary content is stored separately in the archive. */
@Serializable
data class VaultEntry(
    val uuid: String,
    val name: String,
    val media: List<VaultMediaEntry> = emptyList()
)

/**
 * Metadata of a single vault media item. Mirrors [com.dot.gallery.feature_node.domain.model.Media.EncryptedMedia2].
 * [fileName] is the archive entry path that holds the decrypted binary content.
 */
@Serializable
data class VaultMediaEntry(
    val id: Long,
    val label: String,
    val path: String,
    val relativePath: String,
    val albumID: Long,
    val albumLabel: String,
    val timestamp: Long,
    val expiryTimestamp: Long? = null,
    val takenTimestamp: Long? = null,
    val fullDate: String,
    val mimeType: String,
    val favorite: Int,
    val trashed: Int,
    val size: Long,
    val duration: String? = null,
    val fileName: String,
    /** Plaintext payload size in the archive. Required for schema v2 and newer. */
    val archiveSize: Long = -1L,
    /** Lower-case SHA-256 of the plaintext archive payload. Required for schema v2 and newer. */
    val sha256: String = ""
)

/**
 * Selectable categories of data to include in an export / apply on import.
 *
 * [selectedVaultIds] further narrows the VAULTS section to specific vault UUIDs.
 * `null` means "all vaults" (the default); a non-null set restricts to those ids only.
 */
data class BackupSelection(
    val settings: Boolean = true,
    val localFavorites: Boolean = true,
    val cloudFavorites: Boolean = true,
    val cloudConfigs: Boolean = true,
    val vaults: Boolean = true,
    val selectedVaultIds: Set<String>? = null
) {
    /** True if the vault with [uuid] should be included (all vaults when no explicit set). */
    fun isVaultSelected(uuid: String): Boolean = selectedVaultIds?.contains(uuid) ?: true
}

/** The individual data categories handled during a backup / restore, used for per-section progress. */
enum class BackupSection {
    SETTINGS,
    LOCAL_FAVORITES,
    CLOUD_FAVORITES,
    CLOUD_CONFIGS,
    VAULTS
}

/**
 * Summary of what a backup archive contains, produced by inspecting the manifest only
 * (without restoring anything). Used to drive the import "pick what to restore" step.
 */
data class BackupContents(
    val schemaVersion: Int,
    val appVersionName: String,
    val exportedAt: Long,
    val encrypted: Boolean,
    val settingsCount: Int,
    val localFavoritesCount: Int,
    val cloudFavoritesCount: Int,
    val cloudConfigsCount: Int,
    val vaultCount: Int,
    val vaultMediaCount: Int,
    /** Per-vault summary so the import UI can let the user pick individual vaults. */
    val vaults: List<VaultSummary> = emptyList(),
) {
    /** True if the given section has any data in this backup. */
    fun has(section: BackupSection): Boolean = when (section) {
        BackupSection.SETTINGS -> settingsCount > 0
        BackupSection.LOCAL_FAVORITES -> localFavoritesCount > 0
        BackupSection.CLOUD_FAVORITES -> cloudFavoritesCount > 0
        BackupSection.CLOUD_CONFIGS -> cloudConfigsCount > 0
        BackupSection.VAULTS -> vaultCount > 0
    }
}

/** Lightweight per-vault summary used to drive per-vault selection in the import UI. */
data class VaultSummary(
    val uuid: String,
    val name: String,
    val mediaCount: Int
)

/** Callback invoked as a backup/restore progresses through a [BackupSection]. */
typealias BackupProgressListener = (section: BackupSection, current: Int, total: Int) -> Unit
