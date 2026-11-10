/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.settings.subsettings

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.icu.text.Collator
import android.icu.util.ULocale
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.dot.gallery.BuildConfig
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.logging.printWarn
import com.dot.gallery.core.navigate
import com.dot.gallery.core.ocr.catalog.OcrModelRole
import com.dot.gallery.core.ocr.model.OcrFileMatch
import com.dot.gallery.core.ocr.model.OcrInstallPolicy
import com.dot.gallery.core.ocr.model.OcrPackState
import com.dot.gallery.core.ocr.model.OcrPackStore
import com.dot.gallery.core.ocr.model.OcrSwitchPolicy
import com.dot.gallery.core.ocr.policy.OcrLocaleKey
import com.dot.gallery.core.ocr.policy.OcrLocalePackResolver
import com.dot.gallery.core.presentation.util.Screen
import com.dot.gallery.core.ui.settings.rememberPreference
import com.dot.gallery.core.work.WorkComponentHolder
import com.dot.gallery.core.workers.cancelOcrPackInstall
import com.dot.gallery.core.workers.installOcrPack
import com.dot.gallery.data.entity.SmartScanFeature
import com.dot.gallery.domain.settings.OCR_MODE_ON_REQUEST
import com.dot.gallery.domain.settings.SettingsKeys
import com.dot.gallery.feature.help.domain.SettingsSearchRegistry
import com.dot.gallery.feature.settings.ui.OcrPackRow
import com.dot.gallery.feature.settings.ui.OcrPackStatus
import com.dot.gallery.feature.settings.ui.OcrPacksActions
import com.dot.gallery.feature.settings.ui.OcrPacksContent
import com.dot.gallery.feature.settings.ui.OcrPacksState
import com.dot.gallery.feature.settings.ui.OcrVerification
import com.dot.gallery.feature.settings.ui.OcrVerifiedFile
import com.dot.gallery.feature.settings.ui.OcrVerifiedResult
import com.dot.gallery.feature.settings.ui.TextRecognitionActions
import com.dot.gallery.feature.settings.ui.TextRecognitionContent
import com.dot.gallery.feature.settings.ui.TextRecognitionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** Published pack list (languages, files, sizes, SHA-256, pinned links), opened in the browser. */
private const val PACK_LIST_URL = "https://github.com/IacobIonut01/ReFra/blob/main/app/ml-models/catalog/PACKS.md"
private const val TAG = "ml.ocr"

/**
 * Text recognition's state as the Intelligence Hub row and the text recognition page show it. It can
 * never stay on without a ready pack in use: it is turned off when the last one goes away.
 */
@Composable
internal fun rememberTextRecognitionState(): TextRecognitionState {
    val component = remember { WorkComponentHolder.component }
    val store = component.ocrPackStore
    val repository = component.ocrRepository

    var enabled by rememberPreference(SettingsKeys.ocrEnabled)
    val mode by rememberPreference(SettingsKeys.ocrMode)
    val indexLibrary by rememberPreference(SettingsKeys.ocrIndexLibrary)
    val packs by rememberPreference(SettingsKeys.ocrPacks)
    val links by rememberPreference(SettingsKeys.ocrExternalModelUris)
    val states by store.states.collectAsState()
    val indexed by repository.observeCount().collectAsState(initial = 0)
    val indexBytes by repository.observeBytes().collectAsState(initial = 0L)

    LaunchedEffect(links) { withContext(Dispatchers.IO) { store.setLinks(links) } }
    val ready = states.filterValues { it.isReady }.keys
    val active = OcrSwitchPolicy.activePacks(packs, ready)
    LaunchedEffect(active) { if (enabled && active.isEmpty()) enabled = false }
    val modelBytes by produceState(0L, states) { value = withContext(Dispatchers.IO) { store.storedBytes() } }

    return TextRecognitionState(
        canEnable = OcrSwitchPolicy.canEnable(active.toSet()),
        enabled = enabled,
        onRequest = mode == OCR_MODE_ON_REQUEST,
        indexLibrary = indexLibrary,
        installedPackIds = store.catalog.packs.map { it.id }.filter { it in active },
        modelBytes = modelBytes,
        indexBytes = indexBytes,
        indexedPhotos = indexed,
    )
}

/**
 * Intelligence Hub › Text recognition. It cannot be turned on until a pack is installed or linked,
 * and is turned back off when the last ready pack goes away.
 */
