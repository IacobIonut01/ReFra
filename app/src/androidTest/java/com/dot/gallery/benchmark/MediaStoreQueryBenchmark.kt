package com.dot.gallery.benchmark

import android.content.ContentResolver
import android.provider.MediaStore
import android.util.Log
import androidx.core.os.bundleOf
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dot.gallery.benchmark.BenchmarkUtils.TAG
import com.dot.gallery.benchmark.BenchmarkUtils.benchmark
import com.dot.gallery.benchmark.BenchmarkUtils.logComparison
import com.dot.gallery.benchmark.BenchmarkUtils.logInfo
import com.dot.gallery.benchmark.BenchmarkUtils.logSection
import com.dot.gallery.feature_node.data.data_source.mediastore.MediaQuery
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Benchmarks that run real MediaStore queries on the device.
 *
 * These tests require media access permissions. Make sure the Gallery app
 * has been opened at least once and permissions have been granted before
 * running these benchmarks.
 *
 * Run with:
 *   ./gradlew :app:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.dot.gallery.benchmark.MediaStoreQueryBenchmark
 *
 * View results with:
 *   adb logcat -s GalleryBenchmark
 */
@RunWith(AndroidJUnit4::class)
class MediaStoreQueryBenchmark {

    private lateinit var contentResolver: ContentResolver
    private val imageOrVideoSelection =
        "(${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}" +
                " OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO})"

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        contentResolver = context.contentResolver

