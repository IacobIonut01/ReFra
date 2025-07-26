package com.dot.gallery.feature_node.presentation.search

import ai.onnxruntime.OrtSession

interface SearchHelper {

    fun sortByCosineDistance(
        searchEmbedding: FloatArray,
        imageEmbeddingsList: List<FloatArray>,
        imageIdxList: List<Long>
    ): List<Pair<Long, Float>>

    suspend fun getTextEmbedding(session: OrtSession, text: String): FloatArray

    fun setupTextSession(): OrtSession
}