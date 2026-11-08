package com.dot.gallery.feature.classifier.ui.search

import android.graphics.Bitmap
import com.dot.gallery.app.presentation.search.helpers.SearchVisionHelper.SearchSessions


interface SearchHelper {

    val isAvailable: Boolean

    fun sortByCosineDistance(
        searchEmbedding: FloatArray,
        imageEmbeddingsList: List<FloatArray>,
        imageIdxList: List<Long>
    ): List<Pair<Long, Float>>

    suspend fun getTextEmbedding(session: SearchSessions, text: String): FloatArray

    fun setupTextSession(): SearchSessions

    fun setupVisionSession(): SearchSessions

    suspend fun getImageEmbedding(session: SearchSessions, bitmap: Bitmap): FloatArray

    suspend fun getVideoEmbedding(session: SearchSessions, frames: List<Bitmap>): FloatArray
}
