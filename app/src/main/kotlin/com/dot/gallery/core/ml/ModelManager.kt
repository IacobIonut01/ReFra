/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ml

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import com.dot.gallery.BuildConfig
import com.dot.gallery.feature_node.presentation.util.printDebug
import com.dot.gallery.feature_node.presentation.util.printInfo
import com.dot.gallery.feature_node.presentation.util.printWarning
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

data class DownloadInfo(
    val speed: Long = 0L,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val currentFile: String = ""
)

data class ModelFileInfo(
    val name: String,
    val size: Long,
    val sha256: String,
    val verified: Boolean
)

enum class ModelStatus {
    NOT_INSTALLED,
    COPYING,
    DOWNLOADING,
    READY,
    ERROR
}

@Singleton
class ModelManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val _status = MutableStateFlow(ModelStatus.NOT_INSTALLED)
    val status: StateFlow<ModelStatus> = _status.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _downloadInfo = MutableStateFlow(DownloadInfo())
    val downloadInfo: StateFlow<DownloadInfo> = _downloadInfo.asStateFlow()

    private val mutex = Mutex()

    val isReady: Boolean get() = _status.value == ModelStatus.READY

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
     * nomaps-withML builds (which strip INTERNET but bundle models) still expose AI features.
     */
    val areAiFeaturesAvailable: Boolean by lazy {
        BuildConfig.ML_MODELS_BUNDLED || hasInternetPermission
    }

    val modelsDir: File get() = File(context.filesDir, MODELS_DIR)

    /**
     * Initialize models on app start.
     * For withML builds: copies bundled assets to filesDir if not already present.
     * For noML builds: checks if models have been previously downloaded.
     */
    suspend fun initializeModels() = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (checkModelsPresent()) {
                _status.value = ModelStatus.READY
                printInfo("ModelManager: Models already present in filesDir")
                return@withContext
            }

            if (BuildConfig.ML_MODELS_BUNDLED) {
                copyBundledModels()
            } else {
                _status.value = ModelStatus.NOT_INSTALLED
                printInfo("ModelManager: Models not installed (noML build)")
            }
        }
    }

    /**
     * Check if all required model files are present and non-empty.
     */
    fun checkModelsPresent(): Boolean {
        return REQUIRED_FILES.all { fileName ->
            val file = File(modelsDir, fileName)
            file.exists() && file.length() > 0
        }
    }

    /**
     * Get a model file by name.
     * @throws ModelsNotAvailableException if models are not installed.
     */
    fun getModelFile(name: String): File {
        if (!isReady) throw ModelsNotAvailableException()
        val file = File(modelsDir, name)
        if (!file.exists()) throw ModelsNotAvailableException("Model file not found: $name")
        return file
    }

    /**
     * Get total installed model size in bytes.
     */
    fun getInstalledSize(): Long {
        if (!checkModelsPresent()) return 0L
        return REQUIRED_FILES.sumOf { File(modelsDir, it).length() }
    }

    /**
     * Get detailed info (name, size, SHA-256) for each installed model file.
     */
    fun getFileInfos(): List<ModelFileInfo> {
        if (!checkModelsPresent()) return emptyList()
        return REQUIRED_FILES.map { fileName ->
            val file = File(modelsDir, fileName)
            val hash = file.sha256()
            ModelFileInfo(
                name = fileName,
                size = file.length(),
                sha256 = hash,
                verified = EXPECTED_CHECKSUMS[fileName] == hash
            )
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

    /**
     * Delete all downloaded/copied model files.
     */
    suspend fun deleteModels() = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (modelsDir.exists()) {
                modelsDir.deleteRecursively()
                printInfo("ModelManager: Models deleted")
            }
            _status.value = ModelStatus.NOT_INSTALLED
            _downloadProgress.value = 0f
            _errorMessage.value = null
            _downloadInfo.value = DownloadInfo()
        }
    }

    /**
     * Called by ModelDownloadWorker to update download progress.
     */
    fun updateDownloadProgress(progress: Float) {
        _downloadProgress.value = progress
        _status.value = ModelStatus.DOWNLOADING
    }

    fun updateDownloadInfo(info: DownloadInfo) {
        _downloadInfo.value = info
    }

    /**
     * Called by ModelDownloadWorker when download completes successfully.
     */
    fun onDownloadComplete() {
        if (checkModelsPresent()) {
            _status.value = ModelStatus.READY
            _downloadProgress.value = 100f
            _errorMessage.value = null
            printInfo("ModelManager: Download complete, models ready")
        } else {
            _status.value = ModelStatus.ERROR
            _errorMessage.value = "Download completed but model files are missing"
            printWarning("ModelManager: Download completed but validation failed")
        }
    }

    /**
     * Called by ModelDownloadWorker on failure.
     */
    fun onDownloadFailed(error: String) {
        _status.value = ModelStatus.ERROR
        _errorMessage.value = error
        _downloadProgress.value = 0f
        printWarning("ModelManager: Download failed: $error")
    }

    /**
     * Copy bundled assets to filesDir (withML builds only).
     */
    private suspend fun copyBundledModels() {
        _status.value = ModelStatus.COPYING
        try {
            modelsDir.mkdirs()
            val assetManager = context.assets
            val totalFiles = REQUIRED_FILES.size
            REQUIRED_FILES.forEachIndexed { index, fileName ->
                val destFile = File(modelsDir, fileName)
                if (!destFile.exists() || destFile.length() == 0L) {
                    printDebug("ModelManager: Copying asset $fileName to filesDir")
                    assetManager.open(fileName).use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output, bufferSize = 65536)
                        }
                    }
                }
                _downloadProgress.value = ((index + 1).toFloat() / totalFiles) * 100f
            }
            _status.value = ModelStatus.READY
            printInfo("ModelManager: Bundled models copied to filesDir")
        } catch (e: Exception) {
            _status.value = ModelStatus.ERROR
            _errorMessage.value = "Failed to copy bundled models: ${e.message}"
            printWarning("ModelManager: Failed to copy bundled models: ${e.message}")
        }
    }

    companion object {
        const val MODELS_DIR = "models/clip"

        val REQUIRED_FILES = listOf(
            "visual_quant.onnx",
            "textual_quant.onnx",
            "vocab.json",
            "merges.txt"
        )

        const val BASE_DOWNLOAD_URL =
            "https://raw.githubusercontent.com/IacobIonut01/ReFra/refs/heads/main/ml-models/src/main/assets/"

        val EXPECTED_CHECKSUMS = mapOf(
            "visual_quant.onnx" to "a2fbb26b5f6ab5c79dd9bf99ab2dbac4711abc88dc2e20afc02a0827aa3d59c2",
            "textual_quant.onnx" to "1ebb71a5ea1897823a829af8fc8168c5cfff761969bb62aee1fafdf5a2788aba",
            "vocab.json" to "e089ad92ba36837a0d31433e555c8f45fe601ab5c221d4f607ded32d9f7a4349",
            "merges.txt" to "9fd691f7c8039210e0fced15865466c65820d09b63988b0174bfe25de299051a"
        )
    }
}

class ModelsNotAvailableException(
    message: String = "ML models are not installed. Download them from Settings > Smart Features."
) : RuntimeException(message)
