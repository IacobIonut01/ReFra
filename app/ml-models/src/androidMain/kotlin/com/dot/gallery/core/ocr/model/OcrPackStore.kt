/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr.model

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.dot.gallery.core.logging.printInfo
import com.dot.gallery.core.logging.printWarn
import com.dot.gallery.core.ocr.catalog.OcrCatalogModel
import com.dot.gallery.core.ocr.catalog.OcrCatalogPack
import com.dot.gallery.core.ocr.catalog.OcrCatalogParser
import com.dot.gallery.core.ocr.catalog.OcrDependencyResolver
import com.dot.gallery.core.ocr.catalog.OcrModelCatalog
import com.dot.gallery.core.ocr.catalog.OcrModelRole
import com.dot.gallery.core.ocr.engine.OcrModelHandle
import com.dot.gallery.core.ocr.engine.OcrModelSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.io.FileInputStream
import java.nio.channels.FileChannel
import java.util.concurrent.ConcurrentHashMap

/**
 * The installed and linked OCR models.
 *
 * Downloaded models live in `files/models/ocr/<model>/<sha256>/inference.onnx`. Picked documents are
 * read in place through a persisted read grant and are re-checked on every use: the grant must still
 * exist and the size and modification time must match what was verified, or the file is hashed again.
 * A model that no longer verifies marks its packs "needs re-adding" and is never loaded.
 */
class OcrPackStore(context: Context) : OcrModelSource {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val root = File(appContext.filesDir, "models/ocr")

    override val catalog: OcrModelCatalog by lazy { OcrCatalogParser.parse(resourceText(CATALOG_RESOURCE)) }
    private val dependencies by lazy { OcrDependencyResolver(catalog) }
    val matcher by lazy { OcrFileMatcher(catalog) }

    private val links = ConcurrentHashMap<String, OcrExternalLink>()
    private val brokenLinks = ConcurrentHashMap.newKeySet<String>()
    private val verifiedFiles = ConcurrentHashMap<String, String>()
    private val dictionaries = ConcurrentHashMap<String, List<String>>()
    private val progress = ConcurrentHashMap<String, OcrPackState>()

    private val _states = MutableStateFlow<Map<String, OcrPackState>>(emptyMap())
    val states: StateFlow<Map<String, OcrPackState>> = _states.asStateFlow()

    /** Loads the persisted links (`ocrExternalModelUris`) and recomputes every pack state. */
    fun setLinks(encoded: Set<String>) {
        // The viewer and the indexer both call this while the engine may be opening a linked model:
        // never empty the map first, or a concurrent open() finds the model missing.
        val next = encoded.mapNotNull(OcrExternalLink::decode).associateBy(OcrExternalLink::modelId)
        links.putAll(next)
        links.keys.retainAll(next.keys)
        refresh()
    }

    fun linksEncoded(): Set<String> = links.values.mapTo(linkedSetOf()) { it.encode() }

    fun refresh() {
        brokenLinks.clear()
        _states.value = catalog.packs.associate { it.id to computeState(it) }
    }

    fun readyPackIds(): Set<String> = _states.value.filterValues { it.isReady }.keys

    fun setProgress(packId: String, state: OcrPackState?) {
        if (state == null) progress.remove(packId) else progress[packId] = state
        _states.update { current -> current + (packId to (state ?: catalog.pack(packId)?.let(::computeState) ?: OcrPackState.NotInstalled)) }
    }

    override fun areReady(packIds: Collection<String>): Boolean =
        packIds.isNotEmpty() && packIds.all { _states.value[it]?.isReady == true }

    override fun packsFor(packIds: Collection<String>): List<OcrCatalogPack> =
        packIds.sorted().mapNotNull(catalog::pack).sortedBy { it.suggestPriority }

    override fun detectorFor(packIds: Collection<String>): OcrCatalogModel =
        dependencies.resolvePacks(packIds).first { it.role == OcrModelRole.DETECTOR }

    override fun recognizerFor(packId: String): OcrCatalogModel =
        dependencies.resolvePack(packId).first { it.role == OcrModelRole.RECOGNIZER }

    override fun revision(packIds: Collection<String>): String =
        "ppocrv5:" + packIds.sorted().joinToString(",") { pack -> pack + ":" + recognizerFor(pack).onnx.sha256.take(12) }

    override fun open(model: OcrCatalogModel): OcrModelHandle {
        installedFile(model)?.let { return OcrModelHandle.Path(it.absolutePath) }
        val link = links[model.id] ?: error("Model ${model.id} is not installed")
        check(linkStillValid(link)) { "Linked model ${model.id} no longer verifies" }
        val descriptor = resolver.openFileDescriptor(Uri.parse(link.uri), "r") ?: error("Cannot open ${model.id}")
        return try {
            val stream = FileInputStream(descriptor.fileDescriptor)
            val buffer = stream.channel.map(FileChannel.MapMode.READ_ONLY, 0, model.onnx.sizeBytes)
            OcrModelHandle.Buffer(buffer) { stream.close(); descriptor.close() }
        } catch (e: Exception) {
            // A pipe cannot be mapped: read it into memory, bounded by the catalog size.
            printWarn(TAG, "Mapping a linked model failed; reading it instead", mapOf("model" to model.id, "error" to e.toString()))
            descriptor.close()
            val bytes = resolver.openInputStream(Uri.parse(link.uri))!!.use { it.readNBytes(model.onnx.sizeBytes.toInt()) }
            val direct = java.nio.ByteBuffer.allocateDirect(bytes.size).put(bytes).also { it.flip() }
            OcrModelHandle.Buffer(direct) {}
        }
    }

