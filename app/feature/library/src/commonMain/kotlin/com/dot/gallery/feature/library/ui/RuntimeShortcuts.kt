/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.library.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlin.random.Random
import com.dot.gallery.core.ui.settings.rememberPreference
import com.dot.gallery.domain.model.LibraryIndicatorState
import com.dot.gallery.domain.settings.PrefKey
import com.dot.gallery.domain.settings.SettingsKeys
import com.dot.gallery.ui.core.icons.Encrypted
import com.dot.gallery.ui.core.Icons as GalleryIcons
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import com.dot.gallery.feature.library.generated.resources.Res
import com.dot.gallery.feature.library.generated.resources.library_trash_expiry_today
import com.dot.gallery.feature.library.generated.resources.library_trash_next_expiry
import com.dot.gallery.feature.library.generated.resources.library_vault_count
import com.dot.gallery.feature.library.generated.resources.library_vault_locked
import com.dot.gallery.feature.library.generated.resources.categories
import com.dot.gallery.feature.library.generated.resources.cloud_people
import com.dot.gallery.feature.library.generated.resources.locations
import com.dot.gallery.feature.library.generated.resources.cloud_accounts
import com.dot.gallery.feature.library.generated.resources.cloud_archive
import com.dot.gallery.feature.library.generated.resources.cloud_backup_and_sync
import com.dot.gallery.feature.library.generated.resources.memories
import com.dot.gallery.feature.library.generated.resources.cloud_shared_links
import com.dot.gallery.feature.library.generated.resources.favorites
import com.dot.gallery.feature.library.generated.resources.ignored
import com.dot.gallery.feature.library.generated.resources.security_private_folder
import com.dot.gallery.feature.library.generated.resources.trash
import com.dot.gallery.feature.library.generated.resources.vault


/**
 * Build the live runtime definition for every potentially-available Library
 * shortcut, keyed by [LibraryShortcut]. The map only contains shortcuts whose
 * underlying feature is currently usable.
 *
 * [supportsTrash]/[supportsFavorites] are platform capabilities measured
 * app-side (`SdkCompat`); [routes] carries the app's route strings.
 * [vaultCount] is null while the vault list is behind a lock, so the tile never says how many
 * vaults there are. [explore] carries the Locations, People and Categories tiles, and
 * [shuffleSeed] picks the shuffled Favorites cover, so it changes only when the seed does.
 */
