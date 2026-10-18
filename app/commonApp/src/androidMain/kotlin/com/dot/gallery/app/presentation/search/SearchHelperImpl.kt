package com.dot.gallery.app.presentation.search

import com.dot.gallery.domain.util.dot

import android.graphics.Bitmap
import com.dot.gallery.feature.classifier.ui.search.SearchHelper
import com.dot.gallery.app.presentation.search.helpers.SearchVisionHelper
import com.dot.gallery.app.presentation.search.helpers.SearchVisionHelper.SearchSessions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import com.dot.gallery.core.ml.ModelManager



class SearchHelperImpl @Inject constructor(
    private val modelManager: ModelManager,
) : SearchHelper {

    override val isAvailable: Boolean get() = modelManager.isReady(modelManager.activeSearchModel)

    private val helper by lazy { SearchVisionHelper(modelManager) }

    override fun sortByCosineDistance(
        searchEmbedding: FloatArray,
        imageEmbeddingsList: List<FloatArray>,
        imageIdxList: List<Long>
    ): List<Pair<Long, Float>> {
        val distances = LinkedHashMap<Long, Float>()
        for (i in imageEmbeddingsList.indices) {
            // Rows from a previous embedding model generation have a different
            // dimension — skip them instead of crashing the dot product (they are
            // re-embedded on the next Smart Scan pass).
            if (imageEmbeddingsList[i].size != searchEmbedding.size) continue
            val dist = searchEmbedding.dot(imageEmbeddingsList[i])
            distances[imageIdxList[i]] = dist
        }
        return distances.toList()
            .filter { it.second >= modelManager.activeSearchModel.searchThreshold }
            .sortedByDescending { (k, v) -> v }
    }

    // Interactive callers go through shared sessions: creating an OrtSession for the
    // EG2 backbone (174 MB external data + full graph optimization) takes seconds, so
    // a fresh session per keystroke made search unusably slow. Shared sessions are
    // owned by the helper — close() on them is a no-op.
    override fun setupTextSession(): SearchSessions = helper.sharedTextSession()

    override suspend fun getTextEmbedding(session: SearchSessions, text: String): FloatArray = withContext(Dispatchers.IO) {
        helper.getTextEmbedding(session, text)
    }

    override fun setupVisionSession(): SearchSessions = helper.sharedVisionSession()

    override suspend fun getImageEmbedding(session: SearchSessions, bitmap: Bitmap): FloatArray = withContext(Dispatchers.IO) {
        helper.getImageEmbedding(session, bitmap)
    }

    override suspend fun getVideoEmbedding(session: SearchSessions, frames: List<Bitmap>): FloatArray = withContext(Dispatchers.IO) {
        helper.getVideoEmbedding(session, frames)
    }

}
