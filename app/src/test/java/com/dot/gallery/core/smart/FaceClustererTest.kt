/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.smart

import com.dot.gallery.core.smart.FaceClusterer.Component
import com.dot.gallery.core.smart.FaceClusterer.Face
import com.dot.gallery.core.smart.FaceClusterer.FaceAssertion
import com.dot.gallery.core.smart.FaceClusterer.MediaAssertion
import com.dot.gallery.core.smart.FaceClusterer.PersonSeed
import com.dot.gallery.core.smart.FaceClusterer.Suppression
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/**
 * Synthetic-embedding tests for [FaceClusterer]. Embeddings are built as
 * `normalize(weight * e[shared] + (1 - weight) * e[unique])` over disjoint basis
 * dims, so pairwise cosine is fully controlled by [weight].
 */
class FaceClustererTest {

    private var nextFaceId = 1L

    /** Pairwise cosine between two faces with the same shared dim ≈ weight² / (weight² + (1−weight)²). */
    private fun embedding(shared: Int, unique: Int, weight: Float): FloatArray {
        val v = FloatArray(DIM)
        v[shared] = weight
        v[unique] = 1f - weight
        var sum = 0f
        for (x in v) sum += x * x
        val norm = sqrt(sum)
        return FloatArray(DIM) { v[it] / norm }
    }

    private fun face(
        mediaId: Long,
        embedding: FloatArray,
        personId: String? = null,
        left: Float = 0.1f,
        top: Float = 0.1f,
        right: Float = 0.2f,
        bottom: Float = 0.2f,
        confidence: Float = 0.9f
    ) = Face(
        id = nextFaceId++,
        mediaId = mediaId,
        embedding = embedding,
        left = left,
        top = top,
        right = right,
        bottom = bottom,
        confidence = confidence,
        personId = personId
    )

    private fun person(id: String, curated: Boolean = false, faceCount: Int = 0) =
        PersonSeed(id, curated, faceCount)

    private fun cluster(
        faces: List<Face>,
        persons: List<PersonSeed> = emptyList(),
        faceAssertions: List<FaceAssertion> = emptyList(),
        mediaAssertions: List<MediaAssertion> = emptyList(),
        suppressions: List<Suppression> = emptyList()
    ) = runBlocking {
        FaceClusterer.cluster(faces, persons, faceAssertions, mediaAssertions, suppressions)
    }

    private fun FaceClusterer.Result.personOf(faceId: Long): String? =
        resolved.firstOrNull { (_, c) -> faceId in c.faceIds }?.first

    @Test
    fun identicalPersonAcrossTwoOldClustersIsMerged() {
        // The bug this fixes: one person's faces split into two existing clusters
        // by the old online assigner. Batch re-grouping must unify them.
        val emb = { u: Int -> embedding(shared = 0, unique = u, weight = 0.8f) } // pairwise ≈0.94
        val faces = listOf(
            face(1, emb(10), personId = "p1"),
            face(2, emb(11), personId = "p1"),
            face(3, emb(12), personId = "p1"),
            face(4, emb(13), personId = "p2"),
            face(5, emb(14), personId = "p2")
        )
        val result = cluster(
            faces,
            persons = listOf(person("p1", faceCount = 3), person("p2", faceCount = 2))
        )

        assertEquals(1, result.resolved.size)
        val (pid, component) = result.resolved.single()
        assertEquals("p1", pid) // larger member overlap wins
        assertEquals(faces.map { it.id }.toSet(), component.faceIds.toSet())
        assertTrue(result.fresh.isEmpty())
    }

    @Test
    fun dissimilarFacesStayInSeparatePersons() {
        val faces = listOf(
            face(1, embedding(0, 10, 0.9f), personId = "p1"),
            face(2, embedding(0, 11, 0.9f), personId = "p1"),
            face(3, embedding(40, 50, 0.9f), personId = "p2"),
            face(4, embedding(40, 51, 0.9f), personId = "p2")
        )
        val result = cluster(
            faces,
            persons = listOf(person("p1"), person("p2"))
        )

        assertEquals(2, result.resolved.size)
        assertTrue(result.fresh.isEmpty())
        assertEquals("p1", result.personOf(faces[0].id))
        assertEquals("p1", result.personOf(faces[1].id))
        assertEquals("p2", result.personOf(faces[2].id))
        assertEquals("p2", result.personOf(faces[3].id))
    }

    @Test
    fun excludeLinkPreventsMergingSimilarFaces() {
        // Two similar faces that the user said are different people must stay apart.
        val f1 = face(1, embedding(0, 10, 0.8f), personId = "p1")
        val f2 = face(2, embedding(0, 11, 0.8f), personId = "p2")
        val exclude = FaceAssertion(
            mediaId = f1.mediaId,
            left = f1.left, top = f1.top, right = f1.right, bottom = f1.bottom,
            personId = "p2",
            include = false
        )

        val result = cluster(
            listOf(f1, f2),
            persons = listOf(person("p1"), person("p2")),
            faceAssertions = listOf(exclude)
        )

        assertEquals("p1", result.personOf(f1.id))
        assertEquals("p2", result.personOf(f2.id))
    }