@Composable
fun rememberLibraryRuntimeShortcuts(
    indicatorState: LibraryIndicatorState,
    cloudState: CloudLibraryState,
    routes: LibraryRoutes,
    supportsTrash: Boolean,
    supportsFavorites: Boolean,
    previews: LibraryShortcutPreviews = LibraryShortcutPreviews(),
    vaultCount: Int? = null,
    explore: LibraryExploreData = LibraryExploreData(),
    shuffleSeed: Int = 0,
): Map<LibraryShortcut, RuntimeShortcut> {
    val privateFolderUri by rememberPreference(SettingsKeys.privateFolderUri)
    val colorScheme = MaterialTheme.colorScheme
    val map = LinkedHashMap<LibraryShortcut, RuntimeShortcut>()
    val shuffledFavorites = remember(previews.favoritePool, shuffleSeed) {
        previews.favoritePool.shuffled(Random(shuffleSeed)).take(MAX_SHORTCUT_PREVIEW_MEDIA)
    }

    if (supportsTrash) {
        val expiryDays = previews.nextTrashExpiry?.let { trashDaysLeft(it, Clock.System.now().epochSeconds) }
        map[LibraryShortcut.TRASH] = RuntimeShortcut(
            shortcut = LibraryShortcut.TRASH,
            title = stringResource(Res.string.trash),
            icon = Icons.Outlined.DeleteOutline,
            contentColor = colorScheme.primary,
            useIndicator = true,
            indicatorCounter = indicatorState.trashCount,
            route = routes.trash,
            available = true,
            subtitle = when {
                indicatorState.trashCount == 0 || expiryDays == null -> null
                expiryDays == 0 -> stringResource(Res.string.library_trash_expiry_today)
                else -> pluralStringResource(Res.plurals.library_trash_next_expiry, expiryDays, expiryDays)
            },
            previewMedia = previews.recentTrash,
            dimPreview = true,
        )
    }
    if (supportsFavorites) {
        map[LibraryShortcut.FAVORITES] = RuntimeShortcut(
            shortcut = LibraryShortcut.FAVORITES,
            title = stringResource(Res.string.favorites),
            icon = Icons.Outlined.FavoriteBorder,
            contentColor = colorScheme.error,
            useIndicator = true,
            indicatorCounter = indicatorState.favoriteCount,
            route = routes.favorites,
            available = true,
            previewMedia = previews.latestFavorites,
            oldestPreviewMedia = previews.oldestFavorites,
            shufflePreviewMedia = shuffledFavorites,
        )
    }
    map[LibraryShortcut.VAULT] = RuntimeShortcut(
        shortcut = LibraryShortcut.VAULT,
        title = stringResource(Res.string.vault),
        icon = GalleryIcons.Encrypted,
        contentColor = colorScheme.secondary,
        useIndicator = false,
        indicatorCounter = 0,
        route = routes.vault,
        available = true,
        subtitle = if (vaultCount == null) {
            stringResource(Res.string.library_vault_locked)
        } else pluralStringResource(Res.plurals.library_vault_count, vaultCount, vaultCount),
        previewStyle = ShortcutPreviewStyle.PLACEHOLDER,
    )
    map[LibraryShortcut.IGNORED] = RuntimeShortcut(
        shortcut = LibraryShortcut.IGNORED,
        title = stringResource(Res.string.ignored),
        icon = Icons.Outlined.VisibilityOff,
        contentColor = colorScheme.tertiary,
        useIndicator = false,
        indicatorCounter = 0,
        route = routes.ignored,
        available = true
    )
    // Memories come from the device library, and from a photo server when one provides them.
    map[LibraryShortcut.MEMORIES] = RuntimeShortcut(
        shortcut = LibraryShortcut.MEMORIES,
        title = stringResource(Res.string.memories),
        icon = Icons.Outlined.AutoAwesome,
        contentColor = colorScheme.tertiary,
        useIndicator = false,
        indicatorCounter = 0,
        route = routes.memories,
        available = true
    )
    if (privateFolderUri.isNotEmpty()) {
        map[LibraryShortcut.PRIVATE_FOLDER] = RuntimeShortcut(
            shortcut = LibraryShortcut.PRIVATE_FOLDER,
            title = stringResource(Res.string.security_private_folder),
            icon = Icons.Outlined.FolderOff,
            contentColor = colorScheme.secondary,
            useIndicator = false,
            indicatorCounter = 0,
            route = routes.privateFolder,
            available = true
        )
    }
    if (cloudState.hasCloud) {
        if (cloudState.hasArchive) {
            map[LibraryShortcut.CLOUD_ARCHIVE] = RuntimeShortcut(
                shortcut = LibraryShortcut.CLOUD_ARCHIVE,
                title = stringResource(Res.string.cloud_archive),
                icon = Icons.Outlined.Archive,
                contentColor = colorScheme.secondary,
                useIndicator = true,
                indicatorCounter = cloudState.archivedCount,
                route = routes.cloudArchive,
                available = true
            )
        }
        if (cloudState.hasShareLink) {
            map[LibraryShortcut.CLOUD_SHARED_LINKS] = RuntimeShortcut(
                shortcut = LibraryShortcut.CLOUD_SHARED_LINKS,
                title = stringResource(Res.string.cloud_shared_links),
                icon = Icons.Outlined.Link,
                contentColor = colorScheme.tertiary,
                useIndicator = true,
                indicatorCounter = cloudState.sharedLinkCount,
                route = routes.cloudSharedLinks,
                available = true
            )
        }
        map[LibraryShortcut.CLOUD_BACKUP] = RuntimeShortcut(
            shortcut = LibraryShortcut.CLOUD_BACKUP,
            title = stringResource(Res.string.cloud_backup_and_sync),
            icon = Icons.Outlined.Backup,
            contentColor = colorScheme.primary,
            useIndicator = false,
            indicatorCounter = 0,
            route = routes.cloudBackup,
            available = true
        )
        map[LibraryShortcut.CLOUD_ACCOUNTS] = RuntimeShortcut(
            shortcut = LibraryShortcut.CLOUD_ACCOUNTS,
            title = stringResource(Res.string.cloud_accounts),
            icon = Icons.Outlined.Cloud,
            contentColor = colorScheme.onSurface,
            useIndicator = false,
            indicatorCounter = 0,
            route = routes.cloudAccounts,
            available = true
        )
    }
    if (explore.locations) {
        map[LibraryShortcut.LOCATIONS] = RuntimeShortcut(
            shortcut = LibraryShortcut.LOCATIONS,
            title = stringResource(Res.string.locations),
            icon = Icons.Outlined.LocationOn,
            contentColor = colorScheme.primary,
            useIndicator = true,
            indicatorCounter = explore.locationCount,
            route = routes.locations,
            available = true,
            previewMedia = listOfNotNull(explore.latestGeo?.media),
            previewStyle = ShortcutPreviewStyle.MAP,
            mapLatitude = explore.latestGeo?.latitude,
            mapLongitude = explore.latestGeo?.longitude,
        )
    }
    if (explore.people) {
        map[LibraryShortcut.PEOPLE] = RuntimeShortcut(
            shortcut = LibraryShortcut.PEOPLE,
            title = stringResource(Res.string.cloud_people),
            icon = Icons.Outlined.People,
            contentColor = colorScheme.secondary,
            useIndicator = true,
            indicatorCounter = explore.peopleCount,
            route = routes.people,
            available = true,
            previewStyle = ShortcutPreviewStyle.FACES,
            previewFaces = explore.faces,
        )
    }
    if (explore.categories) {
        map[LibraryShortcut.CATEGORIES] = RuntimeShortcut(
            shortcut = LibraryShortcut.CATEGORIES,
            title = stringResource(Res.string.categories),
            icon = Icons.Outlined.ImageSearch,
            contentColor = colorScheme.tertiary,
            useIndicator = true,
            indicatorCounter = explore.categoryCount,
            route = routes.categories,
            available = true,
            previewMedia = explore.categoryThumbnails,
            previewStyle = ShortcutPreviewStyle.SPREAD,
        )
    }
    return map
}

/** Whole days until [expirySeconds], counted from [nowSeconds]; 0 once it is due today or past. */
fun trashDaysLeft(expirySeconds: Long, nowSeconds: Long): Int =
    ((expirySeconds - nowSeconds).coerceAtLeast(0) / 86_400L).toInt()
