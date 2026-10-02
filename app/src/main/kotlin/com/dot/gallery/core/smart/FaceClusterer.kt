/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.smart

import kotlin.math.sqrt

/**
 * Deterministic batch face grouper. Rebuilds every person assignment from scratch over
 * all stored embeddings, so the result never depends on scan order — the fix for the
 * old online nearest-centroid assignment, which split one person into many clusters.
 *
 * Pipeline:
 *  1. Quality gate — tiny/low-confidence faces are kept out of grouping entirely.
 *  2. Union-find over pairwise cosine similarity, honouring user assertions:
 *     INCLUDE links must-link faces to a person, EXCLUDE links and per-media
 *     exclusions cannot-link a face away from a person.
 *  3. Cohesion refinement — members too far from their component centroid are
 *     ejected and re-attached to the best fitting component (or left unassigned),
 *     which limits single-linkage chaining.
 *  4. Components are mapped back onto existing persons by max member overlap,
 *     preserving user-curated identities (names, hidden flags, covers).
 *
 * Pure Kotlin, no Room/Android deps — unit-testable with synthetic vectors.
 */
object FaceClusterer {

    /** Cosine similarity at/above which two faces are linked into one component. */
    const val LINK_THRESHOLD = 0.50f

    /** Above this similarity a pair links unconditionally — confident same-person match. */
    const val STRONG_LINK_THRESHOLD = 0.62f

    /**
     * Marginal links ([LINK_THRESHOLD]..[STRONG_LINK_THRESHOLD]) must additionally be
     * mutual top-[MUTUAL_K] neighbors — kills single-linkage chaining through
     * mediocre matches without hurting confident merges.
     */
    const val MUTUAL_K = 8

    /** Members below this similarity to their component centroid get ejected. */
    const val COHESION_MIN = 0.40f

    /** Minimum normalized-box-area × confidence for a face to take part in grouping. */
    const val MIN_FACE_QUALITY = 0.002f

    /** IoU at which a stored assertion box matches a live face box. */
    const val LINK_MATCH_IOU = 0.5f

    class Face(
        val id: Long,
        val mediaId: Long,
        val embedding: FloatArray,
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val confidence: Float,
        val personId: String?
    ) {
        val area: Float get() = (right - left).coerceAtLeast(0f) * (bottom - top).coerceAtLeast(0f)
        val quality: Float get() = area * confidence
    }

    /** An existing person row, reduced to what grouping needs. */
    class PersonSeed(
        val id: String,
        /** True when the user touched this person (named or hidden) — wins identity ties. */
        val curated: Boolean,
        val faceCount: Int
    )

    /** A face-scoped user assertion: "this face (mediaId + box, matched by IoU) is / is not personId". */
    class FaceAssertion(
        val mediaId: Long,
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val personId: String,
        val include: Boolean
    )

    /** A media-scoped user assertion: "this photo does not contain personId". */
    class MediaAssertion(
        val mediaId: Long,
        val personId: String
    )

    /**
     * A media+box region that must never enter a person group — written when a
     * person is deleted (#1262). Matched to live faces by [LINK_MATCH_IOU], the
     * same rule as [FaceAssertion], so it survives re-detected face ids.
     */
    class Suppression(
        val mediaId: Long,
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    )

    /** A connected component of faces plus its normalized centroid. */
    class Component(
        val faceIds: List<Long>,
        val centroid: FloatArray
    )

    class Result(
        /** Components mapped onto an existing person id. */
        val resolved: List<Pair<String, Component>>,
        /** Components that need a new person row. */
        val fresh: List<Component>,
        /** Face ids that end up with no person (low quality or incoherent outliers). */
        val unassignedFaceIds: List<Long>
    )

