package com.dot.gallery.feature.classifier.ui.search

/**
 * A remote search index the app can delegate queries to — implemented by the
 * ReFra Handover client (com.dot.gallery.handover). Sits next to [SearchHelper]
 * (on-device inference): callers try this first when configured, fall back to
 * local inference otherwise.
 *
 * All methods return null when remote search is disabled, unconfigured or
 * unreachable — an empty list means "reached the server, no hits".
 */
interface RemoteSearchProvider {

    /** Text → ranked (media id string, similarity score). */
    suspend fun search(query: String, k: Int = REMOTE_RESULT_LIMIT): List<Pair<String, Float>>?

    /** "More like this" — [itemId] must already exist in the server index. */
    suspend fun similarTo(itemId: String, k: Int = REMOTE_RESULT_LIMIT): List<Pair<String, Float>>?

    /**
     * Free-form vector query for visual-search targets that were never indexed
     * (e.g. a picked image). The vector must match the server engine's dim.
     */
    suspend fun searchVector(embedding: FloatArray, k: Int = REMOTE_RESULT_LIMIT): List<Pair<String, Float>>?

    /** One-off text embed for the server engine (rare; search() usually suffices). */
    suspend fun embedText(text: String): FloatArray?

    companion object {
        const val REMOTE_RESULT_LIMIT = 200
    }
}
