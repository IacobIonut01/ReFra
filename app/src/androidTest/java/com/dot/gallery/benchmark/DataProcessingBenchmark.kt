package com.dot.gallery.benchmark

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dot.gallery.benchmark.BenchmarkUtils.benchmark
import com.dot.gallery.benchmark.BenchmarkUtils.benchmarkSuspend
import com.dot.gallery.benchmark.BenchmarkUtils.logComparison
import com.dot.gallery.benchmark.BenchmarkUtils.logInfo
import com.dot.gallery.benchmark.BenchmarkUtils.logSection
import com.dot.gallery.benchmark.BenchmarkUtils.logSummaryHeader
import com.dot.gallery.core.Constants
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.presentation.util.mapMediaToItem
import kotlinx.coroutines.runBlocking
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import kotlin.random.Random

/**
 * Pure algorithm benchmarks using synthetic data at realistic sizes.
 *
 * Adjust [MEDIA_COUNT] to approximate the number of photos on your device.
 *
 * Run with:
 *   ./gradlew :app:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.dot.gallery.benchmark.DataProcessingBenchmark
 *
 * View results with:
 *   adb logcat -s GalleryBenchmark
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class DataProcessingBenchmark {

    companion object {
        /** Adjust these to match your device's library size */
        const val MEDIA_COUNT = 15_000
        const val ALBUM_COUNT = 60
        const val METADATA_COUNT = 10_000
        const val LOCATION_GROUP_COUNT = 150

        private val SD_CARD_REGEX_CACHED = ".*[0-9a-f]{4}-[0-9a-f]{4}".toRegex()
    }

    // ==========================================================================
    // Helpers
    // ==========================================================================

    private fun generateMedia(
        count: Int,
        albumCount: Int = ALBUM_COUNT
    ): List<Media.UriMedia> {
        val now = System.currentTimeMillis() / 1000
        return List(count) { i ->
            val albumId = (i % albumCount).toLong()
            Media.UriMedia(
                id = i.toLong(),
                label = "IMG_${1000 + i}.jpg",
                uri = Uri.parse("content://media/external/images/media/$i"),
                path = "/storage/emulated/0/DCIM/Camera/IMG_${1000 + i}.jpg",
                relativePath = "DCIM/Camera/",
                albumID = albumId,
                albumLabel = "Album_$albumId",
                timestamp = now - (i * 86400L / count), // spread across ~1 day per unit
                takenTimestamp = (now - (i * 86400L / count)) * 1000,
                expiryTimestamp = null,
                fullDate = "2024-01-01",
                duration = null,
                favorite = 0,
                trashed = 0,
                size = 4_000_000L,
                mimeType = "image/jpeg"
            )
        }
    }

    // ==========================================================================
    // 1. mapMediaToItem — the hot path for every timeline/album render
    // ==========================================================================

    @Test
    fun test1_mapMediaToItem_scaling() = runBlocking {
        logSection("mapMediaToItem — Scaling with media count")

        for (size in listOf(1_000, 5_000, 10_000, 20_000)) {
            val media = generateMedia(size)
            benchmarkSuspend(
                name = "mapMediaToItem (n=$size)",
                warmup = 2,
                iterations = 5
            ) {
                mapMediaToItem(
                    data = media,
                    error = "",
                    albumId = -1L,
                    groupByMonth = false,
                    defaultDateFormat = Constants.DEFAULT_DATE_FORMAT,
                    extendedDateFormat = Constants.EXTENDED_DATE_FORMAT,
                    weeklyDateFormat = Constants.WEEKLY_DATE_FORMAT
                )
            }
        }
    }

    /**
     * Issue #9-10: mapMediaToItem always builds both mappedData and
     * mappedDataWithMonthly (nearly identical copies).
     * Skipping monthly headers when they aren't needed halves the work.
     */
    @Test
    fun test2_mapMediaToItem_withMonthlyHeaders_vs_without() = runBlocking {
        logSection("Issue #9-10: mapMediaToItem with vs without monthly headers")

        val media = generateMedia(MEDIA_COUNT)

        val withMonthly = benchmarkSuspend(
            name = "CURRENT  — withMonthHeader=true (n=$MEDIA_COUNT)",
            warmup = 2,
            iterations = 5
        ) {
            mapMediaToItem(
                data = media,
                error = "",
                albumId = -1L,
                groupByMonth = false,
                withMonthHeader = true,
                defaultDateFormat = Constants.DEFAULT_DATE_FORMAT,
                extendedDateFormat = Constants.EXTENDED_DATE_FORMAT,
                weeklyDateFormat = Constants.WEEKLY_DATE_FORMAT
            )
        }

        val withoutMonthly = benchmarkSuspend(
            name = "OPTIMIZED — withMonthHeader=false (n=$MEDIA_COUNT)",
            warmup = 2,
            iterations = 5
        ) {
            mapMediaToItem(
                data = media,
                error = "",
                albumId = -1L,
                groupByMonth = false,
                withMonthHeader = false,
                defaultDateFormat = Constants.DEFAULT_DATE_FORMAT,
                extendedDateFormat = Constants.EXTENDED_DATE_FORMAT,
                weeklyDateFormat = Constants.WEEKLY_DATE_FORMAT
            )
        }

        logComparison("Skip monthly vs full", withMonthly, withoutMonthly)
    }

    // ==========================================================================
    // 2. Issue #6: Metadata diff — O(N×M) linear scan vs O(N) HashSet
    // ==========================================================================

    @Test
    fun test3_metadataDiff_linearVsHashSet() {
        logSection("Issue #6: Metadata Diff — Linear Scan O(N×M) vs HashSet O(N)")

        data class SimpleMedia(val id: Long)

        val oldMedia = List(METADATA_COUNT) { SimpleMedia(it.toLong()) }
        val allMedia = List(METADATA_COUNT + 500) { SimpleMedia(it.toLong()) }

        logInfo("oldMedia: ${oldMedia.size}, allMedia: ${allMedia.size}")
        logInfo("Expected diff: ${allMedia.size - oldMedia.size} items")

        // CURRENT: O(N×M)
        val current = benchmark(
            name = "CURRENT  — linear scan O(N×M)",
            warmup = 2,
            iterations = 5
        ) {
            allMedia.filter { mediaItem ->
                oldMedia.none { it.id == mediaItem.id }
            }
        }

        // OPTIMIZED: O(N)
        val optimized = benchmark(
            name = "OPTIMIZED — HashSet O(N)",
            warmup = 2,
            iterations = 5
        ) {
            val oldIds = oldMedia.mapTo(HashSet(oldMedia.size)) { it.id }
            allMedia.filter { it.id !in oldIds }
        }

        logComparison("HashSet vs Linear", current, optimized)
    }

    // ==========================================================================
    // 3. Issue #7: Location filtering — chained filter+find vs Set lookup
    // ==========================================================================

    @Test
    fun test4_locationFiltering_findVsHashSet() {
        logSection("Issue #7: Location Filtering — find() O(N×M) vs HashSet O(N)")

        data class MetadataItem(
            val mediaId: Long,
            val gpsLocationNameCity: String?,
            val gpsLocationNameCountry: String?
        )

        val cities = listOf(
            "Paris", "London", "Berlin", "Tokyo", "NYC",
            "Rome", "Madrid", "Vienna", "Prague", "Dublin"
        )
        val countries = listOf(
            "France", "UK", "Germany", "Japan", "USA",
            "Italy", "Spain", "Austria", "Czechia", "Ireland"
        )

        val metadata = List(METADATA_COUNT) { i ->
            MetadataItem(
                mediaId = i.toLong(),
                gpsLocationNameCity = if (i % 3 == 0) null else cities[i % cities.size],
                gpsLocationNameCountry = if (i % 3 == 0) null else countries[i % countries.size]
            )
        }

        val media = generateMedia(MEDIA_COUNT)
        val targetCity = "Paris"
        val targetCountry = "France"

        logInfo("metadata: ${metadata.size}, media: ${media.size}")

        // CURRENT: chained filter + find O(N×M)
        val current = benchmark(
            name = "CURRENT  — chained filter+find O(N×M)",
            iterations = 10
        ) {
            val filteredMetadata = metadata.filter {
                it.gpsLocationNameCity != null && it.gpsLocationNameCountry != null
            }.filter {
                it.gpsLocationNameCity == targetCity && it.gpsLocationNameCountry == targetCountry
            }
            media.filter { m ->
                filteredMetadata.find { it.mediaId == m.id } != null
            }
        }

        // OPTIMIZED: HashSet of matching IDs
        val optimized = benchmark(
            name = "OPTIMIZED — HashSet O(N)",
            iterations = 10
        ) {
            val matchingIds = metadata
                .filter {
                    it.gpsLocationNameCity == targetCity &&
                            it.gpsLocationNameCountry == targetCountry
                }
                .mapTo(HashSet()) { it.mediaId }
            media.filter { it.id in matchingIds }
        }

        logComparison("HashSet vs find()", current, optimized)
    }

    // ==========================================================================
    // 4. Issue #8: locationsMediaFlow — sortedBy per group vs pre-built Map
    // ==========================================================================

    @Test
    fun test5_locationsMedia_sortedFindVsMapLookup() {
        logSection("Issue #8: Locations — sorted+find per group vs Map lookup")

        data class LocationItem(val mediaId: Long, val location: String)

        val media = generateMedia(MEDIA_COUNT)
        val locationGroups = List(LOCATION_GROUP_COUNT) { i ->
            LocationItem(
                mediaId = Random.nextLong(0, MEDIA_COUNT.toLong()),
                location = "City_${i % 50}, Country_${i % 20}"
            )
        }.groupBy { it.location }

        logInfo("media: ${media.size}, location groups: ${locationGroups.size}")

        // CURRENT: sortedByDescending + find per group
        val current = benchmark(
            name = "CURRENT  — sortedByDescending+find per group",
            iterations = 10
        ) {
            locationGroups.mapNotNull { (location, items) ->
                val found = media
                    .sortedByDescending { it.definedTimestamp }
                    .find { it.id == items.first().mediaId }
                found?.let { location to it }
            }
        }

        // OPTIMIZED: pre-built Map, O(1) lookup per group
        val optimized = benchmark(
            name = "OPTIMIZED — pre-built Map, O(1) lookup",
            iterations = 10
        ) {
            val mediaMap = HashMap<Long, Media.UriMedia>(media.size)
            for (m in media) { mediaMap[m.id] = m }
            locationGroups.mapNotNull { (location, items) ->
                mediaMap[items.first().mediaId]?.let { location to it }
            }
        }

        logComparison("Map vs sorted+find", current, optimized)
    }

    // ==========================================================================
    // 5. Issue #3: Album isPinned — N individual DB queries vs batch Set
    // ==========================================================================

    @Test
    fun test6_albumPinnedLookup_individualVsBatchSet() {
        logSection("Issue #3: Album isPinned — individual lookup vs batch Set")

        // Simulate the pinned album IDs (a small set)
        val pinnedIds = List(8) { Random.nextLong(0, ALBUM_COUNT.toLong()) }
        val albumIds = List(ALBUM_COUNT) { it.toLong() }

        logInfo("albums: ${albumIds.size}, pinned: ${pinnedIds.size}")
        logInfo("NOTE: real impact is larger — current approach does N synchronous Room queries")

        // CURRENT: simulate individual lookups (List.any per album)
        val current = benchmark(
            name = "CURRENT  — individual lookup × $ALBUM_COUNT",
            iterations = 500
        ) {
            albumIds.map { albumId ->
                pinnedIds.any { it == albumId }
            }
        }

        // OPTIMIZED: batch to HashSet, O(1) per check
        val optimized = benchmark(
            name = "OPTIMIZED — batch HashSet",
            iterations = 500
        ) {
            val pinnedSet = pinnedIds.toHashSet()
            albumIds.map { it in pinnedSet }
        }

        logComparison("Set vs Individual", current, optimized)
    }

    // ==========================================================================
    // 6. Issue #5: Album.isOnSdcard — regex compiled per instance vs cached
    // ==========================================================================

    @Test
    fun test7_regexCompilation_perInstanceVsCached() {
        logSection("Issue #5: Regex — per-instance compilation vs cached")

        val volumes = List(ALBUM_COUNT) {
            if (it % 5 == 0) "/storage/1a2b-3c4d" else "/storage/emulated/0"
        }

        logInfo("Testing on ${volumes.size} album volumes")

        // CURRENT: toRegex() compiled per Album constructor call
        val current = benchmark(
            name = "CURRENT  — toRegex() per instance × $ALBUM_COUNT",
            iterations = 200
        ) {
            volumes.map { volume ->
                volume.lowercase().matches(".*[0-9a-f]{4}-[0-9a-f]{4}".toRegex())
            }
        }

        // OPTIMIZED: pre-compiled companion val
        val optimized = benchmark(
            name = "OPTIMIZED — cached companion regex",
            iterations = 200
        ) {
            volumes.map { volume ->
                volume.lowercase().matches(SD_CARD_REGEX_CACHED)
            }
        }

        logComparison("Cached vs per-instance", current, optimized)
    }

    // ==========================================================================
    // 7. Memory: MediaState triple-list allocation measurement
    // ==========================================================================

    @Test
    fun test8_mediaStateMemoryPressure() = runBlocking {
        logSection("Issue #9-10: MediaState — triple-list memory measurement")

        val media = generateMedia(MEDIA_COUNT)

        logInfo("Measuring object allocation for $MEDIA_COUNT media items...")

        val runtime = Runtime.getRuntime()

        // Force GC to get a clean baseline
        System.gc(); Thread.sleep(200)
        val beforeFull = runtime.totalMemory() - runtime.freeMemory()

        val stateFull = mapMediaToItem(
            data = media,
            error = "",
            albumId = -1L,
            groupByMonth = false,
            withMonthHeader = true,
            defaultDateFormat = Constants.DEFAULT_DATE_FORMAT,
            extendedDateFormat = Constants.EXTENDED_DATE_FORMAT,
            weeklyDateFormat = Constants.WEEKLY_DATE_FORMAT
        )

        val afterFull = runtime.totalMemory() - runtime.freeMemory()
        val fullBytes = afterFull - beforeFull

        // Prevent GC from collecting stateFull
        @Suppress("UNUSED_VARIABLE")
        val keepAlive1 = stateFull.mappedMedia.size + stateFull.mappedMediaWithMonthly.size

        logInfo("Full mapping (3 lists): ~${fullBytes / 1024}KB allocated")
        logInfo("  media list:          ${stateFull.media.size} items")
        logInfo("  mappedMedia:         ${stateFull.mappedMedia.size} items")
        logInfo("  mappedMediaMonthly:  ${stateFull.mappedMediaWithMonthly.size} items")
        logInfo("  headers:             ${stateFull.headers.size} items")

        System.gc(); Thread.sleep(200)
        val beforeLean = runtime.totalMemory() - runtime.freeMemory()

        val stateLean = mapMediaToItem(
            data = media,
            error = "",
            albumId = -1L,
            groupByMonth = false,
            withMonthHeader = false,
            defaultDateFormat = Constants.DEFAULT_DATE_FORMAT,
            extendedDateFormat = Constants.EXTENDED_DATE_FORMAT,
            weeklyDateFormat = Constants.WEEKLY_DATE_FORMAT
        )

        val afterLean = runtime.totalMemory() - runtime.freeMemory()
        val leanBytes = afterLean - beforeLean

        @Suppress("UNUSED_VARIABLE")
        val keepAlive2 = stateLean.mappedMedia.size + stateLean.mappedMediaWithMonthly.size

        logInfo("Lean mapping (2 lists): ~${leanBytes / 1024}KB allocated")
        logInfo("  mappedMediaMonthly:  ${stateLean.mappedMediaWithMonthly.size} items (should be 0)")

        if (fullBytes > 0 && leanBytes > 0) {
            val savedKB = (fullBytes - leanBytes) / 1024
            val pct = ((fullBytes - leanBytes).toFloat() / fullBytes * 100)
            logInfo("Potential savings: ~${savedKB}KB (${String.format("%.0f", pct)}%)")
        }
    }

    // ==========================================================================
    // Summary
    // ==========================================================================

    @Test
    fun test9_summary() {
        logSummaryHeader()
        logInfo("All data processing benchmarks complete.")
        logInfo("Review results above or filter logcat with: adb logcat -s GalleryBenchmark")
        logInfo("")
        logInfo("Key metrics to watch:")
        logInfo("  - mapMediaToItem time at your library size")
        logInfo("  - Metadata diff speedup (HashSet vs linear)")
        logInfo("  - Location filtering speedup")
        logInfo("  - Locations sorted+find vs Map lookup")
        logInfo("  - Album pinned batch vs individual (real DB impact is larger)")
        logInfo("  - Regex compilation overhead")
        logInfo("  - Memory savings from skipping monthly headers")
    }
}