    suspend fun cluster(
        faces: List<Face>,
        persons: List<PersonSeed>,
        faceAssertions: List<FaceAssertion>,
        mediaAssertions: List<MediaAssertion>,
        suppressions: List<Suppression> = emptyList(),
        onProgress: suspend (processed: Int, total: Int) -> Unit = { _, _ -> }
    ): Result {
        fun suppressed(face: Face): Boolean = suppressions.any { s ->
            s.mediaId == face.mediaId &&
                boxIoU(
                    face.left, face.top, face.right, face.bottom,
                    s.left, s.top, s.right, s.bottom
                ) >= LINK_MATCH_IOU
        }
        val usable = faces.filter { it.quality >= MIN_FACE_QUALITY && !suppressed(it) }
        // Suppressed faces ride along with the low-quality faces so apply()
        // forcibly clears any stale personId they may still carry.
        val unusableFaceIds = faces
            .filter { it.quality < MIN_FACE_QUALITY || suppressed(it) }
            .map { it.id }
        val n = usable.size
        if (n == 0) {
            return Result(
                resolved = persons
                    .filter { p -> faces.any { it.personId == p.id } }
                    .map { it.id to Component(emptyList(), FloatArray(0)) },
                fresh = emptyList(),
                unassignedFaceIds = unusableFaceIds
            )
        }
        onProgress(0, n)

        val dsu = UnionFind(n)
        val personIds = persons.mapTo(hashSetOf()) { it.id }
        val personById = persons.associateBy { it.id }

        // ── Resolve assertions onto live faces ──────────────────────────────
        // pinnedFacePersons[i]: persons this face was INCLUDE-pinned to (existing only)
        // excludedPersons[i]:  persons this face may never join (face + media assertions)
        val pinnedByFace = HashMap<Int, MutableSet<String>>()
        val excludedByFace = arrayOfNulls<HashSet<String>>(n)
        val excludedByMedia = HashMap<Long, MutableSet<String>>()
        mediaAssertions.forEach { assertion ->
            if (assertion.personId in personIds) {
                excludedByMedia.getOrPut(assertion.mediaId) { HashSet() } += assertion.personId
            }
        }

        fun excludedOf(index: Int): Set<String> {
            val face = usable[index]
            val mediaEx = excludedByMedia[face.mediaId]
            val faceEx = excludedByFace[index]
            return when {
                faceEx == null && mediaEx == null -> emptySet()
                faceEx == null -> mediaEx!!
                mediaEx == null -> faceEx
                else -> faceEx + mediaEx
            }
        }

        // claimedOf(i): persons the face is attached to right now (pinned or current assignment)
        fun claimedOf(index: Int): Set<String> {
            val pinned = pinnedByFace[index]
            val current = usable[index].personId
            return when {
                pinned.isNullOrEmpty() && current == null -> emptySet()
                pinned.isNullOrEmpty() -> setOf(current!!)
                current == null -> pinned
                else -> pinned + current
            }
        }

        fun boxIoU(face: Face, l: Float, t: Float, r: Float, b: Float): Float =
            boxIoU(face.left, face.top, face.right, face.bottom, l, t, r, b)

        // Best-IoU live face per assertion; assertions pointing at deleted persons are ignored.
        val facesByMedia = usable.indices.groupBy { usable[it].mediaId }
        faceAssertions.forEach { assertion ->
            if (assertion.personId !in personIds) return@forEach
            val match = facesByMedia[assertion.mediaId]
                ?.maxByOrNull {
                    boxIoU(usable[it], assertion.left, assertion.top, assertion.right, assertion.bottom)
                }
                ?.takeIf {
                    boxIoU(usable[it], assertion.left, assertion.top, assertion.right, assertion.bottom) >=
                        LINK_MATCH_IOU
                } ?: return@forEach
            if (assertion.include) {
                pinnedByFace.getOrPut(match) { HashSet() } += assertion.personId
            } else {
                (excludedByFace[match] ?: HashSet<String>().also { excludedByFace[match] = it }) +=
                    assertion.personId
            }
        }

        // ── Union-find ──────────────────────────────────────────────────────
        // Must-link: faces pinned to the same person are one component.
        val pinnedGroups = HashMap<String, MutableList<Int>>()
        pinnedByFace.forEach { (index, pins) ->
            pins.forEach { personId -> pinnedGroups.getOrPut(personId) { ArrayList() } += index }
        }
        pinnedGroups.values.forEach { indices ->
            for (k in 1 until indices.size) dsu.union(indices[0], indices[k])
        }

        // Cannot-link helper: face i excluded from a person face j claims (or vice versa).
        fun blocked(i: Int, j: Int): Boolean {
            val exI = excludedOf(i)
            val exJ = excludedOf(j)
            if (exI.isEmpty() && exJ.isEmpty()) return false
            val claimedI = if (exJ.isEmpty()) emptySet() else claimedOf(i)
            val claimedJ = if (exI.isEmpty()) emptySet() else claimedOf(j)
            return exI.any { it in claimedJ } || exJ.any { it in claimedI }
        }

        // Collect candidate edges (sim ≥ LINK) plus each face's top-MUTUAL_K
        // neighbors. Marginal edges must be mutual — confident edges link directly.
        class Edge(val i: Int, val j: Int, val sim: Float)
        class Neighbor(val j: Int, val sim: Float)
        val edges = ArrayList<Edge>()
        val topK = Array(n) { ArrayList<Neighbor>(MUTUAL_K + 1) }

        fun recordNeighbor(i: Int, j: Int, sim: Float) {
            val list = topK[i]
            if (list.size < MUTUAL_K) {
                list += Neighbor(j, sim)
                return
            }
            var minIdx = 0
            var minSim = Float.MAX_VALUE
            for (k in list.indices) {
                if (list[k].sim < minSim) { minSim = list[k].sim; minIdx = k }
            }
            if (sim > minSim) list[minIdx] = Neighbor(j, sim)
        }

        for (i in 0 until n) {
            val embI = usable[i].embedding
            for (j in i + 1 until n) {
                if (blocked(i, j)) continue
                val sim = cosine(embI, usable[j].embedding)
                if (sim >= LINK_THRESHOLD) {
                    edges += Edge(i, j, sim)
                    recordNeighbor(i, j, sim)
                    recordNeighbor(j, i, sim)
                }
            }
            if (i and 0x1FF == 0) onProgress(i, n)
        }
        edges.sortByDescending { it.sim }
        edges.forEach { edge ->
            if (dsu.find(edge.i) == dsu.find(edge.j)) return@forEach
            val strong = edge.sim >= STRONG_LINK_THRESHOLD
            val mutual = topK[edge.i].any { it.j == edge.j } &&
                topK[edge.j].any { it.j == edge.i }
            if (strong || mutual) dsu.union(edge.i, edge.j)
        }

        // ── Components ──────────────────────────────────────────────────────
        val groups = HashMap<Int, MutableList<Int>>()
        for (i in 0 until n) groups.getOrPut(dsu.find(i)) { ArrayList() } += i
        var components: MutableList<List<Int>> = ArrayList(groups.values)

        fun centroidOf(indices: List<Int>): FloatArray {
            val dim = usable[indices.first()].embedding.size
            val sum = FloatArray(dim)
            indices.forEach { index ->
                val emb = usable[index].embedding
                for (d in 0 until dim) sum[d] += emb[d]
            }
            return l2Normalize(FloatArray(dim) { sum[it] / indices.size })
        }

        // ── Cohesion refinement: eject members far from the component centroid ──
        val outliers = ArrayList<Int>()
        components = components.mapNotNullTo(ArrayList()) { members ->
            if (members.size < 3) return@mapNotNullTo members
            val centroid = centroidOf(members)
            val (keep, eject) = members.partition {
                // Pinned faces are must-linked — they may never be ejected.
                pinnedByFace.containsKey(it) ||
                    cosine(usable[it].embedding, centroid) >= COHESION_MIN
            }
            outliers += eject
            keep.ifEmpty { null }
        }.toMutableList()

        // Re-cluster the outliers among themselves first — a coherent subgroup
        // (e.g. one odd-looking person) is better kept together than scattered.
        if (outliers.size >= 2) {
            val odd = UnionFind(outliers.size)
            for (i in outliers.indices) {
                val fi = usable[outliers[i]]
                for (j in i + 1 until outliers.size) {
                    if (odd.find(i) == odd.find(j)) continue
                    if (cosine(fi.embedding, usable[outliers[j]].embedding) >= LINK_THRESHOLD) {
                        odd.union(i, j)
                    }
                }
            }
            val oddGroups = HashMap<Int, MutableList<Int>>()
            for (i in outliers.indices) oddGroups.getOrPut(odd.find(i)) { ArrayList() } += outliers[i]
            val regrouped = oddGroups.values.filter { it.size >= 2 }
            if (regrouped.isNotEmpty()) {
                val regroupedIds = regrouped.flatten().toHashSet()
                components += regrouped
                outliers.removeAll { it in regroupedIds }
            }
        }

        // Remaining outliers re-attach to the best component centroid, else unassigned.
        val unassigned = ArrayList<Long>()
        if (outliers.isNotEmpty()) {
            val centroids = components.map { centroidOf(it) }
            outliers.forEach { index ->
                val emb = usable[index].embedding
                var best = -1
                var bestSim = LINK_THRESHOLD
                centroids.forEachIndexed { c, centroid ->
                    if (components[c].any { blocked(index, it) }) return@forEachIndexed
                    val sim = cosine(emb, centroid)
                    if (sim >= bestSim) {
                        bestSim = sim
                        best = c
                    }
                }
                if (best >= 0) components[best] += index else unassigned += usable[index].id
            }
        }
        unassigned += unusableFaceIds

        // ── Map components onto existing persons ────────────────────────────
        class Scored(val members: List<Int>, val pinned: Set<String>, val membersByPerson: Map<String, Int>)

        val scored = components.map { members ->
            val pinned = members.flatMapTo(HashSet()) { pinnedByFace[it].orEmpty() }
            val byPerson = HashMap<String, Int>()
            members.forEach { index ->
                usable[index].personId?.let { byPerson[it] = (byPerson[it] ?: 0) + 1 }
            }
            Scored(members, pinned, byPerson)
        }

        fun componentExcludedFrom(memberIdx: List<Int>, personId: String): Boolean =
            memberIdx.any { personId in excludedOf(it) }

        val claimed = HashSet<String>()
        val resolved = ArrayList<Pair<String, Component>>()
        val fresh = ArrayList<Component>()

        // Pinned components first — a user pin is an explicit identity claim.
        scored.sortedByDescending { it.pinned.size }.forEach { entry ->
            val members = entry.members
            val pinnedTarget = entry.pinned
                .filter { it in personById && it !in claimed && !componentExcludedFrom(members, it) }
                .maxWithOrNull(
                    compareBy<String> { pid -> members.count { pinnedByFace[it]?.contains(pid) == true } }
                        .thenBy { personById.getValue(it).faceCount }
                )
            val target = pinnedTarget ?: entry.membersByPerson
                .filterKeys { it in personById && it !in claimed }
                .filterKeys { !componentExcludedFrom(members, it) }
                .maxWithOrNull(
                    compareBy<Map.Entry<String, Int>> { it.value }
                        .thenBy { if (personById.getValue(it.key).curated) 1 else 0 }
                        .thenBy { personById.getValue(it.key).faceCount }
                )?.key
            if (target != null && claimed.add(target)) {
                resolved += target to Component(
                    faceIds = members.map { usable[it].id },
                    centroid = centroidOf(members)
                )
            } else {
                fresh += Component(
                    faceIds = members.map { usable[it].id },
                    centroid = centroidOf(members)
                )
            }
        }

        return Result(resolved = resolved, fresh = fresh, unassignedFaceIds = unassigned)
    }