@Composable
fun TextRecognitionSettingsScreen() {
    val component = remember { WorkComponentHolder.component }
    val store = component.ocrPackStore
    val repository = component.ocrRepository
    val eventHandler = LocalEventHandler.current
    val scope = rememberCoroutineScope()

    val state = rememberTextRecognitionState()
    var enabled by rememberPreference(SettingsKeys.ocrEnabled)
    var mode by rememberPreference(SettingsKeys.ocrMode)
    var indexLibrary by rememberPreference(SettingsKeys.ocrIndexLibrary)
    val packs by rememberPreference(SettingsKeys.ocrPacks)
    val states by store.states.collectAsState()
    val active = OcrSwitchPolicy.activePacks(packs, states.filterValues { it.isReady }.keys)

    val scheduleIndexing: () -> Unit = {
        scope.launch {
            runCatching { component.smartScanScheduler.automatic(SmartScanFeature.TEXT.bit) }
                .onFailure { printWarn(TAG, "Scheduling text indexing failed", mapOf("error" to it.toString())) }
        }
    }

    TextRecognitionContent(
        state = state,
        actions = TextRecognitionActions(
            onEnabledChange = { requested ->
                enabled = OcrSwitchPolicy.effective(requested, active.toSet())
                if (enabled && indexLibrary) scheduleIndexing()
                if (!requested) scope.launch { component.ppOcrV5Engine.releaseSessions() }
            },
            onModeChange = { mode = it },
            onIndexLibraryChange = {
                indexLibrary = it
                if (it && enabled) scheduleIndexing()
            },
            onOpenPacks = { eventHandler.navigate(Screen.OcrPacksScreen()) },
            onDeleteIndex = {
                scope.launch {
                    withContext(Dispatchers.IO) { repository.deleteAll() }
                    if (enabled && indexLibrary) scheduleIndexing()
                }
            },
        ),
        searchRoute = Screen.TextRecognitionSettingsScreen(),
        searchSink = SettingsSearchRegistry::register,
    )
}

/**
 * Settings › Text recognition › Language packs. Packs are downloaded by `OcrPackInstallWorker`, which
 * adds them to `ocrPacks` once they verify; picked files are linked in place and shown in the
 * verification sheet.
 */
@Composable
fun OcrPacksSettingsScreen() {
    val component = remember { WorkComponentHolder.component }
    val store = component.ocrPackStore
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val policy = remember { OcrInstallPolicy.of(BuildConfig.OFFLINE_MODE, hasInternet(context)) }

    var enabled by rememberPreference(SettingsKeys.ocrEnabled)
    var packs by rememberPreference(SettingsKeys.ocrPacks)
    var links by rememberPreference(SettingsKeys.ocrExternalModelUris)
    val states by store.states.collectAsState()
    var verification by remember { mutableStateOf<OcrVerification?>(null) }

    LaunchedEffect(links) { withContext(Dispatchers.IO) { store.setLinks(links) } }

    val configuration = LocalConfiguration.current
    val uiLocale = configuration.locales[0] ?: Locale.getDefault()
    val ownTags = remember(configuration) {
        (0 until configuration.locales.size()).map { configuration.locales[it].toLanguageTag() }
    }
    val suggestion = remember(ownTags) { OcrLocalePackResolver.suggest(ownTags, store.catalog) }

    val rows by produceState(emptyList<OcrPackRow>(), states, uiLocale, ownTags, policy) {
        value = withContext(Dispatchers.IO) { packRows(store, states, uiLocale, ownTags, suggestion.packIds, policy) }
    }
    val unavailable = remember(uiLocale) { sortedNames(store.catalog.unsupportedLocales, uiLocale) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val before = store.readyPackIds()
            val files = withContext(Dispatchers.IO) { uris.map { uri -> verify(context, store, uri) } }
            links = store.linksEncoded()
            val newlyReady = store.catalog.packs.map { it.id }.filter { it in store.readyPackIds() && it !in before }
            packs = packs + newlyReady
            verification = OcrVerification(files, newlyReady)
        }
    }

    OcrPacksContent(
        state = OcrPacksState(
            downloadsAllowed = policy.downloadsAllowed,
            packs = rows,
            unavailableLanguages = unavailable,
            verification = verification,
        ),
        actions = OcrPacksActions(
            onDownload = { packId ->
                scope.launch {
                    val total = withContext(Dispatchers.IO) { store.missingModels(packId).sumOf { it.onnx.sizeBytes } }
                    store.setProgress(packId, OcrPackState.Downloading(0, total))
                    if (!component.backgroundTaskScheduler.installOcrPack(packId, policy)) store.setProgress(packId, null)
                }
            },
            onCancelDownload = { packId ->
                component.backgroundTaskScheduler.cancelOcrPackInstall(packId)
                store.setProgress(packId, null)
            },
            onDelete = { packId ->
                scope.launch {
                    val others = store.readyPackIds() - packId
                    withContext(Dispatchers.IO) { store.delete(packId, others) }
                    links = store.linksEncoded()
                    packs = packs - packId
                    if (OcrSwitchPolicy.activePacks(packs - packId, store.readyPackIds()).isEmpty()) enabled = false
                    component.ppOcrV5Engine.releaseSessions()
                }
            },
            onAddFiles = { picker.launch(arrayOf("*/*")) },
            onOpenPackList = { openPackList(context) },
            onDismissVerification = { verification = null },
        ),
    )
}

