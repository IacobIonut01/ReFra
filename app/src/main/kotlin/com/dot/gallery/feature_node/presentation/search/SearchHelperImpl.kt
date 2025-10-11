package com.dot.gallery.feature_node.presentation.search

import ai.onnxruntime.OrtSession
import android.content.Context
import com.dot.gallery.feature_node.presentation.search.helpers.SearchVisionHelper
import com.dot.gallery.feature_node.presentation.search.util.dot
import com.dot.gallery.feature_node.presentation.util.printDebug
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SearchHelperImpl @Inject constructor(
    @param:ApplicationContext
    private val appContext: Context,
) : SearchHelper {

    private val helper by lazy { SearchVisionHelper(appContext) }

    override fun sortByCosineDistance(
        searchEmbedding: FloatArray,
        imageEmbeddingsList: List<FloatArray>,
        imageIdxList: List<Long>
    ): List<Pair<Long, Float>> {
        val distances = LinkedHashMap<Long, Float>()
        for (i in imageEmbeddingsList.indices) {
            val dist = searchEmbedding.dot(imageEmbeddingsList[i])
            distances[imageIdxList[i]] = dist
        }
        return distances.toList()
            .filter { it.second >= SearchVisionHelper.threshold }
            .sortedByDescending { (k, v) -> v }
            .map {
                printDebug(it)
                it
            }
    }

    override fun setupTextSession(): OrtSession = helper.setupTextSession()

    override suspend fun getTextEmbedding(session: OrtSession, text: String): FloatArray = withContext(Dispatchers.IO) {
        helper.getTextEmbedding(session, text)
    }

}