    @Test
    fun includeLinkKeepsOddLookingFaceInPerson() {
        // A pinned face that is embedding-wise far from its person must not be ejected.
        val emb = { u: Int -> embedding(0, u, 0.85f) }
        val members = listOf(
            face(1, emb(10), personId = "p1"),
            face(2, emb(11), personId = "p1"),
            face(3, emb(12), personId = "p1")
        )
        val odd = face(4, embedding(200, 210, 0.9f), personId = null)
        val pin = FaceAssertion(
            mediaId = odd.mediaId,
            left = odd.left, top = odd.top, right = odd.right, bottom = odd.bottom,
            personId = "p1",
            include = true
        )

        val result = cluster(
            members + odd,
            persons = listOf(person("p1", faceCount = 3)),
            faceAssertions = listOf(pin)
        )

        assertEquals("p1", result.personOf(odd.id))
    }

    @Test
    fun mediaExclusionBlocksFaceFromExcludedPerson() {
        // "This photo does not contain p2" — face on that media can't join p2.
        val f1 = face(1, embedding(0, 10, 0.85f), personId = null)
        val f2 = face(2, embedding(0, 11, 0.85f), personId = "p2")
        val f3 = face(3, embedding(0, 12, 0.85f), personId = "p2")

        val result = cluster(
            listOf(f1, f2, f3),
            persons = listOf(person("p2")),
            mediaAssertions = listOf(MediaAssertion(mediaId = 1, personId = "p2"))
        )

        assertTrue(result.personOf(f1.id) != "p2")
        assertEquals("p2", result.personOf(f2.id))
        assertEquals("p2", result.personOf(f3.id))
    }

    @Test
    fun curatedPersonWinsIdentityOverUnnamed() {
        // Two same-sized groups merging: the named person keeps the id.
        val f1 = face(1, embedding(0, 10, 0.8f), personId = "p_unnamed")
        val f2 = face(2, embedding(0, 11, 0.8f), personId = "p_named")
        val result = cluster(
            listOf(f1, f2),
            persons = listOf(
                person("p_unnamed", curated = false, faceCount = 1),
                person("p_named", curated = true, faceCount = 1)
            )
        )

        assertEquals(1, result.resolved.size)
        assertEquals("p_named", result.resolved.single().first)
    }

    @Test
    fun unassignedFacesFormFreshComponents() {
        val faces = listOf(
            face(1, embedding(0, 10, 0.85f)),
            face(2, embedding(0, 11, 0.85f)),
            face(3, embedding(100, 110, 0.9f))
        )

        val result = cluster(faces)

        assertTrue(result.resolved.isEmpty())
        assertEquals(2, result.fresh.size)
        val groups = result.fresh.map { it.faceIds.toSet() }
        assertTrue(setOf(faces[0].id, faces[1].id) in groups)
        assertTrue(setOf(faces[2].id) in groups)
    }

    @Test
    fun lowQualityFacesAreLeftUnassigned() {
        val tiny = face(
            1, embedding(0, 10, 0.9f),
            left = 0.0f, top = 0.0f, right = 0.03f, bottom = 0.03f, confidence = 0.9f
        )
        val good = face(2, embedding(0, 11, 0.85f), personId = "p1")

        val result = cluster(listOf(tiny, good), persons = listOf(person("p1")))

        assertTrue(tiny.id in result.unassignedFaceIds)
        assertEquals("p1", result.personOf(good.id))
    }

    @Test
    fun clusteringIsOrderIndependent() {
        val faces = listOf(
            face(1, embedding(0, 10, 0.8f), personId = "p1"),
            face(2, embedding(0, 11, 0.8f), personId = "p2"),
            face(3, embedding(0, 12, 0.8f), personId = "p1"),
            face(4, embedding(50, 60, 0.9f), personId = null),
            face(5, embedding(50, 61, 0.9f), personId = null)
        )
        val persons = listOf(person("p1"), person("p2"))

        val forward = cluster(faces, persons)
        val backward = cluster(faces.reversed(), persons)

        fun partition(r: FaceClusterer.Result): Set<Set<Long>> =
            (r.resolved.map { it.second.faceIds.toSet() } + r.fresh.map { it.faceIds.toSet() }).toSet()

        assertEquals(partition(forward), partition(backward))
    }

    @Test
    fun emptyInputProducesEmptyResult() {
        val result = cluster(emptyList(), persons = listOf(person("p1")))
        assertTrue(result.resolved.isEmpty())
        assertTrue(result.fresh.isEmpty())
        assertTrue(result.unassignedFaceIds.isEmpty())
    }

    @Test
    fun componentCentroidsAreNormalized() {
        val faces = listOf(
            face(1, embedding(0, 10, 0.8f)),
            face(2, embedding(0, 11, 0.8f))
        )
        val result = cluster(faces)
        val centroid = result.fresh.single().centroid
        var sum = 0f
        for (x in centroid) sum += x * x
        assertEquals(1f, sqrt(sum), 0.001f)
    }