    override fun dictionary(model: OcrCatalogModel): List<String> = dictionaries.getOrPut(model.id) {
        val artifact = model.dictionary ?: return@getOrPut emptyList()
        val bytes = resourceBytes(artifact.resource!!)
        check(bytes.size.toLong() == artifact.sizeBytes && OcrFileMatcher.sha256(bytes.inputStream()) == artifact.sha256) {
            "Bundled dictionary of ${model.id} does not verify"
        }
        OcrDictionaryReader.read(bytes.decodeToString())
    }

    /** Destination of a downloaded model; written by the install worker through [tempFile] then moved. */
    fun destination(model: OcrCatalogModel): File = File(root, "${model.id}/${model.onnx.sha256}/${model.onnx.fileName}")

    fun tempFile(model: OcrCatalogModel): File = File(destination(model).path + ".part")

    /** Models still missing for [packId] (already installed or linked ones are skipped). */
    fun missingModels(packId: String): List<OcrCatalogModel> =
        dependencies.resolvePack(packId).filter { installedFile(it) == null && links[it.id]?.let(::linkStillValid) != true }

    /** The models [packId] needs: the shared detector and its recognizer. */
    fun modelsFor(packId: String): List<OcrCatalogModel> = dependencies.resolvePack(packId)

    /** Whether [model] is downloaded into app storage and verifies (linked files are not counted). */
    fun isStored(model: OcrCatalogModel): Boolean = installedFile(model) != null

    /** Bytes of verified models in app storage. Linked files are read in place and cost nothing here. */
    fun storedBytes(): Long = catalog.models.filter(::isStored).sumOf { it.onnx.sizeBytes }

    /** The pack whose recognizer is [modelId], or null for the shared detector and unknown ids. */
    fun packOfModel(modelId: String): OcrCatalogPack? = catalog.packs.firstOrNull { modelId in it.rootModelIds }

    /** Checks a picked document and, when it is a catalog model, keeps a persisted read grant on it. */
    fun link(uri: Uri): OcrFileMatch {
        val (size, modified) = documentStat(uri) ?: return OcrFileMatch.Unknown
        val match = matcher.match(size) { resolver.openInputStream(uri) ?: error("Cannot read $uri") }
        if (match is OcrFileMatch.Accepted) {
            runCatching { resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                .onFailure { printWarn(TAG, "No persistable grant for a picked model", mapOf("error" to it.toString())) }
            links.put(match.modelId, OcrExternalLink(match.modelId, size, modified, uri.toString()))?.let { old ->
                if (old.uri != uri.toString()) release(old)
            }
            printInfo(TAG, "Linked model ${match.modelId}")
        }
        refresh()
        return match
    }

    /** Deletes a pack's models that no other ready pack needs, and releases their grants. */
    fun delete(packId: String, otherPacks: Collection<String>) {
        dependencies.modelsToDelete(packId, otherPacks).forEach { model ->
            File(root, model.id).deleteRecursively()
            links.remove(model.id)?.let(::release)
        }
        verifiedFiles.clear()
        refresh()
    }

    private fun computeState(pack: OcrCatalogPack): OcrPackState {
        progress[pack.id]?.let { return it }
        var linked = false
        for (model in dependencies.resolvePack(pack.id)) {
            if (installedFile(model) != null) continue
            val link = links[model.id] ?: return OcrPackState.NotInstalled
            if (!linkStillValid(link)) return OcrPackState.NeedsReadding
            linked = true
        }
        return if (linked) OcrPackState.Linked else OcrPackState.Installed
    }

    private fun installedFile(model: OcrCatalogModel): File? {
        val file = destination(model)
        if (!file.isFile || file.length() != model.onnx.sizeBytes) return null
        val key = "${file.path}:${file.length()}:${file.lastModified()}"
        val sha = verifiedFiles.getOrPut(key) { file.inputStream().use(OcrFileMatcher::sha256) }
        return file.takeIf { sha == model.onnx.sha256 }
    }

    private fun linkStillValid(link: OcrExternalLink): Boolean {
        if (link.modelId in brokenLinks) return false
        val uri = Uri.parse(link.uri)
        val granted = resolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
        val ok = granted && runCatching {
            val (size, modified) = documentStat(uri) ?: return@runCatching false
            val model = catalog.model(link.modelId) ?: return@runCatching false
            when {
                size != model.onnx.sizeBytes -> false
                size == link.size && modified == link.lastModified -> true
                else -> (matcher.match(size) { resolver.openInputStream(uri)!! } == OcrFileMatch.Accepted(model.id))
                    .also { if (it) links[model.id] = link.copy(size = size, lastModified = modified) }
            }
        }.getOrElse {
            printWarn(TAG, "Linked model is no longer readable", mapOf("model" to link.modelId, "error" to it.toString()))
            false
        }
        if (!ok) brokenLinks += link.modelId
        return ok
    }

    private fun documentStat(uri: Uri): Pair<Long, Long>? = resolver.query(
        uri, arrayOf(OpenableColumns.SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED), null, null, null,
    )?.use { cursor ->
        if (!cursor.moveToFirst()) return null
        val size = cursor.getLong(0)
        val modified = if (cursor.isNull(1)) 0L else cursor.getLong(1)
        size to modified
    }

    private fun release(link: OcrExternalLink) {
        runCatching { resolver.releasePersistableUriPermission(Uri.parse(link.uri), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            .onFailure { printWarn(TAG, "Could not release a model grant", mapOf("error" to it.toString())) }
    }

    private fun resourceBytes(path: String): ByteArray =
        OcrPackStore::class.java.classLoader!!.getResourceAsStream(path)?.use { it.readBytes() }
            ?: error("Missing OCR resource $path")

    private fun resourceText(path: String): String = resourceBytes(path).decodeToString()

    companion object {
        private const val TAG = "ml.ocr"
        const val CATALOG_RESOURCE = "ocr/model-catalog-v1.json"
    }
}
