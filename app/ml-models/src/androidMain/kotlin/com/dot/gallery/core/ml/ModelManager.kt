/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ml

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import com.dot.gallery.core.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import com.dot.gallery.core.injection.Singleton
import com.dot.gallery.core.logging.printInfo
import com.dot.gallery.core.logging.printWarning
import com.dot.gallery.core.logging.printDebug
import com.dot.gallery.core.AppIdentity


fun installBundledModel(
    destinationFile: File,
    expectedChecksum: String?,
    openInput: () -> InputStream,
) {
    val tempFile = File(destinationFile.parentFile, "${destinationFile.name}.bundled.tmp")
    destinationFile.parentFile?.mkdirs()
    try {
        openInput().use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output, bufferSize = 65536)
            }
        }
        if (tempFile.length() == 0L || expectedChecksum != null && tempFile.sha256() != expectedChecksum) {
            throw IOException("Bundled model validation failed: ${destinationFile.name}")
        }
        Files.move(
            tempFile.toPath(),
            destinationFile.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
        )
    } finally {
        tempFile.delete()
    }
}

private fun File.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().use { stream ->
        val buffer = ByteArray(65536)
        var read: Int
        while (stream.read(buffer).also { read = it } != -1) {
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

@Singleton
class ModelManager @Inject constructor(
    private val context: Context
) {
    // Per-group observable state. Each ModelGroup tracks its own status, progress and errors so the
    // two feature sets (search/categories vs. subject cutout) install and uninstall independently.
    private class GroupFlows {
        val status = MutableStateFlow(ModelStatus.NOT_INSTALLED)
        val progress = MutableStateFlow(0f)
        val error = MutableStateFlow<String?>(null)
        val info = MutableStateFlow(DownloadInfo())
    }

    private val flows: Map<ModelGroup, GroupFlows> = ModelGroup.entries.associateWith { GroupFlows() }
    private fun flows(group: ModelGroup) = flows.getValue(group)

    fun status(group: ModelGroup): StateFlow<ModelStatus> = flows(group).status.asStateFlow()
    fun downloadProgress(group: ModelGroup): StateFlow<Float> = flows(group).progress.asStateFlow()
    fun errorMessage(group: ModelGroup): StateFlow<String?> = flows(group).error.asStateFlow()
    fun downloadInfo(group: ModelGroup): StateFlow<DownloadInfo> = flows(group).info.asStateFlow()

    private val mutex = Mutex()

    fun isReady(group: ModelGroup): Boolean = flows(group).status.value == ModelStatus.READY

    /** True only when every group's models are present. */
    val isReady: Boolean get() = ModelGroup.entries.all { isReady(it) }

    /**
     * Whether the app has INTERNET permission declared in its manifest.
     * When false, model *download* is not possible, but bundled models still work.
     */
    val hasInternetPermission: Boolean by lazy {
        context.packageManager.checkPermission(
            Manifest.permission.INTERNET,
            context.packageName
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Whether AI features (categories, smart search, etc.) should be shown in the UI.
     * True when models are bundled (withML builds) OR the app can download them (has INTERNET).
     * This decouples AI feature visibility from INTERNET permission so that
     * offline-withML builds (which strip INTERNET but bundle models) still expose AI features.
     */
    val areAiFeaturesAvailable: Boolean by lazy {
        AppIdentity.mlModelsBundled || hasInternetPermission
    }

    /**
     * Whether models can be (re)installed at all: either downloadable over the network
     * (INTERNET permission) or shipped inside the APK as bundled assets (withML builds).
     * Bundled installs are local copies, so no connectivity is needed for them.
     */
    val canInstallModels: Boolean
        get() = hasInternetPermission || AppIdentity.mlModelsBundled

    val modelsDir: File get() = File(context.filesDir, MODELS_DIR)

    fun getDestinationFile(name: String): File =
        File(modelsDir, "${ModelGroup.of(name).subDir}/$name")

    fun getTempFile(name: String): File =
        File(modelsDir, "${ModelGroup.of(name).subDir}/$name.tmp")

    /**
     * The search engine selected in Settings → AI Models. Read from DataStore at init and
     * cached here so synchronous hot paths (session setup, `isReady` checks) can resolve
     * the active [SearchModel] without suspending.
     */
    private val _activeSearchModel = MutableStateFlow(SearchModel.DEFAULT)
    val activeSearchModelFlow: StateFlow<SearchModel> = _activeSearchModel.asStateFlow()
    val activeSearchModel: SearchModel get() = _activeSearchModel.value

    /** Persist + apply a new search engine. Callers should schedule a Smart Scan to re-index. */
    suspend fun setActiveSearchModel(model: SearchModel) {
        if (model == _activeSearchModel.value) return
        Settings.SmartFeatures.setSearchModel(context, model)
        _activeSearchModel.value = model
        printInfo("ModelManager: active search model switched to ${model.name}")
    }

    /** Status of whichever search engine is currently selected. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun activeSearchModelStatus(): Flow<ModelStatus> =
        activeSearchModelFlow.flatMapLatest { status(it.group) }

    fun isReady(model: SearchModel): Boolean = isReady(model.group)
    fun status(model: SearchModel): StateFlow<ModelStatus> = status(model.group)
    fun processorRevision(model: SearchModel): String = processorRevision(model.group)
    fun getInstalledSize(model: SearchModel): Long = getInstalledSize(model.group)

    /**
     * Initialize models on app start.
     * For withML builds: copies bundled assets to filesDir if not already present, unless the
     * user explicitly deleted the group (its removal is persisted — issue #1229).
     * For noML builds: checks if models have been previously downloaded.
     */
    suspend fun initializeModels() = mutex.withLock {
        withContext(Dispatchers.IO) {
            val removedGroups = runCatching {
                Settings.SmartFeatures.removedModelGroups(context).first()
            }.getOrElse {
                printWarning("ModelManager: failed to read removed model groups: ${it.message}")
                emptySet()
            }
            _activeSearchModel.value = runCatching {
                Settings.SmartFeatures.searchModel(context).first()
            }.getOrElse {
                printWarning("ModelManager: failed to read search model pref: ${it.message}")
                SearchModel.DEFAULT
            }
            if (AppIdentity.mlModelsBundled) {
                ModelGroup.entries.forEach { group ->
                    if (group !in removedGroups && !checkModelsPresent(group)) {
                        flows(group).status.value = ModelStatus.COPYING
                    }
                }
            }
            ModelGroup.entries.forEach { group -> initializeGroup(group, removedGroups) }
        }
    }

    private fun initializeGroup(group: ModelGroup, removedGroups: Set<ModelGroup>) {
        if (group in removedGroups) {
            // The user deleted this group — finish any interrupted delete and stay uninstalled
            // instead of silently restoring the bundled copies (issue #1229).
            File(modelsDir, group.subDir).deleteRecursively()
            fileInfoCache.remove(group)
            modelValidationCache.remove(group)
            flows(group).status.value = ModelStatus.NOT_INSTALLED
            printInfo("ModelManager: ${group.name} models were removed by the user, staying uninstalled")
            return
        }
        if (checkModelsPresent(group)) {
            flows(group).status.value = ModelStatus.READY
            printInfo("ModelManager: ${group.name} models already present in filesDir")
            return
        }
        if (AppIdentity.mlModelsBundled) {
            copyBundledModels(group)
            if (checkModelsPresent(group)) {
                flows(group).status.value = ModelStatus.READY
            } else if (flows(group).status.value != ModelStatus.ERROR) {
                flows(group).status.value = ModelStatus.NOT_INSTALLED
                printInfo("ModelManager: ${group.name} bundled copy pass completed, but some files are missing (will download on-demand)")
            }
        } else {
            flows(group).status.value = ModelStatus.NOT_INSTALLED
            printInfo("ModelManager: ${group.name} models not installed (noML build)")
        }
    }

    /**
     * Check if all model files for [group] are present and non-empty.
     */
    fun checkModelsPresent(group: ModelGroup): Boolean {
        val signature = group.files.joinToString(":") { fileName ->
            val file = getDestinationFile(fileName)
            "$fileName:${file.length()}:${file.lastModified()}"
        }
        modelValidationCache[group]?.takeIf { it.first == signature }?.let { return it.second }
        val valid = group.files.all { fileName ->
            val file = getDestinationFile(fileName)
            val expected = EXPECTED_CHECKSUMS[fileName]
            file.exists() && file.length() > 0 && (expected == null || file.sha256() == expected)
        }
        modelValidationCache[group] = signature to valid
        return valid
    }

    fun processorRevision(group: ModelGroup): String = group.files.joinToString(":") { fileName ->
        EXPECTED_CHECKSUMS[fileName] ?: fileName
    }

    fun isModelFileValid(fileName: String): Boolean {
        val file = getDestinationFile(fileName)
        val expected = EXPECTED_CHECKSUMS[fileName]
        return file.exists() && file.length() > 0 && (expected == null || file.sha256() == expected)
    }

    /**
     * Get a model file by name.
     * @throws ModelsNotAvailableException if that file's group is not installed.
     */
    fun getModelFile(name: String): File {
        val group = ModelGroup.of(name)
        if (!isReady(group)) throw ModelsNotAvailableException()
        val file = getDestinationFile(name)
        if (!file.exists()) throw ModelsNotAvailableException("Model file not found: $name")
        return file
    }

    /**
     * Get installed model size in bytes for [group].
     */
    fun getInstalledSize(group: ModelGroup): Long {
        if (!checkModelsPresent(group)) return 0L
        return group.files.sumOf { getDestinationFile(it).length() }
    }

    /** Cache of computed per-file SHA-256 infos, so the (potentially expensive, e.g. ~130 MB
     * ArcFace) hashing runs only once per install rather than on every recomposition. */
    private val fileInfoCache = ConcurrentHashMap<ModelGroup, List<ModelFileInfo>>()
    private val modelValidationCache = ConcurrentHashMap<ModelGroup, Pair<String, Boolean>>()

    /**
     * Get detailed info (name, size, SHA-256) for each installed file in [group].
     * Runs on [Dispatchers.IO] and caches the result, as SHA-256 hashing large model files is
     * far too slow to do on the main thread / during composition.
     */
    suspend fun getFileInfos(group: ModelGroup): List<ModelFileInfo> = withContext(Dispatchers.IO) {
        if (!checkModelsPresent(group)) {
            fileInfoCache.remove(group)
            return@withContext emptyList()
        }
        fileInfoCache[group]?.let { return@withContext it }
        val infos = group.files.map { fileName ->
            val file = getDestinationFile(fileName)
            val hash = file.sha256()
            ModelFileInfo(
                name = fileName,
                size = file.length(),
                sha256 = hash,
                verified = EXPECTED_CHECKSUMS[fileName] == hash
            )
        }
        fileInfoCache[group] = infos
        infos
    }

    /**
     * Delete all downloaded/copied model files and persist the removal so the group is not
     * silently restored on the next launch (issue #1229). Re-install stays possible via
     * [installBundledModels] (withML) or a network download (INTERNET builds), so deletion
     * is only offered when [canInstallModels] is true.
     */
    suspend fun deleteModels(group: ModelGroup) {
        if (!canInstallModels) return
        mutex.withLock {
            withContext(Dispatchers.IO) {
                // Persist the removal intent first: if the delete below is interrupted, the
                // next launch sees the flag and finishes the cleanup instead of restoring.
                Settings.SmartFeatures.setModelGroupRemoved(context, group, true)
                val dir = File(modelsDir, group.subDir)
                if (dir.exists()) {
                    dir.deleteRecursively()
                    printInfo("ModelManager: ${group.name} models deleted")
                }
                fileInfoCache.remove(group)
                modelValidationCache.remove(group)
                flows(group).apply {
                    status.value = ModelStatus.NOT_INSTALLED
                    progress.value = 0f
                    error.value = null
                    info.value = DownloadInfo()
                }
            }
        }
    }

    /**
     * Re-install a group from the bundled APK assets (withML builds only). Clears the
     * persisted user-removal flag so future launches keep the group installed.
     * No network access is needed.
     */
    suspend fun installBundledModels(group: ModelGroup) {
        if (!AppIdentity.mlModelsBundled) return
        mutex.withLock {
            withContext(Dispatchers.IO) {
                Settings.SmartFeatures.setModelGroupRemoved(context, group, false)
                copyBundledModels(group)
                if (checkModelsPresent(group)) {
                    flows(group).status.value = ModelStatus.READY
                    flows(group).error.value = null
                } else if (flows(group).status.value != ModelStatus.ERROR) {
                    flows(group).status.value = ModelStatus.NOT_INSTALLED
                }
            }
        }
    }

    /**
     * Update the persisted user-removal flag for [group]. An explicit install request
     * clears it so the group stays installed across launches; [deleteModels] sets it.
     */
    suspend fun setRemoved(group: ModelGroup, removed: Boolean) {
        Settings.SmartFeatures.setModelGroupRemoved(context, group, removed)
    }

    /**
     * Called by ModelDownloadWorker to update download progress for [group].
     */
    fun updateDownloadProgress(group: ModelGroup, progress: Float) {
        // A queued or superseded download must not demote a group that is already
        // installed (e.g. restored from bundled assets while the work was pending).
        if (isReady(group)) return
        flows(group).progress.value = progress
        flows(group).status.value = ModelStatus.DOWNLOADING
    }

    fun updateDownloadInfo(group: ModelGroup, info: DownloadInfo) {
        flows(group).info.value = info
    }

    /**
     * Called by ModelDownloadWorker when a group's download completes successfully.
     */
    fun onDownloadComplete(group: ModelGroup) {
        if (checkModelsPresent(group)) {
            flows(group).status.value = ModelStatus.READY
            flows(group).progress.value = 100f
            flows(group).error.value = null
            printInfo("ModelManager: ${group.name} download complete, models ready")
        } else {
            flows(group).status.value = ModelStatus.ERROR
            flows(group).error.value = "Download completed but model files are missing"
            printWarning("ModelManager: ${group.name} download completed but validation failed")
        }
    }

    /**
     * Called by ModelDownloadWorker on failure for [group].
     */
    fun onDownloadFailed(group: ModelGroup, error: String) {
        // When the files are already complete (e.g. restored from bundled assets while a
        // download was queued), the failure belongs to the stale download attempt, not to
        // the installed models — keep the group READY instead of surfacing a bogus
        // Download/Install state.
        if (checkModelsPresent(group)) {
            flows(group).status.value = ModelStatus.READY
            flows(group).error.value = null
            printInfo("ModelManager: ${group.name} download failed but models are already installed")
            return
        }
        flows(group).status.value = ModelStatus.ERROR
        flows(group).error.value = error
        flows(group).progress.value = 0f
        printWarning("ModelManager: ${group.name} download failed: $error")
    }

    /**
     * Copy a group's bundled assets to filesDir (withML builds only).
     */
    private fun copyBundledModels(group: ModelGroup) {
        flows(group).status.value = ModelStatus.COPYING
        try {
            modelsDir.mkdirs()
            val assetManager = context.assets
            val totalFiles = group.files.size
            group.files.forEachIndexed { index, fileName ->
                val destFile = getDestinationFile(fileName)
                if (!isModelFileValid(fileName)) {
                    try {
                        printDebug("ModelManager: Copying asset $fileName to filesDir")
                        installBundledModel(destFile, EXPECTED_CHECKSUMS[fileName]) {
                            assetManager.open(fileName)
                        }
                    } catch (e: java.io.FileNotFoundException) {
                        printWarning("ModelManager: Bundled asset $fileName not found in assets, skipping copy.")
                    }
                }
                flows(group).progress.value = ((index + 1).toFloat() / totalFiles) * 100f
            }
            printInfo("ModelManager: ${group.name} bundled models copy pass completed")
        } catch (e: Exception) {
            flows(group).status.value = ModelStatus.ERROR
            flows(group).error.value = "Failed to copy bundled models: ${e.message}"
            printWarning("ModelManager: Failed to copy bundled models: ${e.message}")
        }
    }

    companion object {
        const val MODELS_DIR = "models"

        /** Flattened list of every model file across all groups. */
        val REQUIRED_FILES: List<String> get() = ModelGroup.entries.flatMap { it.files }

        const val BASE_DOWNLOAD_URL =
            "https://raw.githubusercontent.com/IacobIonut01/ReFra/refs/heads/main/ml-models/src/main/assets/"

        val EXPECTED_CHECKSUMS = mapOf(
            // CLIP ViT-B/32 INT8 — hosted in-repo under ml-models/src/main/assets
            "visual_quant.onnx" to "a2fbb26b5f6ab5c79dd9bf99ab2dbac4711abc88dc2e20afc02a0827aa3d59c2",
            "textual_quant.onnx" to "1ebb71a5ea1897823a829af8fc8168c5cfff761969bb62aee1fafdf5a2788aba",
            "vocab.json" to "e089ad92ba36837a0d31433e555c8f45fe601ab5c221d4f607ded32d9f7a4349",
            "merges.txt" to "9fd691f7c8039210e0fced15865466c65820d09b63988b0174bfe25de299051a",
            // EmbeddingGemma 2 — onnx-community/embeddinggemma-2-ONNX @ onnx/ + tokenizer
            "model_q4.onnx" to "f9eeba97acddf139b8ee2ddf04bc30dceafa88de93fadf74d7644e0d61a477a9",
            "model_q4.onnx_data" to "c3975f2d1ab7a1878ae31a7d7a9b7804a827aff3800b60dfceafce21cac3df49",
            "vision_encoder_q4.onnx" to "7ea284226d4938f0ad921ab091f1d80a9ca699aa802984ef5cd5eec4f4761d96",
            "vision_encoder_q4.onnx_data" to "0a9d6c927334f152a33dd90874f65d6ea5228999abe6a450d3f7813677fa704c",
            "tokenizer.json" to "4d777ef5bdc1aa36227abdfb77c3e49e7b9c892d16e1b6bda41c393504828be4",
            "tokenizer_config.json" to "17bd5d6e9364ca49a534e1502076593317c298d4a663623091ed45388f004874",
            "mobile_sam_image_encoder.onnx" to "580f5fb648ea1062c0aabc26217aed56921985f03f0cbbd852bba81d760cc749",
            "sam_mask_decoder_single.onnx" to "93915fc7c993ab9d59ab8c9ccd3bce37f7509c81ab4150a74abd4d2abbd8570d",
            "version-RFB-320.onnx" to "34cd7e60aeff28744c657de7a3dc64e872d506741de66987f3426f2b79f88017",
            "arcface.onnx" to "ffe014a45c9488506719d37fd578ece6661bb385535b36e8039975fa5d4683db"
        )
    }
}