    @Test
    fun assertionToDeletedPersonIsIgnored() {
        // A link pointing at a person row that no longer exists must not pin anything.
        val f1 = face(1, embedding(0, 10, 0.8f), personId = "p1")
        val f2 = face(2, embedding(0, 11, 0.8f), personId = "p2")
        val stalePin = FaceAssertion(
            mediaId = f1.mediaId,
            left = f1.left, top = f1.top, right = f1.right, bottom = f1.bottom,
            personId = "p_gone",
            include = true
        )
        val result = cluster(
            listOf(f1, f2),
            persons = listOf(person("p1"), person("p2")),
            faceAssertions = listOf(stalePin)
        )
        // Faces are similar → still merge into one of the existing persons.
        assertEquals(1, result.resolved.size)
        assertTrue("p_gone" != result.resolved.single().first)
    }

    @Test
    fun mutuallySimilarOddFacesFormTheirOwnComponent() {
        // Two faces similar to each other but unrelated to p1 must never land in p1.
        val main = (0 until 6).map { i -> face(i.toLong() + 1, embedding(0, 20 + i, 0.85f), personId = "p1") }
        val odd1 = face(7, embedding(70, 100, 0.8f), personId = null)
        val odd2 = face(8, embedding(70, 101, 0.8f), personId = null)
        val odd3 = face(9, embedding(300, 310, 0.9f), personId = null) // unrelated singleton

        val result = cluster(main + odd1 + odd2 + odd3, persons = listOf(person("p1")))

        assertEquals("p1", result.personOf(main[0].id))
        // The odd pair regroups into one fresh component together.
        val oddGroups = result.fresh.filter { odd1.id in it.faceIds || odd2.id in it.faceIds }
        assertEquals(1, oddGroups.size)
        assertTrue(odd1.id in oddGroups.single().faceIds)
        assertTrue(odd2.id in oddGroups.single().faceIds)
        // The unrelated singleton ends up alone — fresh or unassigned, never in p1.
        assertTrue(result.personOf(odd3.id) != "p1")
    }

    @Test
    fun suppressedFaceNeverJoinsAnyPerson() {
        // Person deleted → its faces were written as suppressions. Even when the
        // remaining faces are a near-duplicate of another person, the suppressed
        // box must stay out of every group.
        val suppressed = face(1, embedding(0, 10, 0.9f), personId = "p_gone")
        val member = face(2, embedding(0, 11, 0.9f), personId = "p1")

        val result = cluster(
            listOf(suppressed, member),
            persons = listOf(person("p1")),
            suppressions = listOf(
                Suppression(
                    mediaId = suppressed.mediaId,
                    left = suppressed.left, top = suppressed.top,
                    right = suppressed.right, bottom = suppressed.bottom
                )
            )
        )

        assertTrue(suppressed.id in result.unassignedFaceIds)
        assertTrue(result.resolved.none { suppressed.id in it.second.faceIds })
        assertTrue(result.fresh.none { suppressed.id in it.faceIds })
        assertEquals("p1", result.personOf(member.id))
    }

    @Test
    fun suppressedFacesDoNotFormAFreshPersonTogether() {
        // Two suppressed boxes that are similar to each other must not resurrect
        // as a new unnamed person — they are dropped before grouping runs.
        val s1 = face(1, embedding(0, 10, 0.85f))
        val s2 = face(2, embedding(0, 11, 0.85f))
        val live = face(3, embedding(40, 50, 0.85f))

        val result = cluster(
            listOf(s1, s2, live),
            suppressions = listOf(
                Suppression(1, s1.left, s1.top, s1.right, s1.bottom),
                Suppression(2, s2.left, s2.top, s2.right, s2.bottom)
            )
        )

        assertTrue(s1.id in result.unassignedFaceIds)
        assertTrue(s2.id in result.unassignedFaceIds)
        assertEquals(1, result.fresh.size)
        assertEquals(listOf(live.id), result.fresh.single().faceIds)
    }

    @Test
    fun suppressionOnlyMatchesTheSameMediaAndOverlappingBox() {
        // Same box on a different media, or a disjoint box on the same media,
        // must not suppress the face.
        val f = face(1, embedding(0, 10, 0.85f), personId = "p1")

        val differentMedia = cluster(
            listOf(f),
            persons = listOf(person("p1")),
            suppressions = listOf(
                Suppression(99, f.left, f.top, f.right, f.bottom)
            )
        )
        assertEquals("p1", differentMedia.personOf(f.id))

        val disjointBox = cluster(
            listOf(f),
            persons = listOf(person("p1")),
            suppressions = listOf(
                Suppression(1, 0.7f, 0.7f, 0.9f, 0.9f)
            )
        )
        assertEquals("p1", disjointBox.personOf(f.id))
    }

    private companion object {
        const val DIM = 512
    }
}