        // Verify we can access media — skip gracefully if no permission or no media
        val cursor = contentResolver.query(
            MediaQuery.MediaStoreFileUri,
            arrayOf(MediaStore.Files.FileColumns._ID),
            bundleOf(
                ContentResolver.QUERY_ARG_SQL_SELECTION to imageOrVideoSelection,
                ContentResolver.QUERY_ARG_SQL_LIMIT to "1"
            ),
            null
        )
        val hasMedia = cursor?.use { it.count > 0 } ?: false
        Assume.assumeTrue(
            "No media accessible on device — grant permissions and ensure media exists",
            hasMedia
        )
    }

    /**
     * Issue #1: albumsTimelinesMediaFlow creates one MediaStore query per album.
     *
     * This benchmark compares:
     * - CURRENT: N individual queries (one per album bucket ID)
     * - OPTIMIZED: 1 single query, then group by albumID in memory
     */
    @Test
    fun benchmark_singleQuery_vs_perAlbumQueries() {
        logSection("Issue #1: Single Query vs Per-Album Queries")

        // Discover album IDs and total count with a single pass
        val albumIds = mutableSetOf<Long>()
        var totalMedia = 0

        contentResolver.query(
            MediaQuery.MediaStoreFileUri,
            MediaQuery.AlbumsProjection,
            bundleOf(
                ContentResolver.QUERY_ARG_SQL_SELECTION to imageOrVideoSelection,
                ContentResolver.QUERY_ARG_SQL_SORT_ORDER to "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
            ),
            null
        )?.use { cursor ->
            totalMedia = cursor.count
            val bucketIdx = cursor.getColumnIndex(MediaStore.Files.FileColumns.BUCKET_ID)
            while (cursor.moveToNext()) {
                albumIds.add(cursor.getLong(bucketIdx))
            }
        } ?: run {
            logInfo("Query failed"); return
        }

        logInfo("Device: $totalMedia media items across ${albumIds.size} albums")

        // --- OPTIMIZED: single query, group in memory ---
        val singleQueryResult = benchmark(
            name = "OPTIMIZED — single query + in-memory grouping",
            warmup = 2,
            iterations = 5
        ) {
            val grouped = HashMap<Long, MutableList<Long>>()
            contentResolver.query(
                MediaQuery.MediaStoreFileUri,
                MediaQuery.MediaProjection,
                bundleOf(
                    ContentResolver.QUERY_ARG_SQL_SELECTION to imageOrVideoSelection,
                    ContentResolver.QUERY_ARG_SQL_SORT_ORDER to "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
                ),
                null
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(MediaStore.Files.FileColumns._ID)
                val bucketIdx = cursor.getColumnIndex(MediaStore.Files.FileColumns.BUCKET_ID)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIdx)
                    val bucket = cursor.getLong(bucketIdx)
                    grouped.getOrPut(bucket) { mutableListOf() }.add(id)
                }
            }
        }

        // --- CURRENT: N individual queries ---
        val albumIdList = albumIds.toList()
        val perAlbumResult = benchmark(
            name = "CURRENT  — ${albumIds.size} individual album queries",
            warmup = 2,
            iterations = 5
        ) {
            for (albumId in albumIdList) {
                contentResolver.query(
                    MediaQuery.MediaStoreFileUri,
                    MediaQuery.MediaProjection,
                    bundleOf(
                        ContentResolver.QUERY_ARG_SQL_SELECTION to
                                "($imageOrVideoSelection AND ${MediaStore.Files.FileColumns.BUCKET_ID}=?)",
                        ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS to arrayOf(albumId.toString()),
                        ContentResolver.QUERY_ARG_SQL_SORT_ORDER to "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
                    ),
                    null
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        cursor.getLong(0) // simulate row read
                    }
                }
            }
        }

        logComparison("Single query vs N queries", perAlbumResult, singleQueryResult)
    }

    /**
     * Issue #4: querySteppedFlow fires LIMIT 250 + full query sequentially.
     *
     * This benchmark compares:
     * - CURRENT: two queries (LIMIT 250 then full)
     * - OPTIMIZED: one full query
     */
    @Test
    fun benchmark_steppedQuery_vs_singleQuery() {
        logSection("Issue #4: Stepped Query (250 + full) vs Single Query")

        val queryArgs = bundleOf(
            ContentResolver.QUERY_ARG_SQL_SELECTION to imageOrVideoSelection,
            ContentResolver.QUERY_ARG_SQL_SORT_ORDER to "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
        )

        val limitArgs = bundleOf(
            ContentResolver.QUERY_ARG_SQL_SELECTION to imageOrVideoSelection,
            ContentResolver.QUERY_ARG_SQL_SORT_ORDER to "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC",
            ContentResolver.QUERY_ARG_SQL_LIMIT to "250"
        )

        // --- CURRENT: stepped (batch + full) ---
        val stepped = benchmark(
            name = "CURRENT  — stepped (LIMIT 250 + full)",
            warmup = 2,
            iterations = 5
        ) {
            // Batch query
            contentResolver.query(
                MediaQuery.MediaStoreFileUri, MediaQuery.MediaProjection, limitArgs, null
            )?.use { c -> while (c.moveToNext()) { c.getLong(0) } }
            // Full query
            contentResolver.query(
                MediaQuery.MediaStoreFileUri, MediaQuery.MediaProjection, queryArgs, null
            )?.use { c -> while (c.moveToNext()) { c.getLong(0) } }
        }

        // --- OPTIMIZED: single full query ---
        val single = benchmark(
            name = "OPTIMIZED — single full query",
            warmup = 2,
            iterations = 5
        ) {
            contentResolver.query(
                MediaQuery.MediaStoreFileUri, MediaQuery.MediaProjection, queryArgs, null
            )?.use { c -> while (c.moveToNext()) { c.getLong(0) } }
        }

        logComparison("Single vs Stepped", stepped, single)
    }

    /**
     * Bonus: Measures raw query time and row-parsing time for the full timeline.
     * Useful as a baseline to know how fast MediaStore is on this device.
     */
    @Test
    fun benchmark_rawTimelineQueryBaseline() {
        logSection("Baseline: Raw Timeline Query Performance")

        val queryArgs = bundleOf(
            ContentResolver.QUERY_ARG_SQL_SELECTION to imageOrVideoSelection,
            ContentResolver.QUERY_ARG_SQL_SORT_ORDER to "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
        )

        // Query only (no row iteration)
        val queryOnly = benchmark(
            name = "Query only (no iteration)",
            warmup = 2,
            iterations = 5
        ) {
            contentResolver.query(
                MediaQuery.MediaStoreFileUri, MediaQuery.MediaProjection, queryArgs, null
            )?.close()
        }

        // Query + full row parsing
        val queryAndParse = benchmark(
            name = "Query + parse all rows",
            warmup = 2,
            iterations = 5
        ) {
            contentResolver.query(
                MediaQuery.MediaStoreFileUri, MediaQuery.MediaProjection, queryArgs, null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    cursor.getLong(0)   // _ID
                    cursor.getString(1) // DATA
                    cursor.getString(2) // RELATIVE_PATH
                    cursor.getString(3) // DISPLAY_NAME
                    cursor.getLong(4)    // BUCKET_ID
                    cursor.getString(5) // BUCKET_DISPLAY_NAME
                    cursor.getLong(7)    // DATE_MODIFIED
                    cursor.getLong(9)    // SIZE
                    cursor.getString(10) // MIME_TYPE
                    cursor.getInt(11)   // IS_FAVORITE
                    cursor.getInt(12)   // IS_TRASHED
                }
            }
        }

        val parseTimeMs = queryAndParse.avgTimeMs - queryOnly.avgTimeMs
        logInfo("Estimated parse overhead: ${String.format("%.3f", parseTimeMs)}ms")
    }
}
