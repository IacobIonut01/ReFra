/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.docs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.presentation.components.EmptyAlbum
import com.dot.gallery.core.presentation.components.EmptyMedia
import com.dot.gallery.core.presentation.components.LoadingAlbumContent
import com.dot.gallery.core.presentation.components.LoadingMediaContent
import com.dot.gallery.core.ui.components.SettingsAppHeader
import com.dot.gallery.core.ui.components.settings
import com.dot.gallery.core.logging.AreaGroup
import com.dot.gallery.core.logging.LogLevel
import com.dot.gallery.core.logging.LogStats
import com.dot.gallery.core.logging.LogEntry
import com.dot.gallery.docs.scenes.DateFormatScene
import com.dot.gallery.docs.scenes.SettingsItemScene
import com.dot.gallery.docs.scenes.DocsLongPressActionScene
import com.dot.gallery.docs.scenes.DocsVisualSearchOptionsScene
import com.dot.gallery.data.entity.SmartScanStatus
import com.dot.gallery.docs.scenes.TimelineDateHeaderScene
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.data.fixture.DemoFixtures
import com.dot.gallery.docs.scenes.docsLogoPainter
import com.dot.gallery.domain.settings.ALIAS_REFRA
import com.dot.gallery.feature.settings.ui.CategoriesSettingsContent
import com.dot.gallery.feature.settings.ui.CategoriesSettingsState
import com.dot.gallery.feature.settings.ui.CloudIndexingProviderState
import com.dot.gallery.feature.settings.ui.PrivateFolderDetailScreen
import com.dot.gallery.feature.settings.ui.PrivateFolderPage
import com.dot.gallery.feature.settings.ui.PrivateFolderStats
import com.dot.gallery.feature.settings.ui.SettingsBackupScreen
import com.dot.gallery.feature.settings.ui.SettingsCloudIndexingContent
import com.dot.gallery.feature.settings.ui.SettingsCloudIndexingState
import com.dot.gallery.feature.settings.ui.SlideshowSettingsContent
import com.dot.gallery.feature.settings.ui.SettingsSecurityContent
import com.dot.gallery.feature.settings.ui.SmartScanDetailContent
import com.dot.gallery.feature.settings.ui.SmartScanDetailState
import com.dot.gallery.feature.settings.ui.SettingsSecurityState
import com.dot.gallery.feature.settings.ui.SmartFeaturesScreenContent
import com.dot.gallery.feature.settings.ui.SmartFeaturesScreenState
import com.dot.gallery.feature.settings.ui.SettingsRoute
import com.dot.gallery.feature.settings.ui.SettingsScreenActions
import com.dot.gallery.feature.settings.ui.SettingsScreenContent
import com.dot.gallery.feature.settings.ui.SettingsScreenState
import com.dot.gallery.feature.settings.ui.SessionLogsActions
import com.dot.gallery.feature.settings.ui.SessionLogsContent
import com.dot.gallery.feature.settings.ui.SessionLogsMode
import com.dot.gallery.feature.settings.ui.SessionLogsState
import com.dot.gallery.feature.settings.ui.DeveloperScreenActions
import com.dot.gallery.feature.settings.ui.DeveloperScreenContent
import com.dot.gallery.feature.settings.ui.DeveloperScreenState
import com.dot.gallery.feature.settings.ui.SettingsGeneralActions
import com.dot.gallery.feature.settings.ui.SettingsGeneralContent
import com.dot.gallery.feature.settings.ui.SettingsGeneralState
import com.dot.gallery.core.backup.BackupContents
import com.dot.gallery.core.backup.BackupSection
import com.dot.gallery.core.backup.BackupSelection
import com.dot.gallery.core.backup.VaultSummary
import com.dot.gallery.core.ml.DownloadInfo
import com.dot.gallery.core.ml.ModelGroup
import com.dot.gallery.core.ml.ModelStatus
import com.dot.gallery.feature.settings.ui.AiModelGroupUi
import com.dot.gallery.feature.settings.ui.AiModelsManagerActions
import com.dot.gallery.feature.settings.ui.AiModelsManagerContent
import com.dot.gallery.feature.settings.ui.AiModelsManagerState
import com.dot.gallery.feature.settings.ui.ExportEncryptStep
import com.dot.gallery.feature.settings.ui.ExportIncludeStep
import com.dot.gallery.feature.settings.ui.ExportSaveStep
import com.dot.gallery.feature.settings.ui.ImportChooseState
import com.dot.gallery.feature.settings.ui.ImportChooseStep
import com.dot.gallery.feature.settings.ui.ImportReviewStep
import com.dot.gallery.feature.settings.ui.ColorPaletteContent
import com.dot.gallery.feature.settings.ui.ColorPaletteOption
import com.dot.gallery.feature.settings.ui.ColorPaletteState
import com.dot.gallery.core.ui.mediaview.KnownVisualSearchProvider
import com.dot.gallery.core.ui.mediaview.VisualSearchTarget
import com.dot.gallery.domain.model.AlbumSection
import com.dot.gallery.domain.model.AlbumSectionType
import com.dot.gallery.feature.settings.ui.SettingsMediaViewerActions
import com.dot.gallery.feature.settings.ui.SettingsMediaViewerContent
import com.dot.gallery.feature.settings.ui.SettingsMediaViewerState
import com.dot.gallery.feature.settings.ui.SettingsNavigationActions
import com.dot.gallery.feature.settings.ui.SettingsSelectionActionsContent
import com.dot.gallery.feature.settings.ui.SettingsTimelineAlbumsActions
import com.dot.gallery.feature.settings.ui.SettingsTimelineAlbumsContent
import com.dot.gallery.feature.settings.ui.SettingsTimelineAlbumsState
import com.dot.gallery.feature.settings.ui.SettingsNavigationContent
import com.dot.gallery.feature.settings.ui.SettingsNavigationState