    fun cosine(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return -1f
        var dot = 0f
        for (i in a.indices) dot += a[i] * b[i]
        return dot
    }

    fun l2Normalize(v: FloatArray): FloatArray {
        var sum = 0f
        for (x in v) sum += x * x
        val norm = sqrt(sum).coerceAtLeast(1e-10f)
        return FloatArray(v.size) { v[it] / norm }
    }

    /** Intersection-over-union of two normalized boxes. */
    private fun boxIoU(
        l1: Float, t1: Float, r1: Float, b1: Float,
        l2: Float, t2: Float, r2: Float, b2: Float
    ): Float {
        val ix = maxOf(l1, l2)
        val iy = maxOf(t1, t2)
        val ax = minOf(r1, r2)
        val ay = minOf(b1, b2)
        val inter = (ax - ix).coerceAtLeast(0f) * (ay - iy).coerceAtLeast(0f)
        if (inter <= 0f) return 0f
        val area1 = (r1 - l1).coerceAtLeast(0f) * (b1 - t1).coerceAtLeast(0f)
        val union = area1 + (r2 - l2) * (b2 - t2) - inter
        return if (union <= 0f) 0f else inter / union
    }

    private class UnionFind(size: Int) {
        private val parent = IntArray(size) { it }
        private val rank = ByteArray(size)

        fun find(x: Int): Int {
            var root = x
            while (parent[root] != root) root = parent[root]
            var node = x
            while (parent[node] != root) {
                val next = parent[node]
                parent[node] = root
                node = next
            }
            return root
        }

        fun union(a: Int, b: Int) {
            val ra = find(a)
            val rb = find(b)
            if (ra == rb) return
            when {
                rank[ra] < rank[rb] -> parent[ra] = rb
                rank[ra] > rank[rb] -> parent[rb] = ra
                else -> {
                    parent[rb] = ra
                    rank[ra]++
                }
            }
        }
    }
}