/** Builds the pack rows: CLDR names in the UI language, sizes from the catalog, and each pack's state. */
private fun packRows(
    store: OcrPackStore,
    states: Map<String, OcrPackState>,
    uiLocale: Locale,
    ownTags: List<String>,
    suggested: Set<String>,
    policy: OcrInstallPolicy,
): List<OcrPackRow> {
    val ownKeys = ownTags.map(OcrLocaleKey::of).toSet()
    // The shared text finder is counted once, on the first pack in app storage.
    val finderCarrier = store.catalog.packs.firstOrNull { pack ->
        states[pack.id] == OcrPackState.Installed &&
            store.modelsFor(pack.id).any { it.role == OcrModelRole.DETECTOR && store.isStored(it) }
    }?.id
    return store.catalog.packs.map { pack ->
        // English rides along in most recognizers; it is only listed for the Latin and English packs.
        val tags = pack.locales.filter { it != "en" || pack.id == OcrLocalePackResolver.LATIN || pack.id == OcrLocalePackResolver.ENGLISH }
        val named = tags.map { it to displayName(it, uiLocale) }
        val collator = Collator.getInstance(ULocale.forLocale(uiLocale))
        val sorted = named.sortedWith { a, b -> collator.compare(a.second, b.second) }
        val status = when (val state = states[pack.id] ?: OcrPackState.NotInstalled) {
            OcrPackState.NotInstalled -> OcrPackStatus.NotAdded(
                store.missingModels(pack.id).sumOf { it.onnx.sizeBytes }.takeIf { policy.downloadsAllowed }
            )
            is OcrPackState.Downloading -> OcrPackStatus.Downloading(state.downloadedBytes, state.totalBytes)
            OcrPackState.Installed -> {
                val models = store.modelsFor(pack.id)
                val recognizer = models.filter { it.role == OcrModelRole.RECOGNIZER }.sumOf { it.onnx.sizeBytes }
                val finder = models.filter { it.role == OcrModelRole.DETECTOR }.sumOf { it.onnx.sizeBytes }
                val carries = pack.id == finderCarrier
                OcrPackStatus.Installed(recognizer + if (carries) finder else 0L, includesFinder = carries)
            }
            OcrPackState.Linked -> OcrPackStatus.Linked
            OcrPackState.NeedsReadding -> OcrPackStatus.NeedsReadding
            is OcrPackState.Failed -> OcrPackStatus.Failed
        }
        OcrPackRow(
            id = pack.id,
            languages = sorted.map { it.second },
            ownLanguages = sorted.filter { OcrLocaleKey.of(it.first) in ownKeys }.map { it.second }.toSet(),
            suggested = pack.id in suggested,
            status = status,
        )
    }
}

/** Checks one picked file against the catalog and links it when it is a known model. */
private fun verify(context: Context, store: OcrPackStore, uri: Uri): OcrVerifiedFile {
    val name = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.onFailure { printWarn(TAG, "Could not read a picked file name", mapOf("error" to it.toString())) }
        .getOrNull() ?: uri.lastPathSegment.orEmpty()
    val match = runCatching { store.link(uri) }
        .onFailure { printWarn(TAG, "Checking a picked model failed", mapOf("error" to it.toString())) }
        .getOrDefault(OcrFileMatch.Unknown)
    return when (match) {
        is OcrFileMatch.Accepted -> OcrVerifiedFile(name, OcrVerifiedResult.ADDED, store.packOfModel(match.modelId)?.id)
        is OcrFileMatch.Altered -> OcrVerifiedFile(name, OcrVerifiedResult.ALTERED, store.packOfModel(match.modelId)?.id)
        OcrFileMatch.Unknown -> OcrVerifiedFile(name, OcrVerifiedResult.UNKNOWN)
    }
}

/** The CLDR name of [tag] in [uiLocale]; plain Chinese is shown as Simplified next to Traditional. */
private fun displayName(tag: String, uiLocale: Locale): String {
    val shown = if (tag == "zh") "zh-Hans" else tag
    return ULocale.forLanguageTag(shown).getDisplayName(ULocale.forLocale(uiLocale))
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(uiLocale) else it.toString() }
}

private fun sortedNames(tags: List<String>, uiLocale: Locale): List<String> {
    val collator = Collator.getInstance(ULocale.forLocale(uiLocale))
    return tags.map { displayName(it, uiLocale) }.sortedWith(collator::compare)
}

private fun openPackList(context: Context) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PACK_LIST_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        printWarn(TAG, "No browser for the pack list", mapOf("error" to e.toString()))
        Toast.makeText(context, PACK_LIST_URL, Toast.LENGTH_LONG).show()
    }
}

private fun hasInternet(context: Context): Boolean =
    context.packageManager.checkPermission(Manifest.permission.INTERNET, context.packageName) == PackageManager.PERMISSION_GRANTED