/**
 * The scene catalogue the docs site embeds. Every entry must be renderable
 * without app state — scenes that need data own their own fixtures.
 */
val sceneRegistry: List<DocsScene> = listOf(
    DocsScene(
        id = "settings-item",
        title = "Settings item",
        pathTrace = listOf("Settings", "Any section row"),
        states = listOf("default", "disabled", "horizontal"),
        presentation = "component",
        widthDp = 412,
        heightDp = 220,
    ) { state -> SettingsItemScene(state) },
    DocsScene(
        id = "settings-list",
        title = "Settings list",
        pathTrace = listOf("Settings"),
    ) {
        LazyColumn(Modifier.fillMaxSize()) {
            settings {
                Header("Library")
                Preference(
                    title = "Media source",
                    summary = "All media on this device",
                    icon = Icons.Outlined.Settings,
                )
                Preference(
                    title = "Favourites album",
                    icon = Icons.Outlined.Star,
                    onClick = {},
                )
                Header("Timeline")
                Preference(
                    title = "Group by month",
                    summary = "Off",
                )
                Preference(
                    title = "Secure items",
                    summary = "Requires authentication",
                    enabled = false,
                )
            }
        }
    },
    DocsScene(
        id = "settings-hub",
        title = "Settings hub",
        pathTrace = listOf("Settings"),
        states = listOf("banner", "banner-dismissed", "developer", "scrolled"),
    ) { state ->
        val docsNav = LocalDocsNavigator.current
        SettingsScreenContent(
            appIcon = docsLogoPainter(ALIAS_REFRA),
            // "scrolled" opens part-way down so rows sit under the transparent blur bar.
            listState = rememberLazyListState(
                initialFirstVisibleItemIndex = if (state == "scrolled") 2 else 0,
                initialFirstVisibleItemScrollOffset = if (state == "scrolled") 120 else 0,
            ),
            state = SettingsScreenState(
                developerMode = state == "developer",
                bannerDismissed = state == "banner-dismissed",
                hasCloudProviders = true,
            ),
            actions = SettingsScreenActions(
                onNavigate = { route ->
                    settingsRouteSceneId(route)?.let { docsNav?.navigateTo(it) }
                },
                onDismissBanner = {},
                onRestoreBanner = {},
                onShowSupport = {},
                onDeveloperUnlocked = {},
            ),
        )
    },
    DocsScene(
        id = "settings-private-folder",
        title = "Private folder detail",
        pathTrace = listOf("Settings", "Security", "Private folder"),
        states = listOf("none", "configured", "saf-uri", "how-it-works"),
    ) { state ->
        PrivateFolderDetailScreen(
            currentUri = when (state) {
                "configured", "how-it-works" -> "content://com.android.externalstorage.documents/tree/primary%3APictures%2FPrivate"
                "saf-uri" -> "content://com.android.externalstorage.documents/tree/1234-5678%3ASD%20Card%2FHidden%20Photos"
                else -> ""
            },
            stats = PrivateFolderStats(itemCount = 342, totalBytes = 1_932_735_283L),
            onOpenFolder = {},
            initialPage = if (state == "how-it-works") PrivateFolderPage.HowItWorks else PrivateFolderPage.Main,
            onPickFolder = {},
            onClearFolder = {},
            onClearFolderAndReveal = {},
        )
    },
    DocsScene(
        id = "settings-cloud-indexing",
        title = "Cloud indexing detail",
        pathTrace = listOf("Settings", "Smart Features", "Cloud libraries"),
        states = listOf("none", "configured", "delegated"),
    ) { state ->
        SettingsCloudIndexingContent(
            state = SettingsCloudIndexingState(
                providerSmartSearch = state == "delegated",
                providers = when (state) {
                    "configured", "delegated" -> listOf(
                        CloudIndexingProviderState(ProviderType.IMMICH, indexOnDevice = true, delegatesSmartSearch = state == "delegated"),
                        CloudIndexingProviderState(ProviderType.WEBDAV, indexOnDevice = false, delegatesSmartSearch = false),
                    )
                    else -> emptyList()
                },
            ),
        )
    },
    DocsScene(
        id = "settings-backup",
        title = "Backup & restore",
        pathTrace = listOf("Settings", "Backup & restore"),
        states = listOf("hub", "never", "scrolled"),
    ) { state ->
        SettingsBackupScreen(
            state = if (state == "never") {
                com.dot.gallery.feature.settings.ui.BackupHubState()
            } else {
                com.dot.gallery.feature.settings.ui.BackupHubState(
                    lastExportLabel = "Nov 4, 09:12",
                    lastExportSize = "1.2 GB",
                    lastExportSections = 11,
                    healthCheckedLabel = "Nov 7, 07:40",
                )
            },
            actions = com.dot.gallery.feature.settings.ui.BackupHubActions(
                onExport = {},
                onImport = {},
                onCleanNow = {},
                onAutoCleanChange = {}
            ),
            listState = rememberLazyListState(
                initialFirstVisibleItemIndex = if (state == "scrolled") 1 else 0,
                initialFirstVisibleItemScrollOffset = if (state == "scrolled") 40 else 0,
            ),
        )
    },
    DocsScene(
        id = "settings-slideshow",
        title = "Slideshow settings",
        pathTrace = listOf("Settings", "Slideshow"),
    ) {
        SlideshowSettingsContent()
    },
    DocsScene(
        id = "settings-smart-features",
        title = "Smart Features",
        pathTrace = listOf("Settings", "Smart Features"),
        states = listOf("ai-ready", "ai-unavailable"),
    ) { state ->
        SmartFeaturesScreenContent(
            state = SmartFeaturesScreenState(
                aiAvailable = state == "ai-ready",
                modelsReady = state == "ai-ready",
                modelSummary = "AI-powered search and classification are available",
                categoriesSummary = "Categorise your media",
                scanSummary = "Refresh and repair metadata, search, categories, and persons",
                hasCloudProviders = true,
            ),
        )
    },
    DocsScene(
        id = "settings-security",
        title = "Security settings",
        pathTrace = listOf("Settings", "Security"),
        states = listOf("default", "aapm"),
    ) { state ->
        SettingsSecurityContent(
            state = SettingsSecurityState(advancedProtection = state == "aapm"),
        )
    },
    DocsScene(
        id = "settings-categories",
        title = "Categories settings",
        pathTrace = listOf("Settings", "Smart Features", "Categories"),
        states = listOf("idle", "scanning"),
    ) { state ->
        val nowSec = DOCS_DEMO_EPOCH_SECONDS
        CategoriesSettingsContent(
            state = CategoriesSettingsState(
                categoryScan = if (state == "scanning") DemoFixtures.smartScanRun(nowSec) else null,
                workerProgress = if (state == "scanning") 42f else 0f,
                hasCategories = true,
                modelsReady = true,
                categoryCount = 24,
                matchedCount = 1_812,
                unmatchedCount = 204,
                lastScanAt = nowSec * 1_000L,
            ),
        )
    },
    DocsScene(
        id = "settings-smart-scan",
        title = "Smart Features data",
        pathTrace = listOf("Settings", "Smart Features", "Smart Features data"),
        states = listOf("idle", "running", "partial", "scrolled"),
    ) { state ->
        val nowSec = DOCS_DEMO_EPOCH_SECONDS
        SmartScanDetailContent(
            state = SmartScanDetailState(
                activeScan = if (state == "running") DemoFixtures.smartScanRun(nowSec) else null,
                activePhases = if (state == "running") DemoFixtures.smartScanPhases(nowSec) else emptyList(),
                latestScan = DemoFixtures.smartScanRun(nowSec).copy(
                    status = if (state == "partial") SmartScanStatus.PARTIAL else SmartScanStatus.SUCCEEDED,
                    finishedAt = nowSec * 1_000L,
                ),
                searchReady = true,
                personsReady = true,
            ),
            listState = rememberLazyListState(
                initialFirstVisibleItemIndex = if (state == "scrolled") 2 else 0,
                initialFirstVisibleItemScrollOffset = if (state == "scrolled") 60 else 0,
            ),
        )
    },
    DocsScene(
        id = "settings-session-logs",
        title = "Session logs",
        pathTrace = listOf("Settings", "Developer", "Session logs"),
        states = listOf("recent", "by-area", "empty"),
    ) { state ->
        val nowMs = DOCS_DEMO_EPOCH_SECONDS * 1_000L
        val entries = listOf(
            LogEntry(
                ts = nowMs - 4_000,
                level = "ERROR",
                tag = "cloud.sync",
                message = "Upload failed after 3 attempts",
                ctx = mapOf("provider" to "immich", "asset" to "IMG_2041.jpg"),
                stackTrace = "java.io.IOException: timeout\n\tat okhttp3.RealCall.execute(...)",
                session = "20260214-220102",
                screen = "TimelineScreen",
                scope = "worker.upload",
            ),
            LogEntry(
                ts = nowMs - 3_500,
                level = "WARN",
                tag = "decode.heic",
                message = "Hardware decode fell back to software",
                session = "20260214-220102",
                scope = "decode.heic",
            ),
            LogEntry(
                ts = nowMs - 40_000,
                level = "INFO",
                tag = "media-ops",
                message = "Album refresh completed",
                session = "20260214-215500",
                screen = "LibraryScreen",
            ),
            LogEntry(
                ts = nowMs - 90_000,
                level = "CRASH",
                tag = "app.crash",
                message = "Unhandled exception in viewer",
                stackTrace = "java.lang.IllegalStateException: ...",
            ),
        )
        val groups = listOf(
            AreaGroup(
                id = "TimelineScreen",
                kind = AreaGroup.Kind.SCREEN,
                entries = entries.filter { it.screen == "TimelineScreen" },
            ),
            AreaGroup(
                id = "worker.upload",
                kind = AreaGroup.Kind.SCOPE,
                entries = entries.filter { it.scope == "worker.upload" },
            ),
        )
        SessionLogsContent(
            state = SessionLogsState(
                loading = false,
                mode = if (state == "by-area") SessionLogsMode.BY_AREA else SessionLogsMode.RECENT,
                entries = if (state == "empty") emptyList() else entries,
                groups = if (state == "empty") emptyList() else groups,
                expandedGroups = setOf("worker.upload"),
            ),
            actions = SessionLogsActions(),
        )
    },
    DocsScene(
        id = "settings-general",
        title = "General settings",
        pathTrace = listOf("Settings", "General"),
    ) {
        SettingsGeneralContent(
            state = SettingsGeneralState(
                supportsTrash = true,
                appNameAlias = "ReFra",
                appLogoAlias = "ReFra",
            ),
            actions = SettingsGeneralActions(),
        )
    },
    DocsScene(
        id = "settings-navigation",
        title = "Navigation settings",
        pathTrace = listOf("Settings", "Navigation"),
    ) {
        SettingsNavigationContent(
            state = SettingsNavigationState(),
            actions = SettingsNavigationActions(),
        )
    },
    DocsScene(
        id = "settings-appearance",
        title = "Appearance settings",
        pathTrace = listOf("Settings", "Appearance"),
        states = listOf("default", "maps", "wallpaper"),
    ) { state ->
        ColorPaletteContent(
            state = ColorPaletteState(
                supportsBlur = true,
                mapsEnabled = state != "default",
                showManualCartoKey = state == "maps",
                wallpaperPalettes = if (state == "wallpaper") listOf(
                    ColorPaletteOption("Primary", 0xFF5B8DEF.toInt(), "FF5B8DEF"),
                    ColorPaletteOption("Secondary", 0xFF9BB7F0.toInt(), "FF9BB7F0"),
                    ColorPaletteOption("Tertiary", 0xFF7E57C2.toInt(), "FF7E57C2"),
                ) else emptyList(),
            )
        )
    },
    DocsScene(
        id = "settings-selection-actions",
        title = "Selection actions",
        pathTrace = listOf("Settings", "Navigation", "Selection actions"),
    ) {
        SettingsSelectionActionsContent()
    },
    DocsScene(
        id = "settings-timeline-albums",
        title = "Timeline & albums settings",
        pathTrace = listOf("Settings", "Timeline & albums"),
        states = listOf("default", "sections", "scrolled"),
        swipedStates = listOf("scrolled"),
    ) { state ->
        SettingsTimelineAlbumsContent(
            state = SettingsTimelineAlbumsState(
                supportsFavorites = true,
                albumSections = if (state == "sections") listOf(
                    AlbumSection(id = 1, label = "Camera", type = AlbumSectionType.APPS.value, displayOrder = 0),
                    AlbumSection(id = 2, label = "Trips", type = AlbumSectionType.CUSTOM.value, displayOrder = 1),
                ) else emptyList(),
                albumCountBySection = mapOf(1L to 412, 2L to 87),
            ),
            actions = SettingsTimelineAlbumsActions(),
        )
    },
    DocsScene(
        id = "settings-media-viewer",
        title = "Media viewer settings",
        pathTrace = listOf("Settings", "Media viewer"),
        states = listOf("default", "no-visual-search"),
    ) { state ->
        SettingsMediaViewerContent(
            state = SettingsMediaViewerState(
                hdrDisplayCapable = true,
                supportsFavorites = true,
                visualSearchTargets = if (state == "no-visual-search") emptyList() else listOf(
                    VisualSearchTarget(
                        packageName = "com.google.ar.lens",
                        activityName = "com.google.ar.lens.LensActivity",
                        label = "Lens",
                        known = KnownVisualSearchProvider.GOOGLE_LENS,
                    ),
                ),
            ),
            actions = SettingsMediaViewerActions(),
        )
    },
    DocsScene(
        id = "visual-search-options",
        title = "Visual search options",
        pathTrace = listOf("Settings", "Media viewer", "Visual search"),
        states = listOf("default", "off", "scrolled"),
        swipedStates = listOf("scrolled"),
    ) { state -> DocsVisualSearchOptionsScene(state) },
    DocsScene(
        id = "long-press-action",
        title = "Long-press action",
        pathTrace = listOf("Settings", "Media viewer", "Long-press action"),
        states = listOf("rotate", "cutout-unavailable"),
    ) { state -> DocsLongPressActionScene(state) },
    DocsScene(
        id = "settings-backup-wizard",
        title = "Backup wizards",
        pathTrace = listOf("Settings", "Backup & restore", "Create backup"),
        states = listOf("include", "encrypt", "save", "choose-file", "password", "review", "review-empty"),
    ) { state ->
        val exportSections = listOf(
            BackupSection.SETTINGS, BackupSection.LOCAL_FAVORITES, BackupSection.CLOUD_FAVORITES,
            BackupSection.CLOUD_CONFIGS, BackupSection.VAULTS, BackupSection.ALBUMS,
            BackupSection.CATEGORIES, BackupSection.EDIT_BACKUPS,
        )
        val on = exportSections - BackupSection.EDIT_BACKUPS
        when (state) {
            "include" -> ExportIncludeStep(
                sections = exportSections,
                isSelected = { it in on },
                onToggle = { _, _ -> },
                vaultItems = emptyList(),
                vaultIsSelected = { true },
                onToggleVault = { _, _ -> },
                showVaultList = false,
                nextEnabled = true,
                onNext = {},
                onCancel = {},
            )
            "encrypt" -> ExportEncryptStep(
                protect = true,
                onProtectChange = {},
                password = "correct horse",
                onPasswordChange = {},
                confirm = "correct horse",
                onConfirmChange = {},
                showPassword = false,
                onShowPasswordChange = {},
                nextEnabled = true,
                onNext = {},
                onBack = {},
            )
            "save" -> ExportSaveStep(
                fileName = "refra-backup-20261107-091200.rfbk",
                protected = true,
                includedTitles = listOf("App settings", "Favorites", "Cloud accounts", "Albums", "Vaults"),
                sectionCount = on.size,
                totalSections = exportSections.size,
                vaultCount = 2,
                onSave = {},
                onBack = {},
            )
            "choose-file", "password" -> ImportChooseStep(
                state = if (state == "password") ImportChooseState.NeedsPassword(wrongPassword = false)
                else ImportChooseState.NoFile,
                onPickFile = {},
                onSubmitPassword = {},
                onCancel = {},
            )
            else -> ImportReviewStep(
                sections = if (state == "review-empty") emptyList() else listOf(
                    BackupSection.SETTINGS, BackupSection.LOCAL_FAVORITES, BackupSection.CLOUD_FAVORITES,
                    BackupSection.CLOUD_CONFIGS, BackupSection.VAULTS,
                ),
                contents = BackupContents(
                    schemaVersion = 2,
                    appVersionName = "1.8.0",
                    exportedAt = 1_771_000_000_000,
                    encrypted = false,
                    settingsCount = 148,
                    localFavoritesCount = 37,
                    cloudFavoritesCount = 12,
                    cloudConfigsCount = 2,
                    vaultCount = 2,
                    vaultMediaCount = 64,
                    vaults = listOf(
                        VaultSummary(uuid = "v-001", name = "Private", mediaCount = 41),
                        VaultSummary(uuid = "v-002", name = "Work", mediaCount = 23),
                    ),
                ),
                selection = BackupSelection(),
                onToggle = { _, _ -> },
                onToggleVault = { _, _ -> },
                onRestore = {},
                onBack = {},
            )
        }
    },
    DocsScene(
        id = "settings-ai-models",
        title = "AI models manager",
        pathTrace = listOf("Settings", "Smart Features", "AI models"),
        states = listOf("not-installed", "downloading", "ready"),
    ) { state ->
        AiModelsManagerContent(
            state = AiModelsManagerState(
                isBundled = false,
                canInstallModels = true,
                groups = ModelGroup.entries.map { group ->
                    AiModelGroupUi(
                        group = group,
                        status = when (state) {
                            "downloading" -> ModelStatus.DOWNLOADING
                            "ready" -> ModelStatus.READY
                            else -> ModelStatus.NOT_INSTALLED
                        },
                        progress = if (state == "downloading") 42f else 0f,
                        info = if (state == "downloading") DownloadInfo(
                            speed = 8_500_000,
                            downloadedBytes = 55_000_000,
                            totalBytes = 132_000_000,
                            currentFile = group.files.first(),
                        ) else DownloadInfo(),
                        installedSize = if (state == "ready") 132_000_000 else 0L,
                    )
                },
            ),
            actions = AiModelsManagerActions(),
        )
    },
    DocsScene(
        id = "settings-developer",
        title = "Developer",
        pathTrace = listOf("Settings", "Developer"),
        states = listOf("default", "crash-banner"),
    ) { state ->
        val nowMs = DOCS_DEMO_EPOCH_SECONDS * 1_000L
        val errors = listOf(
            LogEntry(
                ts = nowMs - 4_000,
                level = "ERROR",
                tag = "cloud.sync",
                message = "Upload failed after 3 attempts",
                session = "20260214-220102",
                screen = "TimelineScreen",
                scope = "worker.upload",
            ),
            LogEntry(
                ts = nowMs - 30_000,
                level = "ERROR",
                tag = "decode.jxl",
                message = "Decoder exhausted retries",
                session = "20260214-220102",
            ),
        )
        DeveloperScreenContent(
            state = DeveloperScreenState(
                stats = LogStats(
                    totalBytes = 2_400_000,
                    sessionCount = 7,
                    crashCount = if (state == "crash-banner") 1 else 0,
                    unreportedCrash = state == "crash-banner",
                ),
                totalCount = 1_204,
                latestErrors = errors,
                entries = errors,
                unreportedCrashEntry = if (state == "crash-banner") {
                    LogEntry(
                        ts = nowMs - 300_000,
                        level = "CRASH",
                        tag = "app.crash",
                        message = "Unhandled exception in viewer",
                    )
                } else null,
                minLevel = LogLevel.INFO,
                breadcrumbsEnabled = true,
            ),
            actions = DeveloperScreenActions(),
        )
    },
    DocsScene(
        id = "settings-app-header",
        title = "Settings app header",
        pathTrace = listOf("Settings"),
        presentation = "component",
        widthDp = 412,
        heightDp = 160,
    ) {
        Column(Modifier.fillMaxWidth()) {
            SettingsAppHeader()
            HorizontalDivider()
        }
    },
    DocsScene(
        id = "empty-media",
        title = "Empty media state",
        pathTrace = listOf("Library", "Empty timeline"),
    ) {
        EmptyMedia(Modifier.fillMaxSize())
    },
    DocsScene(
        id = "empty-album",
        title = "Empty album state",
        pathTrace = listOf("Library", "Empty album"),
    ) {
        EmptyAlbum(Modifier.fillMaxSize())
    },
    DocsScene(
        id = "loading-media",
        title = "Loading media grid",
        pathTrace = listOf("Library", "Timeline (loading)"),
        states = listOf("grid-2", "grid-3", "grid-4"),
    ) { state ->
        val grid = state?.removePrefix("grid-")?.toIntOrNull() ?: 3
        LoadingMediaContent(grid = grid, canShimmer = true, modifier = Modifier.fillMaxSize())
    },
    DocsScene(
        id = "loading-album",
        title = "Loading album grid",
        pathTrace = listOf("Library", "Albums (loading)"),
        states = listOf("grid-2", "grid-3", "grid-4"),
    ) { state ->
        val grid = state?.removePrefix("grid-")?.toIntOrNull() ?: 3
        LoadingAlbumContent(grid = grid, canShimmer = true, modifier = Modifier.fillMaxSize())
    },
    DocsScene(
        id = "timeline-date-headers",
        title = "Timeline date headers",
        pathTrace = listOf("Library", "Timeline"),
    ) { state -> TimelineDateHeaderScene(state) },
    DocsScene(
        id = "settings-date-formats",
        title = "Date formats",
        pathTrace = listOf("Settings", "Timeline", "Date formats"),
        states = listOf("default", "scrolled"),
        swipedStates = listOf("scrolled"),
    ) { state -> DateFormatScene(state) },
) + browseScenes + setupScenes + organizeScenes + platformScenes()

fun findScene(id: String?): DocsScene =
    requireNotNull(findSceneOrNull(id)) { "Unknown docs scene: $id" }

fun findSceneOrNull(id: String?): DocsScene? = sceneRegistry.find { it.id == id }

/** Settings hub route → registry scene id; routes without a scene return null. */
fun settingsRouteSceneId(route: SettingsRoute): String? = when (route) {
    SettingsRoute.APPEARANCE -> "settings-appearance"
    SettingsRoute.TIMELINE_ALBUMS -> "settings-timeline-albums"
    SettingsRoute.MEDIA_VIEWER -> "settings-media-viewer"
    SettingsRoute.NAVIGATION -> "settings-navigation"
    SettingsRoute.GENERAL -> "settings-general"
    SettingsRoute.SECURITY -> "settings-security"
    SettingsRoute.BACKUP -> "settings-backup"
    SettingsRoute.SMART_FEATURES -> "settings-smart-features"
    SettingsRoute.DEVELOPER -> "settings-developer"
    SettingsRoute.CLOUD_ACCOUNTS, SettingsRoute.HELP -> null
}
